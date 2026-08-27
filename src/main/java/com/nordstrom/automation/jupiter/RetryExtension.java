package com.nordstrom.automation.jupiter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nordstrom.common.base.ExceptionUnwrapper;

/**
 * This extension provides automatic retry of failed {@code @Test} method invocations.
 * <p>
 * <b>WHY THIS ISN'T JUST "LOOP invocation.proceed()"</b>
 * <p>
 * {@link InvocationInterceptor.Invocation#proceed()} may only be called (or {@code skip()} called
 * instead) <b>exactly once</b> per interceptor method invocation - calling it a second time is a
 * documented contract violation, not merely discouraged. So the legitimate first attempt uses
 * {@code invocation.proceed()} - which is valid, since {@code @BeforeEach}/{@code @AfterEach} methods
 * are each their own separate {@code InvocationInterceptor} callback, already fired by the engine
 * independently of this one by the time it runs. Every <i>additional</i> attempt beyond the first,
 * however, cannot reuse {@code proceed()} at all: it manually re-invokes the test method (and, to match
 * the behavior of JUnit-Foundation's {@code RetryHandler} - a fresh, independent attempt rather than a
 * bare re-run against stale before-state - the declaring class's {@code @BeforeEach}/{@code @AfterEach}
 * methods too) via plain reflection, entirely outside the {@code InvocationInterceptor} chain.
 * <p>
 * <b>CONSEQUENCE FOR OTHER INTERCEPTORS</b>
 * <p>
 * Because retries beyond the first bypass the interceptor chain, any other registered
 * {@code InvocationInterceptor} (e.g. Selenium Foundation's driver-lifecycle watcher) will <b>not</b>
 * automatically re-wrap those attempts. This class is deliberately built to be extended - override
 * {@link #beforeAttempt(Object, Method)}/{@link #afterAttempt(Object, Method, Throwable)} to run whatever a subclass
 * needs around every attempt, matching the extension pattern already documented by TestNG Foundation's
 * own {@code RetryManager}: subclass, override, and register your subclass instead of this base class.
 * <p>
 * <b>CONFIGURATION</b>
 * <p>
 * The maximum retry count is sourced from {@link JupiterConfig} - the
 * {@link JupiterConfig.JupiterSettings#MAX_RETRY MAX_RETRY} setting (default {@code 0} - retry
 * disabled), overridable via system property or {@code jupiter.properties}, exactly matching the
 * configuration model TestNG Foundation's own {@code TestNGConfig} uses for the same purpose. Override
 * {@link #getMaxRetry(ExtensionContext, Method)} only if a different configuration mechanism is
 * genuinely needed - the retry count is a Jupiter Foundation setting, not something a consuming
 * framework (e.g. Selenium Foundation) should redefine, the same way {@code TestNGSettings.MAX_RETRY}
 * belongs to TestNG Foundation and not to any framework built on top of it.
 * <p>
 * Retry can be disabled per-method or per-class via {@link NoRetry}. Beyond the basic
 * exception-was-thrown check, scenario-specific veto/approval is delegated to any
 * {@link JupiterRetryAnalyzer} instances registered via {@link ServiceLoader}.
 *
 * @see JupiterRetryAnalyzer
 * @see NoRetry
 */
public class RetryExtension implements InvocationInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetryExtension.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public void interceptTestMethod(final Invocation<Void> invocation,
            final ReflectiveInvocationContext<Method> invocationContext,
            final ExtensionContext extensionContext) throws Throwable {

        Method testMethod = invocationContext.getExecutable();

        // the ONE legitimate proceed() call for this interceptor invocation - befores/afters for this
        // first attempt already ran via the engine's own separate, independent callbacks
        try {
            invocation.proceed();
            return;
        } catch (Throwable t) {
            Throwable thrown = ExceptionUnwrapper.unwrap(t);
            int maxRetry = getMaxRetry(extensionContext, testMethod);
            if (!isRetriable(testMethod, thrown, maxRetry)) {
                throw t;
            }
            logRetry(testMethod, thrown);

            // every attempt from here on is manual reflection - proceed()/skip() must never be called
            // again; the contract is satisfied by the single proceed() call above, regardless of outcome
            Throwable lastThrown = retryLoop(extensionContext, testMethod, thrown, maxRetry);
            if (lastThrown != null) {
                throw lastThrown;
            }
        }
    }

    /**
     * Run the manual retry loop for attempts beyond the first.
     *
     * @param extensionContext current extension context
     * @param testMethod failed test method
     * @param firstFailure exception from the already-consumed first attempt
     * @param maxRetry maximum number of retry attempts
     * @return exception from the final attempt; {@code null} if a retry attempt passed
     * @throws Throwable if instance/method access fails outside the retried invocation itself
     */
    private Throwable retryLoop(final ExtensionContext extensionContext, final Method testMethod,
            final Throwable firstFailure, final int maxRetry) throws Throwable {

        Object instance = extensionContext.getRequiredTestInstance();
        Class<?> declaringClass = testMethod.getDeclaringClass();
        List<Method> beforeEachMethods = findLifecycleMethods(declaringClass, BeforeEach.class, true);
        List<Method> afterEachMethods = findLifecycleMethods(declaringClass, AfterEach.class, false);

        Throwable lastThrown = firstFailure;

        // attempt 1 was the initial proceed() call already consumed above; this loop covers 2..maxRetry+1
        for (int attempt = 2; attempt <= maxRetry + 1; attempt++) {
            lastThrown = null;
            beforeAttempt(instance, testMethod);
            try {
                invokeAll(beforeEachMethods, instance);
                invokeMethod(testMethod, instance);
            } catch (Throwable t) {
                lastThrown = ExceptionUnwrapper.unwrap(t);
            } finally {
                try {
                    invokeAll(afterEachMethods, instance);
                } catch (Throwable t) {
                    if (lastThrown == null) {
                        lastThrown = ExceptionUnwrapper.unwrap(t);
                    }
                } finally {
                    afterAttempt(instance, testMethod, lastThrown);
                }
            }

            if (lastThrown == null) {
                return null; // this attempt passed
            }

            if ((attempt <= maxRetry) && isRetriable(testMethod, lastThrown, maxRetry)) {
                logRetry(testMethod, lastThrown);
            } else {
                break;
            }
        }

        return lastThrown;
    }

    /**
     * Hook invoked before every retry attempt beyond the first (never for the first attempt, which
     * runs through the normal {@code InvocationInterceptor} chain via {@code invocation.proceed()}).
     * Override to run framework-specific setup around each attempt - e.g. Selenium Foundation's
     * subclass calls {@code DriverManager.beforeInvocation(...)} here, since retries beyond the first
     * bypass the normal interceptor chain that would otherwise do so.
     * <p>
     * Default implementation does nothing.
     *
     * @param instance test class instance
     * @param method test method about to be (re-)invoked
     */
    protected void beforeAttempt(final Object instance, final Method method) {
        // no-op by default
    }

    /**
     * Hook invoked after every retry attempt beyond the first, whether it passed or failed. See
     * {@link #beforeAttempt(Object)}.
     * <p>
     * Default implementation does nothing.
     *
     * @param instance test class instance
     * @param method test method that was (re-)invoked
     * @param thrown exception from this attempt; {@code null} if the attempt passed
     */
    protected void afterAttempt(final Object instance, final Method method, final Throwable thrown) {
        // no-op by default
    }

    /**
     * Get the configured maximum retry count for failed tests.
     * <p>
     * <b>NOTE</b>: If the specified method or its declaring class are marked with {@link NoRetry}, this
     * method returns zero.
     * <p>
     * Default implementation reads the {@code jupiter.max.retry} system property; override to source
     * this from a different configuration mechanism.
     *
     * @param context current extension context
     * @param method test method for which retry is being considered
     * @return maximum retry attempts that will be made if the specified method fails
     */
    protected int getMaxRetry(final ExtensionContext context, final Method method) {
        boolean noRetryOnMethod = method.isAnnotationPresent(NoRetry.class);
        boolean noRetryOnClass = method.getDeclaringClass().isAnnotationPresent(NoRetry.class);

        if (noRetryOnMethod || noRetryOnClass) {
            return 0;
        }

        return JupiterConfig.getConfig().getInt(JupiterConfig.JupiterSettings.MAX_RETRY.key());
    }

    /**
     * Determine if the specified failed invocation should be retried.
     *
     * @param method failed test method
     * @param thrown exception for this failed invocation
     * @param maxRetry configured maximum retry count
     * @return {@code true} if invocation should be retried; otherwise {@code false}
     */
    protected boolean isRetriable(final Method method, final Throwable thrown, final int maxRetry) {
        if (maxRetry <= 0) {
            return false;
        }
        synchronized (RetryExtension.class) {
            for (JupiterRetryAnalyzer analyzer : ServiceLoader.load(JupiterRetryAnalyzer.class)) {
                if (analyzer.retry(method, thrown)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void logRetry(final Method method, final Throwable thrown) {
        boolean showDetail = LOGGER.isDebugEnabled()
                || JupiterConfig.getConfig().getBoolean(JupiterConfig.JupiterSettings.RETRY_MORE_INFO.key());
        if (showDetail) {
            LOGGER.warn("### RETRY ### {}", method, thrown);
        } else {
            LOGGER.warn("### RETRY ### {}", method);
        }
    }

    /**
     * Find {@code @BeforeEach}/{@code @AfterEach} methods declared on the specified class and its
     * superclasses, in Jupiter's own documented execution order: superclass-declared methods before
     * subclass-declared for {@code @BeforeEach} (ascending); the reverse for {@code @AfterEach}
     * (descending).
     *
     * @param testClass test class to search, starting point of the hierarchy walk
     * @param annotationType {@code BeforeEach.class} or {@code AfterEach.class}
     * @param ascending {@code true} for superclass-to-subclass order; {@code false} to reverse it
     * @return ordered, accessible lifecycle methods
     */
    private List<Method> findLifecycleMethods(final Class<?> testClass,
            final Class<? extends java.lang.annotation.Annotation> annotationType, final boolean ascending) {
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> c = testClass; (c != null) && (c != Object.class); c = c.getSuperclass()) {
            hierarchy.add(c);
        }
        Collections.reverse(hierarchy); // superclass first

        List<Method> methods = new ArrayList<>();
        for (Class<?> c : hierarchy) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.isAnnotationPresent(annotationType)) {
                    m.setAccessible(true);
                    methods.add(m);
                }
            }
        }

        if (!ascending) {
            Collections.reverse(methods);
        }
        return methods;
    }

    private void invokeAll(final List<Method> methods, final Object instance) throws Throwable {
        for (Method m : methods) {
            invokeMethod(m, instance);
        }
    }

    private void invokeMethod(final Method method, final Object instance) throws Throwable {
        try {
            method.invoke(instance);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
}

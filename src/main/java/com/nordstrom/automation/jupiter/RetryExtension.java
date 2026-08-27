package com.nordstrom.automation.jupiter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
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
 * This extension provides automatic retry of failed {@code @Test} and {@code @TestTemplate}
 * (e.g. {@code @ParameterizedTest}, {@code @RepeatedTest}) method invocations.
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
 * {@link #beforeAttempt(Object, Method)}/{@link #afterAttempt(Object, Method, Throwable)} to run
 * whatever a subclass needs around every attempt, matching the extension pattern already documented by
 * TestNG Foundation's own {@code RetryManager}: subclass, override, and register your subclass instead
 * of this base class.
 * <p>
 * <b>PARAMETERIZED/REPEATED TESTS</b>
 * <p>
 * Plain {@code @Test} methods never carry real invocation parameters in Jupiter - only
 * {@code @TestTemplate}-based methods (e.g. {@code @ParameterizedTest}) do, via
 * {@code invocationContext.getArguments()} - available directly since {@code RetryExtension} is
 * itself an {@code InvocationInterceptor}, unlike {@code ArtifactCollector}'s
 * {@code TestWatcher.testFailed(...)}, which has no such context and genuinely needs
 * {@link ArgumentsCaptor}'s {@code Store}-based hand-off instead. Retrying such a method re-invokes it
 * with the
 * same resolved argument values captured for the original (failed) invocation - not a fresh set from
 * whatever {@code @ArgumentsSource} produced them.
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
 * {@link JupiterRetryAnalyzer} instances registered via {@link ServiceLoader}. Sensitive parameter
 * values can be redacted from retry log messages via {@link RedactValue}.
 *
 * @see JupiterRetryAnalyzer
 * @see NoRetry
 * @see RedactValue
 */
public class RetryExtension implements InvocationInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetryExtension.class);
    private static final Object[] NO_ARGS = new Object[0];

    /**
     * {@inheritDoc}
     */
    @Override
    public void interceptTestMethod(final Invocation<Void> invocation,
            final ReflectiveInvocationContext<Method> invocationContext,
            final ExtensionContext extensionContext) throws Throwable {
        intercept(invocation, invocationContext, extensionContext);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Covers {@code @ParameterizedTest}, {@code @RepeatedTest}, and any other {@code @TestTemplate}
     * method - the only category of test with real invocation parameters to log or redact.
     */
    @Override
    public void interceptTestTemplateMethod(final Invocation<Void> invocation,
            final ReflectiveInvocationContext<Method> invocationContext,
            final ExtensionContext extensionContext) throws Throwable {
        intercept(invocation, invocationContext, extensionContext);
    }

    /**
     * Shared logic for both {@code @Test} and {@code @TestTemplate} methods.
     */
    private void intercept(final Invocation<Void> invocation,
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

            // available directly from invocationContext - no need for ArgumentsCaptor's Store-based
            // hand-off here, unlike ArtifactCollector's TestWatcher.testFailed(...), which has no
            // ReflectiveInvocationContext of its own and genuinely needs that indirection. Empty for a
            // plain @Test method (no parameters); populated for @TestTemplate methods.
            List<Object> arguments = invocationContext.getArguments();
            logRetry(testMethod, arguments, thrown);

            // every attempt from here on is manual reflection - proceed()/skip() must never be called
            // again; the contract is satisfied by the single proceed() call above, regardless of outcome
            Throwable lastThrown = retryLoop(extensionContext, testMethod, arguments, maxRetry);
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
     * @param arguments resolved argument values for this invocation (empty for a plain {@code @Test})
     * @param maxRetry maximum number of retry attempts
     * @return exception from the final attempt; {@code null} if a retry attempt passed
     * @throws Throwable if instance/method access fails outside the retried invocation itself
     */
    private Throwable retryLoop(final ExtensionContext extensionContext, final Method testMethod,
            final List<Object> arguments, final int maxRetry) throws Throwable {

        Object instance = extensionContext.getRequiredTestInstance();
        Class<?> declaringClass = testMethod.getDeclaringClass();
        List<Method> beforeEachMethods = findLifecycleMethods(declaringClass, BeforeEach.class, true);
        List<Method> afterEachMethods = findLifecycleMethods(declaringClass, AfterEach.class, false);
        Object[] args = arguments.toArray();

        Throwable lastThrown = null;

        // attempt 1 was the initial proceed() call already consumed above; this loop covers 2..maxRetry+1
        for (int attempt = 2; attempt <= maxRetry + 1; attempt++) {
            lastThrown = null;
            beforeAttempt(instance, testMethod);
            try {
                invokeAll(beforeEachMethods, instance, NO_ARGS);
                invokeMethod(testMethod, instance, args);
            } catch (Throwable t) {
                lastThrown = ExceptionUnwrapper.unwrap(t);
            } finally {
                try {
                    invokeAll(afterEachMethods, instance, NO_ARGS);
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
                logRetry(testMethod, arguments, lastThrown);
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
     * {@link #beforeAttempt(Object, Method)}.
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

    private void logRetry(final Method method, final List<Object> arguments, final Throwable thrown) {
        boolean showDetail = LOGGER.isDebugEnabled()
                || JupiterConfig.getConfig().getBoolean(JupiterConfig.JupiterSettings.RETRY_MORE_INFO.key());
        String invocation = formatInvocation(method, arguments);
        if (showDetail) {
            LOGGER.warn("### RETRY ### {}", invocation, thrown);
        } else {
            LOGGER.warn("### RETRY ### {}", invocation);
        }
    }

    /**
     * Format a method invocation as {@code className.methodName(parmValue...)}, redacting any
     * parameter marked with {@link RedactValue} - matching TestNG Foundation's own
     * {@code InvocationRecord.toString()} format and placeholder convention exactly.
     * <p>
     * Package-private (not {@code private}) specifically so tests in this package can verify
     * redaction behavior directly, rather than only indirectly through captured log output.
     *
     * @param method invoked method
     * @param arguments resolved argument values, in declared-parameter order
     * @return formatted invocation string
     */
    String formatInvocation(final Method method, final List<Object> arguments) {
        StringBuilder builder = new StringBuilder(getQualifiedName(method)).append('(');

        if (!arguments.isEmpty()) {
            Parameter[] params = method.getParameters();
            for (int i = 0; i < arguments.size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                if ((i < params.length) && params[i].isAnnotationPresent(RedactValue.class)) {
                    builder.append("|:arg").append(i).append(":|");
                } else {
                    builder.append(arguments.get(i));
                }
            }
        }

        return builder.append(')').toString();
    }

    /**
     * Get {@code className.methodName} for the specified method - matches
     * {@code InvocationRecord.getQualifiedName} exactly.
     *
     * @param method method object
     * @return qualified name string for the specified method object
     */
    private static String getQualifiedName(final Method method) {
        String methodSig = method.toString();
        int endIndex = methodSig.lastIndexOf('(');
        int midIndex = methodSig.lastIndexOf('.', endIndex - 1);
        int beginIndex = methodSig.lastIndexOf('.', midIndex - 1) + 1;
        return methodSig.substring(beginIndex, endIndex);
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

    private void invokeAll(final List<Method> methods, final Object instance, final Object[] args)
            throws Throwable {
        for (Method m : methods) {
            invokeMethod(m, instance, args);
        }
    }

    private void invokeMethod(final Method method, final Object instance, final Object[] args)
            throws Throwable {
        try {
            method.invoke(instance, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
}

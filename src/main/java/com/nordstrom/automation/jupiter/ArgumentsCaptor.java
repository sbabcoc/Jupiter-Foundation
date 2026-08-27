package com.nordstrom.automation.jupiter;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

/**
 * This extension captures the resolved argument values of {@code @TestTemplate} invocations (which
 * includes {@code @ParameterizedTest} and {@code @RepeatedTest}), making them available to any other
 * extension that needs to distinguish between invocations of the same method — e.g. for artifact-naming
 * purposes, the role {@code ArtifactParams} played for the various JUnit 4 parameterized-test runners in
 * {@code JUnit-Foundation}.
 * <p>
 * Unlike {@code ArtifactParams}, this requires no per-runner reflective extraction: every
 * {@code @ArgumentsSource} implementation (built-in or custom) funnels through the same
 * {@code TestTemplateInvocationContext} → reflective-invocation pipeline before this interceptor ever
 * sees it, so one implementation covers all of them uniformly.
 * <p>
 * <b>Registration</b>: this class has no dependency on any Selenium Foundation (or other) base class —
 * register it via JUnit Platform's automatic extension detection
 * ({@code META-INF/services/org.junit.jupiter.api.extension.Extension}) rather than
 * {@code @RegisterExtension}, so it applies uniformly to any project depending on Jupiter Foundation.
 * <p>
 * <b>WARNING - this fails silently if forgotten, not loudly:</b> automatic extension detection is
 * <b>OFF by default</b> in JUnit Platform. The
 * {@code META-INF/services/org.junit.jupiter.api.extension.Extension} entry alone does nothing unless
 * the consuming project ALSO sets {@code junit.jupiter.extensions.autodetection.enabled=true} (in its
 * own {@code junit-platform.properties}, or as a system property). Forget either piece and
 * {@code ArgumentsCaptor} simply never runs - no error, no log message - {@link #getArguments} just
 * returns an empty list every time, exactly as documented for a plain {@code @Test} with no
 * parameters. Anything consuming that empty list where real arguments were expected (e.g. reflectively
 * re-invoking a {@code @ParameterizedTest} method during a retry) fails downstream with a confusing
 * "wrong number of arguments" error that gives no hint the actual cause is a missing configuration
 * property two files away. Verify both pieces are in place before trusting this class is active.
 */
public class ArgumentsCaptor implements InvocationInterceptor {

    private static final Namespace NAMESPACE = Namespace.create(ArgumentsCaptor.class);
    private static final String ARGUMENTS_KEY = "arguments";

    /**
     * {@inheritDoc}
     */
    @Override
    public void interceptTestTemplateMethod(final Invocation<Void> invocation,
            final ReflectiveInvocationContext<Method> invocationContext,
            final ExtensionContext extensionContext) throws Throwable {
        extensionContext.getStore(NAMESPACE).put(ARGUMENTS_KEY, invocationContext.getArguments());
        invocation.proceed();
    }

    /**
     * Get the resolved argument values for the current {@code @TestTemplate} invocation, if any were
     * captured.
     *
     * @param context current extension context
     * @return resolved argument values; empty list if the current invocation isn't a
     * {@code @TestTemplate} method (e.g. a plain {@code @Test}), or if no invocation is in progress
     */
    @SuppressWarnings("unchecked")
    public static List<Object> getArguments(final ExtensionContext context) {
        List<Object> arguments = (List<Object>) context.getStore(NAMESPACE).get(ARGUMENTS_KEY);
        return (arguments != null) ? arguments : Collections.emptyList();
    }
}

package com.nordstrom.automation.jupiter;

import java.lang.reflect.Method;

import com.nordstrom.automation.jupiter.fixtures.RetryAnalyzerVetoFixture;

/**
 * Test-only {@link JupiterRetryAnalyzer}, registered via
 * {@code META-INF/services/com.nordstrom.automation.jupiter.JupiterRetryAnalyzer}.
 * <p>
 * Approves retry for every method EXCEPT {@link RetryAnalyzerVetoFixture}'s, which it specifically
 * vetoes. Both behaviors live in one analyzer, not two competing ones, because
 * {@code isRetriable(...)} approves a retry if ANY registered analyzer says yes - two analyzers
 * registered simultaneously (one always-approve, one always-reject) couldn't actually demonstrate a
 * veto, since the approving one's "yes" would win regardless of what the rejecting one says. This way,
 * the same analyzer both enables retry for the other fixtures in this test module (which have no
 * built-in "any exception is retriable" default of their own - approval is entirely delegated to
 * registered analyzers, matching {@code RetryManager}'s real TestNG behavior exactly) and proves the
 * veto path works for the one fixture that specifically needs it vetoed.
 */
public class TestScopeRetryAnalyzer implements JupiterRetryAnalyzer {
    @Override
    public boolean retry(final Method method, final Throwable thrown) {
        return method.getDeclaringClass() != RetryAnalyzerVetoFixture.class;
    }
}

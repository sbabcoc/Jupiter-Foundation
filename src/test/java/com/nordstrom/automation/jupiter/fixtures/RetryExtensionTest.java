package com.nordstrom.automation.jupiter.fixtures;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.testkit.engine.EventConditions.event;
import static org.junit.platform.testkit.engine.EventConditions.finishedSuccessfully;
import static org.junit.platform.testkit.engine.EventConditions.finishedWithFailure;
import static org.junit.platform.testkit.engine.EventConditions.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

import com.nordstrom.automation.jupiter.RetryExtension;
import com.nordstrom.automation.jupiter.JupiterConfig.JupiterSettings;

/**
 * Integration tests for {@link RetryExtension}, run via the JUnit Platform Test Kit against small
 * fixture classes - the Jupiter-native equivalent of how JUnit-Foundation's own
 * {@code AutomaticRetryTest} drives its fixture classes through {@code JUnitCore}.
 */
class RetryExtensionTest {

    private static final String MAX_RETRY_KEY = JupiterSettings.MAX_RETRY.key();

    @BeforeEach
    void resetState() {
        System.clearProperty(MAX_RETRY_KEY);
        RetryUntilPassFixture.ATTEMPTS.set(0);
        RetryExhaustedFixture.ATTEMPTS.set(0);
        NoRetryMethodFixture.ATTEMPTS.set(0);
        BeforeEachReinvocationFixture.BEFORE_EACH_COUNT.set(0);
        BeforeEachReinvocationFixture.TEST_COUNT.set(0);
        ParameterizedRetryFixture.SEEN_VALUES.clear();
        RetryAnalyzerVetoFixture.ATTEMPTS.set(0);
    }

    @AfterEach
    void clearProperty() {
        System.clearProperty(MAX_RETRY_KEY);
    }

    @Test
    void retriesUntilPass() {
        System.setProperty(MAX_RETRY_KEY, "2");

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(RetryUntilPassFixture.class))
                .execute()
                .testEvents()
                .assertThatEvents()
                .haveExactly(1, event(test("testPassesOnThirdAttempt"), finishedSuccessfully()));

        Assertions.assertEquals(3, RetryUntilPassFixture.ATTEMPTS.get(),
                "should have run once, then retried twice, before passing");
    }

    @Test
    void exhaustsRetriesAndFails() {
        System.setProperty(MAX_RETRY_KEY, "2");

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(RetryExhaustedFixture.class))
                .execute()
                .testEvents()
                .assertThatEvents()
                .haveExactly(1, event(test("testAlwaysFails"), finishedWithFailure()));

        Assertions.assertEquals(3, RetryExhaustedFixture.ATTEMPTS.get(),
                "should have run the original attempt plus both retries, then given up");
    }

    @Test
    void noRetryAnnotationSuppressesRetry() {
        System.setProperty(MAX_RETRY_KEY, "2");

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(NoRetryMethodFixture.class))
                .execute()
                .testEvents()
                .assertThatEvents()
                .haveExactly(1, event(test("testNeverRetried"), finishedWithFailure()));

        Assertions.assertEquals(1, NoRetryMethodFixture.ATTEMPTS.get(),
                "@NoRetry should suppress retry even though MAX_RETRY > 0");
    }

    @Test
    void defaultMaxRetryIsZero() {
        // MAX_RETRY left unset - defaults to "0" per JupiterSettings.MAX_RETRY

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(RetryExhaustedFixture.class))
                .execute()
                .testEvents()
                .assertThatEvents()
                .haveExactly(1, event(test("testAlwaysFails"), finishedWithFailure()));

        Assertions.assertEquals(1, RetryExhaustedFixture.ATTEMPTS.get(),
                "with no MAX_RETRY configured, no retry should occur at all");
    }

    @Test
    void beforeEachReinvokedOnEveryRetryAttempt() {
        System.setProperty(MAX_RETRY_KEY, "2");

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(BeforeEachReinvocationFixture.class))
                .execute()
                .testEvents()
                .assertThatEvents()
                .haveExactly(1, event(test("testPassesOnThirdAttempt"), finishedSuccessfully()));

        Assertions.assertEquals(3, BeforeEachReinvocationFixture.TEST_COUNT.get());
        Assertions.assertEquals(BeforeEachReinvocationFixture.TEST_COUNT.get(),
                BeforeEachReinvocationFixture.BEFORE_EACH_COUNT.get(),
                "@BeforeEach should run exactly once per attempt, including retries");
    }

    @Test
    void parameterizedTestRetriesWithSameArgument() {
        System.setProperty(MAX_RETRY_KEY, "1");

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(ParameterizedRetryFixture.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.succeeded(3).failed(0));

        Assertions.assertEquals(java.util.Arrays.asList(10, 10, 20, 20, 30, 30),
                ParameterizedRetryFixture.SEEN_VALUES,
                "each invocation's retry should see the SAME argument value as its original attempt");
    }

    @Test
    void analyzerCanVetoRetryForSpecificFixture() {
        System.setProperty(MAX_RETRY_KEY, "2");

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(RetryAnalyzerVetoFixture.class))
                .execute()
                .testEvents()
                .assertThatEvents()
                .haveExactly(1, event(test("testVetoedByAnalyzer"), finishedWithFailure()));

        Assertions.assertEquals(1, RetryAnalyzerVetoFixture.ATTEMPTS.get(),
                "TestScopeRetryAnalyzer should veto retry for this fixture despite MAX_RETRY > 0");
    }
}

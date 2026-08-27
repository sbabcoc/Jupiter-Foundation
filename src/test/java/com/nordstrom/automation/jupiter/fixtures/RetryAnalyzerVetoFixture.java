package com.nordstrom.automation.jupiter.fixtures;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.nordstrom.automation.jupiter.RetryExtension;

/**
 * Always fails. {@code TestScopeRetryAnalyzer} specifically vetoes retry for this class - should run
 * exactly once despite MAX_RETRY > 0, proving a {@code JupiterRetryAnalyzer} can override the basic
 * exception-was-thrown default.
 */
@ExtendWith(RetryExtension.class)
public class RetryAnalyzerVetoFixture {

    public static final AtomicInteger ATTEMPTS = new AtomicInteger(0);

    @Test
    public void testVetoedByAnalyzer() {
        ATTEMPTS.incrementAndGet();
        Assertions.fail("This test always fails");
    }
}

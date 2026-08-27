package com.nordstrom.automation.jupiter.fixtures;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.nordstrom.automation.jupiter.RetryExtension;

/**
 * Verifies that {@code @BeforeEach} is genuinely re-invoked on every retry attempt (matching
 * RetryHandler's full before+test+after reconstruction), not just run once at the start.
 */
@ExtendWith(RetryExtension.class)
public class BeforeEachReinvocationFixture {

    public static final AtomicInteger BEFORE_EACH_COUNT = new AtomicInteger(0);
    public static final AtomicInteger TEST_COUNT = new AtomicInteger(0);

    @BeforeEach
    public void setUp() {
        BEFORE_EACH_COUNT.incrementAndGet();
    }

    @Test
    public void testPassesOnThirdAttempt() {
        int attempt = TEST_COUNT.incrementAndGet();
        Assertions.assertTrue(attempt >= 3, "Deliberate failure on attempt " + attempt);
    }
}

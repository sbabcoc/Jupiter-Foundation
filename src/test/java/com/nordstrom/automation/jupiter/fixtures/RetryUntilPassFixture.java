package com.nordstrom.automation.jupiter.fixtures;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.nordstrom.automation.jupiter.RetryExtension;

/**
 * Fails on attempts 1 and 2; passes on attempt 3. With MAX_RETRY=2, the overall test should be
 * reported as passed, having actually run 3 times.
 */
@ExtendWith(RetryExtension.class)
public class RetryUntilPassFixture {

    public static final AtomicInteger ATTEMPTS = new AtomicInteger(0);

    @Test
    public void testPassesOnThirdAttempt() {
        int attempt = ATTEMPTS.incrementAndGet();
        Assertions.assertTrue(attempt >= 3, "Deliberate failure on attempt " + attempt);
    }
}

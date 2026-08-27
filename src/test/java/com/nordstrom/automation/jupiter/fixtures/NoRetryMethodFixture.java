package com.nordstrom.automation.jupiter.fixtures;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.nordstrom.automation.jupiter.NoRetry;
import com.nordstrom.automation.jupiter.RetryExtension;

/**
 * Always fails, but marked {@code @NoRetry} - should run exactly once regardless of MAX_RETRY.
 */
@ExtendWith(RetryExtension.class)
public class NoRetryMethodFixture {

    public static final AtomicInteger ATTEMPTS = new AtomicInteger(0);

    @Test
    @NoRetry
    public void testNeverRetried() {
        ATTEMPTS.incrementAndGet();
        Assertions.fail("This test always fails");
    }
}

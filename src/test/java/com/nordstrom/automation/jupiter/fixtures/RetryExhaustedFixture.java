package com.nordstrom.automation.jupiter.fixtures;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.nordstrom.automation.jupiter.RetryExtension;

/**
 * Always fails. With MAX_RETRY=2, should run exactly 3 times (1 original + 2 retries) and ultimately
 * be reported as failed.
 */
@ExtendWith(RetryExtension.class)
public class RetryExhaustedFixture {

    public static final AtomicInteger ATTEMPTS = new AtomicInteger(0);

    @Test
    public void testAlwaysFails() {
        ATTEMPTS.incrementAndGet();
        Assertions.fail("This test always fails");
    }
}

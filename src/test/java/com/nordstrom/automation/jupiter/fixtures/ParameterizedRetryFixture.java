package com.nordstrom.automation.jupiter.fixtures;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.nordstrom.automation.jupiter.RetryExtension;

/**
 * Verifies that retrying a {@code @ParameterizedTest} invocation reuses the SAME resolved argument
 * value on retry - not a fresh draw from the {@code @ArgumentsSource} - and that
 * {@code ArgumentsCaptor} correctly captures per-invocation arguments even under retry.
 * <p>
 * Each of the 3 invocations fails on its first appearance of its value and passes on the second
 * (i.e. the retry) - recording every value seen, in order, into {@link #SEEN_VALUES}.
 */
@ExtendWith(RetryExtension.class)
public class ParameterizedRetryFixture {

    public static final List<Integer> SEEN_VALUES = new CopyOnWriteArrayList<>();

    @ParameterizedTest
    @ValueSource(ints = {10, 20, 30})
    public void testRetriesWithSameArgument(int value) {
        SEEN_VALUES.add(value);
        long timesSeen = Collections.frequency(SEEN_VALUES, value);
        Assertions.assertTrue(timesSeen >= 2, "Deliberate failure on first appearance of " + value);
    }
}

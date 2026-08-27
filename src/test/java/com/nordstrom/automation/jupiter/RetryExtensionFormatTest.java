package com.nordstrom.automation.jupiter;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Direct unit tests for {@link RetryExtension#formatInvocation(Method, java.util.List)} - verifies the
 * {@code className.methodName(parmValue...)} format and {@link RedactValue} placeholder behavior
 * without needing a full engine run, now that the method is package-private specifically for this.
 */
class RetryExtensionFormatTest {

    private final RetryExtension extension = new RetryExtension();

    @Test
    void formatsMethodWithNoArguments() throws Exception {
        Method method = RetryFormatFixture.class.getDeclaredMethod("noArgs");
        String result = extension.formatInvocation(method, java.util.Collections.emptyList());
        Assertions.assertEquals("RetryFormatFixture.noArgs()", result);
    }

    @Test
    void formatsUnredactedArgumentAsIs() throws Exception {
        Method method = RetryFormatFixture.class.getDeclaredMethod("oneArg", String.class);
        String result = extension.formatInvocation(method, Arrays.asList("john.doe"));
        Assertions.assertEquals("RetryFormatFixture.oneArg(john.doe)", result);
    }

    @Test
    void redactsMarkedArgumentOnly() throws Exception {
        Method method = RetryFormatFixture.class.getDeclaredMethod("withRedacted", String.class, String.class);
        String result = extension.formatInvocation(method, Arrays.asList("john.doe", "secret123"));
        Assertions.assertEquals("RetryFormatFixture.withRedacted(john.doe, |:arg1:|)", result,
                "username should appear as-is; password (arg index 1) should be redacted");
    }
}

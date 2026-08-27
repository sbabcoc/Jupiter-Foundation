package com.nordstrom.automation.jupiter;

/**
 * Reflection target for {@link RetryExtensionFormatTest} only - never actually run as a test.
 * <p>
 * <b>NOTE</b>: Deliberately a top-level class, not nested - {@code getQualifiedName}'s dot-splitting
 * treats a nested class's {@code Outer$Inner} separator as one unsplit token (it's '$', not '.'),
 * which would produce {@code "RetryExtensionFormatTest$RetryFormatFixture.methodName"} instead of the
 * clean {@code "RetryFormatFixture.methodName"} a top-level class gives.
 */
class RetryFormatFixture {
    void noArgs() { }
    void oneArg(String username) { }
    void withRedacted(String username, @RedactValue String password) { }
}

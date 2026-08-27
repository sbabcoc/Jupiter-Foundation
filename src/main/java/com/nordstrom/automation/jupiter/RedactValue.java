package com.nordstrom.automation.jupiter;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Use this annotation to mark test method arguments whose values should be redacted in log output:
 * 
 * <blockquote><pre>
 * &#64;ParameterizedTest
 * &#64;CsvSource({"john.doe, secret123"})
 * public void testLogin(String username, &#64;RedactValue String password) {
 *     // test implementation goes here
 * }</pre></blockquote>
 */
@Retention(RUNTIME)
@Target(PARAMETER)
@Inherited
public @interface RedactValue { }

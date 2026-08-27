package com.nordstrom.automation.jupiter;

import java.lang.reflect.Method;
import java.util.ServiceLoader;

/**
 * Managed retry analyzers implement this interface, activated via the {@link ServiceLoader} through
 * entries in a file named:
 * <blockquote>{@code META-INF/services/com.nordstrom.automation.jupiter.JupiterRetryAnalyzer}</blockquote>
 * <p>
 * Multiple analyzers may be active simultaneously; {@link RetryExtension} retries the failed invocation
 * if <b>any</b> registered analyzer approves - the same stacking behavior as
 * {@code TestNGRetryAnalyzer}/{@code JUnitRetryAnalyzer} in the sibling foundation libraries.
 * 
 * @see RetryExtension
 * @see NoRetry
 */
public interface JupiterRetryAnalyzer {

    /**
     * Determine if the specified failed invocation should be retried.
     * 
     * @param method failed test method
     * @param thrown exception for this failed invocation (already unwrapped via
     * {@link com.nordstrom.common.base.ExceptionUnwrapper})
     * @return {@code true} if invocation should be retried; otherwise {@code false}
     */
    boolean retry(Method method, Throwable thrown);
}

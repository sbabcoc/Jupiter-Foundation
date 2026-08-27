package com.nordstrom.automation.jupiter;

import java.io.IOException;

import org.apache.commons.configuration2.ex.ConfigurationException;

import com.nordstrom.automation.settings.SettingsCore;
import com.nordstrom.common.base.UncheckedThrow;

/**
 * This class declares the settings and methods related to Jupiter Foundation configuration.
 * <p>
 * <b>NOTE</b>: Unlike TestNG Foundation's {@code TestNGConfig}, there is no {@code RETRY_ANALYZER}
 * setting here - TestNG's engine instantiates its retry analyzer by reflecting a class name out of
 * config, since TestNG has no equivalent to Jupiter's {@code @RegisterExtension} field declaration.
 * Selecting {@link RetryExtension} versus a subclass (e.g. Selenium Foundation's own) is just ordinary
 * Java - whichever one a test class's {@code @RegisterExtension} field actually declares - so there's
 * nothing for a config setting to select here.
 *
 * @see JupiterSettings
 */
public class JupiterConfig extends SettingsCore<JupiterConfig.JupiterSettings> {

    private static final String SETTINGS_FILE = "jupiter.properties";

    /**
     * This enumeration declares the settings that enable you to control the parameters used by
     * <b>Jupiter Foundation</b>.
     * <p>
     * Each setting is defined by a constant name and System property key. Many settings also define
     * default values. Note that all of these settings can be overridden via the
     * {@code jupiter.properties} file and System property declarations.
     */
    public enum JupiterSettings implements SettingsCore.SettingsAPI {
        /**
         * This setting specifies the maximum number of times a failed test method will be retried.
         * <p>
         * name: <b>jupiter.max.retry</b><br>
         * default: <b>0</b>
         */
        MAX_RETRY("jupiter.max.retry", "0"),

        /**
         * This setting specifies whether the exception that caused a test to fail will be logged in
         * the notification that the test is being retried.
         * <p>
         * name: <b>jupiter.retry.more.info</b><br>
         * default: {@code false}
         */
        RETRY_MORE_INFO("jupiter.retry.more.info", "false");

        private String propertyName;
        private String defaultValue;

        JupiterSettings(String propertyName, String defaultValue) {
            this.propertyName = propertyName;
            this.defaultValue = defaultValue;
        }

        @Override
        public String key() {
            return propertyName;
        }

        @Override
        public String val() {
            return defaultValue;
        }
    }

    private static final ThreadLocal<JupiterConfig> jupiterConfig = new InheritableThreadLocal<JupiterConfig>() {
        @Override
        protected JupiterConfig initialValue() {
            try {
                return new JupiterConfig();
            } catch (ConfigurationException | IOException e) {
                throw UncheckedThrow.throwUnchecked(e);
            }
        }
    };

    /**
     * Instantiate a <b>Jupiter Foundation</b> configuration object.
     *
     * @throws ConfigurationException If a failure is encountered while initializing this configuration object.
     * @throws IOException If a failure is encountered while reading from a configuration input stream.
     */
    public JupiterConfig() throws ConfigurationException, IOException {
        super(JupiterSettings.class);
    }

    /**
     * Get the Jupiter configuration object for the current context.
     * <p>
     * <b>NOTE</b>: Unlike {@code TestNGConfig}, which caches per-{@code ITestResult} (since its values
     * could in principle vary per test), this is a single thread-scoped instance - {@code MAX_RETRY}/
     * {@code RETRY_MORE_INFO} are run-wide constants, not per-invocation state, so no
     * {@code ExtensionContext}-keyed caching is needed the way it was for driver storage elsewhere in
     * this library.
     *
     * @return Jupiter configuration object
     */
    public static JupiterConfig getConfig() {
        return jupiterConfig.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSettingsPath() {
        return SETTINGS_FILE;
    }
}

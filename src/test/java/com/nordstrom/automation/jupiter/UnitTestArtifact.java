package com.nordstrom.automation.jupiter;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test double for {@link ArtifactCollectorTest} - matches the shape of TestNG Foundation's own
 * {@code UnitTestArtifact}: static flags toggle capture behavior (disabled entirely, or "able but
 * fails"), reset before each outer test.
 */
public class UnitTestArtifact extends ArtifactType {

    private static final String EXTENSION = "txt";
    private static final String ARTIFACT = "This text artifact was captured for '%s'";
    private static final Logger LOGGER = LoggerFactory.getLogger(UnitTestArtifact.class);

    private static volatile boolean captureDisabled;
    private static volatile boolean captureCrippled;

    public static void reset() {
        captureDisabled = false;
        captureCrippled = false;
    }

    public static void disableCapture() {
        captureDisabled = true;
    }

    public static void crippleCapture() {
        captureCrippled = true;
    }

    @Override
    public Logger getLogger() {
        return LOGGER;
    }

    @Override
    public boolean canGetArtifact(final Object instance) {
        return !captureDisabled;
    }

    @Override
    public byte[] getArtifact(final Object instance, final Throwable reason) {
        if (captureCrippled) {
            return new byte[0];
        }
        return String.format(ARTIFACT, instance.getClass().getSimpleName()).getBytes();
    }

    @Override
    public Path getArtifactPath(final Object instance) {
        // distinct subfolder, matching the pattern PageSourceArtifact/ScreenshotArtifact use, so this
        // test double's output lands somewhere unambiguous and separate from any real usage
        return super.getArtifactPath(instance).resolve("unit-test");
    }

    @Override
    public String getArtifactExtension() {
        return EXTENSION;
    }
}

package com.nordstrom.automation.jupiter;

import java.nio.file.Path;

import org.slf4j.Logger;

import com.nordstrom.common.file.PathUtils;

/**
 * This interface defines the contract fulfilled by artifact capture providers for Jupiter Foundation's
 * {@link ArtifactCollector}. Identical in shape to JUnit-Foundation's and TestNG-Foundation's own
 * {@code ArtifactType} interfaces, defined independently here rather than depended-on cross-library,
 * matching the precedent those two already set of being independent peers rather than layered on
 * one another.
 */
public abstract class ArtifactType {

    /**
     * Get the SLF4J {@link Logger} for this artifact type.
     *
     * @return logger for this artifact (may be {@code null})
     */
    public Logger getLogger() {
        return null;
    }

    /**
     * Determine if artifact capture is available in the specified context.
     *
     * @param instance test class instance
     * @return {@code true} if capture is available; otherwise {@code false}
     */
    public abstract boolean canGetArtifact(Object instance);

    /**
     * Capture an artifact from the specified context.
     *
     * @param instance test class instance
     * @param reason impetus for capture request; may be {@code null}
     * @return byte array containing the captured artifact; if capture fails, an empty array is returned
     */
    public abstract byte[] getArtifact(Object instance, Throwable reason);

    /**
     * Get the path at which artifacts of this type should be stored.
     * <p>
     * <b>NOTE</b>: The returned path can be either relative or absolute.
     *
     * @param instance test class instance
     * @return artifact storage path
     */
    public Path getArtifactPath(Object instance) {
        return PathUtils.ReportsDirectory.getPathForObject(instance);
    }

    /**
     * Get the extension for artifact files of this type.
     *
     * @return artifact file extension
     */
    public abstract String getArtifactExtension();
}

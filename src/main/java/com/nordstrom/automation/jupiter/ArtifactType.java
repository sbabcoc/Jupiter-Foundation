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

    public Logger getLogger() {
        return null;
    }

    public abstract boolean canGetArtifact(Object instance);

    public abstract byte[] getArtifact(Object instance, Throwable reason);

    public Path getArtifactPath(Object instance) {
        return PathUtils.ReportsDirectory.getPathForObject(instance);
    }

    public abstract String getArtifactExtension();
}

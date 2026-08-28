package com.nordstrom.automation.jupiter.fixtures;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.nordstrom.automation.jupiter.ArtifactCollector;
import com.nordstrom.automation.jupiter.UnitTestArtifact;

/**
 * Fails - testFailed(...) fires, and the provider is fully able to capture, so an artifact should
 * land on disk automatically.
 */
public class ArtifactCollectorFailFixture {

    @RegisterExtension
    final ArtifactCollector<UnitTestArtifact> collector = new ArtifactCollector<>(new UnitTestArtifact());

    @Test
    public void testCollectorFail() {
        Assertions.fail("deliberate failure to trigger capture");
    }
}

package com.nordstrom.automation.jupiter.fixtures;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.nordstrom.automation.jupiter.ArtifactCollector;
import com.nordstrom.automation.jupiter.UnitTestArtifact;

/**
 * Passes - testFailed(...) never fires, so no artifact should be captured.
 */
public class ArtifactCollectorPassFixture {

    @RegisterExtension
    final ArtifactCollector<UnitTestArtifact> collector = new ArtifactCollector<>(new UnitTestArtifact());

    @Test
    public void testCollectorPass() {
        Assertions.assertTrue(true);
    }
}

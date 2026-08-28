package com.nordstrom.automation.jupiter.fixtures;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.nordstrom.automation.jupiter.ArtifactCollector;
import com.nordstrom.automation.jupiter.UnitTestArtifact;

/**
 * Fails, and the provider says it CAN capture (canGetArtifact true) but the actual capture attempt
 * comes back empty (getArtifact returns a zero-length array) - no artifact should be written, since
 * ArtifactCollector treats an empty result the same as "nothing to save".
 */
public class ArtifactCollectorCaptureFailedFixture {

    @RegisterExtension
    final ArtifactCollector<UnitTestArtifact> collector = new ArtifactCollector<>(new UnitTestArtifact());

    @Test
    public void testCollectorCaptureFailed() {
        UnitTestArtifact.crippleCapture();
        Assertions.fail("deliberate failure - capture attempt should come back empty");
    }
}

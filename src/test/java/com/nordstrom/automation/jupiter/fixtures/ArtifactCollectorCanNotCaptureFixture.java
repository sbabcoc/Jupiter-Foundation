package com.nordstrom.automation.jupiter.fixtures;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.nordstrom.automation.jupiter.ArtifactCollector;
import com.nordstrom.automation.jupiter.UnitTestArtifact;

/**
 * Fails, but the provider reports it CAN'T capture at all (canGetArtifact returns false) - no
 * artifact should be written despite the failure.
 */
public class ArtifactCollectorCanNotCaptureFixture {

    @RegisterExtension
    final ArtifactCollector<UnitTestArtifact> collector = new ArtifactCollector<>(new UnitTestArtifact());

    @Test
    public void testCollectorCanNotCapture() {
        UnitTestArtifact.disableCapture();
        Assertions.fail("deliberate failure - capture should be skipped entirely");
    }
}

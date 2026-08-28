package com.nordstrom.automation.jupiter.fixtures;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.nordstrom.automation.jupiter.ArtifactCollector;
import com.nordstrom.automation.jupiter.UnitTestArtifact;

/**
 * Passes, but explicitly calls captureArtifact(...) directly - matching JUnit-Foundation's own
 * ArtifactCollectorOnDemand.testOnDemandCapture(), proving capture works independent of failure.
 * <p>
 * Ordinary {@code @Test} methods don't get ExtensionContext injected directly, so a small
 * {@code BeforeEachCallback} lambda - a real, correctly-registered extension, unlike a bare
 * {@code implements} that Jupiter would never actually activate - stashes it for the test to use.
 */
public class ArtifactCollectorOnDemandFixture {

    @RegisterExtension
    final ArtifactCollector<UnitTestArtifact> collector = new ArtifactCollector<>(new UnitTestArtifact());

    private ExtensionContext context;

    @RegisterExtension
    final BeforeEachCallback contextCapture = ctx -> this.context = ctx;

    @Test
    public void testOnDemandCapture() {
        collector.captureArtifact(context, null);
        Assertions.assertTrue(true);
    }
}

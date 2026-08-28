package com.nordstrom.automation.jupiter.fixtures;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.nordstrom.automation.jupiter.ArtifactCollector;
import com.nordstrom.automation.jupiter.UnitTestArtifact;

/**
 * Each of two parameterized invocations fails - verifies ArgumentsCaptor integration produces a
 * DIFFERENT hash-suffixed filename per invocation, not one shared/colliding name.
 */
public class ArtifactCollectorParameterizedFixture {

    @RegisterExtension
    final ArtifactCollector<UnitTestArtifact> collector = new ArtifactCollector<>(new UnitTestArtifact());

    @ParameterizedTest
    @ValueSource(ints = {10, 20})
    public void testCollectorParameterized(final int value) {
        Assertions.fail("deliberate failure for value " + value);
    }
}

package com.nordstrom.automation.jupiter;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.testkit.engine.EventConditions.event;
import static org.junit.platform.testkit.engine.EventConditions.finishedSuccessfully;
import static org.junit.platform.testkit.engine.EventConditions.finishedWithFailure;
import static org.junit.platform.testkit.engine.EventConditions.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

import com.nordstrom.automation.jupiter.fixtures.ArtifactCollectorCanNotCaptureFixture;
import com.nordstrom.automation.jupiter.fixtures.ArtifactCollectorCaptureFailedFixture;
import com.nordstrom.automation.jupiter.fixtures.ArtifactCollectorFailFixture;
import com.nordstrom.automation.jupiter.fixtures.ArtifactCollectorOnDemandFixture;
import com.nordstrom.automation.jupiter.fixtures.ArtifactCollectorParameterizedFixture;
import com.nordstrom.automation.jupiter.fixtures.ArtifactCollectorPassFixture;
import com.nordstrom.common.file.PathUtils;

/**
 * Integration tests for {@link ArtifactCollector}, run via the JUnit Platform Test Kit against small
 * fixture classes - mirroring {@code RetryExtensionTest}'s approach.
 * <p>
 * <b>NOTE on verification strategy</b>: {@code TestWatcher.testFailed(...)} fires AFTER a fixture's own
 * {@code @AfterEach} processing completes, so a fixture can't reliably self-report "was I captured" via
 * its own {@code @AfterEach} - by the time that runs, the watcher hasn't fired yet. Verification here
 * instead reads the actual filesystem after {@code EngineTestKit.execute(...)} fully returns, which is
 * only after the fixture's entire isolated lifecycle - watcher included - has completed.
 * <p>
 * <b>NOTE on location</b>: {@code PathUtils.ReportsDirectory} is Maven-convention-based even in this
 * Gradle project - artifacts land under {@code <baseDir>/target/artifact-capture/unit-test/} (the
 * fallback ".*" pattern, since none of these fixture class names match the SureFire/FailSafe naming
 * conventions), not under {@code build-j8}/{@code build-j17}. Already covered by the inherited
 * {@code .gitignore}'s {@code target} entry.
 */
class ArtifactCollectorTest {

    private static final Path COLLECTION_DIR =
            Paths.get(PathUtils.getBaseDir(), "target", "artifact-capture", "unit-test");

    @BeforeEach
    void resetState() throws IOException {
        UnitTestArtifact.reset();
        deleteRecursively(COLLECTION_DIR);
    }

    @AfterEach
    void cleanUp() throws IOException {
        deleteRecursively(COLLECTION_DIR);
    }

    private static void deleteRecursively(final Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static List<Path> capturedFiles() throws IOException {
        if (!Files.exists(COLLECTION_DIR)) {
            return Collections.emptyList();
        }
        try (Stream<Path> list = Files.list(COLLECTION_DIR)) {
            return list.collect(Collectors.toList());
        }
    }

    @Test
    void noCaptureOnPass() throws IOException {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(ArtifactCollectorPassFixture.class))
                .execute()
                .testEvents()
                .assertThatEvents()
                .haveExactly(1, event(test("testCollectorPass"), finishedSuccessfully()));

        Assertions.assertTrue(capturedFiles().isEmpty(),
                "no artifact should be captured when the test passes");
    }

    @Test
    void capturesOnFailure() throws IOException {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(ArtifactCollectorFailFixture.class))
                .execute()
                .testEvents()
                .assertThatEvents()
                .haveExactly(1, event(test("testCollectorFail"), finishedWithFailure()));

        List<Path> files = capturedFiles();
        Assertions.assertEquals(1, files.size(), "exactly one artifact should be captured");

        String fileName = files.get(0).getFileName().toString();
        Assertions.assertTrue(fileName.startsWith("testCollectorFail") && fileName.endsWith(".txt"),
                "unexpected filename: " + fileName
                        + " (not asserting PathUtils.getNextPath()'s exact sequence-number format here"
                        + " - that's an internal detail of a dependency, not this test's concern)");

        String content = new String(Files.readAllBytes(files.get(0)), StandardCharsets.UTF_8);
        Assertions.assertEquals("This text artifact was captured for 'ArtifactCollectorFailFixture'",
                content);
    }

    @Test
    void noCaptureWhenCanNotCapture() throws IOException {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(ArtifactCollectorCanNotCaptureFixture.class))
                .execute()
                .testEvents()
                .assertThatEvents()
                .haveExactly(1, event(test("testCollectorCanNotCapture"), finishedWithFailure()));

        Assertions.assertTrue(capturedFiles().isEmpty(),
                "no artifact should be captured when canGetArtifact() returns false");
    }

    @Test
    void noCaptureWhenCaptureComesBackEmpty() throws IOException {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(ArtifactCollectorCaptureFailedFixture.class))
                .execute()
                .testEvents()
                .assertThatEvents()
                .haveExactly(1, event(test("testCollectorCaptureFailed"), finishedWithFailure()));

        Assertions.assertTrue(capturedFiles().isEmpty(),
                "no artifact should be captured when getArtifact() returns an empty array");
    }

    @Test
    void onDemandCaptureWorksIndependentOfFailure() throws IOException {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(ArtifactCollectorOnDemandFixture.class))
                .execute()
                .testEvents()
                .assertThatEvents()
                .haveExactly(1, event(test("testOnDemandCapture"), finishedSuccessfully()));

        List<Path> files = capturedFiles();
        Assertions.assertEquals(1, files.size(),
                "on-demand capture should produce an artifact even though the test passed");
    }

    @Test
    void parameterizedInvocationsProduceDistinctFilenames() throws IOException {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(ArtifactCollectorParameterizedFixture.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.failed(2));

        List<String> names = capturedFiles().stream()
                .map(p -> p.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());

        Assertions.assertEquals(2, names.size(),
                "each parameterized invocation should produce its own captured artifact");
        Assertions.assertNotEquals(names.get(0), names.get(1),
                "the two invocations' filenames should differ (distinct argument hash per invocation)");
        for (String name : names) {
            Assertions.assertTrue(name.startsWith("testCollectorParameterized-")
                    && name.endsWith(".txt"), "unexpected filename: " + name);
        }
    }
}

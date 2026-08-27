package com.nordstrom.automation.jupiter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import com.nordstrom.common.file.PathUtils;

/**
 * Jupiter-native replacement for JUnit-Foundation's {@code ArtifactCollector}. The capture logic itself
 * (create directory, get next path, write bytes, record path) is an unchanged port — none of it ever had
 * a JUnit 4 dependency. What changes is entirely how the pieces JUnit 4 needed rule-based machinery for
 * are obtained instead:
 * <ul>
 *     <li>Failure detection: JUnit 4's {@code AtomIdentity} implemented {@code TestWatcher.failed(...)}
 *     as a {@code @Rule}. Jupiter's own {@link TestWatcher#testFailed(ExtensionContext, Throwable)} is
 *     the direct native equivalent, registered via {@code @RegisterExtension} exactly like this class's
 *     JUnit 4 counterpart.</li>
 *     <li>Test instance / description: JUnit 4's version captured the instance via constructor
 *     ({@code AtomIdentity(Object instance)}) because JUnit 4's rule-execution model gave no other way
 *     to reach it from {@code failed(...)}. Jupiter's {@code testFailed} callback receives the full
 *     {@link ExtensionContext} directly, so {@code context.getRequiredTestInstance()} /
 *     {@code context.getRequiredTestMethod()} replace that constructor capture — no separate
 *     identity-tracking class needed.</li>
 *     <li>Parameter hash for artifact naming: replaces {@code ArtifactParams.getParameters()} (which
 *     needed a different reflective extractor per JUnit 4 parameterized-test runner) with
 *     {@link ArgumentsCaptor#getArguments(ExtensionContext)} — one implementation, uniform across every
 *     {@code @ArgumentsSource}.</li>
 *     <li>Cross-instance watcher lookup: JUnit 4's {@code WATCHER_MAP}/{@code getWatcher(...)} existed so
 *     other code (with no direct object reference) could retrieve "the collector instance for this
 *     test." Not needed here — a Jupiter {@code @RegisterExtension} field is already a direct,
 *     ordinary field reference on the test instance; nothing needs a side-channel lookup to find it.</li>
 * </ul>
 *
 * @param <T> scenario-specific artifact type
 */
public class ArtifactCollector<T extends ArtifactType> implements TestWatcher {

    private final T provider;
    private final List<Path> artifactPaths = new ArrayList<>();

    /**
     * Constructor for {@code ArtifactCollector} instances.
     *
     * @param provider artifact provider
     */
    public ArtifactCollector(final T provider) {
        this.provider = provider;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testFailed(final ExtensionContext context, final Throwable cause) {
        captureArtifact(context, cause);
    }

    /**
     * Capture artifact from the current test context.
     *
     * @param context current extension context
     * @param reason impetus for capture request; may be {@code null}
     * @return (optional) path at which the captured artifact was stored
     */
    public Optional<Path> captureArtifact(final ExtensionContext context, final Throwable reason) {
        Object instance = context.getRequiredTestInstance();

        if (!provider.canGetArtifact(instance)) {
            return Optional.empty();
        }

        byte[] artifact = provider.getArtifact(instance, reason);
        if ((artifact == null) || (artifact.length == 0)) {
            return Optional.empty();
        }

        Path collectionPath = getCollectionPath(instance);
        if (!collectionPath.toFile().exists()) {
            try {
                Files.createDirectories(collectionPath);
            } catch (IOException e) {
                if (provider.getLogger() != null) {
                    provider.getLogger().warn(
                            "Unable to create collection directory ({}); no artifact was captured",
                            collectionPath, e);
                }
                return Optional.empty();
            }
        }

        Path artifactPath;
        try {
            artifactPath = PathUtils.getNextPath(
                    collectionPath, getArtifactBaseName(context), provider.getArtifactExtension());
        } catch (IOException e) {
            if (provider.getLogger() != null) {
                provider.getLogger().warn("Unable to get output path; no artifact was captured", e);
            }
            return Optional.empty();
        }

        try {
            if (provider.getLogger() != null) {
                provider.getLogger().info("Saving captured artifact to ({}).", artifactPath);
            }
            Files.write(artifactPath, artifact);
        } catch (IOException e) {
            if (provider.getLogger() != null) {
                provider.getLogger().warn("I/O error saving to ({}); no artifact was captured",
                        artifactPath, e);
            }
            return Optional.empty();
        }

        artifactPaths.add(artifactPath);
        return Optional.of(artifactPath);
    }

    private Path getCollectionPath(final Object instance) {
        Path collectionPath = PathUtils.ReportsDirectory.getPathForObject(instance);
        return collectionPath.resolve(provider.getArtifactPath(instance));
    }

    /**
     * Get base name for artifact files for the current test.
     * <br><br>
     * <b>NOTE</b>: The base name is derived from the name of the current test. If the method is a
     * {@code @TestTemplate} (e.g. {@code @ParameterizedTest}), a hash code is computed from the resolved
     * argument values — via {@link ArgumentsCaptor#getArguments(ExtensionContext)} — and appended to the
     * base name as an 8-digit hexadecimal integer.
     *
     * @param context current extension context
     * @return artifact file base name
     */
    private String getArtifactBaseName(final ExtensionContext context) {
        int hashcode = ArgumentsCaptor.getArguments(context).hashCode();
        String sanitized = context.getRequiredTestMethod().getName().replaceAll("[\\/:*?\"<>|]", "_");
        if (hashcode != 0) {
            return sanitized + "-" + String.format("%08X", hashcode);
        } else {
            return sanitized;
        }
    }

    /**
     * Retrieve the paths of artifacts captured by this collector.
     *
     * @return (optional) list of artifact paths
     */
    public Optional<List<Path>> retrieveArtifactPaths() {
        return artifactPaths.isEmpty() ? Optional.empty() : Optional.of(artifactPaths);
    }

    /**
     * Get the artifact provider object.
     *
     * @return artifact provider object
     */
    public T getArtifactProvider() {
        return provider;
    }
}

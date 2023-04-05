package com.hedera.node.app.service.mono.utils.replay;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

/**
 * Utility class for recording data to be used in replay tests into a
 * {@code replay-assets/} directory. The first time any replay asset is
 * touched, it is deleted and re-created.
 *
 * <p>Thread-safety is not a concern, since only {@code handleTransaction} and
 * the {@link com.swirlds.common.system.PlatformStatus#FREEZE_COMPLETE} listener
 * will only ever use this class; and these are necessarily serial.
 *
 * <p>Performance is also not a concern, as we will build the replay assets while
 * running EET specs at perhaps 2 TPS (concurrent execution would make replay
 * impossible).
 */
public class ReplayAssetRecording {
    public static final String DEFAULT_REPLAY_ASSETS_DIR = "replay-assets";
    private final File assetDir;
    private final Set<String> touchedAssets = new HashSet<>();
    private final ObjectMapper om = new ObjectMapper();

    public ReplayAssetRecording(@NonNull final File assetDir) {
        this.assetDir = assetDir;
        try {
            Files.createDirectories(Paths.get(assetDir.toString()));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void appendJsonLineToReplayAsset(final String assetFileName, final Object data) {
        try {
            removeIfFirstUsage(assetFileName);
            final var jsonLine = om.writeValueAsString(data);
            final var assetPath = replayPathDirOf(assetFileName);
            Files.write(assetPath, List.of(jsonLine), CREATE, APPEND);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    public <T> List<T> readJsonLinesFromReplayAsset(
            @NonNull final String assetFileName,
            @NonNull final Class<T> type) {
        try {
            removeIfFirstUsage(assetFileName);
            final var assetPath = replayPathDirOf(assetFileName);
            try (final var lines = Files.lines(assetPath)) {
                return lines.map(line -> readValueUnchecked(line, type)).toList();
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T readValueUnchecked(final String line, final Class<T> type) {
        try {
            return om.readValue(line, type);
        } catch (final JsonProcessingException e) {
            throw new AssertionError("Not implemented");
        }
    }

    private void removeIfFirstUsage(final String assetFileName) throws IOException {
        if (touchedAssets.add(assetFileName)) {
            final var f = replayPathDirOf(assetFileName).toFile();
            if (f.exists()) {
                Files.delete(f.toPath());
            }
        }
    }

    private Path replayPathDirOf(final String assetFileName) {
        return Paths.get(assetDir.toString(), assetFileName);
    }
}

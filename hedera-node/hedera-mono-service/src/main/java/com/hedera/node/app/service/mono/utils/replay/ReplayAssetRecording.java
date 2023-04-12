/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.service.mono.utils.replay;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

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

    public void appendJsonToAsset(final String assetFileName, final Object data) {
        try {
            removeIfFirstUsage(assetFileName);
            final var jsonLine = om.writeValueAsString(data);
            final var assetPath = replayPathDirOf(assetFileName);
            Files.write(assetPath, List.of(jsonLine), CREATE, APPEND);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void appendPlaintextToAsset(final String assetFileName, final String line) {
        try {
            removeIfFirstUsage(assetFileName);
            final var assetPath = replayPathDirOf(assetFileName);
            Files.write(assetPath, List.of(line), CREATE, APPEND);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    public <T> List<T> readJsonLinesFromReplayAsset(@NonNull final String assetFileName, @NonNull final Class<T> type) {
        try {
            final var assetPath = replayPathDirOf(assetFileName);
            try (final var lines = Files.lines(assetPath)) {
                return lines.map(line -> readJsonValueUnchecked(line, type)).toList();
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> readPlaintextLinesFromReplayAsset(@NonNull final String assetFileName) {
        try {
            final var assetPath = replayPathDirOf(assetFileName);
            try (final var lines = Files.lines(assetPath)) {
                return lines.toList();
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T readJsonValueUnchecked(final String line, final Class<T> type) {
        try {
            return om.readValue(line, type);
        } catch (final JsonProcessingException e) {
            throw new UncheckedIOException(e);
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

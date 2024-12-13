/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.blocks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/*
 * The BlockRetentionManager is responsible for managing the lifecycle of block files in the uploaded directory after a configurable retention period.
 */
public class BlockRetentionManager {
    private static final Logger log = LogManager.getLogger(BlockRetentionManager.class);
    private final Path uploadedDir;
    private final Duration retentionPeriod;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService cleanupExecutor;

    public BlockRetentionManager(Path uploadedDir, Duration retentionPeriod) {
        this.uploadedDir = Objects.requireNonNull(uploadedDir, "uploadedDir must not be null");
        this.retentionPeriod = Objects.requireNonNull(retentionPeriod, "retentionPeriod must not be null");

        final int threadPoolSize = 4;
        this.cleanupExecutor = Executors.newFixedThreadPool(threadPoolSize);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void scheduleRepeating(Runnable cleanupTask) {
        scheduler.scheduleAtFixedRate(cleanupTask, 0, retentionPeriod.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void startCleanup() {
        CompletableFuture.runAsync(this::cleanupExpiredBlocks, cleanupExecutor).exceptionally(ex -> {
            log.warn("Error during cleanup: {}", ex.getMessage());
            return null;
        });
    }

    private void cleanupExpiredBlocks() {
        try (Stream<Path> files = Files.list(uploadedDir)) {
            // Collect files into a list to avoid consuming the stream multiple times
            List<Path> fileList =
                    files.filter(this::isBlockFile).filter(this::isFileExpired).toList();

            // Submit deletion tasks for each file
            List<CompletableFuture<Void>> futures = fileList.stream()
                    .map(file -> CompletableFuture.runAsync(() -> deleteFile(file), cleanupExecutor))
                    .toList();

            // Wait for all deletion tasks to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception ex) {
            log.warn("Error scanning directory: {}", ex.getMessage());
        }
    }

    private void deleteFile(Path file) {
        try {
            Files.delete(file);
        } catch (Exception ex) {
            log.warn("Failed to delete file {}: {}", file, ex.getMessage());
        }
    }

    private boolean isBlockFile(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.endsWith(".blk") || fileName.endsWith(".blk.gz");
    }

    private boolean isFileExpired(Path file) {
        try {
            Instant fileTime = Files.getLastModifiedTime(file).toInstant();
            return fileTime.isBefore(Instant.now().minus(retentionPeriod));
        } catch (Exception e) {
            log.warn("Failed to get last modified time for file {}: {}", file, e.getMessage());
            return false;
        }
    }
}

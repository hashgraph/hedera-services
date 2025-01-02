/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The BlockRetentionManager is responsible for managing the lifecycle of block files in the uploaded directory after a configurable retention period.
 */
@Singleton
public class BlockRetentionManager {
    private static final Logger log = LogManager.getLogger(BlockRetentionManager.class);
    private final Path uploadedDir;
    private final Duration retentionPeriod;
    private final Duration cleanupInterval;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService cleanupExecutor;
    public static final String BLOCK_FILE_EXTENSION = ".blk";
    public static final String BLOCK_FILE_EXTENSION_GZ = ".blk.gz";

    /**
     * Creates a new BlockRetentionManager instance.
     * @param uploadedDir the directory where uploaded block files are stored
     * @param retentionPeriod the duration after which block files are considered expired and eligible for deletion
     * @param cleanupInterval the interval at which the cleanup task should run
     * @param cleanupThreadPoolSize the number of threads to use for cleanup tasks
     */
    @Inject
    public BlockRetentionManager(
            @NonNull final Path uploadedDir,
            @NonNull final Duration retentionPeriod,
            @NonNull final Duration cleanupInterval,
            final int cleanupThreadPoolSize) {
        this.uploadedDir = requireNonNull(uploadedDir, "uploadedDir must not be null");
        this.retentionPeriod = requireNonNull(retentionPeriod, "retentionPeriod must not be null");
        this.cleanupInterval = requireNonNull(cleanupInterval, "cleanupInterval must not be null");

        this.cleanupExecutor = Executors.newFixedThreadPool(cleanupThreadPoolSize);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Schedules a cleanup task to run at a fixed interval.
     *
     * @param cleanupTask the task to run
     */
    public void scheduleRepeating(@NonNull final Runnable cleanupTask) {
        requireNonNull(cleanupTask, "cleanupTask must not be null");
        scheduler.scheduleAtFixedRate(
                cleanupTask, cleanupInterval.toMillis(), cleanupInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Starts the cleanup process asynchronously.
     */
    public void startCleanup() {
        CompletableFuture.runAsync(this::cleanupExpiredBlocks, cleanupExecutor).exceptionally(ex -> {
            log.warn("Error during cleanup: {}", ex.getMessage());
            return null;
        });
    }

    /**
     * Deletes all block files that have expired.
     */
    public void cleanupExpiredBlocks() {
        // Collect files into a list to avoid consuming the stream multiple times
        final List<Path> fileList = listFilesIgnoreErrors()
                .filter(file -> isBlockFile(file) && isFileExpired(file))
                .toList();

        // Submit deletion tasks for each file
        final List<CompletableFuture<Void>> futures = fileList.stream()
                .map(file -> CompletableFuture.runAsync(() -> quietDeleteFile(file), cleanupExecutor))
                .toList();

        // Wait for all deletion tasks to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    /**
     * Shuts down the underlying scheduler and cleanup executor services.
     */
    public void shutdown() {
        scheduler.shutdown();
        cleanupExecutor.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            if (!cleanupExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private Stream<Path> listFilesIgnoreErrors() {
        Stream<Path> files = Stream.empty();
        try {
            files = Files.list(uploadedDir);
        } catch (IOException ex) {
            log.warn("Error scanning directory: {}", ex.getMessage());
        }

        return files;
    }

    private void quietDeleteFile(@NonNull final Path file) {
        try {
            Files.delete(file);
        } catch (IOException ex) {
            log.warn("Failed to delete file {}: {}", file, ex.getMessage());
        }
    }

    private boolean isBlockFile(@NonNull final Path file) {
        final String fileName = file.getFileName().toString();
        return fileName.endsWith(BLOCK_FILE_EXTENSION) || fileName.endsWith(BLOCK_FILE_EXTENSION_GZ);
    }

    private boolean isFileExpired(@NonNull final Path file) {
        Instant fileTime;
        try {
            fileTime = Files.getLastModifiedTime(file).toInstant();
        } catch (IOException e) {
            log.warn("Failed to get last modified time for file {}: {}", file, e.getMessage());
            fileTime = Instant.MAX;
        }

        final Instant now = Instant.now();
        final Instant expirationTime = now.minus(retentionPeriod);
        return fileTime.isBefore(expirationTime);
    }
}

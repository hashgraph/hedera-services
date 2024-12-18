// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.blocks;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
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

/*
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

    public void scheduleRepeating(@NonNull final Runnable cleanupTask) {
        requireNonNull(cleanupTask, "cleanupTask must not be null");
        scheduler.scheduleAtFixedRate(
                cleanupTask, cleanupInterval.toMillis(), cleanupInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void startCleanup() {
        CompletableFuture.runAsync(this::cleanupExpiredBlocks, cleanupExecutor).exceptionally(ex -> {
            log.warn("Error during cleanup: {}", ex.getMessage());
            return null;
        });
    }

    public void cleanupExpiredBlocks() {
        // Collect files into a list to avoid consuming the stream multiple times
        List<Path> fileList = listFiles()
                .filter(file -> isBlockFile(file) && isFileExpired(file))
                .toList();

        // Submit deletion tasks for each file
        List<CompletableFuture<Void>> futures = fileList.stream()
                .map(file -> CompletableFuture.runAsync(() -> deleteFile(file), cleanupExecutor))
                .toList();

        // Wait for all deletion tasks to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

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

    private Stream<Path> listFiles() {
        try {
            return Files.list(uploadedDir);
        } catch (Exception ex) {
            log.warn("Error scanning directory: {}", ex.getMessage());
            return Stream.empty();
        }
    }

    private void deleteFile(@NonNull final Path file) {
        try {
            Files.delete(file);
        } catch (Exception ex) {
            log.warn("Failed to delete file {}: {}", file, ex.getMessage());
        }
    }

    private boolean isBlockFile(@NonNull final Path file) {
        String fileName = file.getFileName().toString();
        return fileName.endsWith(BLOCK_FILE_EXTENSION) || fileName.endsWith(BLOCK_FILE_EXTENSION_GZ);
    }

    private boolean isFileExpired(@NonNull final Path file) {
        try {
            Instant fileTime = Files.getLastModifiedTime(file).toInstant();
            Instant now = Instant.now();
            Instant expirationTime = now.minus(retentionPeriod);
            return fileTime.isBefore(expirationTime);
        } catch (Exception e) {
            log.warn("Failed to get last modified time for file {}: {}", file, e.getMessage());
            return false;
        }
    }
}

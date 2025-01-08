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

package com.hedera.node.app.blocks.impl;

import com.hedera.node.app.blocks.BlockFileClosedListener;
import com.hedera.node.app.uploader.BucketConfigurationListener;
import com.hedera.node.app.uploader.BucketConfigurationManager;
import com.hedera.node.app.uploader.CloudBucketUploader;
import com.hedera.node.app.uploader.HashMismatchException;
import com.hedera.node.app.uploader.MinioBucketUploader;
import com.hedera.node.app.uploader.configs.CompleteBucketConfig;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.BlockStreamConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manager responsible for coordinating block uploads to multiple cloud storage providers.
 */
@Singleton
public class BucketUploadManager implements BlockFileClosedListener, BucketConfigurationListener {
    private static final Logger logger = LogManager.getLogger(BucketUploadManager.class);
    public static final String UPLOADED_DIR = "uploaded";
    public static final String HASHMISMATCH_DIR = "hashmismatch";
    private final List<CloudBucketUploader> uploaders;
    private final ReadWriteLock uploadersLock;
    private final ExecutorService executorService;
    private final Path uploadedDir;
    private final Path hashMismatchDir;
    private final ConcurrentHashMap<Path, CompletableFuture<Void>> pendingUploads;
    private final ConfigProvider configProvider;
    private final BucketConfigurationManager bucketConfigurationManager;

    /**
     * Creates a new BucketUploadManager instance.
     *
     * @param executorService the executor service to use for async tasks
     * @param configProvider the configuration provider
     * @param fileSystem the file system to use for file operations
     */
    @Inject
    public BucketUploadManager(
            @NonNull @Named("bucketUploadExecutor") ExecutorService executorService,
            @NonNull ConfigProvider configProvider,
            @NonNull FileSystem fileSystem) {
        this.uploaders = new ArrayList<>();
        this.uploadersLock = new ReentrantReadWriteLock();
        this.executorService = executorService;
        this.pendingUploads = new ConcurrentHashMap<>();
        this.configProvider = configProvider;
        this.bucketConfigurationManager = new BucketConfigurationManager(configProvider);

        final var blockStreamConfig = configProvider.getConfiguration().getConfigData(BlockStreamConfig.class);
        final Path blockFileDir = fileSystem.getPath(blockStreamConfig.blockFileDir());

        // Create necessary directories under the root block file directory
        this.uploadedDir = blockFileDir.resolve(UPLOADED_DIR);
        this.hashMismatchDir = blockFileDir.resolve(HASHMISMATCH_DIR);
        try {
            Files.createDirectories(uploadedDir);
            Files.createDirectories(hashMismatchDir);
        } catch (IOException e) {
            logger.error("Failed to create block stream uploader directories: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onBucketConfigurationsUpdated(@NonNull List<CompleteBucketConfig> configs) {
        // Acquire write lock to update uploaders
        uploadersLock.writeLock().lock();
        try {
            // Clear all existing uploaders
            uploaders.clear();

            // Create new uploaders for each config
            for (CompleteBucketConfig config : configs) {
                if (config.enabled()) {
                    try {
                        CloudBucketUploader newUploader =
                                new MinioBucketUploader(bucketConfigurationManager, executorService, configProvider);
                        uploaders.add(newUploader);
                        logger.info("Created new uploader for bucket: {}", config.bucketName());
                    } catch (Exception e) {
                        logger.error(
                                "Failed to create uploader for bucket {}: {}", config.bucketName(), e.getMessage());
                    }
                }
            }
        } finally {
            uploadersLock.writeLock().unlock();
        }
    }

    @Override
    public void onBlockClosed(@NonNull Path blockPath) {
        // Start a new async task for handling the block closure
        CompletableFuture<Void> uploadTask = CompletableFuture.runAsync(
                () -> {
                    try {
                        processBlockClosure(blockPath);
                    } catch (Exception e) {
                        logger.error("Error processing block closure for {}: {}", blockPath, e.getMessage());
                    }
                },
                executorService);

        // Store the task for tracking
        pendingUploads.put(blockPath, uploadTask);

        // Clean up the tracking when the task completes
        uploadTask.whenComplete((result, error) -> pendingUploads.remove(blockPath));
    }

    private void processBlockClosure(Path blockPath) {
        // Acquire read lock to access uploaders
        uploadersLock.readLock().lock();
        try {
            if (uploaders.isEmpty()) {
                logger.warn("No cloud storage providers registered for upload");
                return;
            }

            // Create upload tasks for each provider
            List<CompletableFuture<Void>> uploadTasks = uploaders.stream()
                    .map(uploader -> uploadToProvider(blockPath, uploader))
                    .toList();

            // Wait for all uploads to complete and handle the results
            CompletableFuture.allOf(uploadTasks.toArray(CompletableFuture[]::new))
                    .thenAccept(v -> handleUploadCompletion(blockPath))
                    .exceptionally(throwable -> {
                        logger.error("Error handling upload completion for {}: {}", blockPath, throwable.getMessage());
                        return null;
                    });
        } finally {
            uploadersLock.readLock().unlock();
        }
    }

    private CompletableFuture<Void> uploadToProvider(Path blockPath, CloudBucketUploader uploader) {
        return CompletableFuture.runAsync(
                () -> {
                    try {
                        uploader.uploadBlock(blockPath);
                    } catch (HashMismatchException e) {
                        logger.error(
                                "Hash mismatch while uploading block to {}: {}",
                                uploader.getProvider().name(),
                                e.getCause());
                        handleHashMismatch(blockPath, uploader.getProvider().name());
                    }
                },
                executorService);
    }

    private void handleHashMismatch(Path blockPath, String providerName) {
        logger.error("Hash mismatch detected for {} in {}", blockPath, providerName);
        try {
            Files.move(blockPath, hashMismatchDir.resolve(blockPath.getFileName()));
        } catch (IOException e) {
            logger.error("Failed to move file {} to hashmismatch directory: {}", blockPath, e.getMessage());
        }
    }

    private void handleUploadCompletion(Path blockPath) {
        try {
            Files.move(blockPath, uploadedDir.resolve(blockPath.getFileName()));
        } catch (IOException e) {
            logger.error("Failed to move file {} to uploaded directory: {}", blockPath, e.getMessage());
        }
    }
}

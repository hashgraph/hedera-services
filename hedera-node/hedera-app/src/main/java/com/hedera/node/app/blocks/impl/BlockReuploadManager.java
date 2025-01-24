/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

import com.hedera.node.app.uploader.CloudBucketUploader;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.BlockStreamConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BlockReuploadManager {

    private final BucketUploadManager bucketUploadManager;
    private final ConfigProvider configProvider;
    private final Path blockFileDir;
    private static final Logger logger = LogManager.getLogger(BlockReuploadManager.class);
    final ConcurrentHashMap<Path, Integer> failedUploads;
    private final CloudBucketUploader cloudBucketUploader;

    public BlockReuploadManager(
            @NonNull BucketUploadManager bucketUploadManager,
            @NonNull ConfigProvider configProvider,
            @NonNull FileSystem fileSystem,
            CloudBucketUploader cloudBucketUploader) {
        this.bucketUploadManager = bucketUploadManager;
        this.configProvider = configProvider;
        this.cloudBucketUploader = cloudBucketUploader;
        final var blockStreamConfig = configProvider.getConfiguration().getConfigData(BlockStreamConfig.class);
        this.blockFileDir = fileSystem.getPath(blockStreamConfig.blockFileDir());
        this.failedUploads = new ConcurrentHashMap<>();
    }

    public void scanAndProcessFailedUploads() {
        try (Stream<Path> files = Files.walk(blockFileDir)) {
            files.filter(Files::isRegularFile).forEach(this::processFailedUpload);
        } catch (IOException e) {
            logger.error("Error scanning block file directory", e);
        }
    }

    private void processFailedUpload(Path path) {
        if (failedUploads.containsKey(path)) {
            int retryAttempts = failedUploads.get(path);
            if (retryAttempts
                    < configProvider
                            .getConfiguration()
                            .getConfigData(BlockStreamConfig.class)
                            .uploadRetryAttempts()) {
                logger.info("Initiating retry for failed upload: {}", path);
                initiateRetryUpload(path);
            } else {
                logger.warn("Max retry attempts reached for upload: {}", path);
            }
        } else {
            logger.info("No failed uploads found for: {}", path);
        }
    }

    void initiateRetryUpload(Path blockPath) {
        CompletableFuture<Void> uploadTask = CompletableFuture.runAsync(
                () -> bucketUploadManager.uploadToProvider(blockPath, cloudBucketUploader),
                bucketUploadManager.executorService);

        uploadTask.whenComplete((result, error) -> {
            if (error != null) {
                logger.error("Error during retry upload for {}: {}", blockPath, error.getMessage());
                failedUploads.put(blockPath, failedUploads.getOrDefault(blockPath, 0) + 1);
                //                handleHashMismatch(file);
            } else {
                failedUploads.remove(blockPath);
                //                Files.move(file, uploadedDir.resolve(file.getFileName()));
                logger.info("Successful retry upload for {}", blockPath);
            }
        });
    }

    public void onNodeRestart() {
        scanAndProcessFailedUploads();
    }
}

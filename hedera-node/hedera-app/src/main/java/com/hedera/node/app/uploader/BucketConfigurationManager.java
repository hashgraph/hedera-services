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

package com.hedera.node.app.uploader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.node.app.blocks.cloud.uploader.BucketConfigurationListener;
import com.hedera.node.app.blocks.impl.BucketUploadManager;
import com.hedera.node.app.uploader.credentials.CompleteBucketConfig;
import com.hedera.node.app.uploader.credentials.OnDiskBucketConfig;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.BlockStreamConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A singleton class that will handle the entire bucket configuration. This is needed since we will have
 * cloud bucket configuration file and on disk credentials.
 */
@Singleton
public class BucketConfigurationManager {
    private static final Logger logger = LogManager.getLogger(BucketConfigurationManager.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final BlockStreamConfig blockStreamConfig;
    private final String bucketCredentialsPath;
    private volatile OnDiskBucketConfig credentials;
    private final AtomicReference<List<CompleteBucketConfig>> currentConfig = new AtomicReference<>();

    /** The list of registered block closed listeners */
    private final List<BucketConfigurationListener> bucketConfigurationListeners = new ArrayList<>();

    /**
     * @param configProvider the configuration provider to use
     */
    @Inject
    public BucketConfigurationManager(
            @NonNull final ConfigProvider configProvider, @NonNull final BucketUploadManager bucketUploadManager) {
        this.blockStreamConfig = configProvider.getConfiguration().getConfigData(BlockStreamConfig.class);
        this.bucketCredentialsPath = blockStreamConfig.credentialsPath();
        this.bucketConfigurationListeners.add(bucketUploadManager);
        loadCompleteBucketConfigs();
        watchCredentialsFile();
    }

    /**
     * Register a listener for bucket configuration changes.
     *
     * @param listener the listener to register
     */
    public void registerBucketConfigurationListener(@NonNull final BucketConfigurationListener listener) {
        bucketConfigurationListeners.add(listener);
    }

    /**
     * Combines the buckets configuration with their respective credentials.
     */
    public void loadCompleteBucketConfigs() {
        final Path credentialsPath = Path.of(bucketCredentialsPath);
        try {
            credentials = mapper.readValue(credentialsPath.toFile(), OnDiskBucketConfig.class);
        } catch (IOException e) {
            logger.error("Failed to load bucket credentials from {}", credentialsPath, e);
            throw new RuntimeException("Failed to load bucket credentials", e);
        }

        currentConfig.set(blockStreamConfig.buckets().stream()
                .map(bucket -> {
                    var bucketCredentials = credentials.credentials().get(bucket.name());
                    if (bucketCredentials == null) {
                        logger.error("No credentials found for bucket: {}", bucket.name());
                        return null;
                    }
                    return new CompleteBucketConfig(
                            bucket.name(),
                            bucket.provider(),
                            bucket.endpoint(),
                            bucket.region(),
                            bucket.bucketName(),
                            bucket.enabled(),
                            bucketCredentials);
                })
                .filter(Objects::nonNull)
                .filter(CompleteBucketConfig::enabled)
                .toList());

        // Notify listeners
        bucketConfigurationListeners.forEach(listener -> listener.onBucketConfigurationsUpdated(currentConfig.get()));
    }

    /**
     * Watch the bucket credentials file for changes and apply them at runtime.
     */
    private void watchCredentialsFile() {
        final Path credentialsPath = Path.of(bucketCredentialsPath);
        new Thread(() -> {
                    try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                        credentialsPath.getParent().register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

                        while (true) {
                            WatchKey key = watchService.take();
                            for (WatchEvent<?> event : key.pollEvents()) {
                                if (event.context().toString().equals(bucketCredentialsPath)) {
                                    System.out.println("Configuration file changed. Reloading...");
                                    loadCompleteBucketConfigs();
                                }
                            }
                            key.reset();
                        }
                    } catch (IOException | InterruptedException e) {
                        System.err.println("Error watching configuration file: " + e.getMessage());
                    }
                })
                .start();
    }

    /**
     * @return the current complete configurations including the credentials for each bucket
     */
    public List<CompleteBucketConfig> getCompleteBucketConfigs() {
        return currentConfig.get();
    }
}

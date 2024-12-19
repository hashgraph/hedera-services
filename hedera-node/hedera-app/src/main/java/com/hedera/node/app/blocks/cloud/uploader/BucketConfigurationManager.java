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

package com.hedera.node.app.blocks.cloud.uploader;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.node.app.blocks.cloud.uploader.configs.CompleteBucketConfig;
import com.hedera.node.app.blocks.cloud.uploader.configs.OnDiskBucketConfig;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.BlockStreamConfig;
import com.swirlds.base.utility.FileSystemUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
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

    private BlockStreamConfig blockStreamConfig;
    private volatile OnDiskBucketConfig credentials;
    private final AtomicReference<List<CompleteBucketConfig>> currentConfig = new AtomicReference<>();

    /**
     * @param configProvider the configuration provider to use
     */
    @Inject
    public BucketConfigurationManager(@NonNull final ConfigProvider configProvider) {
        requireNonNull(configProvider, "configProvider must not be null");
        this.blockStreamConfig = configProvider.getConfiguration().getConfigData(BlockStreamConfig.class);
        loadCompleteBucketConfigs(blockStreamConfig);
        watchCredentialsFile();
    }

    /**
     * Combines the buckets configuration with their respective credentials.
     * @param blockStreamConfig configuration properties for block streams
     */
    public void loadCompleteBucketConfigs(@NonNull final BlockStreamConfig blockStreamConfig) {
        // update local BlockStreamConfig if there was a network properties change
        if (this.blockStreamConfig != blockStreamConfig) {
            this.blockStreamConfig = blockStreamConfig;
        }
        final Path credentialsPath = Path.of(this.blockStreamConfig.credentialsPath());
        try {
            if (FileSystemUtils.waitForPathPresence(credentialsPath)) {
                credentials = mapper.readValue(credentialsPath.toFile(), OnDiskBucketConfig.class);
            }
        } catch (IOException e) {
            logger.error("Failed to load bucket credentials from {}", credentialsPath, e);
        }

        currentConfig.set(this.blockStreamConfig.buckets().stream()
                .map(bucket -> {
                    if (credentials == null || credentials.credentials().get(bucket.name()) == null) {
                        logger.error("No credentials found in {} for bucket: {}", credentialsPath, bucket.name());
                        return null;
                    }
                    final var bucketCredentials = credentials.credentials().get(bucket.name());
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
    }

    /**
     * Watch the bucket credentials file for changes and apply them at runtime.
     */
    private void watchCredentialsFile() {
        final var bucketCredentialsPath = blockStreamConfig.credentialsPath();
        final Path credentialsPath = Path.of(bucketCredentialsPath);

        CompletableFuture.runAsync(() -> {
            try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                credentialsPath.getParent().register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

                while (true) {
                    WatchKey key = watchService.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.context().toString().equals(bucketCredentialsPath)) {
                            logger.info("Configuration file changed. Reloading...");
                            loadCompleteBucketConfigs(blockStreamConfig);
                        }
                    }
                    key.reset();
                }
            } catch (IOException | InterruptedException e) {
                logger.error("Error watching configuration file: {}", e.getMessage());
            }
        });
    }

    /**
     * @return the current complete configurations including the credentials for each bucket
     */
    public List<CompleteBucketConfig> getCompleteBucketConfigs() {
        return currentConfig.get();
    }
}

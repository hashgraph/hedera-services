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
import com.hedera.node.app.uploader.credentials.CompleteBucketConfig;
import com.hedera.node.app.uploader.credentials.OnDiskBucketConfig;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.BlockStreamConfig;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
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
    private volatile OnDiskBucketConfig credentials;

    /**
     * @param configProvider the configuration provider to use
     */
    @Inject
    public BucketConfigurationManager(ConfigProvider configProvider) {
        this.blockStreamConfig = configProvider.getConfiguration().getConfigData(BlockStreamConfig.class);

        final var credentialsPath = Path.of(blockStreamConfig.credentialsPath());
        try {
            credentials = mapper.readValue(credentialsPath.toFile(), OnDiskBucketConfig.class);
        } catch (IOException e) {
            logger.error("Failed to load bucket credentials from {}", credentialsPath, e);
            throw new RuntimeException("Failed to load bucket credentials", e);
        }
    }

    /**
     * Combines the buckets configuration with their respective credentials.
     *
     * @return the list of {@link CompleteBucketConfig} with applied credentials
     */
    public List<CompleteBucketConfig> getCompleteBucketConfigs() {
        return blockStreamConfig.buckets().stream()
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
                .toList();
    }
}

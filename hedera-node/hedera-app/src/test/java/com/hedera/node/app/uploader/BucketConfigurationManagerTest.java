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

package com.hedera.node.app.uploader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.node.app.blocks.cloud.uploader.BucketConfigurationManager;
import com.hedera.node.app.blocks.cloud.uploader.configs.CompleteBucketConfig;
import com.hedera.node.app.blocks.cloud.uploader.configs.OnDiskBucketConfig;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfigImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.node.config.types.CloudBucketConfig;
import com.swirlds.config.extensions.sources.YamlConfigSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class BucketConfigurationManagerTest {
    @Mock
    private ConfigProvider configProvider;

    private BucketConfigurationManager subject;
    private static final CloudBucketConfig AWS_DEFAULT_BUCKET_CONFIG = new CloudBucketConfig(
            "default-aws-bucket", "aws", "https://s3.amazonaws.com", "us-east-1", "bucket-name", true);
    private static final CloudBucketConfig GCP_DEFAULT_BUCKET_CONFIG = new CloudBucketConfig(
            "default-gcp-bucket", "gcp", "https://storage.googleapis.com", "", "bucket-name", true);

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testHappyCompletionOfBucketConfigs() {
        final var config = HederaTestConfigBuilder.create()
                .withSource(new YamlConfigSource("uploader/buckets-config.yaml"))
                .getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(config, 1L));
        subject = new BucketConfigurationManager(configProvider);

        List<CloudBucketConfig> cloudBucketConfigs = List.of(AWS_DEFAULT_BUCKET_CONFIG, GCP_DEFAULT_BUCKET_CONFIG);
        final var credentialsPath = BucketConfigurationManagerTest.class
                .getClassLoader()
                .getResourceAsStream("uploader/bucket-credentials.json");
        OnDiskBucketConfig credentials;
        try {
            credentials = mapper.readValue(credentialsPath, OnDiskBucketConfig.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final List<CompleteBucketConfig> completeBucketConfigs =
                generateCompleteBucketConfigs(cloudBucketConfigs, credentials);
        assertThat(subject.getCompleteBucketConfigs()).isEqualTo(completeBucketConfigs);
    }

    @Test
    void failIfRegionIsEmptyWhenWeUseAWSProvider() {
        assertThrows(
                IllegalStateException.class,
                () -> HederaTestConfigBuilder.create()
                        .withSource(new YamlConfigSource("uploader/aws-bucket-empty-region-config.yaml"))
                        .getOrCreateConfig(),
                "region cannot be null or blank if the provider is AWS");
    }

    private List<CompleteBucketConfig> generateCompleteBucketConfigs(
            @NonNull final List<CloudBucketConfig> cloudBucketConfigs,
            @NonNull final OnDiskBucketConfig onDiskBucketConfig) {
        return cloudBucketConfigs.stream()
                .map(bucket -> {
                    var bucketCredentials = onDiskBucketConfig.credentials().get(bucket.name());
                    return new CompleteBucketConfig(
                            bucket.name(),
                            bucket.provider(),
                            bucket.endpoint(),
                            bucket.region(),
                            bucket.bucketName(),
                            bucket.enabled(),
                            bucketCredentials);
                })
                .toList();
    }
}

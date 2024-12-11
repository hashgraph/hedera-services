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

package com.hedera.node.config.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.node.config.types.BucketProvider;
import org.junit.jupiter.api.Test;

public class CloudBucketConfigTest {

    @Test
    void testValidCloudBucketConfigObject() {
        CloudBucketConfig config = new CloudBucketConfig(
                "test-bucket", BucketProvider.AWS, "test-endpoint", "test-region", "hedera-bucket", true);

        assertThat(config).isNotNull();
        assertThat(config.name()).isEqualTo("test-bucket");
        assertThat(config.provider()).isEqualTo(BucketProvider.AWS);
        assertThat(config.endpoint()).isEqualTo("test-endpoint");
        assertThat(config.region()).isEqualTo("test-region");
        assertThat(config.bucketName()).isEqualTo("hedera-bucket");
    }

    @Test
    void failIfTheProviderIsAWSWithoutRegion() {
        assertThrows(
                NullPointerException.class,
                () -> new CloudBucketConfig(
                        "test-bucket", BucketProvider.AWS, "test-endpoint", null, "hedera-bucket", true),
                "region cannot be null if the provider is AWS");
    }

    @Test
    void failIfTheProviderIsAWSWithEmptyRegion() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CloudBucketConfig(
                        "test-bucket", BucketProvider.AWS, "test-endpoint", "", "hedera-bucket", true),
                "region cannot be null if the provider is AWS");
    }
}

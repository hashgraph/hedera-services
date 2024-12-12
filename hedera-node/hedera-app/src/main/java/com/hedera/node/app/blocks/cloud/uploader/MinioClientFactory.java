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

import com.hedera.node.app.uploader.credentials.CompleteBucketConfig;
import com.hedera.node.config.types.BucketProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.minio.MinioClient;

/**
 * Factory class for creating MinioClient instances.
 */
public class MinioClientFactory {

    private MinioClientFactory() {
        // Utility class
    }

    /**
     * Creates a MinioClient from a bucket configuration.
     *
     * @param config The complete bucket configuration
     * @return A configured MinioClient instance
     */
    public static MinioClient createClient(@NonNull CompleteBucketConfig config) {
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(config.endpoint())
                .credentials(
                        config.credentials().accessKey(),
                        new String(config.credentials().secretKey()));

        // Add region only if the provider is AWS
        if (config.provider() == BucketProvider.AWS && config.region() != null) {
            builder.region(config.region());
        }

        return builder.build();
    }
}

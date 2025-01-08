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

package com.hedera.node.app.blocks.cloud.uploader.configs;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * The complete bucket configuration combining configuration file with on disk credentials.
 *
 * @param name        the name
 * @param provider    the provider to use for the upload
 * @param endpoint    the endpoint
 * @param region      the region (required only for AWS)
 * @param bucketName  the name of the bucket
 * @param credentials the credentials for the bucket
 */
public record CompleteBucketConfig(
        @NonNull String name,
        @NonNull String provider,
        @NonNull String endpoint,
        @Nullable String region,
        @NonNull String bucketName,
        boolean enabled,
        @NonNull BucketCredentials credentials) {}

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

import java.util.Arrays;
import java.util.Objects;

/**
 * @param accessKey the access key of the bucket
 * @param secretKey the secret key of the bucket
 */
public record BucketCredentials(String accessKey, char[] secretKey) {
    public BucketCredentials {
        Objects.requireNonNull(accessKey, "access key cannot be null");
        Objects.requireNonNull(secretKey, "secret key cannot be null");
    }

    @Override
    // Required to allow for comparison of BucketCredentials objects now that we are using char[] for secretKey
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BucketCredentials that)) return false;
        return Objects.equals(accessKey, that.accessKey) && Arrays.equals(secretKey, that.secretKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accessKey, Arrays.hashCode(secretKey));
    }
}

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

package com.hedera.node.config.converter;

import com.hedera.node.config.types.CloudBucketConfig;
import com.swirlds.config.api.converter.ConfigConverter;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.JsonNode;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A {@link ConfigConverter} that converts a string to an {@link CloudBucketConfig}.
 */
public class CloudBucketConverter implements ConfigConverter<CloudBucketConfig> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * {{@inheritDoc}}
     */
    @Nullable
    @Override
    public CloudBucketConfig convert(@NonNull final String value)
            throws IllegalArgumentException, NullPointerException {
        Objects.requireNonNull(value, "value must not be null");
        try {
            final JsonNode jsonNode = OBJECT_MAPPER.readTree(value);
            return new CloudBucketConfig(
                    jsonNode.get("name").asText(),
                    jsonNode.get("provider").asText(),
                    jsonNode.get("endpoint").asText(),
                    jsonNode.get("region").asText(),
                    jsonNode.get("bucketName").asText(),
                    jsonNode.get("enabled").asBoolean());
        } catch (Exception e) {
            throw new IllegalArgumentException("Parsing CloudBucketConfig failed", e);
        }
    }
}

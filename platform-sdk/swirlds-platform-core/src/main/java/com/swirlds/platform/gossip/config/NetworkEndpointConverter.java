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

package com.swirlds.platform.gossip.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swirlds.config.api.converter.ConfigConverter;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.net.InetAddress;
import java.util.Objects;

public class NetworkEndpointConverter implements ConfigConverter<NetworkEndpoint> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Nullable
    @Override
    public NetworkEndpoint convert(@NonNull final String value) throws IllegalArgumentException, NullPointerException {
        Objects.requireNonNull(value, "value must not be null");
        try {
            final JsonNode jsonNode = OBJECT_MAPPER.readTree(value);
            return new NetworkEndpoint(
                    jsonNode.get("nodeId").asLong(),
                    InetAddress.getByName(jsonNode.get("hostname").asText()),
                    jsonNode.get("port").asInt());
        } catch (Exception e) {
            throw new IllegalArgumentException("Parsing InterfaceBinding failed", e);
        }
    }
}

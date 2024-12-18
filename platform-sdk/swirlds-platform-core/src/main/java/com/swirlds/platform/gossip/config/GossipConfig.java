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

package com.swirlds.platform.gossip.config;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import com.swirlds.config.api.Configuration;
import java.util.List;
import java.util.Optional;

@ConfigData("gossip")
public record GossipConfig(
        @ConfigProperty(defaultValue = Configuration.EMPTY_LIST) List<InterfaceBinding> interfaceBindings,
        @ConfigProperty(defaultValue = Configuration.EMPTY_LIST) List<InterfaceBinding> endpointOverrides) {

    /**
     * Returns the interface binding for the given node ID.
     * <p>
     *     <b>Note:</b> If there are multiple interface bindings for the same node ID, only the first one will be
     *     returned.
     *</p>
     *
     * @param nodeId the node ID
     * @return optional of the interface binding, empty if not found
     */
    public Optional<InterfaceBinding> getInterfaceBinding(long nodeId) {
        return interfaceBindings.stream()
                .filter(binding -> binding.nodeId().equals(nodeId))
                .findFirst();
    }

    /**
     * Returns the endpoint override for the given node ID.
     * <p>
     *     <b>Note:</b> If there are multiple endpoint overrides for the same node ID, only the first one will be
     *     returned.
     *</p>
     *
     * @param nodeId the node ID
     * @return optional of the endpoint override, empty if not found
     */
    public Optional<InterfaceBinding> getEndpointOverride(long nodeId) {
        return endpointOverrides.stream()
                .filter(binding -> binding.nodeId().equals(nodeId))
                .findFirst();
    }
}

/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.gossip;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

/**
 * Configuration values that govern behavior of the protocols that execute between pairs of nodes.
 *
 * @param tolerateMismatchedVersion   If true, a node will tolerate peers with a different software version. If false,
 *                                    connections to peers with different software versions will be severed.
 * @param tolerateMismatchedEpochHash If true, a node will tolerate peers with a different epoch hash. If false,
 *                                    connections to peers with different epoch hashes will be severed.
 */
@ConfigData("protocol")
public record ProtocolConfig(
        @ConfigProperty(defaultValue = "false") boolean tolerateMismatchedVersion,
        @ConfigProperty(defaultValue = "false") boolean tolerateMismatchedEpochHash) {}

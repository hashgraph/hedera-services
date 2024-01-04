/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.simulated.config;

import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.util.Map;

/**
 * Configuration for a simulated network of nodes.
 *
 * @param name        a short description of this configuration
 * @param duration    the amount of time this list of configs will be in effect
 * @param nodeConfigs configurations for each node in the network
 */
public record NetworkConfig(
        @Nullable String name, @NonNull Duration duration, @NonNull Map<NodeId, NodeConfig> nodeConfigs) {}

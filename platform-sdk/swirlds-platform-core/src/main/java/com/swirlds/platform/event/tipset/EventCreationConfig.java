/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.tipset;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

/**
 * Configuration for event creation.
 *
 * @param useTipsetAlgorithm if true, use the tipset event creation algorithm
 * @param maxCreationRate    the maximum rate (in hz) that a node can create new events. The maximum rate for the entire
 *                           network is equal to this value times the number of nodes. A value of 0 means that there is
 *                           no limit to the number of events that can be created (as long as those events are legal to
 *                           create).
 * @param antiBullyingFactor the lower this number, the more likely it is that a new event will be created that reduces
 *                           this node's bully score. Setting this too low may result in a suboptimal hashgraph
 *                           topology. Setting this number too high may lead to some nodes being bullied and unable to
 *                           cause their events to reach consensus.
 */
@ConfigData("event.creation")
public record EventCreationConfig(
        @ConfigProperty(defaultValue = "true") boolean useTipsetAlgorithm,
        @ConfigProperty(defaultValue = "0") double maxCreationRate,
        @ConfigProperty(defaultValue = "10") double antiBullyingFactor) {}

/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.fchashmap.config;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import java.time.Duration;

/**
 * Configuration for {@link com.swirlds.fchashmap.FCHashMap}.
 *
 * @param maximumGCQueueSize
 * 		the maximum expected size of the FCHashMapGarbageCollector's queue.
 * @param gCQueueThresholdPeriod
 * 		the amount of time that must pass between error logs about the FCHashMapGarbageCollector's queue size.
 * @param archiveEnabled
 * 		is the archival of FCHashMap enabled?
 */
@ConfigData("fcHashMap")
public record FCHashMapConfig(
        @ConfigProperty(defaultValue = "200") int maximumGCQueueSize,
        @ConfigProperty(defaultValue = "1m") Duration gCQueueThresholdPeriod,
        @ConfigProperty(defaultValue = "true") boolean archiveEnabled) {}

/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.chatter.config;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import java.time.Duration;

/**
 * Configuration of the chatter system
 *
 * @param useChatter
 * 		Whether to use chatter for event gossiping and creation. if true, we should use chatter, if false, it will use
 * 		the sync protocol
 * @param attemptedChatterEventPerSecond
 * 		how many events do we attempt to create per second when running chatter
 * @param chatteringCreationThreshold
 * 		The fraction of neighbors we should be chattering with in order to create events. should be between 0 and 1
 * @param chatterIntakeThrottle
 * 		If the size of the intake queue is higher than this value, don't create events
 * @param otherEventDelay
 * 		Events by other creators are not set immediately to other nodes. We wait a while to see if they will send us a
 * 		descriptor of an event. We wait the amount of time the peer tells us it takes it to process an event plus the
 * 		estimated time it takes the descriptor to this node, plus a constant. This value is the constant.
 * @param selfEventQueueCapacity
 * 		the capacity of each of the neighbour queues for sending self events
 * @param otherEventQueueCapacity
 * 		the capacity of each of the neighbour queues for sending other events
 * @param descriptorQueueCapacity
 * 		the capacity of each of the neighbour queues for sending event descriptors
 * @param processingTimeInterval
 * 		the interval at which to send each peer processing time messages
 * @param heartbeatInterval
 * 		the interval at which to send each peer heartbeats
 * @param futureGenerationLimit
 * 		the number of non-ancient generations we are willing to accept from a peer
 */
@ConfigData("chatter")
public record ChatterConfig(
        @ConfigProperty(defaultValue = "true") boolean useChatter,
        @ConfigProperty(defaultValue = "40") int attemptedChatterEventPerSecond,
        @ConfigProperty(defaultValue = "0.5") double chatteringCreationThreshold,
        @ConfigProperty(defaultValue = "20") int chatterIntakeThrottle,
        @ConfigProperty(defaultValue = "2s") Duration otherEventDelay,
        @ConfigProperty(defaultValue = "1500") int selfEventQueueCapacity,
        @ConfigProperty(defaultValue = "45000") int otherEventQueueCapacity,
        @ConfigProperty(defaultValue = "45000") int descriptorQueueCapacity,
        @ConfigProperty(defaultValue = "100ms") Duration processingTimeInterval,
        @ConfigProperty(defaultValue = "1s") Duration heartbeatInterval,
        @ConfigProperty(defaultValue = "100000") int futureGenerationLimit,
        @ConfigProperty(defaultValue = "50") int criticalQuorumSoftening) {}

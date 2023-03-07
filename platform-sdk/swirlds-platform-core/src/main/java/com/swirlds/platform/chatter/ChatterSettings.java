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

package com.swirlds.platform.chatter;

import java.time.Duration;

/**
 * @deprecated will be replaced by the {@link com.swirlds.config.api.Configuration} API in near future. If you need
 * 		to use this class please try to do as less static access as possible.
 */
@Deprecated(forRemoval = true)
public interface ChatterSettings {
    /**
     * Whether to use chatter for event gossiping and creation
     *
     * @return if true, we should use chatter, if false, it will use the sync protocol
     */
    boolean isChatterUsed();

    /**
     * @return how many events do we attempt to create per second when running chatter
     */
    int getAttemptedChatterEventPerSecond();

    /**
     * The fraction of neighbors we should be chattering with in order to create events. should be between 0 and 1
     *
     * @return the fraction that is between 0 and 1
     */
    double getChatteringCreationThreshold();

    /**
     * If the size of the intake queue is higher than this value, don't create events
     *
     * @return the intake queue threshold
     */
    int getChatterIntakeThrottle();

    /**
     * Events by other creators are not set immediately to other nodes. We wait a while to see if they will send us a
     * descriptor of an event. We wait the amount of time the peer tells us it takes it to process an event plus the
     * estimated time it takes the descriptor to this node, plus a constant. This value is the constant.
     *
     * @return the delay for other events
     */
    Duration getOtherEventDelay();

    /**
     * @return the capacity of each of the neighbour queues for sending self events
     */
    int getSelfEventQueueCapacity();

    /**
     * @return the capacity of each of the neighbour queues for sending other events
     */
    int getOtherEventQueueCapacity();

    /**
     * @return the capacity of each of the neighbour queues for sending event descriptors
     */
    int getDescriptorQueueCapacity();

    /**
     * @return the interval at which to send each peer processing time messages
     */
    Duration getProcessingTimeInterval();

    /**
     * @return the interval at which to send each peer heartbeats
     */
    Duration getHeartbeatInterval();

    /**
     * @return the number of non-ancient generations we are willing to accept from a peer
     */
    int getFutureGenerationLimit();

    /**
     * @return the number of events by which to 'soften' the critical quorum threshold
     */
    int getCriticalQuorumSoftening();
}

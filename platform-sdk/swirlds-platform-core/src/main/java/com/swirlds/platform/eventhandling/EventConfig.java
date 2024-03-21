/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.eventhandling;

import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import com.swirlds.platform.event.AncientMode;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Configuration for event handling inside the platform.
 *
 * @param maxEventQueueForCons              max events that can be put in the forCons queue (q2) in
 *                                          ConsensusRoundHandler (0 for infinity)
 * @param eventIntakeQueueThrottleSize      The value for the event intake queue at which the node should stop syncing
 * @param eventIntakeQueueSize              The size of the event intake queue,
 *                                          {@link QueueThreadConfiguration#UNLIMITED_CAPACITY} for unbounded. It is
 *                                          best that this queue is large, but not unbounded. Filling it up can cause
 *                                          sync threads to drop TCP connections, but leaving it unbounded can cause out
 *                                          of memory errors, even with the {@link #eventIntakeQueueThrottleSize},
 *                                          because syncs that started before the throttle engages can grow the queue to
 *                                          very large sizes on larger networks.
 * @param randomEventProbability            The probability that after a sync, a node will create an event with a random
 *                                          other parent. The probability is 1 in X, where X is the value of
 *                                          randomEventProbability. A value of 0 means that a node will not create any
 *                                          random events.
 *                                          <p>
 *                                          This feature is used to get consensus on events with no descendants which
 *                                          are created by nodes who go offline.
 * @param staleEventPreventionThreshold     A setting used to prevent a node from generating events that will probably
 *                                          become stale. This value is multiplied by the address book size and compared
 *                                          to the number of events received in a sync. If
 *                                          ({@code numEventsReceived > staleEventPreventionThreshold *
 *                                          addressBookSize}) then we will not create an event for that sync, to reduce
 *                                          the probability of creating an event that will become stale.
 * @param rescueChildlessInverseProbability The probability that we will create a child for a childless event. The
 *                                          probability is 1 / X, where X is the value of
 *                                          rescueChildlessInverseProbability. A value of 0 means that a node will not
 *                                          create any children for childless events.
 * @param eventStreamQueueCapacity          capacity of the blockingQueue from which we take events and write to
 *                                          EventStream files
 * @param eventsLogPeriod                   period of generating eventStream file
 * @param eventsLogDir                      eventStream files will be generated in this directory.
 * @param enableEventStreaming              enable stream event to server.
 * @param prehandlePoolSize                 the size of the thread pool used for prehandling transactions
 * @param useBirthRoundAncientThreshold     if true, use birth rounds instead of generations for deciding if an event is
 *                                          ancient or not. Once this setting has been enabled on a network, it can
 *                                          never be disabled again (migration pathway is one-way).
 * @param useOldStyleIntakeQueue            if true then use an old style queue between gossip and the intake queue
 */
@ConfigData("event")
public record EventConfig(
        @ConfigProperty(defaultValue = "1000") int maxEventQueueForCons,
        @ConfigProperty(defaultValue = "1000") int eventIntakeQueueThrottleSize,
        @ConfigProperty(defaultValue = "10000") int eventIntakeQueueSize,
        @ConfigProperty(defaultValue = "0") int randomEventProbability,
        @ConfigProperty(defaultValue = "5") int staleEventPreventionThreshold,
        @ConfigProperty(defaultValue = "10") int rescueChildlessInverseProbability,
        @ConfigProperty(defaultValue = "5000") int eventStreamQueueCapacity,
        @ConfigProperty(defaultValue = "5") long eventsLogPeriod,
        @ConfigProperty(defaultValue = "/opt/hgcapp/eventsStreams") String eventsLogDir,
        @ConfigProperty(defaultValue = "true") boolean enableEventStreaming,
        @ConfigProperty(defaultValue = "8") int prehandlePoolSize,
        @ConfigProperty(defaultValue = "false") boolean useBirthRoundAncientThreshold,
        @ConfigProperty(defaultValue = "true") boolean useOldStyleIntakeQueue) {

    /**
     * @return the {@link AncientMode} based on useBirthRoundAncientThreshold
     */
    @NonNull
    public AncientMode getAncientMode() {
        if (useBirthRoundAncientThreshold()) {
            return AncientMode.BIRTH_ROUND_THRESHOLD;
        } else {
            return AncientMode.GENERATION_THRESHOLD;
        }
    }
}

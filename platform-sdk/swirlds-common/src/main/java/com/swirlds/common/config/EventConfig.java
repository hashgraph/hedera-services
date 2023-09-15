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

package com.swirlds.common.config;

import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

/**
 * Configuration for event handling inside the platform.
 * <p>
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
 * @param eventStreamQueueCapacity          capacity of the blockingQueue from which we take events and write to
 *                                          EventStream files
 * @param eventsLogPeriod                   period of generating eventStream file
 * @param eventsLogDir                      eventStream files will be generated in this directory.
 * @param enableEventStreaming              enable stream event to server.
 * @param asyncPrehandle                    if true then prehandle transactions asynchronously in a thread pool, if
 *                                          false then prehandle happens on the intake thread
 * @param prehandlePoolSize                 the size of the thread pool used for prehandling transactions, if enabled
 */
@ConfigData("event")
public record EventConfig(
        @ConfigProperty(defaultValue = "1000") int maxEventQueueForCons,
        @ConfigProperty(defaultValue = "1000") int eventIntakeQueueThrottleSize,
        @ConfigProperty(defaultValue = "10000") int eventIntakeQueueSize,
        @ConfigProperty(defaultValue = "5000") int eventStreamQueueCapacity,
        @ConfigProperty(defaultValue = "5") long eventsLogPeriod,
        @ConfigProperty(defaultValue = "./eventstreams") String eventsLogDir,
        @ConfigProperty(defaultValue = "true") boolean enableEventStreaming,
        @ConfigProperty(defaultValue = "true") boolean asyncPrehandle,
        @ConfigProperty(defaultValue = "8") int prehandlePoolSize) {}

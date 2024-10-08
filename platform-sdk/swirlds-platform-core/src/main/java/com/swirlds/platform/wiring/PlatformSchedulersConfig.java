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

package com.swirlds.platform.wiring;

import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerConfiguration;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import java.time.Duration;

/**
 * Contains configuration values for the platform schedulers.
 *
 * @param eventHasherUnhandledCapacity         number of unhandled tasks allowed in the event hasher scheduler
 * @param internalEventValidator               configuration for the internal event validator scheduler
 * @param eventDeduplicator                    configuration for the event deduplicator scheduler
 * @param eventSignatureValidator              configuration for the event signature validator scheduler
 * @param orphanBuffer                         configuration for the orphan buffer scheduler
 * @param consensusEngine                      configuration for the consensus engine scheduler
 * @param eventCreationManager                 configuration for the event creation manager scheduler
 * @param selfEventSigner                      configuration for the self event signer scheduler
 * @param stateSigner                          configuration for the state signer scheduler
 * @param pcesWriter                           configuration for the preconsensus event writer scheduler
 * @param pcesSequencer                        configuration for the preconsensus event sequencer scheduler
 * @param applicationTransactionPrehandler     configuration for the application transaction prehandler scheduler
 * @param stateSignatureCollector              configuration for the state signature collector scheduler
 * @param transactionHandler                   configuration for the transaction handler scheduler
 * @param issDetector                          configuration for the ISS detector scheduler
 * @param issHandler                           configuration for the ISS handler scheduler
 * @param hashLogger                           configuration for the hash logger scheduler
 * @param latestCompleteStateNotifier          configuration for the latest complete state notifier scheduler
 * @param stateHasher                          configuration for the state hasher scheduler
 * @param stateGarbageCollector                configuration for the state garbage collector scheduler
 * @param stateGarbageCollectorHeartbeatPeriod the frequency that heartbeats should be sent to the state garbage
 *                                             collector
 * @param platformPublisher                    configuration for the platform publisher scheduler
 * @param consensusEventStream                 configuration for the consensus event stream scheduler
 * @param roundDurabilityBuffer                configuration for the round durability buffer scheduler
 * @param signedStateSentinel                  configuration for the signed state sentinel scheduler
 * @param signedStateSentinelHeartbeatPeriod   the frequency that heartbeats should be sent to the signed state
 *                                             sentinel
 * @param statusStateMachine                   configuration for the status state machine scheduler
 * @param staleEventDetector                   configuration for the stale event detector scheduler
 * @param transactionResubmitter               configuration for the transaction resubmitter scheduler
 * @param transactionPool                      configuration for the transaction pool scheduler
 * @param gossip                               configuration for the gossip scheduler
 * @param eventHasher                          configuration for the event hasher scheduler
 * @param postHashCollector                    configuration for the post hash collector scheduler
 * @param branchDetector                       configuration for the branch detector scheduler
 * @param branchReporter                       configuration for the branch reporter scheduler
 */
@ConfigData("platformSchedulers")
public record PlatformSchedulersConfig(
        @ConfigProperty(defaultValue = "500") int eventHasherUnhandledCapacity,
        @ConfigProperty(defaultValue = "CONCURRENT CAPACITY(500) FLUSHABLE UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration internalEventValidator,
        @ConfigProperty(defaultValue = "SEQUENTIAL CAPACITY(5000) FLUSHABLE UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration eventDeduplicator,
        @ConfigProperty(defaultValue = "CONCURRENT CAPACITY(500) FLUSHABLE UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration eventSignatureValidator,
        @ConfigProperty(defaultValue = "SEQUENTIAL CAPACITY(500) FLUSHABLE UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration orphanBuffer,
        @ConfigProperty(
                        defaultValue =
                                "SEQUENTIAL_THREAD CAPACITY(500) FLUSHABLE SQUELCHABLE UNHANDLED_TASK_METRIC BUSY_FRACTION_METRIC")
                TaskSchedulerConfiguration consensusEngine,
        @ConfigProperty(defaultValue = "SEQUENTIAL CAPACITY(500) FLUSHABLE SQUELCHABLE UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration eventCreationManager,
        @ConfigProperty(defaultValue = "DIRECT") TaskSchedulerConfiguration selfEventSigner,
        @ConfigProperty(defaultValue = "SEQUENTIAL_THREAD CAPACITY(20) UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration stateSnapshotManager,
        @ConfigProperty(defaultValue = "SEQUENTIAL CAPACITY(10) UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration stateSigner,
        @ConfigProperty(defaultValue = "SEQUENTIAL_THREAD CAPACITY(500) UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration pcesWriter,
        @ConfigProperty(defaultValue = "SEQUENTIAL CAPACITY(500) FLUSHABLE UNHANDLED_TASK_METRIC BUSY_FRACTION_METRIC")
                TaskSchedulerConfiguration pcesInlineWriter,
        @ConfigProperty(defaultValue = "DIRECT") TaskSchedulerConfiguration pcesSequencer,
        @ConfigProperty(defaultValue = "CONCURRENT CAPACITY(500) FLUSHABLE UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration applicationTransactionPrehandler,
        @ConfigProperty(defaultValue = "SEQUENTIAL CAPACITY(500) FLUSHABLE UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration stateSignatureCollector,
        @ConfigProperty(
                        defaultValue =
                                "SEQUENTIAL_THREAD CAPACITY(30) FLUSHABLE SQUELCHABLE UNHANDLED_TASK_METRIC BUSY_FRACTION_METRIC")
                TaskSchedulerConfiguration transactionHandler,
        @ConfigProperty(defaultValue = "SEQUENTIAL CAPACITY(500) UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration issDetector,
        @ConfigProperty(defaultValue = "DIRECT") TaskSchedulerConfiguration issHandler,
        @ConfigProperty(defaultValue = "SEQUENTIAL CAPACITY(100) UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration hashLogger,
        @ConfigProperty(defaultValue = "SEQUENTIAL CAPACITY(5) UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration latestCompleteStateNotifier,
        @ConfigProperty(
                        defaultValue =
                                "SEQUENTIAL_THREAD CAPACITY(5) FLUSHABLE UNHANDLED_TASK_METRIC BUSY_FRACTION_METRIC")
                TaskSchedulerConfiguration stateHasher,
        @ConfigProperty(defaultValue = "SEQUENTIAL CAPACITY(60) UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration stateGarbageCollector,
        @ConfigProperty(defaultValue = "200ms") Duration stateGarbageCollectorHeartbeatPeriod,
        @ConfigProperty(defaultValue = "SEQUENTIAL UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration signedStateSentinel,
        @ConfigProperty(defaultValue = "10s") Duration signedStateSentinelHeartbeatPeriod,
        @ConfigProperty(defaultValue = "SEQUENTIAL CAPACITY(500) UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration platformPublisher,
        @ConfigProperty(defaultValue = "DIRECT_THREADSAFE") TaskSchedulerConfiguration consensusEventStream,
        @ConfigProperty(defaultValue = "SEQUENTIAL CAPACITY(5) FLUSHABLE UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration roundDurabilityBuffer,
        @ConfigProperty(defaultValue = "SEQUENTIAL CAPACITY(500) FLUSHABLE UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration statusStateMachine,
        @ConfigProperty(defaultValue = "SEQUENTIAL CAPACITY(500) FLUSHABLE SQUELCHABLE UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration staleEventDetector,
        @ConfigProperty(defaultValue = "SEQUENTIAL CAPACITY(500) UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration transactionResubmitter,
        @ConfigProperty(defaultValue = "DIRECT_THREADSAFE") TaskSchedulerConfiguration transactionPool,
        @ConfigProperty(defaultValue = "SEQUENTIAL CAPACITY(500) FLUSHABLE UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration gossip,
        @ConfigProperty(defaultValue = "CONCURRENT CAPACITY(5000) UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration eventHasher,
        @ConfigProperty(defaultValue = "SEQUENTIAL CAPACITY(-1) UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration postHashCollector,
        @ConfigProperty(defaultValue = "SEQUENTIAL CAPACITY(500) FLUSHABLE UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration branchDetector,
        @ConfigProperty(defaultValue = "SEQUENTIAL CAPACITY(500) FLUSHABLE UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration branchReporter,
        @ConfigProperty(defaultValue = "false") boolean hashCollectorEnabled) {}

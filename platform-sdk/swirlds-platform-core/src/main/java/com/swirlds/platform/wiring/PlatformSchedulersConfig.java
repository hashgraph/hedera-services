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
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.DefaultValue;
import java.time.Duration;

/**
 * Contains configuration values for the platform schedulers.
 *
 * @param defaultPoolMultiplier                             used when calculating the size of the default platform fork
 *                                                          join pool. Maximum parallelism in this pool is calculated as
 *                                                          max(1, (defaultPoolMultipler * [number of processors] +
 *                                                          defaultPoolConstant)).
 * @param defaultPoolConstant                               used when calculating the size of the default platform fork
 *                                                          join pool. Maximum parallelism in this pool is calculated as
 *                                                          max(1, (defaultPoolMultipler * [number of processors] +
 *                                                          defaultPoolConstant)). It is legal for this constant to be a
 *                                                          negative number.
 * @param eventHasherUnhandledCapacity                      number of unhandled tasks allowed in the event hasher
 *                                                          scheduler
 * @param internalEventValidator                            configuration for the internal event validator scheduler
 * @param eventDeduplicator                                 configuration for the event deduplicator scheduler
 * @param eventSignatureValidator                           configuration for the event signature validator scheduler
 * @param orphanBuffer                                      configuration for the orphan buffer scheduler scheduler
 * @param consensusEngine                                   configuration for the consensus engine scheduler
 * @param inOrderLinker                                     configuration for the in order linker scheduler
 * @param eventCreationManager                              configuration for the event creation manager scheduler
 * @param selfEventSigner                                   configuration for the self event signer scheduler
 * @param signedStateFileManagerSchedulerType               the signed state file manager scheduler type
 * @param signedStateFileManagerUnhandledCapacity           number of unhandled tasks allowed in the signed state file
 *                                                          manager scheduler
 * @param stateSignerSchedulerType                          the state signer scheduler type
 * @param stateSignerUnhandledCapacity                      number of unhandled tasks allowed in the state signer
 *                                                          scheduler, default is -1 (unlimited)
 * @param pcesWriterSchedulerType                           the preconsensus event writer scheduler type
 * @param pcesWriterUnhandledCapacity                       number of unhandled tasks allowed in the preconsensus event
 *                                                          writer scheduler
 * @param pcesSequencer                                     configuration for the preconsensus event sequencer
 *                                                          scheduler
 * @param eventDurabilityNexusSchedulerType                 the durability nexus scheduler type
 * @param eventDurabilityNexusUnhandledTaskCapacity         number of unhandled tasks allowed in the durability nexus
 *                                                          scheduler
 * @param applicationTransactionPrehandlerSchedulerType     the application transaction prehandler scheduler type
 * @param applicationTransactionPrehandlerUnhandledCapacity number of unhandled tasks allowed for the application
 *                                                          transaction prehandler
 * @param stateSignatureCollectorSchedulerType              the state signature collector scheduler type
 * @param stateSignatureCollectorUnhandledCapacity          number of unhandled tasks allowed for the state signature
 *                                                          collector
 * @param shadowgraphSchedulerType                          the shadowgraph scheduler type
 * @param shadowgraphUnhandledCapacity                      number of unhandled tasks allowed for the shadowgraph
 * @param consensusRoundHandlerSchedulerType                the consensus round handler scheduler type
 * @param consensusRoundHandlerUnhandledCapacity            number of unhandled tasks allowed for the consensus round
 *                                                          handler
 * @param runningEventHasher                                configuration for the running event hasher scheduler
 * @param issDetectorSchedulerType                          the ISS detector scheduler type
 * @param issDetectorUnhandledCapacity                      number of unhandled tasks allowed for the ISS detector
 * @param hashLoggerSchedulerType                           the hash logger scheduler type
 * @param hashLoggerUnhandledTaskCapacity                   number of unhandled tasks allowed in the hash logger task
 *                                                          scheduler
 * @param completeStateNotifierUnhandledCapacity            number of unhandled tasks allowed for the state completion
 *                                                          notifier
 * @param stateHasherSchedulerType                          the state hasher scheduler type
 * @param stateHasherUnhandledCapacity                      number of unhandled tasks allowed for the state hasher
 * @param stateGarbageCollector                             configuration for the state garbage collector scheduler
 * @param stateGarbageCollectorHeartbeatPeriod              the frequency that heartbeats should be sent to the state
 *                                                          garbage collector
 * @param platformPublisher                                 configuration for the platform publisher scheduler
 * @param consensusEventStream                              configuration for the consensus event stream scheduler
 */
@ConfigData("platformSchedulers")
public record PlatformSchedulersConfig(
        @DefaultValue("1.0") double defaultPoolMultiplier,
        @DefaultValue("0") int defaultPoolConstant,
        @DefaultValue("500") int eventHasherUnhandledCapacity,
        @DefaultValue("SEQUENTIAL CAPACITY(500) FLUSHABLE UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration internalEventValidator,
        @DefaultValue("SEQUENTIAL CAPACITY(500) FLUSHABLE UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration eventDeduplicator,
        @DefaultValue("SEQUENTIAL CAPACITY(500) FLUSHABLE UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration eventSignatureValidator,
        @DefaultValue("SEQUENTIAL CAPACITY(500) FLUSHABLE UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration orphanBuffer,
        @DefaultValue(
                        "SEQUENTIAL_THREAD CAPACITY(500) FLUSHABLE SQUELCHABLE UNHANDLED_TASK_METRIC BUSY_FRACTION_METRIC")
                TaskSchedulerConfiguration consensusEngine,
        @DefaultValue("SEQUENTIAL CAPACITY(500) FLUSHABLE UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration inOrderLinker,
        @DefaultValue("SEQUENTIAL CAPACITY(500) FLUSHABLE SQUELCHABLE UNHANDLED_TASK_METRIC")
                TaskSchedulerConfiguration eventCreationManager,
        @DefaultValue("DIRECT") TaskSchedulerConfiguration selfEventSigner,
        @DefaultValue("SEQUENTIAL_THREAD") TaskSchedulerType signedStateFileManagerSchedulerType,
        @DefaultValue("20") int signedStateFileManagerUnhandledCapacity,
        @DefaultValue("SEQUENTIAL_THREAD") TaskSchedulerType stateSignerSchedulerType,
        @DefaultValue("-1") int stateSignerUnhandledCapacity,
        @DefaultValue("SEQUENTIAL_THREAD") TaskSchedulerType pcesWriterSchedulerType,
        @DefaultValue("500") int pcesWriterUnhandledCapacity,
        @DefaultValue("DIRECT") TaskSchedulerConfiguration pcesSequencer,
        @DefaultValue("DIRECT") TaskSchedulerType eventDurabilityNexusSchedulerType,
        @DefaultValue("-1") int eventDurabilityNexusUnhandledTaskCapacity,
        @DefaultValue("CONCURRENT") TaskSchedulerType applicationTransactionPrehandlerSchedulerType,
        @DefaultValue("500") int applicationTransactionPrehandlerUnhandledCapacity,
        @DefaultValue("SEQUENTIAL") TaskSchedulerType stateSignatureCollectorSchedulerType,
        @DefaultValue("500") int stateSignatureCollectorUnhandledCapacity,
        @DefaultValue("SEQUENTIAL") TaskSchedulerType shadowgraphSchedulerType,
        @DefaultValue("500") int shadowgraphUnhandledCapacity,
        @DefaultValue("SEQUENTIAL_THREAD") TaskSchedulerType consensusRoundHandlerSchedulerType,
        @DefaultValue("5") int consensusRoundHandlerUnhandledCapacity,
        @DefaultValue("SEQUENTIAL CAPACITY(5) UNHANDLED_TASK_METRIC BUSY_FRACTION_METRIC")
                TaskSchedulerConfiguration runningEventHasher,
        @DefaultValue("SEQUENTIAL") TaskSchedulerType issDetectorSchedulerType,
        @DefaultValue("500") int issDetectorUnhandledCapacity,
        @DefaultValue("SEQUENTIAL_THREAD") TaskSchedulerType hashLoggerSchedulerType,
        @DefaultValue("100") int hashLoggerUnhandledTaskCapacity,
        @DefaultValue("1000") int completeStateNotifierUnhandledCapacity,
        @DefaultValue("SEQUENTIAL_THREAD") TaskSchedulerType stateHasherSchedulerType,
        @DefaultValue("2") int stateHasherUnhandledCapacity,
        @DefaultValue("SEQUENTIAL CAPACITY(60) UNHANDLED_TASK_METRIC") TaskSchedulerConfiguration stateGarbageCollector,
        @DefaultValue("200ms") Duration stateGarbageCollectorHeartbeatPeriod,
        @DefaultValue("SEQUENTIAL CAPACITY(500) UNHANDLED_TASK_METRIC") TaskSchedulerConfiguration platformPublisher,
        @DefaultValue("DIRECT_THREADSAFE") TaskSchedulerConfiguration consensusEventStream) {}

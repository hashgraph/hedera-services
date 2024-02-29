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

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.stream.RunningEventHashUpdate;
import com.swirlds.common.wiring.counters.ObjectCounter;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.StateSavingResult;
import com.swirlds.platform.system.state.notifications.IssNotification;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * The {@link TaskScheduler}s used by the platform.
 *
 * @param eventHasherScheduler                      the scheduler for the event hasher
 * @param postHashCollectorScheduler                the scheduler for the post hash collector
 * @param internalEventValidatorScheduler           the scheduler for the internal event validator
 * @param eventDeduplicatorScheduler                the scheduler for the event deduplicator
 * @param eventSignatureValidatorScheduler          the scheduler for the event signature validator
 * @param orphanBufferScheduler                     the scheduler for the orphan buffer
 * @param inOrderLinkerScheduler                    the scheduler for the in-order linker
 * @param consensusEngineScheduler                  the scheduler for the consensus engine
 * @param eventCreationManagerScheduler             the scheduler for the event creation manager
 * @param signedStateFileManagerScheduler           the scheduler for the signed state file manager
 * @param stateSignerScheduler                      the scheduler for the state signer
 * @param pcesReplayerScheduler                     the scheduler for the pces replayer
 * @param pcesWriterScheduler                       the scheduler for the pces writer
 * @param pcesSequencerScheduler                    the scheduler for the pces sequencer
 * @param eventDurabilityNexusScheduler             the scheduler for the event durability nexus
 * @param applicationTransactionPrehandlerScheduler the scheduler for the application transaction prehandler
 * @param stateSignatureCollectorScheduler          the scheduler for the state signature collector
 * @param shadowgraphScheduler                      the scheduler for the shadowgraph
 * @param consensusRoundHandlerScheduler            the scheduler for the consensus round handler
 * @param runningHashUpdateScheduler                the scheduler for the running hash updater
 * @param futureEventBufferScheduler                the scheduler for the future event buffer
 * @param issDetectorScheduler                      the scheduler for the iss detector
 * @param issHandlerScheduler                       the scheduler for the iss handler
 * @param hashLoggerScheduler                       the scheduler for the hash logger
 * @param latestCompleteStateNotificationScheduler  the scheduler for the latest complete state notifier
 */
public record PlatformSchedulers(
        @NonNull TaskScheduler<GossipEvent> eventHasherScheduler,
        @NonNull TaskScheduler<GossipEvent> postHashCollectorScheduler,
        @NonNull TaskScheduler<GossipEvent> internalEventValidatorScheduler,
        @NonNull TaskScheduler<GossipEvent> eventDeduplicatorScheduler,
        @NonNull TaskScheduler<GossipEvent> eventSignatureValidatorScheduler,
        @NonNull TaskScheduler<List<GossipEvent>> orphanBufferScheduler,
        @NonNull TaskScheduler<EventImpl> inOrderLinkerScheduler,
        @NonNull TaskScheduler<List<ConsensusRound>> consensusEngineScheduler,
        @NonNull TaskScheduler<GossipEvent> eventCreationManagerScheduler,
        @NonNull TaskScheduler<StateSavingResult> signedStateFileManagerScheduler,
        @NonNull TaskScheduler<StateSignatureTransaction> stateSignerScheduler,
        @NonNull TaskScheduler<DoneStreamingPcesTrigger> pcesReplayerScheduler,
        @NonNull TaskScheduler<Long> pcesWriterScheduler,
        @NonNull TaskScheduler<GossipEvent> pcesSequencerScheduler,
        @NonNull TaskScheduler<Void> eventDurabilityNexusScheduler,
        @NonNull TaskScheduler<Void> applicationTransactionPrehandlerScheduler,
        @NonNull TaskScheduler<List<ReservedSignedState>> stateSignatureCollectorScheduler,
        @NonNull TaskScheduler<Void> shadowgraphScheduler,
        @NonNull TaskScheduler<Void> consensusRoundHandlerScheduler,
        @NonNull TaskScheduler<Void> eventStreamManagerScheduler,
        @NonNull TaskScheduler<RunningEventHashUpdate> runningHashUpdateScheduler,
        @NonNull TaskScheduler<List<GossipEvent>> futureEventBufferScheduler,
        @NonNull TaskScheduler<List<IssNotification>> issDetectorScheduler,
        @NonNull TaskScheduler<Void> issHandlerScheduler,
        @NonNull TaskScheduler<Void> hashLoggerScheduler,
        @NonNull TaskScheduler<Void> latestCompleteStateNotificationScheduler) {

    /**
     * Instantiate the schedulers for the platform, for the given wiring model
     *
     * @param context              the platform context
     * @param model                the wiring model
     * @param hashingObjectCounter the object counter for the event hasher and post hash collector
     * @return the instantiated platform schedulers
     */
    public static PlatformSchedulers create(
            @NonNull final PlatformContext context,
            @NonNull final WiringModel model,
            @NonNull final ObjectCounter hashingObjectCounter) {
        final PlatformSchedulersConfig config =
                context.getConfiguration().getConfigData(PlatformSchedulersConfig.class);

        return new PlatformSchedulers(
                model.schedulerBuilder("eventHasher")
                        .withType(TaskSchedulerType.CONCURRENT)
                        .withOnRamp(hashingObjectCounter)
                        .withExternalBackPressure(true)
                        .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                        .build()
                        .cast(),
                // don't define a capacity for the postHashCollector, so that the postHashCollector will not apply
                // backpressure to the hasher
                model.schedulerBuilder("postHashCollector")
                        .withType(TaskSchedulerType.SEQUENTIAL)
                        .withOffRamp(hashingObjectCounter)
                        .withExternalBackPressure(true)
                        .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                        .build()
                        .cast(),
                model.schedulerBuilder("internalEventValidator")
                        .withType(config.internalEventValidatorSchedulerType())
                        .withUnhandledTaskCapacity(config.internalEventValidatorUnhandledCapacity())
                        .withFlushingEnabled(true)
                        .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                        .build()
                        .cast(),
                model.schedulerBuilder("eventDeduplicator")
                        .withType(config.eventDeduplicatorSchedulerType())
                        .withUnhandledTaskCapacity(config.eventDeduplicatorUnhandledCapacity())
                        .withFlushingEnabled(true)
                        .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                        .build()
                        .cast(),
                model.schedulerBuilder("eventSignatureValidator")
                        .withType(config.eventSignatureValidatorSchedulerType())
                        .withUnhandledTaskCapacity(config.eventSignatureValidatorUnhandledCapacity())
                        .withFlushingEnabled(true)
                        .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                        .build()
                        .cast(),
                model.schedulerBuilder("orphanBuffer")
                        .withType(config.orphanBufferSchedulerType())
                        .withUnhandledTaskCapacity(config.orphanBufferUnhandledCapacity())
                        .withFlushingEnabled(true)
                        .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                        .build()
                        .cast(),
                model.schedulerBuilder("inOrderLinker")
                        .withType(config.inOrderLinkerSchedulerType())
                        .withUnhandledTaskCapacity(config.inOrderLinkerUnhandledCapacity())
                        .withFlushingEnabled(true)
                        .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                        .build()
                        .cast(),
                model.schedulerBuilder("consensusEngine")
                        .withType(config.consensusEngineSchedulerType())
                        .withUnhandledTaskCapacity(config.consensusEngineUnhandledCapacity())
                        .withFlushingEnabled(true)
                        .withSquelchingEnabled(true)
                        .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                        .build()
                        .cast(),
                model.schedulerBuilder("eventCreationManager")
                        .withType(config.eventCreationManagerSchedulerType())
                        .withUnhandledTaskCapacity(config.eventCreationManagerUnhandledCapacity())
                        .withFlushingEnabled(true)
                        .withSquelchingEnabled(true)
                        .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                        .build()
                        .cast(),
                model.schedulerBuilder("signedStateFileManager")
                        .withType(config.signedStateFileManagerSchedulerType())
                        .withUnhandledTaskCapacity(config.signedStateFileManagerUnhandledCapacity())
                        .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                        .build()
                        .cast(),
                model.schedulerBuilder("stateSigner")
                        .withType(config.stateSignerSchedulerType())
                        .withUnhandledTaskCapacity(config.stateSignerUnhandledCapacity())
                        .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                        .build()
                        .cast(),
                model.schedulerBuilder("pcesReplayer")
                        .withType(TaskSchedulerType.DIRECT)
                        .build()
                        .cast(),
                model.schedulerBuilder("pcesWriter")
                        .withType(config.pcesWriterSchedulerType())
                        .withUnhandledTaskCapacity(config.pcesWriterUnhandledCapacity())
                        .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                        .build()
                        .cast(),
                model.schedulerBuilder("pcesSequencer")
                        .withType(config.pcesSequencerSchedulerType())
                        .withUnhandledTaskCapacity(config.pcesSequencerUnhandledTaskCapacity())
                        .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                        .build()
                        .cast(),
                model.schedulerBuilder("eventDurabilityNexus")
                        .withType(config.eventDurabilityNexusSchedulerType())
                        .withUnhandledTaskCapacity(config.eventDurabilityNexusUnhandledTaskCapacity())
                        .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                        .build()
                        .cast(),
                model.schedulerBuilder("applicationTransactionPrehandler")
                        .withType(config.applicationTransactionPrehandlerSchedulerType())
                        .withUnhandledTaskCapacity(config.applicationTransactionPrehandlerUnhandledCapacity())
                        .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                        .withFlushingEnabled(true)
                        .build()
                        .cast(),
                model.schedulerBuilder("stateSignatureCollector")
                        .withType(config.stateSignatureCollectorSchedulerType())
                        .withUnhandledTaskCapacity(config.stateSignatureCollectorUnhandledCapacity())
                        .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                        .withFlushingEnabled(true)
                        .build()
                        .cast(),
                model.schedulerBuilder("shadowgraph")
                        .withType(config.shadowgraphSchedulerType())
                        .withUnhandledTaskCapacity(config.shadowgraphUnhandledCapacity())
                        .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                        .withFlushingEnabled(true)
                        .build()
                        .cast(),
                // the literal "consensusRoundHandler" is used by the app to log on the transaction handling thread.
                // Do not modify, unless you also change the TRANSACTION_HANDLING_THREAD_NAME constant
                model.schedulerBuilder("consensusRoundHandler")
                        .withType(config.consensusRoundHandlerSchedulerType())
                        .withUnhandledTaskCapacity(config.consensusRoundHandlerUnhandledCapacity())
                        .withMetricsBuilder(model.metricsBuilder()
                                .withUnhandledTaskMetricEnabled(true)
                                .withBusyFractionMetricsEnabled(true))
                        .withFlushingEnabled(true)
                        .withSquelchingEnabled(true)
                        .build()
                        .cast(),
                model.schedulerBuilder("eventStreamManager")
                        .withType(TaskSchedulerType.DIRECT_THREADSAFE)
                        .build()
                        .cast(),
                model.schedulerBuilder("runningHashUpdate")
                        .withType(TaskSchedulerType.DIRECT_THREADSAFE)
                        .build()
                        .cast(),
                model.schedulerBuilder("futureEventBuffer")
                        .withType(config.futureEventBufferSchedulerType())
                        .withUnhandledTaskCapacity(config.futureEventBufferUnhandledCapacity())
                        .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                        .withFlushingEnabled(true)
                        .build()
                        .cast(),
                model.schedulerBuilder("issDetector")
                        .withType(config.issDetectorSchedulerType())
                        .withUnhandledTaskCapacity(config.issDetectorUnhandledCapacity())
                        .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                        .build()
                        .cast(),
                model.schedulerBuilder("issHandler")
                        .withType(TaskSchedulerType.DIRECT)
                        .build()
                        .cast(),
                model.schedulerBuilder("hashLogger")
                        .withType(config.hashLoggerSchedulerType())
                        .withUnhandledTaskCapacity(config.hashLoggerUnhandledTaskCapacity())
                        .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                        .build()
                        .cast(),
                model.schedulerBuilder("latestCompleteStateNotification")
                        .withType(TaskSchedulerType.SEQUENTIAL_THREAD)
                        .withUnhandledTaskCapacity(config.completeStateNotifierUnhandledCapacity())
                        .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                        .build()
                        .cast());
    }
}

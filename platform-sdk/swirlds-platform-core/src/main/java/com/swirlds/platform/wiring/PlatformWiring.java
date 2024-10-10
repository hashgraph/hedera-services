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

import static com.swirlds.common.wiring.model.diagram.HyperlinkBuilder.platformCoreHyperlink;
import static com.swirlds.common.wiring.schedulers.builders.TaskSchedulerConfiguration.DIRECT_THREADSAFE_CONFIGURATION;
import static com.swirlds.common.wiring.schedulers.builders.TaskSchedulerConfiguration.NO_OP_CONFIGURATION;
import static com.swirlds.common.wiring.wires.SolderType.INJECT;
import static com.swirlds.common.wiring.wires.SolderType.OFFER;
import static com.swirlds.platform.event.stale.StaleEventDetectorOutput.SELF_EVENT;
import static com.swirlds.platform.event.stale.StaleEventDetectorOutput.STALE_SELF_EVENT;

import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.stream.RunningEventHashOverride;
import com.swirlds.common.wiring.component.ComponentWiring;
import com.swirlds.common.wiring.counters.BackpressureObjectCounter;
import com.swirlds.common.wiring.counters.ObjectCounter;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerBuilder;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerConfiguration;
import com.swirlds.common.wiring.transformers.RoutableData;
import com.swirlds.common.wiring.transformers.WireFilter;
import com.swirlds.common.wiring.transformers.WireTransformer;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.common.wiring.wires.output.StandardOutputWire;
import com.swirlds.platform.builder.ApplicationCallbacks;
import com.swirlds.platform.builder.PlatformComponentBuilder;
import com.swirlds.platform.components.AppNotifier;
import com.swirlds.platform.components.EventWindowManager;
import com.swirlds.platform.components.SavedStateController;
import com.swirlds.platform.components.appcomm.CompleteStateNotificationWithCleanup;
import com.swirlds.platform.components.appcomm.LatestCompleteStateNotifier;
import com.swirlds.platform.components.consensus.ConsensusEngine;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.components.transaction.system.SystemTransactionExtractionUtils;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.event.branching.BranchDetector;
import com.swirlds.platform.event.branching.BranchReporter;
import com.swirlds.platform.event.creation.EventCreationConfig;
import com.swirlds.platform.event.creation.EventCreationManager;
import com.swirlds.platform.event.deduplication.EventDeduplicator;
import com.swirlds.platform.event.hashing.EventHasher;
import com.swirlds.platform.event.orphan.OrphanBuffer;
import com.swirlds.platform.event.preconsensus.InlinePcesWriter;
import com.swirlds.platform.event.preconsensus.NoOpInlinePcesWriter;
import com.swirlds.platform.event.preconsensus.PcesConfig;
import com.swirlds.platform.event.preconsensus.PcesReplayer;
import com.swirlds.platform.event.preconsensus.PcesSequencer;
import com.swirlds.platform.event.preconsensus.PcesWriter;
import com.swirlds.platform.event.preconsensus.durability.RoundDurabilityBuffer;
import com.swirlds.platform.event.resubmitter.TransactionResubmitter;
import com.swirlds.platform.event.signing.SelfEventSigner;
import com.swirlds.platform.event.stale.StaleEventDetector;
import com.swirlds.platform.event.stale.StaleEventDetectorOutput;
import com.swirlds.platform.event.stream.ConsensusEventStream;
import com.swirlds.platform.event.validation.AddressBookUpdate;
import com.swirlds.platform.event.validation.EventSignatureValidator;
import com.swirlds.platform.event.validation.InternalEventValidator;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.platform.eventhandling.TransactionHandler;
import com.swirlds.platform.eventhandling.TransactionPrehandler;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.pool.TransactionPool;
import com.swirlds.platform.publisher.PlatformPublisher;
import com.swirlds.platform.state.hasher.StateHasher;
import com.swirlds.platform.state.hashlogger.HashLogger;
import com.swirlds.platform.state.iss.IssDetector;
import com.swirlds.platform.state.iss.IssHandler;
import com.swirlds.platform.state.nexus.LatestCompleteStateNexus;
import com.swirlds.platform.state.nexus.SignedStateNexus;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateSentinel;
import com.swirlds.platform.state.signed.StateGarbageCollector;
import com.swirlds.platform.state.signed.StateSignatureCollector;
import com.swirlds.platform.state.signer.StateSigner;
import com.swirlds.platform.state.snapshot.StateDumpRequest;
import com.swirlds.platform.state.snapshot.StateSavingResult;
import com.swirlds.platform.state.snapshot.StateSnapshotManager;
import com.swirlds.platform.system.events.BirthRoundMigrationShim;
import com.swirlds.platform.system.events.UnsignedEvent;
import com.swirlds.platform.system.state.notifications.IssNotification;
import com.swirlds.platform.system.state.notifications.StateHashedNotification;
import com.swirlds.platform.system.status.PlatformStatus;
import com.swirlds.platform.system.status.PlatformStatusConfig;
import com.swirlds.platform.system.status.StatusActionSubmitter;
import com.swirlds.platform.system.status.StatusStateMachine;
import com.swirlds.platform.system.transaction.TransactionWrapper;
import com.swirlds.platform.wiring.components.GossipWiring;
import com.swirlds.platform.wiring.components.PassThroughWiring;
import com.swirlds.platform.wiring.components.PcesReplayerWiring;
import com.swirlds.platform.wiring.components.RunningEventHashOverrideWiring;
import com.swirlds.platform.wiring.components.StateAndRound;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Encapsulates wiring for {@link com.swirlds.platform.SwirldsPlatform}.
 */
public class PlatformWiring {

    private final WiringModel model;

    private final PlatformContext platformContext;
    private final PlatformSchedulersConfig config;
    private final boolean inlinePces;

    private final ComponentWiring<EventHasher, PlatformEvent> eventHasherWiring;
    private final PassThroughWiring<PlatformEvent> postHashCollectorWiring;
    private final ComponentWiring<InternalEventValidator, PlatformEvent> internalEventValidatorWiring;
    private final ComponentWiring<EventDeduplicator, PlatformEvent> eventDeduplicatorWiring;
    private final ComponentWiring<EventSignatureValidator, PlatformEvent> eventSignatureValidatorWiring;
    private final ComponentWiring<OrphanBuffer, List<PlatformEvent>> orphanBufferWiring;
    private final ComponentWiring<ConsensusEngine, List<ConsensusRound>> consensusEngineWiring;
    private final ComponentWiring<EventCreationManager, UnsignedEvent> eventCreationManagerWiring;
    private final ComponentWiring<SelfEventSigner, PlatformEvent> selfEventSignerWiring;
    private final ComponentWiring<StateSnapshotManager, StateSavingResult> stateSnapshotManagerWiring;
    private final ComponentWiring<StateSigner, StateSignatureTransaction> stateSignerWiring;
    private final PcesReplayerWiring pcesReplayerWiring;
    private final ComponentWiring<PcesWriter, Long> pcesWriterWiring;
    private final ComponentWiring<InlinePcesWriter, PlatformEvent> pcesInlineWriterWiring;
    private final ComponentWiring<RoundDurabilityBuffer, List<ConsensusRound>> roundDurabilityBufferWiring;
    private final ComponentWiring<PcesSequencer, PlatformEvent> pcesSequencerWiring;
    private final ComponentWiring<TransactionPrehandler, Void> applicationTransactionPrehandlerWiring;
    private final ComponentWiring<StateSignatureCollector, List<ReservedSignedState>> stateSignatureCollectorWiring;
    private final GossipWiring gossipWiring;
    private final ComponentWiring<EventWindowManager, EventWindow> eventWindowManagerWiring;
    private final ComponentWiring<TransactionHandler, StateAndRound> transactionHandlerWiring;
    private final ComponentWiring<ConsensusEventStream, Void> consensusEventStreamWiring;
    private final RunningEventHashOverrideWiring runningEventHashOverrideWiring;
    private final ComponentWiring<IssDetector, List<IssNotification>> issDetectorWiring;
    private final ComponentWiring<IssHandler, Void> issHandlerWiring;
    private final ComponentWiring<HashLogger, Void> hashLoggerWiring;
    private final ComponentWiring<LatestCompleteStateNotifier, CompleteStateNotificationWithCleanup>
            latestCompleteStateNotifierWiring;
    private final ComponentWiring<SignedStateNexus, Void> latestImmutableStateNexusWiring;
    private final ComponentWiring<LatestCompleteStateNexus, Void> latestCompleteStateNexusWiring;
    private final ComponentWiring<SavedStateController, StateAndRound> savedStateControllerWiring;
    private final ComponentWiring<StateHasher, StateAndRound> stateHasherWiring;
    private final PlatformCoordinator platformCoordinator;
    private final ComponentWiring<BirthRoundMigrationShim, PlatformEvent> birthRoundMigrationShimWiring;
    private final ComponentWiring<AppNotifier, Void> notifierWiring;
    private final ComponentWiring<StateGarbageCollector, Void> stateGarbageCollectorWiring;
    private final ComponentWiring<SignedStateSentinel, Void> signedStateSentinelWiring;
    private final ComponentWiring<PlatformPublisher, Void> platformPublisherWiring;
    private final boolean publishPreconsensusEvents;
    private final boolean publishSnapshotOverrides;
    private final boolean publishStaleEvents;
    private final ComponentWiring<StaleEventDetector, List<RoutableData<StaleEventDetectorOutput>>>
            staleEventDetectorWiring;
    private final ComponentWiring<TransactionResubmitter, List<TransactionWrapper>> transactionResubmitterWiring;
    private final ComponentWiring<TransactionPool, Void> transactionPoolWiring;
    private final ComponentWiring<StatusStateMachine, PlatformStatus> statusStateMachineWiring;
    private final ComponentWiring<BranchDetector, PlatformEvent> branchDetectorWiring;
    private final ComponentWiring<BranchReporter, Void> branchReporterWiring;

    private final boolean hashCollectorEnabled;

    /**
     * Constructor.
     *
     * @param platformContext      the platform context
     * @param model                the wiring model
     * @param applicationCallbacks the application callbacks (some wires are only created if the application wants a
     *                             callback for something)
     */
    public PlatformWiring(
            @NonNull final PlatformContext platformContext,
            @NonNull final WiringModel model,
            @NonNull final ApplicationCallbacks applicationCallbacks) {

        this.platformContext = Objects.requireNonNull(platformContext);
        this.model = Objects.requireNonNull(model);

        config = platformContext.getConfiguration().getConfigData(PlatformSchedulersConfig.class);
        inlinePces = platformContext
                .getConfiguration()
                .getConfigData(ComponentWiringConfig.class)
                .inlinePces();
        hashCollectorEnabled = config.hashCollectorEnabled();

        final AncientMode ancientMode = platformContext
                .getConfiguration()
                .getConfigData(EventConfig.class)
                .getAncientMode();
        if (ancientMode == AncientMode.BIRTH_ROUND_THRESHOLD) {
            birthRoundMigrationShimWiring =
                    new ComponentWiring<>(model, BirthRoundMigrationShim.class, DIRECT_THREADSAFE_CONFIGURATION);
        } else {
            birthRoundMigrationShimWiring = null;
        }

        // Provides back pressure across both the event hasher and the post hash collector
        final ObjectCounter hashingObjectCounter;
        if (hashCollectorEnabled) {
            hashingObjectCounter = new BackpressureObjectCounter(
                    "hashingObjectCounter",
                    platformContext
                            .getConfiguration()
                            .getConfigData(PlatformSchedulersConfig.class)
                            .eventHasherUnhandledCapacity(),
                    Duration.ofNanos(100));
        } else {
            hashingObjectCounter = null;
        }

        eventHasherWiring =
                new ComponentWiring<>(model, EventHasher.class, buildEventHasherScheduler(hashingObjectCounter));

        if (hashCollectorEnabled) {
            postHashCollectorWiring = new PassThroughWiring<>(
                    model, "PlatformEvent", buildPostHashCollectorScheduler(hashingObjectCounter));
        } else {
            postHashCollectorWiring = null;
        }

        internalEventValidatorWiring =
                new ComponentWiring<>(model, InternalEventValidator.class, config.internalEventValidator());
        eventDeduplicatorWiring = new ComponentWiring<>(model, EventDeduplicator.class, config.eventDeduplicator());
        eventSignatureValidatorWiring =
                new ComponentWiring<>(model, EventSignatureValidator.class, config.eventSignatureValidator());
        orphanBufferWiring = new ComponentWiring<>(model, OrphanBuffer.class, config.orphanBuffer());
        consensusEngineWiring = new ComponentWiring<>(model, ConsensusEngine.class, config.consensusEngine());

        eventCreationManagerWiring =
                new ComponentWiring<>(model, EventCreationManager.class, config.eventCreationManager());
        selfEventSignerWiring = new ComponentWiring<>(model, SelfEventSigner.class, config.selfEventSigner());

        applicationTransactionPrehandlerWiring =
                new ComponentWiring<>(model, TransactionPrehandler.class, config.applicationTransactionPrehandler());
        stateSignatureCollectorWiring =
                new ComponentWiring<>(model, StateSignatureCollector.class, config.stateSignatureCollector());
        stateSnapshotManagerWiring =
                new ComponentWiring<>(model, StateSnapshotManager.class, config.stateSnapshotManager());
        stateSignerWiring = new ComponentWiring<>(model, StateSigner.class, config.stateSigner());
        transactionHandlerWiring = new ComponentWiring<>(model, TransactionHandler.class, config.transactionHandler());
        consensusEventStreamWiring =
                new ComponentWiring<>(model, ConsensusEventStream.class, config.consensusEventStream());
        runningEventHashOverrideWiring = RunningEventHashOverrideWiring.create(model);

        stateHasherWiring = new ComponentWiring<>(model, StateHasher.class, config.stateHasher());

        gossipWiring = new GossipWiring(platformContext, model);

        pcesReplayerWiring = PcesReplayerWiring.create(model);

        if (inlinePces) {
            // when using inline PCES, we don't need these components
            roundDurabilityBufferWiring = null;
            pcesSequencerWiring = null;
            pcesWriterWiring = null;
            pcesInlineWriterWiring = new ComponentWiring<>(model, InlinePcesWriter.class, config.pcesInlineWriter());
        } else {
            roundDurabilityBufferWiring =
                    new ComponentWiring<>(model, RoundDurabilityBuffer.class, config.roundDurabilityBuffer());
            pcesSequencerWiring = new ComponentWiring<>(model, PcesSequencer.class, config.pcesSequencer());
            pcesWriterWiring = new ComponentWiring<>(model, PcesWriter.class, config.pcesWriter());
            pcesInlineWriterWiring = null;
        }

        eventWindowManagerWiring =
                new ComponentWiring<>(model, EventWindowManager.class, DIRECT_THREADSAFE_CONFIGURATION);

        issDetectorWiring = new ComponentWiring<>(model, IssDetector.class, config.issDetector());
        issHandlerWiring = new ComponentWiring<>(model, IssHandler.class, config.issHandler());
        hashLoggerWiring = new ComponentWiring<>(model, HashLogger.class, config.hashLogger());

        latestCompleteStateNotifierWiring =
                new ComponentWiring<>(model, LatestCompleteStateNotifier.class, config.latestCompleteStateNotifier());

        latestImmutableStateNexusWiring =
                new ComponentWiring<>(model, SignedStateNexus.class, DIRECT_THREADSAFE_CONFIGURATION);
        latestCompleteStateNexusWiring =
                new ComponentWiring<>(model, LatestCompleteStateNexus.class, DIRECT_THREADSAFE_CONFIGURATION);
        savedStateControllerWiring =
                new ComponentWiring<>(model, SavedStateController.class, DIRECT_THREADSAFE_CONFIGURATION);

        notifierWiring = new ComponentWiring<>(model, AppNotifier.class, DIRECT_THREADSAFE_CONFIGURATION);

        this.publishPreconsensusEvents = applicationCallbacks.preconsensusEventConsumer() != null;
        this.publishSnapshotOverrides = applicationCallbacks.snapshotOverrideConsumer() != null;
        this.publishStaleEvents = applicationCallbacks.staleEventConsumer() != null;

        final TaskSchedulerConfiguration publisherConfiguration;
        if (publishPreconsensusEvents || publishSnapshotOverrides || publishStaleEvents) {
            publisherConfiguration = config.platformPublisher();
        } else {
            publisherConfiguration = NO_OP_CONFIGURATION;
        }
        platformPublisherWiring = new ComponentWiring<>(model, PlatformPublisher.class, publisherConfiguration);

        stateGarbageCollectorWiring =
                new ComponentWiring<>(model, StateGarbageCollector.class, config.stateGarbageCollector());
        signedStateSentinelWiring =
                new ComponentWiring<>(model, SignedStateSentinel.class, config.signedStateSentinel());
        statusStateMachineWiring = new ComponentWiring<>(model, StatusStateMachine.class, config.statusStateMachine());

        staleEventDetectorWiring = new ComponentWiring<>(model, StaleEventDetector.class, config.staleEventDetector());
        transactionResubmitterWiring =
                new ComponentWiring<>(model, TransactionResubmitter.class, config.transactionResubmitter());
        transactionPoolWiring = new ComponentWiring<>(model, TransactionPool.class, config.transactionPool());
        branchDetectorWiring = new ComponentWiring<>(model, BranchDetector.class, config.branchDetector());
        branchReporterWiring = new ComponentWiring<>(model, BranchReporter.class, config.branchReporter());

        platformCoordinator = new PlatformCoordinator(
                () -> {
                    if (hashCollectorEnabled) {
                        hashingObjectCounter.waitUntilEmpty();
                    } else {
                        eventHasherWiring.flush();
                    }
                },
                internalEventValidatorWiring,
                eventDeduplicatorWiring,
                eventSignatureValidatorWiring,
                orphanBufferWiring,
                gossipWiring,
                consensusEngineWiring,
                eventCreationManagerWiring,
                applicationTransactionPrehandlerWiring,
                stateSignatureCollectorWiring,
                transactionHandlerWiring,
                roundDurabilityBufferWiring,
                stateHasherWiring,
                staleEventDetectorWiring,
                transactionPoolWiring,
                statusStateMachineWiring,
                branchDetectorWiring,
                branchReporterWiring,
                pcesInlineWriterWiring);

        wire();
    }

    /**
     * Build the event hasher scheduler. Normally we don't build schedulers in this class, but a special exception is
     * made here because for back pressure reasons. Will be removed from this class when we implement a platform health
     * monitor.
     *
     * @param hashingObjectCounter the object counter to use for back pressure
     * @return the event hasher scheduler
     */
    @NonNull
    private TaskScheduler<PlatformEvent> buildEventHasherScheduler(@NonNull final ObjectCounter hashingObjectCounter) {
        final TaskSchedulerBuilder<Object> builder = model.schedulerBuilder("EventHasher")
                .configure(config.eventHasher())
                .withUnhandledTaskMetricEnabled(true)
                .withHyperlink(platformCoreHyperlink(EventHasher.class));

        if (hashCollectorEnabled) {
            builder.withOnRamp(hashingObjectCounter).withExternalBackPressure(true);
        } else {
            builder.withUnhandledTaskCapacity(platformContext
                            .getConfiguration()
                            .getConfigData(PlatformSchedulersConfig.class)
                            .eventHasherUnhandledCapacity())
                    .withFlushingEnabled(true);
        }

        return builder.build().cast();
    }

    /**
     * Build the post hash collector scheduler. Normally we don't build schedulers in this class, but a special
     * exception is made here because for back pressure reasons. Will be removed from this class when we implement a
     * platform health monitor.
     *
     * @param hashingObjectCounter the object counter to use for back pressure
     * @return the post hash collector scheduler
     */
    @NonNull
    private TaskScheduler<PlatformEvent> buildPostHashCollectorScheduler(
            @NonNull final ObjectCounter hashingObjectCounter) {
        return model.schedulerBuilder("PostHashCollector")
                .configure(config.postHashCollector())
                .withOffRamp(hashingObjectCounter)
                .withExternalBackPressure(true)
                .build()
                .cast();
    }

    /**
     * Get the wiring model.
     *
     * @return the wiring model
     */
    @NonNull
    public WiringModel getModel() {
        return model;
    }

    /**
     * Solder the EventWindow output to all components that need it.
     */
    private void solderEventWindow() {
        final OutputWire<EventWindow> eventWindowOutputWire = eventWindowManagerWiring.getOutputWire();

        eventWindowOutputWire.solderTo(eventDeduplicatorWiring.getInputWire(EventDeduplicator::setEventWindow), INJECT);
        eventWindowOutputWire.solderTo(
                eventSignatureValidatorWiring.getInputWire(EventSignatureValidator::setEventWindow), INJECT);
        eventWindowOutputWire.solderTo(orphanBufferWiring.getInputWire(OrphanBuffer::setEventWindow), INJECT);
        eventWindowOutputWire.solderTo(gossipWiring.getEventWindowInput(), INJECT);
        if (inlinePces) {
            eventWindowOutputWire.solderTo(
                    pcesInlineWriterWiring.getInputWire(InlinePcesWriter::updateNonAncientEventBoundary), INJECT);
        } else {
            eventWindowOutputWire.solderTo(
                    pcesWriterWiring.getInputWire(PcesWriter::updateNonAncientEventBoundary), INJECT);
        }
        eventWindowOutputWire.solderTo(
                eventCreationManagerWiring.getInputWire(EventCreationManager::setEventWindow), INJECT);
        eventWindowOutputWire.solderTo(
                latestCompleteStateNexusWiring.getInputWire(LatestCompleteStateNexus::updateEventWindow));
        eventWindowOutputWire.solderTo(
                transactionResubmitterWiring.getInputWire(TransactionResubmitter::updateEventWindow));
        eventWindowOutputWire.solderTo(branchDetectorWiring.getInputWire(BranchDetector::updateEventWindow), INJECT);
        eventWindowOutputWire.solderTo(branchReporterWiring.getInputWire(BranchReporter::updateEventWindow), INJECT);
    }

    /**
     * Solder notifications into the notifier.
     */
    private void solderNotifier() {
        latestCompleteStateNotifierWiring
                .getOutputWire()
                .solderTo(notifierWiring.getInputWire(AppNotifier::sendLatestCompleteStateNotification));
        stateSnapshotManagerWiring
                .getTransformedOutput(StateSnapshotManager::toNotification)
                .solderTo(notifierWiring.getInputWire(AppNotifier::sendStateWrittenToDiskNotification), INJECT);

        final OutputWire<IssNotification> issNotificationOutputWire = issDetectorWiring.getSplitOutput();
        issNotificationOutputWire.solderTo(notifierWiring.getInputWire(AppNotifier::sendIssNotification));
        statusStateMachineWiring
                .getOutputWire()
                .solderTo(notifierWiring.getInputWire(AppNotifier::sendPlatformStatusChangeNotification));
    }

    /**
     * Wire the components together.
     */
    private void wire() {
        final InputWire<PlatformEvent> pipelineInputWire;
        if (birthRoundMigrationShimWiring != null) {
            birthRoundMigrationShimWiring
                    .getOutputWire()
                    .solderTo(eventHasherWiring.getInputWire(EventHasher::hashEvent));
            pipelineInputWire = birthRoundMigrationShimWiring.getInputWire(BirthRoundMigrationShim::migrateEvent);
        } else {
            pipelineInputWire = eventHasherWiring.getInputWire(EventHasher::hashEvent);
        }

        gossipWiring.getEventOutput().solderTo(pipelineInputWire);
        if (hashCollectorEnabled) {
            eventHasherWiring.getOutputWire().solderTo(postHashCollectorWiring.getInputWire());
            postHashCollectorWiring
                    .getOutputWire()
                    .solderTo(internalEventValidatorWiring.getInputWire(InternalEventValidator::validateEvent));
        } else {
            eventHasherWiring
                    .getOutputWire()
                    .solderTo(internalEventValidatorWiring.getInputWire(InternalEventValidator::validateEvent));
        }

        internalEventValidatorWiring
                .getOutputWire()
                .solderTo(eventDeduplicatorWiring.getInputWire(EventDeduplicator::handleEvent));
        eventDeduplicatorWiring
                .getOutputWire()
                .solderTo(eventSignatureValidatorWiring.getInputWire(EventSignatureValidator::validateSignature));
        eventSignatureValidatorWiring
                .getOutputWire()
                .solderTo(orphanBufferWiring.getInputWire(OrphanBuffer::handleEvent));
        final OutputWire<PlatformEvent> splitOrphanBufferOutput = orphanBufferWiring.getSplitOutput();

        if (inlinePces) {
            splitOrphanBufferOutput.solderTo(pcesInlineWriterWiring.getInputWire(InlinePcesWriter::writeEvent));
            // make sure that an event is persisted before being sent to consensus, this avoids the situation where we
            // reach consensus with events that might be lost due to a crash
            pcesInlineWriterWiring
                    .getOutputWire()
                    .solderTo(consensusEngineWiring.getInputWire(ConsensusEngine::addEvent));
            // make sure events are persisted before being gossipped, this prevents accidental branching in the case
            // where an event is created, gossipped, and then the node crashes before the event is persisted.
            // after restart, a node will not be aware of this event, so it can create a branch
            pcesInlineWriterWiring.getOutputWire().solderTo(gossipWiring.getEventInput(), INJECT);
            // avoid using events as parents before they are persisted
            pcesInlineWriterWiring
                    .getOutputWire()
                    .solderTo(eventCreationManagerWiring.getInputWire(EventCreationManager::registerEvent));
        } else {
            splitOrphanBufferOutput.solderTo(
                    pcesSequencerWiring.getInputWire(PcesSequencer::assignStreamSequenceNumber));
            pcesSequencerWiring.getOutputWire().solderTo(pcesWriterWiring.getInputWire(PcesWriter::writeEvent));
            pcesSequencerWiring.getOutputWire().solderTo(consensusEngineWiring.getInputWire(ConsensusEngine::addEvent));
            // This must use injection to avoid cyclical back pressure
            splitOrphanBufferOutput.solderTo(gossipWiring.getEventInput(), INJECT);
            splitOrphanBufferOutput.solderTo(
                    eventCreationManagerWiring.getInputWire(EventCreationManager::registerEvent));
        }

        model.getHealthMonitorWire()
                .solderTo(eventCreationManagerWiring.getInputWire(EventCreationManager::reportUnhealthyDuration));

        model.getHealthMonitorWire().solderTo(gossipWiring.getSystemHealthInput());

        model.getHealthMonitorWire()
                .solderTo(transactionPoolWiring.getInputWire(TransactionPool::reportUnhealthyDuration));

        splitOrphanBufferOutput.solderTo(branchDetectorWiring.getInputWire(BranchDetector::checkForBranches));
        branchDetectorWiring.getOutputWire().solderTo(branchReporterWiring.getInputWire(BranchReporter::reportBranch));

        final double eventCreationHeartbeatFrequency = platformContext
                .getConfiguration()
                .getConfigData(EventCreationConfig.class)
                .creationAttemptRate();
        model.buildHeartbeatWire(eventCreationHeartbeatFrequency)
                .solderTo(eventCreationManagerWiring.getInputWire(EventCreationManager::maybeCreateEvent), OFFER);
        model.buildHeartbeatWire(platformContext
                        .getConfiguration()
                        .getConfigData(PlatformStatusConfig.class)
                        .statusStateMachineHeartbeatPeriod())
                .solderTo(statusStateMachineWiring.getInputWire(StatusStateMachine::heartbeat), OFFER);

        eventCreationManagerWiring
                .getOutputWire()
                .solderTo(selfEventSignerWiring.getInputWire(SelfEventSigner::signEvent));
        selfEventSignerWiring
                .getOutputWire()
                .solderTo(staleEventDetectorWiring.getInputWire(StaleEventDetector::addSelfEvent));

        final OutputWire<PlatformEvent> staleEventsFromStaleEventDetector =
                staleEventDetectorWiring.getSplitAndRoutedOutput(STALE_SELF_EVENT);
        final OutputWire<PlatformEvent> selfEventsFromStaleEventDetector =
                staleEventDetectorWiring.getSplitAndRoutedOutput(SELF_EVENT);

        selfEventsFromStaleEventDetector.solderTo(
                internalEventValidatorWiring.getInputWire(InternalEventValidator::validateEvent), INJECT);

        staleEventsFromStaleEventDetector.solderTo(
                transactionResubmitterWiring.getInputWire(TransactionResubmitter::resubmitStaleTransactions));
        final OutputWire<StateSignatureTransaction> splitTransactionResubmitterOutput =
                transactionResubmitterWiring.getSplitOutput();
        splitTransactionResubmitterOutput.solderTo(
                transactionPoolWiring.getInputWire(TransactionPool::submitSystemTransaction));

        if (publishStaleEvents) {
            staleEventsFromStaleEventDetector.solderTo(
                    platformPublisherWiring.getInputWire(PlatformPublisher::publishStaleEvent));
        }

        splitOrphanBufferOutput.solderTo(applicationTransactionPrehandlerWiring.getInputWire(
                TransactionPrehandler::prehandleApplicationTransactions));

        // From the orphan buffer, extract signatures from preconsensus events for input to the StateSignatureCollector.
        final WireTransformer<PlatformEvent, List<ScopedSystemTransaction<StateSignatureTransaction>>>
                preConsensusTransformer = new WireTransformer<>(
                        model,
                        "extractPreconsensusSignatureTransactions",
                        "preconsensus signatures",
                        event -> SystemTransactionExtractionUtils.extractFromEvent(
                                event, StateSignatureTransaction.class));
        splitOrphanBufferOutput.solderTo(preConsensusTransformer.getInputWire());
        preConsensusTransformer
                .getOutputWire()
                .solderTo(stateSignatureCollectorWiring.getInputWire(
                        StateSignatureCollector::handlePreconsensusSignatures));

        // Split output of StateSignatureCollector into single ReservedSignedStates.
        final OutputWire<ReservedSignedState> splitReservedSignedStateWire = stateSignatureCollectorWiring
                .getOutputWire()
                .buildSplitter("reservedStateSplitter", "reserved state lists");
        // Add another reservation to the signed states since we are soldering to two different input wires
        final OutputWire<ReservedSignedState> allReservedSignedStatesWire =
                splitReservedSignedStateWire.buildAdvancedTransformer(new SignedStateReserver("allStatesReserver"));

        // Future work: this should be a full component in its own right or folded in with the state file manager.
        final WireFilter<ReservedSignedState> saveToDiskFilter =
                new WireFilter<>(model, "saveToDiskFilter", "states", state -> {
                    if (state.get().isStateToSave()) {
                        return true;
                    }
                    state.close();
                    return false;
                });

        allReservedSignedStatesWire.solderTo(saveToDiskFilter.getInputWire());

        saveToDiskFilter
                .getOutputWire()
                .solderTo(stateSnapshotManagerWiring.getInputWire(StateSnapshotManager::saveStateTask));

        // Filter to complete states only and add a 3rd reservation since completes states are used in two input wires.
        final OutputWire<ReservedSignedState> completeReservedSignedStatesWire = allReservedSignedStatesWire
                .buildFilter("completeStateFilter", "states", rs -> {
                    if (rs.get().isComplete()) {
                        return true;
                    } else {
                        // close the second reservation on states that are not passed on.
                        rs.close();
                        return false;
                    }
                })
                .buildAdvancedTransformer(new SignedStateReserver("completeStatesReserver"));
        completeReservedSignedStatesWire.solderTo(
                latestCompleteStateNexusWiring.getInputWire(LatestCompleteStateNexus::setStateIfNewer));

        solderEventWindow();

        pcesReplayerWiring.eventOutput().solderTo(pipelineInputWire);

        final OutputWire<ConsensusRound> consensusRoundOutputWire = consensusEngineWiring.getSplitOutput();

        consensusRoundOutputWire.solderTo(staleEventDetectorWiring.getInputWire(StaleEventDetector::addConsensusRound));

        if (inlinePces) {
            pcesReplayerWiring
                    .doneStreamingPcesOutputWire()
                    .solderTo(pcesInlineWriterWiring.getInputWire(InlinePcesWriter::beginStreamingNewEvents));
            // with inline PCES, the round bypasses the round durability buffer and goes directly to the round handler
            consensusRoundOutputWire.solderTo(
                    transactionHandlerWiring.getInputWire(TransactionHandler::handleConsensusRound));
        } else {
            pcesReplayerWiring
                    .doneStreamingPcesOutputWire()
                    .solderTo(pcesWriterWiring.getInputWire(PcesWriter::beginStreamingNewEvents));
            // Create the transformer that extracts keystone event sequence number from consensus rounds.
            // This is done here instead of in ConsensusEngineWiring, since the transformer needs to be soldered with
            // specified ordering, relative to the wire carrying consensus rounds to the round handler
            final WireTransformer<ConsensusRound, Long> keystoneEventSequenceNumberTransformer = new WireTransformer<>(
                    model, "getKeystoneEventSequenceNumber", "rounds", round -> round.getKeystoneEvent()
                            .getStreamSequenceNumber());
            keystoneEventSequenceNumberTransformer
                    .getOutputWire()
                    .solderTo(pcesWriterWiring.getInputWire(PcesWriter::submitFlushRequest));
            // The request to flush the keystone event for a round must be sent to the PCES writer before the consensus
            // round is passed to the round handler. This prevents a deadlock scenario where the consensus round
            // handler has a full queue and won't accept additional rounds, and is waiting on a keystone event to be
            // durably flushed to disk. Meanwhile, the PCES writer hasn't even received the flush request yet, so the
            // necessary keystone event is *never* flushed.
            consensusRoundOutputWire.orderedSolderTo(List.of(
                    keystoneEventSequenceNumberTransformer.getInputWire(),
                    roundDurabilityBufferWiring.getInputWire(RoundDurabilityBuffer::addRound)));

            final OutputWire<ConsensusRound> splitRoundDurabilityBufferOutput =
                    roundDurabilityBufferWiring.getSplitOutput();
            splitRoundDurabilityBufferOutput.solderTo(
                    transactionHandlerWiring.getInputWire(TransactionHandler::handleConsensusRound));
        }

        consensusRoundOutputWire.solderTo(
                eventWindowManagerWiring.getInputWire(EventWindowManager::extractEventWindow));

        consensusEngineWiring
                .getSplitAndTransformedOutput(ConsensusEngine::getCesEvents)
                .solderTo(consensusEventStreamWiring.getInputWire(ConsensusEventStream::addEvents));

        final OutputWire<StateAndRound> transactionHandlerStateAndRoundOutput = transactionHandlerWiring
                .getOutputWire()
                .buildAdvancedTransformer(new StateAndRoundReserver("postHandler_stateAndRoundReserver"));

        final OutputWire<ReservedSignedState> transactionHandlerRoundOutput =
                transactionHandlerStateAndRoundOutput.buildTransformer(
                        "getState", "state and round", StateAndRound::reservedSignedState);

        transactionHandlerRoundOutput.solderTo(
                latestImmutableStateNexusWiring.getInputWire(SignedStateNexus::setState));
        transactionHandlerStateAndRoundOutput.solderTo(
                savedStateControllerWiring.getInputWire(SavedStateController::markSavedState));

        savedStateControllerWiring.getOutputWire().solderTo(stateHasherWiring.getInputWire(StateHasher::hashState));

        transactionHandlerStateAndRoundOutput.solderTo(
                stateGarbageCollectorWiring.getInputWire(StateGarbageCollector::registerState));
        model.buildHeartbeatWire(config.stateGarbageCollectorHeartbeatPeriod())
                .solderTo(stateGarbageCollectorWiring.getInputWire(StateGarbageCollector::heartbeat), OFFER);
        model.buildHeartbeatWire(config.signedStateSentinelHeartbeatPeriod())
                .solderTo(signedStateSentinelWiring.getInputWire(SignedStateSentinel::checkSignedStates), OFFER);

        // The state hasher needs to pass its data through a bunch of transformers. Construct those here.
        final OutputWire<StateAndRound> hashedStateAndRoundOutputWire = stateHasherWiring
                .getOutputWire()
                .buildAdvancedTransformer(new StateAndRoundReserver("postHasher_stateAndRoundReserver"));
        final OutputWire<ReservedSignedState> hashedStateOutputWire =
                hashedStateAndRoundOutputWire.buildAdvancedTransformer(
                        new StateAndRoundToStateReserver("postHasher_stateReserver"));
        final OutputWire<ConsensusRound> hashedConsensusRoundOutput = stateHasherWiring
                .getOutputWire()
                .buildTransformer("postHasher_getConsensusRound", "stateAndRound", StateAndRound::round);

        hashedStateOutputWire.solderTo(hashLoggerWiring.getInputWire(HashLogger::logHashes));
        hashedStateOutputWire.solderTo(stateSignerWiring.getInputWire(StateSigner::signState));
        hashedStateAndRoundOutputWire.solderTo(issDetectorWiring.getInputWire(IssDetector::handleStateAndRound));
        hashedStateAndRoundOutputWire
                .buildTransformer("postHasher_notifier", "state and round", StateHashedNotification::from)
                .solderTo(notifierWiring.getInputWire(AppNotifier::sendStateHashedNotification));

        stateSignerWiring
                .getOutputWire()
                .solderTo(transactionPoolWiring.getInputWire(TransactionPool::submitSystemTransaction));

        // FUTURE WORK: combine the signedStateHasherWiring State and Round outputs into a single StateAndRound output.
        // FUTURE WORK: Split the single StateAndRound output into separate State and Round wires.

        // Extract signatures from post-consensus events for input to the StateSignatureCollector.
        final WireTransformer<ConsensusRound, List<ScopedSystemTransaction<StateSignatureTransaction>>>
                postConsensusTransformer = new WireTransformer<>(
                        model,
                        "extractConsensusSignatureTransactions",
                        "consensus events",
                        round -> SystemTransactionExtractionUtils.extractFromRound(
                                round, StateSignatureTransaction.class));
        hashedConsensusRoundOutput.solderTo(postConsensusTransformer.getInputWire());
        postConsensusTransformer
                .getOutputWire()
                .solderTo(stateSignatureCollectorWiring.getInputWire(
                        StateSignatureCollector::handlePostconsensusSignatures));
        // Solder the state output as input to the state signature collector.
        hashedStateOutputWire.solderTo(
                stateSignatureCollectorWiring.getInputWire(StateSignatureCollector::addReservedState));

        if (inlinePces) {
            stateSnapshotManagerWiring
                    .getTransformedOutput(StateSnapshotManager::extractOldestMinimumGenerationOnDisk)
                    .solderTo(
                            pcesInlineWriterWiring.getInputWire(InlinePcesWriter::setMinimumAncientIdentifierToStore),
                            INJECT);
        } else {
            pcesWriterWiring
                    .getOutputWire()
                    .solderTo(
                            roundDurabilityBufferWiring.getInputWire(
                                    RoundDurabilityBuffer::setLatestDurableSequenceNumber),
                            INJECT);
            model.buildHeartbeatWire(platformContext
                            .getConfiguration()
                            .getConfigData(PcesConfig.class)
                            .roundDurabilityBufferHeartbeatPeriod())
                    .solderTo(
                            roundDurabilityBufferWiring.getInputWire(RoundDurabilityBuffer::checkForStaleRounds),
                            OFFER);
            stateSnapshotManagerWiring
                    .getTransformedOutput(StateSnapshotManager::extractOldestMinimumGenerationOnDisk)
                    .solderTo(pcesWriterWiring.getInputWire(PcesWriter::setMinimumAncientIdentifierToStore), INJECT);
        }

        stateSnapshotManagerWiring
                .getTransformedOutput(StateSnapshotManager::toStateWrittenToDiskAction)
                .solderTo(statusStateMachineWiring.getInputWire(StatusStateMachine::submitStatusAction));

        runningEventHashOverrideWiring
                .runningHashUpdateOutput()
                .solderTo(transactionHandlerWiring.getInputWire(TransactionHandler::updateLegacyRunningEventHash));
        runningEventHashOverrideWiring
                .runningHashUpdateOutput()
                .solderTo(consensusEventStreamWiring.getInputWire(ConsensusEventStream::legacyHashOverride));

        final OutputWire<IssNotification> splitIssDetectorOutput = issDetectorWiring.getSplitOutput();
        splitIssDetectorOutput.solderTo(issHandlerWiring.getInputWire(IssHandler::issObserved));
        issDetectorWiring
                .getSplitAndTransformedOutput(IssDetector::getStatusAction)
                .solderTo(statusStateMachineWiring.getInputWire(StatusStateMachine::submitStatusAction));

        completeReservedSignedStatesWire.solderTo(latestCompleteStateNotifierWiring.getInputWire(
                LatestCompleteStateNotifier::latestCompleteStateHandler));

        statusStateMachineWiring
                .getOutputWire()
                .solderTo(eventCreationManagerWiring.getInputWire(EventCreationManager::updatePlatformStatus));
        statusStateMachineWiring
                .getOutputWire()
                .solderTo(consensusEngineWiring.getInputWire(ConsensusEngine::updatePlatformStatus), INJECT);
        statusStateMachineWiring
                .getOutputWire()
                .solderTo(transactionPoolWiring.getInputWire(TransactionPool::updatePlatformStatus));
        statusStateMachineWiring.getOutputWire().solderTo(gossipWiring.getPlatformStatusInput(), INJECT);

        solderNotifier();

        if (publishPreconsensusEvents) {
            splitOrphanBufferOutput.solderTo(
                    platformPublisherWiring.getInputWire(PlatformPublisher::publishPreconsensusEvent));
        }

        buildUnsolderedWires();
    }

    /**
     * {@link ComponentWiring} objects build their input wires when you first request them. Normally that happens when
     * we are soldering things together, but there are a few wires that aren't soldered and aren't used until later in
     * the lifecycle. This method forces those wires to be built.
     */
    private void buildUnsolderedWires() {
        eventDeduplicatorWiring.getInputWire(EventDeduplicator::clear);
        consensusEngineWiring.getInputWire(ConsensusEngine::outOfBandSnapshotUpdate);
        if (publishSnapshotOverrides) {
            platformPublisherWiring.getInputWire(PlatformPublisher::publishSnapshotOverride);
        }
        eventCreationManagerWiring.getInputWire(EventCreationManager::clear);
        notifierWiring.getInputWire(AppNotifier::sendReconnectCompleteNotification);
        notifierWiring.getInputWire(AppNotifier::sendPlatformStatusChangeNotification);
        eventSignatureValidatorWiring.getInputWire(EventSignatureValidator::updateAddressBooks);
        eventWindowManagerWiring.getInputWire(EventWindowManager::updateEventWindow);
        orphanBufferWiring.getInputWire(OrphanBuffer::clear);
        if (inlinePces) {
            pcesInlineWriterWiring.getInputWire(InlinePcesWriter::registerDiscontinuity);
        } else {
            roundDurabilityBufferWiring.getInputWire(RoundDurabilityBuffer::clear);
            pcesWriterWiring.getInputWire(PcesWriter::registerDiscontinuity);
        }
        stateSignatureCollectorWiring.getInputWire(StateSignatureCollector::clear);
        issDetectorWiring.getInputWire(IssDetector::overridingState);
        issDetectorWiring.getInputWire(IssDetector::signalEndOfPreconsensusReplay);
        staleEventDetectorWiring.getInputWire(StaleEventDetector::setInitialEventWindow);
        staleEventDetectorWiring.getInputWire(StaleEventDetector::clear);
        transactionPoolWiring.getInputWire(TransactionPool::clear);
        stateSnapshotManagerWiring.getInputWire(StateSnapshotManager::dumpStateTask);
        branchDetectorWiring.getInputWire(BranchDetector::clear);
        branchReporterWiring.getInputWire(BranchReporter::clear);
    }

    /**
     * Bind components to the wiring.
     *
     * @param builder                   builds platform components that need to be bound to wires
     * @param pcesReplayer              the PCES replayer to bind
     * @param stateSignatureCollector   the signed state manager to bind
     * @param eventWindowManager        the event window manager to bind
     * @param birthRoundMigrationShim   the birth round migration shim to bind, ignored if birth round migration has not
     *                                  yet happened, must not be null if birth round migration has happened
     * @param latestImmutableStateNexus the latest immutable state nexus to bind
     * @param latestCompleteStateNexus  the latest complete state nexus to bind
     * @param savedStateController      the saved state controller to bind
     * @param notifier                  the notifier to bind
     * @param platformPublisher         the platform publisher to bind
     */
    public void bind(
            @NonNull final PlatformComponentBuilder builder,
            @NonNull final PcesReplayer pcesReplayer,
            @NonNull final StateSignatureCollector stateSignatureCollector,
            @NonNull final EventWindowManager eventWindowManager,
            @Nullable final BirthRoundMigrationShim birthRoundMigrationShim,
            @NonNull final SignedStateNexus latestImmutableStateNexus,
            @NonNull final LatestCompleteStateNexus latestCompleteStateNexus,
            @NonNull final SavedStateController savedStateController,
            @NonNull final AppNotifier notifier,
            @NonNull final PlatformPublisher platformPublisher) {

        eventHasherWiring.bind(builder::buildEventHasher);
        internalEventValidatorWiring.bind(builder::buildInternalEventValidator);
        eventDeduplicatorWiring.bind(builder::buildEventDeduplicator);
        eventSignatureValidatorWiring.bind(builder::buildEventSignatureValidator);
        orphanBufferWiring.bind(builder::buildOrphanBuffer);
        consensusEngineWiring.bind(builder::buildConsensusEngine);
        stateSnapshotManagerWiring.bind(builder::buildStateSnapshotManager);
        stateSignerWiring.bind(builder::buildStateSigner);
        pcesReplayerWiring.bind(pcesReplayer);
        if (inlinePces) {
            pcesInlineWriterWiring.bind(new NoOpInlinePcesWriter());
        } else {
            roundDurabilityBufferWiring.bind(builder::buildRoundDurabilityBuffer);
            pcesSequencerWiring.bind(builder::buildPcesSequencer);
            pcesWriterWiring.bind(builder::buildPcesWriter);
        }
        eventCreationManagerWiring.bind(builder::buildEventCreationManager);
        selfEventSignerWiring.bind(builder::buildSelfEventSigner);
        stateSignatureCollectorWiring.bind(stateSignatureCollector);
        eventWindowManagerWiring.bind(eventWindowManager);
        applicationTransactionPrehandlerWiring.bind(builder::buildTransactionPrehandler);
        transactionHandlerWiring.bind(builder::buildTransactionHandler);
        consensusEventStreamWiring.bind(builder::buildConsensusEventStream);
        issDetectorWiring.bind(builder::buildIssDetector);
        issHandlerWiring.bind(builder::buildIssHandler);
        hashLoggerWiring.bind(builder::buildHashLogger);
        if (birthRoundMigrationShimWiring != null) {
            birthRoundMigrationShimWiring.bind(Objects.requireNonNull(birthRoundMigrationShim));
        }
        latestCompleteStateNotifierWiring.bind(builder::buildLatestCompleteStateNotifier);
        latestImmutableStateNexusWiring.bind(latestImmutableStateNexus);
        latestCompleteStateNexusWiring.bind(latestCompleteStateNexus);
        savedStateControllerWiring.bind(savedStateController);
        stateHasherWiring.bind(builder::buildStateHasher);
        notifierWiring.bind(notifier);
        platformPublisherWiring.bind(platformPublisher);
        stateGarbageCollectorWiring.bind(builder::buildStateGarbageCollector);
        statusStateMachineWiring.bind(builder::buildStatusStateMachine);
        signedStateSentinelWiring.bind(builder::buildSignedStateSentinel);
        staleEventDetectorWiring.bind(builder::buildStaleEventDetector);
        transactionResubmitterWiring.bind(builder::buildTransactionResubmitter);
        transactionPoolWiring.bind(builder::buildTransactionPool);
        gossipWiring.bind(builder.buildGossip());
        branchDetectorWiring.bind(builder::buildBranchDetector);
        branchReporterWiring.bind(builder::buildBranchReporter);
    }

    /**
     * Start gossiping.
     */
    public void startGossip() {
        gossipWiring.getStartInput().inject(NoInput.getInstance());
    }

    /**
     * Get the input wire for the address book update.
     * <p>
     * Future work: this is a temporary hook to update the address book in the new intake pipeline.
     *
     * @return the input method for the address book update
     */
    @NonNull
    public InputWire<AddressBookUpdate> getAddressBookUpdateInput() {
        return eventSignatureValidatorWiring.getInputWire(EventSignatureValidator::updateAddressBooks);
    }

    /**
     * Get the input wire for dumping a state to disk
     * <p>
     * Future work: this is a temporary hook to allow the components to dump a state to disk, prior to the whole system
     * being migrated to the new framework.
     *
     * @return the input wire for dumping a state to disk
     */
    @NonNull
    public InputWire<StateDumpRequest> getDumpStateToDiskInput() {
        return stateSnapshotManagerWiring.getInputWire(StateSnapshotManager::dumpStateTask);
    }

    /**
     * @return the input wire for states that need their signatures collected
     */
    @NonNull
    public InputWire<ReservedSignedState> getSignatureCollectorStateInput() {
        return stateSignatureCollectorWiring.getInputWire(StateSignatureCollector::addReservedState);
    }

    /**
     * Get the input wire for passing a PCES iterator to the replayer.
     *
     * @return the input wire for passing a PCES iterator to the replayer
     */
    @NonNull
    public InputWire<IOIterator<PlatformEvent>> getPcesReplayerIteratorInput() {
        return pcesReplayerWiring.pcesIteratorInputWire();
    }

    /**
     * Get the output wire that the replayer uses to pass events from file into the intake pipeline.
     *
     * @return the output wire that the replayer uses to pass events from file into the intake pipeline
     */
    @NonNull
    public StandardOutputWire<PlatformEvent> getPcesReplayerEventOutput() {
        return pcesReplayerWiring.eventOutput();
    }

    /**
     * Get the input wire that the hashlogger uses to accept the signed state.
     *
     * @return the input wire that the hashlogger uses to accept the signed state
     */
    @NonNull
    public InputWire<ReservedSignedState> getHashLoggerInput() {
        return hashLoggerWiring.getInputWire(HashLogger::logHashes);
    }

    /**
     * Forward a state to the hash logger.
     *
     * @param signedState the state to forward
     */
    public void sendStateToHashLogger(@NonNull final SignedState signedState) {
        if (signedState.getState().getHash() != null) {
            final ReservedSignedState stateReservedForHasher = signedState.reserve("logging state hash");

            final boolean offerResult = getHashLoggerInput().offer(stateReservedForHasher);
            if (!offerResult) {
                stateReservedForHasher.close();
            }
        }
    }

    /**
     * Get the input wire for the PCES writer minimum generation to store
     *
     * @return the input wire for the PCES writer minimum generation to store
     */
    @NonNull
    public InputWire<Long> getPcesMinimumGenerationToStoreInput() {
        return inlinePces
                ? pcesInlineWriterWiring.getInputWire(InlinePcesWriter::setMinimumAncientIdentifierToStore)
                : pcesWriterWiring.getInputWire(PcesWriter::setMinimumAncientIdentifierToStore);
    }

    /**
     * Get the input wire for the PCES writer to register a discontinuity
     *
     * @return the input wire for the PCES writer to register a discontinuity
     */
    @NonNull
    public InputWire<Long> getPcesWriterRegisterDiscontinuityInput() {
        return inlinePces
                ? pcesInlineWriterWiring.getInputWire(InlinePcesWriter::registerDiscontinuity)
                : pcesWriterWiring.getInputWire(PcesWriter::registerDiscontinuity);
    }

    /**
     * Get the wiring for the app notifier
     *
     * @return the wiring for the app notifier
     */
    @NonNull
    public ComponentWiring<AppNotifier, Void> getNotifierWiring() {
        return notifierWiring;
    }

    /**
     * Get a supplier for the number of unprocessed tasks at the front of the intake pipeline. This is for the purpose
     * of applying backpressure to the event creator and gossip when the intake pipeline is overloaded.
     * <p>
     * Technically, the first component of the intake pipeline is the hasher, but tasks to be passed along actually
     * accumulate in the post hash collector. This is due to how the concurrent hasher handles backpressure.
     *
     * @return a supplier for the number of unprocessed tasks in the PostHashCollector
     */
    @NonNull
    public LongSupplier getIntakeQueueSizeSupplier() {
        if (hashCollectorEnabled) {
            return () -> postHashCollectorWiring.getScheduler().getUnprocessedTaskCount();
        } else {
            return () -> 0;
        }
    }

    /**
     * Update the running hash for all components that need it.
     *
     * @param runningHashUpdate the object containing necessary information to update the running hash
     */
    public void updateRunningHash(@NonNull final RunningEventHashOverride runningHashUpdate) {
        runningEventHashOverrideWiring.runningHashUpdateInput().inject(runningHashUpdate);
    }

    /**
     * Pass an overriding state to the ISS detector.
     *
     * @param state the overriding state
     */
    @NonNull
    public void overrideIssDetectorState(@NonNull final ReservedSignedState state) {
        issDetectorWiring.getInputWire(IssDetector::overridingState).put(state);
    }

    /**
     * Signal the end of the preconsensus replay to the ISS detector.
     */
    public void signalEndOfPcesReplay() {
        issDetectorWiring
                .getInputWire(IssDetector::signalEndOfPreconsensusReplay)
                .put(NoInput.getInstance());
    }

    /**
     * Get the status action submitter.
     *
     * @return the status action submitter
     */
    @NonNull
    public StatusActionSubmitter getStatusActionSubmitter() {
        return action -> statusStateMachineWiring
                .getInputWire(StatusStateMachine::submitStatusAction)
                .put(action);
    }

    /**
     * Inject a new event window into all components that need it.
     *
     * @param eventWindow the new event window
     */
    public void updateEventWindow(@NonNull final EventWindow eventWindow) {
        // Future work: this method can merge with consensusSnapshotOverride
        eventWindowManagerWiring
                .getInputWire(EventWindowManager::updateEventWindow)
                .inject(eventWindow);

        staleEventDetectorWiring
                .getInputWire(StaleEventDetector::setInitialEventWindow)
                .inject(eventWindow);

        // Since there is asynchronous access to the shadowgraph, it's important to ensure that
        // it has fully ingested the new event window before continuing.
        gossipWiring.flush();
    }

    /**
     * Inject a new consensus snapshot into all components that need it. This will happen at restart and reconnect
     * boundaries.
     *
     * @param consensusSnapshot the new consensus snapshot
     */
    public void consensusSnapshotOverride(@NonNull final ConsensusSnapshot consensusSnapshot) {
        consensusEngineWiring
                .getInputWire(ConsensusEngine::outOfBandSnapshotUpdate)
                .inject(consensusSnapshot);

        if (publishSnapshotOverrides) {
            platformPublisherWiring
                    .getInputWire(PlatformPublisher::publishSnapshotOverride)
                    .inject(consensusSnapshot);
        }
    }

    /**
     * Flush the intake pipeline.
     */
    public void flushIntakePipeline() {
        platformCoordinator.flushIntakePipeline();
    }

    /**
     * Flush the transaction handler.
     */
    public void flushTransactionHandler() {
        transactionHandlerWiring.flush();
    }

    /**
     * Flush the state hasher.
     */
    public void flushStateHasher() {
        stateHasherWiring.flush();
    }

    /**
     * Start the wiring framework.
     */
    public void start() {
        model.start();
    }

    /**
     * Stop the wiring framework.
     */
    public void stop() {
        model.stop();
    }

    /**
     * Clear all the wiring objects.
     */
    public void clear() {
        platformCoordinator.clear();
    }
}

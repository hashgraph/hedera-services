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

import static com.swirlds.common.wiring.wires.SolderType.INJECT;
import static com.swirlds.logging.legacy.LogMarker.STARTUP;

import com.swirlds.base.state.Startable;
import com.swirlds.base.state.Stoppable;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.stream.RunningEventHashUpdate;
import com.swirlds.common.utility.Clearable;
import com.swirlds.common.wiring.component.ComponentWiring;
import com.swirlds.common.wiring.counters.BackpressureObjectCounter;
import com.swirlds.common.wiring.counters.ObjectCounter;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.common.wiring.transformers.WireTransformer;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.common.wiring.wires.output.StandardOutputWire;
import com.swirlds.platform.StateSigner;
import com.swirlds.platform.components.ConsensusEngine;
import com.swirlds.platform.components.SavedStateController;
import com.swirlds.platform.components.appcomm.LatestCompleteStateNotifier;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.FutureEventBuffer;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.creation.EventCreationConfig;
import com.swirlds.platform.event.creation.EventCreationManager;
import com.swirlds.platform.event.deduplication.EventDeduplicator;
import com.swirlds.platform.event.hashing.EventHasher;
import com.swirlds.platform.event.linking.InOrderLinker;
import com.swirlds.platform.event.orphan.OrphanBuffer;
import com.swirlds.platform.event.preconsensus.EventDurabilityNexus;
import com.swirlds.platform.event.preconsensus.PcesReplayer;
import com.swirlds.platform.event.preconsensus.PcesSequencer;
import com.swirlds.platform.event.preconsensus.PcesWriter;
import com.swirlds.platform.event.stream.EventStreamManager;
import com.swirlds.platform.event.validation.AddressBookUpdate;
import com.swirlds.platform.event.validation.EventSignatureValidator;
import com.swirlds.platform.event.validation.InternalEventValidator;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.platform.eventhandling.TransactionPool;
import com.swirlds.platform.eventhandling.TransactionPrehandler;
import com.swirlds.platform.gossip.shadowgraph.Shadowgraph;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.publisher.PlatformPublisher;
import com.swirlds.platform.state.iss.IssDetector;
import com.swirlds.platform.state.iss.IssHandler;
import com.swirlds.platform.state.nexus.LatestCompleteStateNexus;
import com.swirlds.platform.state.nexus.SignedStateNexus;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedStateFileManager;
import com.swirlds.platform.state.signed.SignedStateHasher;
import com.swirlds.platform.state.signed.StateDumpRequest;
import com.swirlds.platform.state.signed.StateSavingResult;
import com.swirlds.platform.state.signed.StateSignatureCollector;
import com.swirlds.platform.system.events.BirthRoundMigrationShim;
import com.swirlds.platform.system.state.notifications.IssListener;
import com.swirlds.platform.system.state.notifications.IssNotification;
import com.swirlds.platform.system.status.PlatformStatusManager;
import com.swirlds.platform.system.status.actions.CatastrophicFailureAction;
import com.swirlds.platform.util.HashLogger;
import com.swirlds.platform.wiring.components.ConsensusRoundHandlerWiring;
import com.swirlds.platform.wiring.components.EventDurabilityNexusWiring;
import com.swirlds.platform.wiring.components.EventStreamManagerWiring;
import com.swirlds.platform.wiring.components.EventWindowManagerWiring;
import com.swirlds.platform.wiring.components.GossipWiring;
import com.swirlds.platform.wiring.components.HashLoggerWiring;
import com.swirlds.platform.wiring.components.IssDetectorWiring;
import com.swirlds.platform.wiring.components.IssHandlerWiring;
import com.swirlds.platform.wiring.components.LatestCompleteStateNotifierWiring;
import com.swirlds.platform.wiring.components.PassThroughWiring;
import com.swirlds.platform.wiring.components.PcesReplayerWiring;
import com.swirlds.platform.wiring.components.PcesWriterWiring;
import com.swirlds.platform.wiring.components.RunningHashUpdaterWiring;
import com.swirlds.platform.wiring.components.ShadowgraphWiring;
import com.swirlds.platform.wiring.components.StateHasherWiring;
import com.swirlds.platform.wiring.components.StateSignatureCollectorWiring;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.function.LongSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Encapsulates wiring for {@link com.swirlds.platform.SwirldsPlatform}.
 */
public class PlatformWiring implements Startable, Stoppable, Clearable {

    private static final Logger logger = LogManager.getLogger(PlatformWiring.class);

    private final WiringModel model;

    private final PlatformContext platformContext;

    private final ComponentWiring<EventHasher, GossipEvent> eventHasherWiring;
    private final PassThroughWiring<GossipEvent> postHashCollectorWiring;
    private final ComponentWiring<InternalEventValidator, GossipEvent> internalEventValidatorWiring;
    private final ComponentWiring<EventDeduplicator, GossipEvent> eventDeduplicatorWiring;
    private final EventSignatureValidatorWiring eventSignatureValidatorWiring;
    private final OrphanBufferWiring orphanBufferWiring;
    private final InOrderLinkerWiring inOrderLinkerWiring;
    private final ComponentWiring<ConsensusEngine, List<ConsensusRound>> consensusEngineWiring;
    private final ComponentWiring<EventCreationManager, GossipEvent> eventCreationManagerWiring;
    private final SignedStateFileManagerWiring signedStateFileManagerWiring;
    private final StateSignerWiring stateSignerWiring;
    private final PcesReplayerWiring pcesReplayerWiring;
    private final PcesWriterWiring pcesWriterWiring;
    private final ComponentWiring<PcesSequencer, GossipEvent> pcesSequencerWiring;
    private final EventDurabilityNexusWiring eventDurabilityNexusWiring;
    private final ComponentWiring<TransactionPrehandler, Void> applicationTransactionPrehandlerWiring;
    private final StateSignatureCollectorWiring stateSignatureCollectorWiring;
    private final ShadowgraphWiring shadowgraphWiring;
    private final ComponentWiring<FutureEventBuffer, List<GossipEvent>> futureEventBufferWiring;
    private final GossipWiring gossipWiring;
    private final EventWindowManagerWiring eventWindowManagerWiring;
    private final ConsensusRoundHandlerWiring consensusRoundHandlerWiring;
    private final EventStreamManagerWiring eventStreamManagerWiring;
    private final RunningHashUpdaterWiring runningHashUpdaterWiring;
    private final IssDetectorWiring issDetectorWiring;
    private final IssHandlerWiring issHandlerWiring;
    private final HashLoggerWiring hashLoggerWiring;
    private final LatestCompleteStateNotifierWiring latestCompleteStateNotifierWiring;
    private final ComponentWiring<SignedStateNexus, Void> latestImmutableStateNexusWiring;
    private final ComponentWiring<LatestCompleteStateNexus, Void> latestCompleteStateNexusWiring;
    private final ComponentWiring<SavedStateController, Void> savedStateControllerWiring;
    private final StateHasherWiring signedStateHasherWiring;
    private final PlatformCoordinator platformCoordinator;
    private final ComponentWiring<BirthRoundMigrationShim, GossipEvent> birthRoundMigrationShimWiring;

    private final ComponentWiring<PlatformPublisher, Void> platformPublisherWiring;
    private final boolean publishPreconsensusEvents;
    private final boolean publishSnapshotOverrides;

    /**
     * Constructor.
     *
     * @param platformContext           the platform context
     * @param publishPreconsensusEvents whether to publish preconsensus events (i.e. if a handler is registered). Extra
     *                                  things need to be wired together if we are publishing preconsensus events.
     * @param publishSnapshotOverrides  whether to publish snapshot overrides. Extra things need to be wired together if
     *                                  we are publishing snapshot overrides.
     */
    public PlatformWiring(
            @NonNull final PlatformContext platformContext,
            final boolean publishPreconsensusEvents,
            final boolean publishSnapshotOverrides) {

        this.platformContext = Objects.requireNonNull(platformContext);

        final PlatformSchedulersConfig schedulersConfig =
                platformContext.getConfiguration().getConfigData(PlatformSchedulersConfig.class);

        final int coreCount = Runtime.getRuntime().availableProcessors();
        final int parallelism = (int) Math.max(
                1, schedulersConfig.defaultPoolMultiplier() * coreCount + schedulersConfig.defaultPoolConstant());
        final ForkJoinPool defaultPool = new ForkJoinPool(parallelism);
        logger.info(STARTUP.getMarker(), "Default platform pool parallelism: {}", parallelism);

        model = WiringModel.create(platformContext, platformContext.getTime(), defaultPool);

        // This counter spans both the event hasher and the post hash collector. This is a workaround for the current
        // inability of concurrent schedulers to handle backpressure from an immediately subsequent scheduler.
        // This counter is the on-ramp for the event hasher, and the off-ramp for the post hash collector.
        final ObjectCounter hashingObjectCounter = new BackpressureObjectCounter(
                "hashingObjectCounter",
                platformContext
                        .getConfiguration()
                        .getConfigData(PlatformSchedulersConfig.class)
                        .eventHasherUnhandledCapacity(),
                Duration.ofNanos(100));

        final PlatformSchedulers schedulers = PlatformSchedulers.create(platformContext, model, hashingObjectCounter);

        final AncientMode ancientMode = platformContext
                .getConfiguration()
                .getConfigData(EventConfig.class)
                .getAncientMode();
        if (ancientMode == AncientMode.BIRTH_ROUND_THRESHOLD) {
            birthRoundMigrationShimWiring = new ComponentWiring<>(
                    model,
                    BirthRoundMigrationShim.class,
                    model.schedulerBuilder("birthRoundMigrationShim")
                            .withType(TaskSchedulerType.DIRECT_THREADSAFE)
                            .build()
                            .cast());
        } else {
            birthRoundMigrationShimWiring = null;
        }

        eventHasherWiring = new ComponentWiring<>(model, EventHasher.class, schedulers.eventHasherScheduler());
        postHashCollectorWiring =
                new PassThroughWiring<>(model, "GossipEvent", schedulers.postHashCollectorScheduler());
        internalEventValidatorWiring = new ComponentWiring<>(
                model, InternalEventValidator.class, schedulers.internalEventValidatorScheduler());
        eventDeduplicatorWiring =
                new ComponentWiring<>(model, EventDeduplicator.class, schedulers.eventDeduplicatorScheduler());
        eventSignatureValidatorWiring =
                EventSignatureValidatorWiring.create(schedulers.eventSignatureValidatorScheduler());
        orphanBufferWiring = OrphanBufferWiring.create(schedulers.orphanBufferScheduler());
        inOrderLinkerWiring = InOrderLinkerWiring.create(schedulers.inOrderLinkerScheduler());
        consensusEngineWiring =
                new ComponentWiring<>(model, ConsensusEngine.class, schedulers.consensusEngineScheduler());
        futureEventBufferWiring =
                new ComponentWiring<>(model, FutureEventBuffer.class, schedulers.futureEventBufferScheduler());
        eventCreationManagerWiring =
                new ComponentWiring<>(model, EventCreationManager.class, schedulers.eventCreationManagerScheduler());
        pcesSequencerWiring = new ComponentWiring<>(model, PcesSequencer.class, schedulers.pcesSequencerScheduler());

        applicationTransactionPrehandlerWiring = new ComponentWiring<>(
                model, TransactionPrehandler.class, schedulers.applicationTransactionPrehandlerScheduler());
        stateSignatureCollectorWiring =
                StateSignatureCollectorWiring.create(model, schedulers.stateSignatureCollectorScheduler());
        signedStateFileManagerWiring =
                SignedStateFileManagerWiring.create(model, schedulers.signedStateFileManagerScheduler());
        stateSignerWiring = StateSignerWiring.create(schedulers.stateSignerScheduler());
        shadowgraphWiring = ShadowgraphWiring.create(schedulers.shadowgraphScheduler());
        consensusRoundHandlerWiring = ConsensusRoundHandlerWiring.create(schedulers.consensusRoundHandlerScheduler());
        eventStreamManagerWiring = EventStreamManagerWiring.create(schedulers.eventStreamManagerScheduler());
        runningHashUpdaterWiring = RunningHashUpdaterWiring.create(schedulers.runningHashUpdateScheduler());

        signedStateHasherWiring = StateHasherWiring.create(schedulers.stateHasherScheduler());

        platformCoordinator = new PlatformCoordinator(
                hashingObjectCounter,
                internalEventValidatorWiring,
                eventDeduplicatorWiring,
                eventSignatureValidatorWiring,
                orphanBufferWiring,
                inOrderLinkerWiring,
                shadowgraphWiring,
                consensusEngineWiring,
                futureEventBufferWiring,
                eventCreationManagerWiring,
                applicationTransactionPrehandlerWiring,
                stateSignatureCollectorWiring,
                consensusRoundHandlerWiring,
                signedStateHasherWiring);

        pcesReplayerWiring = PcesReplayerWiring.create(schedulers.pcesReplayerScheduler());
        pcesWriterWiring = PcesWriterWiring.create(schedulers.pcesWriterScheduler());
        eventDurabilityNexusWiring = EventDurabilityNexusWiring.create(schedulers.eventDurabilityNexusScheduler());

        gossipWiring = GossipWiring.create(model);
        eventWindowManagerWiring = EventWindowManagerWiring.create(model);

        issDetectorWiring = IssDetectorWiring.create(schedulers.issDetectorScheduler());
        issHandlerWiring = IssHandlerWiring.create(schedulers.issHandlerScheduler());
        hashLoggerWiring = HashLoggerWiring.create(schedulers.hashLoggerScheduler());

        latestCompleteStateNotifierWiring =
                LatestCompleteStateNotifierWiring.create(schedulers.latestCompleteStateNotificationScheduler());

        latestImmutableStateNexusWiring = new ComponentWiring<>(
                model,
                SignedStateNexus.class,
                model.schedulerBuilder("latestImmutableStateNexus")
                        .withType(TaskSchedulerType.DIRECT_THREADSAFE)
                        .build()
                        .cast());
        latestCompleteStateNexusWiring = new ComponentWiring<>(
                model,
                LatestCompleteStateNexus.class,
                model.schedulerBuilder("latestCompleteStateNexus")
                        .withType(TaskSchedulerType.DIRECT_THREADSAFE)
                        .build()
                        .cast());
        savedStateControllerWiring = new ComponentWiring<>(
                model,
                SavedStateController.class,
                model.schedulerBuilder("savedStateController")
                        .withType(TaskSchedulerType.DIRECT_THREADSAFE)
                        .build()
                        .cast());

        this.publishPreconsensusEvents = publishPreconsensusEvents;
        this.publishSnapshotOverrides = publishSnapshotOverrides;
        if (publishPreconsensusEvents || publishSnapshotOverrides) {
            // Although with the usual pattern we don't define schedulers in this class, this is a special case.
            // We don't want to construct this scheduler in scenarios where we aren't using a publisher, and
            // we don't have the ability to conditionally construct the scheduler using the standard pattern.
            final TaskScheduler<Void> publisherScheduler = model.schedulerBuilder("platformPublisher")
                    .withType(TaskSchedulerType.SEQUENTIAL)
                    .build()
                    .cast();
            platformPublisherWiring = new ComponentWiring<>(model, PlatformPublisher.class, publisherScheduler);
        } else {
            platformPublisherWiring = null;
        }

        wire();
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
     * Solder the NonAncientEventWindow output to all components that need it.
     */
    private void solderNonAncientEventWindow() {
        final OutputWire<NonAncientEventWindow> nonAncientEventWindowOutputWire =
                eventWindowManagerWiring.nonAncientEventWindowOutput();

        nonAncientEventWindowOutputWire.solderTo(
                eventDeduplicatorWiring.getInputWire(EventDeduplicator::setNonAncientEventWindow), INJECT);
        nonAncientEventWindowOutputWire.solderTo(eventSignatureValidatorWiring.nonAncientEventWindowInput(), INJECT);
        nonAncientEventWindowOutputWire.solderTo(orphanBufferWiring.nonAncientEventWindowInput(), INJECT);
        nonAncientEventWindowOutputWire.solderTo(inOrderLinkerWiring.nonAncientEventWindowInput(), INJECT);
        nonAncientEventWindowOutputWire.solderTo(pcesWriterWiring.nonAncientEventWindowInput(), INJECT);
        nonAncientEventWindowOutputWire.solderTo(
                eventCreationManagerWiring.getInputWire(EventCreationManager::setNonAncientEventWindow), INJECT);
        nonAncientEventWindowOutputWire.solderTo(shadowgraphWiring.eventWindowInput(), INJECT);
        nonAncientEventWindowOutputWire.solderTo(
                futureEventBufferWiring.getInputWire(FutureEventBuffer::updateEventWindow), INJECT);
    }

    /**
     * Wire the components together.
     */
    private void wire() {
        final InputWire<GossipEvent> pipelineInputWire;
        if (birthRoundMigrationShimWiring != null) {
            birthRoundMigrationShimWiring
                    .getOutputWire()
                    .solderTo(eventHasherWiring.getInputWire(EventHasher::hashEvent));
            pipelineInputWire = birthRoundMigrationShimWiring.getInputWire(BirthRoundMigrationShim::migrateEvent);
        } else {
            pipelineInputWire = eventHasherWiring.getInputWire(EventHasher::hashEvent);
        }

        gossipWiring.eventOutput().solderTo(pipelineInputWire);
        eventHasherWiring.getOutputWire().solderTo(postHashCollectorWiring.getInputWire());
        postHashCollectorWiring
                .getOutputWire()
                .solderTo(internalEventValidatorWiring.getInputWire(InternalEventValidator::validateEvent));
        internalEventValidatorWiring
                .getOutputWire()
                .solderTo(eventDeduplicatorWiring.getInputWire(EventDeduplicator::handleEvent));
        eventDeduplicatorWiring.getOutputWire().solderTo(eventSignatureValidatorWiring.eventInput());
        eventSignatureValidatorWiring.eventOutput().solderTo(orphanBufferWiring.eventInput());
        orphanBufferWiring
                .eventOutput()
                .solderTo(pcesSequencerWiring.getInputWire(PcesSequencer::assignStreamSequenceNumber));
        pcesSequencerWiring.getOutputWire().solderTo(inOrderLinkerWiring.eventInput());
        pcesSequencerWiring.getOutputWire().solderTo(pcesWriterWiring.eventInputWire());
        inOrderLinkerWiring.eventOutput().solderTo(consensusEngineWiring.getInputWire(ConsensusEngine::addEvent));
        inOrderLinkerWiring.eventOutput().solderTo(shadowgraphWiring.eventInput());
        orphanBufferWiring.eventOutput().solderTo(futureEventBufferWiring.getInputWire(FutureEventBuffer::addEvent));

        final OutputWire<GossipEvent> futureEventBufferSplitOutput = futureEventBufferWiring.getSplitOutput();
        futureEventBufferSplitOutput.solderTo(
                eventCreationManagerWiring.getInputWire(EventCreationManager::registerEvent));

        final double eventCreationHeartbeatFrequency = platformContext
                .getConfiguration()
                .getConfigData(EventCreationConfig.class)
                .creationAttemptRate();
        model.buildHeartbeatWire(eventCreationHeartbeatFrequency)
                .solderTo(eventCreationManagerWiring.getInputWire(EventCreationManager::maybeCreateEvent));

        eventCreationManagerWiring
                .getOutputWire()
                .solderTo(internalEventValidatorWiring.getInputWire(InternalEventValidator::validateEvent), INJECT);
        orphanBufferWiring
                .eventOutput()
                .solderTo(applicationTransactionPrehandlerWiring.getInputWire(
                        TransactionPrehandler::prehandleApplicationTransactions));
        orphanBufferWiring.eventOutput().solderTo(stateSignatureCollectorWiring.preConsensusEventInput());
        stateSignatureCollectorWiring.getAllStatesOutput().solderTo(signedStateFileManagerWiring.saveToDiskFilter());
        stateSignatureCollectorWiring
                .getCompleteStatesOutput()
                .solderTo(latestCompleteStateNexusWiring.getInputWire(LatestCompleteStateNexus::setStateIfNewer));

        solderNonAncientEventWindow();

        pcesReplayerWiring.doneStreamingPcesOutputWire().solderTo(pcesWriterWiring.doneStreamingPcesInputWire());
        pcesReplayerWiring.eventOutput().solderTo(pipelineInputWire);

        // Create the transformer that extracts keystone event sequence number from consensus rounds.
        // This is done here instead of in ConsensusEngineWiring, since the transformer needs to be soldered with
        // specified ordering, relative to the wire carrying consensus rounds to the round handler
        final WireTransformer<ConsensusRound, Long> keystoneEventSequenceNumberTransformer = new WireTransformer<>(
                model, "getKeystoneEventSequenceNumber", "rounds", round -> round.getKeystoneEvent()
                        .getBaseEvent()
                        .getStreamSequenceNumber());
        keystoneEventSequenceNumberTransformer.getOutputWire().solderTo(pcesWriterWiring.flushRequestInputWire());

        final OutputWire<ConsensusRound> consensusRoundOutputWire = consensusEngineWiring.getSplitOutput();

        // The request to flush the keystone event for a round must be sent to the PCES writer before the consensus
        // round is passed to the round handler. This prevents a deadlock scenario where the consensus round
        // handler has a full queue and won't accept additional rounds, and is waiting on a keystone event to be
        // durably flushed to disk. Meanwhile, the PCES writer hasn't even received the flush request yet, so the
        // necessary keystone event is *never* flushed.
        consensusRoundOutputWire.orderedSolderTo(List.of(
                keystoneEventSequenceNumberTransformer.getInputWire(), consensusRoundHandlerWiring.roundInput()));
        consensusRoundOutputWire.solderTo(eventWindowManagerWiring.consensusRoundInput());

        consensusEngineWiring
                .getSplitAndTransformedOutput(ConsensusEngine::getConsensusEvents)
                .solderTo(eventStreamManagerWiring.eventsInput());

        consensusRoundHandlerWiring
                .stateOutput()
                .solderTo(latestImmutableStateNexusWiring.getInputWire(SignedStateNexus::setState));
        // FUTURE WORK: it is guaranteed that markSavedState will be called before the state arrives at the
        // signedStateFileManager, since SavedStateController::markSavedState is directly scheduled following a
        // transformer (wired during construction), whereas the data flowing to the signedStateFileManager is soldered
        // here in this method (via the signedStateHasher). This is guaranteed because data is distributed at runtime
        // in the order that it was originally soldered.
        //
        // Though robust, this guarantee is not immediately obvious, and thus is difficult to maintain. The solution is
        // to move the logic of SavedStateController::markSavedState into the signedStateFileManager. There is no reason
        // that saved state marking needs to happen in a separate place from where states are actually being saved.
        consensusRoundHandlerWiring
                .stateOutput()
                .solderTo(savedStateControllerWiring.getInputWire(SavedStateController::markSavedState));
        consensusRoundHandlerWiring
                .roundNumberOutput()
                .solderTo(latestCompleteStateNexusWiring.getInputWire(LatestCompleteStateNexus::newIncompleteState));
        consensusRoundHandlerWiring.stateAndRoundOutput().solderTo(signedStateHasherWiring.stateAndRoundInput());

        signedStateHasherWiring.stateOutput().solderTo(hashLoggerWiring.hashLoggerInputWire());
        signedStateHasherWiring.stateOutput().solderTo(stateSignerWiring.signState());
        signedStateHasherWiring.stateAndRoundOutput().solderTo(issDetectorWiring.stateAndRoundInput());

        // FUTURE WORK: combine these two methods into a single input method, which accepts a StateAndRound object
        signedStateHasherWiring.stateOutput().solderTo(stateSignatureCollectorWiring.getReservedStateInput());
        signedStateHasherWiring.roundOutput().solderTo(stateSignatureCollectorWiring.getConsensusRoundInput());

        pcesWriterWiring
                .latestDurableSequenceNumberOutput()
                .solderTo(eventDurabilityNexusWiring.latestDurableSequenceNumber());
        signedStateFileManagerWiring
                .oldestMinimumGenerationOnDiskOutputWire()
                .solderTo(pcesWriterWiring.minimumAncientIdentifierToStoreInputWire());

        runningHashUpdaterWiring
                .runningHashUpdateOutput()
                .solderTo(consensusRoundHandlerWiring.runningHashUpdateInput());
        runningHashUpdaterWiring.runningHashUpdateOutput().solderTo(eventStreamManagerWiring.runningHashUpdateInput());

        issDetectorWiring.issNotificationOutput().solderTo(issHandlerWiring.issNotificationInput());

        stateSignatureCollectorWiring
                .getCompleteStatesOutput()
                .solderTo(latestCompleteStateNotifierWiring.completeStateNotificationInputWire());

        if (publishPreconsensusEvents) {
            orphanBufferWiring
                    .eventOutput()
                    .solderTo(platformPublisherWiring.getInputWire(PlatformPublisher::publishPreconsensusEvent));
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
        futureEventBufferWiring.getInputWire(FutureEventBuffer::clear);
        consensusEngineWiring.getInputWire(ConsensusEngine::outOfBandSnapshotUpdate);
        if (publishSnapshotOverrides) {
            platformPublisherWiring.getInputWire(PlatformPublisher::publishSnapshotOverride);
        }
        eventCreationManagerWiring.getInputWire(EventCreationManager::clear);
    }

    /**
     * Wire components that adhere to the framework to components that don't
     * <p>
     * Future work: as more components are moved to the framework, this method should shrink, and eventually be
     * removed.
     *
     * @param statusManager      the status manager to wire
     * @param transactionPool    the transaction pool to wire
     * @param notificationEngine the notification engine to wire
     */
    public void wireExternalComponents(
            @NonNull final PlatformStatusManager statusManager,
            @NonNull final TransactionPool transactionPool,
            @NonNull final NotificationEngine notificationEngine) {

        signedStateFileManagerWiring
                .stateWrittenToDiskOutputWire()
                .solderTo(
                        "statusManager_submitStateWritten",
                        "state written to disk notification",
                        statusManager::submitStatusAction);

        stateSignerWiring
                .stateSignature()
                .solderTo("transactionPool", "signature transactions", transactionPool::submitSystemTransaction);

        issDetectorWiring
                .issNotificationOutput()
                .solderTo(
                        "issNotificationEngine",
                        "ISS notification",
                        n -> notificationEngine.dispatch(IssListener.class, n));
        issDetectorWiring
                .issNotificationOutput()
                .solderTo("statusManager_submitCatastrophicFailure", "ISS notification", n -> {
                    if (Set.of(IssNotification.IssType.SELF_ISS, IssNotification.IssType.CATASTROPHIC_ISS)
                            .contains(n.getIssType())) {
                        statusManager.submitStatusAction(new CatastrophicFailureAction());
                    }
                });
    }

    /**
     * Bind components to the wiring.
     *
     * @param eventHasher               the event hasher to bind
     * @param internalEventValidator    the internal event validator to bind
     * @param eventDeduplicator         the event deduplicator to bind
     * @param eventSignatureValidator   the event signature validator to bind
     * @param orphanBuffer              the orphan buffer to bind
     * @param inOrderLinker             the in order linker to bind
     * @param consensusEngine           the consensus engine to bind
     * @param signedStateFileManager    the signed state file manager to bind
     * @param stateSigner               the state signer to bind
     * @param pcesReplayer              the PCES replayer to bind
     * @param pcesWriter                the PCES writer to bind
     * @param eventDurabilityNexus      the event durability nexus to bind
     * @param shadowgraph               the shadowgraph to bind
     * @param pcesSequencer             the PCES sequencer to bind
     * @param eventCreationManager      the event creation manager to bind
     * @param stateSignatureCollector   the signed state manager to bind
     * @param transactionPrehandler     the transaction prehandler to bind
     * @param consensusRoundHandler     the consensus round handler to bind
     * @param eventStreamManager        the event stream manager to bind
     * @param futureEventBuffer         the future event buffer to bind
     * @param issDetector               the ISS detector to bind
     * @param issHandler                the ISS handler to bind
     * @param hashLogger                the hash logger to bind
     * @param birthRoundMigrationShim   the birth round migration shim to bind, ignored if birth round migration has not
     *                                  yet happened, must not be null if birth round migration has happened
     * @param completeStateNotifier     the latest complete state notifier to bind
     * @param latestImmutableStateNexus the latest immutable state nexus to bind
     * @param latestCompleteStateNexus  the latest complete state nexus to bind
     * @param savedStateController      the saved state controller to bind
     * @param signedStateHasher         the signed state hasher to bind
     * @param platformPublisher         the platform publisher to bind
     */
    public void bind(
            @NonNull final EventHasher eventHasher,
            @NonNull final InternalEventValidator internalEventValidator,
            @NonNull final EventDeduplicator eventDeduplicator,
            @NonNull final EventSignatureValidator eventSignatureValidator,
            @NonNull final OrphanBuffer orphanBuffer,
            @NonNull final InOrderLinker inOrderLinker,
            @NonNull final ConsensusEngine consensusEngine,
            @NonNull final SignedStateFileManager signedStateFileManager,
            @NonNull final StateSigner stateSigner,
            @NonNull final PcesReplayer pcesReplayer,
            @NonNull final PcesWriter pcesWriter,
            @NonNull final EventDurabilityNexus eventDurabilityNexus,
            @NonNull final Shadowgraph shadowgraph,
            @NonNull final PcesSequencer pcesSequencer,
            @NonNull final EventCreationManager eventCreationManager,
            @NonNull final StateSignatureCollector stateSignatureCollector,
            @NonNull final TransactionPrehandler transactionPrehandler,
            @NonNull final ConsensusRoundHandler consensusRoundHandler,
            @NonNull final EventStreamManager eventStreamManager,
            @NonNull final FutureEventBuffer futureEventBuffer,
            @NonNull final IssDetector issDetector,
            @NonNull final IssHandler issHandler,
            @NonNull final HashLogger hashLogger,
            @Nullable final BirthRoundMigrationShim birthRoundMigrationShim,
            @NonNull final LatestCompleteStateNotifier completeStateNotifier,
            @NonNull final SignedStateNexus latestImmutableStateNexus,
            @NonNull final LatestCompleteStateNexus latestCompleteStateNexus,
            @NonNull final SavedStateController savedStateController,
            @NonNull final SignedStateHasher signedStateHasher,
            @Nullable final PlatformPublisher platformPublisher) {

        eventHasherWiring.bind(eventHasher);
        internalEventValidatorWiring.bind(internalEventValidator);
        eventDeduplicatorWiring.bind(eventDeduplicator);
        eventSignatureValidatorWiring.bind(eventSignatureValidator);
        orphanBufferWiring.bind(orphanBuffer);
        inOrderLinkerWiring.bind(inOrderLinker);
        consensusEngineWiring.bind(consensusEngine);
        signedStateFileManagerWiring.bind(signedStateFileManager);
        stateSignerWiring.bind(stateSigner);
        pcesReplayerWiring.bind(pcesReplayer);
        pcesWriterWiring.bind(pcesWriter);
        eventDurabilityNexusWiring.bind(eventDurabilityNexus);
        shadowgraphWiring.bind(shadowgraph);
        pcesSequencerWiring.bind(pcesSequencer);
        eventCreationManagerWiring.bind(eventCreationManager);
        stateSignatureCollectorWiring.bind(stateSignatureCollector);
        applicationTransactionPrehandlerWiring.bind(transactionPrehandler);
        consensusRoundHandlerWiring.bind(consensusRoundHandler);
        eventStreamManagerWiring.bind(eventStreamManager);
        futureEventBufferWiring.bind(futureEventBuffer);
        issDetectorWiring.bind(issDetector);
        issHandlerWiring.bind(issHandler);
        hashLoggerWiring.bind(hashLogger);
        if (birthRoundMigrationShimWiring != null) {
            birthRoundMigrationShimWiring.bind(Objects.requireNonNull(birthRoundMigrationShim));
        }
        latestCompleteStateNotifierWiring.bind(completeStateNotifier);
        latestImmutableStateNexusWiring.bind(latestImmutableStateNexus);
        latestCompleteStateNexusWiring.bind(latestCompleteStateNexus);
        savedStateControllerWiring.bind(savedStateController);
        signedStateHasherWiring.bind(signedStateHasher);

        if (platformPublisherWiring != null) {
            platformPublisherWiring.bind(platformPublisher);
        }
    }

    /**
     * Get the input wire gossip. All events received from peers during should be passed to this wire.
     *
     * @return the wire where all events from gossip should be passed
     */
    @NonNull
    public InputWire<GossipEvent> getGossipEventInput() {
        return gossipWiring.eventInput();
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
        return eventSignatureValidatorWiring.addressBookUpdateInput();
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
        return signedStateFileManagerWiring.dumpStateToDisk();
    }

    /**
     * @return the output wire for the state saving result
     */
    @NonNull
    public OutputWire<StateSavingResult> getStateSavingResultOutput() {
        return signedStateFileManagerWiring.stateSavingResultOutputWire();
    }

    /**
     * @return the input wire for states that need their signatures collected
     */
    @NonNull
    public InputWire<ReservedSignedState> getSignatureCollectorStateInput() {
        return stateSignatureCollectorWiring.getReservedStateInput();
    }

    /**
     * Get the input wire for passing a PCES iterator to the replayer.
     *
     * @return the input wire for passing a PCES iterator to the replayer
     */
    @NonNull
    public InputWire<IOIterator<GossipEvent>> getPcesReplayerIteratorInput() {
        return pcesReplayerWiring.pcesIteratorInputWire();
    }

    /**
     * Get the output wire that the replayer uses to pass events from file into the intake pipeline.
     *
     * @return the output wire that the replayer uses to pass events from file into the intake pipeline
     */
    @NonNull
    public StandardOutputWire<GossipEvent> getPcesReplayerEventOutput() {
        return pcesReplayerWiring.eventOutput();
    }

    /**
     * Get the input wire that the hashlogger uses to accept the signed state.
     *
     * @return the input wire that the hashlogger uses to accept the signed state
     */
    @NonNull
    public InputWire<ReservedSignedState> getHashLoggerInput() {
        return hashLoggerWiring.hashLoggerInputWire();
    }

    /**
     * Get the input wire for the PCES writer minimum generation to store
     *
     * @return the input wire for the PCES writer minimum generation to store
     */
    @NonNull
    public InputWire<Long> getPcesMinimumGenerationToStoreInput() {
        return pcesWriterWiring.minimumAncientIdentifierToStoreInputWire();
    }

    /**
     * Get the input wire for the PCES writer to register a discontinuity
     *
     * @return the input wire for the PCES writer to register a discontinuity
     */
    @NonNull
    public InputWire<Long> getPcesWriterRegisterDiscontinuityInput() {
        return pcesWriterWiring.discontinuityInputWire();
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
        return () -> postHashCollectorWiring.getScheduler().getUnprocessedTaskCount();
    }

    /**
     * Update the running hash for all components that need it.
     *
     * @param runningHashUpdate the object containing necessary information to update the running hash
     */
    public void updateRunningHash(@NonNull final RunningEventHashUpdate runningHashUpdate) {
        runningHashUpdaterWiring.runningHashUpdateInput().inject(runningHashUpdate);
    }

    /**
     * @return the wiring wrapper for the ISS detector
     */
    public @NonNull IssDetectorWiring getIssDetectorWiring() {
        return issDetectorWiring;
    }

    /**
     * Inject a new non-ancient event window into all components that need it.
     *
     * @param nonAncientEventWindow the new non-ancient event window
     */
    public void updateNonAncientEventWindow(@NonNull final NonAncientEventWindow nonAncientEventWindow) {
        // Future work: this method can merge with consensusSnapshotOverride

        eventWindowManagerWiring.manualWindowInput().inject(nonAncientEventWindow);

        // Since there is asynchronous access to the shadowgraph, it's important to ensure that
        // it has fully ingested the new event window before continuing.
        shadowgraphWiring.flushRunnable().run();
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
     * Flush the consensus round handler.
     */
    public void flushConsensusRoundHandler() {
        consensusRoundHandlerWiring.flushRunnable().run();
    }

    /**
     * Flush the state hasher.
     */
    public void flushStateHasher() {
        signedStateHasherWiring.flushRunnable().run();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        model.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        model.stop();
    }

    /**
     * Clear all the wiring objects.
     */
    @Override
    public void clear() {
        platformCoordinator.clear();
    }
}

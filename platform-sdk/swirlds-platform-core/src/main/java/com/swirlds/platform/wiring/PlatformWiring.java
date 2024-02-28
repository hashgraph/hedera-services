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
import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.stream.EventStreamManager;
import com.swirlds.common.stream.RunningEventHashUpdate;
import com.swirlds.common.utility.Clearable;
import com.swirlds.common.wiring.counters.BackpressureObjectCounter;
import com.swirlds.common.wiring.counters.ObjectCounter;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.transformers.WireTransformer;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.common.wiring.wires.output.StandardOutputWire;
import com.swirlds.platform.StateSigner;
import com.swirlds.platform.components.ConsensusEngine;
import com.swirlds.platform.components.appcomm.LatestCompleteStateNotifier;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.event.FutureEventBuffer;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.creation.EventCreationManager;
import com.swirlds.platform.event.deduplication.EventDeduplicator;
import com.swirlds.platform.event.hashing.EventHasher;
import com.swirlds.platform.event.linking.InOrderLinker;
import com.swirlds.platform.event.orphan.OrphanBuffer;
import com.swirlds.platform.event.preconsensus.EventDurabilityNexus;
import com.swirlds.platform.event.preconsensus.PcesReplayer;
import com.swirlds.platform.event.preconsensus.PcesSequencer;
import com.swirlds.platform.event.preconsensus.PcesWriter;
import com.swirlds.platform.event.validation.AddressBookUpdate;
import com.swirlds.platform.event.validation.EventSignatureValidator;
import com.swirlds.platform.event.validation.InternalEventValidator;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.eventhandling.TransactionPool;
import com.swirlds.platform.gossip.shadowgraph.Shadowgraph;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.iss.IssDetector;
import com.swirlds.platform.state.iss.IssHandler;
import com.swirlds.platform.state.nexus.LatestCompleteStateNexus;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedStateFileManager;
import com.swirlds.platform.state.signed.StateDumpRequest;
import com.swirlds.platform.state.signed.StateSavingResult;
import com.swirlds.platform.state.signed.StateSignatureCollector;
import com.swirlds.platform.system.state.notifications.IssListener;
import com.swirlds.platform.system.state.notifications.IssNotification;
import com.swirlds.platform.system.status.PlatformStatusManager;
import com.swirlds.platform.system.status.actions.CatastrophicFailureAction;
import com.swirlds.platform.util.HashLogger;
import com.swirlds.platform.wiring.components.ApplicationTransactionPrehandlerWiring;
import com.swirlds.platform.wiring.components.ConsensusRoundHandlerWiring;
import com.swirlds.platform.wiring.components.EventCreationManagerWiring;
import com.swirlds.platform.wiring.components.EventDurabilityNexusWiring;
import com.swirlds.platform.wiring.components.EventHasherWiring;
import com.swirlds.platform.wiring.components.EventStreamManagerWiring;
import com.swirlds.platform.wiring.components.EventWindowManagerWiring;
import com.swirlds.platform.wiring.components.FutureEventBufferWiring;
import com.swirlds.platform.wiring.components.GossipWiring;
import com.swirlds.platform.wiring.components.HashLoggerWiring;
import com.swirlds.platform.wiring.components.IssDetectorWiring;
import com.swirlds.platform.wiring.components.IssHandlerWiring;
import com.swirlds.platform.wiring.components.LatestCompleteStateNotifierWiring;
import com.swirlds.platform.wiring.components.PcesReplayerWiring;
import com.swirlds.platform.wiring.components.PcesSequencerWiring;
import com.swirlds.platform.wiring.components.PcesWriterWiring;
import com.swirlds.platform.wiring.components.PostHashCollectorWiring;
import com.swirlds.platform.wiring.components.RunningHashUpdaterWiring;
import com.swirlds.platform.wiring.components.ShadowgraphWiring;
import com.swirlds.platform.wiring.components.StateSignatureCollectorWiring;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.List;
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

    private final EventHasherWiring eventHasherWiring;
    private final PostHashCollectorWiring postHashCollectorWiring;
    private final InternalEventValidatorWiring internalEventValidatorWiring;
    private final EventDeduplicatorWiring eventDeduplicatorWiring;
    private final EventSignatureValidatorWiring eventSignatureValidatorWiring;
    private final OrphanBufferWiring orphanBufferWiring;
    private final InOrderLinkerWiring inOrderLinkerWiring;
    private final ConsensusEngineWiring consensusEngineWiring;
    private final EventCreationManagerWiring eventCreationManagerWiring;
    private final SignedStateFileManagerWiring signedStateFileManagerWiring;
    private final StateSignerWiring stateSignerWiring;
    private final PcesReplayerWiring pcesReplayerWiring;
    private final PcesWriterWiring pcesWriterWiring;
    private final PcesSequencerWiring pcesSequencerWiring;
    private final EventDurabilityNexusWiring eventDurabilityNexusWiring;
    private final ApplicationTransactionPrehandlerWiring applicationTransactionPrehandlerWiring;
    private final StateSignatureCollectorWiring stateSignatureCollectorWiring;
    private final ShadowgraphWiring shadowgraphWiring;
    private final FutureEventBufferWiring futureEventBufferWiring;
    private final GossipWiring gossipWiring;
    private final EventWindowManagerWiring eventWindowManagerWiring;
    private final ConsensusRoundHandlerWiring consensusRoundHandlerWiring;
    private final EventStreamManagerWiring eventStreamManagerWiring;
    private final RunningHashUpdaterWiring runningHashUpdaterWiring;
    private final IssDetectorWiring issDetectorWiring;
    private final IssHandlerWiring issHandlerWiring;
    private final HashLoggerWiring hashLoggerWiring;
    private final LatestCompleteStateNotifierWiring latestCompleteStateNotifierWiring;

    private final PlatformCoordinator platformCoordinator;

    /**
     * Constructor.
     *
     * @param platformContext the platform context
     * @param time            provides wall clock time
     */
    public PlatformWiring(@NonNull final PlatformContext platformContext, @NonNull final Time time) {

        final PlatformSchedulersConfig schedulersConfig =
                platformContext.getConfiguration().getConfigData(PlatformSchedulersConfig.class);

        final int coreCount = Runtime.getRuntime().availableProcessors();
        final int parallelism = (int) Math.max(
                1, schedulersConfig.defaultPoolMultiplier() * coreCount + schedulersConfig.defaultPoolConstant());
        final ForkJoinPool defaultPool = new ForkJoinPool(parallelism);
        logger.info(STARTUP.getMarker(), "Default platform pool parallelism: {}", parallelism);

        model = WiringModel.create(platformContext, time, defaultPool);

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

        eventHasherWiring = EventHasherWiring.create(schedulers.eventHasherScheduler());
        postHashCollectorWiring = PostHashCollectorWiring.create(schedulers.postHashCollectorScheduler());
        internalEventValidatorWiring =
                InternalEventValidatorWiring.create(schedulers.internalEventValidatorScheduler());
        eventDeduplicatorWiring = EventDeduplicatorWiring.create(schedulers.eventDeduplicatorScheduler());
        eventSignatureValidatorWiring =
                EventSignatureValidatorWiring.create(schedulers.eventSignatureValidatorScheduler());
        orphanBufferWiring = OrphanBufferWiring.create(schedulers.orphanBufferScheduler());
        inOrderLinkerWiring = InOrderLinkerWiring.create(schedulers.inOrderLinkerScheduler());
        consensusEngineWiring = ConsensusEngineWiring.create(schedulers.consensusEngineScheduler());
        eventCreationManagerWiring =
                EventCreationManagerWiring.create(platformContext, schedulers.eventCreationManagerScheduler());
        pcesSequencerWiring = PcesSequencerWiring.create(schedulers.pcesSequencerScheduler());

        applicationTransactionPrehandlerWiring =
                ApplicationTransactionPrehandlerWiring.create(schedulers.applicationTransactionPrehandlerScheduler());
        stateSignatureCollectorWiring =
                StateSignatureCollectorWiring.create(model, schedulers.stateSignatureCollectorScheduler());
        signedStateFileManagerWiring =
                SignedStateFileManagerWiring.create(model, schedulers.signedStateFileManagerScheduler());
        stateSignerWiring = StateSignerWiring.create(schedulers.stateSignerScheduler());
        shadowgraphWiring = ShadowgraphWiring.create(schedulers.shadowgraphScheduler());
        consensusRoundHandlerWiring = ConsensusRoundHandlerWiring.create(schedulers.consensusRoundHandlerScheduler());
        eventStreamManagerWiring = EventStreamManagerWiring.create(schedulers.eventStreamManagerScheduler());
        runningHashUpdaterWiring = RunningHashUpdaterWiring.create(schedulers.runningHashUpdateScheduler());

        platformCoordinator = new PlatformCoordinator(
                hashingObjectCounter,
                internalEventValidatorWiring,
                eventDeduplicatorWiring,
                eventSignatureValidatorWiring,
                orphanBufferWiring,
                inOrderLinkerWiring,
                shadowgraphWiring,
                consensusEngineWiring,
                eventCreationManagerWiring,
                applicationTransactionPrehandlerWiring,
                stateSignatureCollectorWiring,
                consensusRoundHandlerWiring);

        pcesReplayerWiring = PcesReplayerWiring.create(schedulers.pcesReplayerScheduler());
        pcesWriterWiring = PcesWriterWiring.create(schedulers.pcesWriterScheduler());
        eventDurabilityNexusWiring = EventDurabilityNexusWiring.create(schedulers.eventDurabilityNexusScheduler());

        futureEventBufferWiring = FutureEventBufferWiring.create(schedulers.futureEventBufferScheduler());
        gossipWiring = GossipWiring.create(model);
        eventWindowManagerWiring = EventWindowManagerWiring.create(model);

        issDetectorWiring = IssDetectorWiring.create(model, schedulers.issDetectorScheduler());
        issHandlerWiring = IssHandlerWiring.create(schedulers.issHandlerScheduler());
        hashLoggerWiring = HashLoggerWiring.create(schedulers.hashLoggerScheduler());

        latestCompleteStateNotifierWiring =
                LatestCompleteStateNotifierWiring.create(schedulers.latestCompleteStateNotificationScheduler());

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

        nonAncientEventWindowOutputWire.solderTo(eventDeduplicatorWiring.nonAncientEventWindowInput(), INJECT);
        nonAncientEventWindowOutputWire.solderTo(eventSignatureValidatorWiring.nonAncientEventWindowInput(), INJECT);
        nonAncientEventWindowOutputWire.solderTo(orphanBufferWiring.nonAncientEventWindowInput(), INJECT);
        nonAncientEventWindowOutputWire.solderTo(inOrderLinkerWiring.nonAncientEventWindowInput(), INJECT);
        nonAncientEventWindowOutputWire.solderTo(pcesWriterWiring.nonAncientEventWindowInput(), INJECT);
        nonAncientEventWindowOutputWire.solderTo(eventCreationManagerWiring.nonAncientEventWindowInput(), INJECT);
        nonAncientEventWindowOutputWire.solderTo(shadowgraphWiring.eventWindowInput(), INJECT);
        nonAncientEventWindowOutputWire.solderTo(futureEventBufferWiring.eventWindowInput(), INJECT);
    }

    /**
     * Wire the components together.
     */
    private void wire() {
        gossipWiring.eventOutput().solderTo(eventHasherWiring.eventInput());
        eventHasherWiring.eventOutput().solderTo(postHashCollectorWiring.eventInput());
        postHashCollectorWiring.eventOutput().solderTo(internalEventValidatorWiring.eventInput());
        internalEventValidatorWiring.eventOutput().solderTo(eventDeduplicatorWiring.eventInput());
        eventDeduplicatorWiring.eventOutput().solderTo(eventSignatureValidatorWiring.eventInput());
        eventSignatureValidatorWiring.eventOutput().solderTo(orphanBufferWiring.eventInput());
        orphanBufferWiring.eventOutput().solderTo(pcesSequencerWiring.eventInput());
        pcesSequencerWiring.eventOutput().solderTo(inOrderLinkerWiring.eventInput());
        pcesSequencerWiring.eventOutput().solderTo(pcesWriterWiring.eventInputWire());
        inOrderLinkerWiring.eventOutput().solderTo(consensusEngineWiring.eventInput());
        inOrderLinkerWiring.eventOutput().solderTo(shadowgraphWiring.eventInput());
        orphanBufferWiring.eventOutput().solderTo(futureEventBufferWiring.eventInput());
        futureEventBufferWiring.eventOutput().solderTo(eventCreationManagerWiring.eventInput());
        eventCreationManagerWiring.newEventOutput().solderTo(internalEventValidatorWiring.eventInput(), INJECT);
        orphanBufferWiring
                .eventOutput()
                .solderTo(applicationTransactionPrehandlerWiring.appTransactionsToPrehandleInput());
        orphanBufferWiring.eventOutput().solderTo(stateSignatureCollectorWiring.preConsensusEventInput());
        stateSignatureCollectorWiring.getAllStatesOutput().solderTo(signedStateFileManagerWiring.saveToDiskFilter());

        solderNonAncientEventWindow();

        pcesReplayerWiring.doneStreamingPcesOutputWire().solderTo(pcesWriterWiring.doneStreamingPcesInputWire());
        pcesReplayerWiring.eventOutput().solderTo(eventHasherWiring.eventInput());

        // Create the transformer that extracts keystone event sequence number from consensus rounds.
        // This is done here instead of in ConsensusEngineWiring, since the transformer needs to be soldered with
        // specified ordering, relative to the wire carrying consensus rounds to the round handler
        final WireTransformer<ConsensusRound, Long> keystoneEventSequenceNumberTransformer = new WireTransformer<>(
                model, "getKeystoneEventSequenceNumber", "rounds", round -> round.getKeystoneEvent()
                        .getBaseEvent()
                        .getStreamSequenceNumber());
        keystoneEventSequenceNumberTransformer.getOutputWire().solderTo(pcesWriterWiring.flushRequestInputWire());

        // The request to flush the keystone event for a round must be sent to the PCES writer before the consensus
        // round is passed to the round handler. This prevents a deadlock scenario where the consensus round
        // handler has a full queue and won't accept additional rounds, and is waiting on a keystone event to be
        // durably flushed to disk. Meanwhile, the PCES writer hasn't even received the flush request yet, so the
        // necessary keystone event is *never* flushed.
        consensusEngineWiring
                .consensusRoundOutput()
                .orderedSolderTo(List.of(
                        keystoneEventSequenceNumberTransformer.getInputWire(),
                        consensusRoundHandlerWiring.roundInput()));
        consensusEngineWiring.consensusRoundOutput().solderTo(eventWindowManagerWiring.consensusRoundInput());
        consensusEngineWiring.consensusEventsOutput().solderTo(eventStreamManagerWiring.eventsInput());
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
            @NonNull final LatestCompleteStateNexus latestCompleteStateNexus,
            @NonNull final NotificationEngine notificationEngine) {

        signedStateFileManagerWiring
                .stateWrittenToDiskOutputWire()
                .solderTo(
                        "statusManager_submitStateWritten",
                        "state written to disk notification",
                        statusManager::submitStatusAction);

        stateSignerWiring
                .stateSignature()
                .solderTo("transactionPool", "state signature transactions", transactionPool::submitSystemTransaction);

        stateSignatureCollectorWiring
                .getCompleteStatesOutput()
                .solderTo("latestCompleteStateNexus", "complete states", latestCompleteStateNexus::setStateIfNewer);

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
     * @param eventHasher             the event hasher to bind
     * @param internalEventValidator  the internal event validator to bind
     * @param eventDeduplicator       the event deduplicator to bind
     * @param eventSignatureValidator the event signature validator to bind
     * @param orphanBuffer            the orphan buffer to bind
     * @param inOrderLinker           the in order linker to bind
     * @param consensusEngine         the consensus engine to bind
     * @param signedStateFileManager  the signed state file manager to bind
     * @param stateSigner             the state signer to bind
     * @param pcesReplayer            the PCES replayer to bind
     * @param pcesWriter              the PCES writer to bind
     * @param eventDurabilityNexus    the event durability nexus to bind
     * @param shadowgraph             the shadowgraph to bind
     * @param pcesSequencer           the PCES sequencer to bind
     * @param eventCreationManager    the event creation manager to bind
     * @param swirldStateManager      the swirld state manager to bind
     * @param stateSignatureCollector the signed state manager to bind
     * @param consensusRoundHandler   the consensus round handler to bind
     * @param eventStreamManager      the event stream manager to bind
     * @param futureEventBuffer       the future event buffer to bind
     * @param issDetector             the ISS detector to bind
     * @param issHandler              the ISS handler to bind
     * @param hashLogger              the hash logger to bind
     * @param completeStateNotifier   the latest complete state notifier to bind
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
            @NonNull final SwirldStateManager swirldStateManager,
            @NonNull final StateSignatureCollector stateSignatureCollector,
            @NonNull final ConsensusRoundHandler consensusRoundHandler,
            @NonNull final EventStreamManager<EventImpl> eventStreamManager,
            @NonNull final FutureEventBuffer futureEventBuffer,
            @NonNull final IssDetector issDetector,
            @NonNull final IssHandler issHandler,
            @NonNull final HashLogger hashLogger,
            @NonNull final LatestCompleteStateNotifier completeStateNotifier) {

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
        applicationTransactionPrehandlerWiring.bind(swirldStateManager);
        stateSignatureCollectorWiring.bind(stateSignatureCollector);
        consensusRoundHandlerWiring.bind(consensusRoundHandler);
        eventStreamManagerWiring.bind(eventStreamManager);
        futureEventBufferWiring.bind(futureEventBuffer);
        issDetectorWiring.bind(issDetector);
        issHandlerWiring.bind(issHandler);
        hashLoggerWiring.bind(hashLogger);
        latestCompleteStateNotifierWiring.bind(completeStateNotifier);
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
     * @return the input wire for collecting post-consensus signatures
     */
    @NonNull
    public InputWire<ConsensusRound> getSignatureCollectorConsensusInput() {
        return stateSignatureCollectorWiring.getConsensusRoundInput();
    }

    /**
     * @return the input wire for states that need their signatures collected
     */
    @NonNull
    public InputWire<ReservedSignedState> getSignatureCollectorStateInput() {
        return stateSignatureCollectorWiring.getReservedStateInput();
    }

    /**
     * Get the input wire for signing a state
     * <p>
     * Future work: this is a temporary hook to allow the components to sign a state, prior to the whole system being
     * migrated to the new framework.
     *
     * @return the input wire for signing a state
     */
    @NonNull
    public InputWire<ReservedSignedState> getSignStateInput() {
        return stateSignerWiring.signState();
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
        return postHashCollectorWiring.unprocessedTaskCountSupplier();
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
        eventWindowManagerWiring.manualWindowInput().inject(nonAncientEventWindow);

        // Since there is asynchronous access to the shadowgraph, it's important to ensure that
        // it has fully ingested the new event window before continuing.
        shadowgraphWiring.flushRunnable().run();
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

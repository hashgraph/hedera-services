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

package com.swirlds.platform.gossip.sync.turbo;

import static com.swirlds.platform.gossip.shadowgraph.SyncUtils.filterLikelyDuplicates;
import static com.swirlds.platform.gossip.shadowgraph.SyncUtils.getMyTipsTheyKnow;
import static com.swirlds.platform.gossip.shadowgraph.SyncUtils.getTheirTipsIHave;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.pool.ParallelExecutionException;
import com.swirlds.common.threading.pool.ParallelExecutor;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.FallenBehindManager;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.gossip.SyncException;
import com.swirlds.platform.gossip.shadowgraph.GenerationReservation;
import com.swirlds.platform.gossip.shadowgraph.Generations;
import com.swirlds.platform.gossip.shadowgraph.LatestEventTipsetTracker;
import com.swirlds.platform.gossip.shadowgraph.ShadowEvent;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.gossip.shadowgraph.SyncFallenBehindStatus;
import com.swirlds.platform.gossip.shadowgraph.SyncUtils;
import com.swirlds.platform.gossip.sync.SyncInputStream;
import com.swirlds.platform.gossip.sync.SyncOutputStream;
import com.swirlds.platform.gossip.sync.config.SyncConfig;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.EventDescriptor;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * This class is responsible for managing the business logic of an initialized {@link TurboSyncProtocol}. Where as
 * {@link TurboSyncProtocol} is responsible for deciding when to start syncing, this class is responsible for actually
 * performing the syncing.
 */
public class TurboSyncRunner {

    /**
     * Used to play nicely with the parallel execution API.
     */
    private static final Runnable NO_OP = () -> {};

    /**
     * The platform context.
     */
    private final PlatformContext platformContext;

    /**
     * The ID of this node.
     */
    private final NodeId selfId;

    /**
     * The ID of the peer we are syncing with.
     */
    private final NodeId peerId;

    /**
     * Tracks the number of peers that tell us we have fallen behind and signals when we have fallen behind.
     */
    private final FallenBehindManager fallenBehindManager;

    /**
     * Will return true if gossip has been halted.
     */
    private final BooleanSupplier gossipHalted;

    /**
     * Will return true if the intake pipeline is too full.
     */
    private final BooleanSupplier intakeIsTooFull;

    /**
     * Counts the number of events from each peer that are in the intake pipeline.
     */
    private final IntakeEventCounter intakeEventCounter;

    /**
     * The connection to the peer we are syncing with.
     */
    private final Connection connection;

    /**
     * The output stream to the peer.
     */
    private final SyncOutputStream dataOutputStream;

    /**
     * The input stream from the peer.
     */
    private final SyncInputStream dataInputStream;

    /**
     * The executor to use for parallel read/write operations.
     */
    private final ParallelExecutor executor;

    /**
     * The shadowgraph, contains events we know about.
     */
    private final ShadowGraph shadowgraph;

    /**
     * A supplier of the current graph generations.
     */
    private final Supplier<GraphGenerations> generationsSupplier;

    /**
     * Tracks the tipset of the latest self event and also tracks childless events.
     */
    private final LatestEventTipsetTracker latestEventTipsetTracker;

    /**
     * When we receive an event from a peer, pass it to this consumer.
     */
    private final InterruptableConsumer<GossipEvent> gossipEventConsumer;

    /**
     * Metrics tracking syncing.
     */
    private final SyncMetrics syncMetrics;

    /**
     * For events that are neither self events nor ancestors of self events, we must have had this event for at least
     * this amount of time before it is eligible to be sent.
     */
    private final Duration nonAncestorFilterThreshold;

    /**
     * If true, then the hash of events is computed on the sync thread. If false, then the hash of events is computed
     * elsewhere.
     */
    private final boolean hashOnSyncThread;

    /**
     * The current sync cycle number. This value is sent once per sync iteration as a sanity check to ensure that the
     * streams are aligned. When this reaches {@link Integer#MAX_VALUE} it will wrap around into negative numbers, which
     * is fine.
     */
    private int cycleNumber = 0;

    /**
     * The maximum number of tips that are supported.
     */
    private final int maxTipCount;

    /**
     * The maximum number of events that are allowed to be in the intake pipeline for a single peer.
     */
    private final int maximumPermissibleEventsInIntake;

    /**
     * If true, then we have decided we want to abort the sync protocol. We still have to do a little more work before
     * we can actually abort in order to avoid leaving garbage on the wire.
     */
    private boolean abortRequested = false;

    /**
     * If true, then the peer has requested that we abort the sync protocol. We still have to do a little more work
     * before we can actually abort in order to avoid leaving garbage on the wire.
     */
    private boolean peerRequestedAbort = false;

    /**
     * All data received from the peer in the current iteration is written here. At the end of the phase, it is shifted
     * to dataReceivedB.
     */
    private TurboSyncDataReceived dataReceivedA;

    /**
     * All data received from the peer in the previous iteration is stored here. At the end of the phase, it is shifted
     * to dataReceivedC.
     */
    private TurboSyncDataReceived dataReceivedB;

    /**
     * All data received from the peer in the iteration before the previous iteration is stored here. At the end of the
     * phase, it is deleted.
     */
    private TurboSyncDataReceived dataReceivedC;

    /**
     * Information about what we sent to the peer in the current iteration is written here. At the end of the phase, it
     * is shifted to dataSentB.
     */
    private TurboSyncDataSent dataSentA;

    /**
     * Information about what we sent to the peer in the previous iteration is written here. At the end of the phase, it
     * is shifted to dataSentC.
     */
    private TurboSyncDataSent dataSentB;

    /**
     * Information about what we sent to the peer in the iteration before the previous iteration is written here. At the
     * end of the phase, it is deleted.
     */
    private TurboSyncDataSent dataSentC;

    /**
     * Constructor.
     *
     * @param platformContext          the platform context
     * @param addressBook              the address book
     * @param selfId                   our ID
     * @param peerId                   the ID of the peer we are syncing with
     * @param fallenBehindManager      tracks if we are behind or not
     * @param gossipHalted             returns true if gossip is halted, false otherwise
     * @param intakeIsTooFull          returns true if the intake queue is too full, false otherwise
     * @param intakeEventCounter       the intake event counter, counts how many events from each peer are in the intake
     *                                 pipeline
     * @param connection               the connection to the peer we are syncing with
     * @param executor                 the executor to use for parallel read/write operations
     * @param shadowgraph              the shadowgraph, contains events we know about
     * @param generationsSupplier      a supplier of the current graph generations
     * @param latestEventTipsetTracker a tracker of the latest event tipset
     * @param gossipEventConsumer      a consumer for gossip events
     */
    public TurboSyncRunner(
            @NonNull final PlatformContext platformContext,
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            @NonNull final NodeId peerId,
            @NonNull final FallenBehindManager fallenBehindManager,
            @NonNull final BooleanSupplier gossipHalted,
            @NonNull final BooleanSupplier intakeIsTooFull,
            @NonNull final IntakeEventCounter intakeEventCounter,
            @NonNull final Connection connection,
            @NonNull final ParallelExecutor executor,
            @NonNull final ShadowGraph shadowgraph,
            @NonNull final Supplier<GraphGenerations> generationsSupplier,
            @NonNull final LatestEventTipsetTracker latestEventTipsetTracker,
            @NonNull final InterruptableConsumer<GossipEvent> gossipEventConsumer,
            @NonNull final SyncMetrics syncMetrics) {

        this.platformContext = Objects.requireNonNull(platformContext);
        this.selfId = Objects.requireNonNull(selfId);
        this.peerId = Objects.requireNonNull(peerId);
        this.fallenBehindManager = Objects.requireNonNull(fallenBehindManager);
        this.gossipHalted = Objects.requireNonNull(gossipHalted);
        this.intakeIsTooFull = Objects.requireNonNull(intakeIsTooFull);
        this.intakeEventCounter = Objects.requireNonNull(intakeEventCounter);
        this.connection = Objects.requireNonNull(connection);
        this.dataOutputStream = connection.getDos();
        this.dataInputStream = connection.getDis();
        this.executor = Objects.requireNonNull(executor);
        this.shadowgraph = Objects.requireNonNull(shadowgraph);
        this.generationsSupplier = Objects.requireNonNull(generationsSupplier);
        this.latestEventTipsetTracker = Objects.requireNonNull(latestEventTipsetTracker);
        this.gossipEventConsumer = Objects.requireNonNull(gossipEventConsumer);

        this.maxTipCount = addressBook.getSize() * 2;

        final SyncConfig syncConfig = platformContext.getConfiguration().getConfigData(SyncConfig.class);
        this.nonAncestorFilterThreshold = syncConfig.nonAncestorFilterThreshold();
        this.hashOnSyncThread = syncConfig.hashOnGossipThreads();
        this.maximumPermissibleEventsInIntake = syncConfig.maximumPermissibleEventsInIntake();
        this.syncMetrics = Objects.requireNonNull(syncMetrics);
    }

    /**
     * Run the protocol. This method will block until the protocol is complete.
     */
    public void run() throws IOException, ParallelExecutionException {
        try {
            do {
                checkIfProtocolShouldBeAborted();
                runProtocolIteration();
                shiftDataWindow();
                cycleNumber++;
            } while (!abortRequested && !peerRequestedAbort && !areNodesOutOfSync());
        } finally {
            if (dataSentA != null) {
                dataSentA.release();
            }
            if (dataSentB != null) {
                dataSentB.release();
            }
            if (dataSentC != null) {
                dataSentC.release();
            }
        }
    }

    /**
     * Check if the nodes are out of sync (i.e. we have fallen behind, or the peer has fallen behind). When nodes go out
     * of sync we can abort the sync protocol immediately since the peer will reach the same conclusion in the same
     * iteration that we do.
     *
     * @return true if the nodes are out of sync, false otherwise
     */
    private boolean areNodesOutOfSync() {
        // The most recent exchanged generations will be in phase B.

        final Generations myGenerations = dataSentB.generationsSent();
        final Generations theirGenerations = dataReceivedB.theirGenerations();

        final SyncFallenBehindStatus status = SyncFallenBehindStatus.getStatus(myGenerations, theirGenerations);
        switch (status) {
            case NONE_FALLEN_BEHIND -> {
                fallenBehindManager.reportNotFallenBehind(peerId);
                return false;
            }
            case SELF_FALLEN_BEHIND -> {
                fallenBehindManager.reportFallenBehind(peerId);
                return true;
            }
            case OTHER_FALLEN_BEHIND -> {
                // The peer will realize it has fallen behind and will stop syncing.
                return true;
            }
            default -> throw new IllegalStateException("Unexpected status: " + status);
        }
    }

    /**
     * Handles the state transitions when we want to abort a sync. There are several reasons why we might want to stop
     * syncing, such as being in a fallen behind status or having our queues fill up too much.
     */
    private void checkIfProtocolShouldBeAborted() {
        if (fallenBehindManager.hasFallenBehind()
                || gossipHalted.getAsBoolean()
                || intakeIsTooFull.getAsBoolean()
                || intakeEventCounter.getUnprocessedEventCount(peerId) > maximumPermissibleEventsInIntake) {

            // We need to abort. We will continue for one more cycle and then stop syncing.
            abortRequested = true;
        }
    }

    /**
     * Shift the data window.
     * <li>
     *     <ul>data in C is cleaned up</ul>
     *     <ul>C is replaced by B</ul>
     *     <ul>B is replaced by A</ul>
     *     <ul>A is nulled-out in preparation for the next iteration</ul>
     * </li>
     */
    private void shiftDataWindow() {
        if (dataSentC != null) {
            dataSentC.release();
        }

        dataSentC = dataSentB;
        dataSentB = dataSentA;
        dataSentA = null; // sendData will write into this variable

        dataReceivedC = dataReceivedB;
        dataReceivedB = dataReceivedA;
        dataReceivedA = null; // receiveData will write into this variable
    }

    /**
     * Perform a single iteration of the protocol.
     */
    private void runProtocolIteration() throws ParallelExecutionException {
        // TODO does this really need to happen on threads?
        executor.doParallel(this::receiveData, this::sendData, NO_OP);
        syncMetrics.syncComplete();
    }

    /**
     * Send all data we intend to send during this iteration of the protocol.
     */
    private void sendData() throws IOException, SyncException {
        // Sanity check: make sure both participants have aligned streams.
        dataOutputStream.writeInt(cycleNumber);

        dataOutputStream.writeBoolean(abortRequested);
        if (abortRequested) {
            return;
        }

        final List<EventImpl> tipsOfSendList = sendEvents();
        sendBooleans();
        final List<Hash> tipsSent = sendTips();

        final GenerationReservation generationReservation = shadowgraph.reserve();
        final Generations generations;

        try {
            generations = getGenerations(generationReservation.getGeneration());
            sendGenerations(generations);
            dataOutputStream.flush();
        } catch (final Throwable t) {
            generationReservation.close();
            return;
        }

        dataSentA = new TurboSyncDataSent(generationReservation, generations, tipsSent, tipsOfSendList);
    }

    /**
     * Receive all data we intend to receive during this iteration of the protocol.
     */
    private void receiveData() throws IOException, InterruptedException {

        // Sanity check: make sure both participants have aligned streams.
        final int cycleNumber = dataInputStream.readInt();
        if (cycleNumber != this.cycleNumber) {
            throw new IOException("Expected cycle " + this.cycleNumber + ", got " + cycleNumber);
        }

        if (dataInputStream.readBoolean()) {
            peerRequestedAbort = true;
            return;
        }

        receiveEvents();
        final List<Boolean> theirBooleans = receiveBooleans();
        final List<Hash> theirTips = receiveTips();
        final Generations theirGenerations = receiveGenerations();

        dataReceivedA = new TurboSyncDataReceived(theirGenerations, theirTips, theirBooleans);
    }

    /**
     * Compute our current tips and send them to the peer.
     *
     * @return the tips we sent
     */
    @NonNull
    private List<Hash> sendTips() throws IOException {

        final List<EventDescriptor> tipDescriptors = latestEventTipsetTracker.getTips();
        final List<Hash> myTips =
                tipDescriptors.stream().map(EventDescriptor::getHash).toList();

        // If we sync more rapidly than we create events then we will end up sending the same tips over and over.
        // The following serialization algorithm is designed to minimize the impact of this problem.
        //
        // 1) write the number of tips we are going to send
        // 2) for each tip:
        //    a) if the tip was not previously sent: write the integer -1 followed by the tip hash
        //    b) if the tip was previously sent: write the integer index of the tip in the previous list of tips

        dataOutputStream.writeInt(myTips.size());

        final List<Hash> previousTips = dataSentB == null ? List.of() : dataSentB.tipsSent();
        final Map<Hash, Integer> previousTipPositions = new HashMap<>();
        for (int i = 0; i < previousTips.size(); i++) {
            previousTipPositions.put(previousTips.get(i), i);
        }

        for (final Hash tip : myTips) {
            final int previousPosition = previousTipPositions.getOrDefault(tip, -1);
            dataOutputStream.writeInt(previousPosition);

            if (previousPosition == -1) {
                dataOutputStream.writeSerializable(tip, false);
            }
        }

        return myTips;
    }

    /**
     * Receive tips from the peer.
     *
     * @return the tips received
     */
    @NonNull
    private List<Hash> receiveTips() throws IOException {
        final int tipCount = dataInputStream.readInt();

        if (tipCount > maxTipCount) {
            throw new IOException("Peer sent too many tips: " + tipCount);
        }

        final List<Hash> previousTips = dataReceivedB == null ? List.of() : dataReceivedB.theirTips();

        final List<Hash> tips = new ArrayList<>(tipCount);
        for (int i = 0; i < tipCount; i++) {
            final int previousPosition = dataInputStream.readInt();

            if (previousPosition == -1) {
                final Hash tip = dataInputStream.readSerializable(false, Hash::new);
                tips.add(tip);
            } else {
                tips.add(previousTips.get(previousPosition));
            }
        }

        return tips;
    }

    /**
     * Send our generations to the peer.
     */
    private void sendGenerations(@NonNull final Generations generations) throws IOException {
        dataOutputStream.writeGenerations(generations);
    }

    /**
     * Receive generations from the peer.
     */
    @NonNull
    private Generations receiveGenerations() throws IOException {
        return dataInputStream.readGenerations();
    }

    /**
     * Get the generations we intend to send to the peer.
     *
     * @param minRoundGen the minimum round generation we intend to send
     * @return the generations we intend to send
     */
    @NonNull
    private Generations getGenerations(final long minRoundGen) {
        return new Generations(
                minRoundGen,
                generationsSupplier.get().getMinGenerationNonAncient(),
                generationsSupplier.get().getMaxRoundGeneration());
    }

    /**
     * Look at the tips sent to us in dataReceivedB and send a list of booleans indicating which tips we have.
     */
    private void sendBooleans() throws IOException {
        if (dataReceivedB == null) {
            // We haven't yet received the tips and generations from the peer.
            // This happens right at the beginning of the protocol.
            return;
        }

        // For each tip they send us, determine if we have that event.
        // For each tip, send true if we have the event and false if we don't.
        final List<ShadowEvent> theirTips = shadowgraph.shadows(dataReceivedB.theirTips());
        final List<Boolean> theirTipsIHave = getTheirTipsIHave(theirTips);

        dataOutputStream.writeBooleanList(theirTipsIHave);
    }

    /**
     * Receive a list of booleans indicating which tips the peer has. The peer will send us a list of booleans
     * corresponding to the tips we sent in dataSentB indicating which tips they have and which tips they do not have.
     */
    @NonNull
    private List<Boolean> receiveBooleans() throws IOException {
        if (dataSentB == null) {
            // We haven't yet sent the tips and generations to the peer.
            // This happens right at the beginning of the protocol.
            return List.of();
        }

        return dataInputStream.readBooleanList(dataSentB.tipsSent().size());
    }

    /**
     * Send events needed by the peer.
     *
     * @return a list of the tip hashes for the events that were sent, used to prevent the sending of the same event
     * multiple times to the same peer
     */
    @NonNull
    private List<EventImpl> sendEvents() throws IOException, SyncException {
        if (dataSentC == null) {
            // We haven't yet sent the booleans to the peer.
            // This happens right at the beginning of the protocol.
            return List.of();
        }

        final List<EventImpl> eventsToSend = getEventsToSend();

        dataOutputStream.writeInt(eventsToSend.size());
        for (final EventImpl event : eventsToSend) {
            dataOutputStream.writeEventData(event);
        }

        syncMetrics.registerNumberOfEventsSent(eventsToSend.size());

        return SyncUtils.computeSentTips(
                dataSentB.tipsOfEventsSent(),
                eventsToSend,
                dataReceivedC.theirGenerations().getMinGenerationNonAncient());
    }

    /**
     * Receive events from the peer.
     */
    private void receiveEvents() throws IOException, InterruptedException {
        if (dataReceivedC == null) {
            // We haven't yet received the booleans from the peer.
            // This happens right at the beginning of the protocol.
            return;
        }

        final int eventCount = dataInputStream.readInt();
        syncMetrics.registerNumberOfEventsReceived(eventCount);

        for (int i = 0; i < eventCount; i++) {
            final GossipEvent event = dataInputStream.readEventData();

            if (hashOnSyncThread) {
                platformContext.getCryptography().digestSync(event.getHashedData());
                event.buildDescriptor();
            }

            gossipEventConsumer.accept(event);
        }
    }

    /**
     * Get a list of events to send.
     *
     * @return the events to send
     */
    @NonNull
    private List<EventImpl> getEventsToSend() throws SyncException {

        final Set<ShadowEvent> knownSet = getEventsTheyHave();

        final Generations myGenerations = dataSentC.generationsSent();
        final Generations theirGenerations = dataReceivedC.theirGenerations();

        final Set<ShadowEvent> knownAncestors = shadowgraph.findAncestors(
                knownSet, SyncUtils.unknownNonAncient(knownSet, myGenerations, theirGenerations));

        // since knownAncestors is a lot bigger than knownSet, it is a lot cheaper to
        // add knownSet to knownAncestors then vice versa
        knownAncestors.addAll(knownSet);

        // predicate used to search for events to send
        final Predicate<ShadowEvent> knownAncestorsPredicate =
                SyncUtils.unknownNonAncient(knownAncestors, myGenerations, theirGenerations);

        // in order to get the peer the latest events, we get a new set of tips to search from
        final List<ShadowEvent> myNewTips = shadowgraph.getTips();

        // find all ancestors of tips that are not known
        final List<ShadowEvent> unknownTips =
                myNewTips.stream().filter(knownAncestorsPredicate).collect(Collectors.toList());
        final Set<ShadowEvent> sendSet = shadowgraph.findAncestors(unknownTips, knownAncestorsPredicate);
        // add the tips themselves
        sendSet.addAll(unknownTips);

        final List<EventImpl> eventsTheyMayNeed =
                sendSet.stream().map(ShadowEvent::getEvent).collect(Collectors.toCollection(ArrayList::new));

        final List<EventImpl> sendList = filterLikelyDuplicates(
                selfId,
                nonAncestorFilterThreshold,
                platformContext.getTime().now(),
                eventsTheyMayNeed,
                latestEventTipsetTracker.getTipsetOfLatestSelfEvent(eventsTheyMayNeed));

        SyncUtils.sort(sendList);
        return sendList;
    }

    /**
     * Given all the data we currently know, determine which events we know the peer has.
     *
     * @return the events we know the peer has
     */
    @NonNull
    private Set<ShadowEvent> getEventsTheyHave() throws SyncException {
        final Set<ShadowEvent> eventsTheyHave = new HashSet<>();

        // Add the most recent tips they have sent us.
        // Note: we intentionally take the tips from phase B and phase C.
        // The original sync algorithm used just tips from phase C. But since we
        // have more recent information from phase B we'd might as well use it.
        eventsTheyHave.addAll(shadowgraph.shadowsIfPresent(dataReceivedB.theirTips()));
        eventsTheyHave.addAll(shadowgraph.shadowsIfPresent(dataReceivedC.theirTips()));

        // Now, use the booleans they sent us in phase B to determine which of our tips they have.
        // The booleans in phase B will correspond to the tips we sent in phase C.
        eventsTheyHave.addAll(getMyTipsTheyKnow(
                connection, shadowgraph.shadows(dataSentC.tipsSent()), dataReceivedB.theirBooleans()));

        // Add the tips of the events we have previously sent.
        for (final EventImpl tip : dataSentB.tipsOfEventsSent()) {
            final ShadowEvent shadow = shadowgraph.shadow(tip);
            if (shadow != null) {
                eventsTheyHave.add(shadow);
            }
        }

        return eventsTheyHave;
    }
}

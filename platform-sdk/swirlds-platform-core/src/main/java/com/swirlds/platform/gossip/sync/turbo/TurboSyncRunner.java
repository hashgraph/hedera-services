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
import com.swirlds.platform.gossip.SyncException;
import com.swirlds.platform.gossip.shadowgraph.GenerationReservation;
import com.swirlds.platform.gossip.shadowgraph.Generations;
import com.swirlds.platform.gossip.shadowgraph.ShadowEvent;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.gossip.shadowgraph.SyncUtils;
import com.swirlds.platform.gossip.sync.SyncInputStream;
import com.swirlds.platform.gossip.sync.SyncOutputStream;
import com.swirlds.platform.gossip.sync.config.SyncConfig;
import com.swirlds.platform.gossip.sync.protocol.TurboSyncProtocol;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.network.Connection;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * This class is responsible for managing the business logic of an initialized {@link TurboSyncProtocol}. Where as
 * {@link TurboSyncProtocol} is responsible for deciding when to start syncing, this class is responsible for actually
 * performing the syncing.
 */
public class TurboSyncRunner {

    private static final Runnable NO_OP = () -> {};

    private final PlatformContext platformContext;
    private final NodeId selfId;
    private final Connection connection;
    private final SyncOutputStream dataOutputStream;
    private final SyncInputStream dataInputStream;
    private final ParallelExecutor executor;
    private final ShadowGraph shadowgraph;
    private final Supplier<GraphGenerations> generationsSupplier;
    private final InterruptableConsumer<GossipEvent> gossipEventConsumer;

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

    /*

    3 phases:
      - A: send/receive tips and generations
      - B: send/receive booleans
      - C: send/receive events

    In each iteration, perform all three phases in the order C -> B -> A.

    Phase C:
      - Copy data in current phase B into current phase C
      - use data to determine which events to send
      - send and receive events

    Phase B:
      - Copy data in current phase A into current phase B
      - use data to determine which booleans to send
      - send and receive booleans

    Phase A:
      - compute tips and generations
      - Send/receive tips and generations
      - abort protocol if one node is behind

     */

    private TurboSyncDataReceived dataReceivedA;
    private TurboSyncDataReceived dataReceivedB;
    private TurboSyncDataReceived dataReceivedC;

    private TurboSyncDataSent dataSentA;
    private TurboSyncDataSent dataSentB;
    private TurboSyncDataSent dataSentC;

    //    /**
    //     * Data that was received during the previous cycle. Is used in conjunction with {@link }to determine what
    // data is sent during the current
    //     * cycle.
    //     */
    //    private TurboSyncDataReceived dataPreviouslyReceived;
    //
    //    /**
    //     * Data received during the current cycle is written here. Will be used during the next cycle to determine
    // what
    //     * data to send.
    //     */
    //    private TurboSyncDataReceived dataBeingReceived;
    //
    //    /**
    //     * Data that was sent during the previous cycle. Is used to determine what data is sent during the current
    //     * cycle.
    //     */
    //    private TurboSyncDataSent dataPreviouslySent;
    //    private TurboSyncDataSent dataBeingSent;

    private boolean continueProtocol = true;

    private long cycleNumber = 0;

    /**
     * Constructor.
     *
     * @param platformContext     the platform context
     * @param selfId              our ID
     * @param connection          the connection to the peer we are syncing with
     * @param executor            the executor to use for parallel read/write operations
     * @param shadowgraph         the shadowgraph, contains events we know about
     * @param generationsSupplier a supplier of the current graph generations
     * @param gossipEventConsumer a consumer for gossip events
     */
    public TurboSyncRunner(
            @NonNull final PlatformContext platformContext,
            @NonNull final NodeId selfId,
            @NonNull final Connection connection,
            @NonNull final ParallelExecutor executor,
            @NonNull final ShadowGraph shadowgraph,
            @NonNull final Supplier<GraphGenerations> generationsSupplier,
            @NonNull final InterruptableConsumer<GossipEvent> gossipEventConsumer) {

        this.platformContext = Objects.requireNonNull(platformContext);
        this.selfId = Objects.requireNonNull(selfId);
        this.connection = Objects.requireNonNull(connection);
        this.dataOutputStream = connection.getDos();
        this.dataInputStream = connection.getDis();
        this.executor = Objects.requireNonNull(executor);
        this.shadowgraph = Objects.requireNonNull(shadowgraph);
        this.generationsSupplier = Objects.requireNonNull(generationsSupplier);
        this.gossipEventConsumer = Objects.requireNonNull(gossipEventConsumer);

        final SyncConfig syncConfig = platformContext.getConfiguration().getConfigData(SyncConfig.class);
        this.nonAncestorFilterThreshold = syncConfig.nonAncestorFilterThreshold();
        this.hashOnSyncThread = syncConfig.hashOnGossipThreads();
    }

    /**
     * Run the protocol. This method will block until the protocol is complete.
     */
    public void run() throws IOException, ParallelExecutionException {
        try {
            while (continueProtocol) {
                runProtocolIteration();
                cycleNumber++;
            }
        } finally {
            // TODO I don't think this is thread safe...
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
     * Perform a single iteration of the protocol. Within a single iteration we will perform each of the three phases:
     * A, B, and C.
     */
    private void runProtocolIteration() throws ParallelExecutionException {

        // Move the data down the pipeline.
        //  - C is replaced by B
        //  - B is replaced by A
        //  - A is nulled-out in preparation for this next iteration.

        if (dataSentC != null) {
            dataSentC.release();
        }

        dataSentC = dataSentB;
        dataSentB = dataSentA;
        dataSentA = null; // sendData will write into this variable

        dataReceivedC = dataReceivedB;
        dataReceivedB = dataReceivedA;
        dataReceivedA = null; // receiveData will write into this variable

        executor.doParallel(this::receiveData, this::sendData, NO_OP);

        // TODO break out of protocol if we fall behind or if intake fills up too much
    }

    /**
     * Send all data we intend to send during this iteration of the protocol.
     */
    private void sendData() throws IOException, SyncException {
        // Sanity check
        dataOutputStream.writeLong(cycleNumber);

        final List<Hash> tipsOfSendList = sendEvents();
        sendBooleans();
        final TipsAndReservedGenerations tipsAndReservedGenerations = sendTipsAndGenerations();
        dataOutputStream.flush();

        dataSentA = new TurboSyncDataSent(
                tipsAndReservedGenerations.reservedGenerations(),
                tipsAndReservedGenerations.generationsSent(),
                tipsAndReservedGenerations.tipsSent(),
                tipsOfSendList);
    }

    /**
     * Receive all data we intend to receive during this iteration of the protocol.
     */
    private void receiveData() throws IOException, InterruptedException {

        // TODO decide if this is worth keeping as a sanity check
        final long cycleNumber = dataInputStream.readLong();
        if (cycleNumber != this.cycleNumber) {
            throw new IOException("Expected cycle " + this.cycleNumber + ", got " + cycleNumber);
        }

        receiveEvents();
        final List<Boolean> theirBooleans = receiveBooleans();
        final TipsAndGenerations theirTipsAndGenerations = receiveTipsAndGenerations();

        dataReceivedA = new TurboSyncDataReceived(
                theirTipsAndGenerations.generations(), theirTipsAndGenerations.tips(), theirBooleans);
    }

    /**
     * Look at the shadowgraph and compute the tips and generations we need to send to the peer.
     */
    @NonNull
    private TipsAndReservedGenerations sendTipsAndGenerations() throws IOException {
        final GenerationReservation generationReservation = shadowgraph.reserve();
        final Generations generations = getGenerations(generationReservation.getGeneration());
        final List<Hash> myTips = shadowgraph.getTips().stream()
                .map(e -> e.getEvent().getBaseHash())
                .toList();

        dataOutputStream.writeGenerations(generations);

        // TODO: important optimization: don't resend the same tips over and over again

        dataOutputStream.writeTipHashes(myTips);

        return new TipsAndReservedGenerations(generationReservation, generations, myTips);
    }

    /**
     * Receive tips and generations from the peer.
     */
    @NonNull
    private TipsAndGenerations receiveTipsAndGenerations() throws IOException {
        final Generations theirGenerations = dataInputStream.readGenerations();
        final List<Hash> theirTips = dataInputStream.readTipHashes(1024); // TODO use number of nodes
        return new TipsAndGenerations(theirGenerations, theirTips);
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
    private List<Hash> sendEvents() throws IOException, SyncException {
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

        return SyncUtils.findTipHashesOfEventList(eventsToSend);
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
                null); // TODO tipset

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

        // Add the tips of the events we sent in the previous sync.
        eventsTheyHave.addAll(shadowgraph.shadows(dataSentB.tipsOfSendList()));

        return eventsTheyHave;
    }
}

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
import com.swirlds.platform.gossip.shadowgraph.LatestEventTipsetTracker;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private final LatestEventTipsetTracker latestEventTipsetTracker;
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

    private TurboSyncDataReceived dataReceivedA;
    private TurboSyncDataReceived dataReceivedB;
    private TurboSyncDataReceived dataReceivedC;

    private TurboSyncDataSent dataSentA;
    private TurboSyncDataSent dataSentB;
    private TurboSyncDataSent dataSentC;

    private boolean continueProtocol = true;

    private long cycleNumber = 0;

    /**
     * Constructor.
     *
     * @param platformContext          the platform context
     * @param selfId                   our ID
     * @param connection               the connection to the peer we are syncing with
     * @param executor                 the executor to use for parallel read/write operations
     * @param shadowgraph              the shadowgraph, contains events we know about
     * @param generationsSupplier      a supplier of the current graph generations
     * @param latestEventTipsetTracker a tracker of the latest event tipset
     * @param gossipEventConsumer      a consumer for gossip events
     */
    public TurboSyncRunner(
            @NonNull final PlatformContext platformContext,
            @NonNull final NodeId selfId,
            @NonNull final Connection connection,
            @NonNull final ParallelExecutor executor,
            @NonNull final ShadowGraph shadowgraph,
            @NonNull final Supplier<GraphGenerations> generationsSupplier,
            @NonNull final LatestEventTipsetTracker latestEventTipsetTracker,
            @NonNull final InterruptableConsumer<GossipEvent> gossipEventConsumer) {

        this.platformContext = Objects.requireNonNull(platformContext);
        this.selfId = Objects.requireNonNull(selfId);
        this.connection = Objects.requireNonNull(connection);
        this.dataOutputStream = connection.getDos();
        this.dataInputStream = connection.getDis();
        this.executor = Objects.requireNonNull(executor);
        this.shadowgraph = Objects.requireNonNull(shadowgraph);
        this.generationsSupplier = Objects.requireNonNull(generationsSupplier);
        this.latestEventTipsetTracker = Objects.requireNonNull(latestEventTipsetTracker);
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
        final List<Hash> tipsSent = sendTips();
        final ReservedGenerations reservedGenerations = sendGenerations();

        dataOutputStream.flush();

        dataSentA = new TurboSyncDataSent(
                reservedGenerations.reservedGenerations(),
                reservedGenerations.generationsSent(),
                tipsSent,
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
        final List<Hash> myTips = shadowgraph.getTips().stream()
                .map(e -> e.getEvent().getBaseHash())
                .toList();

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

        // TODO throw if tip count is too high

        final List<Hash> previousTips = dataReceivedB == null ? List.of() : dataReceivedB.theirTips();

        final List<Hash> tips = new ArrayList<>();
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
     * Compute our current generations and send them to the peer.
     */
    @NonNull
    private ReservedGenerations sendGenerations() throws IOException {
        final GenerationReservation generationReservation = shadowgraph.reserve();
        final Generations generations = getGenerations(generationReservation.getGeneration());
        dataOutputStream.writeGenerations(generations);

        return new ReservedGenerations(generationReservation, generations);
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

        // Add the tips of the events we sent in the previous sync.
        eventsTheyHave.addAll(shadowgraph.shadowsIfPresent(dataSentB.tipsOfSendList()));

        return eventsTheyHave;
    }
}

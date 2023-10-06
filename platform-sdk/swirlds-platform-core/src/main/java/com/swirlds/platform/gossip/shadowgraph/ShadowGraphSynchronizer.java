/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.gossip.shadowgraph;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.SYNC_INFO;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import com.swirlds.common.threading.pool.ParallelExecutionException;
import com.swirlds.common.threading.pool.ParallelExecutor;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.FallenBehindManager;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.gossip.SyncException;
import com.swirlds.platform.gossip.sync.config.SyncConfig;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.network.Connection;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The goal of the ShadowGraphSynchronizer is to compare graphs with a remote node, and update them so both sides have
 * the same events in the graph. This process is called a sync.
 * <p>
 * This instance can be called by multiple threads at the same time. To avoid accidental concurrency issues, all the
 * variables in this class are final. The ones that are used for storing information about an ongoing sync are method
 * local.
 */
public class ShadowGraphSynchronizer {
    private static final Logger logger = LogManager.getLogger(ShadowGraphSynchronizer.class);

    /**
     * The shadow graph manager to use for this sync
     */
    private final ShadowGraph shadowGraph;
    /**
     * Number of member nodes in the network for this sync
     */
    private final int numberOfNodes;
    /**
     * All sync stats
     */
    private final SyncMetrics syncMetrics;
    /**
     * provides the current consensus instance, a supplier is used because this instance will change after a reconnect,
     * so we have to make sure we always get the latest one
     */
    private final Supplier<GraphGenerations> generationsSupplier;
    /**
     * consumes events received by the peer
     */
    private final Consumer<GossipEvent> eventHandler;
    /**
     * manages sync related decisions
     */
    private final FallenBehindManager fallenBehindManager;

    /**
     * Keeps track of how many events from each peer have been received, but haven't yet made it through the intake
     * pipeline
     */
    private final IntakeEventCounter intakeEventCounter;

    /**
     * executes tasks in parallel
     */
    private final ParallelExecutor executor;
    /**
     * if set to true, send and receive initial negotiation bytes at the start of the sync
     */
    private final boolean sendRecInitBytes;
    /**
     * executed before fetching the tips from the shadowgraph for the second time in phase 3
     */
    private final InterruptableRunnable executePreFetchTips;

    public ShadowGraphSynchronizer(
            @NonNull final PlatformContext platformContext,
            final ShadowGraph shadowGraph,
            final int numberOfNodes,
            final SyncMetrics syncMetrics,
            final Supplier<GraphGenerations> generationsSupplier,
            @NonNull final QueueThread<GossipEvent> intakeQueue,
            final FallenBehindManager fallenBehindManager,
            @NonNull final IntakeEventCounter intakeEventCounter,
            final ParallelExecutor executor,
            final boolean sendRecInitBytes,
            final InterruptableRunnable executePreFetchTips) {

        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(intakeQueue);

        this.shadowGraph = shadowGraph;
        this.numberOfNodes = numberOfNodes;
        this.syncMetrics = syncMetrics;
        this.generationsSupplier = generationsSupplier;
        this.fallenBehindManager = fallenBehindManager;
        this.intakeEventCounter = Objects.requireNonNull(intakeEventCounter);
        this.executor = executor;
        this.sendRecInitBytes = sendRecInitBytes;
        this.executePreFetchTips = executePreFetchTips;
        this.eventHandler = buildEventHandler(platformContext, intakeQueue);
    }

    /**
     * Construct the event handler for new events. If configured to do so, this handler will also hash events before
     * passing them down the pipeline.
     *
     * @param platformContext the platform context
     * @param intakeQueue     the event intake queue
     */
    private Consumer<GossipEvent> buildEventHandler(
            @NonNull final PlatformContext platformContext, @NonNull final QueueThread<GossipEvent> intakeQueue) {

        final boolean hashOnGossipThreads = platformContext
                .getConfiguration()
                .getConfigData(SyncConfig.class)
                .hashOnGossipThreads();

        final Consumer<GossipEvent> wrappedPut = event -> {
            try {
                intakeQueue.put(event);
            } catch (final InterruptedException e) {
                // should never happen, and we don't have a simple way of recovering from it
                logger.error(
                        EXCEPTION.getMarker(), "CRITICAL ERROR, adding intakeTask to the event intake queue failed", e);
                Thread.currentThread().interrupt();
            }
        };

        if (hashOnGossipThreads) {
            final Cryptography cryptography = platformContext.getCryptography();
            return event -> {
                cryptography.digestSync(event.getHashedData());
                event.buildDescriptor();

                wrappedPut.accept(event);
            };
        } else {
            return wrappedPut;
        }
    }

    private static List<Boolean> getMyBooleans(final List<ShadowEvent> theirTipShadows) {
        final List<Boolean> myBooleans = new ArrayList<>(theirTipShadows.size());
        for (final ShadowEvent s : theirTipShadows) {
            myBooleans.add(s != null); // is this event is known to me
        }
        return myBooleans;
    }

    private static List<ShadowEvent> processTheirBooleans(
            final Connection conn, final List<ShadowEvent> myTips, final List<Boolean> theirBooleans)
            throws SyncException {
        if (theirBooleans.size() != myTips.size()) {
            throw new SyncException(
                    conn,
                    String.format(
                            "peer booleans list is wrong size. Expected: %d Actual: %d,",
                            myTips.size(), theirBooleans.size()));
        }
        final List<ShadowEvent> knownTips = new ArrayList<>();
        // process their booleans
        for (int i = 0; i < theirBooleans.size(); i++) {
            if (Boolean.TRUE.equals(theirBooleans.get(i))) {
                knownTips.add(myTips.get(i));
            }
        }

        return knownTips;
    }

    /**
     * Synchronize with a remote node using the supplied connection
     *
     * @param conn the connection to sync through
     * @return true iff a sync was (a) accepted, and (b) completed, including exchange of event data
     * @throws IOException                if any problem occurs with the connection
     * @throws ParallelExecutionException if issue occurs while executing tasks in parallel
     * @throws SyncException              if any sync protocol issues occur
     * @throws InterruptedException       if the calling thread gets interrupted while the sync is ongoing
     */
    public boolean synchronize(final Connection conn)
            throws IOException, ParallelExecutionException, SyncException, InterruptedException {
        logger.info(SYNC_INFO.getMarker(), "{} sync start", conn.getDescription());
        try {
            return reserveSynchronize(conn);
        } finally {
            logger.info(SYNC_INFO.getMarker(), "{} sync end", conn.getDescription());
        }
    }

    /**
     * Executes a sync using the supplied connection. This method contains all the logic while
     * {@link #synchronize(Connection)} is just for exception handling.
     */
    private boolean reserveSynchronize(final Connection conn)
            throws IOException, ParallelExecutionException, SyncException, InterruptedException {
        // accumulates time points for each step in the execution of a single gossip session, used for stats
        // reporting and performance analysis
        final SyncTiming timing = new SyncTiming();
        final List<EventImpl> sendList;
        try (final GenerationReservation reservation = shadowGraph.reserve()) {
            conn.initForSync();

            timing.start();

            if (sendRecInitBytes) {
                SyncComms.writeFirstByte(conn);
            }

            // the generation we reserved is our minimum round generation
            // the ShadowGraph guarantees it won't be expired until we release it
            final Generations myGenerations = getGenerations(reservation.getGeneration());
            final List<ShadowEvent> myTips = getTips();
            // READ and WRITE generation numbers & tip hashes
            final Phase1Response theirGensTips = readWriteParallel(
                    SyncComms.phase1Read(conn, numberOfNodes, sendRecInitBytes),
                    SyncComms.phase1Write(conn, myGenerations, myTips),
                    conn);
            timing.setTimePoint(1);

            if (theirGensTips.isSyncRejected()) {
                logger.info(SYNC_INFO.getMarker(), "{} sync rejected by other", conn.getDescription());
                // null means the sync was rejected
                return false;
            }

            syncMetrics.generations(myGenerations, theirGensTips.getGenerations());

            if (fallenBehind(myGenerations, theirGensTips.getGenerations(), conn)) {
                // aborting the sync since someone has fallen behind
                return false;
            }

            // events that I know they already have
            final Set<ShadowEvent> knownSet = new HashSet<>();

            // process the hashes received
            final List<ShadowEvent> theirTipShadows = shadowGraph.shadows(theirGensTips.getTips());
            final List<Boolean> myBooleans = getMyBooleans(theirTipShadows);
            // add known shadows to known set
            theirTipShadows.stream().filter(Objects::nonNull).forEach(knownSet::add);

            // comms phase 2
            timing.setTimePoint(2);
            final List<Boolean> theirBooleans = readWriteParallel(
                    SyncComms.phase2Read(conn, myTips.size()), SyncComms.phase2Write(conn, myBooleans), conn);
            timing.setTimePoint(3);

            // process their booleans and add them to the known set
            final List<ShadowEvent> knownTips = processTheirBooleans(conn, myTips, theirBooleans);
            knownSet.addAll(knownTips);

            // create a send list based on the known set
            sendList = createSendList(knownSet, myGenerations, theirGensTips.getGenerations());
        }

        return phase3(conn, timing, sendList);
    }

    private Generations getGenerations(final long minRoundGen) {
        return new Generations(
                minRoundGen,
                generationsSupplier.get().getMinGenerationNonAncient(),
                generationsSupplier.get().getMaxRoundGeneration());
    }

    private List<ShadowEvent> getTips() {
        final List<ShadowEvent> myTips = shadowGraph.getTips();
        syncMetrics.updateTipsPerSync(myTips.size());
        syncMetrics.updateMultiTipsPerSync(SyncUtils.computeMultiTipCount(myTips));
        return myTips;
    }

    private boolean fallenBehind(final Generations self, final Generations other, final Connection conn) {
        final SyncFallenBehindStatus status = SyncFallenBehindStatus.getStatus(self, other);
        if (status == SyncFallenBehindStatus.SELF_FALLEN_BEHIND) {
            fallenBehindManager.reportFallenBehind(conn.getOtherId());
        }

        if (status != SyncFallenBehindStatus.NONE_FALLEN_BEHIND) {
            logger.info(SYNC_INFO.getMarker(), "{} aborting sync due to {}", conn.getDescription(), status);
            return true; // abort the sync
        }
        return false;
    }

    private List<EventImpl> createSendList(
            final Set<ShadowEvent> knownSet, final Generations myGenerations, final Generations theirGenerations)
            throws InterruptedException {
        // add to knownSet all the ancestors of each known event
        final Set<ShadowEvent> knownAncestors = shadowGraph.findAncestors(
                knownSet, SyncUtils.unknownNonAncient(knownSet, myGenerations, theirGenerations));

        // since knownAncestors is a lot bigger than knownSet, it is a lot cheaper to add knownSet to knownAncestors
        // then vice versa
        knownAncestors.addAll(knownSet);

        syncMetrics.knownSetSize(knownAncestors.size());

        // predicate used to search for events to send
        final Predicate<ShadowEvent> knownAncestorsPredicate =
                SyncUtils.unknownNonAncient(knownAncestors, myGenerations, theirGenerations);

        executePreFetchTips.run();
        // in order to get the peer the latest events, we get a new set of tips to search from
        final List<ShadowEvent> myNewTips = shadowGraph.getTips();

        // find all ancestors of tips that are not known
        final List<ShadowEvent> unknownTips =
                myNewTips.stream().filter(knownAncestorsPredicate).collect(Collectors.toList());
        final Set<ShadowEvent> sendSet = shadowGraph.findAncestors(unknownTips, knownAncestorsPredicate);
        // add the tips themselves
        sendSet.addAll(unknownTips);

        // convert to list
        final List<EventImpl> sendList =
                sendSet.stream().map(ShadowEvent::getEvent).collect(Collectors.toCollection(ArrayList::new));
        // sort by generation
        SyncUtils.sort(sendList);

        return sendList;
    }

    /**
     * Executes phase 3 of a sync
     *
     * @param conn     the connection to use
     * @param timing   metrics that track sync timing
     * @param sendList the events to send
     * @return true if the phase was successful, false if it was aborted
     * @throws ParallelExecutionException if anything goes wrong
     */
    private boolean phase3(final Connection conn, final SyncTiming timing, final List<EventImpl> sendList)
            throws ParallelExecutionException {
        timing.setTimePoint(4);
        // the reading thread uses this to indicate to the writing thread that it is done
        final CountDownLatch eventReadingDone = new CountDownLatch(1);
        // the writer will set it to true if writing is aborted
        final AtomicBoolean writeAborted = new AtomicBoolean(false);
        final Integer eventsRead = readWriteParallel(
                SyncComms.phase3Read(conn, eventHandler, syncMetrics, eventReadingDone, intakeEventCounter),
                SyncComms.phase3Write(conn, sendList, eventReadingDone, writeAborted),
                conn);
        if (eventsRead < 0 || writeAborted.get()) {
            // sync was aborted
            logger.info(SYNC_INFO.getMarker(), "{} sync aborted", conn::getDescription);
            return false;
        }
        logger.info(
                SYNC_INFO.getMarker(), "{} writing events done, wrote {} events", conn::getDescription, sendList::size);
        logger.info(SYNC_INFO.getMarker(), "{} reading events done, read {} events", conn.getDescription(), eventsRead);

        syncMetrics.syncDone(new SyncResult(conn.isOutbound(), conn.getOtherId(), eventsRead, sendList.size()));

        timing.setTimePoint(5);
        syncMetrics.recordSyncTiming(timing, conn);
        return true;
    }

    /**
     * A method to do reads and writes in parallel.
     * <p>
     * It is very important that the read task is executed by the caller thread. The reader thread can always time out,
     * if the writer thread gets blocked by a write method because the buffer is full, the only way to unblock it is to
     * close the connection. So the reader will close the connection and unblock the writer if it times out or if
     * anything goes wrong.
     *
     * @param readTask   read task
     * @param writeTask  write task
     * @param connection the connection to close if anything goes wrong
     * @param <T>        the return type of the read task and this method
     * @return whatever the read task returns
     * @throws ParallelExecutionException thrown if anything goes wrong during these read write operations. the
     *                                    connection will be closed before this exception is thrown
     */
    private <T> T readWriteParallel(
            final Callable<T> readTask, final Callable<Void> writeTask, final Connection connection)
            throws ParallelExecutionException {
        return executor.doParallel(readTask, writeTask, connection::disconnect);
    }

    /**
     * Reject a sync
     *
     * @param conn the connection over which the sync was initiated
     * @throws IOException if there are any connection issues
     */
    public void rejectSync(final Connection conn) throws IOException {
        try {
            conn.initForSync();
            SyncComms.rejectSync(conn, numberOfNodes);
        } finally {
            logger.info(SYNC_INFO.getMarker(), "{} sync rejected by self", conn.getDescription());
        }
    }
}

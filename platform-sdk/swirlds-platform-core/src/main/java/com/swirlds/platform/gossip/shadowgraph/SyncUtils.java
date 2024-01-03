/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.logging.legacy.LogMarker.SYNC_INFO;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.utility.CompareTo;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.creation.tipset.Tipset;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.gossip.SyncException;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.network.ByteConstants;
import com.swirlds.platform.network.Connection;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Various static utility method used in syncing
 */
public final class SyncUtils {

    private static final Logger logger = LogManager.getLogger();

    /**
     * Private constructor to never instantiate this class
     */
    private SyncUtils() {}

    public static void writeFirstByte(final Connection conn) throws IOException {
        if (conn.isOutbound()) { // caller WRITE sync request
            // try to initiate a sync
            conn.getDos().requestSync();
        } else { // listener WRITE sync request response
            conn.getDos().acceptSync();
        }
        // no need to flush, since we will write more data right after
    }

    public static void rejectSync(final Connection conn, final int numberOfNodes) throws IOException {
        // respond with a nack
        conn.getDos().rejectSync();
        conn.getDos().flush();

        // read data and ignore since we rejected the sync
        conn.getDis().readGenerations();
        conn.getDis().readTipHashes(numberOfNodes);
    }

    /**
     * Write the tips and generations to the peer. This is the first data exchanged during a sync (after protocol
     * negotiation). The complementary function to {@link #readTheirTipsAndGenerations(Connection, int, boolean)}.
     *
     * @param connection  the connection to write to
     * @param generations the generations to write
     * @param tips        the tips to write
     * @return a {@link Callable} that writes the tips and generations
     */
    public static Callable<Void> writeMyTipsAndGenerations(
            final Connection connection, final Generations generations, final List<ShadowEvent> tips) {
        return () -> {
            final List<Hash> tipHashes =
                    tips.stream().map(ShadowEvent::getEventBaseHash).collect(Collectors.toList());
            connection.getDos().writeGenerations(generations);
            connection.getDos().writeTipHashes(tipHashes);
            connection.getDos().flush();
            logger.info(
                    SYNC_INFO.getMarker(),
                    "{} sent generations: {}",
                    connection::getDescription,
                    generations::toString);
            logger.info(
                    SYNC_INFO.getMarker(),
                    "{} sent tips: {}",
                    connection::getDescription,
                    () -> SyncLogging.toShortShadows(tips));
            return null;
        };
    }

    /**
     * Read the tips and generations from the peer. This is the first data exchanged during a sync (after protocol
     * negotiation). The complementary function to {@link #writeMyTipsAndGenerations(Connection, Generations, List)}.
     *
     * @param connection    the connection to read from
     * @param numberOfNodes the number of nodes in the network
     * @param readInitByte  if true, read the first byte of the sync request response
     * @return a {@link Callable} that reads the tips and generations
     */
    public static Callable<TheirTipsAndGenerations> readTheirTipsAndGenerations(
            final Connection connection, final int numberOfNodes, final boolean readInitByte) {
        return () -> {
            // Caller thread requested a sync, so now caller thread reads if its request was accepted.
            if (connection.isOutbound() && readInitByte) {
                try {
                    if (!connection.getDis().readSyncRequestResponse()) {
                        // sync rejected
                        return TheirTipsAndGenerations.syncRejected();
                    }
                } catch (final SyncException | IOException e) {
                    final Instant sentTime = connection.getDos().getRequestSentTime();
                    final long inMs = sentTime == null ? -1 : sentTime.until(Instant.now(), ChronoUnit.MILLIS);
                    throw new SyncException(
                            connection,
                            "Problem while reading sync request response. Request was sent " + inMs + "ms ago",
                            e);
                }
            }

            final Generations generations = connection.getDis().readGenerations();
            final List<Hash> tips = connection.getDis().readTipHashes(numberOfNodes);

            logger.info(
                    SYNC_INFO.getMarker(),
                    "{} received generations: {}",
                    connection::getDescription,
                    generations::toString);
            logger.info(
                    SYNC_INFO.getMarker(),
                    "{} received tips: {}",
                    connection::getDescription,
                    () -> SyncLogging.toShortHashes(tips));

            return TheirTipsAndGenerations.create(generations, tips);
        };
    }

    /**
     * Tell the sync peer which of their tips I have. The complementary function to
     * {@link #readMyTipsTheyHave(Connection, int)}.
     *
     * @param connection     the connection to write to
     * @param theirTipsIHave for each tip they sent me, write true if I have it, false otherwise. Order corresponds to
     *                       the order in which they sent me their tips.
     * @return a {@link Callable} that writes the booleans
     */
    public static Callable<Void> writeTheirTipsIHave(final Connection connection, final List<Boolean> theirTipsIHave) {
        return () -> {
            connection.getDos().writeBooleanList(theirTipsIHave);
            connection.getDos().flush();
            logger.info(
                    SYNC_INFO.getMarker(),
                    "{} sent booleans: {}",
                    connection::getDescription,
                    () -> SyncLogging.toShortBooleans(theirTipsIHave));
            return null;
        };
    }

    /**
     * Read from the peer which of my tips they have. The complementary function to
     * {@link #writeTheirTipsIHave(Connection, List)}.
     *
     * @param connection   the connection to read from
     * @param numberOfTips the number of tips I sent them
     * @return a {@link Callable} that reads the booleans
     */
    public static Callable<List<Boolean>> readMyTipsTheyHave(final Connection connection, final int numberOfTips) {
        return () -> {
            final List<Boolean> booleans = connection.getDis().readBooleanList(numberOfTips);
            if (booleans == null) {
                throw new SyncException(connection, "peer sent null booleans");
            }
            logger.info(
                    SYNC_INFO.getMarker(),
                    "{} received booleans: {}",
                    connection::getDescription,
                    () -> SyncLogging.toShortBooleans(booleans));
            return booleans;
        };
    }

    /**
     * Send the events the peer needs. The complementary function to
     * {@link #readEventsINeed(Connection, Consumer, SyncMetrics, CountDownLatch, IntakeEventCounter, Duration)}.
     *
     * @param connection          the connection to write to
     * @param events              the events to write
     * @param eventReadingDone    used to know when the writing thread is done
     * @param writeAborted        set to true if writing is aborted
     * @param syncKeepalivePeriod send a keepalive message every this many milliseconds when writing events during a
     *                            sync
     * @return A {@link Callable} that executes this part of the sync
     */
    public static Callable<Void> sendEventsTheyNeed(
            final Connection connection,
            final List<EventImpl> events,
            final CountDownLatch eventReadingDone,
            final AtomicBoolean writeAborted,
            final Duration syncKeepalivePeriod) {
        return () -> {
            logger.info(
                    SYNC_INFO.getMarker(),
                    "{} writing events start. send list size: {}",
                    connection.getDescription(),
                    events.size());
            for (final EventImpl event : events) {
                if (event.isFromSignedState()) {
                    // if we encounter an event from a signed state, we should not send that event because it will have
                    // had its transactions removed. the receiver would get the wrong hash and the signature check
                    // would fail
                    connection.getDos().writeByte(ByteConstants.COMM_EVENT_ABORT);
                    writeAborted.set(true);
                    break;
                }
                connection.getDos().writeByte(ByteConstants.COMM_EVENT_NEXT);
                connection.getDos().writeEventData(event);
            }
            if (writeAborted.get()) {
                logger.info(SYNC_INFO.getMarker(), "{} writing events aborted", connection.getDescription());
            } else {
                logger.info(
                        SYNC_INFO.getMarker(),
                        "{} writing events done, wrote {} events",
                        connection.getDescription(),
                        events.size());
                connection.getDos().writeByte(ByteConstants.COMM_EVENT_DONE);
            }
            connection.getDos().flush();

            // if we are still reading events, send keepalive messages
            while (!eventReadingDone.await(syncKeepalivePeriod.toMillis(), TimeUnit.MILLISECONDS)) {
                connection.getDos().writeByte(ByteConstants.COMM_SYNC_ONGOING);
                connection.getDos().flush();
            }

            // we have now finished reading and writing all the events of a sync. the remote node may not have
            // finished reading and processing all the events this node has sent. so we write a byte to tell the remote
            // node we have finished, and the reader will wait for it to send us the same byte.
            connection.getDos().writeByte(ByteConstants.COMM_SYNC_DONE);
            connection.getDos().flush();

            logger.debug(SYNC_INFO.getMarker(), "{} sent COMM_SYNC_DONE", connection.getDescription());

            // (ignored)
            return null;
        };
    }

    /**
     * Read events from the peer that I need. The complementary function to
     * {@link #sendEventsTheyNeed(Connection, List, CountDownLatch, AtomicBoolean, Duration)}.
     *
     * @param connection         the connection to read from
     * @param eventHandler       the consumer of received events
     * @param syncMetrics        tracks event reading metrics
     * @param eventReadingDone   used to notify the writing thread that reading is done
     * @param intakeEventCounter keeps track of the number of events in the intake pipeline from each peer
     * @param maxSyncTime        the maximum amount of time to spend syncing with a peer, syncs that take longer than
     *                           this will be aborted
     * @return A {@link Callable} that executes this part of the sync
     */
    public static Callable<Integer> readEventsINeed(
            final Connection connection,
            final Consumer<GossipEvent> eventHandler,
            final SyncMetrics syncMetrics,
            final CountDownLatch eventReadingDone,
            @NonNull final IntakeEventCounter intakeEventCounter,
            @NonNull final Duration maxSyncTime) {

        return () -> {
            logger.info(SYNC_INFO.getMarker(), "{} reading events start", connection.getDescription());
            int eventsRead = 0;
            try {
                final long startTime = System.nanoTime();
                while (true) {
                    // readByte() will throw a timeout exception if the socket timeout is exceeded
                    final byte next = connection.getDis().readByte();
                    // if the peer continuously sends COMM_SYNC_ONGOING, or sends the data really slowly,
                    // this timeout will be triggered
                    checkEventExchangeTime(maxSyncTime, startTime);
                    switch (next) {
                        case ByteConstants.COMM_EVENT_NEXT -> {
                            final GossipEvent gossipEvent = connection.getDis().readEventData();

                            gossipEvent.setSenderId(connection.getOtherId());
                            intakeEventCounter.eventEnteredIntakePipeline(connection.getOtherId());

                            eventHandler.accept(gossipEvent);
                            eventsRead++;
                        }
                        case ByteConstants.COMM_EVENT_ABORT -> {
                            logger.info(
                                    SYNC_INFO.getMarker(), "{} reading events aborted", connection.getDescription());
                            // event reading was aborted, tell the writer thread to send a COMM_SYNC_DONE
                            eventReadingDone.countDown();
                            eventsRead = Integer.MIN_VALUE;
                        }
                        case ByteConstants.COMM_EVENT_DONE -> {
                            logger.info(
                                    SYNC_INFO.getMarker(),
                                    "{} reading events done, read {} events",
                                    connection.getDescription(),
                                    eventsRead);
                            syncMetrics.eventsReceived(startTime, eventsRead);
                            // we are done reading event, tell the writer thread to send a COMM_SYNC_DONE
                            eventReadingDone.countDown();
                        }
                            // while we are waiting for the peer to tell us they are done, they might send
                            // COMM_SYNC_ONGOING
                            // if they are still busy reading events
                        case ByteConstants.COMM_SYNC_ONGOING -> {
                            // peer is still reading events, waiting for them to finish
                            logger.debug(
                                    SYNC_INFO.getMarker(),
                                    "{} received COMM_SYNC_ONGOING",
                                    connection.getDescription());
                        }
                        case ByteConstants.COMM_SYNC_DONE -> {
                            logger.debug(
                                    SYNC_INFO.getMarker(), "{} received COMM_SYNC_DONE", connection.getDescription());
                            return eventsRead;
                        }
                        default -> throw new SyncException(
                                connection, String.format("while reading events, received unexpected byte %02x", next));
                    }
                }
            } finally {
                // in case an exception gets thrown, unblock the writer thread
                eventReadingDone.countDown();
            }
        };
    }

    /**
     * Checks if the time spent sending and receiving events exceeds maximum allowed. If it has, it throws an
     * exception.
     *
     * @param maxSyncTime the maximum amount of time to spend syncing with a peer, syncs that take longer than this will
     *                    be aborted
     * @param startTime   the time at which sending and receiving of events started
     * @throws SyncTimeoutException thrown if the time is exceeded
     */
    private static void checkEventExchangeTime(@NonNull final Duration maxSyncTime, final long startTime)
            throws SyncTimeoutException {
        final long syncTime = System.nanoTime() - startTime;
        if (syncTime > maxSyncTime.toNanos()) {
            throw new SyncTimeoutException(Duration.ofNanos(syncTime), maxSyncTime);
        }
    }

    /**
     * Get the latest self event in the shadowgraph.
     *
     * @param shadowGraph the shadow graph
     * @param selfId      the id of the node
     * @return the latest self event in the shadowgraph, or null if there is none
     */
    @Nullable
    private static ShadowEvent getLatestSelfEventInShadowgraph(
            @NonNull final ShadowGraph shadowGraph, @NonNull final NodeId selfId) {

        final List<ShadowEvent> tips = shadowGraph.getTips();
        for (final ShadowEvent tip : tips) {
            if (tip.getEvent().getCreatorId().equals(selfId)) {
                return tip;
            }
        }
        return null;
    }

    /**
     * Given a list of events we think the other node may not have, reduce that list to events that we think they do not
     * have and that are unlikely to end up being duplicate events.
     *
     * <p>
     * General principles:
     * <ul>
     * <li>Always send self events right away.</li>
     * <li>Don't send non-ancestors of self events unless we've known about that event for a long time.</li>
     * </ul>
     *
     * @param selfId                the id of this node
     * @param nonAncestorThreshold  for each event that is not a self event and is not an ancestor of a self event, the
     *                              amount of time the event must be known about before it is eligible to be sent
     * @param now                   the current time
     * @param eventsTheyNeed        the list of events we think they need
     * @param latestSelfEventTipset the tipset of the latest self event, or null if there is none
     * @return the events that should be actually sent, will be a subset of the eventsTheyNeed list
     */
    @NonNull
    public static List<EventImpl> filterLikelyDuplicates(
            @NonNull final NodeId selfId,
            @NonNull final Duration nonAncestorThreshold,
            @NonNull final Instant now,
            @NonNull final List<EventImpl> eventsTheyNeed,
            @Nullable final Tipset latestSelfEventTipset) {

        final List<EventImpl> filteredList = new ArrayList<>();

        for (final EventImpl event : eventsTheyNeed) {
            if (event.getCreatorId().equals(selfId)) {
                // Always send self events right away.
                filteredList.add(event);
                continue;
            }

            if (latestSelfEventTipset == null) {
                // Special case: we have no self events yet.
                filteredList.add(event);
                continue;
            }

            final long latestGenerationInAncestry = latestSelfEventTipset.getTipGenerationForNode(event.getCreatorId());

            if (latestGenerationInAncestry == Tipset.UNDEFINED) {
                // Special case: we don't have enough information to decide if this node is in our ancestry.
                filteredList.add(event);
                continue;
            }

            if (latestGenerationInAncestry >= event.getGeneration()) {
                // This event is an ancestor* of our latest self event.
                //
                // We want to answer the question: "Is this event an ancestor of my latest self event?"
                // The latest self event's tipset makes this easy to answer. A tipset is basically just
                // an array containing the latest generations of each event creator in our ancestry.
                // If we compare the generation of the event we're looking at to the generation of the
                // latest ancestor from the same creator, we can tell if this is an ancestor. If the event's
                // generation is less than or equal to the latest ancestor's generation, then it is an ancestor.
                // If it is greater than the latest ancestor's generation, then it is not an ancestor.
                //
                // *Note: there is an edge case where this breaks down a little. If this event's creator is branching,
                // then we may falsely conclude that this event is in our ancestry when it is not. But this is not
                // harmful. The purpose of this method is to reduce the number of events we send to the peer, and so
                // the worst that can happen is that we send a few extra events and get a slightly higher duplication
                // rate.
                filteredList.add(event);
                continue;
            }

            final Instant eventReceivedTime = event.getBaseEvent().getTimeReceived();
            final Duration timeKnown = Duration.between(eventReceivedTime, now);
            if (CompareTo.isGreaterThan(timeKnown, nonAncestorThreshold)) {
                // Always send ancestors of self events right away.
                // For all other events, only send it if we've known about it for long enough.
                filteredList.add(event);
            }

            // We won't send this event now, but we might do so at a future time if needed.
        }

        return filteredList;
    }

    /**
     * Returns a predicate that determines if a {@link ShadowEvent}'s generation is non-ancient for the peer and greater
     * than this node's minimum non-expired generation, and is not already known.
     *
     * @param knownShadows     the {@link ShadowEvent}s that are already known and should therefore be rejected by the
     *                         predicate
     * @param myGenerations    the generations of this node
     * @param theirGenerations the generations of the peer node
     * @return the predicate
     */
    public static Predicate<ShadowEvent> unknownNonAncient(
            final Collection<ShadowEvent> knownShadows,
            final GraphGenerations myGenerations,
            final GraphGenerations theirGenerations) {
        long minSearchGen =
                Math.max(myGenerations.getMinRoundGeneration(), theirGenerations.getMinGenerationNonAncient());
        return s -> s.getEvent().getGeneration() >= minSearchGen && !knownShadows.contains(s);
    }

    /**
     * Computes the number of creators that have more than one tip. If a single creator has more than two tips, this
     * method will only report once for each such creator. The execution time cost for this method is O(T + N) where T
     * is the number of tips including all forks and N is the number of network nodes. There is some memory overhead,
     * but it is fairly nominal in favor of the time complexity savings.
     *
     * @return the number of event creators that have more than one tip.
     */
    public static int computeMultiTipCount(Iterable<ShadowEvent> tips) {
        // The number of tips per creator encountered when iterating over the sending tips
        final Map<NodeId, Integer> tipCountByCreator = new HashMap<>();

        // Make a single O(N) where N is the number of tips including all forks. Typically, N will be equal to the
        // number of network nodes.
        for (final ShadowEvent tip : tips) {
            tipCountByCreator.compute(tip.getEvent().getCreatorId(), (k, v) -> (v != null) ? (v + 1) : 1);
        }

        // Walk the entrySet() which is O(N) where N is the number network nodes. This is still more efficient than a
        // O(N^2) loop.
        int creatorsWithForks = 0;
        for (final Map.Entry<NodeId, Integer> entry : tipCountByCreator.entrySet()) {
            // If the number of tips for a given creator is greater than 1 then we have a fork.
            // This map is broken down by creator ID already as the key so this is guaranteed to be a single increment
            // for each creator with a fork. Therefore, this holds to the method contract.
            if (entry.getValue() > 1) {
                creatorsWithForks++;
            }
        }

        return creatorsWithForks; // total number of unique creators with more than one tip
    }

    /**
     * @param sendList The list of events to sort.
     */
    static void sort(final List<EventImpl> sendList) {
        sendList.sort((EventImpl e1, EventImpl e2) -> (int) (e1.getGeneration() - e2.getGeneration()));
    }

    /**
     * For each tip they send us, determine if we have that event. For each tip, send true if we have the event and
     * false if we don't.
     *
     * @param theirTipShadows the tips they sent us
     * @return a list of booleans corresponding to their tips in the order they were sent. True if we have the event,
     * false if we don't
     */
    @NonNull
    static List<Boolean> getTheirTipsIHave(@NonNull final List<ShadowEvent> theirTipShadows) {
        final List<Boolean> myBooleans = new ArrayList<>(theirTipShadows.size());
        for (final ShadowEvent s : theirTipShadows) {
            myBooleans.add(s != null); // is this event is known to me
        }
        return myBooleans;
    }

    /**
     * For each tip sent to the peer, determine if they have that event. If they have it, add it to the list that is
     * returned.
     *
     * @param connection     the connection to use
     * @param myTips         the tips we sent them
     * @param myTipsTheyHave a list of booleans corresponding to our tips in the order they were sent. True if they have
     *                       the event, false if they don't
     * @return a list of tips that they have
     */
    @NonNull
    static List<ShadowEvent> getMyTipsTheyKnow(
            @NonNull final Connection connection,
            @NonNull final List<ShadowEvent> myTips,
            @NonNull final List<Boolean> myTipsTheyHave)
            throws SyncException {

        Objects.requireNonNull(connection);

        if (myTipsTheyHave.size() != myTips.size()) {
            throw new SyncException(
                    connection,
                    String.format(
                            "peer booleans list is wrong size. Expected: %d Actual: %d,",
                            myTips.size(), myTipsTheyHave.size()));
        }
        final List<ShadowEvent> knownTips = new ArrayList<>();
        // process their booleans
        for (int i = 0; i < myTipsTheyHave.size(); i++) {
            if (Boolean.TRUE.equals(myTipsTheyHave.get(i))) {
                knownTips.add(myTips.get(i));
            }
        }

        return knownTips;
    }
}

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

import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.gossip.SyncException;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.network.ByteConstants;
import com.swirlds.platform.network.Connection;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Helper methods for performing sync gossip.
 */
public final class SyncComms {
    /**
     * send a {@link ByteConstants#COMM_SYNC_ONGOING} every this many milliseconds after we are done writing events,
     * until we are done reading events
     */
    private static final int SYNC_ONGOING_SEND_EVERY_MS = 500;
    /**
     * The maximum time we will allow sending and receiving events. If not done within this time limit, we will abort
     * the sync.
     */
    private static final Duration SEND_AND_RECEIVE_EVENTS_MAX_TIME = Duration.ofMinutes(1);


    // Prevent instantiations of this static utility class
    private SyncComms() {}

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
            return booleans;
        };
    }

    /**
     * Send the events the peer needs. The complementary function to
     * {@link #readEventsINeed(Connection, Consumer, SyncMetrics, CountDownLatch, IntakeEventCounter)}.
     *
     * @param connection       the connection to write to
     * @param events           the events to write
     * @param eventReadingDone used to know when the writing thread is done
     * @param writeAborted     set to true if writing is aborted
     * @return A {@link Callable} that executes this part of the sync
     */
    public static Callable<Void> sendEventsTheyNeed(
            final Connection connection,
            final List<EventImpl> events,
            final CountDownLatch eventReadingDone,
            final AtomicBoolean writeAborted) {
        return () -> {
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
            if (!writeAborted.get()) {
                connection.getDos().writeByte(ByteConstants.COMM_EVENT_DONE);
            }
            connection.getDos().flush();

            // if we are still reading events, send keepalive messages
            while (!eventReadingDone.await(SYNC_ONGOING_SEND_EVERY_MS, TimeUnit.MILLISECONDS)) {
                connection.getDos().writeByte(ByteConstants.COMM_SYNC_ONGOING);
                connection.getDos().flush();
            }

            // we have now finished reading and writing all the events of a sync. the remote node may not have
            // finished reading and processing all the events this node has sent. so we write a byte to tell the remote
            // node we have finished, and the reader will wait for it to send us the same byte.
            connection.getDos().writeByte(ByteConstants.COMM_SYNC_DONE);
            connection.getDos().flush();

            // (ignored)
            return null;
        };
    }

    /**
     * Read events from the peer that I need. The complementary function to
     * {@link #sendEventsTheyNeed(Connection, List, CountDownLatch, AtomicBoolean)}.
     *
     * @param connection         the connection to read from
     * @param eventHandler       the consumer of received events
     * @param syncMetrics        tracks event reading metrics
     * @param eventReadingDone   used to notify the writing thread that reading is done
     * @param intakeEventCounter keeps track of the number of events in the intake pipeline from each peer
     * @return A {@link Callable} that executes this part of the sync
     */
    public static Callable<Integer> readEventsINeed(
            final Connection connection,
            final Consumer<GossipEvent> eventHandler,
            final SyncMetrics syncMetrics,
            final CountDownLatch eventReadingDone,
            @NonNull final IntakeEventCounter intakeEventCounter) {

        return () -> {
            int eventsRead = 0;
            try {
                final long startTime = System.nanoTime();
                while (true) {
                    // readByte() will throw a timeout exception if the socket timeout is exceeded
                    final byte next = connection.getDis().readByte();
                    // if the peer continuously sends COMM_SYNC_ONGOING, or sends the data really slowly,
                    // this timeout will be triggered
                    checkEventExchangeTime(startTime);
                    switch (next) {
                        case ByteConstants.COMM_EVENT_NEXT -> {
                            final GossipEvent gossipEvent = connection.getDis().readEventData();

                            gossipEvent.setSenderId(connection.getOtherId());
                            intakeEventCounter.eventEnteredIntakePipeline(connection.getOtherId());

                            eventHandler.accept(gossipEvent);
                            eventsRead++;
                        }
                        case ByteConstants.COMM_EVENT_ABORT -> {
                            // event reading was aborted, tell the writer thread to send a COMM_SYNC_DONE
                            eventReadingDone.countDown();
                            eventsRead = Integer.MIN_VALUE;
                        }
                        case ByteConstants.COMM_EVENT_DONE -> {
                            syncMetrics.eventsReceived(startTime, eventsRead);
                            // we are done reading event, tell the writer thread to send a COMM_SYNC_DONE
                            eventReadingDone.countDown();
                        }
                        // while we are waiting for the peer to tell us they are done, they might send
                        // COMM_SYNC_ONGOING
                        // if they are still busy reading events
                        case ByteConstants.COMM_SYNC_ONGOING -> {
                            // peer is still reading events, waiting for them to finish
                        }
                        case ByteConstants.COMM_SYNC_DONE -> {
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
     * @param startTime the time at which phase 3 started
     * @throws SyncTimeoutException thrown if the time is exceeded
     */
    private static void checkEventExchangeTime(final long startTime) throws SyncTimeoutException {
        final long phase3Nanos = System.nanoTime() - startTime;
        if (phase3Nanos > SEND_AND_RECEIVE_EVENTS_MAX_TIME.toNanos()) {
            throw new SyncTimeoutException(Duration.ofNanos(phase3Nanos), SEND_AND_RECEIVE_EVENTS_MAX_TIME);
        }
    }
}

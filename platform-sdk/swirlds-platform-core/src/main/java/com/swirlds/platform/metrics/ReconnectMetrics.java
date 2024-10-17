/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.metrics;

import static com.swirlds.metrics.api.FloatFormats.FORMAT_10_0;
import static com.swirlds.metrics.api.Metrics.PLATFORM_CATEGORY;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.swirlds.common.metrics.extensions.CountPerSecond;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.units.TimeUnit;
import com.swirlds.metrics.api.Counter;
import com.swirlds.metrics.api.LongAccumulator;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Collection of metrics related to reconnects
 */
public class ReconnectMetrics {

    public static final String RECONNECT_CATEGORY = "Reconnect";

    private static final Counter.Config SENDER_START_TIMES_CONFIG = new Counter.Config(
                    RECONNECT_CATEGORY, "startsReconnectAsSender")
            .withDescription("number of times a node starts reconnect as a sender");
    private final Counter senderStartTimes;

    private static final Counter.Config RECEIVER_START_TIMES_CONFIG = new Counter.Config(
                    RECONNECT_CATEGORY, "startsReconnectAsReceiver")
            .withDescription("number of times a node starts reconnect as a receiver");
    private final Counter receiverStartTimes;

    private static final Counter.Config SENDER_END_TIMES_CONFIG = new Counter.Config(
                    RECONNECT_CATEGORY, "endsReconnectAsSender")
            .withDescription("number of times a node ends reconnect as a sender");
    private final Counter senderEndTimes;

    private static final Counter.Config RECEIVER_END_TIMES_CONFIG = new Counter.Config(
                    RECONNECT_CATEGORY, "endsReconnectAsReceiver")
            .withDescription("number of times a node ends reconnect as a receiver");
    private final Counter receiverEndTimes;
    /**
     * Number of reconnect rejections per second per peer in the address book.
     */
    private final Map<NodeId, CountPerSecond> rejectionFrequency = new HashMap<>();

    private static final LongAccumulator.Config SENDER_DURATION_CONFIG = new LongAccumulator.Config(
                    RECONNECT_CATEGORY, "senderReconnectDurationSeconds")
            .withInitialValue(0)
            .withAccumulator(Long::sum)
            .withDescription("duration of reconnect as a sender")
            .withUnit(TimeUnit.UNIT_SECONDS.getAbbreviation());
    private final LongAccumulator senderReconnectDurationSeconds;

    private static final LongAccumulator.Config RECEIVER_DURATION_CONFIG = new LongAccumulator.Config(
                    RECONNECT_CATEGORY, "receiverReconnectDurationSeconds")
            .withInitialValue(0)
            .withAccumulator(Long::sum)
            .withDescription("duration of reconnect as a receiver")
            .withUnit(TimeUnit.UNIT_SECONDS.getAbbreviation());
    private final LongAccumulator receiverReconnectDurationSeconds;

    // Assuming that reconnect is a "singleton" operation (a single node cannot teach multiple learners
    // simultaneously, and a single node cannot learn from multiple teachers at once), we maintain
    // state variables here to measure the duration of reconnect operations.
    // A caller of incrementStart/End methods is responsible for synchronizing access to these.
    private long senderStartNanos = 0L;
    private long receiverStartNanos = 0L;

    /**
     * Constructor of {@code ReconnectMetrics}
     *
     * @param metrics
     * 		reference to the metrics-system
     * @throws IllegalArgumentException if {@code metrics} is {@code null}
     */
    public ReconnectMetrics(@NonNull final Metrics metrics, @NonNull final Roster roster) {
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(roster, "roster");
        senderStartTimes = metrics.getOrCreate(SENDER_START_TIMES_CONFIG);
        receiverStartTimes = metrics.getOrCreate(RECEIVER_START_TIMES_CONFIG);
        senderEndTimes = metrics.getOrCreate(SENDER_END_TIMES_CONFIG);
        receiverEndTimes = metrics.getOrCreate(RECEIVER_END_TIMES_CONFIG);
        senderReconnectDurationSeconds = metrics.getOrCreate(SENDER_DURATION_CONFIG);
        receiverReconnectDurationSeconds = metrics.getOrCreate(RECEIVER_DURATION_CONFIG);

        for (final RosterEntry entry : roster.rosterEntries()) {
            final NodeId nodeId = NodeId.of(entry.nodeId());
            rejectionFrequency.put(
                    nodeId,
                    new CountPerSecond(
                            metrics,
                            new CountPerSecond.Config(
                                            PLATFORM_CATEGORY,
                                            String.format("reconnectRejections_per_sec_%02d", nodeId.id()))
                                    .withDescription(String.format(
                                            "number of reconnections rejected per second from node %02d", nodeId.id()))
                                    .withUnit("rejectionsPerSec")
                                    .withFormat(FORMAT_10_0)));
        }
    }

    public void incrementSenderStartTimes() {
        senderStartNanos = System.nanoTime();
        senderStartTimes.increment();
    }

    public void incrementReceiverStartTimes() {
        receiverStartNanos = System.nanoTime();
        receiverStartTimes.increment();
    }

    public void incrementSenderEndTimes() {
        senderEndTimes.increment();
        senderReconnectDurationSeconds.update(
                Duration.ofNanos(System.nanoTime() - senderStartNanos).toSeconds());
    }

    public void incrementReceiverEndTimes() {
        receiverEndTimes.increment();
        receiverReconnectDurationSeconds.update(
                Duration.ofNanos(System.nanoTime() - receiverStartNanos).toSeconds());
    }

    /**
     * Records the occurrence of rejecting a reconnect attempt from a peer.
     *
     * @param nodeId the peer being rejected.
     */
    public void recordReconnectRejection(@NonNull final NodeId nodeId) {
        Objects.requireNonNull(nodeId);
        if (rejectionFrequency.containsKey(nodeId)) {
            rejectionFrequency.get(nodeId).count();
        }
    }
}

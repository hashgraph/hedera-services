/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.network;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_10_0;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_16_2;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_4_2;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_7_0;
import static com.swirlds.common.metrics.Metrics.INTERNAL_CATEGORY;
import static com.swirlds.common.metrics.Metrics.PLATFORM_CATEGORY;

import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.Connection;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.IntStream;

/**
 * Collection of metrics related to the network
 */
public class NetworkMetrics {

    private static final String PING_CATEGORY = "ping";
    private static final String BPSS_CATEGORY = "bpss";

    private static final RunningAverageMetric.Config AVG_PING_CONFIG = new RunningAverageMetric.Config(
                    PLATFORM_CATEGORY, "ping")
            .withDescription("average time for a round trip message between 2 computers (in milliseconds)")
            .withFormat(FORMAT_7_0);
    private static final SpeedometerMetric.Config BYTES_PER_SECOND_SENT_CONFIG = new SpeedometerMetric.Config(
                    INTERNAL_CATEGORY, "bytes/sec_sent")
            .withDescription("number of bytes sent per second over the network (total for this member)")
            .withFormat(FORMAT_16_2);
    private static final RunningAverageMetric.Config AVG_CONNS_CREATED_CONFIG = new RunningAverageMetric.Config(
                    PLATFORM_CATEGORY, "conns")
            .withDescription("number of times a TLS connections was created")
            .withFormat(FORMAT_10_0)
            .withHalfLife(0.0);

    private final NodeId selfId;
    /** all connections of this platform */
    private final Queue<Connection> connections = new ConcurrentLinkedQueue<>();
    /** total number of connections created so far (both caller and listener) */
    private final LongAdder connsCreated = new LongAdder();

    private final List<RunningAverageMetric> avgPingMilliseconds;
    private final List<SpeedometerMetric> avgBytePerSecSent;
    private final RunningAverageMetric avgPing;
    private final SpeedometerMetric bytesPerSecondSent;
    private final RunningAverageMetric avgConnsCreated;

    /**
     * Constructor of {@code NetworkMetrics}
     *
     * @param metrics
     * 		a reference to the metrics-system
     * @param selfId
     * 		this node's id
     * @param addressBookSize
     * 		the number of nodes in the address book
     * @throws IllegalArgumentException
     * 		if {@code platform} is {@code null}
     */
    public NetworkMetrics(final Metrics metrics, final NodeId selfId, final int addressBookSize) {
        CommonUtils.throwArgNull(selfId, "selfId");
        this.selfId = selfId;

        avgPingMilliseconds = IntStream.range(0, addressBookSize)
                .mapToObj(i -> metrics.getOrCreate(new RunningAverageMetric.Config(
                                PING_CATEGORY, String.format("ping_ms_%02d", i))
                        .withDescription(String.format("milliseconds to send node %02d a byte and receive a reply", i))
                        .withFormat(FORMAT_4_2)))
                .toList();
        avgBytePerSecSent = IntStream.range(0, addressBookSize)
                .mapToObj(i -> metrics.getOrCreate(
                        new SpeedometerMetric.Config(BPSS_CATEGORY, String.format("bytes/sec_sent_%02d", i))
                                .withDescription(String.format("bytes per second sent to node %02d", i))
                                .withFormat(FORMAT_16_2)))
                .toList();
        avgPing = metrics.getOrCreate(AVG_PING_CONFIG);
        bytesPerSecondSent = metrics.getOrCreate(BYTES_PER_SECOND_SENT_CONFIG);
        avgConnsCreated = metrics.getOrCreate(AVG_CONNS_CREATED_CONFIG);
    }

    /**
     * Notifies the stats that a new connection has been established
     *
     * @param connection
     * 		a new connection
     */
    public void connectionEstablished(final Connection connection) {
        if (connection == null) {
            return;
        }
        connections.add(connection);
        connsCreated.increment(); // count new connections
    }

    /**
     * Record the ping time to this particular node
     *
     * @param node
     * 		the node to which the latency is referring to
     * @param pingNanos
     * 		the ping time, in nanoseconds
     */
    public void recordPingTime(final NodeId node, final long pingNanos) {
        avgPingMilliseconds.get(node.getIdAsInt()).update((pingNanos) / 1_000_000.0);
    }

    /**
     * Updates the metrics.
     * <p>
     * This method will be called by {@link Metrics} and is not intended to be called from anywhere else.
     */
    public void update() {
        // calculate the value for otherStatPing (the average of all, not including self)
        double sum = 0;
        final double[] times = getPingMilliseconds(); // times are in seconds
        for (final double time : times) {
            sum += time;
        }
        // don't average in the times[selfId]==0, so subtract 1 from the length
        final double pingValue = sum / (times.length - 1); // pingValue is in milliseconds

        avgPing.update(pingValue);

        long totalBytesSent = 0;
        for (final Iterator<Connection> iterator = connections.iterator(); iterator.hasNext(); ) {
            final Connection conn = iterator.next();
            if (conn != null) {
                final long bytesSent = conn.getDos().getConnectionByteCounter().getAndResetCount();
                totalBytesSent += bytesSent;
                final int otherId = conn.getOtherId().getIdAsInt();
                if (otherId < avgBytePerSecSent.size() && avgBytePerSecSent.get(otherId) != null) {
                    avgBytePerSecSent.get(otherId).update(bytesSent);
                }
                if (!conn.connected()) {
                    iterator.remove();
                }
            }
        }
        bytesPerSecondSent.update(totalBytesSent);
        avgConnsCreated.update(connsCreated.sum());
    }

    /**
     * Returns the time for a round-trip message to each member (in milliseconds).
     * <p>
     * This is an exponentially-weighted average of recent ping times.
     *
     * @return the average times, for each member, in milliseconds
     */
    public double[] getPingMilliseconds() {
        final double[] times = new double[avgPingMilliseconds.size()];
        for (int i = 0; i < times.length; i++) {
            times[i] = avgPingMilliseconds.get(i).get();
        }
        times[selfId.getIdAsInt()] = 0;
        return times;
    }

    public List<RunningAverageMetric> getAvgPingMilliseconds() {
        return avgPingMilliseconds;
    }

    public List<SpeedometerMetric> getAvgBytePerSecSent() {
        return avgBytePerSecSent;
    }
}

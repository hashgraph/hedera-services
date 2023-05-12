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

package com.swirlds.platform.test.chatter.simulator;

import static com.swirlds.common.formatting.StringFormattingUtils.commaSeparatedNumber;
import static com.swirlds.common.units.DataUnit.UNIT_BYTES;
import static com.swirlds.common.utility.Units.NANOSECONDS_TO_SECONDS;
import static com.swirlds.platform.test.chatter.simulator.GossipSimulationUtils.printHeader;
import static com.swirlds.platform.test.chatter.simulator.GossipSimulationUtils.roundDecimal;

import com.swirlds.common.formatting.TextTable;
import com.swirlds.common.formatting.UnitFormatter;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates statistics for a {@link SimulatedNetwork}.
 */
public class SimulatedNetworkStatistics {

    /**
     * Total number of nodes in the simulation.
     */
    private final long nodeCount;

    /**
     * The duration of a single time step.
     */
    private final Duration timeStep;

    /**
     * The outgoing bandwidth of each node in bytes/timestep.
     */
    private final Map<Long, Long> outgoingBandwidth;

    /**
     * The incoming bandwidth of each node in bytes/timestep.
     */
    private final Map<Long, Long> incomingBandwidth;

    /**
     * Records the number of messages sent by each node.
     */
    private final Map<Long /* node ID */, Long /* num messages */> messagesSentByEachNode = new HashMap<>();

    /**
     * Records the number of bytes sent by each node.
     */
    private final Map<Long /* node ID */, Long /* bytes */> bytesSentByEachNode = new HashMap<>();

    /**
     * Records the number of messages received by each node. Does not include dropped messages.
     */
    private final Map<Long /* node ID */, Long /* num messages */> messagesReceivedByEachNode = new HashMap<>();

    /**
     * Records the number of bytes received by each node. Does not include dropped messages.
     */
    private final Map<Long /* node ID */, Long /* bytes */> bytesReceivedByEachNode = new HashMap<>();

    /**
     * Records the number of messages sent, sorted by type of message.
     */
    private final Map<Class<?> /* message type */, Long /* num messages */> messagesSentByType = new HashMap<>();

    /**
     * Records the number of bytes sent, sorted by type of message.
     */
    private final Map<Class<?> /* message type */, Long /* bytes */> bytesSentByType = new HashMap<>();

    /**
     * The number of messages received and not dropped, sorted by message type.
     */
    private final Map<Class<?> /* message type */, Long /* num messages */> messagesReceivedByType = new HashMap<>();

    /**
     * Number of bytes received and not dropped, sorted by message type.
     */
    private final Map<Class<?> /* message type */, Long /* bytes */> bytesReceivedByType = new HashMap<>();

    /**
     * Number of messages dropped, sorted by message type.
     */
    private final Map<Class<?> /* message type */, Long /* num messages */> messagesDroppedByType = new HashMap<>();

    /**
     * Number of bytes dropped, sorted by message type.
     */
    private final Map<Class<?> /* message type */, Long /* bytes */> bytesDroppedByType = new HashMap<>();

    /**
     * Sum of all available incoming bandwidth over the course of the simulation.
     */
    private final Map<Long /* node ID */, Long /* bytes */> incomingBandwidthCapacity = new HashMap<>();

    /**
     * Sum of all incoming bandwidth used over the course of the simulation.
     */
    private final Map<Long /* node ID */, Long /* bytes */> outgoingBandwidthCapacity = new HashMap<>();

    /**
     * The total amount of time that has been simulated.
     */
    private Duration totalSimulatedTime = Duration.ZERO;

    public SimulatedNetworkStatistics(
            final int nodeCount,
            final Duration timeStep,
            final Map<Long, Long> incomingBandwidth,
            final Map<Long, Long> outgoingBandwidth) {

        this.nodeCount = nodeCount;
        this.timeStep = timeStep;
        this.outgoingBandwidth = outgoingBandwidth;
        this.incomingBandwidth = incomingBandwidth;
    }

    /**
     * This should be called for each time step that is simulated.
     */
    public void simulateOneStep() {

        totalSimulatedTime = totalSimulatedTime.plus(timeStep);

        for (long nodeId = 0; nodeId < nodeCount; nodeId++) {
            final long previousIncomingBandwidthCapacity = incomingBandwidthCapacity.getOrDefault(nodeId, 0L);
            incomingBandwidthCapacity.put(nodeId, previousIncomingBandwidthCapacity + incomingBandwidth.get(nodeId));

            final long previousOutgoingBandwidthCapacity = outgoingBandwidthCapacity.getOrDefault(nodeId, 0L);
            outgoingBandwidthCapacity.put(nodeId, previousOutgoingBandwidthCapacity + outgoingBandwidth.get(nodeId));
        }
    }

    /**
     * Capture statistics for a sent message.
     *
     * @param message
     * 		the message that was sent
     */
    public void captureSendStatistics(final SimulatedMessage message) {
        final long source = message.getSource();
        final Class<?> type = message.getPayload().getClass();

        final long originalSendCount = messagesSentByEachNode.getOrDefault(source, 0L);
        messagesSentByEachNode.put(source, originalSendCount + 1);

        final long originalSendBytes = bytesSentByEachNode.getOrDefault(source, 0L);
        bytesSentByEachNode.put(source, originalSendBytes + message.getSize());

        final long originalSendCountByType = messagesSentByType.getOrDefault(type, 0L);
        messagesSentByType.put(type, originalSendCountByType + 1);

        final long originalSendBytesByType = bytesSentByType.getOrDefault(type, 0L);
        bytesSentByType.put(type, originalSendBytesByType + message.getSize());
    }

    /**
     * Capture statistics for a received message.
     *
     * @param message
     * 		the message that was received
     */
    public void captureReceiveStatistics(final SimulatedMessage message) {
        final long destination = message.getDestination();
        final Class<?> type = message.getPayload().getClass();

        final long originalReceivedCount = messagesReceivedByEachNode.getOrDefault(destination, 0L);
        messagesReceivedByEachNode.put(destination, originalReceivedCount + 1);

        final long originalReceivedBytes = bytesReceivedByEachNode.getOrDefault(destination, 0L);
        bytesReceivedByEachNode.put(destination, originalReceivedBytes + message.getSize());

        final long originalReceivedCountByType = messagesReceivedByType.getOrDefault(type, 0L);
        messagesReceivedByType.put(type, originalReceivedCountByType + 1);

        final long originalReceivedBytesByType = bytesReceivedByType.getOrDefault(type, 0L);
        bytesReceivedByType.put(type, originalReceivedBytesByType + message.getSize());
    }

    private void printNetworkSummary() {
        final TextTable table =
                new TextTable().setTitle("NETWORK SUMMARY").addRow("", "", "Utilization", "Fraction", "Rate");

        final double totalSimulationSeconds = totalSimulatedTime.toNanos() * NANOSECONDS_TO_SECONDS;

        long totalMessagesSent = 0;
        for (long nodeId = 0; nodeId < nodeCount; nodeId++) {
            totalMessagesSent += messagesSentByEachNode.getOrDefault(nodeId, 0L);
        }
        final int messagesSentPerSecond = (int) (totalMessagesSent / totalSimulationSeconds);
        table.addRow(
                "Total messages sent",
                commaSeparatedNumber(totalMessagesSent) + " messages",
                "",
                "",
                commaSeparatedNumber(messagesSentPerSecond) + " messages/second");

        long totalBytesSent = 0;
        long totalSendingCapacity = 0;
        for (long nodeId = 0; nodeId < nodeCount; nodeId++) {
            totalBytesSent += bytesSentByEachNode.getOrDefault(nodeId, 0L);
            totalSendingCapacity += outgoingBandwidthCapacity.getOrDefault(nodeId, 0L);
        }
        final double sentUtilization = ((double) totalBytesSent) / totalSendingCapacity * 100;
        final long bytesSentPerSecond = (long) (((double) totalBytesSent) / totalSimulationSeconds);

        table.addRow(
                "Total bytes sent",
                new UnitFormatter(totalBytesSent, UNIT_BYTES).render(),
                roundDecimal(sentUtilization, 2) + " %",
                "",
                new UnitFormatter(bytesSentPerSecond, UNIT_BYTES).render() + "/second");

        long totalMessagesReceived = 0;
        for (long nodeId = 0; nodeId < nodeCount; nodeId++) {
            totalMessagesReceived += messagesReceivedByEachNode.getOrDefault(nodeId, 0L);
        }
        final double percentReceived = ((double) totalMessagesReceived) / totalMessagesSent * 100;
        final int messagesReceivedPerSecond = (int) (totalMessagesReceived / totalSimulationSeconds);
        table.addRow(
                "Total messages received",
                commaSeparatedNumber(totalMessagesReceived) + " messages",
                "",
                roundDecimal(percentReceived, 2) + " % of messages sent",
                commaSeparatedNumber(messagesReceivedPerSecond) + " messages/second");

        long totalBytesReceived = 0;
        long totalReceiveCapacity = 0;
        for (long nodeId = 0; nodeId < nodeCount; nodeId++) {
            totalBytesReceived += bytesReceivedByEachNode.getOrDefault(nodeId, 0L);
            totalReceiveCapacity += incomingBandwidthCapacity.get(nodeId);
        }
        final double percentBytesReceived = ((double) totalBytesReceived) / totalBytesSent * 100;
        final int bytesReceivedPerSecond = (int) (totalBytesReceived / totalSimulationSeconds);
        final double receivedUtilization = ((double) totalBytesReceived) / totalReceiveCapacity * 100;
        table.addRow(
                "Total bytes received",
                new UnitFormatter(totalBytesReceived, UNIT_BYTES).render(),
                roundDecimal(receivedUtilization, 2) + " %",
                roundDecimal(percentBytesReceived, 2) + " % of bytes sent",
                new UnitFormatter(bytesReceivedPerSecond, UNIT_BYTES).render() + "/second");

        System.out.println(table);
    }

    private void printMessagesSentByEachNode() {
        final TextTable table = new TextTable()
                .setTitle("MESSAGES SENT BY EACH NODE")
                .addRow("Node ID", "Messages Sent", "Fraction Of Whole", "Rate");

        final double totalSimulationSeconds = totalSimulatedTime.toNanos() * NANOSECONDS_TO_SECONDS;

        long totalMessagesSent = 0;
        for (long nodeId = 0; nodeId < nodeCount; nodeId++) {
            totalMessagesSent += messagesSentByEachNode.getOrDefault(nodeId, 0L);
        }

        for (long nodeId = 0; nodeId < nodeCount; nodeId++) {

            final long messagesSentByNode = messagesSentByEachNode.getOrDefault(nodeId, 0L);
            final double fractionOfWhole = ((double) messagesSentByNode) / totalMessagesSent * 100;
            final long messagesPerSecond = (long) (messagesSentByNode / totalSimulationSeconds);

            table.addRow(
                    nodeId,
                    commaSeparatedNumber(messagesSentByNode) + " messages",
                    roundDecimal(fractionOfWhole, 2) + " %",
                    commaSeparatedNumber(messagesPerSecond) + " messages/second");
        }

        System.out.println(table);
    }

    private void printBytesSentByEachNode() {
        final TextTable table = new TextTable()
                .setTitle("BYTES SENT BY EACH NODE")
                .addRow("Node ID", "Data Sent", "Fraction Of Whole", "Utilization", "Rate");

        final double totalSimulationSeconds = totalSimulatedTime.toNanos() * NANOSECONDS_TO_SECONDS;

        long totalBytesSent = 0;
        for (long nodeId = 0; nodeId < nodeCount; nodeId++) {
            totalBytesSent += bytesSentByEachNode.getOrDefault(nodeId, 0L);
        }

        for (long nodeId = 0; nodeId < nodeCount; nodeId++) {
            final long bytesSent = bytesSentByEachNode.getOrDefault(nodeId, 0L);
            final double fractionOfWhole = ((double) bytesSent / totalBytesSent) * 100;
            final double utilization = ((double) bytesSent) / outgoingBandwidthCapacity.get(nodeId) * 100;
            final long bytesPerSecond = (long) (bytesSent / totalSimulationSeconds);

            table.addRow(
                    nodeId,
                    new UnitFormatter(bytesSent, UNIT_BYTES).render(),
                    roundDecimal(fractionOfWhole, 2) + " %",
                    roundDecimal(utilization, 2) + " %",
                    new UnitFormatter(bytesPerSecond, UNIT_BYTES).render() + "/s");
        }

        System.out.println(table);
    }

    private void printMessagesSentByType() {
        final TextTable table = new TextTable()
                .setTitle("MESSAGES SENT BY TYPE")
                .addRow("Message Type", "Number Sent", "Fraction Of Whole", "Rate");

        final double totalSimulationSeconds = totalSimulatedTime.toNanos() * NANOSECONDS_TO_SECONDS;

        long totalMessagesSent = 0;
        for (long nodeId = 0; nodeId < nodeCount; nodeId++) {
            totalMessagesSent += messagesSentByEachNode.getOrDefault(nodeId, 0L);
        }

        for (final Class<?> type : messagesSentByType.keySet()) {

            final long messagesSent = messagesSentByType.getOrDefault(type, 0L);
            final double fraction = ((double) messagesSent) / totalMessagesSent * 100;
            final long rate = (long) (messagesSent / totalSimulationSeconds);

            table.addRow(
                    type.getSimpleName(),
                    commaSeparatedNumber(messagesSent),
                    roundDecimal(fraction, 2) + " %",
                    commaSeparatedNumber(rate) + " messages/second");
        }

        System.out.println(table);
    }

    private void printBytesSentByType() {
        final TextTable table = new TextTable()
                .setTitle("BYTES SENT BY TYPE")
                .addRow("Message Type", "Data Sent", "Fraction Of Whole", "Rate");

        final double totalSimulationSeconds = totalSimulatedTime.toNanos() * NANOSECONDS_TO_SECONDS;

        long totalBytesSent = 0;
        for (long nodeId = 0; nodeId < nodeCount; nodeId++) {
            totalBytesSent += bytesSentByEachNode.getOrDefault(nodeId, 0L);
        }

        for (final Class<?> type : bytesSentByType.keySet()) {
            final long bytesSent = bytesSentByType.getOrDefault(type, 0L);
            final double fraction = ((double) bytesSent) / totalBytesSent * 100;
            final long rate = (long) (bytesSent / totalSimulationSeconds);

            table.addRow(
                    type.getSimpleName(),
                    new UnitFormatter(bytesSent, UNIT_BYTES).render(),
                    roundDecimal(fraction, 2) + " %",
                    new UnitFormatter(rate, UNIT_BYTES).render() + "/second");
        }

        System.out.println(table);
    }

    private void printMessagesReceivedByEachNode() {
        final TextTable table = new TextTable()
                .setTitle("MESSAGES RECEIVED BY EACH NODE")
                .addRow("Node ID", "Messages Received", "Fraction Of Whole", "Rate");

        final double totalSimulationSeconds = totalSimulatedTime.toNanos() * NANOSECONDS_TO_SECONDS;

        long totalMessagesReceived = 0;
        for (long nodeId = 0; nodeId < nodeCount; nodeId++) {
            totalMessagesReceived += messagesReceivedByEachNode.getOrDefault(nodeId, 0L);
        }

        for (long nodeId = 0; nodeId < nodeCount; nodeId++) {

            final long messagesReceived = messagesReceivedByEachNode.getOrDefault(nodeId, 0L);
            final double fraction =
                    totalMessagesReceived == 0 ? 0 : ((double) messagesReceived) / totalMessagesReceived * 100;
            final long rate = (long) (messagesReceived / totalSimulationSeconds);

            table.addRow(
                    nodeId,
                    commaSeparatedNumber(messagesReceived) + " messages",
                    roundDecimal(fraction, 2) + " %",
                    commaSeparatedNumber(rate) + " messages/second");
        }

        System.out.println(table);
    }

    private void printBytesReceivedByEachNode() {
        final TextTable table = new TextTable()
                .setTitle("BYTES RECEIVED BY EACH NODE")
                .addRow("Node ID", "Data Received", "Fraction Of Whole", "Utilization", "Rate");

        final double totalSimulationSeconds = totalSimulatedTime.toNanos() * NANOSECONDS_TO_SECONDS;

        long totalBytesReceived = 0;
        for (long nodeId = 0; nodeId < nodeCount; nodeId++) {
            totalBytesReceived += bytesReceivedByEachNode.getOrDefault(nodeId, 0L);
        }

        for (long nodeId = 0; nodeId < nodeCount; nodeId++) {

            final long bytesReceived = bytesReceivedByEachNode.getOrDefault(nodeId, 0L);
            final double fraction = totalBytesReceived == 0 ? 0 : ((double) bytesReceived) / totalBytesReceived * 100;
            final double utilization = ((double) bytesReceived) / incomingBandwidthCapacity.get(nodeId) * 100;
            final long rate = (long) (bytesReceived / totalSimulationSeconds);

            table.addRow(
                    nodeId,
                    new UnitFormatter(bytesReceived, UNIT_BYTES).render(),
                    roundDecimal(fraction, 2) + " %",
                    roundDecimal(utilization, 2) + " %",
                    new UnitFormatter(rate, UNIT_BYTES).render() + "/second");
        }

        System.out.println(table);
    }

    private void printMessagesReceivedByType() {
        final TextTable table = new TextTable()
                .setTitle("MESSAGES RECEIVED BY TYPE")
                .addRow("Message Type", "Number Received", "Fraction Of Whole", "Rate");

        final double totalSimulationSeconds = totalSimulatedTime.toNanos() * NANOSECONDS_TO_SECONDS;

        long totalMessagesReceived = 0;
        for (long nodeId = 0; nodeId < nodeCount; nodeId++) {
            totalMessagesReceived += messagesReceivedByEachNode.getOrDefault(nodeId, 0L);
        }

        for (final Class<?> type : messagesReceivedByType.keySet()) {

            final long messagesReceived = messagesReceivedByType.getOrDefault(type, 0L);
            final double fraction = ((double) messagesReceived) / totalMessagesReceived * 100;
            final long rate = (long) (messagesReceived / totalSimulationSeconds);

            table.addRow(
                    type.getSimpleName(),
                    commaSeparatedNumber(messagesReceived) + " messages",
                    roundDecimal(fraction, 2) + " %",
                    commaSeparatedNumber(rate) + " messages/second");
        }

        System.out.println(table);
    }

    private void printBytesReceivedByType() {
        final TextTable table = new TextTable()
                .setTitle("BYTES RECEIVED BY TYPE")
                .addRow("Message Type", "Data Received", "Fraction Of Whole", "Rate");

        final double totalSimulationSeconds = totalSimulatedTime.toNanos() * NANOSECONDS_TO_SECONDS;

        long totalBytesReceived = 0;
        for (long nodeId = 0; nodeId < nodeCount; nodeId++) {
            totalBytesReceived += bytesReceivedByEachNode.getOrDefault(nodeId, 0L);
        }

        for (final Class<?> type : bytesReceivedByType.keySet()) {

            final long bytesReceived = bytesReceivedByType.getOrDefault(type, 0L);
            final double fraction = ((double) bytesReceived) / totalBytesReceived * 100;
            final long rate = (long) (bytesReceived / totalSimulationSeconds);

            table.addRow(
                    type.getSimpleName(),
                    new UnitFormatter(bytesReceived, UNIT_BYTES).render(),
                    roundDecimal(fraction, 2) + " %",
                    new UnitFormatter(rate, UNIT_BYTES).render() + "/second");
        }

        System.out.println(table);
    }

    /**
     * Print statistics from the previous run.
     */
    public void printStatistics() {
        printHeader("Network Statistics");
        printNetworkSummary();
        printMessagesSentByEachNode();
        printBytesSentByEachNode();
        printMessagesSentByType();
        printBytesSentByType();
        printMessagesReceivedByEachNode();
        printBytesReceivedByEachNode();
        printMessagesReceivedByType();
        printBytesReceivedByType();
    }
}

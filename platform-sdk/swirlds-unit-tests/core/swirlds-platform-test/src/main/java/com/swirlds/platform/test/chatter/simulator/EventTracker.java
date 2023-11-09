/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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
import static com.swirlds.common.utility.Threshold.STRONG_MINORITY;
import static com.swirlds.common.utility.Threshold.SUPER_MAJORITY;
import static com.swirlds.platform.test.chatter.simulator.GossipSimulationUtils.printHeader;
import static com.swirlds.platform.test.chatter.simulator.GossipSimulationUtils.roundDecimal;

import com.swirlds.common.formatting.TextTable;
import com.swirlds.common.sequence.map.ConcurrentSequenceMap;
import com.swirlds.common.sequence.map.SequenceMap;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.events.EventDescriptor;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * This class tracks events as they travel through the network.
 */
public class EventTracker {

    /**
     * If true then print extra stuff to the console.
     */
    private final boolean debugEnabled;

    /**
     * All events that are currently being tracked. Events are removed as they are purged.
     */
    private final SequenceMap<EventDescriptor, TrackedEvent> events =
            new ConcurrentSequenceMap<>(0, 100_000, EventDescriptor::getGeneration);

    /**
     * The total number of nodes in the simulation.
     */
    private final int nodeCount;

    /**
     * Encapsulates the CSV used to export detailed data, null if not enabled.
     */
    private final GossipCSV eventCSV;

    /**
     * Records the number of times that an event has been distributed to N nodes. For example, distributionCounts.get(3)
     * returns the number of events that have been distributed to at least 3 nodes.
     */
    private final Map<Integer, Long> distributionCounts = new HashMap<>();

    /**
     * Records the number of times that an event has failed to be distributed to N nodes. For example,
     * distributionFailureCounts.get(3) returns the number of events that were distributed to fewer than 3 nodes.
     */
    private final Map<Integer, Long> distributionFailureCounts = new HashMap<>();

    /**
     * Records the total time required to deliver events to N nodes. Times are summed, when the simulation is complete
     * the average can be computed.
     */
    private final Map<Integer, Duration> distributionSums = new HashMap<>();

    /**
     * Create a new EventTracker.
     *
     * @param builder contains configuration for the simulation
     */
    public EventTracker(final GossipSimulationBuilder builder) {
        debugEnabled = builder.isDebugEnabled();
        nodeCount = builder.getNodeCount();

        final List<String> headers = new LinkedList<>();
        headers.add("Creation Time (ms)");
        headers.add("Creator");
        for (int i = 2; i <= nodeCount; i++) {
            headers.add(Integer.toString(i));
        }

        if (builder.isCSVGenerationEnabled()) {
            eventCSV = new GossipCSV(builder.getCSVFile(), headers);
        } else {
            eventCSV = null;
        }
    }

    /**
     * This is called by the simulation engine each time a new event is created.
     *
     * @param descriptor   describes the event that was just created
     * @param creationTime the time when the event was created
     */
    public void registerNewEvent(final EventDescriptor descriptor, final Instant creationTime) {
        final TrackedEvent prev = events.put(descriptor, new TrackedEvent(descriptor.getCreator(), creationTime));

        if (prev != null) {
            throw new IllegalStateException("new event registered multiple times");
        }
    }

    /**
     * Each node should call this method each time it receives a new event, including self events.
     *
     * @param descriptor  describes the event that a node just received
     * @param nodeId      the ID of the node that received the event
     * @param receiveTime the time when the event was received
     */
    public void registerEvent(
            @NonNull final EventDescriptor descriptor,
            @NonNull final NodeId nodeId,
            @NonNull final Instant receiveTime) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(receiveTime, "receiveTime must not be null");

        final TrackedEvent trackedEvent = events.get(descriptor);

        if (trackedEvent == null) {
            // information about this event has already been purged
            if (debugEnabled) {
                System.out.println("event " + descriptor.getHash() + " registered after purge threshold");
            }
            return;
        }

        trackedEvent.registerEventPropagation(nodeId, receiveTime);

        if (debugEnabled) {
            System.out.println("event " + descriptor.getHash() + " has been distributed to "
                    + trackedEvent.getNodeCount() + " nodes");
        }
    }

    /**
     * Purge all data about events older than a given round.
     *
     * @param olderThanRound all events associated with rounds strictly older than this will be erased
     */
    public void purge(final long olderThanRound) {
        events.shiftWindow(olderThanRound, this::captureStatistics);
    }

    /**
     * Capture event statistics on an event that is about to be purged.
     *
     * @param descriptor a description of the event
     * @param event      the destination event
     */
    private void captureStatistics(final EventDescriptor descriptor, final TrackedEvent event) {

        final List<Duration> times = new ArrayList<>(event.getPropagationTimes());
        times.sort(Duration::compareTo);

        writeDataToCSV(event, times);

        for (int i = 0; i < nodeCount; i++) {

            // The time at this index represents the time required to distribute
            // the event to this many nodes
            final int nodesThatReceivedEvent = i + 2;

            if (times.size() > i) {
                // the event was successfully distributed to this number of nodes
                final long originalCount = distributionCounts.getOrDefault(nodesThatReceivedEvent, 0L);
                distributionCounts.put(nodesThatReceivedEvent, originalCount + 1);

                final Duration time = times.get(i);

                final Duration originalTime = distributionSums.getOrDefault(nodesThatReceivedEvent, Duration.ZERO);
                distributionSums.put(nodesThatReceivedEvent, originalTime.plus(time));
            } else {
                // the event was not distributed to this number of nodes
                final long originalCount = distributionFailureCounts.getOrDefault(nodesThatReceivedEvent, 0L);
                distributionFailureCounts.put(nodesThatReceivedEvent, originalCount + 1);
            }
        }
    }

    /**
     * Capture data about how an event propagated through the network.
     *
     * @param event the event in question
     */
    private void writeDataToCSV(final TrackedEvent event, final List<Duration> times) {
        if (eventCSV == null) {
            return;
        }

        final List<String> values = new LinkedList<>();
        values.add(Long.toString(event.getCreationTime().toEpochMilli()));
        values.add(event.getCreatorId().toString());

        for (int i = 0; i < nodeCount; i++) {
            if (i >= times.size()) {
                // this event didn't propagate to all nodes
                break;
            }
            values.add(Long.toString(times.get(i).toMillis()));
        }

        eventCSV.writeLine(values);
    }

    private void printNumberOfEventsDelivered() {
        final TextTable table = new TextTable()
                .setTitle("NUMBER OF EVENTS DELIVERED TO AT LEAST N NODES")
                .addRow("Number Of Nodes", "Event Count", "Fraction", "Threshold", "Significance");

        for (int i = 2; i <= nodeCount; i++) {

            String threshold = "";
            String significance = "";

            if (STRONG_MINORITY.isSatisfiedBy(i, nodeCount) && !STRONG_MINORITY.isSatisfiedBy(i - 1, nodeCount)) {
                threshold = ">= 1/3";
                significance = ">= 1 honest & functional";
            }

            if (SUPER_MAJORITY.isSatisfiedBy(i, nodeCount) && !SUPER_MAJORITY.isSatisfiedBy(i - 1, nodeCount)) {
                threshold = "> 2/3";
                significance = ">= quorum";
            }

            if (i == nodeCount) {
                threshold = "all";
                significance = "Hooray!";
            }

            final long totalCount =
                    distributionCounts.getOrDefault(i, 0L) + distributionFailureCounts.getOrDefault(i, 0L);
            final long count = distributionCounts.getOrDefault(i, 0L);
            final double fraction = ((double) count) / totalCount * 100;

            table.addRow(i, commaSeparatedNumber(count), roundDecimal(fraction, 2) + " %", threshold, significance);
        }

        System.out.println(table);
    }

    private void printNumberOfEventsNotDelivered() {
        final TextTable table = new TextTable()
                .setTitle("NUMBER OF EVENTS DELIVERED TO FEWER THAN N NODES")
                .addRow("Number Of Nodes", "Event Count", "Fraction", "Threshold", "Significance");

        for (int i = 2; i <= nodeCount; i++) {

            String threshold = "";
            String significance = "";

            if (STRONG_MINORITY.isSatisfiedBy(i, nodeCount) && !STRONG_MINORITY.isSatisfiedBy(i - 1, nodeCount)) {
                threshold = ">= 1/3";
                significance = ">= 1 honest & functional";
            }

            if (SUPER_MAJORITY.isSatisfiedBy(i, nodeCount) && !SUPER_MAJORITY.isSatisfiedBy(i - 1, nodeCount)) {
                threshold = "> 2/3";
                significance = ">= quorum";
            }

            if (i == nodeCount) {
                threshold = "all";
            }

            final long totalCount =
                    distributionCounts.getOrDefault(i, 0L) + distributionFailureCounts.getOrDefault(i, 0L);
            final long count = distributionFailureCounts.getOrDefault(i, 0L);
            final double fraction = ((double) count) / totalCount * 100;

            table.addRow(i, commaSeparatedNumber(count), roundDecimal(fraction, 2) + " %", threshold, significance);
        }

        System.out.println(table);
    }

    private void printAverageDeliveryTime() {
        final TextTable table = new TextTable()
                .setTitle("AVERAGE TIME TO DELIVER AN EVENT TO N NODES")
                .addRow("Number Of Nodes", "Time", "Threshold", "Significance");

        for (int i = 2; i <= nodeCount; i++) {

            String threshold = "";
            String significance = "";

            if (STRONG_MINORITY.isSatisfiedBy(i, nodeCount) && !STRONG_MINORITY.isSatisfiedBy(i - 1, nodeCount)) {
                threshold = ">= 1/3";
                significance = ">= 1 honest & functional";
            }

            if (SUPER_MAJORITY.isSatisfiedBy(i, nodeCount) && !SUPER_MAJORITY.isSatisfiedBy(i - 1, nodeCount)) {
                threshold = "> 2/3";
                significance = ">= quorum";
            }

            if (i == nodeCount) {
                threshold = "all";
                significance = "Hooray!";
            }

            final long count = distributionCounts.getOrDefault(i, 0L);
            final Duration totalTime = distributionSums.getOrDefault(i, Duration.ZERO);
            final Duration averageTime = count == 0 ? Duration.ZERO : totalTime.dividedBy(count);

            table.addRow(i, commaSeparatedNumber(averageTime.toMillis()) + " ms", threshold, significance);
        }

        System.out.println(table);
    }

    /**
     * Write statistical data to the console.
     */
    private void printStatistics() {
        printHeader("Event Statistics");
        System.out.println("NOTE: only data from purged events is displayed.");

        printNumberOfEventsDelivered();
        printNumberOfEventsNotDelivered();
        printAverageDeliveryTime();
    }

    /**
     * Close all files and write statistics to the console.
     */
    public void close() {
        if (eventCSV != null) {
            eventCSV.close();
        }
        printStatistics();
    }
}

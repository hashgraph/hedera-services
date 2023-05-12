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

import com.swirlds.platform.test.chatter.SimulatedChatter;
import com.swirlds.platform.test.chatter.SimulatedChatterFactory;
import java.io.File;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configures and builds a {@link GossipSimulation}.
 */
public class GossipSimulationBuilder {
    /**
     * Outgoing bandwidth values for individual nodes. Overrides the default.
     */
    private final Map<Long, Long> outgoingBandwidthMap = new HashMap<>();
    /**
     * Incoming bandwidth values for individual nodes. Overrides the default.
     */
    private final Map<Long, Long> incomingBandwidthMap = new HashMap<>();
    /**
     * Latency between pairs of nodes. Overrides default.
     */
    private final Map<SourceDestinationPair, Duration> latencyMap = new HashMap<>();
    /**
     * The average event creation rate for individual nodes. Overrides the default.
     */
    private final Map<Long, Double> averageEventsCreatedPerSecondMap = new HashMap<>();
    /**
     * The probability that a message sent from a particular node to another will be dropped.
     * Overrides {@link #dropProbabilityDefault}
     */
    private final Map<SourceDestinationPair, Double> dropProbabilityMap = new HashMap<>();
    /**
     * The connection status between specific nodes, overrides default. True = connected, false = disconnected.
     */
    private final Map<SourceDestinationPair, Boolean> connectionStatusMap = new HashMap<>();
    /**
     * Time step of the simulation.
     */
    private Duration timeStep = Duration.ofMillis(1);
    /**
     * The total number of nodes.
     */
    private int nodeCount = 4;
    /**
     * The default outgoing bandwidth of each node.
     */
    private long outgoingBandwidthDefault = 10_000;
    /**
     * The default incoming bandwidth of each node in bytes/second indexed by node ID.
     */
    private long incomingBandwidthDefault = 10_000;
    /**
     * The default latency from one node to another.
     */
    private Duration latencyDefault = Duration.ofMillis(100);
    /**
     * The default average number of events that a node creates per second.
     */
    private Double averageEventsCreatedPerSecondDefault = 1.0;
    /**
     * The average size of an event, in bytes.
     */
    private double averageEventSizeInBytes = 10_000.0;
    /**
     * The standard deviation of an event size, in bytes.
     */
    private double eventSizeInBytesStandardDeviation = 100.0;
    /**
     * Seed used for all randomness.
     */
    private long seed = 0;
    /**
     * If set to true then print extra debug information to the console. Quite spammy for large simulations.
     */
    private boolean debugEnabled = false;
    /**
     * The maximum number of messages that may be enqueued at a destination before messages start throttled.
     */
    private int incomingMessageQueueSize = 1000;
    /**
     * If true then generate a CSV with the results of the simulation.
     */
    private boolean csvGenerationEnabled = false;
    /**
     * If CSV generation is enabled then write it into this file.
     */
    private File csvFile = new File("gossipSimulation.csv");
    /**
     * The length of time between printing the currently simulated timestamp to the console.
     */
    private Duration timeReportPeriod = Duration.ofSeconds(1);
    /**
     * The default probability that a message will be dropped as it is sent.
     */
    private double dropProbabilityDefault = 0.0;
    /**
     * The number of threads to use when simulating. If unset, creates one thread per node.
     * Does not influence result of the simulation.
     */
    private Integer threadCount = null;
    /**
     * The default connection status of nodes. True = connected, false = disconnected.
     */
    private boolean connectionStatusDefault = true;
    /**
     * Factory for constructing {@link SimulatedChatter} instances
     */
    private SimulatedChatterFactory chatterFactory = null;
    /** Whether to run the simulation in a single thread */
    private boolean singleThreaded = false;

    /**
     * Build the simulation.
     *
     * @return the simulation
     */
    public GossipSimulation build() {
        return new GossipSimulation(this);
    }

    /**
     * Get the time step, i.e. the smallest temporal granularity used by the simulator.
     *
     * @return the time step
     */
    public Duration getTimeStep() {
        return timeStep;
    }

    /**
     * Set the time step, i.e. the smallest temporal granularity used by the simulator.
     *
     * @param timeStep
     * 		the time step
     * @return this object
     */
    public GossipSimulationBuilder setTimeStep(final Duration timeStep) {
        this.timeStep = timeStep;
        return this;
    }

    /**
     * Get the total number of nodes.
     *
     * @return the total number of nodes
     */
    public int getNodeCount() {
        return nodeCount;
    }

    /**
     * Set the total number of nodes.
     *
     * @param nodeCount
     * 		the total number of nodes
     * @return this object
     */
    public GossipSimulationBuilder setNodeCount(final int nodeCount) {
        this.nodeCount = nodeCount;
        return this;
    }

    /**
     * Set the default outgoing bandwidth in bytes/second.
     *
     * @param outgoingBandwidthDefault
     * 		the default outgoing bandwidth
     * @return this object
     */
    public GossipSimulationBuilder setOutgoingBandwidthDefault(final int outgoingBandwidthDefault) {
        this.outgoingBandwidthDefault = outgoingBandwidthDefault;
        return this;
    }

    /**
     * Get the outgoing bandwidth for a particular node. If no bandwidth has been specified then the default is used.
     *
     * @param nodeId
     * 		the node ID in question
     * @return the outgoing bandwidth for the given node
     */
    public long getOutgoingBandwidth(final Long nodeId) {
        return outgoingBandwidthMap.getOrDefault(nodeId, outgoingBandwidthDefault);
    }

    /**
     * Set the outgoing bandwidth for a particular node. Overrides the default.
     *
     * @param nodeId
     * 		the destination node
     * @param outgoingBandwidth
     * 		the node's new bandwidth in bytes/second
     * @return this object
     */
    public GossipSimulationBuilder setOutgoingBandwidth(final Long nodeId, final long outgoingBandwidth) {
        outgoingBandwidthMap.put(nodeId, outgoingBandwidth);
        return this;
    }

    /**
     * Set the default incoming bandwidth for a node in bytes/second.
     *
     * @param incomingBandwidthDefault
     * 		bandwidth in bytes/second
     * @return this object
     */
    public GossipSimulationBuilder setIncomingBandwidthDefault(final long incomingBandwidthDefault) {
        this.incomingBandwidthDefault = incomingBandwidthDefault;
        return this;
    }

    /**
     * Get incoming bandwidth for a particular node in bytes/second.
     *
     * @param nodeId
     * 		the destination node
     * @return the node's incoming bandwidth in bytes/second
     */
    public long getIncomingBandwidth(final long nodeId) {
        return incomingBandwidthMap.getOrDefault(nodeId, incomingBandwidthDefault);
    }

    /**
     * Set the incoming bandwidth for a particular node in bytes/second.
     *
     * @param nodeId
     * 		the destination node
     * @param incomingBandwidth
     * 		bandwidth in bytes/second
     * @return this object
     */
    public GossipSimulationBuilder setIncomingBandwidth(final long nodeId, final long incomingBandwidth) {
        incomingBandwidthMap.put(nodeId, incomingBandwidth);
        return this;
    }

    /**
     * Set the default value for average events created per second by each node.
     *
     * @param averageEventsCreatedPerSecondDefault
     * 		the default value
     * @return this object
     */
    public GossipSimulationBuilder setAverageEventsCreatedPerSecondDefault(
            final Double averageEventsCreatedPerSecondDefault) {
        this.averageEventsCreatedPerSecondDefault = averageEventsCreatedPerSecondDefault;
        return this;
    }

    /**
     * Get the average number of events created per second for a particular node.
     *
     * @param nodeId
     * 		the node in question
     * @return the average number of events created per second for a node
     */
    public double getAverageEventsCreatedPerSecond(final long nodeId) {
        return averageEventsCreatedPerSecondMap.getOrDefault(nodeId, averageEventsCreatedPerSecondDefault);
    }

    /**
     * Set the average number of events created per second for a particular node. Overrides the default.
     *
     * @param nodeId
     * 		the node in question
     * @param averageEventsCreatedPerSecondAverage
     * 		the node's average number of events per second
     * @return this object
     */
    public GossipSimulationBuilder setAverageEventsCreatedPerSecond(
            final long nodeId, final double averageEventsCreatedPerSecondAverage) {
        averageEventsCreatedPerSecondMap.put(nodeId, averageEventsCreatedPerSecondAverage);
        return this;
    }

    /**
     * Get the average size of an event, in bytes.
     *
     * @return the average event size
     */
    public double getAverageEventSizeInBytes() {
        return averageEventSizeInBytes;
    }

    /**
     * Set the average size of an event, in bytes.
     *
     * @param averageEventSizeInBytes
     * 		the average event size
     * @return this object
     */
    public GossipSimulationBuilder setAverageEventSizeInBytes(final double averageEventSizeInBytes) {
        this.averageEventSizeInBytes = averageEventSizeInBytes;
        return this;
    }

    /**
     * Get the standard deviation of the event size.
     *
     * @return the standard deviation
     */
    public double getEventSizeInBytesStandardDeviation() {
        return eventSizeInBytesStandardDeviation;
    }

    /**
     * Set the standard deviation of the event size.
     *
     * @param eventSizeInBytesStandardDeviation
     * 		the standard deviation
     * @return this object
     */
    public GossipSimulationBuilder setEventSizeInBytesStandardDeviation(
            final double eventSizeInBytesStandardDeviation) {
        this.eventSizeInBytesStandardDeviation = eventSizeInBytesStandardDeviation;
        return this;
    }

    /**
     * Get the simulation seed.
     *
     * @return the seed
     */
    public long getSeed() {
        return seed;
    }

    /**
     * Set the simulation seed.
     *
     * @param seed
     * 		the seed
     * @return this object
     */
    public GossipSimulationBuilder setSeed(final long seed) {
        this.seed = seed;
        return this;
    }

    /**
     * Set the default latency between nodes.
     *
     * @param latencyDefault
     * 		the default latency
     * @return this object
     */
    public GossipSimulationBuilder setLatencyDefault(final Duration latencyDefault) {
        this.latencyDefault = latencyDefault;
        return this;
    }

    /**
     * Get the latency between a pair of nodes.
     *
     * @param source
     * 		the source node for a message
     * @param destination
     * 		the destination node for a message
     * @return the time required for a message to travel from the source to the destination
     */
    public Duration getLatency(final Long source, final long destination) {
        return latencyMap.getOrDefault(new SourceDestinationPair(source, destination), latencyDefault);
    }

    /**
     * Set the latency between a pair of nodes. Overrides the default.
     *
     * @param source
     * 		the source node for a message
     * @param destination
     * 		the destination node for a message
     * @param latency
     * 		the time required for a message to travel from the source to the destination
     * @return this object
     */
    public GossipSimulationBuilder setLatency(final long source, final long destination, final Duration latency) {
        latencyMap.put(new SourceDestinationPair(source, destination), latency);
        return this;
    }

    /**
     * Check if debug mode is enabled.
     *
     * @return true if debug mode is enabled
     */
    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    /**
     * Set debug mode.
     *
     * @param debugEnabled
     * 		true if debug mode should be enabled, false if it should be disabled
     * @return this object
     */
    public GossipSimulationBuilder setDebugEnabled(final boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
        return this;
    }

    /**
     * Get the maximum number of incoming messages that may be enqueued at a node before
     * messages start getting throttled.
     *
     * @return the queue size
     */
    public int getIncomingMessageQueueSize() {
        return incomingMessageQueueSize;
    }

    /**
     * Set the maximum number of incoming messages that may be enqueued at a node before messages start getting dropped.
     *
     * @param incomingMessageQueueSize
     * 		the queue size
     * @return this object
     */
    public GossipSimulationBuilder setIncomingMessageQueueSize(final int incomingMessageQueueSize) {
        this.incomingMessageQueueSize = incomingMessageQueueSize;
        return this;
    }

    /**
     * Check if CSV generation is enabled.
     *
     * @return true if CSV generation is enabled
     */
    public boolean isCSVGenerationEnabled() {
        return csvGenerationEnabled;
    }

    /**
     * Set if CSV generation is enabled.
     *
     * @param csvGenerationEnabled
     * 		true if CSV generation is enabled
     * @return this object
     */
    public GossipSimulationBuilder setCSVGenerationEnabled(final boolean csvGenerationEnabled) {
        this.csvGenerationEnabled = csvGenerationEnabled;
        return this;
    }

    /**
     * Get the file where the CSV will be written (if enabled).
     *
     * @return the CSV file
     */
    public File getCSVFile() {
        return csvFile;
    }

    /**
     * Set the file where the CSV will be written (if enabled).
     *
     * @param csvFile
     * 		the CSV file
     * @return this object
     */
    public GossipSimulationBuilder setCSVFile(final File csvFile) {
        this.csvFile = csvFile;
        return this;
    }

    /**
     * Get the time between when the simulated time is printed to the console.
     *
     * @return the period between time reports, if null then time is never reported
     */
    public Duration getTimeReportPeriod() {
        return timeReportPeriod;
    }

    /**
     * Set the time between when the simulated time is printed to the console.
     *
     * @param timeReportPeriod
     * 		the period between time reports, if null then time is never reported
     * @return this object
     */
    public GossipSimulationBuilder setTimeReportPeriod(final Duration timeReportPeriod) {
        this.timeReportPeriod = timeReportPeriod;
        return this;
    }

    /**
     * Set the default probability that a message will be dropped while in transit.
     *
     * @param dropProbabilityDefault
     * 		a probability as a fraction of 1.0
     * @return this object
     */
    public GossipSimulationBuilder setDropProbabilityDefault(final double dropProbabilityDefault) {
        this.dropProbabilityDefault = dropProbabilityDefault;
        return this;
    }

    /**
     * Set the drop probability between a pair of nodes. Overrides the default.
     *
     * @param source
     * 		the sending node ID
     * @param destination
     * 		the receiving node ID
     * @param dropProbability
     * 		a probability as a fraction of 1.0
     * @return this object
     */
    public GossipSimulationBuilder setDropProbability(
            final long source, final long destination, final double dropProbability) {
        dropProbabilityMap.put(new SourceDestinationPair(source, destination), dropProbability);
        return this;
    }

    /**
     * Get the probability that a message sent from the source to the destinatino will be dropped.
     *
     * @param source
     * 		the sending node
     * @param destination
     * 		the receiving node
     * @return a probability as a fraction of 1.0
     */
    public double getDropProbability(final long source, final long destination) {
        return dropProbabilityMap.getOrDefault(new SourceDestinationPair(source, destination), dropProbabilityDefault);
    }

    /**
     * Get the number of threads used by the simulation. Does not influence simulation results.
     *
     * @return the thread count
     */
    public int getThreadCount() {
        return threadCount == null ? Math.min(nodeCount, Runtime.getRuntime().availableProcessors()) : threadCount;
    }

    /**
     * Set the number of threads used by the simulation. Does not influence simulation results.
     *
     * @param threadCount
     * 		the thread count
     * @return this object
     */
    public GossipSimulationBuilder setThreadCount(final int threadCount) {
        this.threadCount = threadCount;
        return this;
    }

    /**
     * Set the default connection status between nodes.
     *
     * @param connectionStatusDefault
     * 		true = connected, false = disconnected
     * @return this object
     */
    public GossipSimulationBuilder setConnectionStatusDefault(final boolean connectionStatusDefault) {
        this.connectionStatusDefault = connectionStatusDefault;
        return this;
    }

    /**
     * Set the default connection status between two specific nodes. Overrides default.
     *
     * @param source
     * 		the source of a message
     * @param destination
     * 		the destination of a message
     * @param connectionStatus
     * 		true if the source can send a message to the destination
     * @return this object
     */
    public GossipSimulationBuilder setConnectionStatus(
            final long source, final long destination, final boolean connectionStatus) {

        connectionStatusMap.put(new SourceDestinationPair(source, destination), connectionStatus);
        return this;
    }

    /**
     * Get the connection status between two nodes
     *
     * @param source
     * 		the source of messages
     * @param destination
     * 		the destination of messages
     * @return true = connected, false = disconnected
     */
    public boolean getConnectionStatus(final long source, final long destination) {
        return connectionStatusMap.getOrDefault(
                new SourceDestinationPair(source, destination), connectionStatusDefault);
    }

    /**
     * @return factory for constructing {@link SimulatedChatter} instances
     */
    public SimulatedChatterFactory getChatterFactory() {
        return chatterFactory;
    }

    /**
     * @param chatterFactory
     * 		factory for constructing {@link SimulatedChatter} instances
     * @return this object
     */
    public GossipSimulationBuilder setChatterFactory(SimulatedChatterFactory chatterFactory) {
        this.chatterFactory = chatterFactory;
        return this;
    }

    /**
     * @return whether to run the simulation in a single thread
     */
    public boolean isSingleThreaded() {
        return singleThreaded;
    }

    /**
     * @param singleThreaded
     * 		whether to run the simulation in a single thread
     * @return this object
     */
    public GossipSimulationBuilder setSingleThreaded(boolean singleThreaded) {
        this.singleThreaded = singleThreaded;
        return this;
    }
}

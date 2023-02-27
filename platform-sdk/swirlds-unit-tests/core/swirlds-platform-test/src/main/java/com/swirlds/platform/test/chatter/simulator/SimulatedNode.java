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

import static com.swirlds.common.utility.Units.NANOSECONDS_TO_SECONDS;

import com.swirlds.platform.chatter.protocol.messages.ChatterEvent;
import com.swirlds.platform.test.chatter.GossipPayload;
import com.swirlds.platform.test.chatter.SimulatedChatter;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * A simulation of a single node.
 */
public class SimulatedNode {

    final long selfId;
    final int nodeCount;
    final Set<Long> nodeIds;
    final Duration timeStep;
    /**
     * If true then print extra stuff to the console.
     */
    private final boolean debugEnabled;

    private final SimulatedNetwork network;
    private final Random random;

    private final SimulatedChatter chatter;

    private final Double averageEventsPerSecond;

    private final Double averageEventSize;
    private final Double eventSizeStandardDeviation;

    private final Supplier<Long> roundProvider;

    private final EventTracker eventTracker;
    /**
     * A list of all currently connected destinations.
     */
    private final List<Long> connectedDestinations;

    private long lastPurgedRound = -1;
    private final AtomicReference<Instant> currentTime = new AtomicReference<>();

    /**
     * Create a new simulated node.
     *
     * @param selfId
     * 		the ID of the node
     * @param seed
     * 		a seed for this node's random number generator
     */
    public SimulatedNode(
            final GossipSimulationBuilder builder,
            final long selfId,
            final long seed,
            final SimulatedNetwork network,
            final EventTracker eventTracker,
            final Supplier<Long> roundProvider) {

        this.debugEnabled = builder.isDebugEnabled();

        this.selfId = selfId;
        this.nodeCount = builder.getNodeCount();
        this.timeStep = builder.getTimeStep();

        this.random = new Random(seed);
        this.network = network;

        this.nodeIds = new HashSet<>();
        for (long nodeId = 0; nodeId < nodeCount; nodeId++) {
            nodeIds.add(nodeId);
        }

        this.chatter = builder.getChatterFactory().build(selfId, nodeIds, this::registerEvent, currentTime::get);

        this.averageEventsPerSecond = builder.getAverageEventsCreatedPerSecond(selfId);
        this.averageEventSize = builder.getAverageEventSizeInBytes();
        this.eventSizeStandardDeviation = builder.getEventSizeInBytesStandardDeviation();
        this.roundProvider = roundProvider;

        this.eventTracker = eventTracker;

        connectedDestinations = nodeIds.stream()
                .filter(d -> d != selfId)
                .filter(d -> network.isDestinationConnected(selfId, d))
                .collect(Collectors.toList());
    }

    private void registerEvent(final ChatterEvent e) {
        eventTracker.registerEvent(e.getDescriptor(), selfId, currentTime.get());
    }

    /**
     * Simulate one step in the simulation. Send and receive a bunch of messages, and possibly generate an event.
     *
     * @param now
     * 		the current (simulated) time
     */
    public void simulateOneStep(final Instant now) {
        currentTime.set(now);
        maybeGenerateEvent(now);
        receiveMessages(now);
        sendMessages(now);
        purge();
    }

    /**
     * Generate an event with some probability.
     */
    private void maybeGenerateEvent(final Instant now) {
        final double averageEventsPerTimeStep = averageEventsPerSecond * (timeStep.getNano() * NANOSECONDS_TO_SECONDS);
        if (random.nextDouble() < averageEventsPerTimeStep) {
            final int size = (int) Math.max(1, random.nextGaussian(averageEventSize, eventSizeStandardDeviation));
            final SimulatedEvent event = new SimulatedEvent(random, selfId, roundProvider.get(), size);
            event.setTimeReceived(now);

            if (debugEnabled) {
                System.out.println(now.toEpochMilli() + ": node " + selfId + " created event "
                        + event.getDescriptor().getHash());
            }

            eventTracker.registerNewEvent(event.getDescriptor(), now);
            chatter.newEvent(event);
        }
    }

    /**
     * Receive some messages from the network.
     */
    private void receiveMessages(final Instant now) {
        while (network.hasMessages(selfId)) {
            final SimulatedMessage message = network.nextMessage(selfId);
            if (message.getPayload() instanceof SimulatedEvent se) {
                se.setTimeReceived(now);
            }

            if (debugEnabled) {
                System.out.println(now.toEpochMilli() + ": " + selfId + " received "
                        + message.getPayload().getClass().getSimpleName() + " from " + message.getSource());
            }

            chatter.handlePayload(message.getPayload(), message.getSource());
        }
    }

    /**
     * Send some messages to the network.
     *
     * @param now
     * 		the current time
     */
    private void sendMessages(final Instant now) {
        final List<Long> destinations = new ArrayList<>(connectedDestinations);
        while (network.isOutgoingCapacityAvailable(selfId) && !destinations.isEmpty()) {
            // pick a random destination
            final int destinationIndex = random.nextInt(destinations.size());
            final Long destination = connectedDestinations.get(destinationIndex);

            if (!network.isDestinationAvailable(selfId, destination)) {
                // no more capacity for this destination
                destinations.remove(destinationIndex);
                continue;
            }

            GossipPayload payload = chatter.generatePayload(now, false, destination);

            if (payload == null) {
                payload = chatter.generatePayload(now, true, destination);
            }

            if (payload == null) {
                // no messages for this destination
                destinations.remove(destinationIndex);
                continue;
            }

            if (payload.destination() == selfId) {
                throw new RuntimeException("ERROR: Should not send to self");
            }

            final SimulatedMessage message = new SimulatedMessage(selfId, payload.destination(), payload.payload());
            if (debugEnabled) {
                System.out.println(now.toEpochMilli() + ": node " + selfId + " sending message to " + destination);
            }
            network.send(message);
        }
    }

    /**
     * Purge old data.
     */
    private void purge() {
        final long roundToPurge = roundProvider.get() - 60;
        if (roundToPurge > lastPurgedRound) {
            chatter.shiftWindow(roundToPurge);
            lastPurgedRound = roundToPurge;
        }
    }
}

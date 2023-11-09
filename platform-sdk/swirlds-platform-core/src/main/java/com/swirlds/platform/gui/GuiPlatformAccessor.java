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

package com.swirlds.platform.gui;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.events.PlatformEvent;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.components.state.StateManagementComponent;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Provides a way to access private platform objects from the GUI. Suboptimal, but necessary to preserve the current UI
 * architecture if we don't want to allow public access to these objects.
 *
 * @deprecated this class will eventually be removed
 */
@Deprecated(forRemoval = true)
public final class GuiPlatformAccessor {

    private final Map<NodeId, ShadowGraph> shadowGraphs = new ConcurrentHashMap<>();
    private final Map<NodeId, StateManagementComponent> stateManagementComponents = new ConcurrentHashMap<>();
    private final Map<NodeId, AtomicReference<Consensus>> consensusReferences = new ConcurrentHashMap<>();

    private static final GuiPlatformAccessor INSTANCE = new GuiPlatformAccessor();

    /**
     * Get the static instance of the GuiPlatformAccessor.
     *
     * @return the static instance of the GuiPlatformAccessor
     */
    public static GuiPlatformAccessor getInstance() {
        return INSTANCE;
    }

    private GuiPlatformAccessor() {}

    /**
     * Set the shadow graph for a node.
     *
     * @param nodeId      the ID of the node
     * @param shadowGraph the shadow graph
     */
    public void setShadowGraph(@NonNull final NodeId nodeId, @NonNull final ShadowGraph shadowGraph) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(shadowGraph, "shadowGraph must not be null");
        shadowGraphs.put(nodeId, shadowGraph);
    }

    /**
     * Get the shadow graph for a node, or null if none is set.
     *
     * @param nodeId the ID of the node
     * @return the shadow graph
     */
    @Nullable
    public ShadowGraph getShadowGraph(@NonNull NodeId nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        return shadowGraphs.getOrDefault(nodeId, null);
    }

    /**
     * Information required to sort events by consensus order (or, if events have not yet reached consensus, by received
     * time).
     *
     * @param consensusOrder the consensus order if known, else -1 if unknown
     * @param timeReceived   the time the event was received, used to break ties when consensus order is unknown for
     *                       both events
     */
    private record EventOrderInfo(long consensusOrder, @NonNull Instant timeReceived)
            implements Comparable<EventOrderInfo> {

        /**
         * Create a new EventOrderInfo from an event.
         */
        public static EventOrderInfo of(@NonNull final EventImpl event) {
            return new EventOrderInfo(
                    event.getConsensusOrder(), event.getBaseEvent().getTimeReceived());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int compareTo(@NonNull final EventOrderInfo that) {
            if (this.consensusOrder == -1 && that.consensusOrder == -1) {
                // neither have reached consensus
                return this.timeReceived.compareTo(that.timeReceived);
            } else if (this.consensusOrder == -1) {
                // that has reached consensus but not this
                return 1;
            } else if (that.consensusOrder == -1) {
                // this has reached consensus but not that
                return -1;
            } else {
                // both have reached consensus
                return Long.compare(this.consensusOrder, that.consensusOrder);
            }
        }
    }

    /**
     * Get a sorted list of events.
     */
    public PlatformEvent[] getAllEvents(@NonNull NodeId nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");

        final EventImpl[] allEvents = getShadowGraph(nodeId).getAllEvents();

        // If events reach consensus during the execution of this method, order will change.
        // Capture information before sorting, since sorting breaks if order changes mid-sort.
        final Map<Hash, EventOrderInfo> orderInfo = new HashMap<>();
        for (final EventImpl event : allEvents) {
            orderInfo.put(event.getHashedData().getHash(), EventOrderInfo.of(event));
        }

        Arrays.sort(allEvents, (eventA, eventB) -> {
            final EventOrderInfo orderA = orderInfo.get(eventA.getHashedData().getHash());
            final EventOrderInfo orderB = orderInfo.get(eventB.getHashedData().getHash());
            return orderA.compareTo(orderB);
        });
        return allEvents;
    }

    /**
     * Set the state management component for a node.
     *
     * @param nodeId                   the ID of the node
     * @param stateManagementComponent the state management component
     */
    public void setStateManagementComponent(
            @NonNull final NodeId nodeId, @NonNull final StateManagementComponent stateManagementComponent) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(stateManagementComponent, "stateManagementComponent must not be null");
        stateManagementComponents.put(nodeId, stateManagementComponent);
    }

    /**
     * Get the state management component for a node, or null if none is set.
     *
     * @param nodeId the ID of the node
     * @return the state management component
     */
    @Nullable
    public StateManagementComponent getStateManagementComponent(@NonNull final NodeId nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        return stateManagementComponents.getOrDefault(nodeId, null);
    }

    /**
     * Set the consensus for a node.
     *
     * @param nodeId    the ID of the node
     * @param consensus the consensus
     */
    public void setConsensusReference(
            @NonNull final NodeId nodeId, @NonNull final AtomicReference<Consensus> consensus) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(consensus, "consensus must not be null");
        consensusReferences.put(nodeId, consensus);
    }

    /**
     * Get the consensus for a node, or null if none is set.
     *
     * @param nodeId the ID of the node
     * @return the consensus
     */
    @Nullable
    public Consensus getConsensus(@NonNull final NodeId nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        final AtomicReference<Consensus> consensusReference = consensusReferences.getOrDefault(nodeId, null);
        if (consensusReference == null) {
            return null;
        }
        return consensusReference.get();
    }
}

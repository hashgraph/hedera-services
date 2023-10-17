/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.gossip.sync;

import static com.swirlds.common.metrics.Metrics.INTERNAL_CATEGORY;
import static com.swirlds.logging.LogMarker.FREEZE;

import com.swirlds.common.config.EventConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.components.CriticalQuorum;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.FallenBehindManager;
import com.swirlds.platform.gossip.sync.config.SyncConfig;
import com.swirlds.platform.network.RandomGraph;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A class that manages information about who we need to sync with, and whether we need to reconnect
 */
public class SyncManagerImpl implements FallenBehindManager {

    private static final Logger logger = LogManager.getLogger(SyncManagerImpl.class);

    /**
     * When looking for a neighbor to call, this is the maximum number of neighbors we query before just selecting one
     * if a suitable neighbor is not found yet.
     */
    private static final int MAXIMUM_NEIGHBORS_TO_QUERY = 10;

    private final EventConfig eventConfig;

    /** the event intake queue */
    private final BlockingQueue<GossipEvent> intakeQueue;
    /** This object holds data on how nodes are connected to each other. */
    private final RandomGraph connectionGraph;
    /** The id of this node */
    private final NodeId selfId;
    /** Tracks recent events */
    private final CriticalQuorum criticalQuorum;

    private final boolean useCriticalQuorum;
    /** The initial address book */
    private final AddressBook addressBook;

    private final FallenBehindManager fallenBehindManager;

    /**
     * True if gossip has been artificially halted.
     */
    private final AtomicBoolean gossipHalted = new AtomicBoolean(false);

    /**
     * Creates a new SyncManager
     *
     * @param platformContext    the platform context
     * @param intakeQueue        the event intake queue
     * @param connectionGraph    The platforms connection graph.
     * @param selfId             The ID of the platform.
     */
    public SyncManagerImpl(
            @NonNull final PlatformContext platformContext,
            @NonNull final BlockingQueue<GossipEvent> intakeQueue,
            @NonNull final RandomGraph connectionGraph,
            @NonNull final NodeId selfId,
            @NonNull final CriticalQuorum criticalQuorum,
            @NonNull final AddressBook addressBook,
            @NonNull final FallenBehindManager fallenBehindManager,
            @NonNull final EventConfig eventConfig) {

        this.intakeQueue = Objects.requireNonNull(intakeQueue);
        this.connectionGraph = Objects.requireNonNull(connectionGraph);
        this.selfId = Objects.requireNonNull(selfId);

        this.criticalQuorum = Objects.requireNonNull(criticalQuorum);
        this.addressBook = Objects.requireNonNull(addressBook);

        this.fallenBehindManager = Objects.requireNonNull(fallenBehindManager);
        this.eventConfig = Objects.requireNonNull(eventConfig);

        useCriticalQuorum = platformContext
                .getConfiguration()
                .getConfigData(SyncConfig.class)
                .criticalQuorumEnabled();

        platformContext
                .getMetrics()
                .getOrCreate(new FunctionGauge.Config<>(
                                INTERNAL_CATEGORY, "hasFallenBehind", Object.class, this::hasFallenBehind)
                        .withDescription("has this node fallen behind?"));
        platformContext
                .getMetrics()
                .getOrCreate(new FunctionGauge.Config<>(
                                INTERNAL_CATEGORY,
                                "numReportFallenBehind",
                                Integer.class,
                                this::numReportedFallenBehind)
                        .withDescription("the number of nodes that have fallen behind")
                        .withUnit("count"));
    }

    /**
     * A method called by the sync listener to determine whether a sync should be accepted or not
     *
     * @return true if the sync should be accepted, false otherwise
     */
    public boolean shouldAcceptSync() {
        // don't gossip if halted
        if (gossipHalted.get()) {
            return false;
        }

        // we shouldn't sync if the event intake queue is too big
        final int intakeQueueSize = intakeQueue.size();
        if (intakeQueueSize > eventConfig.eventIntakeQueueThrottleSize()) {
            return false;
        }
        return true;
    }

    /**
     * A method called by the sync caller to determine whether a sync should be initiated or not
     *
     * @return true if the sync should be initiated, false otherwise
     */
    public boolean shouldInitiateSync() {
        // don't gossip if halted
        if (gossipHalted.get()) {
            return false;
        }

        // we shouldn't sync if the event intake queue is too big
        return intakeQueue.size() <= eventConfig.eventIntakeQueueThrottleSize();
    }

    /**
     * Retrieves a list of neighbors in order of syncing priority
     *
     * @return a list of neighbors
     */
    public List<NodeId> getNeighborsToCall() {
        // if there is an indication we might have fallen behind, calling nodes to establish this takes priority
        List<NodeId> list = getNeededForFallenBehind();
        if (list != null) {
            return list;
        }
        list = new LinkedList<>();
        final int selfIndex = addressBook.getIndexOfNodeId(selfId);
        for (int i = 0; i < MAXIMUM_NEIGHBORS_TO_QUERY; i++) {
            // Noncontiguous NodeId compatibility: connectionGraph is interpreted as addressbook indexes for NodeIds
            final int neighbor = connectionGraph.randomNeighbor(selfIndex) % addressBook.getSize();
            if (neighbor == selfIndex) {
                continue;
            }
            final NodeId neighborId = addressBook.getNodeId(neighbor);

            // don't add duplicated nodes here
            if (list.contains(neighborId)) {
                continue;
            }

            if (useCriticalQuorum) {
                // we try to call a neighbor in the bottom 1/3 by number of events created in the latest round, if
                // we fail to find one after 10 tries, we just call the last neighbor we find
                if (criticalQuorum.isInCriticalQuorum(neighborId) || i == MAXIMUM_NEIGHBORS_TO_QUERY - 1) {
                    list.add(neighborId);
                }
            } else {
                list.add(neighborId);
            }
        }

        return list;
    }

    /**
     * Observers halt requested dispatches. Causes gossip to permanently stop (until node reboot).
     *
     * @param reason the reason why gossip is being stopped
     */
    public void haltRequestedObserver(final String reason) {
        gossipHalted.set(true);
        logger.info(FREEZE.getMarker(), "Gossip frozen, reason: {}", reason);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reportFallenBehind(final NodeId id) {
        fallenBehindManager.reportFallenBehind(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resetFallenBehind() {
        fallenBehindManager.resetFallenBehind();
    }

    @Override
    public List<NodeId> getNeededForFallenBehind() {
        return fallenBehindManager.getNeededForFallenBehind();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasFallenBehind() {
        return fallenBehindManager.hasFallenBehind();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<NodeId> getNeighborsForReconnect() {
        return fallenBehindManager.getNeighborsForReconnect();
    }

    @Override
    public boolean shouldReconnectFrom(final NodeId peerId) {
        return fallenBehindManager.shouldReconnectFrom(peerId);
    }

    @Override
    public int numReportedFallenBehind() {
        return fallenBehindManager.numReportedFallenBehind();
    }
}

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

package com.swirlds.platform;

import static com.swirlds.logging.LogMarker.FREEZE;
import static com.swirlds.logging.LogMarker.SYNC;

import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.components.CriticalQuorum;
import com.swirlds.platform.components.EventCreationRules;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.network.RandomGraph;
import com.swirlds.platform.sync.FallenBehindManager;
import com.swirlds.platform.sync.SyncManager;
import com.swirlds.platform.sync.SyncResult;
import com.swirlds.platform.sync.SyncUtils;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A class that manages information about who we need to sync with, and whether we need to reconnect
 */
public class SyncManagerImpl implements SyncManager, FallenBehindManager {

    private static final Logger logger = LogManager.getLogger(SyncManagerImpl.class);

    /**
     * When looking for a neighbor to call, this is the maximum number of neighbors we query before just selecting
     * one if a suitable neighbor is not found yet.
     */
    private static final int MAXIMUM_NEIGHBORS_TO_QUERY = 10;

    private final Settings settings = Settings.getInstance();

    /** the event intake queue */
    private final BlockingQueue<EventIntakeTask> intakeQueue;
    /** This object holds data on how nodes are connected to each other. */
    private final RandomGraph connectionGraph;
    /** The id of this node */
    private final NodeId selfId;
    /** This object is used for checking whether this node should create an event or not */
    private final EventCreationRules eventCreationRules;
    /** Tracks recent events */
    private final CriticalQuorum criticalQuorum;
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
     * @param intakeQueue
     * 		the event intake queue
     * @param connectionGraph
     * 		The platforms connection graph.
     * @param selfId
     * 		The ID of the platform.
     * @param eventCreationRules
     * 		Contains a list of rules for checking whether this node should create an event or not
     */
    SyncManagerImpl(
            final BlockingQueue<EventIntakeTask> intakeQueue,
            final RandomGraph connectionGraph,
            final NodeId selfId,
            final EventCreationRules eventCreationRules,
            final CriticalQuorum criticalQuorum,
            final AddressBook addressBook,
            final FallenBehindManager fallenBehindManager) {
        super();

        this.intakeQueue = intakeQueue;
        this.connectionGraph = connectionGraph;
        this.selfId = selfId;

        this.eventCreationRules = eventCreationRules;
        this.criticalQuorum = criticalQuorum;
        this.addressBook = addressBook;

        this.fallenBehindManager = fallenBehindManager;
    }

    /**
     * A method called by the sync listener to determine whether a sync should be accepted or not
     *
     * @return true if the sync should be accepted, false otherwise
     */
    @Override
    public boolean shouldAcceptSync() {
        // don't gossip if halted
        if (gossipHalted.get()) {
            return false;
        }

        // we shouldn't sync if the event intake queue is too big
        final int intakeQueueSize = intakeQueue.size();
        if (intakeQueueSize > settings.getEventIntakeQueueThrottleSize()) {
            logger.debug(
                    SYNC.getMarker(),
                    "don't accept sync because event intake queue is too big, size: {}",
                    intakeQueueSize);
            return false;
        }
        return true;
    }

    /**
     * A method called by the sync caller to determine whether a sync should be initiated or not
     *
     * @return true if the sync should be initiated, false otherwise
     */
    @Override
    public boolean shouldInitiateSync() {
        // don't gossip if halted
        if (gossipHalted.get()) {
            return false;
        }

        // we shouldn't sync if the event intake queue is too big
        return intakeQueue.size() <= settings.getEventIntakeQueueThrottleSize();
    }

    /**
     * Retrieves a list of neighbors in order of syncing priority
     *
     * @return a list of neighbors
     */
    @Override
    public List<Long> getNeighborsToCall() {
        // if there is an indication we might have fallen behind, calling nodes to establish this takes priority
        List<Long> list = getNeededForFallenBehind();
        if (list != null) {
            return list;
        }
        list = new LinkedList<>();
        for (int i = 0; i < MAXIMUM_NEIGHBORS_TO_QUERY; i++) {
            final long neighbor = connectionGraph.randomNeighbor(selfId.getIdAsInt());

            // don't add duplicated nodes here
            if (list.contains(neighbor)) {
                continue;
            }

            // we try to call a neighbor in the bottom 1/3 by number of events created in the latest round, if
            // we fail to find one after 10 tries, we just call the last neighbor we find
            if (criticalQuorum.isInCriticalQuorum(neighbor) || i == MAXIMUM_NEIGHBORS_TO_QUERY - 1) {
                list.add(neighbor);
            }
        }

        return list;
    }

    /**
     * Observers halt requested dispatches. Causes gossip to permanently stop (until node reboot).
     *
     * @param reason
     * 		the reason why gossip is being stopped
     */
    public void haltRequestedObserver(final String reason) {
        gossipHalted.set(true);
        logger.info(FREEZE.getMarker(), "Gossip frozen, reason: {}", reason);
    }

    /**
     * Called by SyncUtils after a successful sync to check whether it should create an event or not
     *
     * @param otherId
     * 		the ID of the node we synced with
     * @param oneNodeFallenBehind
     * 		true if one of the nodes in the sync has fallen behind
     * @param eventsRead
     * 		the number of events read during the sync
     * @param eventsWritten
     * 		the number of events written during the sync
     * @return true if an event should be created, false otherwise
     */
    @Override
    public boolean shouldCreateEvent(
            final NodeId otherId, final boolean oneNodeFallenBehind, final int eventsRead, final int eventsWritten) {
        return shouldCreateEvent(new SyncResult(/*unused here*/ false, otherId, eventsRead, eventsWritten));
    }

    /**
     * Called by {@link SyncUtils} after a successful sync to check whether it should create an event or not
     *
     * @param info
     * 		information about the sync
     * @return true if an event should be created, false otherwise
     */
    @Override
    public boolean shouldCreateEvent(final SyncResult info) {
        // check EventCreationRules:
        // (1) if the node is not main node, it should not create events.
        // (2) if the number of freeze transactions is greater than 0, should create an event.
        // (3) during startup freeze, should not create an event.
        // (4) in freeze period, should not create an event.
        final EventCreationRuleResponse response = eventCreationRules.shouldCreateEvent();
        if (response == EventCreationRuleResponse.CREATE) {
            return true;
        } else if (response == EventCreationRuleResponse.DONT_CREATE) {
            return false;
        }

        // check 3: if neither node is part of the superMinority in the latest round, don't create an event
        if (!criticalQuorum.isInCriticalQuorum(info.getOtherId().getId())
                && !criticalQuorum.isInCriticalQuorum(selfId.getId())) {
            return false;
        }

        // check 4: staleEventPrevention
        if (settings.getStaleEventPreventionThreshold() > 0
                && info.getEventsRead() > settings.getStaleEventPreventionThreshold() * addressBook.getSize()) {
            // if we read too many events during this sync, we skip creating an event to reduce the probability of
            // having a stale event
            return false;
        }

        // if all checks pass, an event should be created
        return true;
    }

    /**
     * Notifies the sync manager that there was a successful sync
     */
    @Override
    public void successfulSync() {
        // TODO remove
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
    public List<Long> getNeededForFallenBehind() {
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
    public List<Long> getNeighborsForReconnect() {
        return fallenBehindManager.getNeighborsForReconnect();
    }

    @Override
    public boolean shouldReconnectFrom(final Long peerId) {
        return fallenBehindManager.shouldReconnectFrom(peerId);
    }

    @Override
    public int numReportedFallenBehind() {
        return fallenBehindManager.numReportedFallenBehind();
    }
}

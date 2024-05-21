/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.branching;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.EventDescriptor;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A standard implementation of {@link BranchDetector}.
 */
public class DefaultBranchDetector implements BranchDetector {

    private static final Logger logger = LogManager.getLogger(DefaultBranchDetector.class);

    /**
     * The current roster.
     */
    private final AddressBook currentRoster;

    /**
     * Keep a rate limited logger per node. We want to log when we see a node branching, but shouldn't allow a single
     * node to spam the logs.
     */
    private final Map<NodeId, RateLimitedLogger> nodeLoggers = new HashMap<>();

    /**
     * The current event window.
     */
    private EventWindow currentEventWindow;

    /**
     * The node IDs of the nodes in the network in sorted order.
     */
    private final List<NodeId> nodes = new ArrayList<>();

    /**
     * The most recent non-ancient events for each node (not present or null if there are none).
     */
    private final Map<NodeId, EventDescriptor> mostRecentEvents = new HashMap<>();

    /**
     * The most recent non-ancient branching event for each node (not present or null if there are none).
     */
    private final Map<NodeId, EventDescriptor> mostRecentBranchingEvents = new HashMap<>();

    /**
     * The total number of nodes that currently have a non-ancient branching event.
     */
    private int branchingCount;

    /**
     * The sum of the consensus weight for all nodes that currently have a non-ancient branching event.
     */
    private long branchingWeight;

    /**
     * Metrics for the branch detector.
     */
    private final BranchDetectorMetrics metrics;

    /**
     * Create a new branch detector.
     *
     * @param platformContext the platform context
     * @param currentRoster   the current roster
     */
    public DefaultBranchDetector(
            @NonNull final PlatformContext platformContext, @NonNull final AddressBook currentRoster) {

        this.currentRoster = Objects.requireNonNull(currentRoster);
        for (final NodeId nodeId : currentRoster.getNodeIdSet()) {
            nodes.add(nodeId);
            nodeLoggers.put(nodeId, new RateLimitedLogger(logger, platformContext.getTime(), Duration.ofMinutes(10)));
        }

        metrics = new BranchDetectorMetrics(platformContext);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addEvent(@NonNull final GossipEvent event) {
        if (currentEventWindow == null) {
            throw new IllegalStateException("Event window must be set before adding events");
        }

        if (currentEventWindow.isAncient(event)) {
            // Ignore ancient events.
            return;
        }

        final NodeId creator = event.getCreatorId();
        final EventDescriptor descriptor = event.getDescriptor();

        final EventDescriptor previousEvent = mostRecentEvents.get(creator);
        final EventDescriptor selfParent = event.getHashedData().getSelfParent();

        if (selfParent == null) {
            if (previousEvent != null) {
                // Event has no self parent even though an eligible self event exists.
                reportBranch(descriptor);
            }
        } else if (previousEvent != null && !previousEvent.equals(selfParent)) {
            // Event is not a child of the most recent event by the same creator.
            reportBranch(descriptor);
        }

        mostRecentEvents.put(creator, event.getDescriptor());
    }

    /**
     * Report the existence of a branching event.
     *
     * @param branchingEvent the branching event
     */
    private void reportBranch(@NonNull final EventDescriptor branchingEvent) {
        final NodeId creator = branchingEvent.getCreator();
        nodeLoggers.get(creator).error(EXCEPTION.getMarker(), "Node {} is branching", creator);

        final EventDescriptor previousBranchingEvent = mostRecentBranchingEvents.put(creator, branchingEvent);

        if (previousBranchingEvent == null) {
            // This node is now branching but wasn't previously.
            branchingCount++;
            branchingWeight += currentRoster.getAddress(creator).getWeight();
        }

        metrics.reportBranchingEvent();
        metrics.reportBranchingNodeCount(branchingCount);
        metrics.reportBranchingWeightFraction((double) branchingWeight / currentRoster.getTotalWeight());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateEventWindow(@NonNull final EventWindow eventWindow) {
        currentEventWindow = eventWindow;

        for (final NodeId nodeId : nodes) {
            final EventDescriptor mostRecentEvent = mostRecentEvents.get(nodeId);
            if (mostRecentEvent != null && eventWindow.isAncient(mostRecentEvent)) {
                // Event is ancient, forget it.
                mostRecentEvents.put(nodeId, null);
            }

            final EventDescriptor mostRecentBranchingEvent = mostRecentBranchingEvents.get(nodeId);
            if (mostRecentBranchingEvent != null && eventWindow.isAncient(mostRecentBranchingEvent)) {
                // Branching event is ancient, forget it.
                mostRecentBranchingEvents.put(nodeId, null);
                branchingCount--;
                branchingWeight -= currentRoster.getAddress(nodeId).getWeight();

                metrics.reportBranchingNodeCount(branchingCount);
                metrics.reportBranchingWeightFraction((double) branchingWeight / currentRoster.getTotalWeight());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        currentEventWindow = null;
        mostRecentEvents.clear();
        nodes.clear();
        branchingCount = 0;
        branchingWeight = 0;

        metrics.reportBranchingNodeCount(0);
        metrics.reportBranchingWeightFraction(0);
    }
}

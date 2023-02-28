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

package com.swirlds.platform.test.chatter.network;

import com.swirlds.common.system.NodeId;
import com.swirlds.platform.chatter.protocol.ChatterCore;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * A pass through class for events that also keeps track of:
 * <ul>
 *     <li>the number of self events created</li>
 *     <li>the number of events received by each peer</li>
 *     <li>the number of duplicated events received</li>
 * </ul>
 */
public class GossipEventTracker extends AbstractSimulatedEventPipeline<CountingChatterEvent> {

    private final NodeId nodeId;
    private final Map<Long, Queue<CountingChatterEvent>> eventsReceivedByCreator = new HashMap<>();
    private final Queue<CountingChatterEvent> selfEvents = new ArrayDeque<>();
    private final Set<Long> peerEventsReceived = new HashSet<>();
    private long duplicateEventCounter = 0;

    public GossipEventTracker(final NodeId nodeId) {
        this.nodeId = nodeId;
    }

    @Override
    public void addEvent(final CountingChatterEvent event) {
        final long creator = event.getCreator();
        if (creator == nodeId.getId()) {
            selfEvents.add(event);
        } else {
            if (!peerEventsReceived.add(event.getOrder())) {
                duplicateEventCounter++;
            } else {
                eventsReceivedByCreator.putIfAbsent(creator, new ArrayDeque<>());
                eventsReceivedByCreator.get(creator).add(event);
            }
        }
        next.addEvent(event);
    }

    @Override
    public void maybeHandleEvents(final ChatterCore<CountingChatterEvent> core) {
        // Do nothing, this class only tracks events that pass through it
    }

    @Override
    public void printResults() {
        final Map<Long, Integer> eventCounts = new HashMap<>();
        eventsReceivedByCreator.forEach((key, value) -> eventCounts.put(key, value.size()));

        final StringBuilder sb = new StringBuilder();
        sb.append(String.format("Node %s", nodeId.getId())).append("\n");
        sb.append("\tGossiped Events").append("\n");
        sb.append(String.format("\t\tCreated: %s%n", selfEvents.size()));
        sb.append(String.format("\t\tDuplicates Received: %s%n", duplicateEventCounter));
        eventCounts.forEach((k, v) -> sb.append(String.format("\t\tReceived %s events created by %s%n", v, k)));
        System.out.println(sb);
    }
}

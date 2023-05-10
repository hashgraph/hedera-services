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
import com.swirlds.platform.gossip.chatter.protocol.ChatterCore;
import com.swirlds.platform.test.chatter.network.framework.AbstractSimulatedEventPipeline;
import java.util.HashSet;
import java.util.Set;

/**
 * A pass through class for events that also keeps track of:
 * <ul>
 *     <li>the number of self events created</li>
 *     <li>the number of events received by each peer</li>
 *     <li>the number of duplicated events received</li>
 * </ul>
 */
public class EventDeduper extends AbstractSimulatedEventPipeline<CountingChatterEvent> {

    private final NodeId nodeId;
    private final Set<Long> eventsReceived = new HashSet<>();
    private long numDiscarded = 0;

    public EventDeduper(final NodeId nodeId) {
        this.nodeId = nodeId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addEvent(final CountingChatterEvent event) {
        if (!eventsReceived.contains(event.getOrder())) {
            eventsReceived.add(event.getOrder());
            next.addEvent(event);
        } else {
            numDiscarded++;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void maybeHandleEvents(final ChatterCore<CountingChatterEvent> core) {
        // Do nothing, this class only passes along events this class has not already encountered
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void printCurrentState() {
        String sb = String.format("\tDuplicate Events Discarded: %s%n", numDiscarded);
        System.out.println(sb);
    }
}

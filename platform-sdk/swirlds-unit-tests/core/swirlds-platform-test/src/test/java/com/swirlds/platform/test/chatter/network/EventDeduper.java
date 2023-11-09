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
import com.swirlds.common.system.events.EventDescriptor;
import com.swirlds.platform.gossip.chatter.protocol.ChatterCore;
import com.swirlds.platform.test.chatter.network.framework.AbstractSimulatedEventPipeline;
import com.swirlds.platform.test.chatter.network.framework.SimulatedChatterEvent;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * An event pipeline component that drops duplicate events.
 *
 * @param <T> the type of event
 */
public class EventDeduper<T extends SimulatedChatterEvent> extends AbstractSimulatedEventPipeline<T> {

    private final NodeId selfId;
    private final Set<EventDescriptor> eventsReceived = new HashSet<>();
    private long numDiscarded = 0;

    public EventDeduper(final NodeId nodeId) {
        this.selfId = nodeId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addEvent(final T event) {
        if (Objects.equals(event.getDescriptor().getCreator(), selfId)) {
            next.addEvent(event);
            return;
        }

        if (!eventsReceived.contains(event.getDescriptor())) {
            eventsReceived.add(event.getDescriptor());
            next.addEvent(event);
        } else {
            numDiscarded++;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void maybeHandleEvents(final ChatterCore<T> core) {
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

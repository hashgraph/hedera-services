/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.gossip.chatter.protocol.ChatterCore;
import com.swirlds.platform.test.chatter.network.framework.AbstractSimulatedEventPipeline;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Mimics an orphan buffer using {@link CountingChatterEvent}. It buffers the events and only allows an event to emitted
 * if one of the following is true:
 * <ul>
 *     <li>the event was created by self</li>
 *     <li>all events with a lower number have already been emitted</li>
 * </ul>
 */
public class InOrderOrphanBuffer extends AbstractSimulatedEventPipeline<CountingChatterEvent> {

    private final NodeId selfId;
    /** An ordered map of events to be emitted when the become linked */
    private final Map<Long, CountingChatterEvent> eventMap = new TreeMap<>();
    /** The order number of the event that was most recently linked */
    private long lastEventLinked = -1;
    /** An ordered set of self events that have been linked but are ahead of lastEventLinked */
    private final Set<Long> selfLinkedEvents = new TreeSet<>();

    public InOrderOrphanBuffer(final NodeId selfId) {
        this.selfId = selfId;
    }

    /**
     * Buffers the events for handling
     *
     * @param event the event to add
     */
    @Override
    public void addEvent(final CountingChatterEvent event) {
        eventMap.put(event.getOrder(), event);
    }

    /**
     * Emit all events that are considered "linked"
     *
     * @param core
     */
    @Override
    public void maybeHandleEvents(final ChatterCore<CountingChatterEvent> core) {
        boolean eventsLinked = true;
        while (eventsLinked) {
            eventsLinked = linkEvents(core);
        }
    }

    /**
     * Iterates through the event in the event map once, in order, and passes any events that are linked to chatter
     * core.
     *
     * @param core the chatter core instance
     * @return {@code true} if any events became linked and were sent to chatter core, {@code false} otherwise
     */
    private boolean linkEvents(final ChatterCore<CountingChatterEvent> core) {
        final Iterator<Map.Entry<Long, CountingChatterEvent>> iterator =
                eventMap.entrySet().iterator();
        final List<Long> toRemove = new ArrayList<>();
        while (iterator.hasNext()) {
            final CountingChatterEvent event = iterator.next().getValue();
            final boolean isSelfEvent = isSelfEvent(event);

            /*
            Self events are always linked immediately, because in real operations,
            no event can be created without first having its parent events.

            Other events become linked when their order is the next number after the
            last linked event order
             */
            if (isSelfEvent || event.getOrder() == lastEventLinked + 1) {
                toRemove.add(event.getOrder());

                if (isSelfEvent) {
                    selfLinkedEvents.add(event.getOrder());
                }

                advanceLastLinked(event);

                // Emit the event to chatter core, mimicking current intake thread behavior
                if (isSelfEvent) {
                    core.eventCreated(event);
                } else {
                    core.eventReceived(event);
                }
            }
        }
        toRemove.forEach(eventMap::remove);
        return !toRemove.isEmpty();
    }

    private void advanceLastLinked(final CountingChatterEvent event) {
        if (isSelfEvent(event)) {
            if (lastEventLinked + 1 == event.getOrder()) {
                lastEventLinked = event.getOrder();
            }
        } else {
            // if the linked event was created by a peer, it is possible
            // that the next event to be linked is a self event we already
            // have. In that case, the lastEventLinked pointer must be advanced,
            // possibly several events if there are several contiguous self events.
            lastEventLinked = event.getOrder();
            final Iterator<Long> selfLinkedIt = selfLinkedEvents.iterator();
            while (selfLinkedIt.hasNext()) {
                final long next = selfLinkedIt.next();
                if (next == lastEventLinked + 1) {
                    lastEventLinked = next;
                    selfLinkedIt.remove();
                } else if (next > lastEventLinked + 1) {
                    return;
                } else {
                    selfLinkedIt.remove();
                }
            }
        }
    }

    private boolean isSelfEvent(@NonNull final CountingChatterEvent event) {
        return Objects.equals(event.getCreator(), selfId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void printCurrentState() {
        final StringBuilder sb = new StringBuilder();
        sb.append("\tOrphan Buffer (").append(eventMap.size()).append(")").append("\n");
        sb.append("\t\tLast event linked: ").append(lastEventLinked).append("\n");
        for (final CountingChatterEvent e : eventMap.values()) {
            sb.append("\t\t").append(e).append("\n");
        }
        System.out.println(sb);
    }
}

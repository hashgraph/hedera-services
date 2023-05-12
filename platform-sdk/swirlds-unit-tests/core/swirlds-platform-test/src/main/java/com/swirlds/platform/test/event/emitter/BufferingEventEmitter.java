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

package com.swirlds.platform.test.event.emitter;

import com.swirlds.platform.test.event.IndexedEvent;
import com.swirlds.platform.test.event.generator.GraphGenerator;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * A base event emitter class that buffers events created by the {@link GraphGenerator}. Buffering events allows
 * subclasses to emit events in an order different from the order they are generated.
 *
 * @param <T>
 * 		the type of class extending this class
 */
public abstract class BufferingEventEmitter<T extends BufferingEventEmitter<T>> extends AbstractEventEmitter<T> {

    /**
     * The maximum number of events that this generator is permitted to buffer.
     */
    private static final int MAX_BUFFERED_EVENTS = 1_000;

    /**
     * A list of queues, each containing buffered events from an event source. There is one queue per source.
     * The queue at index 0 corresponds to the source with node ID 0, and so on. Events are strongly ordered within
     * an individual queue.
     */
    protected List<Queue<IndexedEvent>> events;

    /**
     * The number of events that are currently buffered by this generator.
     */
    protected int bufferedEvents;

    protected BufferingEventEmitter(final GraphGenerator<?> graphGenerator) {
        super(graphGenerator);

        this.events = new ArrayList<>(graphGenerator.getNumberOfSources());
        for (int index = 0; index < graphGenerator.getNumberOfSources(); index++) {
            this.events.add(new LinkedList<>());
        }
    }

    /**
     * Generates 0 or more events that are internally buffered. Events will be generated until there is at least one
     * buffered event from the given node ID or until the buffer fills up.
     */
    protected void attemptToGenerateEventFromNode(final int nodeID) {
        while (events.get(nodeID).isEmpty() && bufferedEvents < MAX_BUFFERED_EVENTS) {
            final IndexedEvent nextEvent = getGraphGenerator().generateEvent();
            events.get((int) nextEvent.getCreatorId()).add(nextEvent);
            bufferedEvents++;
        }
    }

    protected void eventEmittedFromBuffer() {
        bufferedEvents--;
        numEventsEmitted++;
    }

    protected void clearEvents() {
        events = new ArrayList<>(getGraphGenerator().getNumberOfSources());
        for (int index = 0; index < getGraphGenerator().getNumberOfSources(); index++) {
            events.add(new LinkedList<>());
        }
        bufferedEvents = 0;
    }

    /**
     * Checks to see if a given node is ready to emit an event:
     * <ul>
     *     <li>Events can not be emitted if all of their ancestors have not yet been emitted.</li>
     *     <li>Events can not be emitted if their generator index is not less than the current active checkpoint.</li>
     * </ul>
     */
    protected boolean isReadyToEmitEvent(final int nodeID) {
        final IndexedEvent potentialEvent = events.get(nodeID).peek();
        if (potentialEvent == null) {
            return false;
        }

        final IndexedEvent otherParent = (IndexedEvent) potentialEvent.getOtherParent();

        // if the checkpoint is active AND
        if (getCheckpoint() > getNumEventsEmitted()
                &&
                // this event must not be emitted until after the checkpoint
                potentialEvent.getGeneratorIndex() >= getCheckpoint()) {
            // do not emit it
            return false;
        }

        if (otherParent == null) {
            // There is no other parent, so no need to wait for it to be emitted
            return true;
        }

        final long otherNodeID = otherParent.getCreatorId();

        for (final IndexedEvent event : events.get((int) otherNodeID)) {
            if (event == otherParent) {
                // Our other parent has not yet been emitted
                return false;
            }
        }

        return true;
    }

    @Override
    public void reset() {
        super.reset();
        clearEvents();
    }
}

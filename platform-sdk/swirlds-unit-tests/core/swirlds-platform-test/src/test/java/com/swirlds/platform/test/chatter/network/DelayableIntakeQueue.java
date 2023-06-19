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

import com.swirlds.base.time.Time;
import com.swirlds.common.system.NodeId;
import com.swirlds.platform.gossip.chatter.protocol.ChatterCore;
import com.swirlds.platform.gossip.chatter.protocol.messages.ChatterEvent;
import com.swirlds.platform.test.chatter.network.framework.AbstractSimulatedEventPipeline;
import com.swirlds.platform.test.chatter.network.framework.SimulatedChatterEvent;
import com.swirlds.platform.test.simulated.config.NodeConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;

/**
 * Mimics the intake queue, including delay.
 *
 * @param <T>
 */
public class DelayableIntakeQueue<T extends SimulatedChatterEvent> extends AbstractSimulatedEventPipeline<T> {

    private static final Duration DEFAULT_DELAY = Duration.ZERO;
    private final NodeId nodeId;
    /** The instance of time used by the simulation */
    private final Time time;
    /** The amount of time each event must wait in the queue */
    private Duration intakeQueueDelay;
    /** The queue of intake tasks */
    private final Queue<IntakeQueueTask<T>> intakeQueue = new ArrayDeque<>();

    public DelayableIntakeQueue(final NodeId nodeId, final Time time) {
        this(nodeId, time, DEFAULT_DELAY);
    }

    public DelayableIntakeQueue(final NodeId nodeId, final Time time, final Duration intakeQueueDelay) {
        this.nodeId = nodeId;
        this.time = time;
        this.intakeQueueDelay = intakeQueueDelay;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void applyNodeConfig(final NodeConfig nodeConfig) {
        this.intakeQueueDelay = nodeConfig.intakeQueueDelay();
        // Update the delay for items currently in the queue
        for (final IntakeQueueTask<T> task : intakeQueue) {
            task.updateDelay(intakeQueueDelay);
        }
    }

    /**
     * Buffer the events in a queue
     *
     * @param event the event to add
     */
    @Override
    public void addEvent(final T event) {
        final Instant taskProcessTime = time.now().plusMillis(intakeQueueDelay.toMillis());
        intakeQueue.add(new IntakeQueueTask<>(event, time.now(), taskProcessTime));
    }

    /**
     * Release the events when they have spent enough time waiting in the queue
     *
     * @param core
     */
    @Override
    public void maybeHandleEvents(final ChatterCore<T> core) {
        for (final Iterator<IntakeQueueTask<T>> iterator = intakeQueue.iterator(); iterator.hasNext(); ) {
            final IntakeQueueTask<T> task = iterator.next();
            if (!time.now().isBefore(task.eventProcessTime())) {
                iterator.remove();
                next.addEvent(task.event());
            } else {
                // Stop iterating through events once the first task is not processable.
                // This maintains the order of tasks as they were added, in case the delay
                // decreases during the simulation.
                return;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void printCurrentState() {
        final StringBuilder sb = new StringBuilder();
        sb.append("\tIntake Queue (").append(intakeQueue.size()).append(")\n");
        for (final IntakeQueueTask<T> task : intakeQueue) {
            sb.append("\t\t").append(task).append("\n");
        }
        System.out.println(sb);
    }

    /**
     * A wrapper for events that sit in the intake queue.
     *
     * @param event            the event in the queue
     * @param eventProcessTime the time at which the event may exit the queue
     * @param <T>              the type of event
     */
    private static class IntakeQueueTask<T extends ChatterEvent> {

        private final T event;
        private final Instant insertionTime;
        private Instant eventProcessTime;

        public IntakeQueueTask(final T event, Instant insertionTime, Instant eventProcessTime) {
            this.event = event;
            this.insertionTime = insertionTime;
            this.eventProcessTime = eventProcessTime;
        }

        public void updateDelay(final Duration delay) {
            eventProcessTime = insertionTime.plus(delay);
        }

        public Instant eventProcessTime() {
            return eventProcessTime;
        }

        public T event() {
            return event;
        }

        @Override
        public String toString() {
            return event + ", " + "process at: " + eventProcessTime;
        }
    }
}

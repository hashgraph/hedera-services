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

import com.swirlds.common.time.Time;
import com.swirlds.platform.chatter.protocol.ChatterCore;
import com.swirlds.platform.chatter.protocol.messages.ChatterEvent;
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
public class DelayedIntakeQueue<T extends SimulatedChatterEvent> extends AbstractSimulatedEventPipeline<T> {

    private final Time time;
    private Duration intakeQueueDelay;
    private final Queue<IntakeQueueTask<T>> intakeQueue = new ArrayDeque<>();

    public DelayedIntakeQueue(final Time time, final Duration intakeQueueDelay) {
        this.time = time;
        this.intakeQueueDelay = intakeQueueDelay;
    }

    public void setDelay(final Duration intakeQueueDelay) {
        this.intakeQueueDelay = intakeQueueDelay;
    }

    /**
     * Buffer the events in a queue
     *
     * @param event the event to add
     */
    @Override
    public void addEvent(final T event) {
        final Instant taskProcessTime = time.now().plusMillis(intakeQueueDelay.toMillis());
        intakeQueue.add(new IntakeQueueTask<>(event, taskProcessTime));
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
            }
        }
    }

    @Override
    public void printResults() {
        final StringBuilder sb = new StringBuilder();
        sb.append("\tIntake Queue (").append(intakeQueue.size()).append(")\n");
        for (final IntakeQueueTask<T> task : intakeQueue) {
            sb.append("\t\t").append(task).append("\n");
        }
        System.out.println(sb);
    }

    record IntakeQueueTask<T extends ChatterEvent>(T event, Instant eventProcessTime) {
        @Override
        public String toString() {
            return event + ", " + "process at: " + eventProcessTime;
        }
    }
}

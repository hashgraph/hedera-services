/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components;

import com.swirlds.base.time.Time;
import com.swirlds.logging.LogMarker;
import com.swirlds.platform.event.CreateEventTask;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.ValidEvent;
import com.swirlds.platform.event.validation.EventValidator;
import com.swirlds.platform.intake.IntakeCycleStats;
import com.swirlds.platform.metrics.EventIntakeMetrics;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class is responsible for dispatching tasks to the {@link EventCreator} and {@link EventValidator}.
 */
public class EventTaskDispatcher {
    private static final Logger logger = LogManager.getLogger(EventTaskDispatcher.class);

    /** An {@link EventValidator} */
    private final EventValidator eventValidator;

    /** Responsible for creating all new events that originate on this node */
    private final EventCreator eventCreator;

    /** Handles events that are known to be valid */
    private final Consumer<GossipEvent> validEventHandler;

    /**
     * A statistics accumulator for hashgraph-related quantities, used here to record
     * time to taken to process a task to hashgraph event
     */
    private final EventIntakeMetrics eventIntakeMetrics;

    private final IntakeCycleStats cycleStats;
    private final Time time;

    /**
     * Constructor
     *
     * @param time
     * 		provides the wall clock time
     * @param eventValidator
     * 		an event validator
     * @param eventCreator
     * 		an event creator
     * @param validEventHandler
     * 		handles already validated events
     * @param eventIntakeMetrics
     * 		dispatching metrics
     */
    public EventTaskDispatcher(
            final Time time,
            final EventValidator eventValidator,
            final EventCreator eventCreator,
            final Consumer<GossipEvent> validEventHandler,
            final EventIntakeMetrics eventIntakeMetrics,
            final IntakeCycleStats cycleStats) {
        this.time = time;
        this.eventValidator = eventValidator;
        this.eventCreator = eventCreator;
        this.validEventHandler = validEventHandler;
        this.eventIntakeMetrics = eventIntakeMetrics;
        this.cycleStats = cycleStats;
    }

    /**
     * Dispatch a single {@code EventIntakeTask} instance. This routine validates
     * the given task and adds it to the hashgraph via {@code EventIntake.addEvent}
     *
     * @param eventIntakeTask
     * 		A task to be dispatched
     */
    public void dispatchTask(final EventIntakeTask eventIntakeTask) {
        cycleStats.startedIntake();
        final long start = time.nanoTime();
        // Moving validation to inline model
        if (eventIntakeTask instanceof GossipEvent task) {
            eventValidator.validateEvent(task);
        } else if (eventIntakeTask instanceof ValidEvent task) {
            validEventHandler.accept(task.event());
        } else if (eventIntakeTask instanceof CreateEventTask task) {
            eventCreator.createEvent(task.getOtherId());
        } else {
            logger.error(LogMarker.EXCEPTION.getMarker(), "Unknown instance type: {}", eventIntakeTask.getClass());
        }
        eventIntakeMetrics.processedEventTask(start);
        cycleStats.doneIntake();
    }
}

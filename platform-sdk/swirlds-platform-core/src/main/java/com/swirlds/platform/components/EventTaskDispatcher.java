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
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.validation.EventValidator;
import com.swirlds.platform.intake.IntakeCycleStats;
import com.swirlds.platform.metrics.EventIntakeMetrics;

/**
 * This class is responsible for TODO
 */
public class EventTaskDispatcher { // TODO this class can finally die perhaps

    /** An {@link EventValidator} */
    private final EventValidator eventValidator;

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
     * @param eventIntakeMetrics
     * 		dispatching metrics
     */
    public EventTaskDispatcher(
            final Time time,
            final EventValidator eventValidator,
            final EventIntakeMetrics eventIntakeMetrics,
            final IntakeCycleStats cycleStats) {
        this.time = time;
        this.eventValidator = eventValidator;
        this.eventIntakeMetrics = eventIntakeMetrics;
        this.cycleStats = cycleStats;
    }

    /**
     * TODO
     */
    public void dispatchTask(final GossipEvent event) {
        cycleStats.startedIntake();
        final long start = time.nanoTime();
        eventValidator.validateEvent(event);
        eventIntakeMetrics.processedEventTask(start);
        cycleStats.doneIntake();
    }
}

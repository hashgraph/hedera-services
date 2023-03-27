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

package com.swirlds.platform.intake;

import com.swirlds.base.time.Time;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.platform.stats.cycle.AccumulatedCycleMetrics;
import com.swirlds.platform.stats.cycle.CycleDefinition;
import com.swirlds.platform.stats.cycle.CycleTracker;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Statistics that track time spent in various phases of event intake
 */
public class IntakeCycleStats {
    private final CycleDefinition unlinked = new CycleDefinition(
            "intake",
            "intake-unlinked",
            List.of(
                    Pair.of("validation/creation", "time to create or validate an event"),
                    Pair.of("received", "time dispatch received event"),
                    Pair.of("linking", "time link the event to its parents"),
                    Pair.of("adding", "time add all linked events")));
    private final CycleDefinition linked = new CycleDefinition(
            "intake",
            "intake-linked",
            List.of(
                    Pair.of("validation-2", "time of second phase of event validation"),
                    Pair.of("preConsensus", "time dispatch pre-consensus event"),
                    Pair.of("consensus", "time add event to consensus"),
                    Pair.of("added", "time dispatch event added"),
                    Pair.of("round", "time dispatch consensus round"),
                    Pair.of("stale", "time dispatch stale event")));
    private final CycleTracker unlinkedEventTiming;
    private final CycleTracker eventIntakeTiming;

    public IntakeCycleStats(final Time time, final Metrics metrics) {
        unlinkedEventTiming = new CycleTracker(time, new AccumulatedCycleMetrics(metrics, unlinked));
        eventIntakeTiming = new CycleTracker(time, new AccumulatedCycleMetrics(metrics, linked));
    }

    public void startedIntake() {
        unlinkedEventTiming.startCycle();
    }

    /** An unlinked event it added to gossip */
    public void receivedUnlinkedEvent() {
        unlinkedEventTiming.intervalEnded(0);
    }

    /** An unlinked event has been dispatched as received */
    public void dispatchedReceived() {
        unlinkedEventTiming.intervalEnded(1);
    }

    /** Event linking is done */
    public void doneLinking() {
        unlinkedEventTiming.intervalEnded(2);
    }

    /** Done adding a linked event */
    public void doneIntake() {
        unlinkedEventTiming.cycleEnded();
    }

    /** Intake of a linked event is starting */
    public void startIntakeAddEvent() {
        eventIntakeTiming.startCycle();
    }

    /** Intake of a linked event is done */
    public void doneIntakeAddEvent() {
        eventIntakeTiming.cycleEnded();
    }

    /** Linked event validation is done */
    public void doneValidation() {
        eventIntakeTiming.intervalEnded(0);
    }

    /** A linked event has been dispatched before adding to consensus */
    public void dispatchedPreConsensus() {
        eventIntakeTiming.intervalEnded(1);
    }

    /** A linked event has been added to consensus */
    public void addedToConsensus() {
        eventIntakeTiming.intervalEnded(2);
    }

    /** A linked event has been dispatched after adding to consensus */
    public void dispatchedAdded() {
        eventIntakeTiming.intervalEnded(3);
    }

    /** A linked consensus round has been dispatched */
    public void dispatchedRound() {
        eventIntakeTiming.intervalEnded(4);
    }

    /** A linked stale event has been dispatched */
    public void dispatchedStale() {
        eventIntakeTiming.intervalEnded(5);
    }
}

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

package com.swirlds.platform.metrics;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_10_1;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_10_2;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_14_2;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_16_2;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_9_6;
import static com.swirlds.common.metrics.Metrics.INTERNAL_CATEGORY;
import static com.swirlds.common.metrics.Metrics.PLATFORM_CATEGORY;
import static com.swirlds.common.units.UnitConstants.NANOSECONDS_TO_SECONDS;

import com.swirlds.base.time.Time;
import com.swirlds.common.metrics.LongAccumulator;
import com.swirlds.common.metrics.LongGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.system.NodeId;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.observers.StaleEventObserver;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.LongBinaryOperator;

/**
 * Collection of metrics related to event intake
 */
public class EventIntakeMetrics implements StaleEventObserver {

    private static final SpeedometerMetric.Config RESCUED_EVENTS_PER_SECOND_CONFIG = new SpeedometerMetric.Config(
                    INTERNAL_CATEGORY, "rescuedEv/sec")
            .withDescription("number of events per second generated to prevent stale events")
            .withFormat(FORMAT_16_2);
    private final SpeedometerMetric rescuedEventsPerSecond;

    private static final SpeedometerMetric.Config DUPLICATE_EVENTS_PER_SECOND_CONFIG = new SpeedometerMetric.Config(
                    INTERNAL_CATEGORY, "dupEv/sec")
            .withDescription("number of events received per second that are already known")
            .withFormat(FORMAT_14_2);
    private final SpeedometerMetric duplicateEventsPerSecond;

    private static final RunningAverageMetric.Config AVG_DUPLICATE_PERCENT_CONFIG = new RunningAverageMetric.Config(
                    PLATFORM_CATEGORY, "dupEv%")
            .withDescription("percentage of events received that are already known")
            .withFormat(FORMAT_10_2);
    private final RunningAverageMetric avgDuplicatePercent;

    private static final SpeedometerMetric.Config TIME_FRAC_ADD_CONFIG = new SpeedometerMetric.Config(
                    INTERNAL_CATEGORY, "timeFracAdd")
            .withDescription(
                    "fraction of each second spent adding an event to the hashgraph and " + "finding consensus")
            .withFormat(FORMAT_9_6);
    private final SpeedometerMetric timeFracAdd;

    private static final RunningAverageMetric.Config SHOULD_CREATE_EVENT_CONFIG =
            new RunningAverageMetric.Config(INTERNAL_CATEGORY, "shouldCreateEvent").withFormat(FORMAT_10_1);
    private final RunningAverageMetric shouldCreateEvent;

    private static final LongAccumulator.Config STALE_EVENTS_CONFIG = new LongAccumulator.Config(INTERNAL_CATEGORY, "staleEvents")
            .withAccumulator(Long::sum)
            .withDescription("number of stale events");
    private final LongAccumulator staleEventCount;

    private static final LongAccumulator.Config STALE_SELF_EVENTS_CONFIG = new LongAccumulator.Config(
                    INTERNAL_CATEGORY, "staleSelfEvents")
            .withAccumulator(Long::sum)
            .withDescription("number of stale self events");
    private final LongAccumulator staleSelfEventCount;

    private static final LongAccumulator.Config STALE_APP_TRANSACTIONS_CONFIG = new LongAccumulator.Config(
                    INTERNAL_CATEGORY, "staleAppTransactions")
            .withAccumulator(Long::sum)
            .withDescription("number of application transactions in stale events");
    private final LongAccumulator staleAppTransactionCount;

    private static final LongAccumulator.Config STALE_SELF_APP_TRANSACTIONS_CONFIG = new LongAccumulator.Config(
                    INTERNAL_CATEGORY, "staleSelfAppTransactions")
            .withAccumulator(Long::sum)
            .withDescription("number of application transactions in stale self events");
    private final LongAccumulator staleSelfAppTransactionCount;

    private static final LongAccumulator.Config STALE_SYSTEM_TRANSACTIONS_CONFIG = new LongAccumulator.Config(
                    INTERNAL_CATEGORY, "staleSystemTransactions")
            .withAccumulator(Long::sum)
            .withDescription("number of system transactions in stale events");
    private final LongAccumulator staleSystemTransactionCount;

    private static final LongAccumulator.Config STALE_SELF_SYSTEM_TRANSACTIONS_CONFIG = new LongAccumulator.Config(
                    INTERNAL_CATEGORY, "staleSelfSystemTransactions")
            .withAccumulator(Long::sum)
            .withDescription("number of system transactions in stale self events");
    private final LongAccumulator staleSelfSystemTransactionCount;

    private final Time time;
    private final NodeId selfId;

    /**
     * Constructor of {@code EventIntakeMetrics}
     *
     * @param metrics a reference to the metrics-system
     * @param time    provides wall clock time
     * @param selfId  the ID of this node
     * @throws IllegalArgumentException if {@code metrics} is {@code null}
     */
    public EventIntakeMetrics(@NonNull final Metrics metrics, @NonNull final Time time, @NonNull final NodeId selfId) {

        this.time = Objects.requireNonNull(time);
        this.selfId = Objects.requireNonNull(selfId);

        rescuedEventsPerSecond = metrics.getOrCreate(RESCUED_EVENTS_PER_SECOND_CONFIG);
        duplicateEventsPerSecond = metrics.getOrCreate(DUPLICATE_EVENTS_PER_SECOND_CONFIG);
        avgDuplicatePercent = metrics.getOrCreate(AVG_DUPLICATE_PERCENT_CONFIG);
        timeFracAdd = metrics.getOrCreate(TIME_FRAC_ADD_CONFIG);
        shouldCreateEvent = metrics.getOrCreate(SHOULD_CREATE_EVENT_CONFIG);
        staleEventCount = metrics.getOrCreate(STALE_EVENTS_CONFIG);
        staleSelfEventCount = metrics.getOrCreate(STALE_SELF_EVENTS_CONFIG);
        staleAppTransactionCount = metrics.getOrCreate(STALE_APP_TRANSACTIONS_CONFIG);
        staleSelfAppTransactionCount = metrics.getOrCreate(STALE_SELF_APP_TRANSACTIONS_CONFIG);
        staleSystemTransactionCount = metrics.getOrCreate(STALE_SYSTEM_TRANSACTIONS_CONFIG);
        staleSelfSystemTransactionCount = metrics.getOrCreate(STALE_SELF_SYSTEM_TRANSACTIONS_CONFIG);
    }

    /**
     * Update a statistics accumulator whenever this node creates an event with an other-parent that has no children.
     * (The OP is "rescued".)
     */
    public void rescuedEvent() {
        rescuedEventsPerSecond.cycle();
    }

    /**
     * Update a statistics accumulator when a duplicate event has been detected.
     */
    public void duplicateEvent() {
        duplicateEventsPerSecond.cycle();
        // move toward 100%
        avgDuplicatePercent.update(100);
    }

    /**
     * Update a statistics accumulator when a non-duplicate event has been detected
     */
    public void nonDuplicateEvent() {
        // move toward 0%
        avgDuplicatePercent.update(0);
    }

    /**
     * Update event task statistics
     *
     * @param startTime a start time, in nanoseconds
     */
    public void processedEventTask(final long startTime) {
        // nanoseconds spent adding to hashgraph
        timeFracAdd.update(((double) time.nanoTime() - startTime) * NANOSECONDS_TO_SECONDS);
    }

    /**
     * Notifies the stats that the event creation phase has entered
     *
     * @param shouldCreateEvent did the sync manager tell us to create an event?
     */
    public void eventCreation(final boolean shouldCreateEvent) {
        this.shouldCreateEvent.update(shouldCreateEvent ? 1 : 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void staleEvent(@NonNull final EventImpl event) {
        final int applicationTransactionCount = event.getNumAppTransactions();
        final int systemTransactionCount = event.getNumTransactions() - applicationTransactionCount;

        staleEventCount.update(1);
        staleAppTransactionCount.update(applicationTransactionCount);
        staleSystemTransactionCount.update(systemTransactionCount);

        if (event.getCreatorId().equals(selfId)) {
            staleSelfEventCount.update(1);
            staleSelfAppTransactionCount.update(applicationTransactionCount);
            staleSelfSystemTransactionCount.update(systemTransactionCount);
        }
    }
}

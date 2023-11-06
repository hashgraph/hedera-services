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

import static com.swirlds.common.metrics.FloatFormats.FORMAT_10_2;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_14_2;
import static com.swirlds.common.metrics.Metrics.INTERNAL_CATEGORY;
import static com.swirlds.common.metrics.Metrics.PLATFORM_CATEGORY;

import com.swirlds.common.metrics.LongAccumulator;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.system.NodeId;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.observers.StaleEventObserver;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Collection of metrics related to event intake
 */
public class EventIntakeMetrics implements StaleEventObserver {
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

    private static final LongAccumulator.Config STALE_EVENTS_CONFIG = new LongAccumulator.Config(
                    INTERNAL_CATEGORY, "staleEvents")
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

    private final NodeId selfId;

    /**
     * Constructor of {@code EventIntakeMetrics}
     *
     * @param metrics a reference to the metrics-system
     * @param selfId  the ID of this node
     * @throws IllegalArgumentException if {@code metrics} is {@code null}
     */
    public EventIntakeMetrics(@NonNull final Metrics metrics, @NonNull final NodeId selfId) {
        this.selfId = Objects.requireNonNull(selfId);

        duplicateEventsPerSecond = metrics.getOrCreate(DUPLICATE_EVENTS_PER_SECOND_CONFIG);
        avgDuplicatePercent = metrics.getOrCreate(AVG_DUPLICATE_PERCENT_CONFIG);
        staleEventCount = metrics.getOrCreate(STALE_EVENTS_CONFIG);
        staleSelfEventCount = metrics.getOrCreate(STALE_SELF_EVENTS_CONFIG);
        staleAppTransactionCount = metrics.getOrCreate(STALE_APP_TRANSACTIONS_CONFIG);
        staleSelfAppTransactionCount = metrics.getOrCreate(STALE_SELF_APP_TRANSACTIONS_CONFIG);
        staleSystemTransactionCount = metrics.getOrCreate(STALE_SYSTEM_TRANSACTIONS_CONFIG);
        staleSelfSystemTransactionCount = metrics.getOrCreate(STALE_SELF_SYSTEM_TRANSACTIONS_CONFIG);
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

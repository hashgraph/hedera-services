/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.common.units.TimeUnit.UNIT_MILLISECONDS;
import static com.swirlds.common.units.TimeUnit.UNIT_NANOSECONDS;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.RunningAverageMetric;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Supplier;

/**
 * Encapsulates metrics for EventIntake
 */
public class EventIntakeMetrics {

    private final RunningAverageMetric.Config timeWaitingForTransactionPrehandlingConfig =
            new RunningAverageMetric.Config("platform", "timeWaitingForTransactionPrehandling")
                    .withDescription("The time spent waiting for a transaction to be prehandled.")
                    .withUnit("ms");
    private final RunningAverageMetric timeWaitingForTransactionPrehandling;

    /**
     * Constructor.
     *
     * @param platformContext                       the platform context
     * @param prehandleTransactionQueueSizeSupplier provides the current size of the transaction prehandle queue
     */
    public EventIntakeMetrics(
            @NonNull final PlatformContext platformContext,
            @NonNull Supplier<Integer> prehandleTransactionQueueSizeSupplier) {

        final FunctionGauge.Config<Integer> prehandleTransactionQueueSizeConfig = new FunctionGauge.Config<>(
                        "platform",
                        "prehandleTransactionQueueSize",
                        Integer.class,
                        prehandleTransactionQueueSizeSupplier)
                .withDescription("The number of events in the app prehandle transaction queue.");
        platformContext.getMetrics().getOrCreate(prehandleTransactionQueueSizeConfig);

        timeWaitingForTransactionPrehandling =
                platformContext.getMetrics().getOrCreate(timeWaitingForTransactionPrehandlingConfig);
    }

    /**
     * Report an amount of time that was waited for transactions in a round to be fully prehandled.
     */
    public void reportTimeWaitedForPrehandlingTransaction(final long nanoseconds) {
        timeWaitingForTransactionPrehandling.update(UNIT_NANOSECONDS.convertTo(nanoseconds, UNIT_MILLISECONDS));
    }
}

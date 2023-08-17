/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.eventhandling;

import static com.swirlds.common.metrics.Metrics.PLATFORM_CATEGORY;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.SpeedometerMetric;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Supplier;

/**
 * Encapsulates metrics for the transaction pool.
 */
public class TransactionPoolMetrics {

    private static final SpeedometerMetric.Config ACCEPTED_APP_TRANSACTIONS_CONFIG = new SpeedometerMetric.Config(
                    "platform", "acceptedAppTransactions")
            .withDescription("Cycled when an app transaction is submitted to the transaction pool and accepted.");
    private final SpeedometerMetric acceptedAppTransactions;

    private static final SpeedometerMetric.Config REJECTED_APP_TRANSACTIONS_CONFIG = new SpeedometerMetric.Config(
                    "platform", "rejectedAppTransactions")
            .withDescription("Cycled when an app transaction is submitted to the transaction pool and not accepted.");
    private final SpeedometerMetric rejectedAppTransactions;

    private static final SpeedometerMetric.Config SUBMITTED_PLATFORM_TRANSACTIONS_CONFIG = new SpeedometerMetric.Config(
                    "platform", "submittedPlatformTransactions")
            .withDescription(
                    "Cycled when an app transaction is submitted (platform transactions are always accepted).");
    private final SpeedometerMetric submittedPlatformTransactions;

    /**
     * Create metrics for the transaction pool.
     *
     * @param platformContext the platform context
     */
    public TransactionPoolMetrics(
            @NonNull final PlatformContext platformContext,
            @NonNull final Supplier<Integer> getBufferedTransactionCount,
            @NonNull final Supplier<Integer> getPriorityBufferedTransactionCount) {

        final Metrics metrics = platformContext.getMetrics();

        acceptedAppTransactions = metrics.getOrCreate(ACCEPTED_APP_TRANSACTIONS_CONFIG);
        rejectedAppTransactions = metrics.getOrCreate(REJECTED_APP_TRANSACTIONS_CONFIG);
        submittedPlatformTransactions = metrics.getOrCreate(SUBMITTED_PLATFORM_TRANSACTIONS_CONFIG);

        metrics.getOrCreate(new FunctionGauge.Config<>(
                        PLATFORM_CATEGORY, "bufferedTransactions", Integer.class, getBufferedTransactionCount)
                .withDescription("transEvent queue size")
                .withUnit("count"));
        metrics.getOrCreate(new FunctionGauge.Config<>(
                        PLATFORM_CATEGORY,
                        "bufferedPriorityTransactions",
                        Integer.class,
                        getPriorityBufferedTransactionCount)
                .withDescription("priorityTransEvent queue size")
                .withUnit("count"));
    }

    /**
     * Record that an app transaction was accepted.
     */
    public void recordAcceptedAppTransaction() {
        acceptedAppTransactions.cycle();
    }

    /**
     * Record that an app transaction was rejected.
     */
    public void recordRejectedAppTransaction() {
        rejectedAppTransactions.cycle();
    }

    /**
     * Record that a platform transaction was submitted.
     */
    public void recordSubmittedPlatformTransaction() {
        submittedPlatformTransactions.cycle();
    }
}

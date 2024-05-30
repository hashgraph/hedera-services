/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.resubmitter;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.SpeedometerMetric;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Encapsulates metrics for the {@link TransactionResubmitter}.
 */
public class TransactionResubmitterMetrics {

    private static final SpeedometerMetric.Config RESUBMITTED_SYSTEM_TRANSACTIONS_CONFIG = new SpeedometerMetric.Config(
                    "platform", "resubmittedSystemTransactions")
            .withUnit("hz")
            .withDescription(
                    "number of system transactions that have been resubmitted due to the host event becoming stale");
    private final SpeedometerMetric resubmittedSystemTransactions;

    private static final SpeedometerMetric.Config ABANDONED_SYSTEM_TRANSACTIONS_CONFIG = new SpeedometerMetric.Config(
                    "platform", "abandonedSystemTransactions")
            .withUnit("hz")
            .withDescription(
                    "number of system transactions that have been abandoned after the host event became stale");
    private final SpeedometerMetric abandonedSystemTransactions;

    /**
     * Constructor.
     *
     * @param platformContext the platform context
     */
    public TransactionResubmitterMetrics(@NonNull final PlatformContext platformContext) {
        resubmittedSystemTransactions =
                platformContext.getMetrics().getOrCreate(RESUBMITTED_SYSTEM_TRANSACTIONS_CONFIG);
        abandonedSystemTransactions = platformContext.getMetrics().getOrCreate(ABANDONED_SYSTEM_TRANSACTIONS_CONFIG);
    }

    /**
     * Report that a system transaction has been resubmitted.
     */
    public void reportResubmittedSystemTransaction() {
        resubmittedSystemTransactions.cycle();
    }

    /**
     * Report that a system transaction has been abandoned.
     */
    public void reportAbandonedSystemTransaction() {
        abandonedSystemTransactions.cycle();
    }
}

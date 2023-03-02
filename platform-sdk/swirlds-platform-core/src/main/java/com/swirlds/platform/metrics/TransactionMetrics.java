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

package com.swirlds.platform.metrics;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_1_3;
import static com.swirlds.common.metrics.Metrics.INTERNAL_CATEGORY;

import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.system.PlatformStatNames;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import com.swirlds.platform.SwirldTransactionSubmitter;
import com.swirlds.platform.stats.AverageStat;

/**
 * Provides access to statistics relevant to transactions.
 */
public class TransactionMetrics {

    private final AverageStat avgTransSubmitMicros;

    /**
     * Constructor of {@code TransactionMetrics}
     *
     * @param metrics
     * 		a reference to the metrics-system
     * @throws IllegalArgumentException
     * 		if {@code metrics} is {@code null}
     */
    public TransactionMetrics(final Metrics metrics) {
        avgTransSubmitMicros = new AverageStat(
                metrics,
                INTERNAL_CATEGORY,
                PlatformStatNames.TRANS_SUBMIT_MICROS,
                "average time spent submitting a user transaction (in microseconds)",
                FORMAT_1_3,
                AverageStat.WEIGHT_VOLATILE);
    }

    /**
     * Called by {@link SwirldTransactionSubmitter#submitTransaction(SwirldTransaction)} when a transaction passes initial
     * checks and is offered to the transaction pool.
     */
    public void updateTransSubmitMicros(final long microseconds) {
        avgTransSubmitMicros.update(microseconds);
    }
}

/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

import static com.swirlds.base.units.UnitConstants.NANOSECONDS_TO_MICROSECONDS;

import com.swirlds.base.function.BooleanFunction;
import com.swirlds.common.config.TransactionConfig;
import com.swirlds.platform.metrics.TransactionMetrics;
import com.swirlds.platform.system.status.PlatformStatus;
import com.swirlds.platform.system.status.PlatformStatusGetter;
import com.swirlds.platform.system.transaction.SwirldTransaction;

/**
 * Submits valid transactions received from the application to a consumer. Invalid transactions are rejected.
 */
public class SwirldTransactionSubmitter {

    private final PlatformStatusGetter platformStatusGetter;
    private final TransactionConfig transactionConfig;
    private final BooleanFunction<SwirldTransaction> addToTransactionPool;
    private final TransactionMetrics transactionMetrics;

    /**
     * Creates a new instance.
     *
     * @param platformStatusGetter
     * 		supplier of the current status of the platform
     * @param transactionConfig
     * 		provider of static settings
     * @param addToTransactionPool
     * 		a function that adds the transaction to the transaction pool, if room is available
     * @param transactionMetrics
     * 		stats relevant to transactions
     */
    public SwirldTransactionSubmitter(
            final PlatformStatusGetter platformStatusGetter,
            final TransactionConfig transactionConfig,
            final BooleanFunction<SwirldTransaction> addToTransactionPool,
            final TransactionMetrics transactionMetrics) {

        this.platformStatusGetter = platformStatusGetter;
        this.transactionConfig = transactionConfig;
        this.addToTransactionPool = addToTransactionPool;
        this.transactionMetrics = transactionMetrics;
    }

    /**
     * Submits a transaction to the consumer if it passes validity checks.
     *
     * @param trans
     * 		the transaction to submit
     * @return true if the transaction passed all validity checks and was accepted by the consumer
     */
    public boolean submitTransaction(final SwirldTransaction trans) {

        // if the platform is not active, it is better to reject transactions submitted by the app
        if (platformStatusGetter.getCurrentStatus() != PlatformStatus.ACTIVE) {
            return false;
        }

        if (trans == null) {
            return false;
        }

        // check if system transaction serialized size is above the required threshold
        if (trans.getSize() > transactionConfig.transactionMaxBytes()) {
            return false;
        }

        final long start = System.nanoTime();
        final boolean success = addToTransactionPool.apply(trans);
        transactionMetrics.updateTransSubmitMicros((long) ((System.nanoTime() - start) * NANOSECONDS_TO_MICROSECONDS));

        return success;
    }
}

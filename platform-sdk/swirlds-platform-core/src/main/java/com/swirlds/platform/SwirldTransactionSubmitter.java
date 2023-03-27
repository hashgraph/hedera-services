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

package com.swirlds.platform;

import static com.swirlds.common.utility.Units.NANOSECONDS_TO_MICROSECONDS;

import com.swirlds.base.function.BooleanFunction;
import com.swirlds.common.system.PlatformStatus;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import com.swirlds.platform.metrics.TransactionMetrics;
import java.util.function.Supplier;

/**
 * Submits valid transactions received from the application to a consumer. Invalid transactions are rejected.
 */
public class SwirldTransactionSubmitter {

    private final Supplier<PlatformStatus> platformStatusSupplier;
    private final boolean isZeroStakeNode;
    private final SettingsProvider settings;
    private final BooleanFunction<SwirldTransaction> addToTransactionPool;
    private final TransactionMetrics transactionMetrics;

    /**
     * Creates a new instance.
     *
     * @param platformStatusSupplier
     * 		supplier of the current status of the platform
     * @param settings
     * 		provider of static settings
     * @param isZeroStakeNode
     * 		true is this node is a zero-stake node
     * @param addToTransactionPool
     * 		a function that adds the transaction to the transaction pool, if room is available
     * @param transactionMetrics
     * 		stats relevant to transactions
     */
    public SwirldTransactionSubmitter(
            final Supplier<PlatformStatus> platformStatusSupplier,
            final SettingsProvider settings,
            final boolean isZeroStakeNode,
            final BooleanFunction<SwirldTransaction> addToTransactionPool,
            final TransactionMetrics transactionMetrics) {

        this.platformStatusSupplier = platformStatusSupplier;
        this.settings = settings;
        this.isZeroStakeNode = isZeroStakeNode;
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
        if (platformStatusSupplier.get() != PlatformStatus.ACTIVE) {
            return false;
        }

        // create a transaction to be added to the next Event when it is created.
        // The "system" boolean is set to false, because this is an app-generated transaction.
        // Refuse to create any type of transaction if this node has zero stake
        if (isZeroStakeNode) {
            return false;
        }

        if (trans == null) {
            return false;
        }

        // check if system transaction serialized size is above the required threshold
        if (trans.getSize() > settings.getTransactionMaxBytes()) {
            return false;
        }

        final long start = System.nanoTime();
        final boolean success = addToTransactionPool.apply(trans);
        transactionMetrics.updateTransSubmitMicros((long) ((System.nanoTime() - start) * NANOSECONDS_TO_MICROSECONDS));

        return success;
    }
}

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

package com.swirlds.platform.event.stale;

import static com.swirlds.metrics.api.Metrics.INTERNAL_CATEGORY;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.event.PlatformEvent;
import com.swirlds.common.transaction.Transaction;
import com.swirlds.metrics.api.LongAccumulator;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Iterator;

/**
 * Collection of metrics related to stale events and transactions
 */
public class StaleEventDetectorMetrics {

    private static final LongAccumulator.Config STALE_EVENTS_CONFIG = new LongAccumulator.Config(
                    INTERNAL_CATEGORY, "staleEvents")
            .withAccumulator(Long::sum)
            .withDescription("number of stale self events");
    private final LongAccumulator staleEventCount;

    private static final LongAccumulator.Config STALE_APP_TRANSACTIONS_CONFIG = new LongAccumulator.Config(
                    INTERNAL_CATEGORY, "staleAppTransactions")
            .withAccumulator(Long::sum)
            .withDescription("number of application transactions in stale self events");
    private final LongAccumulator staleAppTransactionCount;

    private static final LongAccumulator.Config STALE_SYSTEM_TRANSACTIONS_CONFIG = new LongAccumulator.Config(
                    INTERNAL_CATEGORY, "staleSystemTransactions")
            .withAccumulator(Long::sum)
            .withDescription("number of system transactions in stale self events");
    private final LongAccumulator staleSystemTransactionCount;

    /**
     * Constructor
     *
     * @param platformContext the platform context
     */
    public StaleEventDetectorMetrics(@NonNull final PlatformContext platformContext) {
        final Metrics metrics = platformContext.getMetrics();

        staleEventCount = metrics.getOrCreate(STALE_EVENTS_CONFIG);
        staleAppTransactionCount = metrics.getOrCreate(STALE_APP_TRANSACTIONS_CONFIG);
        staleSystemTransactionCount = metrics.getOrCreate(STALE_SYSTEM_TRANSACTIONS_CONFIG);
    }

    /**
     * Update metrics when a stale event has been detected
     *
     * @param event the stale event
     */
    public void reportStaleEvent(@NonNull final PlatformEvent event) {
        int systemTransactions = 0;
        int appTransactions = 0;

        final Iterator<Transaction> iterator = event.transactionIterator();
        while (iterator.hasNext()) {
            final Transaction transaction = iterator.next();
            if (transaction.isSystem()) {
                systemTransactions++;
            } else {
                appTransactions++;
            }
        }

        staleEventCount.update(1);
        staleSystemTransactionCount.update(systemTransactions);
        staleAppTransactionCount.update(appTransactions);
    }
}

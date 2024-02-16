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

package com.swirlds.platform.metrics;

import static com.swirlds.metrics.api.Metrics.INTERNAL_CATEGORY;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.metrics.api.LongAccumulator;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Collection of metrics related to stale events and transactions
 */
public class StaleMetrics {

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
     * Constructor
     *
     * @param platformContext the platform context
     * @param selfId          the ID of the node
     */
    public StaleMetrics(@NonNull final PlatformContext platformContext, @NonNull final NodeId selfId) {
        final Metrics metrics = platformContext.getMetrics();
        this.selfId = Objects.requireNonNull(selfId);

        staleEventCount = metrics.getOrCreate(STALE_EVENTS_CONFIG);
        staleSelfEventCount = metrics.getOrCreate(STALE_SELF_EVENTS_CONFIG);
        staleAppTransactionCount = metrics.getOrCreate(STALE_APP_TRANSACTIONS_CONFIG);
        staleSelfAppTransactionCount = metrics.getOrCreate(STALE_SELF_APP_TRANSACTIONS_CONFIG);
        staleSystemTransactionCount = metrics.getOrCreate(STALE_SYSTEM_TRANSACTIONS_CONFIG);
        staleSelfSystemTransactionCount = metrics.getOrCreate(STALE_SELF_SYSTEM_TRANSACTIONS_CONFIG);
    }

    /**
     * Update metrics when a stale event has been detected
     *
     * @param event the stale event
     */
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

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

package com.hedera.node.app.state.recordcache;

import static com.hedera.hapi.util.HapiUtils.ACCOUNT_ID_COMPARATOR;
import static com.hedera.hapi.util.HapiUtils.TIMESTAMP_COMPARATOR;
import static com.hedera.hapi.util.HapiUtils.asTimestamp;
import static com.hedera.hapi.util.HapiUtils.minus;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.TransactionID;
import com.hedera.node.app.state.DeduplicationCache;
import com.amh.config.ConfigProvider;
import com.hedera.node.config.data.HederaConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import javax.inject.Inject;
import javax.inject.Singleton;

/** An implementation of {@link DeduplicationCache}. */
@Singleton
public final class DeduplicationCacheImpl implements DeduplicationCache {
    /**
     * The {@link TransactionID}s that this node has already submitted to the platform, sorted by transaction start
     * time, such that earlier start times come first.
     * <p>
     * Note that an ID with scheduled set is different from the same ID without scheduled set.
     * In fact, an ID with scheduled set will always match the ID of the ScheduleCreate transaction that created
     * the schedule, except scheduled is set.
     */
    private final Set<TransactionID> submittedTxns = new ConcurrentSkipListSet<>(
            Comparator.comparing(TransactionID::transactionValidStartOrThrow, TIMESTAMP_COMPARATOR)
                    .thenComparing(TransactionID::accountID, ACCOUNT_ID_COMPARATOR)
                    .thenComparing(TransactionID::scheduled)
                    .thenComparing(TransactionID::nonce));

    /** Used for looking up the max transaction duration window. */
    private final ConfigProvider configProvider;

    /** Constructs a new {@link DeduplicationCacheImpl}. */
    @Inject
    public DeduplicationCacheImpl(@NonNull final ConfigProvider configProvider) {
        this.configProvider = requireNonNull(configProvider);
    }

    /** {@inheritDoc} */
    @Override
    public void add(@NonNull final TransactionID transactionID) {
        // We don't want to use another thread to prune the set, so we will take the opportunity here to do so.
        // Remember that at this point we have passed through all the throttles, so this method is only called
        // at most 10,000 / (Number of nodes) times per second, which is not a lot.
        final var epochSeconds = earliestEpicSecond();
        removeTransactionsOlderThan(epochSeconds);

        // If the transaction is within the max transaction duration window, then add it to the set.
        if (transactionID.transactionValidStartOrThrow().seconds() >= epochSeconds) {
            submittedTxns.add(transactionID);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean contains(@NonNull final TransactionID transactionID) {
        // We will prune the set here as well. By pruning before looking up, we are sure that we only return true
        // if the transactionID is still valid
        final var epochSeconds = earliestEpicSecond();
        removeTransactionsOlderThan(epochSeconds);
        return submittedTxns.contains(transactionID);
    }

    /** {@inheritDoc} */
    @Override
    public void clear() {
        submittedTxns.clear();
    }

    /**
     * Gets the earliest valid start timestamp that is still within the max transaction duration window based on
     * wall-clock time.
     */
    private long earliestEpicSecond() {
        // Compute the earliest valid start timestamp that is still within the max transaction duration window.
        final var now = asTimestamp(Instant.now());
        final var config = configProvider.getConfiguration().getConfigData(HederaConfig.class);
        final var earliestValidState = minus(now, config.transactionMaxValidDuration());
        return earliestValidState.seconds();
    }

    /**
     * Removes all expired {@link TransactionID}s from the cache. This method is not threadsafe and should only be
     * called from within a block synchronized on {@link #submittedTxns}.
     *
     * @param earliestEpochSecond The earliest epoch second that should be kept in the cache.
     */
    private void removeTransactionsOlderThan(final long earliestEpochSecond) {
        final var itr = submittedTxns.iterator();
        while (itr.hasNext()) {
            final var txId = itr.next();
            if (txId.transactionValidStartOrThrow().seconds() < earliestEpochSecond) {
                itr.remove();
            } else {
                return;
            }
        }
    }
}

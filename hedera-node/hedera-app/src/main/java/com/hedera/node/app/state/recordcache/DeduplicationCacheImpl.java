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

package com.hedera.node.app.state.recordcache;

import static com.hedera.node.app.spi.HapiUtils.TIMESTAMP_COMPARATOR;
import static com.hedera.node.app.spi.HapiUtils.asTimestamp;
import static com.hedera.node.app.spi.HapiUtils.minus;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.TransactionID;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.state.DeduplicationCache;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;

/** An implementation of {@link DeduplicationCache}. */
public final class DeduplicationCacheImpl implements DeduplicationCache {
    /**
     * The {@link TransactionID}s that this node has already submitted to the platform, sorted by transaction start
     * time, such that earlier start times come first. We guard this data structure within a synchronized block.
     */
    private final Set<TransactionID> submittedTxns = new TreeSet<>((t1, t2) ->
            TIMESTAMP_COMPARATOR.compare(t1.transactionValidStartOrThrow(), t2.transactionValidStartOrThrow()));

    /** Used for looking up the max transaction duration window. To be replaced by some new config object */
    private final GlobalDynamicProperties props;

    /** Constructs a new {@link DeduplicationCacheImpl}. */
    @Inject
    public DeduplicationCacheImpl(@NonNull final GlobalDynamicProperties props) {
        this.props = requireNonNull(props);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method is not called at a super high rate, so synchronizing here is perfectly fine.
     *
     * @param transactionID
     */
    @Override
    public synchronized void add(@NonNull final TransactionID transactionID) {
        // We don't want to use another thread to prune the set, so we will take the opportunity here to do so.
        // Remember that at this point we have passed through all the throttles, so this method is only called
        // at most 10,000 / (Number of nodes) times per second, which is not a lot.
        final var epochSeconds = earliestEpicSecond();
        removeTransactionsOlderThan(epochSeconds);

        // If the transaction is within the max transaction duration window, then add it to the set.
        if (transactionID.transactionValidStartOrThrow().seconds() > epochSeconds) {
            submittedTxns.add(transactionID);
        }
    }

    /** {@inheritDoc} */
    @Override
    public synchronized boolean contains(@NonNull final TransactionID transactionID) {
        // We will prune the set here as well. By pruning before looking up, we are sure that we only return true
        // if the transactionID is still valid
        final var epochSeconds = earliestEpicSecond();
        removeTransactionsOlderThan(epochSeconds);
        return submittedTxns.contains(transactionID);
    }

    /**
     * Gets the earliest valid start timestamp that is still within the max transaction duration window based on
     * wall-clock time.
     */
    private long earliestEpicSecond() {
        // Compute the earliest valid start timestamp that is still within the max transaction duration window.
        final var now = asTimestamp(Instant.now());
        final var earliestValidState = minus(now, props.maxTxnDuration());
        return earliestValidState.seconds();
    }

    /**
     * Removes all expired {@link TransactionID}s from the cache. This method is not threadsafe and should only be
     * called from within a block synchronized on {@link #submittedTxns}.
     *
     * @param earliestEpochSecond The earliest epoch second that should be kept in the cache.
     */
    private synchronized void removeTransactionsOlderThan(final long earliestEpochSecond) {
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

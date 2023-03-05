/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.system.events;

import com.swirlds.common.system.transaction.Transaction;
import java.time.Instant;
import java.util.Iterator;
import java.util.function.Consumer;

/**
 * An event created by a node with zero or more transactions.
 * <p>
 * IMPORTANT: Although this interface is not sealed, it should only be implemented by internal classes. This
 * interface may be changed at any time, in any way, without notice or prior deprecation. Third parties should NOT
 * implement this interface.
 */
public interface Event {

    /**
     * Returns an iterator over the application events in this transaction.
     *
     * @return a transaction iterator
     */
    Iterator<Transaction> transactionIterator();

    /**
     * Returns the time this event was created as claimed by its creator.
     *
     * @return the created time
     */
    Instant getTimeCreated();

    /**
     * Returns the creator of this event.
     *
     * @return the creator id
     */
    long getCreatorId();

    /**
     * Returns an estimate of what the consensus timestamp will be (could be a very bad guess).
     *
     * @return the estimated consensus timestamp
     */
    Instant getEstimatedTime();

    /**
     * A convenience method that supplies every transaction in this event to a consumer.
     *
     * @param consumer
     * 		a transaction consumer
     */
    default void forEachTransaction(final Consumer<Transaction> consumer) {
        for (final Iterator<Transaction> transIt = transactionIterator(); transIt.hasNext(); ) {
            consumer.accept(transIt.next());
        }
    }
}

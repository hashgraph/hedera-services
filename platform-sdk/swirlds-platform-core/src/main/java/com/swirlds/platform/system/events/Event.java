/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.system.events;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.platform.event.EventCore;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.transaction.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
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
    @NonNull
    NodeId getCreatorId();

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

    /**
     * Returns the software version of the node that created this event.
     *
     * @return the software version
     */
    @NonNull
    SemanticVersion getSoftwareVersion();

    /**
     * Returns the core data of the event.
     *
     * @return the core data
     */
    @NonNull
    EventCore getEventCore();

    /**
     * Returns the signature of the event.
     *
     * @return the signature
     */
    @NonNull
    Bytes getSignature();
}

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

package com.swirlds.common.system;

import com.swirlds.common.system.events.ConsensusEvent;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import java.util.Iterator;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A collection of unique events that reached consensus at the same time. The consensus data for every event in the round
 * will never change, and no more events will ever be added to the round. A round with a lower round number will always
 * reach consensus before a round with a higher round number.
 * <p>
 * IMPORTANT: Although this interface is not sealed, it should only be implemented by internal classes. This
 * interface may be changed at any time, in any way, without notice or prior deprecation. Third parties should NOT
 * implement this interface.
 */
public interface Round extends Iterable<ConsensusEvent> {

    /**
     * An iterator for all consensus events in this round. Each invocation returns a new iterator over the same events.
     * This method is thread safe.
     *
     * @return an iterator of consensus events
     */
    @Override
    Iterator<ConsensusEvent> iterator();

    /**
     * Provides the unique round number for this round. Lower numbers reach consensus before higher numbers. This
     * method is thread safe.
     *
     * @return the round number
     */
    long getRoundNum();

    /**
     * Check if the round is empty.
     *
     * @return true if this round has no events, else returns false.
     */
    boolean isEmpty();

    /**
     * Get the number of events in this round.
     *
     * @return the number of events in the round
     */
    int getEventCount();

    /**
     * A convenience method that supplies every transaction in this round to a consumer.
     *
     * @param transactionConsumer
     * 		a transaction consumer
     */
    default void forEachTransaction(final Consumer<ConsensusTransaction> transactionConsumer) {
        for (final Iterator<ConsensusEvent> eventIt = iterator(); eventIt.hasNext(); ) {
            final ConsensusEvent event = eventIt.next();
            for (final Iterator<ConsensusTransaction> transIt = event.consensusTransactionIterator();
                    transIt.hasNext(); ) {
                transactionConsumer.accept(transIt.next());
            }
        }
    }

    /**
     * A convenience method that supplies every transaction in this round to a consumer, along with the transaction's
     * event.
     *
     * @param consumer
     * 		an event and transaction consumer
     */
    default void forEachEventTransaction(final BiConsumer<ConsensusEvent, ConsensusTransaction> consumer) {
        for (final Iterator<ConsensusEvent> eventIt = iterator(); eventIt.hasNext(); ) {
            final ConsensusEvent event = eventIt.next();
            for (final Iterator<ConsensusTransaction> transIt = event.consensusTransactionIterator();
                    transIt.hasNext(); ) {
                consumer.accept(event, transIt.next());
            }
        }
    }
}

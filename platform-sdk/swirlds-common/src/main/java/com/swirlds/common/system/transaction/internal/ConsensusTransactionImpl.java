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

package com.swirlds.common.system.transaction.internal;

import static com.swirlds.common.system.events.ConsensusData.NO_CONSENSUS;

import com.swirlds.common.system.transaction.ConsensusTransaction;
import java.time.Instant;

/**
 * A transaction that may or may not reach consensus.
 */
public abstract non-sealed class ConsensusTransactionImpl implements ConsensusTransaction {

    /**
     * The consensus order of the event that contains this transaction, or -1 if consensus has not yet been reached.
     * NOT serialized and not part of object equality or hash code
     */
    private long consensusOrder = NO_CONSENSUS;

    /**
     * The consensus timestamp of this transaction, or null if consensus has not yet been reached.
     * NOT serialized and not part of object equality or hash code
     */
    private Instant consensusTimestamp;

    /**
     * {@inheritDoc}
     */
    @Override
    public long getConsensusOrder() {
        return consensusOrder;
    }

    /**
     * Sets the consensus order of this transaction
     *
     * @param consensusOrder
     * 		the consensus order
     */
    public void setConsensusOrder(final long consensusOrder) {
        this.consensusOrder = consensusOrder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Instant getConsensusTimestamp() {
        return consensusTimestamp;
    }

    /**
     * Sets the consensus timestamp of this transaction
     *
     * @param consensusTimestamp
     * 		the consensus timestamp
     */
    public void setConsensusTimestamp(final Instant consensusTimestamp) {
        this.consensusTimestamp = consensusTimestamp;
    }
}

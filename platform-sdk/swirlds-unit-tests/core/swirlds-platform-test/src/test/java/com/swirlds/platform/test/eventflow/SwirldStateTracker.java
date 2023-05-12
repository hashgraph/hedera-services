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

package com.swirlds.platform.test.eventflow;

import com.swirlds.common.system.Round;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.events.ConsensusEvent;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.test.state.DummySwirldState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * An implementation of {@link SwirldState} that tracks transactions passed to it and
 * keeps other information important for testing.
 */
public class SwirldStateTracker extends DummySwirldState implements TransactionTracker, SwirldState {
    private static final int VERSION_ORIGINAL = 1;

    private static final int CLASS_VERSION = VERSION_ORIGINAL;

    private static final long CLASS_ID = 0xa7d6e4b5feda7ce4L;

    /**
     * Static because preHandle() is called once globally, not once per state, and may not change this object's
     * state
     */
    private static final HashSet<Transaction> preHandleTxns = new HashSet<>();

    private final HashSet<ConsensusTransaction> consensusSelfTxns = new HashSet<>();
    private final HashSet<ConsensusTransaction> consensusOtherTxns = new HashSet<>();
    private final List<HandledTransaction> allTxns = new ArrayList<>();

    private final long selfId;

    private StringBuilder failure = new StringBuilder();

    /**
     * Determines if the handle methods should check that metadata was set on the transaction before
     * handling
     */
    private boolean checkForMetadata = false;

    // needed for constructable registry
    public SwirldStateTracker() {
        super();
        selfId = 0L;
    }

    public SwirldStateTracker(final long selfId) {
        this.selfId = selfId;
    }

    public SwirldStateTracker waitForMetadata(final boolean waitForMetadata) {
        this.checkForMetadata = waitForMetadata;
        return this;
    }

    public SwirldStateTracker(final SwirldStateTracker that) {
        this.selfId = that.selfId;
        this.consensusOtherTxns.addAll(that.consensusOtherTxns);
        this.consensusSelfTxns.addAll(that.consensusSelfTxns);
        this.allTxns.addAll(that.allTxns);
        this.failure = that.failure;
        this.checkForMetadata = that.checkForMetadata;
    }

    private void addFailure(final String msg) {
        failure.append(msg).append("\n");
    }

    @Override
    public synchronized void preHandle(final Event event) {
        event.forEachTransaction(trans -> {
            trans.setMetadata(Boolean.TRUE);
            if (!preHandleTxns.add(trans)) {
                addFailure(String.format("Encountered duplicate preHandle transaction: %s", trans));
            }
        });
    }

    @Override
    public synchronized void handleConsensusRound(final Round round, final SwirldDualState swirldDualState) {
        if (isImmutable()) {
            addFailure("Attempt to apply transaction to an immutable state");
        }

        for (final Iterator<ConsensusEvent> eventIt = round.iterator(); eventIt.hasNext(); ) {
            final ConsensusEvent event = eventIt.next();
            for (final Iterator<ConsensusTransaction> transIt = event.consensusTransactionIterator();
                    transIt.hasNext(); ) {

                final ConsensusTransaction trans = transIt.next();

                if (checkForMetadata) {
                    if (trans.getMetadata() != Boolean.TRUE) {
                        addFailure("metadata is not set or is not the correct value: " + trans.getMetadata());
                    }
                }

                addHandledTransaction(trans, trans.getConsensusTimestamp());
                final long creatorId = event.getCreatorId();
                if (creatorId == selfId) {
                    if (!consensusSelfTxns.add(trans)) {
                        addFailure(String.format("Encountered duplicate self consensus transaction %s", trans));
                    }
                } else {
                    if (!consensusOtherTxns.add(trans)) {
                        addFailure(String.format(
                                "Encountered duplicate consensus transaction %s by creator %s", trans, creatorId));
                    }
                }

                if (trans.getConsensusOrder() == -1) {
                    addFailure("\nConsensus transaction does not have consensus order set");
                }
                if (trans.getConsensusTimestamp() == null) {
                    addFailure("\nConsensus transaction does not have consensus timestamp set");
                }
            }
        }
    }

    private void addHandledTransaction(final Transaction trans, final Instant timestamp) {
        final HandledTransaction handledTransaction = new HandledTransaction(trans, Instant.now(), timestamp);
        allTxns.add(handledTransaction);
    }

    @Override
    public SwirldStateTracker copy() {
        throwIfImmutable();
        return new SwirldStateTracker(this);
    }

    @Override
    public Set<Transaction> getPreHandleTransactions() {
        return preHandleTxns;
    }

    @Override
    public synchronized Set<ConsensusTransaction> getPostConsensusSelfTransactions() {
        return consensusSelfTxns;
    }

    @Override
    public synchronized Set<ConsensusTransaction> getPostConsensusOtherTransactions() {
        return consensusOtherTxns;
    }

    @Override
    public synchronized List<HandledTransaction> getOrderedTransactions() {
        return allTxns;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return CLASS_VERSION;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasFailure() {
        return !failure.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFailure() {
        return failure.toString();
    }

    @Override
    public SwirldStateTracker getSwirldState() {
        return this;
    }
}

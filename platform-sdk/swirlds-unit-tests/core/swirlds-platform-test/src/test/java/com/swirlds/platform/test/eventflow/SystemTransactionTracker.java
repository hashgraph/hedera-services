/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.system.transaction.internal.SystemTransactionPing;
import com.swirlds.common.test.TransactionUtils;
import com.swirlds.platform.components.transaction.system.SystemTransactionEndpoint;
import com.swirlds.platform.components.transaction.system.TypedSystemTransactionHandler;
import com.swirlds.platform.components.transaction.system.TypedSystemTransactionHandler.HandleStage;
import com.swirlds.platform.state.State;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SystemTransactionTracker implements SystemTransactionEndpoint, Failable {

    private final Map<Long, Integer> preConsByCreator = new HashMap<>();
    private final Set<Transaction> preConsensusTransactions = new HashSet<>();
    private final Set<Transaction> consensusTransactions = new HashSet<>();
    private final StringBuilder failureMsg = new StringBuilder();

    private void addFailure(final String msg) {
        failureMsg.append(msg);
    }

    /**
     * Consumes pre-consensus system transactions
     * <p>
     * Logs number of pre consensus system transactions be each creator, as well as duplicate pre-consensus system
     * transactions
     *
     * @param state       the state (unused, since we don't need to do know anything about the state)
     * @param creatorId   the id of the node which created the transaction
     * @param transaction the transaction to consume. of type {@link SystemTransactionPing}, since
     *                    {@link TransactionUtils#incrementingSystemTransaction()} uses them
     */
    public void handlePreConsensusSystemTransaction(
            final State state, final long creatorId, final SystemTransactionPing transaction) {
        preConsByCreator.putIfAbsent(creatorId, 0);

        final int prevVal = preConsByCreator.get(creatorId);
        preConsByCreator.put(creatorId, prevVal + 1);
        if (!preConsensusTransactions.add(transaction)) {
            addFailure(String.format(
                    "encountered duplicate pre-consensus system transaction created by node [%s]", creatorId));
        }
    }

    /**
     * Consumes post-consensus system transactions
     * <p>
     * Logs a failure if the consumed transaction has already been handled
     *
     * @param state       the state (unused, since we don't need to do any state modifications)
     * @param creatorId   the id of the node which created the transaction
     * @param transaction the transaction to consume. of type {@link SystemTransactionPing}, since
     *                    {@link TransactionUtils#incrementingSystemTransaction()} uses them
     */
    public void handlePostConsensusSystemTransaction(
            final State state, final long creatorId, final SystemTransactionPing transaction) {

        if (!consensusTransactions.add(transaction)) {
            addFailure(String.format(
                    "encountered duplicate consensus system transaction created by node [%s]", creatorId));
        }
    }

    public synchronized Set<Transaction> getPreConsensusTransactions() {
        return preConsensusTransactions;
    }

    public synchronized Set<Transaction> getConsensusTransactions() {
        return consensusTransactions;
    }

    public Integer getNumPreConsByCreator(final long creator) {
        return preConsByCreator.get(creator);
    }

    @Override
    public boolean hasFailure() {
        return !failureMsg.isEmpty();
    }

    @Override
    public String getFailure() {
        return failureMsg.toString();
    }

    @Override
    public List<TypedSystemTransactionHandler<?>> getHandleMethods() {
        return List.of(
                new TypedSystemTransactionHandler<>(
                        SystemTransactionPing.class,
                        this::handlePreConsensusSystemTransaction,
                        HandleStage.PRE_CONSENSUS),
                new TypedSystemTransactionHandler<>(
                        SystemTransactionPing.class,
                        this::handlePostConsensusSystemTransaction,
                        HandleStage.POST_CONSENSUS));
    }
}

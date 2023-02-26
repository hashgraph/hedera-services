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

import com.swirlds.common.system.events.ConsensusEvent;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.system.transaction.internal.SystemTransaction;
import com.swirlds.platform.components.SystemTransactionHandler;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class SystemTransactionTracker implements SystemTransactionHandler, Failable {

    private final Map<Long, Integer> preConsByCreator = new HashMap<>();
    private final Set<Transaction> preConsensusTransactions = new HashSet<>();
    private final Set<Transaction> consensusTransactions = new HashSet<>();
    private final StringBuilder failureMsg = new StringBuilder();

    private void addFailure(final String msg) {
        failureMsg.append(msg);
    }

    @Override
    public synchronized void handlePreConsensusSystemTransactions(final EventImpl event) {
        preConsByCreator.putIfAbsent(event.getCreatorId(), 0);
        for (final Iterator<SystemTransaction> iter = event.systemTransactionIterator(); iter.hasNext(); ) {
            final SystemTransaction trans = iter.next();
            final int prevVal = preConsByCreator.get(event.getCreatorId());
            preConsByCreator.put(event.getCreatorId(), prevVal + 1);
            if (!preConsensusTransactions.add(trans)) {
                addFailure(String.format(
                        "encountered duplicate pre-consensus system transaction "
                                + "in event %s via handleSystemTransactions(EventImpl)",
                        event.toShortString()));
            }
        }
    }

    @Override
    public synchronized void handlePostConsensusSystemTransactions(final ConsensusRound round) {
        for (final Iterator<ConsensusEvent> eventIt = round.iterator(); eventIt.hasNext(); ) {
            final EventImpl event = (EventImpl) eventIt.next();
            for (final Iterator<SystemTransaction> iter = event.systemTransactionIterator(); iter.hasNext(); ) {
                final SystemTransaction trans = iter.next();

                if (!consensusTransactions.add(trans)) {
                    addFailure(String.format(
                            "encountered duplicate consensus system transaction "
                                    + "in round %s, event %s via handleSystemTransactions(EventImpl)",
                            round.getRoundNum(), event.toShortString()));
                }
            }
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
}

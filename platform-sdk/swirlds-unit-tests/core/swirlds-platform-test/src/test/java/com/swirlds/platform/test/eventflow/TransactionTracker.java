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

import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import com.swirlds.common.system.transaction.Transaction;
import java.util.List;
import java.util.Set;

/**
 * Tracks transactions and other data useful for testing the flow of transactions
 */
public interface TransactionTracker extends Failable {

    Set<Transaction> getPreHandleTransactions();

    Set<ConsensusTransaction> getPostConsensusSelfTransactions();

    Set<ConsensusTransaction> getPostConsensusOtherTransactions();

    List<HandledTransaction> getOrderedTransactions();

    <T extends SwirldState> T getSwirldState();
}

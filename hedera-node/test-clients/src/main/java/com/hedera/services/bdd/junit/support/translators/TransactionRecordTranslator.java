/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit.support.translators;

import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.node.app.state.SingleTransactionRecord;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Translates a format-agnostic transaction into a {@link SingleTransactionRecord}. Defining such
 * translators allows mapping transactions in new (or old) formats to the current {@code SingleTransactionRecord}
 * for accurate comparison.
 */
public interface TransactionRecordTranslator<T> {

    /**
     * Translates a transaction input into a {@link SingleTransactionRecord}.
     *
     * @param transaction a representation of a transaction input. This may be a single object or a
     *                    collection of objects. This argument should include all needed info about a
     *                    transaction to produce a corresponding {@code SingleTransactionRecord}.
     * @param stateChanges any state changes that occurred during the transaction
     * @return the equivalent transaction record
     */
    SingleTransactionRecord translate(@NonNull T transaction, @NonNull StateChanges stateChanges);

    /**
     * Much like the {@link #translate(Object, StateChanges)} method, but for translating a collection
     * of transactions, or for translating a single transaction to multiple {@link SingleTransactionRecord}
     * outputs.
     *
     * @param transactions a collection of transactions to translate
     * @param stateChanges any state changes that occurred during transaction processing
     * @return the equivalent transaction record outputs
     */
    default List<SingleTransactionRecord> translateAll(
            @NonNull final List<T> transactions, @NonNull StateChanges stateChanges) {
        return transactions.stream().map(txn -> translate(txn, stateChanges)).toList();
    }
}

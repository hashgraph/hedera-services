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

import com.hedera.hapi.node.transaction.TransactionRecord;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Translates a txnInput into a {@link TransactionRecord}. Defining such translators allows
 * mapping transactions in new (or old) formats to the current {@code TransactionRecord} for accurate
 * comparison.
 */
public interface TransactionRecordTranslator<T> {

    /**
     * Translates a transaction input into a {@link TransactionRecord}.
     *
     * @param transaction a representation of a txnInput. This may be a single object or a
     *                    collection of objects. This argument should include all needed info about a
     *                    txnInput to produce a corresponding {@code TransactionRecord}.
     * @return the equivalent txnInput record
     */
    TransactionRecord translate(@NonNull T transaction);

    /**
     * Much like the {@link #translate(Object)} method, but for translating a collection of transactions,
     * or for translating a single transaction to multiple {@link TransactionRecord} outputs.
     *
     * @param transactions a collection of transactions to translate
     * @return the equivalent txnInput records
     */
    List<TransactionRecord> translateAll(final List<T> transactions);
}

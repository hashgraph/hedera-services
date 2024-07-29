/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.workflows.record;

import com.hedera.hapi.node.base.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.UnaryOperator;

/**
 * Allows a {@link com.hedera.node.app.spi.workflows.TransactionHandler} that dispatches child transactions
 * to customize exactly how these records are externalized. Specifically, it allows the handler to,
 * <ul>
 *     <li>Completely suppress the record because it contains redundant information (as in the case of
 *     the child transaction dispatched to implement a top-level HAPI {@code ContractCreate}).</li>
 *     <li>Transform the dispatched {@link Transaction} immediately before it is externalized (as
 *     in the case of the child {@link com.hedera.hapi.node.token.CryptoCreateTransactionBody} dispatched
 *     to implement an internal contract creation, which should be externalized as an equivalent
 *     {@link com.hedera.hapi.node.contract.ContractCreateTransactionBody}.</li>
 * </ul>
 *
 * <b>IMPORTANT:</b> implementations that suppress the record should throw if they nonetheless receive an
 * {@link ExternalizedRecordCustomizer#apply(Object)} call. (With the current scope of this interface, the
 * provided {@link #SUPPRESSING_EXTERNALIZED_RECORD_CUSTOMIZER} can simply be used.)
 */
@FunctionalInterface
public interface ExternalizedRecordCustomizer extends UnaryOperator<Transaction> {
    ExternalizedRecordCustomizer NOOP_RECORD_CUSTOMIZER = tx -> tx;

    ExternalizedRecordCustomizer SUPPRESSING_EXTERNALIZED_RECORD_CUSTOMIZER = new ExternalizedRecordCustomizer() {
        @Override
        public Transaction apply(@NonNull final Transaction transaction) {
            throw new UnsupportedOperationException(
                    "Will not customize a transaction that should have been suppressed");
        }

        @Override
        public boolean shouldSuppressRecord() {
            return true;
        }
    };

    /**
     * Indicates whether the record of a dispatched transaction should be suppressed.
     *
     * @return {@code true} if the record should be suppressed; {@code false} otherwise
     */
    default boolean shouldSuppressRecord() {
        return false;
    }
}

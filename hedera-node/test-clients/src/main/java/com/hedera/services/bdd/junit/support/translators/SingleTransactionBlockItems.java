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

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.node.base.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * A logical transaction wrapper for the block items produced for/by processing a single
 * transaction.
 *
 * @param txn    the submitted user transaction
 * @param result the result of processing the user transaction
 * @param output the output (if any) of processing the user transaction
 */
public record SingleTransactionBlockItems(
        @NonNull Transaction txn, @NonNull TransactionResult result, @Nullable TransactionOutput output) {

    /**
     * The input block items should be exactly the block items produced for/by processing a
     * single transaction, with the following expected order:
     * <ol>
     *     <li>Index 0: BlockItem.Transaction</li>
     *     <li>Index 1: BlockItem.TransactionResult</li>
     *     <li>Index 2: BlockItem.TransactionOutput (if applicable)</li>
     * </ol>
     *
     * @param items The block items representing a single transaction
     * @return A logical transaction wrapper for the block items
     */
    public static SingleTransactionBlockItems asSingleTransaction(@NonNull final List<BlockItem> items) {
        final var builder = new Builder();
        final var txnItem = items.get(0);
        if (!txnItem.hasTransaction()) {
            throw new IllegalArgumentException("Expected a transaction item!");
        }
        // The nullable warnings here aren't warranted since we check for `hasTransaction()` above.
        // Similarly for the other fields
        //noinspection DataFlowIssue
        builder.txn(txnItem.transaction());

        final var resultItem = items.get(1);
        if (!resultItem.hasTransactionResult()) {
            throw new IllegalArgumentException("Expected a transaction result item!");
        }
        //noinspection DataFlowIssue
        builder.result(resultItem.transactionResult());

        if (items.size() > 2) {
            final var maybeOutput = items.get(2);
            if (!maybeOutput.hasTransactionOutput()) {
                throw new IllegalArgumentException("Expected a transaction output item!");
            }
            builder.output(maybeOutput.transactionOutput());
        }

        return builder.build();
    }

    public static class Builder {
        private Transaction txn;
        private TransactionResult result;
        private TransactionOutput output;

        public Builder txn(@NonNull final Transaction txn) {
            this.txn = txn;
            return this;
        }

        public Builder result(@NonNull final TransactionResult result) {
            this.result = result;
            return this;
        }

        public Builder output(@Nullable TransactionOutput output) {
            this.output = output;
            return this;
        }

        public SingleTransactionBlockItems build() {
            return new SingleTransactionBlockItems(txn, result, output);
        }
    }
}

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

import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.node.base.Transaction;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A logical transaction wrapper for the block items produced for/by processing a single
 * transaction.
 *
 * @param txn    the submitted user transaction
 * @param result the result of processing the user transaction
 * @param output the output (if any) of processing the user transaction
 */
public record SingleTransactionBlockItems(
        @Nullable Transaction txn, @Nullable TransactionResult result, @Nullable TransactionOutput output) {

    public static class Builder {
        private Transaction txn;
        private TransactionResult result;
        private TransactionOutput output;

        public Builder txn(@Nullable final Transaction txn) {
            this.txn = txn;
            return this;
        }

        public Builder result(@Nullable final TransactionResult result) {
            this.result = result;
            return this;
        }

        public Builder output(@Nullable TransactionOutput output) {
            this.output = output;
            return this;
        }

        /**
         * Builds the logical transaction wrapper for the block items.
         *
         * @return the built object
         */
        public SingleTransactionBlockItems build() {
            return new SingleTransactionBlockItems(txn, result, output);
        }

        /**
         * Determines if the builder has any non-null components
         * @return true if all components are null, false otherwise
         */
        public boolean isEmpty() {
            return txn == null && result == null && output == null;
        }
    }
}

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

package com.hedera.services.bdd.junit.support.translators.inputs;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.node.base.HederaFunctionality;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Groups the block items used to represent a single logical HAPI transaction, which itself may be part of a larger
 * transactional unit with parent/child relationships.
 * @param transactionParts the parts of the transaction
 * @param transactionResult the result of processing the transaction
 * @param transactionOutput the output of processing the transaction
 */
public record BlockTransactionParts(
        @NonNull TransactionParts transactionParts,
        @NonNull TransactionResult transactionResult,
        @Nullable TransactionOutput transactionOutput) {

    /**
     * Returns the functionality of the transaction.
     * @return the functionality
     */
    public HederaFunctionality functionality() {
        return transactionParts.function();
    }

    /**
     * Constructs a new {@link BlockTransactionParts} that includes an output.
     * @param transactionParts the parts of the transaction
     * @param transactionResult the result of processing the transaction
     * @param transactionOutput the output of processing the transaction
     * @return the constructed object
     */
    public static BlockTransactionParts withOutput(
            @NonNull final TransactionParts transactionParts,
            @NonNull final TransactionResult transactionResult,
            @NonNull final TransactionOutput transactionOutput) {
        requireNonNull(transactionParts);
        requireNonNull(transactionResult);
        requireNonNull(transactionOutput);
        return new BlockTransactionParts(transactionParts, transactionResult, transactionOutput);
    }

    /**
     * Constructs a new {@link BlockTransactionParts} that does not include an output.
     * @param transactionParts the parts of the transaction
     * @param transactionResult the result of processing the transaction
     * @return the constructed object
     */
    public static BlockTransactionParts sansOutput(
            @NonNull final TransactionParts transactionParts, @NonNull final TransactionResult transactionResult) {
        requireNonNull(transactionParts);
        requireNonNull(transactionResult);
        return new BlockTransactionParts(transactionParts, transactionResult, null);
    }
}

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

import static com.hedera.node.app.hapi.utils.CommonUtils.noThrowSha384HashOf;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.output.CryptoTransferOutput;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenAssociation;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

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
     * Returns the status of the transaction.
     * @return the status
     */
    public ResponseCodeEnum status() {
        return transactionResult.status();
    }

    /**
     * Returns the body of the transaction.
     * @return the body
     */
    public TransactionBody body() {
        return transactionParts.body();
    }

    /**
     * Returns the functionality of the transaction.
     * @return the functionality
     */
    public HederaFunctionality functionality() {
        return transactionParts.function();
    }

    /**
     * Returns the transaction ID.
     * @return the transaction ID
     */
    public TransactionID transactionIdOrThrow() {
        return transactionParts.body().transactionIDOrThrow();
    }

    /**
     * Returns the consensus timestamp.
     * @return the consensus timestamp
     */
    public Timestamp consensusTimestamp() {
        return transactionResult.consensusTimestamp();
    }

    /**
     * Returns the transaction fee.
     * @return the transaction fee
     */
    public long transactionFee() {
        return transactionResult.transactionFeeCharged();
    }

    /**
     * Returns the transfer list.
     * @return the transfer list
     */
    public TransferList transferList() {
        return transactionResult.transferList();
    }

    /**
     * Returns the token transfer lists.
     * @return the token transfer lists
     */
    public List<TokenTransferList> tokenTransferLists() {
        return transactionResult.tokenTransferLists();
    }

    /**
     * Returns the automatic token associations.
     * @return the automatic token associations
     */
    public List<TokenAssociation> automaticTokenAssociations() {
        return transactionResult.automaticTokenAssociations();
    }

    /**
     * Returns the paid staking rewards.
     * @return the paid staking rewards
     */
    public List<AccountAmount> paidStakingRewards() {
        return transactionResult.paidStakingRewards();
    }

    /**
     * Returns the memo.
     * @return the memo
     */
    public String memo() {
        return transactionParts.body().memo();
    }

    /**
     * Returns the hash of the transaction.
     * @return the hash
     */
    public Bytes transactionHash() {
        final var transaction = transactionParts.wrapper();
        final Bytes transactionBytes;
        if (transaction.signedTransactionBytes().length() > 0) {
            transactionBytes = transaction.signedTransactionBytes();
        } else {
            transactionBytes = Transaction.PROTOBUF.toBytes(transaction);
        }
        return Bytes.wrap(noThrowSha384HashOf(transactionBytes.toByteArray()));
    }

    /**
     * Returns the output of the transaction.
     * @return the output
     * @throws NullPointerException if the output is not present
     */
    public TransactionOutput outputOrThrow() {
        return requireNonNull(transactionOutput);
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

    /**
     * Returns the assessed custom fees.
     * @return the assessed custom fees
     */
    public List<AssessedCustomFee> assessedCustomFees() {
        return transactionOutput == null
                ? emptyList()
                : transactionOutput
                        .cryptoTransferOrElse(CryptoTransferOutput.DEFAULT)
                        .assessedCustomFees();
    }
}

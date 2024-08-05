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
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.swirlds.common.exceptions.NotImplementedException;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;

/**
 * Converts a block stream transaction into a {@link TransactionRecord}. We can then use the converted
 * records to compare block stream outputs with the current/expected outputs.
 */
public class BlockStreamTransactionTranslator implements TransactionRecordTranslator<SingleTransactionBlockItems> {

    /**
     * Translates a {@link SingleTransactionBlockItems} into a {@link SingleTransactionRecord}.
     *
     * @param transaction A wrapper for block items representing a single transaction input
     * @return the translated txnInput record
     */
    @Override
    public SingleTransactionRecord translate(
            @NonNull final SingleTransactionBlockItems transaction, @NonNull final StateChanges stateChanges) {
        Objects.requireNonNull(transaction, "transaction must not be null");
        Objects.requireNonNull(stateChanges, "stateChanges must not be null");

        final var txnType = transaction.txn().bodyOrThrow().data().kind();
        final var singleTxnRecord =
                switch (txnType) {
                    case UTIL_PRNG -> new UtilPrngTranslator().translate(transaction, stateChanges);
                    case UNSET -> throw new IllegalArgumentException("Transaction type not set");
                    default -> new SingleTransactionRecord(
                            transaction.txn(),
                            TransactionRecord.newBuilder().build(),
                            List.of(),
                            new SingleTransactionRecord.TransactionOutputs(null));
                };

        final var txnRecord = singleTxnRecord.transactionRecord();
        final var recordBuilder = txnRecord.copyBuilder();
        final var receiptBuilder =
                txnRecord.hasReceipt() ? txnRecord.receipt().copyBuilder() : TransactionReceipt.newBuilder();

        parseTransaction(transaction.txn(), recordBuilder);

        parseTransactionResult(transaction.result(), recordBuilder, receiptBuilder);

        if (transaction.output() != null) {
            parseTransactionOutput(transaction.output(), recordBuilder, receiptBuilder);
        }

        // TODO: how do we generically parse the state changes, especially for synthetic child transactions?

        return new SingleTransactionRecord(
                transaction.txn(),
                recordBuilder.build(),
                // TODO: how do we construct the sidecar records?
                List.of(),
                // TODO: construct TransactionOutputs correctly when we have access to token-related transaction types
                new SingleTransactionRecord.TransactionOutputs(null));
    }

    /**
     * Computes the {@link SingleTransactionRecord}(s) for an ordered collection of transactions,
     * represented as groups of {@link BlockItem}s and summary {@link StateChanges}. Note that
     * {@link Transaction}s in the input collection will be associated based on having the same
     * {@code accountID} and {@code transactionValidStart} (from their {@link TransactionID}s). In
     * other words, this code will assume that transactions with the same payer account and valid
     * start time are part of the same user transaction, sharing some sort of parent-children
     * relationship.
     *
     * @param transactions a collection of transactions to translate
     * @param stateChanges any state changes that occurred during transaction processing
     * @return the equivalent transaction record outputs
     */
    @Override
    public List<SingleTransactionRecord> translateAll(
            @NonNull final List<SingleTransactionBlockItems> transactions, @NonNull final StateChanges stateChanges) {
        throw new NotImplementedException();
    }

    private TransactionRecord.Builder parseTransaction(
            final Transaction txn, final TransactionRecord.Builder recordBuilder) {
        return recordBuilder
                .transactionID(txn.body().transactionID())
                .memo(txn.body().memo());
    }

    private TransactionRecord.Builder parseTransactionResult(
            final TransactionResult txnResult,
            final TransactionRecord.Builder recordBuilder,
            final TransactionReceipt.Builder receiptBuilder) {
        recordBuilder
                .automaticTokenAssociations(txnResult.automaticTokenAssociations())
                .parentConsensusTimestamp(txnResult.parentConsensusTimestamp())
                .consensusTimestamp(txnResult.consensusTimestamp())
                .scheduleRef(txnResult.scheduleRef())
                .paidStakingRewards(txnResult.paidStakingRewards())
                .transactionFee(txnResult.transactionFeeCharged())
                .transferList(txnResult.transferList())
                .tokenTransferLists(txnResult.tokenTransferLists());

        receiptBuilder.exchangeRate(txnResult.exchangeRate()).status(txnResult.status());

        return recordBuilder;
    }

    private TransactionRecord.Builder parseTransactionOutput(
            final TransactionOutput txnOutput,
            final TransactionRecord.Builder trb,
            final TransactionReceipt.Builder rb) {
        // TODO: why are so many of these methods missing?
        //            if (txnOutput.hasCryptoCreate()) {
        //                rb.accountID(txnOutput.cryptoCreate().accountID());
        //                trb.alias(txnOutput.cryptoCreate().alias());
        //            }

        if (txnOutput.hasCryptoTransfer()) {
            trb.assessedCustomFees(txnOutput.cryptoTransfer().assessedCustomFees());
        }
        //            if (txnOutput.hasFileCreate()) {
        //                rb.fileID(txnOutput.fileCreate().fileID());
        //            }

        if (txnOutput.hasContractCreate()) {
            rb.contractID(txnOutput.contractCreate().contractCreateResult().contractID());
        }

        if (txnOutput.hasContractCreate()) {
            trb.contractCreateResult(txnOutput.contractCreate().contractCreateResult());
        }
        if (txnOutput.hasContractCall()) {
            trb.contractCallResult(txnOutput.contractCall().contractCallResult());
        }

        if (txnOutput.hasEthereumCall()) {
            trb.ethereumHash(txnOutput.ethereumCall().ethereumHash());
        }

        //            if (txnOutput.hasTopicCreate()) {
        //                rb.topicID(txnOutput.topicCreate().topicID());
        //            }

        if (txnOutput.hasSubmitMessage()) {
            //                rb.topicSequenceNumber(txnOutput.submitMessage().topicSequenceNumber());
            rb.topicRunningHashVersion(
                    txnOutput.submitMessage().topicRunningHashVersion().protoOrdinal());
        }

        //            if (txnOutput.hasCreateToken()) {
        //                rb.tokenID(txnOutput.createToken().tokenID());
        //            }

        //            if (txnOutput.hasTokenMint()) {
        //
        //            }
        //            if (txnOutput.hasTokenBurn()) {
        //
        //            }
        //            if (txnOutput.hasTokenWipe()) {
        //
        //            }

        if (txnOutput.hasCreateSchedule()) {
            //                rb.scheduleID(txnOutput.createSchedule().scheduledTransactionId());
        }

        if (txnOutput.hasCreateSchedule()) {
            rb.scheduledTransactionID(txnOutput.createSchedule().scheduledTransactionId());
        }
        if (txnOutput.hasSignSchedule()) {
            rb.scheduledTransactionID(txnOutput.signSchedule().scheduledTransactionId());
        }

        //            if (txnOutput.hasMintToken()) {
        //                rb.serialNumbers(txnOutput.mintToken().serialNumbers());
        //            }

        //            if (txnOutput.hasNodeCreate()) {
        //                rb.nodeId(txnOutput.nodeCreate().nodeID());
        //            }
        //            if (txnOutput.hasNodeUpdate()) {
        //                rb.nodeId(txnOutput.nodeUpdate().nodeID());
        //            }
        //            if (txnOutput.hasNodeDelete()) {
        //                rb.nodeId(txnOutput.nodeDelete().nodeID());
        //            }

        maybeAssignEvmAddressAlias(txnOutput, trb);

        // TODO: assign `newPendingAirdrops` (if applicable)

        trb.receipt(rb.build());

        return trb;
    }

    private void maybeAssignEvmAddressAlias(TransactionOutput txnOutput, TransactionRecord.Builder trb) {
        // Are these the only places where default EVM address aliases are assigned?
        if (txnOutput.hasContractCreate()) {
            trb.evmAddress(txnOutput.contractCreate().contractCreateResult().evmAddress());
        }
        if (txnOutput.hasContractCall()) {
            trb.evmAddress(txnOutput.contractCall().contractCallResult().evmAddress());
        }
        if (txnOutput.hasCryptoTransfer()) {
            //            trb.evmAddress(txnOutput.cryptoTransfer().evmAddress());
        }
        if (txnOutput.hasEthereumCall()) {
            //            trb.evmAddress(txnOutput.ethereumCall().evmAddress());
        }
    }
}

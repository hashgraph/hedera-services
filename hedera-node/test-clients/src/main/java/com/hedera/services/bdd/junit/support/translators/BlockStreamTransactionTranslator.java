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

import static com.hedera.hapi.block.stream.output.UtilPrngOutput.EntropyOneOfType.PRNG_BYTES;
import static com.hedera.hapi.block.stream.output.UtilPrngOutput.EntropyOneOfType.PRNG_NUMBER;
import static com.hedera.services.bdd.junit.support.translators.BlockStreamTransactionTranslator.*;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.swirlds.common.exceptions.NotImplementedException;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts a block stream transaction into a {@link TransactionRecord}. We can then use the converted
 * records to compare block stream outputs with the current/expected outputs.
 */
public class BlockStreamTransactionTranslator implements TransactionRecordTranslator<BlockTransaction> {

    /**
     * A logical transaction wrapper for the block items produced for/by processing a single transaction.
     * @param txnInput the submitted user transaction
     * @param transactionResult the result of processing the user transaction
     * @param transactionOutput the output (if any) of processing the user transaction
     * @param stateChanges the state changes produced by processing the user transaction
     */
    public record BlockTransaction(
            @NonNull BlockItem txnInput,
            @NonNull BlockItem transactionResult,
            @Nullable BlockItem transactionOutput,
            @NonNull BlockItem stateChanges) {
        public List<BlockItem> asItems() {
            final var blockItems = new ArrayList<BlockItem>();
            blockItems.add(txnInput);
            blockItems.add(transactionResult);
            if (transactionOutput != null) {
                blockItems.add(transactionOutput);
            }
            blockItems.add(stateChanges);

            return blockItems;
        }

        /**
         * The input block items should be exactly the block items produced for/by processing a
         * single transaction, with the following expected order:
         * <ol>
         *     <li>Index 0: BlockItem.Transaction</li>
         *     <li>Index 1: BlockItem.TransactionResult</li>
         *     <li>Index 2: BlockItem.TransactionOutput (if applicable)</li>
         *     <li>Index 2 or 3: BlockItem.StateChanges (dependent on the presence of TransactionOutput)</li>
         * </ol>
         *
         * @param items The block items representing a single transaction
         * @return A logical transaction wrapper for the block items
         */
        public static BlockTransaction asBlockTransaction(@NonNull List<BlockItem> items) {
            final var txn = items.get(0);
            final var result = items.get(1);
            final var maybeOutput = items.get(2);

            if (items.size() > 3) {
                return new BlockTransaction(txn, result, maybeOutput, items.get(3));
            } else {
                return new BlockTransaction(txn, result, null, maybeOutput);
            }
        }
    }

    /**
     * Translates a {@link BlockTransaction} into a {@link TransactionRecord}.
     *
     * @param transaction A wrapper for block items representing a single txnInput
     * @return the translated txnInput record
     */
    @Override
    public TransactionRecord translate(@NonNull final BlockTransaction transaction) {
        validateBlockItems(transaction.asItems());

        final var recordBuilder = TransactionRecord.newBuilder();
        final var receiptBuilder = TransactionReceipt.newBuilder();

        BlockItem txnBlockItem = transaction.txnInput();
        if (txnBlockItem.hasTransaction()) {
            parseTransaction(txnBlockItem, recordBuilder);
        }

        txnBlockItem = transaction.transactionResult();
        if (txnBlockItem.hasTransactionResult()) {
            parseTransactionResult(txnBlockItem, recordBuilder, receiptBuilder);
        }

        txnBlockItem = transaction.transactionOutput();
        if (txnBlockItem != null && txnBlockItem.hasTransactionOutput()) {
            parseTransactionOutput(txnBlockItem, recordBuilder, receiptBuilder);
        }

        txnBlockItem = transaction.stateChanges();
        // TODO: how do we generically extract state changes for the txnInput record?
        if (txnBlockItem.hasStateChanges()) {}

        return recordBuilder.build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TransactionRecord> translateAll(final List<BlockTransaction> transactions) {
        throw new NotImplementedException();
    }

    private void validateBlockItems(final List<BlockItem> blockItems) {
        if (blockItems.size() < 2) {
            throw new IllegalArgumentException("Expected at least two block items");
        }

        final var txnItem = blockItems.get(0);
        if (!txnItem.hasTransaction()) {
            throw new IllegalArgumentException("Expected first block item to be a txnInput");
        }

        final var txnResultItem = blockItems.get(1);
        if (!txnResultItem.hasTransactionResult()) {
            throw new IllegalArgumentException("Expected second block item to be a txnInput result");
        }

        final var stateChangesItem = blockItems.getLast();
        if (!stateChangesItem.hasStateChanges()) {
            throw new IllegalArgumentException("Expected last block item to be state changes");
        }
    }

    private TransactionRecord.Builder parseTransaction(
            final BlockItem txnBlockItem, final TransactionRecord.Builder recordBuilder) {
        final var txnItem = txnBlockItem.transaction();
        recordBuilder.transactionID(txnItem.body().transactionID());
        recordBuilder.memo(txnItem.body().memo());
        return recordBuilder;
    }

    private TransactionRecord.Builder parseTransactionResult(
            final BlockItem txnBlockItem,
            final TransactionRecord.Builder recordBuilder,
            final TransactionReceipt.Builder receiptBuilder) {
        final var txnResult = txnBlockItem.transactionResult();

        recordBuilder.automaticTokenAssociations(txnResult.automaticTokenAssociations());
        recordBuilder.parentConsensusTimestamp(txnResult.parentConsensusTimestamp());
        recordBuilder.consensusTimestamp(txnResult.consensusTimestamp());
        recordBuilder.scheduleRef(txnResult.scheduleRef());
        recordBuilder.paidStakingRewards(txnResult.paidStakingRewards());
        recordBuilder.transactionFee(txnResult.transactionFeeCharged());
        recordBuilder.transferList(txnResult.transferList());
        recordBuilder.tokenTransferLists(txnResult.tokenTransferLists());

        receiptBuilder.exchangeRate(txnResult.exchangeRate());
        receiptBuilder.status(txnResult.status());

        return recordBuilder;
    }

    private TransactionRecord.Builder parseTransactionOutput(
            final BlockItem transactionBlockItem, final TransactionRecord.Builder trb, TransactionReceipt.Builder rb) {
        final var txnOutput = transactionBlockItem.transactionOutput();

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

        if (txnOutput.hasUtilPrng()) {
            final var entropy = txnOutput.utilPrng().entropy();
            if (entropy.kind() == PRNG_BYTES) {
                trb.prngBytes(entropy.as());
            } else if (entropy.kind() == PRNG_NUMBER) {
                trb.prngNumber(entropy.as());
            }
        }

        maybeAssignEvmAddress(trb, txnOutput);

        // TODO: assign `newPendingAirdrops` (if applicable)

        trb.receipt(rb.build());

        return trb;
    }

    private void maybeAssignEvmAddress(TransactionRecord.Builder trb, TransactionOutput txnOutput) {
        // Are these the only two places where default EVM addresses are assigned?
        if (txnOutput.hasContractCreate()) {
            trb.evmAddress(txnOutput.contractCreate().contractCreateResult().evmAddress());
        }
        if (txnOutput.hasContractCall()) {
            trb.evmAddress(txnOutput.contractCall().contractCallResult().evmAddress());
        }
    }
}

package com.hedera.node.app;

import static com.hedera.hapi.block.stream.output.UtilPrngOutput.EntropyOneOfType.PRNG_BYTES;
import static com.hedera.hapi.block.stream.output.UtilPrngOutput.EntropyOneOfType.PRNG_NUMBER;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;

import java.util.List;

/**
 * Converts a block stream transaction into a {@link TransactionRecord}. We can then use the converted
 * records to compare block stream outputs with the current/expected outputs.
 */
public class BlockStreamTransactionConverter {

    /**
     * Converts a (specific) list of {@link BlockItem}s into a {@link TransactionRecord}. The input
     * block items should exactly be the block items produced for/by processing a single transaction,
     * with the following expected order:
     * <ol>
     *     <li>Index 0: BlockItem.Transaction</li>
     *     <li>Index 1: BlockItem.TransactionResult</li>
     *     <li>Index 2: BlockItem.TransactionOutput (if applicable)</li>
     *     <li>Index 2 or 3: BlockItem.StateChanges (dependent on the presence of TransactionOutput)</li>
     * </ol>
     *
     * @param blockItems the list of block items (for a single transaction) to convert
     * @return the corresponding transaction record
     */
    public TransactionRecord convert(final List<BlockItem> blockItems) {
        validateBlockItems(blockItems);

        final var recordBuilder = TransactionRecord.newBuilder();
        final var receiptBuilder = TransactionReceipt.newBuilder();

        BlockItem txnBlockItem = blockItems.get(0);
        if (txnBlockItem.hasTransaction()) {
            parseTransaction(txnBlockItem, recordBuilder);
        }

        txnBlockItem = blockItems.get(1);
        if (txnBlockItem.hasTransactionResult()) {
            parseTransactionResult(txnBlockItem, recordBuilder, receiptBuilder);
        }

        txnBlockItem = blockItems.get(2);
        if (txnBlockItem.hasTransactionOutput()) {
            parseTransactionOutput(txnBlockItem, recordBuilder, receiptBuilder);
        }

        // Advance to the next block item (if applicable)
        if (blockItems.size() > 2) {
            txnBlockItem = blockItems.get(3);
        }
        // TODO: how do we generically extract state changes for the transaction record?
        if (txnBlockItem.hasStateChanges()) {

        }

        return recordBuilder.build();
    }

    private void validateBlockItems(final List<BlockItem> blockItems) {
        if (blockItems.size() < 2) {
            throw new IllegalArgumentException("Expected at least two block items");
        }

        final var txnItem = blockItems.get(0);
        if (!txnItem.hasTransaction()) {
            throw new IllegalArgumentException("Expected first block item to be a transaction");
        }

        final var txnResultItem = blockItems.get(1);
        if (!txnResultItem.hasTransactionResult()) {
            throw new IllegalArgumentException("Expected second block item to be a transaction result");
        }
    }

    private TransactionRecord.Builder parseTransaction(final BlockItem txnBlockItem, final TransactionRecord.Builder recordBuilder) {
        final var txnItem = txnBlockItem.transaction();
        recordBuilder.transactionID(txnItem.body().transactionID());
        recordBuilder.memo(txnItem.body().memo());
        return recordBuilder;
    }

    private TransactionRecord.Builder parseTransactionResult(final BlockItem txnBlockItem, final TransactionRecord.Builder recordBuilder, final TransactionReceipt.Builder receiptBuilder) {
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

    private TransactionRecord.Builder parseTransactionOutput(final BlockItem transactionBlockItem, final TransactionRecord.Builder trb, TransactionReceipt.Builder rb) {
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
            rb.topicRunningHashVersion(txnOutput.submitMessage().topicRunningHashVersion().protoOrdinal());
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

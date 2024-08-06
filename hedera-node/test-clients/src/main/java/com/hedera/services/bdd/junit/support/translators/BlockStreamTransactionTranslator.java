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
import static com.hedera.node.app.hapi.utils.CommonPbjConverters.fromPbj;
import static com.hedera.node.app.hapi.utils.CommonPbjConverters.pbjToProto;
import static com.hedera.node.app.hapi.utils.CommonPbjConverters.protoToPbj;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AssessedCustomFee;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenAssociation;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
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

        final var recordBuilder = TransactionRecord.newBuilder();
        final var receiptBuilder = TransactionReceipt.newBuilder();

        parseTransaction(transaction.txn(), recordBuilder);

        try {
            parseTransactionResult(transaction.result(), recordBuilder, receiptBuilder);
            parseTransactionOutput(transaction.output(), recordBuilder, receiptBuilder);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }

        // TODO: how do we generically parse the state changes, especially for synthetic child transactions?

        return new SingleTransactionRecord(
                transaction.txn(),
                protoToPbj(recordBuilder.build(),
                        com.hedera.hapi.node.transaction.TransactionRecord.class),
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
        //todo: implement
        throw new NotImplementedException();
    }

    private TransactionRecord.Builder parseTransaction(
            final Transaction txn, final TransactionRecord.Builder recordBuilder) {
        return recordBuilder
                .setTransactionID(pbjToProto(txn.body().transactionID(),
                        com.hedera.hapi.node.base.TransactionID.class, TransactionID.class)).
                setMemo(txn.body().memo());
    }

    private TransactionRecord.Builder parseTransactionResult(
            final TransactionResult txnResult,
            final TransactionRecord.Builder recordBuilder,
            final TransactionReceipt.Builder receiptBuilder) throws InvalidProtocolBufferException {
        final var autoTokenAssocs = txnResult.automaticTokenAssociations();
        autoTokenAssocs.forEach(tokenAssociation -> {
            var b = pbjToProto(tokenAssociation, com.hedera.hapi.node.base.TokenAssociation.class, TokenAssociation.class);
            recordBuilder.addAutomaticTokenAssociations(b);
        });

        final var paidStakingRewards = txnResult.paidStakingRewards();
        for (com.hedera.hapi.node.base.AccountAmount paidStakingReward : paidStakingRewards) {
            final var proto = pbjToProto(paidStakingReward,
                    com.hedera.hapi.node.base.AccountAmount.class, AccountAmount.class);
            recordBuilder.addPaidStakingRewards(proto);
        }

        final var transferList = TransferList.parseFrom(com.hedera.hapi.node.base.TransferList.PROTOBUF.toBytes(txnResult.transferList()).toByteArray());

        final var tokenTransferLists = txnResult.tokenTransferLists();
        tokenTransferLists.forEach(tokenTransferList -> {
            final var proto = pbjToProto(tokenTransferList, com.hedera.hapi.node.base.TokenTransferList.class, TokenTransferList.class);
            recordBuilder.addTokenTransferLists(proto);
        });

        recordBuilder
                .setParentConsensusTimestamp(fromPbj(txnResult.parentConsensusTimestamp()))
                .setConsensusTimestamp(fromPbj(txnResult.consensusTimestamp()))
                .setScheduleRef(pbjToProto(txnResult.scheduleRef(), com.hedera.hapi.node.base.ScheduleID.class, ScheduleID.class))
                .setTransactionFee(txnResult.transactionFeeCharged())
                .setTransferList(transferList);

        final var exchangeRateSet = ExchangeRateSet.parseFrom(
                com.hedera.hapi.node.transaction.ExchangeRateSet.PROTOBUF.toBytes(txnResult.exchangeRate()).toByteArray());
        final var responseCode = ResponseCodeEnum.valueOf(txnResult.status().name());
        receiptBuilder.setExchangeRate(exchangeRateSet).setStatus(responseCode);

        return recordBuilder;
    }

    private TransactionRecord.Builder parseTransactionOutput(
            final TransactionOutput txnOutput,
            final TransactionRecord.Builder trb,
            final TransactionReceipt.Builder rb)
            throws InvalidProtocolBufferException {
        // TODO: why are so many of these methods missing?
        //            if (txnOutput.hasCryptoCreate()) {
        //                rb.accountID(txnOutput.cryptoCreate().accountID());
        //                trb.alias(txnOutput.cryptoCreate().alias());
        //            }

        if (txnOutput.hasCryptoTransfer()) {
            final var assessedCustomFees = txnOutput.cryptoTransfer().assessedCustomFees();
            for (int i = 0; i < assessedCustomFees.size(); i++) {
                final var assessedCustomFee = AssessedCustomFee.parseFrom(
                        com.hedera.hapi.node.transaction.AssessedCustomFee.PROTOBUF.toBytes(assessedCustomFees.get(i)).toByteArray());
                trb.addAssessedCustomFees(i, assessedCustomFee);
            }
        }

        //            if (txnOutput.hasFileCreate()) {
        //                rb.fileID(txnOutput.fileCreate().fileID());
        //            }

        if (txnOutput.hasContractCreate()) {
            rb.setContractID(
                    fromPbj(txnOutput.contractCreate().contractCreateResult().contractID()));
        }

        if (txnOutput.hasContractCreate()) {
            final var createResult = ContractFunctionResult.parseFrom(
                    com.hedera.hapi.node.contract.ContractFunctionResult.PROTOBUF.toBytes(txnOutput.contractCreate().contractCreateResult()).toByteArray());
            trb.setContractCreateResult(createResult);
        }
        if (txnOutput.hasContractCall()) {
            final var callResult = ContractFunctionResult.parseFrom(
                    com.hedera.hapi.node.contract.ContractFunctionResult.PROTOBUF.toBytes(txnOutput.contractCall().contractCallResult()).toByteArray());
            trb.setContractCallResult(callResult);
        }

        if (txnOutput.hasEthereumCall()) {
            trb.setEthereumHash(ByteString.copyFrom(txnOutput.ethereumCall().ethereumHash().toByteArray()));
        }

        //            if (txnOutput.hasTopicCreate()) {
        //                rb.topicID(txnOutput.topicCreate().topicID());
        //            }

        if (txnOutput.hasSubmitMessage()) {
            //                rb.topicSequenceNumber(txnOutput.submitMessage().topicSequenceNumber());
            rb.setTopicRunningHashVersion(
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
            rb.setScheduledTransactionID(
                    pbjToProto(txnOutput.createSchedule().scheduledTransactionId(), com.hedera.hapi.node.base.TransactionID.class, TransactionID.class));
        }
        if (txnOutput.hasSignSchedule()) {
            rb.setScheduledTransactionID(
                    pbjToProto(txnOutput.signSchedule().scheduledTransactionId(), com.hedera.hapi.node.base.TransactionID.class, TransactionID.class));
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
                trb.setPrngBytes(entropy.as());
            } else if (entropy.kind() == PRNG_NUMBER) {
                trb.setPrngNumber(entropy.as());
            }
        }

        maybeAssignEvmAddressAlias(txnOutput, trb);

        // TODO: assign `newPendingAirdrops` (if applicable)

        trb.setReceipt(rb.build());

        return trb;
    }

    private void maybeAssignEvmAddressAlias(final TransactionOutput txnOutput, final TransactionRecord.Builder trb) {
        // Are these the only places where default EVM address aliases are assigned?
        if (txnOutput.hasContractCreate()) {
            trb.setEvmAddress(toByteString(txnOutput.contractCreate().contractCreateResult().evmAddress()));
        }
        if (txnOutput.hasContractCall()) {
            trb.setEvmAddress(toByteString(txnOutput.contractCall().contractCallResult().evmAddress()));
        }
        if (txnOutput.hasCryptoTransfer()) {
            //            trb.evmAddress(txnOutput.cryptoTransfer().evmAddress());
        }
        if (txnOutput.hasEthereumCall()) {
            //            trb.evmAddress(txnOutput.ethereumCall().evmAddress());
        }
    }

    private static @NonNull ByteString toByteString(final Bytes bytes) {
        return ByteString.copyFrom(bytes.toByteArray());
    }
}
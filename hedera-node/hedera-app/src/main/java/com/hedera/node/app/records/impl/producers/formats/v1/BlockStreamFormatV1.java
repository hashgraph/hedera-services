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

package com.hedera.node.app.records.impl.producers.formats.v1;

import com.hedera.hapi.block.stream.BlockHeader;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.BlockProof;
import com.hedera.hapi.block.stream.EventMetadata;
import com.hedera.hapi.block.stream.FilteredBlockItem;
import com.hedera.hapi.block.stream.input.SystemTransaction;
import com.hedera.hapi.block.stream.output.CallContractOutput;
import com.hedera.hapi.block.stream.output.CreateContractOutput;
import com.hedera.hapi.block.stream.output.CreateScheduleOutput;
import com.hedera.hapi.block.stream.output.CryptoTransferOutput;
import com.hedera.hapi.block.stream.output.EthereumOutput;
import com.hedera.hapi.block.stream.output.RunningHashVersion;
import com.hedera.hapi.block.stream.output.SignScheduleOutput;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.block.stream.output.SubmitMessageOutput;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.block.stream.output.UtilPrngOutput;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.node.app.records.impl.producers.BlockStreamFormat;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

public final class BlockStreamFormatV1 implements BlockStreamFormat {

    public static final int VERSION_8 = 8;

    public static final BlockStreamFormat INSTANCE = new BlockStreamFormatV1();

    private BlockStreamFormatV1() {
        // prohibit instantiation
    }

    @Override
    public Bytes serializeBlockItem(@NonNull final BlockItem blockItem) {
        return BlockItem.PROTOBUF.toBytes(blockItem);
    }

    /** {@inheritDoc} */
    @Override
    public Bytes serializeBlockHeader(@NonNull final BlockHeader blockHeader) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Bytes serializeEventMetadata(@NonNull final EventMetadata eventMetadata) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Bytes serializeSystemTransaction(@NonNull final SystemTransaction systemTransaction) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Bytes serializeTransaction(@NonNull final Transaction transaction) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Bytes serializeTransactionResult(@NonNull final TransactionResult transactionResult) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Bytes serializeTransactionOutput(@NonNull final TransactionOutput transactionOutput) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Bytes serializeStateChanges(@NonNull final StateChanges stateChanges) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Bytes serializeBlockProof(@NonNull final BlockProof blockProof) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Bytes serializeFilteredBlockItem(@NonNull final FilteredBlockItem filteredBlockItem) {
        return null;
    }

    @NonNull
    private TransactionResult extractTransactionResult(@NonNull final SingleTransactionRecord item) {
        // TODO Is there a case where we don't have a receipt?
        final var receipt = item.transactionRecord().receiptOrThrow();
        final var transactionRecord = item.transactionRecord();

        return TransactionResult.newBuilder()
                .status(receipt.status())
                .consensusTimestamp(transactionRecord.consensusTimestamp())
                .parentConsensusTimestamp(transactionRecord.parentConsensusTimestamp())
                .exchangeRate(receipt.exchangeRate())
                .transactionFeeCharged(transactionRecord.transactionFee())
                .transferList(transactionRecord.transferList())
                .tokenTransferLists(transactionRecord.tokenTransferLists())
                .automaticTokenAssociations(transactionRecord.automaticTokenAssociations())
                .paidStakingRewards(transactionRecord.paidStakingRewards())
                // TODO: We need to surface this from services.
                //  .congestionPricingMultiplier(transactionRecord.congestionPricingMultiplier())
                .build();
    }

    @NonNull
    private TransactionOutput extractTransactionOutput(@NonNull final SingleTransactionRecord item) {
        final var builder = TransactionOutput.newBuilder();

        // This is never going to work because body is deprecated.
        // What we really want here is the TransactionBody. Is there a place we can get it from the
        // SingleTransactionRecord?

        // We can't always get the transaction type and use that to determine the transaction output. For example,
        // synthetic transactions from genesis piggyback on transactions.

        //        var body = item.transaction().body();
        //        if (body == null && item.transactionRecord().memo().equals("End of staking period calculation
        // record")) {
        //            // TODO: Skip this one for now till I figure out how we want to handle it.
        //            return builder.build();
        //        }
        // One case where it appears to be empty is when we have a transactionRecord

        // item.transactionRecord().body().kind()
        //        var body = item.transaction().bodyOrThrow();
        switch (item.transactionOutputs().transactionBodyType()) {
            case UNSET -> {} // NOOP
            case CONTRACT_CALL -> builder.contractCall(buildContractCallOutput(item));
            case CONTRACT_CREATE_INSTANCE -> builder.contractCreate(buildContractCreateOutput(item));
            case CONTRACT_UPDATE_INSTANCE -> {} // NOOP
            case CRYPTO_ADD_LIVE_HASH -> {} // NOOP
            case CRYPTO_CREATE_ACCOUNT -> {} // NOOP
            case CRYPTO_DELETE -> {} // NOOP
            case CRYPTO_DELETE_LIVE_HASH -> {} // NOOP
            case CRYPTO_TRANSFER -> builder.cryptoTransfer(buildCryptoTransferOutput(item));
            case CRYPTO_UPDATE_ACCOUNT -> {} // NOOP
            case FILE_APPEND -> {} // NOOP
            case FILE_CREATE -> {} // NOOP
            case FILE_DELETE -> {} // NOOP
            case FILE_UPDATE -> {} // NOOP
            case SYSTEM_DELETE -> {} // NOOP
            case SYSTEM_UNDELETE -> {} // NOOP
            case CONTRACT_DELETE_INSTANCE -> {} // NOOP
            case FREEZE -> {} // NOOP
            case CONSENSUS_CREATE_TOPIC -> {} // NOOP
            case CONSENSUS_UPDATE_TOPIC -> {} // NOOP
            case CONSENSUS_DELETE_TOPIC -> {} // NOOP
            case CONSENSUS_SUBMIT_MESSAGE -> builder.submitMessage(buildConsensusSubmitMessageOutput(item));
            case UNCHECKED_SUBMIT -> {} // NOOP
            case TOKEN_CREATION -> {} // NOOP
            case TOKEN_FREEZE -> {} // NOOP
            case TOKEN_UNFREEZE -> {} // NOOP
            case TOKEN_GRANT_KYC -> {} // NOOP
            case TOKEN_REVOKE_KYC -> {} // NOOP
            case TOKEN_DELETION -> {} // NOOP
            case TOKEN_UPDATE -> {} // NOOP
            case TOKEN_MINT -> {} // NOOP
            case TOKEN_BURN -> {} // NOOP
            case TOKEN_WIPE -> {} // NOOP
            case TOKEN_ASSOCIATE -> {} // NOOP
            case TOKEN_DISSOCIATE -> {} // NOOP
            case SCHEDULE_CREATE -> builder.createSchedule(buildScheduleCreateOutput(item));
            case SCHEDULE_DELETE -> {} // NOOP
            case SCHEDULE_SIGN -> builder.signSchedule(buildScheduleSignOutput(item));
            case TOKEN_FEE_SCHEDULE_UPDATE -> {} // NOOP
            case TOKEN_PAUSE -> {} // NOOP
            case TOKEN_UNPAUSE -> {} // NOOP
            case CRYPTO_APPROVE_ALLOWANCE -> {} // NOOP
            case CRYPTO_DELETE_ALLOWANCE -> {} // NOOP
            case ETHEREUM_TRANSACTION -> builder.ethereumCall(buildEthereumTransactionOutput(item));
            case NODE_STAKE_UPDATE -> {} // NOOP
            case UTIL_PRNG -> builder.utilPrng(buildUtilPrngOutput(item));
                // No default case, so we can get compiler warnings if we are missing one.
        }

        return builder.build();
    }

    @NonNull
    private CallContractOutput.Builder buildContractCallOutput(@NonNull final SingleTransactionRecord item) {
        return CallContractOutput.newBuilder()
                .sidecars(item.transactionSidecarRecords())
                .contractCallResult(item.transactionRecord().contractCallResult());
    }

    @NonNull
    private CreateContractOutput.Builder buildContractCreateOutput(@NonNull final SingleTransactionRecord item) {
        var transactionRecord = item.transactionRecord();
        return CreateContractOutput.newBuilder()
                .sidecars(item.transactionSidecarRecords())
                .contractCreateResult(transactionRecord.contractCreateResult());
    }

    @NonNull
    private CryptoTransferOutput.Builder buildCryptoTransferOutput(@NonNull final SingleTransactionRecord item) {
        return CryptoTransferOutput.newBuilder()
                .assessedCustomFees(item.transactionRecord().assessedCustomFees());
    }

    @NonNull
    private SubmitMessageOutput.Builder buildConsensusSubmitMessageOutput(@NonNull final SingleTransactionRecord item) {
        // TODO: Is there ever a case where we don't have a receipt?
        var receipt = item.transactionRecord().receiptOrThrow();
        return SubmitMessageOutput.newBuilder()
                .topicRunningHashVersion(RunningHashVersion.fromProtobufOrdinal(
                        (int) receipt.topicRunningHashVersion()));
    }

    @NonNull
    private CreateScheduleOutput.Builder buildScheduleCreateOutput(@NonNull final SingleTransactionRecord item) {
        // TODO: Is there ever a case where we don't have a receipt?
        var receipt = item.transactionRecord().receiptOrThrow();
        return CreateScheduleOutput.newBuilder().scheduledTransactionId(receipt.scheduledTransactionID());
    }

    @NonNull
    private SignScheduleOutput.Builder buildScheduleSignOutput(@NonNull final SingleTransactionRecord item) {
        // TODO: Is there ever a case where we don't have a receipt?
        var receipt = item.transactionRecord().receiptOrThrow();
        return SignScheduleOutput.newBuilder().scheduledTransactionId(receipt.scheduledTransactionID());
    }

    @NonNull
    private EthereumOutput.Builder buildEthereumTransactionOutput(@NonNull final SingleTransactionRecord item) {
        return EthereumOutput.newBuilder()
                .sidecars(item.transactionSidecarRecords())
                .ethereumHash(item.transactionRecord().ethereumHash());
    }

    @NonNull
    private UtilPrngOutput.Builder buildUtilPrngOutput(@NonNull final SingleTransactionRecord item) {
        var builder = UtilPrngOutput.newBuilder();
        var record = item.transactionRecord();
        assert record.hasPrngBytes() ^ record.hasPrngNumber() : "Exactly one of prngBytes or prngNumber must be set";
        if (record.hasPrngBytes()) builder.prngBytes(record.prngBytesOrThrow());
        if (record.hasPrngNumber()) builder.prngNumber(record.prngNumberOrThrow());
        return builder;
    }
}

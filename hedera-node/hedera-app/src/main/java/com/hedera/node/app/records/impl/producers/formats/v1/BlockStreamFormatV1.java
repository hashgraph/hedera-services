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
import com.hedera.hapi.block.stream.input.StateSignatureSystemTransaction;
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
import com.swirlds.common.crypto.DigestType;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * A block stream format that uses the v1 format.
 */
public final class BlockStreamFormatV1 implements BlockStreamFormat {

    public static final int VERSION_8 = 8;

    public static final BlockStreamFormat INSTANCE = new BlockStreamFormatV1();

    private BlockStreamFormatV1() {
        // prohibit instantiation
    }

    /** {@inheritDoc} */
    @NonNull
    public Bytes computeNewRunningHash(
            @NonNull final MessageDigest messageDigest,
            @NonNull final Bytes startRunningHash,
            @NonNull final Bytes serializedItem) {

        byte[] previousHash = startRunningHash.toByteArray();

        // Hash the block item
        serializedItem.writeTo(messageDigest);
        final byte[] serializedItemHash = messageDigest.digest();

        // now hash the previous hash and the item hash
        messageDigest.update(previousHash);
        messageDigest.update(serializedItemHash);
        previousHash = messageDigest.digest();
        return Bytes.wrap(previousHash);
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public MessageDigest getMessageDigest() {
        try {
            return MessageDigest.getInstance(DigestType.SHA_384.algorithmName());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Bytes serializeBlockItem(@NonNull final BlockItem blockItem) {
        return BlockItem.PROTOBUF.toBytes(blockItem);
    }

    /** {@inheritDoc} */
    @Override
    public Bytes serializeBlockHeader(@NonNull final BlockHeader blockHeader) {
        BlockItem blockItem = BlockItem.newBuilder().header(blockHeader).build();
        return serializeBlockItem(blockItem);
    }

    /** {@inheritDoc} */
    @Override
    public Bytes serializeEventMetadata(@NonNull final EventMetadata eventMetadata) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Bytes serializeSystemTransaction(@NonNull final ConsensusTransaction systemTransaction) {
        var systemTxnBuilder = SystemTransaction.newBuilder();

        switch (systemTransaction) {
            case StateSignatureTransaction stateSignatureTransaction -> {
                var stateSig = new byte[48];
                var stateHash = new byte[48];
                long round = 0;
                final byte[] bytes = new byte[48];
                new SecureRandom().nextBytes(bytes);
                systemTxnBuilder.stateSignature(StateSignatureSystemTransaction.newBuilder()
                        .stateSignature(Bytes.wrap(stateSig))
                        .stateHash(Bytes.wrap(stateHash))
                        .epochHash(Bytes.wrap(bytes))
                        .round(round));
            }
            default -> {
                // The may be a new ConsensusTransaction being produced that is unhandled and needs to be
                //  added to this switch.
                throw new RuntimeException(
                        String.format("Unhandled ConsensusTransaction type: %s", systemTransaction.getClass()));
            }
        }

        return serializeBlockItem(
                BlockItem.newBuilder().systemTransaction(systemTxnBuilder).build());
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
        return SubmitMessageOutput.newBuilder().topicRunningHashVersion(RunningHashVersion.fromProtobufOrdinal((int)
                receipt.topicRunningHashVersion()));
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

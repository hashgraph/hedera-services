/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.records.streams.impl.producers.formats.v1;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.streams.v7.*;
import com.hedera.node.app.records.streams.impl.producers.BlockStreamFormat;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.app.version.HederaSoftwareVersion;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Stream;

/**
 * This is a prototype for a RecordFileWriter for a streamlined version of V6 format, going to true protobuf and
 * no SelfSerializable formatting. It is the first iteration of the new Block format where all serialized data is
 * contained within a block.
 *
 * <p>While this is V1 of the BlockStream, it is actually V7 of the record stream formats. Readers of the record stream
 * first read the first byte to identify the version, because of this we can't change the version of the record stream
 * format without breaking existing readers. This is why this is V7 of the record stream format.
 */
public final class BlockStreamFormatV1 implements BlockStreamFormat {
    /** The version of this format */
    public static final int VERSION_7 = 7;

    public static final BlockStreamFormat INSTANCE = new BlockStreamFormatV1();

    private BlockStreamFormatV1() {
        // Prohibit instantiation
    }

    // BlockStreamFormat ===============================================================================================

    /** {@inheritDoc} */
    @NonNull
    private Bytes serializeBlockItem(@NonNull final BlockItem blockItem) {
        // The only thing we need to serialize is the BlockItem itself.
        return BlockItem.PROTOBUF.toBytes(blockItem);
    }

    public Bytes serializeBlockHeader(@NonNull final BlockHeader blockHeader) {
        return serializeBlockItem(BlockItem.newBuilder().header(blockHeader).build());
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public Bytes serializeConsensusEvent(@NonNull final ConsensusEvent consensusEvent) {
        // TODO(nickpoorman): Update this code once Platform has Events defined in protobuf.
        EventImpl eventImpl = (EventImpl) consensusEvent;
        HederaSoftwareVersion softwareVersion = (HederaSoftwareVersion) eventImpl.getSoftwareVersion();
        assert softwareVersion != null;
        var version = requireNonNull(softwareVersion.getServicesVersion());

        // These will be null on the first round for genesis.
        final var selfParent = eventImpl.getSelfParent();
        // TODO(nickpoorman): There is something wrong when we try to collect signatures from preHandle. Need to
        //  figure out what is happening.
        //        if (selfParent != null && selfParent.getHash() == null) {
        //            throw new RuntimeException("SelfParent hash is null");
        //        }
        final var otherParent = eventImpl.getOtherParent();

        final var blockItem = BlockItem.newBuilder()
                .startEvent(EventMetadata.newBuilder()
                        //  This is the version of platform that created this event.
                        .softwareVersion(version)
                        .creatorId(consensusEvent.getCreatorId().id())
                        .gen(eventImpl.getGeneration())
                        .birthRound(eventImpl.getHashedData().getBirthRound()) // This may not be populated yet
                        .selfParent(EventDescriptor.newBuilder()
                                .creatorId(
                                        selfParent == null
                                                ? -1
                                                : selfParent.getCreatorId().id())
                                .gen(selfParent == null ? -1 : selfParent.getGeneration())
                                .birthRound(selfParent == null ? -1 : selfParent.getRoundCreated())
                                .hash(Bytes.wrap(
                                        selfParent == null
                                                ? new byte[0]
                                                : selfParent.getHash().copyToByteArray())))
                        // FUTURE: Include all the parents once platform exposes them.
                        .otherParents(EventDescriptor.newBuilder()
                                .creatorId(
                                        otherParent == null
                                                ? -1
                                                : otherParent.getCreatorId().id())
                                .gen(otherParent == null ? -1 : otherParent.getGeneration())
                                .birthRound(otherParent == null ? -1 : otherParent.getRoundCreated())
                                .hash(Bytes.wrap(
                                        otherParent == null
                                                ? new byte[0]
                                                : otherParent.getHash().copyToByteArray()))
                                .build())
                        .timeCreated(Timestamp.newBuilder()
                                .seconds(consensusEvent.getTimeCreated().getEpochSecond())
                                .nanos(consensusEvent.getTimeCreated().getNano()))
                        .hash(Bytes.wrap(
                                eventImpl.getHash() == null
                                        ? new byte[0]
                                        : eventImpl.getHash().copyToByteArray()))
                        .signature(eventImpl.getSignature())
                        .consensusData(ConsensusData.newBuilder()
                                .consensusTimestamp(Timestamp.newBuilder()
                                        .seconds(consensusEvent
                                                .getConsensusTimestamp()
                                                .getEpochSecond())
                                        .nanos(consensusEvent
                                                .getConsensusTimestamp()
                                                .getNano()))
                                .round(eventImpl.getRoundReceived())
                                .order(consensusEvent.getConsensusOrder())))
                .build();

        return serializeBlockItem(blockItem);
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public Bytes serializeSystemTransaction(@NonNull final ConsensusTransaction systemTxn) {
        var systemTxnBuilder = SystemTransaction.newBuilder();

        switch (systemTxn) {
            case StateSignatureTransaction stateSignatureTransaction -> {
                //todo: add API to get the state/block? sig from StateSignatureTransaction, then uncomment below
                // Ensure the signing algorithm is the one we set in the block header for this version.
//                assert stateSignatureTransaction
//                                .getStateSignature()
//                                .getSignatureType()
//                                .signingAlgorithm()
//                                .equals(com.swirlds.common.crypto.SignatureType.RSA.signingAlgorithm())
//                        : "SignatureType must be SHA384withRSA for BlockStreamFormatV1";

                // TODO(nickpoorman): Not sure why this is null, maybe platform doesn't populate it yet. Fake it for
                //  now.
                //                final var epochHash = requireNonNull(stateSignatureTransaction.getEpochHash(),
                // "EpochHash is null");

                //todo: add API to get the following from StateSignatureTransaction
//                var stateSig = stateSignatureTransaction.getStateSignature().getBytes();
//                var stateHash = stateSignatureTransaction.getStateHash();
//                long round = stateSignatureTransaction.getRound();
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

                // These are no longer used by Platform, but we may want to leave them here if we are going to back-fill
                // the Block Stream.
                //            case SystemTransactionBitsPerSecond systemTransactionBitsPerSecond -> {
                //                // We should probably have a PBJ mechanism for supplying an []long instead of
                // List<Long>.
                //                List<Long> bitsPerSecSent =
                // Arrays.stream(systemTransactionBitsPerSecond.getAvgBitsPerSecSent())
                //                        .boxed()
                //                        .collect(Collectors.toList());
                //                systemTxnBuilder.bitsPerSecond(
                //
                // BitsPerSecondSystemTransaction.newBuilder().avgBitsPerSecSent(bitsPerSecSent));
                //            }
                //            case SystemTransactionPing systemTransactionPing -> {
                //                // We should probably have a PBJ mechanism for supplying an []int instead of
                // List<Integer>.
                //                List<Integer> avgPingMillis =
                // Arrays.stream(systemTransactionPing.getAvgPingMilliseconds())
                //                        .boxed()
                //                        .collect(Collectors.toList());
                //
                // systemTxnBuilder.ping(PingSystemTransaction.newBuilder().avgPingMilliseconds(avgPingMillis));
                //            }

            default -> {
                // The may be a new ConsensusTransaction being produced that is unhandled and needs to be
                //  added to this switch.
                throw new RuntimeException(
                        String.format("Unhandled ConsensusTransaction type: %s", systemTxn.getClass()));
            }
        }

        return serializeBlockItem(
                BlockItem.newBuilder().systemTransaction(systemTxnBuilder).build());
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public Stream<Bytes> serializeUserTransaction(@NonNull final SingleTransactionRecord item) {
        // User transactions produce a series of BlockItems.
        final var blockItems = List.of(
                // First is Transaction
                BlockItem.newBuilder().transaction(item.transaction()).build(),
                // Second is TransactionResult
                BlockItem.newBuilder()
                        .transactionResult(extractTransactionResult(item))
                        .build(),
                // Third is TransactionOutput
                BlockItem.newBuilder()
                        .transactionOutput(extractTransactionOutput(item))
                        .build());

        // We serialize each BlockItem individually.
        return blockItems.stream().map(this::serializeBlockItem);
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public Bytes serializeStateChanges(@NonNull final StateChanges stateChanges) {
        // We can have multiple state changes in a single BlockItem.
        return serializeBlockItem(
                BlockItem.newBuilder().stateChanges(stateChanges).build());
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public Bytes serializeBlockStateProof(@NonNull final BlockStateProof stateProof) {
        return serializeBlockItem(BlockItem.newBuilder().stateProof(stateProof).build());
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

    /** {@inheritDoc} */
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

    // private methods =================================================================================================

    @NonNull
    private TransactionResult extractTransactionResult(@NonNull final SingleTransactionRecord item) {
        // Is there a case where we don't have a receipt?
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
                // TODO(nickpoorman): We need to surface this from services.
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
        //            // TODO(nickpoorman): Skip this one for now till I figure out how we want to handle it.
        //            return builder.build();
        //        }
        // One case where it appears to be empty is when we have a transactionRecord

        // item.transactionRecord().body().kind()
        //        var body = item.transaction().bodyOrThrow();
        switch (item.transactionOutputs().transactionBodyType()) {
            case UNSET -> {} // NOOP
            case CONTRACT_CALL -> builder.contractCallOutput(buildContractCallOutput(item));
            case CONTRACT_CREATE_INSTANCE -> builder.contractCreateOutput(buildContractCreateOutput(item));
            case CONTRACT_UPDATE_INSTANCE -> {} // NOOP
            case CRYPTO_ADD_LIVE_HASH -> {} // NOOP
            case CRYPTO_CREATE_ACCOUNT -> builder.cryptoCreateAccountOutput(buildCryptoCreateOutput(item));
            case CRYPTO_DELETE -> {} // NOOP
            case CRYPTO_DELETE_LIVE_HASH -> {} // NOOP
            case CRYPTO_TRANSFER -> builder.cryptoTransferOutput(buildCryptoTransferOutput(item));
            case CRYPTO_UPDATE_ACCOUNT -> {} // NOOP
            case FILE_APPEND -> {} // NOOP
            case FILE_CREATE -> builder.fileCreateOuput(buildFileCreateOutput(item));
            case FILE_DELETE -> {} // NOOP
            case FILE_UPDATE -> {} // NOOP
            case SYSTEM_DELETE -> {} // NOOP
            case SYSTEM_UNDELETE -> {} // NOOP
            case CONTRACT_DELETE_INSTANCE -> {} // NOOP
            case FREEZE -> {} // NOOP
            case CONSENSUS_CREATE_TOPIC -> builder.consensusCreateTopicOuput(buildConsensusCreateTopicOutput(item));
            case CONSENSUS_UPDATE_TOPIC -> {} // NOOP
            case CONSENSUS_DELETE_TOPIC -> {} // NOOP
            case CONSENSUS_SUBMIT_MESSAGE -> builder.consensusSubmitMessageOuput(
                    buildConsensusSubmitMessageOutput(item));
            case UNCHECKED_SUBMIT -> {} // NOOP
            case TOKEN_CREATION -> builder.tokenCreationOuput(buildTokenCreationOutput(item));
            case TOKEN_FREEZE -> {} // NOOP
            case TOKEN_UNFREEZE -> {} // NOOP
            case TOKEN_GRANT_KYC -> {} // NOOP
            case TOKEN_REVOKE_KYC -> {} // NOOP
            case TOKEN_DELETION -> {} // NOOP
            case TOKEN_UPDATE -> {} // NOOP
            case TOKEN_MINT -> builder.tokenMintOuput(buildTokenMintOutput(item));
            case TOKEN_BURN -> builder.tokenBurnOuput(buildTokenBurnOutput(item));
            case TOKEN_WIPE -> builder.tokenWipeOuput(buildTokenWipeOutput(item));
            case TOKEN_ASSOCIATE -> {} // NOOP
            case TOKEN_DISSOCIATE -> {} // NOOP
            case SCHEDULE_CREATE -> builder.scheduleCreateOuput(buildScheduleCreateOutput(item));
            case SCHEDULE_DELETE -> {} // NOOP
            case SCHEDULE_SIGN -> builder.scheduleSignOuput(buildScheduleSignOutput(item));
            case TOKEN_FEE_SCHEDULE_UPDATE -> {} // NOOP
            case TOKEN_PAUSE -> {} // NOOP
            case TOKEN_UNPAUSE -> {} // NOOP
            case CRYPTO_APPROVE_ALLOWANCE -> {} // NOOP
            case CRYPTO_DELETE_ALLOWANCE -> {} // NOOP
            case ETHEREUM_TRANSACTION -> builder.ethereumOuput(buildEthereumTransactionOutput(item));
            case NODE_STAKE_UPDATE -> {} // NOOP
            case UTIL_PRNG -> builder.utilPrngOuput(buildUtilPrngOutput(item));
                // No default case, so we can get compiler warnings if we are missing one.
        }

        return builder.build();
    }

    // Output builders =================================================================================================

    @NonNull
    private ContractCallOutput.Builder buildContractCallOutput(@NonNull final SingleTransactionRecord item) {
        return ContractCallOutput.newBuilder()
                .sidecars(item.transactionSidecarRecords())
                .contractCallResult(item.transactionRecord().contractCallResult());
    }

    @NonNull
    private ContractCreateOutput.Builder buildContractCreateOutput(@NonNull final SingleTransactionRecord item) {
        var transactionRecord = item.transactionRecord();
        return ContractCreateOutput.newBuilder()
                .sidecars(item.transactionSidecarRecords())
                .contractCreateResult(transactionRecord.contractCreateResult())
                .alias(transactionRecord.alias());
    }

    private CryptoCreateOutput.Builder buildCryptoCreateOutput(@NonNull final SingleTransactionRecord item) {
        // TODO(nickpoorman): Is there ever a case where we don't have a receipt?
        var receipt = item.transactionRecord().receiptOrThrow();
        return CryptoCreateOutput.newBuilder()
                .accountId(receipt.accountID())
                .evmAddress(item.transactionRecord().evmAddress());
    }

    @NonNull
    private CryptoTransferOutput.Builder buildCryptoTransferOutput(@NonNull final SingleTransactionRecord item) {
        return CryptoTransferOutput.newBuilder()
                .assessedCustomFees(item.transactionRecord().assessedCustomFees());
    }

    @NonNull
    private FileCreateOutput.Builder buildFileCreateOutput(@NonNull final SingleTransactionRecord item) {
        // TODO(nickpoorman): Is there ever a case where we don't have a receipt?
        var receipt = item.transactionRecord().receiptOrThrow();
        return FileCreateOutput.newBuilder().fileId(receipt.fileID());
    }

    @NonNull
    private ConsensusCreateTopicOutput.Builder buildConsensusCreateTopicOutput(
            @NonNull final SingleTransactionRecord item) {
        // TODO(nickpoorman): Is there ever a case where we don't have a receipt?
        var receipt = item.transactionRecord().receiptOrThrow();
        return ConsensusCreateTopicOutput.newBuilder().topicId(receipt.topicID());
    }

    @NonNull
    private ConsensusSubmitMessageOutput.Builder buildConsensusSubmitMessageOutput(
            @NonNull final SingleTransactionRecord item) {
        // TODO(nickpoorman): Is there ever a case where we don't have a receipt?
        var receipt = item.transactionRecord().receiptOrThrow();
        return ConsensusSubmitMessageOutput.newBuilder()
                .topicSequenceNumber(receipt.topicSequenceNumber())
                .topicRunningHash(receipt.topicRunningHash())
                .topicRunningHashVersion(receipt.topicRunningHashVersion());
    }

    @NonNull
    private TokenCreateOutput.Builder buildTokenCreationOutput(@NonNull final SingleTransactionRecord item) {
        // TODO(nickpoorman): Is there ever a case where we don't have a receipt?
        var receipt = item.transactionRecord().receiptOrThrow();
        return TokenCreateOutput.newBuilder().tokenId(receipt.tokenID());
    }

    @NonNull
    private TokenMintOutput.Builder buildTokenMintOutput(@NonNull final SingleTransactionRecord item) {
        if (isMintNonFungibleToken(item)) {
            return TokenMintOutput.newBuilder()
                    .tokenMintNonFungibleUniqueOutput(buildTokenMintNonFungibleUniqueOutput(item));
        } else {
            return TokenMintOutput.newBuilder().tokenMintFungibleCommonOutput(buildTokenMintFungibleCommonOutput(item));
        }
    }

    private boolean isMintNonFungibleToken(@NonNull final SingleTransactionRecord item) {
        // TODO(nickpoorman): I really don't like this. We should extract a definitive enum type from
        //  the SingleTransactionRecord.
        //        var metadata = item.transaction().bodyOrThrow().tokenMintOrThrow().metadata();
        //        if (metadata == null) {
        //            return false;
        //        }
        //        return !metadata.isEmpty();

        assert item.transactionOutputs().tokenType() != null : "tokenType must not be null";
        return item.transactionOutputs().tokenType() == TokenType.NON_FUNGIBLE_UNIQUE;
    }

    @NonNull
    private TokenMintNonFungibleUniqueOutput.Builder buildTokenMintNonFungibleUniqueOutput(
            @NonNull final SingleTransactionRecord item) {
        // TODO(nickpoorman): Is there ever a case where we don't have a receipt?
        var receipt = item.transactionRecord().receiptOrThrow();
        return TokenMintNonFungibleUniqueOutput.newBuilder()
                .newTotalSupply(receipt.newTotalSupply())
                .serialNumbers(receipt.serialNumbers());
    }

    @NonNull
    private TokenMintFungibleCommonOutput.Builder buildTokenMintFungibleCommonOutput(
            @NonNull final SingleTransactionRecord item) {
        // TODO(nickpoorman): Is there ever a case where we don't have a receipt?
        var receipt = item.transactionRecord().receiptOrThrow();
        return TokenMintFungibleCommonOutput.newBuilder().newTotalSupply(receipt.newTotalSupply());
    }

    private boolean isBurnNonFungibleToken(@NonNull final SingleTransactionRecord item) {
        // TODO(nickpoorman): I really don't like this. We should extract a definitive enum type from
        //  the SingleTransactionRecord.
        //        var serials = item.transaction().bodyOrThrow().tokenBurnOrThrow().serialNumbers();
        //        if (serials == null) {
        //            return false;
        //        }
        //        return !serials.isEmpty();

        assert item.transactionOutputs().tokenType() != null : "tokenType must not be null";
        return item.transactionOutputs().tokenType() == TokenType.NON_FUNGIBLE_UNIQUE;
    }

    @NonNull
    private TokenBurnOutput.Builder buildTokenBurnOutput(@NonNull final SingleTransactionRecord item) {
        if (isBurnNonFungibleToken(item)) {
            return TokenBurnOutput.newBuilder()
                    .tokenBurnNonFungibleUniqueOutput(buildTokenBurnNonFungibleUniqueOutput(item));
        } else {
            return TokenBurnOutput.newBuilder().tokenBurnFungibleCommonOutput(buildTokenBurnFungibleCommonOutput(item));
        }
    }

    @NonNull
    private TokenBurnNonFungibleUniqueOutput.Builder buildTokenBurnNonFungibleUniqueOutput(
            @NonNull final SingleTransactionRecord item) {
        // TODO(nickpoorman): Is there ever a case where we don't have a receipt?
        var receipt = item.transactionRecord().receiptOrThrow();
        return TokenBurnNonFungibleUniqueOutput.newBuilder().newTotalSupply(receipt.newTotalSupply());
    }

    @NonNull
    private TokenBurnFungibleCommonOutput.Builder buildTokenBurnFungibleCommonOutput(
            @NonNull final SingleTransactionRecord item) {
        // TODO(nickpoorman): Is there ever a case where we don't have a receipt?
        var receipt = item.transactionRecord().receiptOrThrow();
        return TokenBurnFungibleCommonOutput.newBuilder().newTotalSupply(receipt.newTotalSupply());
    }

    private boolean isWipeNonFungibleToken(@NonNull final SingleTransactionRecord item) {
        // TODO(nickpoorman): I really don't like this. We should extract a definitive enum type from
        //  the SingleTransactionRecord.
        //        var serials = item.transaction().bodyOrThrow().tokenWipeOrThrow().serialNumbers();
        //        if (serials == null) {
        //            return false;
        //        }
        //        return !serials.isEmpty();

        assert item.transactionOutputs().tokenType() != null : "tokenType must not be null";
        return item.transactionOutputs().tokenType() == TokenType.NON_FUNGIBLE_UNIQUE;
    }

    @NonNull
    private TokenWipeAccountOutput.Builder buildTokenWipeOutput(@NonNull final SingleTransactionRecord item) {
        if (isWipeNonFungibleToken(item)) {
            return TokenWipeAccountOutput.newBuilder()
                    .tokenWipeNonFungibleUniqueOutput(buildTokenWipeNonFungibleUniqueOutput(item));
        } else {
            return TokenWipeAccountOutput.newBuilder()
                    .tokenWipeFungibleCommonOutput(buildTokenWipeFungibleCommonOutput(item));
        }
    }

    @NonNull
    private TokenWipeAccountNonFungibleUniqueOutput.Builder buildTokenWipeNonFungibleUniqueOutput(
            @NonNull final SingleTransactionRecord item) {
        // TODO(nickpoorman): Is there ever a case where we don't have a receipt?
        var receipt = item.transactionRecord().receiptOrThrow();
        return TokenWipeAccountNonFungibleUniqueOutput.newBuilder().newTotalSupply(receipt.newTotalSupply());
    }

    @NonNull
    private TokenWipeAccountFungibleCommonOutput.Builder buildTokenWipeFungibleCommonOutput(
            @NonNull final SingleTransactionRecord item) {
        // TODO(nickpoorman): Is there ever a case where we don't have a receipt?
        var receipt = item.transactionRecord().receiptOrThrow();
        return TokenWipeAccountFungibleCommonOutput.newBuilder().newTotalSupply(receipt.newTotalSupply());
    }

    @NonNull
    private ScheduleCreateOutput.Builder buildScheduleCreateOutput(@NonNull final SingleTransactionRecord item) {
        // TODO(nickpoorman): Is there ever a case where we don't have a receipt?
        var receipt = item.transactionRecord().receiptOrThrow();
        return ScheduleCreateOutput.newBuilder()
                .scheduleId(receipt.scheduleID())
                .scheduledTransactionId(receipt.scheduledTransactionID())
                .scheduleRef(item.transactionRecord().scheduleRef());
    }

    @NonNull
    private ScheduleSignOutput.Builder buildScheduleSignOutput(@NonNull final SingleTransactionRecord item) {
        // TODO(nickpoorman): Is there ever a case where we don't have a receipt?
        var receipt = item.transactionRecord().receiptOrThrow();
        return ScheduleSignOutput.newBuilder()
                .scheduledTransactionId(receipt.scheduledTransactionID())
                .scheduleRef(item.transactionRecord().scheduleRef());
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

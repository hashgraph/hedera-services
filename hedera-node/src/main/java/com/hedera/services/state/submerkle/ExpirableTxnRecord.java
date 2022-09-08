/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.submerkle;

import static com.hedera.services.context.SideEffectsTracker.MAX_PSEUDORANDOM_BYTES_LENGTH;
import static com.hedera.services.context.SideEffectsTracker.MISSING_NUMBER;
import static com.hedera.services.legacy.proto.utils.ByteStringUtils.wrapUnsafely;
import static com.hedera.services.state.merkle.internals.BitPackUtils.packedTime;
import static com.hedera.services.state.serdes.IoUtils.readNullable;
import static com.hedera.services.state.serdes.IoUtils.readNullableSerializable;
import static com.hedera.services.state.serdes.IoUtils.writeNullable;
import static com.hedera.services.state.serdes.IoUtils.writeNullableSerializable;
import static com.hedera.services.state.serdes.IoUtils.writeNullableString;
import static com.hedera.services.utils.MiscUtils.asTimestamp;
import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.legacy.proto.utils.ByteStringUtils;
import com.hedera.services.state.merkle.internals.BitPackUtils;
import com.hedera.services.state.serdes.IoUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.SerializableHashable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.utility.CommonUtils;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

public class ExpirableTxnRecord implements FastCopyable, SerializableHashable {
    public static final long UNKNOWN_SUBMITTING_MEMBER = -1;
    public static final long MISSING_PARENT_CONSENSUS_TIMESTAMP = -1;
    public static final short NO_CHILD_TRANSACTIONS = 0;

    public static final byte[] MISSING_PSEUDORANDOM_BYTES = new byte[0];

    static final List<EntityId> NO_TOKENS = null;
    static final List<CurrencyAdjustments> NO_TOKEN_ADJUSTMENTS = null;
    static final List<NftAdjustments> NO_NFT_TOKEN_ADJUSTMENTS = null;
    static final List<FcAssessedCustomFee> NO_CUSTOM_FEES = null;
    static final EntityId NO_SCHEDULE_REF = null;
    static final List<FcTokenAssociation> NO_NEW_TOKEN_ASSOCIATIONS = Collections.emptyList();

    private static final byte[] MISSING_TXN_HASH = new byte[0];

    static final int RELEASE_0250_VERSION = 8;
    static final int RELEASE_0260_VERSION = 9;
    static final int RELEASE_0270_VERSION = 10;

    static final int RELEASE_0280_VERSION = 11;
    static final int CURRENT_VERSION = RELEASE_0280_VERSION;
    static final long RUNTIME_CONSTRUCTABLE_ID = 0x8b9ede7ca8d8db93L;

    static final int MAX_MEMO_BYTES = 32 * 1_024;
    static final int MAX_TXN_HASH_BYTES = 1_024;
    static final int MAX_INVOLVED_TOKENS = 10;
    static final int MAX_ASSESSED_CUSTOM_FEES_CHANGES = 20;
    public static final ByteString MISSING_ALIAS = ByteString.EMPTY;
    public static final byte[] MISSING_ETHEREUM_HASH = new byte[0];

    private static final byte NO_PRNG_OUTPUT = (byte) 0x00;
    private static final byte PRNG_INT_OUTPUT = (byte) 0x01;
    private static final byte PRNG_BYTES_OUTPUT = (byte) 0x02;
    private long expiry;
    private long submittingMember = UNKNOWN_SUBMITTING_MEMBER;
    private long fee;
    private long packedParentConsensusTime = MISSING_PARENT_CONSENSUS_TIMESTAMP;
    private short numChildRecords = NO_CHILD_TRANSACTIONS;
    private Hash hash;
    private TxnId txnId;
    private byte[] txnHash = MISSING_TXN_HASH;
    private String memo;
    private TxnReceipt receipt;
    private RichInstant consensusTime;
    private CurrencyAdjustments hbarAdjustments;
    private CurrencyAdjustments stakingRewardsPaid;
    private EvmFnResult contractCallResult;
    private EvmFnResult contractCreateResult;
    // IMPORTANT: This class depends on the invariant that if any of the
    // three token-related lists below (tokens, tokenAdjustments, and
    // nftTokenAdjustments) is non-null, then it has the same length as any
    // other non-null list. This would not be necessary if we provided the
    // class with information on the fungibility of the token types---and
    // this information is always available when the Builder is constructed.
    private List<EntityId> tokens = NO_TOKENS;
    private List<CurrencyAdjustments> tokenAdjustments = NO_TOKEN_ADJUSTMENTS;
    private List<NftAdjustments> nftTokenAdjustments = NO_NFT_TOKEN_ADJUSTMENTS;
    private EntityId scheduleRef = NO_SCHEDULE_REF;
    private List<FcAssessedCustomFee> assessedCustomFees = NO_CUSTOM_FEES;
    private List<FcTokenAssociation> newTokenAssociations = NO_NEW_TOKEN_ASSOCIATIONS;
    private ByteString alias = MISSING_ALIAS;
    private byte[] ethereumHash = MISSING_ETHEREUM_HASH;
    private byte[] pseudoRandomBytes = MISSING_PSEUDORANDOM_BYTES;
    private int pseudoRandomNumber = MISSING_NUMBER;

    public ExpirableTxnRecord() {
        /* RuntimeConstructable */
    }

    public ExpirableTxnRecord(Builder builder) {
        this.receipt =
                (builder.receiptBuilder != null) ? builder.receiptBuilder.build() : builder.receipt;
        this.txnHash = builder.txnHash;
        this.txnId = builder.txnId;
        this.consensusTime = builder.consensusTime;
        this.memo = builder.memo;
        this.fee = builder.fee;
        this.hbarAdjustments = builder.hbarAdjustments;
        this.stakingRewardsPaid = builder.stakingRewardsPaid;
        this.contractCallResult = builder.contractCallResult;
        this.contractCreateResult = builder.contractCreateResult;
        this.tokens = builder.tokens;
        this.tokenAdjustments = builder.tokenAdjustments;
        this.nftTokenAdjustments = builder.nftTokenAdjustments;
        this.scheduleRef = builder.scheduleRef;
        this.assessedCustomFees = builder.assessedCustomFees;
        this.newTokenAssociations = builder.newTokenAssociations;
        this.packedParentConsensusTime = builder.packedParentConsensusTime;
        this.numChildRecords = builder.numChildRecords;
        this.alias = builder.alias;
        this.ethereumHash = builder.ethereumHash;
        this.pseudoRandomNumber = builder.pseudoRandomNumber;
        this.pseudoRandomBytes = builder.pseudoRandomBytes;
    }

    /* --- Object --- */
    @Override
    public String toString() {
        var helper =
                MoreObjects.toStringHelper(this)
                        .omitNullValues()
                        .add("numChildRecords", numChildRecords)
                        .add("receipt", receipt)
                        .add("fee", fee)
                        .add("txnHash", CommonUtils.hex(txnHash))
                        .add("txnId", txnId)
                        .add("consensusTimestamp", consensusTime)
                        .add("expiry", expiry)
                        .add("submittingMember", submittingMember)
                        .add("memo", memo)
                        .add("contractCreation", contractCreateResult)
                        .add("contractCall", contractCallResult)
                        .add("hbarAdjustments", hbarAdjustments)
                        .add("stakingRewardsPaid", stakingRewardsPaid)
                        .add("scheduleRef", scheduleRef)
                        .add("alias", alias.toStringUtf8())
                        .add("ethereumHash", CommonUtils.hex(ethereumHash))
                        .add("pseudoRandomNumber", pseudoRandomNumber)
                        .add("pseudoRandomBytes", CommonUtils.hex(pseudoRandomBytes));

        if (packedParentConsensusTime != MISSING_PARENT_CONSENSUS_TIMESTAMP) {
            helper.add(
                    "parentConsensusTime",
                    Instant.ofEpochSecond(
                            BitPackUtils.unsignedHighOrder32From(packedParentConsensusTime),
                            BitPackUtils.signedLowOrder32From(packedParentConsensusTime)));
        }

        if (tokens != NO_TOKENS) {
            int n = tokens.size();
            var readable =
                    IntStream.range(0, n)
                            .mapToObj(
                                    i ->
                                            String.format(
                                                    "%s(%s)",
                                                    tokens.get(i).toAbbrevString(),
                                                    reprOfNonEmptyChange(
                                                            i,
                                                            tokenAdjustments,
                                                            nftTokenAdjustments)))
                            .collect(joining(", "));
            helper.add("tokenAdjustments", readable);
        }

        if (assessedCustomFees != NO_CUSTOM_FEES) {
            var readable =
                    assessedCustomFees.stream()
                            .map(assessedCustomFee -> String.format("(%s)", assessedCustomFee))
                            .collect(joining(", "));
            helper.add("assessedCustomFees", readable);
        }

        if (newTokenAssociations != NO_NEW_TOKEN_ASSOCIATIONS) {
            var readable =
                    newTokenAssociations.stream()
                            .map(newTokenAssociation -> String.format("(%s)", newTokenAssociation))
                            .collect(joining(", "));
            helper.add("newTokenAssociations", readable);
        }
        return helper.toString();
    }

    private String reprOfNonEmptyChange(
            final int i,
            final List<CurrencyAdjustments> tokenAdjustments,
            final List<NftAdjustments> nftTokenAdjustments) {
        final var fungibleAdjust = tokenAdjustments.get(i);
        return fungibleAdjust.isEmpty()
                ? nftTokenAdjustments.get(i).toString()
                : fungibleAdjust.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || ExpirableTxnRecord.class != o.getClass()) {
            return false;
        }
        var that = (ExpirableTxnRecord) o;
        return this.fee == that.fee
                && this.numChildRecords == that.numChildRecords
                && this.packedParentConsensusTime == that.packedParentConsensusTime
                && this.expiry == that.expiry
                && this.submittingMember == that.submittingMember
                && Objects.equals(this.receipt, that.receipt)
                && Arrays.equals(this.txnHash, that.txnHash)
                && this.txnId.equals(that.txnId)
                && Objects.equals(this.consensusTime, that.consensusTime)
                && Objects.equals(this.memo, that.memo)
                && Objects.equals(this.contractCallResult, that.contractCallResult)
                && Objects.equals(this.contractCreateResult, that.contractCreateResult)
                && Objects.equals(this.hbarAdjustments, that.hbarAdjustments)
                && Objects.equals(this.stakingRewardsPaid, that.stakingRewardsPaid)
                && Objects.equals(this.tokens, that.tokens)
                && Objects.equals(this.tokenAdjustments, that.tokenAdjustments)
                && Objects.equals(this.nftTokenAdjustments, that.nftTokenAdjustments)
                && Objects.equals(this.assessedCustomFees, that.assessedCustomFees)
                && Objects.equals(this.newTokenAssociations, that.newTokenAssociations)
                && Objects.equals(this.alias, that.alias)
                && Arrays.equals(this.ethereumHash, that.ethereumHash)
                && this.pseudoRandomNumber == that.pseudoRandomNumber
                && Arrays.equals(this.pseudoRandomBytes, that.pseudoRandomBytes);
    }

    @Override
    public int hashCode() {
        var result =
                Objects.hash(
                        receipt,
                        txnId,
                        consensusTime,
                        memo,
                        fee,
                        contractCallResult,
                        contractCreateResult,
                        hbarAdjustments,
                        stakingRewardsPaid,
                        expiry,
                        submittingMember,
                        tokens,
                        tokenAdjustments,
                        nftTokenAdjustments,
                        scheduleRef,
                        assessedCustomFees,
                        newTokenAssociations,
                        numChildRecords,
                        packedParentConsensusTime,
                        alias,
                        ethereumHash,
                        pseudoRandomNumber);
        result = result * 31 + Arrays.hashCode(txnHash);
        return result * 31 + Arrays.hashCode(pseudoRandomBytes);
    }

    /* --- SelfSerializable --- */
    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public int getMinimumSupportedVersion() {
        return RELEASE_0250_VERSION;
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        writeNullableSerializable(receipt, out);

        out.writeByteArray(txnHash);

        writeNullableSerializable(txnId, out);

        writeNullable(consensusTime, out, RichInstant::serialize);
        writeNullableString(memo, out);

        out.writeLong(this.fee);

        writeNullableSerializable(hbarAdjustments, out);
        writeNullableSerializable(contractCallResult, out);
        writeNullableSerializable(contractCreateResult, out);

        out.writeLong(expiry);
        out.writeLong(submittingMember);

        out.writeSerializableList(tokens, true, true);
        out.writeSerializableList(tokenAdjustments, true, true);

        writeNullableSerializable(scheduleRef, out);
        out.writeSerializableList(nftTokenAdjustments, true, true);
        out.writeSerializableList(assessedCustomFees, true, true);
        out.writeSerializableList(newTokenAssociations, true, true);

        if (numChildRecords != NO_CHILD_TRANSACTIONS) {
            out.writeBoolean(true);
            out.writeShort(numChildRecords);
        } else {
            out.writeBoolean(false);
        }

        if (packedParentConsensusTime != MISSING_PARENT_CONSENSUS_TIMESTAMP) {
            out.writeBoolean(true);
            out.writeLong(packedParentConsensusTime);
        } else {
            out.writeBoolean(false);
        }
        out.writeByteArray(alias.toByteArray());
        out.writeByteArray(ethereumHash);
        writeNullableSerializable(stakingRewardsPaid, out);

        if (pseudoRandomNumber >= 0) {
            out.writeByte(PRNG_INT_OUTPUT);
            out.writeInt(pseudoRandomNumber);
        } else if (pseudoRandomBytes.length != 0) {
            out.writeByte(PRNG_BYTES_OUTPUT);
            out.writeByteArray(pseudoRandomBytes);
        } else {
            out.writeByte(NO_PRNG_OUTPUT);
        }
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        receipt = readNullableSerializable(in);
        txnHash = in.readByteArray(MAX_TXN_HASH_BYTES);
        txnId = readNullableSerializable(in);
        consensusTime = readNullable(in, RichInstant::from);
        memo = IoUtils.readNullableString(in, MAX_MEMO_BYTES);
        fee = in.readLong();
        hbarAdjustments = readNullableSerializable(in);
        contractCallResult = readNullableSerializable(in);
        contractCreateResult = readNullableSerializable(in);
        expiry = in.readLong();
        submittingMember = in.readLong();
        // Added in 0.7
        tokens = in.readSerializableList(MAX_INVOLVED_TOKENS);
        tokenAdjustments = in.readSerializableList(MAX_INVOLVED_TOKENS);
        // Added in 0.8
        scheduleRef = readNullableSerializable(in);
        // Added in 0.16
        nftTokenAdjustments = in.readSerializableList(MAX_INVOLVED_TOKENS);
        assessedCustomFees = in.readSerializableList(MAX_ASSESSED_CUSTOM_FEES_CHANGES);
        // Added in 0.18
        newTokenAssociations = in.readSerializableList(Integer.MAX_VALUE);
        if (newTokenAssociations.isEmpty()) {
            newTokenAssociations = NO_NEW_TOKEN_ASSOCIATIONS;
        }
        // Added in 0.21
        final var hasChildRecords = in.readBoolean();
        if (hasChildRecords) {
            numChildRecords = in.readShort();
        }
        // Added in 0.21
        final var hasParentConsensusTime = in.readBoolean();
        if (hasParentConsensusTime) {
            packedParentConsensusTime = in.readLong();
        }
        // Added in 0.21
        alias = ByteString.copyFrom(in.readByteArray(Integer.MAX_VALUE));
        // Added in 0.23. It is needed only for versions < 0.25.0 and >= 0.23.0
        deserializeAllowanceMaps(in, version);
        // Added in 0.26
        if (version >= RELEASE_0260_VERSION) {
            ethereumHash = in.readByteArray(Integer.MAX_VALUE);
        }
        // Added in 0.27
        if (version >= RELEASE_0270_VERSION) {
            stakingRewardsPaid = readNullableSerializable(in);
        }

        if (version >= RELEASE_0280_VERSION) {
            final var outputType = in.readByte();
            if (outputType == PRNG_INT_OUTPUT) {
                pseudoRandomNumber = in.readInt();
            } else if (outputType == PRNG_BYTES_OUTPUT) {
                pseudoRandomBytes = in.readByteArray(MAX_PSEUDORANDOM_BYTES_LENGTH);
            }
        }
    }

    private void deserializeAllowanceMaps(SerializableDataInputStream in, final int version)
            throws IOException {
        if (version < RELEASE_0250_VERSION) {
            // In release 0.24.x and 0.23.0 three _always-empty_ map sizes were serialized here
            in.readInt();
            in.readInt();
            in.readInt();
        }
    }

    @Override
    public Hash getHash() {
        return this.hash;
    }

    @Override
    public void setHash(Hash hash) {
        this.hash = hash;
    }

    /* --- Object --- */
    public EntityId getScheduleRef() {
        return scheduleRef;
    }

    public List<EntityId> getTokens() {
        return tokens;
    }

    public List<CurrencyAdjustments> getTokenAdjustments() {
        return tokenAdjustments;
    }

    public List<NftAdjustments> getNftTokenAdjustments() {
        return nftTokenAdjustments;
    }

    public TxnReceipt getReceipt() {
        return receipt;
    }

    public ResponseCodeEnum getEnumStatus() {
        return receipt.getEnumStatus();
    }

    public byte[] getTxnHash() {
        return txnHash;
    }

    public TxnId getTxnId() {
        return txnId;
    }

    public RichInstant getConsensusTime() {
        return consensusTime;
    }

    public long getConsensusSecond() {
        return consensusTime.getSeconds();
    }

    public String getMemo() {
        return memo;
    }

    public long getFee() {
        return fee;
    }

    public EvmFnResult getContractCallResult() {
        return contractCallResult;
    }

    public EvmFnResult getContractCreateResult() {
        return contractCreateResult;
    }

    public long getExpiry() {
        return expiry;
    }

    public void setExpiry(long expiry) {
        this.expiry = expiry;
    }

    public long getSubmittingMember() {
        return submittingMember;
    }

    public void setSubmittingMember(long submittingMember) {
        this.submittingMember = submittingMember;
    }

    public List<FcAssessedCustomFee> getCustomFeesCharged() {
        return assessedCustomFees;
    }

    public List<FcTokenAssociation> getNewTokenAssociations() {
        return newTokenAssociations;
    }

    public short getNumChildRecords() {
        return numChildRecords;
    }

    public void setNumChildRecords(final short numChildRecords) {
        this.numChildRecords = numChildRecords;
    }

    public long getPackedParentConsensusTime() {
        return packedParentConsensusTime;
    }

    public void setPackedParentConsensusTime(final long packedParentConsensusTime) {
        this.packedParentConsensusTime = packedParentConsensusTime;
    }

    public ByteString getAlias() {
        return alias;
    }

    public byte[] getEthereumHash() {
        return ethereumHash;
    }

    public void setEthereumHash(byte[] ethereumHash) {
        this.ethereumHash = ethereumHash;
    }

    public void setPseudoRandomBytes(final byte[] pseudoRandomBytes) {
        this.pseudoRandomBytes = pseudoRandomBytes;
    }

    public void setPseudoRandomNumber(final int pseudoRandomNumber) {
        this.pseudoRandomNumber = pseudoRandomNumber;
    }

    public byte[] getPseudoRandomBytes() {
        return pseudoRandomBytes;
    }

    public int getPseudoRandomNumber() {
        return pseudoRandomNumber;
    }

    /* --- FastCopyable --- */
    @Override
    public boolean isImmutable() {
        return true;
    }

    @Override
    public ExpirableTxnRecord copy() {
        return this;
    }

    public static List<TransactionRecord> allToGrpc(List<ExpirableTxnRecord> records) {
        return records.stream().map(ExpirableTxnRecord::asGrpc).toList();
    }

    public TransactionRecord asGrpc() {
        var grpc = TransactionRecord.newBuilder();

        grpc.setTransactionFee(fee);
        if (receipt != null) {
            grpc.setReceipt(TxnReceipt.convert(receipt));
        }
        if (txnId != null) {
            grpc.setTransactionID(txnId.toGrpc());
        }
        if (consensusTime != null) {
            grpc.setConsensusTimestamp(consensusTime.toGrpc());
        }
        if (memo != null) {
            grpc.setMemo(memo);
        }
        if (txnHash != null && txnHash.length > 0) {
            grpc.setTransactionHash(ByteStringUtils.wrapUnsafely(txnHash));
        }
        if (hbarAdjustments != null) {
            grpc.setTransferList(hbarAdjustments.toGrpc());
        }
        if (stakingRewardsPaid != null) {
            grpc.addAllPaidStakingRewards(stakingRewardsPaid.asAccountAmountsList());
        }
        if (contractCallResult != null) {
            grpc.setContractCallResult(contractCallResult.toGrpc());
        }
        if (contractCreateResult != null) {
            grpc.setContractCreateResult(contractCreateResult.toGrpc());
        }
        if (tokens != NO_TOKENS) {
            setGrpcTokens(grpc, tokens, tokenAdjustments, nftTokenAdjustments);
        }
        if (scheduleRef != NO_SCHEDULE_REF) {
            grpc.setScheduleRef(scheduleRef.toGrpcScheduleId());
        }
        if (assessedCustomFees != NO_CUSTOM_FEES) {
            for (final var customFee : assessedCustomFees) {
                grpc.addAssessedCustomFees(customFee.toGrpc());
            }
        }
        if (newTokenAssociations != NO_NEW_TOKEN_ASSOCIATIONS) {
            for (final var association : newTokenAssociations) {
                grpc.addAutomaticTokenAssociations(association.toGrpc());
            }
        }
        if (alias != MISSING_ALIAS) {
            grpc.setAlias(alias);
        }
        if (ethereumHash != MISSING_ETHEREUM_HASH) {
            grpc.setEthereumHash(ByteString.copyFrom(ethereumHash));
        }
        if (packedParentConsensusTime != MISSING_PARENT_CONSENSUS_TIMESTAMP) {
            grpc.setParentConsensusTimestamp(asTimestamp(packedParentConsensusTime));
        }
        if (pseudoRandomNumber >= 0) {
            grpc.setPrngNumber(pseudoRandomNumber);
        } else if (pseudoRandomBytes.length != 0) {
            grpc.setPrngBytes(wrapUnsafely(pseudoRandomBytes));
        }

        return grpc.build();
    }

    private static void setGrpcTokens(
            TransactionRecord.Builder grpcBuilder,
            final List<EntityId> tokens,
            final List<CurrencyAdjustments> tokenAdjustments,
            final List<NftAdjustments> nftTokenAdjustments) {
        for (int i = 0, n = tokens.size(); i < n; i++) {
            var tokenTransferList =
                    TokenTransferList.newBuilder().setToken(tokens.get(i).toGrpcTokenId());
            if (tokenAdjustments != null && !tokenAdjustments.isEmpty()) {
                final var choice = tokenAdjustments.get(i);
                if (choice != null) {
                    choice.addToGrpc(tokenTransferList);
                }
            }
            if (nftTokenAdjustments != null && !nftTokenAdjustments.isEmpty()) {
                final var choice = nftTokenAdjustments.get(i);
                if (choice != null) {
                    choice.addToGrpc(tokenTransferList);
                }
            }
            grpcBuilder.addTokenTransferLists(tokenTransferList);
        }
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private TxnReceipt receipt;
        private TxnReceipt.Builder receiptBuilder;

        private byte[] txnHash;
        private TxnId txnId;
        private RichInstant consensusTime;
        private String memo;
        private long fee;
        private long packedParentConsensusTime = MISSING_PARENT_CONSENSUS_TIMESTAMP;
        private short numChildRecords = NO_CHILD_TRANSACTIONS;
        private CurrencyAdjustments hbarAdjustments;
        private CurrencyAdjustments stakingRewardsPaid;
        private EvmFnResult contractCallResult;
        private EvmFnResult contractCreateResult;
        private List<EntityId> tokens;
        private List<CurrencyAdjustments> tokenAdjustments;
        private List<NftAdjustments> nftTokenAdjustments;
        private EntityId scheduleRef;
        private List<FcAssessedCustomFee> assessedCustomFees;
        private List<FcTokenAssociation> newTokenAssociations = NO_NEW_TOKEN_ASSOCIATIONS;
        private ByteString alias = MISSING_ALIAS;
        private byte[] ethereumHash = MISSING_ETHEREUM_HASH;
        private int pseudoRandomNumber = MISSING_NUMBER;
        private byte[] pseudoRandomBytes = MISSING_PSEUDORANDOM_BYTES;
        private boolean onlyExternalizedIfSuccessful = false;

        public Builder setFee(long fee) {
            this.fee = fee;
            return this;
        }

        public Builder setTxnId(TxnId txnId) {
            this.txnId = txnId;
            return this;
        }

        public Builder setTxnHash(byte[] txnHash) {
            this.txnHash = txnHash;
            return this;
        }

        public Builder setMemo(String memo) {
            this.memo = memo;
            return this;
        }

        public Builder setReceipt(TxnReceipt receipt) {
            this.receipt = receipt;
            return this;
        }

        public Builder setReceiptBuilder(TxnReceipt.Builder receiptBuilder) {
            this.receiptBuilder = receiptBuilder;
            return this;
        }

        public Builder setConsensusTime(RichInstant consensusTime) {
            this.consensusTime = consensusTime;
            return this;
        }

        public Builder setHbarAdjustments(CurrencyAdjustments hbarAdjustments) {
            this.hbarAdjustments = hbarAdjustments;
            return this;
        }

        public Builder setStakingRewardsPaid(CurrencyAdjustments stakingRewardsPaid) {
            this.stakingRewardsPaid = stakingRewardsPaid;
            return this;
        }

        public Builder setContractCallResult(EvmFnResult contractCallResult) {
            this.contractCallResult = contractCallResult;
            return this;
        }

        public Builder setContractCreateResult(EvmFnResult contractCreateResult) {
            this.contractCreateResult = contractCreateResult;
            return this;
        }

        public Builder setTokens(List<EntityId> tokens) {
            this.tokens = tokens;
            return this;
        }

        public Builder setTokenAdjustments(List<CurrencyAdjustments> tokenAdjustments) {
            this.tokenAdjustments = tokenAdjustments;
            return this;
        }

        public Builder setNftTokenAdjustments(List<NftAdjustments> nftTokenAdjustments) {
            this.nftTokenAdjustments = nftTokenAdjustments;
            return this;
        }

        public Builder setScheduleRef(EntityId scheduleRef) {
            this.scheduleRef = scheduleRef;
            return this;
        }

        public Builder setAssessedCustomFees(List<FcAssessedCustomFee> assessedCustomFees) {
            this.assessedCustomFees = assessedCustomFees;
            return this;
        }

        public Builder setNewTokenAssociations(List<FcTokenAssociation> newTokenAssociations) {
            this.newTokenAssociations = newTokenAssociations;
            return this;
        }

        public Builder setParentConsensusTime(final Instant consTime) {
            this.packedParentConsensusTime =
                    packedTime(consTime.getEpochSecond(), consTime.getNano());
            return this;
        }

        public Builder setNumChildRecords(final short numChildRecords) {
            this.numChildRecords = numChildRecords;
            return this;
        }

        public Builder setAlias(ByteString alias) {
            this.alias = alias;
            return this;
        }

        public Builder setEthereumHash(byte[] ethereumHash) {
            this.ethereumHash = ethereumHash;
            return this;
        }

        public Builder setPseudoRandomBytes(final byte[] pseudoRandomBytes) {
            this.pseudoRandomBytes = pseudoRandomBytes;
            return this;
        }

        public Builder setPseudoRandomNumber(final int pseudoRandomNumber) {
            this.pseudoRandomNumber = pseudoRandomNumber;
            return this;
        }

        public ExpirableTxnRecord build() {
            return new ExpirableTxnRecord(this);
        }

        public Builder reset() {
            fee = 0;
            txnId = null;
            txnHash = MISSING_TXN_HASH;
            memo = null;
            receipt = null;
            consensusTime = null;

            nullOutSideEffectFields(true);

            return this;
        }

        public void revert() {
            if (receiptBuilder == null) {
                throw new IllegalStateException("Cannot revert a record with a built receipt");
            }
            receiptBuilder.revert();
            nullOutSideEffectFields(false);
        }

        public void excludeHbarChangesFrom(final ExpirableTxnRecord.Builder that) {
            if (that.hbarAdjustments == null) {
                return;
            }

            final var adjustsHere = this.hbarAdjustments.hbars.length;
            final var adjustsThere = that.hbarAdjustments.hbars.length;
            final var maxAdjusts = adjustsHere + adjustsThere;
            final var changedHere = this.hbarAdjustments.accountNums;
            final var changedThere = that.hbarAdjustments.accountNums;
            final var maxAccountCodes = changedHere.length + changedThere.length;

            final var netAdjustsHere = new long[maxAdjusts];
            final long[] netChanged = new long[maxAccountCodes];

            var i = 0;
            var j = 0;
            var k = 0;
            while (i < adjustsHere && j < adjustsThere) {
                final var iId = changedHere[i];
                final var jId = changedThere[j];
                final var cmp = Long.compare(iId, jId);
                if (cmp == 0) {
                    final var net =
                            this.hbarAdjustments.hbars[i++] - that.hbarAdjustments.hbars[j++];
                    if (net != 0) {
                        netAdjustsHere[k] = net;
                        netChanged[k++] = iId;
                    }
                } else if (cmp < 0) {
                    netAdjustsHere[k] = this.hbarAdjustments.hbars[i++];
                    netChanged[k++] = iId;
                } else {
                    netAdjustsHere[k] = -that.hbarAdjustments.hbars[j++];
                    netChanged[k++] = jId;
                }
            }
            /* Note that at most one of these loops can iterate a non-zero number of times,
             * since if both did we could not have exited the prior loop. */
            while (i < adjustsHere) {
                final var iId = changedHere[i];
                netAdjustsHere[k] = this.hbarAdjustments.hbars[i++];
                netChanged[k++] = iId;
            }
            while (j < adjustsThere) {
                final var jId = changedThere[j];
                netAdjustsHere[k] = -that.hbarAdjustments.hbars[j++];
                netChanged[k++] = jId;
            }

            this.hbarAdjustments.hbars = Arrays.copyOfRange(netAdjustsHere, 0, k);
            this.hbarAdjustments.accountNums = Arrays.copyOfRange(netChanged, 0, k);
        }

        private void nullOutSideEffectFields(boolean removeCallResult) {
            hbarAdjustments = null;
            stakingRewardsPaid = null;
            contractCreateResult = null;
            tokens = NO_TOKENS;
            tokenAdjustments = NO_TOKEN_ADJUSTMENTS;
            nftTokenAdjustments = NO_NFT_TOKEN_ADJUSTMENTS;
            scheduleRef = NO_SCHEDULE_REF;
            assessedCustomFees = NO_CUSTOM_FEES;
            newTokenAssociations = NO_NEW_TOKEN_ASSOCIATIONS;
            alias = MISSING_ALIAS;
            ethereumHash = MISSING_ETHEREUM_HASH;
            pseudoRandomNumber = MISSING_NUMBER;
            pseudoRandomBytes = MISSING_PSEUDORANDOM_BYTES;
            /* if this is a revert of a child record we want to have contractCallResult */
            if (removeCallResult) {
                contractCallResult = null;
            }
        }

        public CurrencyAdjustments getHbarAdjustments() {
            return hbarAdjustments;
        }

        public CurrencyAdjustments getStakingRewardsPaid() {
            return stakingRewardsPaid;
        }

        public EvmFnResult getContractCallResult() {
            return contractCallResult;
        }

        public EvmFnResult getContractCreateResult() {
            return contractCreateResult;
        }

        public List<EntityId> getTokens() {
            return tokens;
        }

        public List<CurrencyAdjustments> getTokenAdjustments() {
            return tokenAdjustments;
        }

        public List<NftAdjustments> getNftTokenAdjustments() {
            return nftTokenAdjustments;
        }

        public EntityId getScheduleRef() {
            return scheduleRef;
        }

        public List<FcAssessedCustomFee> getAssessedCustomFees() {
            return assessedCustomFees;
        }

        public List<FcTokenAssociation> getNewTokenAssociations() {
            return newTokenAssociations;
        }

        public TxnReceipt.Builder getReceiptBuilder() {
            return receiptBuilder;
        }

        public TxnId getTxnId() {
            return txnId;
        }

        public ByteString getAlias() {
            return alias;
        }

        public boolean shouldNotBeExternalized() {
            return onlyExternalizedIfSuccessful
                    && !TxnReceipt.SUCCESS_LITERAL.equals(receiptBuilder.getStatus());
        }

        public long getFee() {
            return fee;
        }

        public void onlyExternalizeIfSuccessful() {
            onlyExternalizedIfSuccessful = true;
        }

        public byte[] getPseudoRandomBytes() {
            return pseudoRandomBytes;
        }

        public int getPseudoRandomNumber() {
            return pseudoRandomNumber;
        }
    }

    @VisibleForTesting
    void clearStakingRewardsPaid() {
        stakingRewardsPaid = null;
    }

    @VisibleForTesting
    public void clearPrngData() {
        pseudoRandomBytes = MISSING_PSEUDORANDOM_BYTES;
        pseudoRandomNumber = MISSING_NUMBER;
    }
}

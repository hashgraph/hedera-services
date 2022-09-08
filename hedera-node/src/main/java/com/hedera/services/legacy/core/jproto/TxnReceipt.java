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
package com.hedera.services.legacy.core.jproto;

import static com.hedera.services.state.serdes.IoUtils.readNullableSerializable;
import static com.hedera.services.state.serdes.IoUtils.writeNullableSerializable;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REVERTED_SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.swirlds.common.utility.CommonUtils.getNormalisedStringFromBytes;

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.TxnId;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.builder.RequestBuilder;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.utility.CommonUtils;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public class TxnReceipt implements SelfSerializable {
    private static final int MAX_STATUS_BYTES = 128;
    private static final int MAX_RUNNING_HASH_BYTES = 1024;
    private static final int MAX_SERIAL_NUMBERS = 16384;

    public static final String SUCCESS_LITERAL = SUCCESS.name();
    public static final String REVERTED_SUCCESS_LITERAL = REVERTED_SUCCESS.name();
    public static final long MISSING_RUNNING_HASH_VERSION = 0L;

    static final TxnId MISSING_SCHEDULED_TXN_ID = null;
    static final byte[] MISSING_RUNNING_HASH = null;
    static final long MISSING_TOPIC_SEQ_NO = 0L;
    static final long MISSING_NEW_TOTAL_SUPPLY = -1L;

    static final int RELEASE_0160_VERSION = 7;
    static final int CURRENT_VERSION = RELEASE_0160_VERSION;
    static final long RUNTIME_CONSTRUCTABLE_ID = 0x65ef569a77dcf125L;

    long runningHashVersion = MISSING_RUNNING_HASH_VERSION;
    long topicSequenceNumber = MISSING_TOPIC_SEQ_NO;
    byte[] topicRunningHash = MISSING_RUNNING_HASH;
    TxnId scheduledTxnId = MISSING_SCHEDULED_TXN_ID;
    String status;
    EntityId accountId;
    EntityId fileId;
    EntityId topicId;
    EntityId tokenId;
    EntityId contractId;
    EntityId scheduleId;
    ExchangeRates exchangeRates;
    long newTotalSupply = MISSING_NEW_TOTAL_SUPPLY;
    long[] serialNumbers;

    public TxnReceipt() {}

    public TxnReceipt(final Builder builder) {
        this.status = builder.status;
        this.accountId = builder.accountId;
        this.fileId = builder.fileId;
        this.contractId = builder.contractId;
        this.exchangeRates = builder.exchangeRates;
        this.topicId = builder.topicId;
        this.tokenId = builder.tokenId;
        this.scheduleId = builder.scheduleId;
        this.topicSequenceNumber = builder.topicSequenceNumber;
        this.topicRunningHash =
                ((builder.topicRunningHash != MISSING_RUNNING_HASH)
                                && (builder.topicRunningHash.length > 0))
                        ? builder.topicRunningHash
                        : MISSING_RUNNING_HASH;
        this.runningHashVersion = builder.runningHashVersion;
        this.newTotalSupply = builder.newTotalSupply;
        this.scheduledTxnId = builder.scheduledTxnId;

        final var hasSerialNumbers =
                (builder.serialNumbers != null) && (builder.serialNumbers.length > 0);
        this.serialNumbers = hasSerialNumbers ? builder.serialNumbers : null;
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
        return CURRENT_VERSION;
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeNormalisedString(status);
        out.writeSerializable(exchangeRates, true);
        writeNullableSerializable(accountId, out);
        writeNullableSerializable(fileId, out);
        writeNullableSerializable(contractId, out);
        writeNullableSerializable(topicId, out);
        writeNullableSerializable(tokenId, out);
        writeNullableSerializable(scheduleId, out);
        if (topicRunningHash == MISSING_RUNNING_HASH) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            out.writeLong(topicSequenceNumber);
            out.writeLong(runningHashVersion);
            out.writeByteArray(topicRunningHash);
        }
        out.writeLong(newTotalSupply);
        writeNullableSerializable(scheduledTxnId, out);
        out.writeLongArray(serialNumbers);
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version)
            throws IOException {
        status = getNormalisedStringFromBytes(in.readByteArray(MAX_STATUS_BYTES));
        exchangeRates = in.readSerializable(true, ExchangeRates::new);
        accountId = readNullableSerializable(in);
        fileId = readNullableSerializable(in);
        contractId = readNullableSerializable(in);
        topicId = readNullableSerializable(in);
        // Added in 0.7
        tokenId = readNullableSerializable(in);
        // Added in 0.11
        scheduleId = readNullableSerializable(in);
        final var isSubmitMessageReceipt = in.readBoolean();
        if (isSubmitMessageReceipt) {
            topicSequenceNumber = in.readLong();
            runningHashVersion = in.readLong();
            topicRunningHash = in.readByteArray(MAX_RUNNING_HASH_BYTES);
        }
        // Added in 0.9
        newTotalSupply = in.readLong();
        // Added in 0.12
        scheduledTxnId = readNullableSerializable(in);
        // Added in 0.16
        serialNumbers = in.readLongArray(MAX_SERIAL_NUMBERS);
    }

    public long getRunningHashVersion() {
        return runningHashVersion;
    }

    public String getStatus() {
        return status;
    }

    public ResponseCodeEnum getEnumStatus() {
        return ResponseCodeEnum.valueOf(status);
    }

    public EntityId getAccountId() {
        return accountId;
    }

    public EntityId getFileId() {
        return fileId;
    }

    public EntityId getContractId() {
        return contractId;
    }

    public ExchangeRates getExchangeRates() {
        return exchangeRates;
    }

    public EntityId getTopicId() {
        return topicId;
    }

    public EntityId getTokenId() {
        return tokenId;
    }

    public EntityId getScheduleId() {
        return scheduleId;
    }

    public long getTopicSequenceNumber() {
        return topicSequenceNumber;
    }

    public byte[] getTopicRunningHash() {
        return topicRunningHash;
    }

    public long getNewTotalSupply() {
        return newTotalSupply;
    }

    public TxnId getScheduledTxnId() {
        return scheduledTxnId;
    }

    public long[] getSerialNumbers() {
        return serialNumbers;
    }

    /* --- Object --- */

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TxnReceipt that = (TxnReceipt) o;
        return this.runningHashVersion == that.runningHashVersion
                && Objects.equals(status, that.status)
                && Objects.equals(accountId, that.accountId)
                && Objects.equals(fileId, that.fileId)
                && Objects.equals(contractId, that.contractId)
                && Objects.equals(topicId, that.topicId)
                && Objects.equals(tokenId, that.tokenId)
                && Objects.equals(topicSequenceNumber, that.topicSequenceNumber)
                && Arrays.equals(topicRunningHash, that.topicRunningHash)
                && Objects.equals(newTotalSupply, that.newTotalSupply)
                && Objects.equals(scheduledTxnId, that.scheduledTxnId)
                && Arrays.equals(serialNumbers, that.serialNumbers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                runningHashVersion,
                status,
                accountId,
                fileId,
                contractId,
                topicId,
                tokenId,
                topicSequenceNumber,
                Arrays.hashCode(topicRunningHash),
                newTotalSupply,
                scheduledTxnId,
                Arrays.hashCode(serialNumbers));
    }

    @Override
    public String toString() {
        var helper =
                MoreObjects.toStringHelper(this)
                        .omitNullValues()
                        .add("status", status)
                        .add("exchangeRates", exchangeRates)
                        .add("accountCreated", accountId)
                        .add("fileCreated", fileId)
                        .add("tokenCreated", tokenId)
                        .add("contractCreated", contractId)
                        .add("topicCreated", topicId)
                        .add("newTotalTokenSupply", newTotalSupply)
                        .add("scheduledTxnId", scheduledTxnId)
                        .add("serialNumbers", serialNumbers);
        if (topicRunningHash != MISSING_RUNNING_HASH) {
            helper.add("topicSeqNo", topicSequenceNumber);
            helper.add("topicRunningHash", CommonUtils.hex(topicRunningHash));
            helper.add("runningHashVersion", runningHashVersion);
        }
        return helper.toString();
    }

    public void setAccountId(EntityId accountId) {
        this.accountId = accountId;
    }

    public TransactionReceipt toGrpc() {
        return convert(this);
    }

    public static TransactionReceipt convert(TxnReceipt txReceipt) {
        final var builder = TransactionReceipt.newBuilder();
        if (txReceipt.getStatus() != null) {
            builder.setStatus(ResponseCodeEnum.valueOf(txReceipt.getStatus()));
        }
        if (txReceipt.getAccountId() != null) {
            builder.setAccountID(
                    RequestBuilder.getAccountIdBuild(
                            txReceipt.getAccountId().num(),
                            txReceipt.getAccountId().realm(),
                            txReceipt.getAccountId().shard()));
        }
        if (txReceipt.getFileId() != null) {
            builder.setFileID(
                    RequestBuilder.getFileIdBuild(
                            txReceipt.getFileId().num(),
                            txReceipt.getFileId().realm(),
                            txReceipt.getFileId().shard()));
        }
        if (txReceipt.getContractId() != null) {
            builder.setContractID(
                    RequestBuilder.getContractIdBuild(
                            txReceipt.getContractId().num(),
                            txReceipt.getContractId().realm(),
                            txReceipt.getContractId().shard()));
        }
        if (txReceipt.getTokenId() != null) {
            builder.setTokenID(txReceipt.getTokenId().toGrpcTokenId());
        }
        if (txReceipt.getScheduleId() != null) {
            builder.setScheduleID(txReceipt.getScheduleId().toGrpcScheduleId());
        }
        if (txReceipt.getExchangeRates() != null) {
            builder.setExchangeRate(txReceipt.exchangeRates.toGrpc());
        }
        if (txReceipt.getTopicId() != null) {
            var receiptTopic = txReceipt.getTopicId();
            builder.setTopicID(
                    TopicID.newBuilder()
                            .setShardNum(receiptTopic.shard())
                            .setRealmNum(receiptTopic.realm())
                            .setTopicNum(receiptTopic.num())
                            .build());
        }
        if (txReceipt.getTopicSequenceNumber() != MISSING_TOPIC_SEQ_NO) {
            builder.setTopicSequenceNumber(txReceipt.getTopicSequenceNumber());
        }
        if (txReceipt.getTopicRunningHash() != MISSING_RUNNING_HASH) {
            builder.setTopicRunningHash(ByteString.copyFrom(txReceipt.getTopicRunningHash()));
        }
        if (txReceipt.getRunningHashVersion() != MISSING_RUNNING_HASH_VERSION) {
            builder.setTopicRunningHashVersion(txReceipt.getRunningHashVersion());
        }
        if (txReceipt.getNewTotalSupply() >= 0) {
            builder.setNewTotalSupply(txReceipt.newTotalSupply);
        }
        if (txReceipt.getScheduledTxnId() != MISSING_SCHEDULED_TXN_ID) {
            builder.setScheduledTransactionID(txReceipt.getScheduledTxnId().toGrpc());
        }

        if (txReceipt.getSerialNumbers() != null) {
            for (final var serialNo : txReceipt.getSerialNumbers()) {
                builder.addSerialNumbers(serialNo);
            }
        }
        return builder.build();
    }

    public static TxnReceipt.Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private String status;
        private EntityId accountId;
        private EntityId fileId;
        private EntityId contractId;
        private EntityId tokenId;
        private EntityId scheduleId;
        private ExchangeRates exchangeRates;
        private EntityId topicId;
        private long topicSequenceNumber;
        private byte[] topicRunningHash;
        private long runningHashVersion;
        private long newTotalSupply;
        private TxnId scheduledTxnId;
        private long[] serialNumbers;

        public void revert() {
            if (SUCCESS_LITERAL.equals(status)) {
                status = REVERTED_SUCCESS_LITERAL;
            }

            accountId = null;
            contractId = null;
            fileId = null;
            tokenId = null;
            topicId = null;
            scheduleId = null;
            scheduledTxnId = null;

            serialNumbers = null;
            topicRunningHash = null;

            newTotalSupply = MISSING_NEW_TOTAL_SUPPLY;
            runningHashVersion = MISSING_RUNNING_HASH_VERSION;
            topicSequenceNumber = MISSING_TOPIC_SEQ_NO;
        }

        public Builder setStatus(final String status) {
            this.status = status;
            return this;
        }

        public Builder setAccountId(final EntityId accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder setFileId(final EntityId fileId) {
            this.fileId = fileId;
            return this;
        }

        public Builder setContractId(final EntityId contractId) {
            this.contractId = contractId;
            return this;
        }

        public Builder setTokenId(final EntityId tokenId) {
            this.tokenId = tokenId;
            return this;
        }

        public Builder setScheduleId(final EntityId scheduleId) {
            this.scheduleId = scheduleId;
            return this;
        }

        public Builder setExchangeRates(final ExchangeRates exchangeRates) {
            this.exchangeRates = exchangeRates;
            return this;
        }

        public Builder setTopicId(final EntityId topicId) {
            this.topicId = topicId;
            return this;
        }

        public Builder setTopicSequenceNumber(final long topicSequenceNumber) {
            this.topicSequenceNumber = topicSequenceNumber;
            return this;
        }

        public Builder setTopicRunningHash(final byte[] topicRunningHash) {
            this.topicRunningHash = topicRunningHash;
            return this;
        }

        public Builder setRunningHashVersion(final long runningHashVersion) {
            this.runningHashVersion = runningHashVersion;
            return this;
        }

        public Builder setNewTotalSupply(final long newTotalSupply) {
            this.newTotalSupply = newTotalSupply;
            return this;
        }

        public Builder setScheduledTxnId(final TxnId scheduledTxnId) {
            this.scheduledTxnId = scheduledTxnId;
            return this;
        }

        public Builder setSerialNumbers(final long[] serialNumbers) {
            this.serialNumbers = serialNumbers;
            return this;
        }

        public TxnReceipt build() {
            return new TxnReceipt(this);
        }

        public String getStatus() {
            return status;
        }

        public EntityId getAccountId() {
            return accountId;
        }

        public EntityId getFileId() {
            return fileId;
        }

        public EntityId getContractId() {
            return contractId;
        }

        public EntityId getTokenId() {
            return tokenId;
        }

        public EntityId getScheduleId() {
            return scheduleId;
        }

        public EntityId getTopicId() {
            return topicId;
        }

        public long getTopicSequenceNumber() {
            return topicSequenceNumber;
        }

        public byte[] getTopicRunningHash() {
            return topicRunningHash;
        }

        public long getRunningHashVersion() {
            return runningHashVersion;
        }

        public long getNewTotalSupply() {
            return newTotalSupply;
        }

        public TxnId getScheduledTxnId() {
            return scheduledTxnId;
        }

        public long[] getSerialNumbers() {
            return serialNumbers;
        }
    }
}

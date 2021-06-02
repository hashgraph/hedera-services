package com.hedera.services.legacy.core.jproto;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.TxnId;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.builder.RequestBuilder;
import com.swirlds.common.CommonUtils;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import static com.swirlds.common.CommonUtils.getNormalisedStringFromBytes;

public class TxnReceipt implements SelfSerializable {
	private static final Logger log = LogManager.getLogger(TxnReceipt.class);

	private static final int MAX_STATUS_BYTES = 128;
	private static final int MAX_RUNNING_HASH_BYTES = 1024;

	static final TxnId MISSING_SCHEDULED_TXN_ID = null;
	static final byte[] MISSING_RUNNING_HASH = null;
	static final long MISSING_TOPIC_SEQ_NO = 0L;
	static final long MISSING_RUNNING_HASH_VERSION = 0L;

	static final int RELEASE_070_VERSION = 1;
	static final int RELEASE_080_VERSION = 2;
	static final int RELEASE_090_VERSION = 3;
	static final int RELEASE_0100_VERSION = 4;
	static final int RELEASE_0110_VERSION = 5;
	static final int RELEASE_0120_VERSION = 6;
	static final int MERKLE_VERSION = RELEASE_0120_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x65ef569a77dcf125L;

	static DomainSerdes serdes = new DomainSerdes();

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
	long newTotalSupply = -1L;

	public TxnReceipt() { }

	public TxnReceipt (Builder builder){
		this.status = builder.status;
		this.accountId = builder.accountId;
		this.fileId = builder.fileId;
		this.contractId = builder.contractId;
		this.exchangeRates = builder.exchangeRates;
		this.topicId = builder.topicId;
		this.tokenId = builder.tokenId;
		this.scheduleId = builder.scheduleId;
		this.topicSequenceNumber = builder.topicSequenceNumber;
		this.topicRunningHash = ((builder.topicRunningHash != MISSING_RUNNING_HASH) && (builder.topicRunningHash.length > 0))
				? builder.topicRunningHash
				: MISSING_RUNNING_HASH;
		this.runningHashVersion = builder.runningHashVersion;
		this.newTotalSupply = builder.newTotalSupply;
		this.scheduledTxnId = builder.scheduledTxnId;
	}

	/* --- SelfSerializable --- */

	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return MERKLE_VERSION;
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeNormalisedString(status);
		out.writeSerializable(exchangeRates, true);
		serdes.writeNullableSerializable(accountId, out);
		serdes.writeNullableSerializable(fileId, out);
		serdes.writeNullableSerializable(contractId, out);
		serdes.writeNullableSerializable(topicId, out);
		serdes.writeNullableSerializable(tokenId, out);
		serdes.writeNullableSerializable(scheduleId, out);
		if (topicRunningHash == MISSING_RUNNING_HASH) {
			out.writeBoolean(false);
		} else {
			out.writeBoolean(true);
			out.writeLong(topicSequenceNumber);
			out.writeLong(runningHashVersion);
			out.writeByteArray(topicRunningHash);
		}
		out.writeLong(newTotalSupply);
		serdes.writeNullableSerializable(scheduledTxnId, out);
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		status = getNormalisedStringFromBytes(in.readByteArray(MAX_STATUS_BYTES));
		exchangeRates = in.readSerializable(true, ExchangeRates::new);
		accountId = serdes.readNullableSerializable(in);
		fileId = serdes.readNullableSerializable(in);
		contractId = serdes.readNullableSerializable(in);
		topicId = serdes.readNullableSerializable(in);
		if (version > RELEASE_070_VERSION) {
			tokenId = serdes.readNullableSerializable(in);
		}
		if (version >= RELEASE_0110_VERSION) {
			scheduleId = serdes.readNullableSerializable(in);
		}
		var isSubmitMessageReceipt = in.readBoolean();
		if (isSubmitMessageReceipt) {
			topicSequenceNumber = in.readLong();
			runningHashVersion = in.readLong();
			topicRunningHash = in.readByteArray(MAX_RUNNING_HASH_BYTES);
		}
		if (version > RELEASE_090_VERSION) {
			newTotalSupply = in.readLong();
		}
		if (version >= RELEASE_0120_VERSION) {
			scheduledTxnId = serdes.readNullableSerializable(in);
		}
	}

	public long getRunningHashVersion() {
		return runningHashVersion;
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
		return this.runningHashVersion == that.runningHashVersion &&
				Objects.equals(status, that.status) &&
				Objects.equals(accountId, that.accountId) &&
				Objects.equals(fileId, that.fileId) &&
				Objects.equals(contractId, that.contractId) &&
				Objects.equals(topicId, that.topicId) &&
				Objects.equals(tokenId, that.tokenId) &&
				Objects.equals(topicSequenceNumber, that.topicSequenceNumber) &&
				Arrays.equals(topicRunningHash, that.topicRunningHash) &&
				Objects.equals(newTotalSupply, that.newTotalSupply) &&
				Objects.equals(scheduledTxnId, that.scheduledTxnId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
				runningHashVersion, status,
				accountId, fileId, contractId, topicId, tokenId,
				topicSequenceNumber, Arrays.hashCode(topicRunningHash),
				newTotalSupply, scheduledTxnId);
	}

	@Override
	public String toString() {
		var helper = MoreObjects.toStringHelper(this)
				.omitNullValues()
				.add("status", status)
				.add("exchangeRates", exchangeRates)
				.add("accountCreated", accountId)
				.add("fileCreated", fileId)
				.add("tokenCreated", tokenId)
				.add("contractCreated", contractId)
				.add("topicCreated", topicId)
				.add("newTotalTokenSupply", newTotalSupply)
				.add("scheduledTxnId", scheduledTxnId);
		if (topicRunningHash != MISSING_RUNNING_HASH) {
			helper.add("topicSeqNo", topicSequenceNumber);
			helper.add("topicRunningHash", CommonUtils.hex(topicRunningHash));
			helper.add("runningHashVersion", runningHashVersion);
		}
		return helper.toString();
	}

	/* ---  Helpers --- */

	public static TxnReceipt fromGrpc(TransactionReceipt grpc) {
		String status = grpc.getStatus() != null ? grpc.getStatus().name() : null;
		EntityId accountId = grpc.hasAccountID() ? EntityId.fromGrpcAccountId(grpc.getAccountID()) : null;
		EntityId jFileID = grpc.hasFileID() ? EntityId.fromGrpcFileId(grpc.getFileID()) : null;
		EntityId jContractID = grpc.hasContractID() ? EntityId.fromGrpcContractId(grpc.getContractID()) : null;
		EntityId topicId = grpc.hasTopicID() ? EntityId.fromGrpcTopicId(grpc.getTopicID()) : null;
		EntityId tokenId = grpc.hasTokenID() ? EntityId.fromGrpcTokenId(grpc.getTokenID()) : null;
		EntityId scheduleId = grpc.hasScheduleID() ? EntityId.fromGrpcScheduleId(grpc.getScheduleID()) : null;
		long runningHashVersion = Math.max(MISSING_RUNNING_HASH_VERSION, grpc.getTopicRunningHashVersion());
		long newTotalSupply = grpc.getNewTotalSupply();
		TxnId scheduledTxnId = grpc.hasScheduledTransactionID()
				? TxnId.fromGrpc(grpc.getScheduledTransactionID())
				: MISSING_SCHEDULED_TXN_ID;
		return TxnReceipt.newBuilder()
				.setStatus(status)
				.setAccountId(accountId)
				.setFileId(jFileID)
				.setContractId(jContractID)
				.setTokenId(tokenId)
				.setScheduleId(scheduleId)
				.setExchangeRates(ExchangeRates.fromGrpc(grpc.getExchangeRate()))
				.setTopicId(topicId)
				.setTopicSequenceNumber(grpc.getTopicSequenceNumber())
				.setTopicRunningHash(grpc.getTopicRunningHash().toByteArray())
				.setRunningHashVersion(runningHashVersion)
				.setNewTotalSupply(newTotalSupply)
				.setScheduledTxnId(scheduledTxnId)
				.build();
	}

	public TransactionReceipt toGrpc() {
		return convert(this);
	}

	public static TransactionReceipt convert(TxnReceipt txReceipt) {
		TransactionReceipt.Builder builder = TransactionReceipt.newBuilder();
		if(txReceipt.getStatus() != null){
			builder.setStatus(ResponseCodeEnum.valueOf(txReceipt.getStatus()));
		}
		if (txReceipt.getAccountId() != null) {
			builder.setAccountID(RequestBuilder.getAccountIdBuild(
					txReceipt.getAccountId().num(),
					txReceipt.getAccountId().realm(),
					txReceipt.getAccountId().shard()));
		}
		if (txReceipt.getFileId() != null) {
			builder.setFileID(RequestBuilder.getFileIdBuild(
					txReceipt.getFileId().num(),
					txReceipt.getFileId().realm(),
					txReceipt.getFileId().shard()));
		}
		if (txReceipt.getContractId() != null) {
			builder.setContractID(RequestBuilder.getContractIdBuild(
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
			builder.setTopicID(TopicID.newBuilder().setShardNum(receiptTopic.shard())
					.setRealmNum(receiptTopic.realm())
					.setTopicNum(receiptTopic.num()).build());
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
		return builder.build();
	}

	public static TxnReceipt.Builder newBuilder(){
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

		public Builder setStatus(String status) {
			this.status = status;
			return this;
		}

		public Builder setAccountId(EntityId accountId) {
			this.accountId = accountId;
			return this;
		}

		public Builder setFileId(EntityId fileId) {
			this.fileId = fileId;
			return this;
		}

		public Builder setContractId(EntityId contractId) {
			this.contractId = contractId;
			return this;
		}

		public Builder setTokenId(EntityId tokenId) {
			this.tokenId = tokenId;
			return this;
		}

		public Builder setScheduleId(EntityId scheduleId) {
			this.scheduleId = scheduleId;
			return this;
		}

		public Builder setExchangeRates(ExchangeRates exchangeRates) {
			this.exchangeRates = exchangeRates;
			return this;
		}

		public Builder setTopicId(EntityId topicId) {
			this.topicId = topicId;
			return this;
		}

		public Builder setTopicSequenceNumber(long topicSequenceNumber) {
			this.topicSequenceNumber = topicSequenceNumber;
			return this;
		}

		public Builder setTopicRunningHash(byte[] topicRunningHash) {
			this.topicRunningHash = topicRunningHash;
			return this;
		}

		public Builder setRunningHashVersion(long runningHashVersion) {
			this.runningHashVersion = runningHashVersion;
			return this;
		}

		public Builder setNewTotalSupply(long newTotalSupply) {
			this.newTotalSupply = newTotalSupply;
			return this;
		}

		public Builder setScheduledTxnId(TxnId scheduledTxnId) {
			this.scheduledTxnId = scheduledTxnId;
			return this;
		}

		public TxnReceipt build(){
			return new TxnReceipt(this);
		}
	}
}

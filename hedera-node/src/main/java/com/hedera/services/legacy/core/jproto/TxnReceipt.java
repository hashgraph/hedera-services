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
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
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
	static final int RELEASE_0140_VERSION = 7;
	static final int MERKLE_VERSION = RELEASE_0140_VERSION;
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
	EntityId nftId;
	ExchangeRates exchangeRates;
	Long newTotalSupply = -1L;

	public TxnReceipt() { }

	TxnReceipt(
			@Nullable String status,
			@Nullable EntityId accountId,
			@Nullable EntityId fileId,
			@Nullable EntityId contractId,
			@Nullable EntityId tokenId,
			@Nullable EntityId scheduleId,
			@Nullable ExchangeRates exchangeRate,
			@Nullable EntityId topicId,
			@Nullable EntityId nftId,
			long topicSequenceNumber,
			@Nullable byte[] topicRunningHash,
			long runningHashVersion,
			long newTotalSupply,
			@Nullable TxnId scheduledTxnId
	) {
		this.status = status;
		this.accountId = accountId;
		this.fileId = fileId;
		this.contractId = contractId;
		this.exchangeRates = exchangeRate;
		this.topicId = topicId;
		this.nftId = nftId;
		this.tokenId = tokenId;
		this.scheduleId = scheduleId;
		this.topicSequenceNumber = topicSequenceNumber;
		this.topicRunningHash = ((topicRunningHash != MISSING_RUNNING_HASH) && (topicRunningHash.length > 0))
				? topicRunningHash
				: MISSING_RUNNING_HASH;
		this.runningHashVersion = runningHashVersion;
		this.newTotalSupply = newTotalSupply;
		this.scheduledTxnId = scheduledTxnId;
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
		serdes.writeNullableSerializable(nftId, out);
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
		if (version >= RELEASE_0140_VERSION) {
			nftId = serdes.readNullableSerializable(in);
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

	public EntityId getNftId() {
		return nftId;
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
				Objects.equals(nftId, that.nftId) &&
				Objects.equals(topicSequenceNumber, that.topicSequenceNumber) &&
				Arrays.equals(topicRunningHash, that.topicRunningHash) &&
				Objects.equals(newTotalSupply, that.newTotalSupply) &&
				Objects.equals(scheduledTxnId, that.scheduledTxnId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
				runningHashVersion, status,
				accountId, fileId, contractId, topicId, tokenId, nftId,
				topicSequenceNumber, Arrays.hashCode(topicRunningHash),
				newTotalSupply, scheduledTxnId);
	}

	@Override
	public String toString() {
		var helper = MoreObjects.toStringHelper(this)
				.add("status", status)
				.add("exchangeRates", exchangeRates);
		if (accountId != null) {
			helper.add("accountCreated", accountId);
		}
		if (fileId != null) {
			helper.add("fileCreated", fileId);
		}
		if (tokenId != null) {
			helper.add("tokenCreated", tokenId);
		}
		if (nftId != null) {
			helper.add("nftCreated", nftId);
		}
		if (contractId != null) {
			helper.add("contractCreated", contractId);
		}
		if (topicId != null) {
			helper.add("topicCreated", topicId);
		}
		if (topicRunningHash != MISSING_RUNNING_HASH) {
			helper.add("topicSeqNo", topicSequenceNumber);
			helper.add("topicRunningHash", Hex.encodeHexString(topicRunningHash));
			helper.add("runningHashVersion", runningHashVersion);
		}
		helper.add("newTotalTokenSupply", newTotalSupply);
		if (scheduledTxnId != MISSING_SCHEDULED_TXN_ID) {
			helper.add("scheduledTxnId", scheduledTxnId);
		}
		return helper.toString();
	}

	/* ---  Helpers --- */

	public static TxnReceipt fromGrpc(TransactionReceipt grpc) {
		String status = grpc.getStatus() != null ? grpc.getStatus().name() : null;
		EntityId accountId =
				grpc.hasAccountID() ? EntityId.ofNullableAccountId(grpc.getAccountID()) : null;
		EntityId jFileID = grpc.hasFileID() ? EntityId.ofNullableFileId(grpc.getFileID()) : null;
		EntityId jContractID =
				grpc.hasContractID() ? EntityId.ofNullableContractId(grpc.getContractID()) : null;
		EntityId topicId = grpc.hasTopicID() ? EntityId.ofNullableTopicId(grpc.getTopicID()) : null;
		EntityId tokenId = grpc.hasTokenID() ? EntityId.ofNullableTokenId(grpc.getTokenID()) : null;
		EntityId scheduleId = grpc.hasScheduleID() ? EntityId.ofNullableScheduleId(grpc.getScheduleID()) : null;
		EntityId nftId = grpc.hasNftID() ? EntityId.ofNullableNftId(grpc.getNftID()) : null;
		long runningHashVersion = Math.max(MISSING_RUNNING_HASH_VERSION, grpc.getTopicRunningHashVersion());
		long newTotalSupply = grpc.getNewTotalSupply();
		TxnId scheduledTxnId = grpc.hasScheduledTransactionID()
				? TxnId.fromGrpc(grpc.getScheduledTransactionID())
				: MISSING_SCHEDULED_TXN_ID;

		return new TxnReceipt(
				status,
				accountId,
				jFileID,
				jContractID,
				tokenId,
				scheduleId,
				ExchangeRates.fromGrpc(grpc.getExchangeRate()),
				topicId,
				nftId,
				grpc.getTopicSequenceNumber(),
				grpc.getTopicRunningHash().toByteArray(),
				runningHashVersion,
				newTotalSupply,
				scheduledTxnId);
	}

	public TransactionReceipt toGrpc() {
		return convert(this);
	}

	public static TransactionReceipt convert(TxnReceipt txReceipt) {
		TransactionReceipt.Builder builder = TransactionReceipt.newBuilder()
				.setStatus(ResponseCodeEnum.valueOf(txReceipt.getStatus()));
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
		if (txReceipt.getNftId() != null) {
			builder.setNftID(txReceipt.getNftId().toGrpcNftId());
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

	/* These constructors are only used in tests. */
	TxnReceipt(
			@Nullable String status,
			@Nullable EntityId accountId,
			@Nullable EntityId fileId,
			@Nullable EntityId contractId,
			@Nullable EntityId tokenId,
			@Nullable EntityId scheduleId,
			@Nullable ExchangeRates exchangeRates,
			@Nullable EntityId topicId,
			@Nullable EntityId nftId,
			long topicSequenceNumber,
			@Nullable byte[] topicRunningHash
	) {
		this(
				status,
				accountId,
				fileId,
				contractId,
				tokenId,
				scheduleId,
				exchangeRates,
				topicId,
				nftId,
				topicSequenceNumber,
				topicRunningHash,
				MISSING_RUNNING_HASH_VERSION);
	}

	TxnReceipt(
			@Nullable String status,
			@Nullable EntityId accountId,
			@Nullable EntityId fileId,
			@Nullable EntityId contractId,
			@Nullable EntityId tokenId,
			@Nullable EntityId scheduleId,
			@Nullable ExchangeRates exchangeRates,
			@Nullable EntityId topicId,
			@Nullable EntityId nftId,
			long topicSequenceNumber,
			@Nullable byte[] topicRunningHash,
			long    runningHashVersion
	) {
		this(
				status,
				accountId,
				fileId,
				contractId,
				tokenId,
				scheduleId,
				exchangeRates,
				topicId,
				nftId,
				topicSequenceNumber,
				topicRunningHash,
				runningHashVersion,
				RELEASE_070_VERSION,
				MISSING_SCHEDULED_TXN_ID);
	}
}

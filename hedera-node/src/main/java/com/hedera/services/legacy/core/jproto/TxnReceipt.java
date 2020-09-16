package com.hedera.services.legacy.core.jproto;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import static com.swirlds.common.CommonUtils.getNormalisedStringFromBytes;
import static org.apache.commons.codec.binary.StringUtils.newStringUtf8;

public class TxnReceipt implements SelfSerializable {
	private static final Logger log = LogManager.getLogger(TxnReceipt.class);

	private static final int MAX_STATUS_BYTES = 128;
	private static final int MAX_RUNNING_HASH_BYTES = 1024;

	static final byte[] MISSING_RUNNING_HASH = null;
	static final long MISSING_TOPIC_SEQ_NO = 0L;
	static final long MISSING_RUNNING_HASH_VERSION = 0L;

	static final int RELEASE_070_VERSION = 1;
	static final int RELEASE_080_VERSION = 2;
	static final int MERKLE_VERSION = RELEASE_080_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x65ef569a77dcf125L;

	static DomainSerdes serdes = new DomainSerdes();
	static EntityId.Provider legacyIdProvider = EntityId.LEGACY_PROVIDER;
	static ExchangeRates.Provider legacyRatesProvider = ExchangeRates.LEGACY_PROVIDER;
	public static final TxnReceipt.Provider LEGACY_PROVIDER = new TxnReceipt.Provider();

	@Deprecated
	public static class Provider {
		private static final long VERSION_WITHOUT_FINAL_RUNNING_HASH = 3;

		public TxnReceipt deserialize(DataInputStream in) throws IOException {
			var receipt = new TxnReceipt();

			var version = in.readLong();
			in.readLong();
			if (in.readBoolean()) {
				receipt.accountId = legacyIdProvider.deserialize(in);
			}
			if (in.readBoolean()) {
				receipt.fileId = legacyIdProvider.deserialize(in);
			}
			if (in.readBoolean()) {
				receipt.contractId = legacyIdProvider.deserialize(in);
			}
			int numStatusBytes = in.readInt();
			if (numStatusBytes > 0) {
				byte[] statusBytes = new byte[numStatusBytes];
				in.readFully(statusBytes);
				receipt.status = newStringUtf8(statusBytes);
			}
			if (in.readBoolean()) {
				receipt.exchangeRates = legacyRatesProvider.deserialize(in);
			}

			if (in.readBoolean()) {
				receipt.topicId = legacyIdProvider.deserialize(in);
			}
			if (in.readBoolean()) {
				receipt.topicSequenceNumber = in.readLong();
				int numHashBytes = in.readInt();
				if (numHashBytes > 0) {
					receipt.topicRunningHash = new byte[numHashBytes];
					in.readFully(receipt.topicRunningHash);
				}
			}
			if (version != VERSION_WITHOUT_FINAL_RUNNING_HASH) {
				receipt.runningHashVersion = in.readLong();
			}

			return receipt;
		}
	}

	long runningHashVersion = MISSING_RUNNING_HASH_VERSION;
	long topicSequenceNumber = MISSING_TOPIC_SEQ_NO;
	byte[] topicRunningHash = MISSING_RUNNING_HASH;
	String status;
	EntityId accountId;
	EntityId fileId;
	EntityId topicId;
	EntityId tokenId;
	EntityId contractId;
	ExchangeRates exchangeRates;

	public TxnReceipt() {
	}

	public TxnReceipt(
			@Nullable String status,
			@Nullable EntityId accountId,
			@Nullable EntityId fileId,
			@Nullable EntityId contractId,
			@Nullable EntityId tokenId,
			@Nullable ExchangeRates exchangeRates,
			@Nullable EntityId topicId,
			long topicSequenceNumber,
			@Nullable byte[] topicRunningHash
	) {
		this(
				status,
				accountId,
				fileId,
				contractId,
				tokenId,
				exchangeRates,
				topicId,
				topicSequenceNumber,
				topicRunningHash,
				MISSING_RUNNING_HASH_VERSION);
	}

	public TxnReceipt(
			@Nullable String status,
			@Nullable EntityId accountId,
			@Nullable EntityId fileId,
			@Nullable EntityId contractId,
			@Nullable EntityId tokenId,
			@Nullable ExchangeRates exchangeRate,
			@Nullable EntityId topicId,
			long topicSequenceNumber,
			@Nullable byte[] topicRunningHash,
			long runningHashVersion
	) {
		this.status = status;
		this.accountId = accountId;
		this.fileId = fileId;
		this.contractId = contractId;
		this.exchangeRates = exchangeRate;
		this.topicId = topicId;
		this.tokenId = tokenId;
		this.topicSequenceNumber = topicSequenceNumber;
		this.topicRunningHash = ((topicRunningHash != MISSING_RUNNING_HASH) && (topicRunningHash.length > 0))
				? topicRunningHash
				: MISSING_RUNNING_HASH;
		this.runningHashVersion = runningHashVersion;
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
		if (topicRunningHash == MISSING_RUNNING_HASH) {
			out.writeBoolean(false);
		} else {
			out.writeBoolean(true);
			out.writeLong(topicSequenceNumber);
			out.writeLong(runningHashVersion);
			out.writeByteArray(topicRunningHash);
		}
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
		var isSubmitMessageReceipt = in.readBoolean();
		if (isSubmitMessageReceipt) {
			topicSequenceNumber = in.readLong();
			runningHashVersion = in.readLong();
			topicRunningHash = in.readByteArray(MAX_RUNNING_HASH_BYTES);
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

	public long getTopicSequenceNumber() {
		return topicSequenceNumber;
	}

	public byte[] getTopicRunningHash() {
		return topicRunningHash;
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
				Arrays.equals(topicRunningHash, that.topicRunningHash);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
				runningHashVersion, status,
				accountId, fileId, contractId, topicId, tokenId,
				topicSequenceNumber, Arrays.hashCode(topicRunningHash));
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
		EntityId tokenId = grpc.hasTokenId() ? EntityId.ofNullableTokenId(grpc.getTokenId()) : null;
		long runningHashVersion = Math.max(MISSING_RUNNING_HASH_VERSION, grpc.getTopicRunningHashVersion());
		return new TxnReceipt(
				status,
				accountId,
				jFileID,
				jContractID,
				tokenId,
				ExchangeRates.fromGrpc(grpc.getExchangeRate()),
				topicId,
				grpc.getTopicSequenceNumber(),
				grpc.getTopicRunningHash().toByteArray(),
				runningHashVersion);
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
			builder.setTokenId(txReceipt.getTokenId().toGrpcTokenId());
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
		return builder.build();
	}

}

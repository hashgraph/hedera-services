package com.hedera.services.state.submerkle;

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
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.common.CommonUtils;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.fcqueue.FCQueueElement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import static com.hedera.services.state.submerkle.EntityId.fromGrpcScheduleId;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class ExpirableTxnRecord implements FCQueueElement {
	public static final long UNKNOWN_SUBMITTING_MEMBER = -1;
	static final List<EntityId> NO_TOKENS = null;
	static final List<CurrencyAdjustments> NO_TOKEN_ADJUSTMENTS = null;
	static final List<AssessedCustomFee> NO_CUSTOM_FEES = null;
	static final EntityId NO_SCHEDULE_REF = null;

	private static final byte[] MISSING_TXN_HASH = new byte[0];

	static final int RELEASE_070_VERSION = 1;
	static final int RELEASE_080_VERSION = 2;
	static final int RELEASE_0120_VERSION = 3;
	static final int RELEASE_0160_VERSION = 4;
	static final int MERKLE_VERSION = RELEASE_0160_VERSION;

	static final int MAX_MEMO_BYTES = 32 * 1_024;
	static final int MAX_TXN_HASH_BYTES = 1_024;
	static final int MAX_INVOLVED_TOKENS = 10;
	static final int MAX_ASSESSED_CUSTOM_FEES_CHANGES = 20;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x8b9ede7ca8d8db93L;

	static DomainSerdes serdes = new DomainSerdes();

	private long expiry;
	private long submittingMember = UNKNOWN_SUBMITTING_MEMBER;

	private long fee;
	private Hash hash;
	private TxnId txnId;
	private byte[] txnHash = MISSING_TXN_HASH;
	private String memo;
	private TxnReceipt receipt;
	private RichInstant consensusTimestamp;
	private CurrencyAdjustments hbarAdjustments;
	private SolidityFnResult contractCallResult;
	private SolidityFnResult contractCreateResult;
	private List<EntityId> tokens = NO_TOKENS;
	private List<CurrencyAdjustments> tokenAdjustments = NO_TOKEN_ADJUSTMENTS;
	private EntityId scheduleRef = NO_SCHEDULE_REF;
	private List<AssessedCustomFee> customFeesCharged = NO_CUSTOM_FEES;

	@Override
	public void release() {
		/* No-op */
	}

	public ExpirableTxnRecord() {
	}

	public ExpirableTxnRecord(Builder builder) {
		this.receipt = builder.receipt;
		this.txnHash = builder.txnHash;
		this.txnId = builder.txnId;
		this.consensusTimestamp = builder.consensusTime;
		this.memo = builder.memo;
		this.fee = builder.fee;
		this.hbarAdjustments = builder.transferList;
		this.contractCallResult = builder.contractCallResult;
		this.contractCreateResult = builder.contractCreateResult;
		this.tokens = builder.tokens;
		this.tokenAdjustments = builder.tokenAdjustments;
		this.scheduleRef = builder.scheduleRef;
		this.customFeesCharged = builder.customFeesCharged;
	}

	/* --- Object --- */

	@Override
	public String toString() {
		var helper = MoreObjects.toStringHelper(this)
				.omitNullValues()
				.add("receipt", receipt)
				.add("txnHash", CommonUtils.hex(txnHash))
				.add("txnId", txnId)
				.add("consensusTimestamp", consensusTimestamp)
				.add("expiry", expiry)
				.add("submittingMember", submittingMember)
				.add("memo", memo)
				.add("contractCreation", contractCreateResult)
				.add("contractCall", contractCallResult)
				.add("hbarAdjustments", hbarAdjustments)
				.add("scheduleRef", scheduleRef);

		if (tokens != NO_TOKENS) {
			int n = tokens.size();
			var readable = IntStream.range(0, n)
					.mapToObj(i -> String.format(
							"%s(%s)",
							tokens.get(i).toAbbrevString(),
							tokenAdjustments.get(i)))
					.collect(joining(", "));
			helper.add("tokenAdjustments", readable);
		}

		if (customFeesCharged != NO_CUSTOM_FEES) {
			int n = customFeesCharged.size();
			var readable = IntStream.range(0, n)
					.mapToObj(i -> String.format("(%s)", customFeesCharged.get(i)))
					.collect(joining(", "));
			helper.add("customFeesCharged", readable);
		}
		return helper.toString();
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
		return fee == that.fee &&
				this.expiry == that.expiry &&
				this.submittingMember == that.submittingMember &&
				this.receipt.equals(that.receipt) &&
				Arrays.equals(this.txnHash, that.txnHash) &&
				this.txnId.equals(that.txnId) &&
				Objects.equals(this.consensusTimestamp, that.consensusTimestamp) &&
				Objects.equals(this.memo, that.memo) &&
				Objects.equals(this.contractCallResult, that.contractCallResult) &&
				Objects.equals(this.contractCreateResult, that.contractCreateResult) &&
				Objects.equals(this.hbarAdjustments, that.hbarAdjustments) &&
				Objects.equals(this.tokens, that.tokens) &&
				Objects.equals(this.tokenAdjustments, that.tokenAdjustments) &&
				Objects.equals(this.scheduleRef, that.scheduleRef) &&
				Objects.equals(this.customFeesCharged, that.customFeesCharged);
	}

	@Override
	public int hashCode() {
		var result = Objects.hash(
				receipt,
				txnId,
				consensusTimestamp,
				memo,
				fee,
				contractCallResult,
				contractCreateResult,
				hbarAdjustments,
				expiry,
				submittingMember,
				tokens,
				tokenAdjustments,
				scheduleRef,
				customFeesCharged);
		return result * 31 + Arrays.hashCode(txnHash);
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
		serdes.writeNullableSerializable(receipt, out);

		out.writeByteArray(txnHash);

		serdes.writeNullableSerializable(txnId, out);
		serdes.writeNullableInstant(consensusTimestamp, out);
		serdes.writeNullableString(memo, out);

		out.writeLong(this.fee);

		serdes.writeNullableSerializable(hbarAdjustments, out);
		serdes.writeNullableSerializable(contractCallResult, out);
		serdes.writeNullableSerializable(contractCreateResult, out);

		out.writeLong(expiry);
		out.writeLong(submittingMember);

		out.writeSerializableList(tokens, true, true);
		out.writeSerializableList(tokenAdjustments, true, true);

		serdes.writeNullableSerializable(scheduleRef, out);
		out.writeSerializableList(customFeesCharged, true, true);
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		receipt = serdes.readNullableSerializable(in);
		txnHash = in.readByteArray(MAX_TXN_HASH_BYTES);
		txnId = serdes.readNullableSerializable(in);
		consensusTimestamp = serdes.readNullableInstant(in);
		memo = serdes.readNullableString(in, MAX_MEMO_BYTES);
		fee = in.readLong();
		hbarAdjustments = serdes.readNullableSerializable(in);
		contractCallResult = serdes.readNullableSerializable(in);
		contractCreateResult = serdes.readNullableSerializable(in);
		expiry = in.readLong();
		submittingMember = in.readLong();
		if (version > RELEASE_070_VERSION) {
			tokens = in.readSerializableList(MAX_INVOLVED_TOKENS);
			tokenAdjustments = in.readSerializableList(MAX_INVOLVED_TOKENS);
		}
		if (version > RELEASE_080_VERSION) {
			scheduleRef = serdes.readNullableSerializable(in);
		}

		if (version >= RELEASE_0160_VERSION) {
			customFeesCharged = in.readSerializableList(MAX_ASSESSED_CUSTOM_FEES_CHANGES);
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

	public TxnReceipt getReceipt() {
		return receipt;
	}

	public byte[] getTxnHash() {
		return txnHash;
	}

	public TxnId getTxnId() {
		return txnId;
	}

	public RichInstant getConsensusTimestamp() {
		return consensusTimestamp;
	}

	public String getMemo() {
		return memo;
	}

	public long getFee() {
		return fee;
	}

	public SolidityFnResult getContractCallResult() {
		return contractCallResult;
	}

	public SolidityFnResult getContractCreateResult() {
		return contractCreateResult;
	}

	public CurrencyAdjustments getHbarAdjustments() {
		return hbarAdjustments;
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

	public List<AssessedCustomFee> getCustomFeesCharged() {
		return customFeesCharged;
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

	/* --- Helpers --- */

	public static ExpirableTxnRecord fromGprc(TransactionRecord record) {
		List<EntityId> tokens = NO_TOKENS;
		List<CurrencyAdjustments> tokenAdjustments = NO_TOKEN_ADJUSTMENTS;
		int n = record.getTokenTransferListsCount();
		if (n > 0) {
			tokens = new ArrayList<>();
			tokenAdjustments = new ArrayList<>();
			for (TokenTransferList tokenTransfers : record.getTokenTransferListsList()) {
				tokens.add(EntityId.fromGrpcTokenId(tokenTransfers.getToken()));
				tokenAdjustments.add(CurrencyAdjustments.fromGrpc(tokenTransfers.getTransfersList()));
			}

		}

		final var fcAssessedFees = record.getAssessedCustomFeesCount() > 0
				? record.getAssessedCustomFeesList().stream().map(AssessedCustomFee::fromGrpc).collect(toList())
				: null;
		return ExpirableTxnRecord.newBuilder()
				.setReceipt(TxnReceipt.fromGrpc(record.getReceipt()))
				.setTxnHash(record.getTransactionHash().toByteArray())
				.setTxnId(TxnId.fromGrpc(record.getTransactionID()))
				.setConsensusTime(RichInstant.fromGrpc(record.getConsensusTimestamp()))
				.setMemo(record.getMemo())
				.setFee(record.getTransactionFee())
				.setTransferList(
						record.hasTransferList() ? CurrencyAdjustments.fromGrpc(record.getTransferList()) : null)
				.setContractCallResult(record.hasContractCallResult() ? SolidityFnResult.fromGrpc(
						record.getContractCallResult()) : null)
				.setContractCreateResult(record.hasContractCreateResult() ? SolidityFnResult.fromGrpc(
						record.getContractCreateResult()) : null)
				.setTokens(tokens)
				.setTokenAdjustments(tokenAdjustments)
				.setScheduleRef(record.hasScheduleRef() ? fromGrpcScheduleId(record.getScheduleRef()) : null)
				.setCustomFeesCharged(fcAssessedFees)
				.build();
	}

	public static List<TransactionRecord> allToGrpc(List<ExpirableTxnRecord> records) {
		return records.stream()
				.map(ExpirableTxnRecord::asGrpc)
				.collect(toList());
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
		if (consensusTimestamp != null) {
			grpc.setConsensusTimestamp(consensusTimestamp.toGrpc());
		}
		if (memo != null) {
			grpc.setMemo(memo);
		}
		if (txnHash != null && txnHash.length > 0) {
			grpc.setTransactionHash(ByteString.copyFrom(txnHash));
		}
		if (hbarAdjustments != null) {
			grpc.setTransferList(hbarAdjustments.toGrpc());
		}
		if (contractCallResult != null) {
			grpc.setContractCallResult(contractCallResult.toGrpc());
		}
		if (contractCreateResult != null) {
			grpc.setContractCreateResult(contractCreateResult.toGrpc());
		}
		if (tokens != NO_TOKENS) {
			for (int i = 0, n = tokens.size(); i < n; i++) {
				grpc.addTokenTransferLists(TokenTransferList.newBuilder()
						.setToken(tokens.get(i).toGrpcTokenId())
						.addAllTransfers(tokenAdjustments.get(i).toGrpc().getAccountAmountsList()));
			}
		}

		if (scheduleRef != NO_SCHEDULE_REF) {
			grpc.setScheduleRef(scheduleRef.toGrpcScheduleId());
		}

		if (customFeesCharged != NO_CUSTOM_FEES) {
			grpc.addAllAssessedCustomFees(customFeesCharged.stream().map(AssessedCustomFee::toGrpc).collect(toList()));
		}

		return grpc.build();
	}

	public static Builder newBuilder() {
		return new Builder();
	}

	public static class Builder {
		private TxnReceipt receipt;
		private byte[] txnHash;
		private TxnId txnId;
		private RichInstant consensusTime;
		private String memo;
		private long fee;
		private CurrencyAdjustments transferList;
		private SolidityFnResult contractCallResult;
		private SolidityFnResult contractCreateResult;
		private List<EntityId> tokens;
		private List<CurrencyAdjustments> tokenAdjustments;
		private EntityId scheduleRef;
		private List<AssessedCustomFee> customFeesCharged;

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

		public Builder setConsensusTime(RichInstant consensusTime) {
			this.consensusTime = consensusTime;
			return this;
		}

		public Builder setTransferList(CurrencyAdjustments hbarAdjustments) {
			this.transferList = hbarAdjustments;
			return this;
		}

		public Builder setContractCallResult(SolidityFnResult contractCallResult) {
			this.contractCallResult = contractCallResult;
			return this;
		}

		public Builder setContractCreateResult(SolidityFnResult contractCreateResult) {
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

		public Builder setScheduleRef(EntityId scheduleRef) {
			this.scheduleRef = scheduleRef;
			return this;
		}

		public Builder setCustomFeesCharged(List<AssessedCustomFee> customFeesCharged) {
			this.customFeesCharged = customFeesCharged;
			return this;
		}

		public ExpirableTxnRecord build() {
			return new ExpirableTxnRecord(this);
		}

		public Builder clear() {
			fee = 0;
			txnId = null;
			txnHash = MISSING_TXN_HASH;
			memo = null;
			receipt = null;
			consensusTime = null;
			transferList = null;
			contractCallResult = null;
			contractCreateResult = null;
			tokens = NO_TOKENS;
			tokenAdjustments = NO_TOKEN_ADJUSTMENTS;
			scheduleRef = NO_SCHEDULE_REF;
			customFeesCharged = NO_CUSTOM_FEES;
			return this;
		}
	}
}

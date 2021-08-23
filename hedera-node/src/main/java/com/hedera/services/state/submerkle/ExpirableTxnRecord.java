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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class ExpirableTxnRecord implements FCQueueElement {
	public static final long UNKNOWN_SUBMITTING_MEMBER = -1;
	static final List<EntityId> NO_TOKENS = null;
	static final List<CurrencyAdjustments> NO_TOKEN_ADJUSTMENTS = null;
	static final List<NftAdjustments> NO_NFT_TOKEN_ADJUSTMENTS = null;
	static final List<FcAssessedCustomFee> NO_CUSTOM_FEES = null;
	static final EntityId NO_SCHEDULE_REF = null;
	static final List<FcTokenAssociation> NO_NEW_TOKEN_ASSOCIATIONS = Collections.emptyList();

	private static final byte[] MISSING_TXN_HASH = new byte[0];

	static final int RELEASE_0120_VERSION = 3;
	static final int RELEASE_0160_VERSION = 4;
	static final int RELEASE_0180_VERSION = 5;
	static final int MERKLE_VERSION = RELEASE_0180_VERSION;

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
	private List<NftAdjustments> nftTokenAdjustments = NO_NFT_TOKEN_ADJUSTMENTS;
	private EntityId scheduleRef = NO_SCHEDULE_REF;
	private List<FcAssessedCustomFee> assessedCustomFees = NO_CUSTOM_FEES;
	private List<FcTokenAssociation> newTokenAssociations = NO_NEW_TOKEN_ASSOCIATIONS;

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
		this.nftTokenAdjustments = builder.nftTokenAdjustments;

		this.scheduleRef = builder.scheduleRef;
		this.assessedCustomFees = builder.assessedCustomFees;
		this.newTokenAssociations = new ArrayList<>(builder.newTokenAssociations);
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

		if (assessedCustomFees != NO_CUSTOM_FEES) {
			var readable = assessedCustomFees.stream().map(
					assessedCustomFee -> String.format("(%s)", assessedCustomFee))
					.collect(joining(", "));
			helper.add("assessedCustomFees", readable);
		}

		if (newTokenAssociations != NO_NEW_TOKEN_ASSOCIATIONS) {
			var readable = newTokenAssociations.stream().map(
					newTokenAssociation -> String.format("(%s)", newTokenAssociation))
					.collect(joining(", "));
			helper.add("newTokenAssociations", readable);
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
				Objects.equals(this.receipt, that.receipt) &&
				Arrays.equals(this.txnHash, that.txnHash) &&
				this.txnId.equals(that.txnId) &&
				Objects.equals(this.consensusTimestamp, that.consensusTimestamp) &&
				Objects.equals(this.memo, that.memo) &&
				Objects.equals(this.contractCallResult, that.contractCallResult) &&
				Objects.equals(this.contractCreateResult, that.contractCreateResult) &&
				Objects.equals(this.hbarAdjustments, that.hbarAdjustments) &&
				Objects.equals(this.tokens, that.tokens) &&
				Objects.equals(this.tokenAdjustments, that.tokenAdjustments) &&
				Objects.equals(this.nftTokenAdjustments, that.nftTokenAdjustments) &&
				Objects.equals(this.assessedCustomFees, that.assessedCustomFees) &&
				Objects.equals(this.newTokenAssociations, that.newTokenAssociations);
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
				nftTokenAdjustments,
				scheduleRef,
				assessedCustomFees,
				newTokenAssociations);
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
		out.writeSerializableList(nftTokenAdjustments, true, true);
		out.writeSerializableList(assessedCustomFees, true, true);
		out.writeSerializableList(newTokenAssociations, true, true);
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
		/* Tokens present since v0.7.0 */
		tokens = in.readSerializableList(MAX_INVOLVED_TOKENS);
		tokenAdjustments = in.readSerializableList(MAX_INVOLVED_TOKENS); /* Schedule references present since v0.8.0 */
		scheduleRef = serdes.readNullableSerializable(in);
		if (version >= RELEASE_0160_VERSION) {
			nftTokenAdjustments = in.readSerializableList(MAX_INVOLVED_TOKENS);
			assessedCustomFees = in.readSerializableList(MAX_ASSESSED_CUSTOM_FEES_CHANGES);
		}
		if (version >= RELEASE_0180_VERSION) {
			newTokenAssociations = in.readSerializableList(Integer.MAX_VALUE);
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

	public List<FcAssessedCustomFee> getCustomFeesCharged() {
		return assessedCustomFees;
	}

	public List<FcTokenAssociation> getNewTokenAssociations() {
		return newTokenAssociations;
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
			setGrpcTokens(grpc, tokens, tokenAdjustments, nftTokenAdjustments);
		}

		if (scheduleRef != NO_SCHEDULE_REF) {
			grpc.setScheduleRef(scheduleRef.toGrpcScheduleId());
		}

		if (assessedCustomFees != NO_CUSTOM_FEES) {
			grpc.addAllAssessedCustomFees(
					assessedCustomFees.stream().map(FcAssessedCustomFee::toGrpc).collect(toList()));
		}

		if (newTokenAssociations != NO_NEW_TOKEN_ASSOCIATIONS) {
			grpc.addAllAutomaticTokenAssociations(
					newTokenAssociations.stream().map(FcTokenAssociation::toGrpc).collect(toList()));
		}

		return grpc.build();
	}


	private static void setGrpcTokens(TransactionRecord.Builder grpcBuilder,
			final List<EntityId> tokens,
			final List<CurrencyAdjustments> tokenAdjustments,
			final List<NftAdjustments> nftTokenAdjustments) {
		for (int i = 0, n = tokens.size(); i < n; i++) {
			var tokenTransferList = TokenTransferList.newBuilder()
					.setToken(tokens.get(i).toGrpcTokenId());
			if (tokenAdjustments != null && !tokenAdjustments.isEmpty()) {
				tokenTransferList.addAllTransfers(tokenAdjustments.get(i).toGrpc().getAccountAmountsList());
			}
			if (nftTokenAdjustments != null && !nftTokenAdjustments.isEmpty()) {
				tokenTransferList.addAllNftTransfers(nftTokenAdjustments.get(i).toGrpc().getNftTransfersList());
			}
			grpcBuilder.addTokenTransferLists(tokenTransferList);
		}
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
		private List<NftAdjustments> nftTokenAdjustments;
		private EntityId scheduleRef;
		private List<FcAssessedCustomFee> assessedCustomFees;
		private List<FcTokenAssociation> newTokenAssociations = NO_NEW_TOKEN_ASSOCIATIONS;

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

		public Builder setNftTokenAdjustments(List<NftAdjustments> nftTokenAdjustments) {
			this.nftTokenAdjustments = nftTokenAdjustments;
			return this;
		}

		public Builder setScheduleRef(EntityId scheduleRef) {
			this.scheduleRef = scheduleRef;
			return this;
		}

		public Builder setCustomFeesCharged(List<FcAssessedCustomFee> assessedCustomFees) {
			this.assessedCustomFees = assessedCustomFees;
			return this;
		}

		public Builder setNewTokenAssociations(List<FcTokenAssociation> newTokenAssociations) {
			this.newTokenAssociations = newTokenAssociations;
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
			nftTokenAdjustments = NO_NFT_TOKEN_ADJUSTMENTS;
			scheduleRef = NO_SCHEDULE_REF;
			assessedCustomFees = NO_CUSTOM_FEES;
			newTokenAssociations = NO_NEW_TOKEN_ASSOCIATIONS;
			return this;
		}
	}
}

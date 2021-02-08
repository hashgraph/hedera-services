package com.hedera.services.state.submerkle;

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
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.fcqueue.FCQueueElement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.encoders.Hex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import static com.hedera.services.state.submerkle.EntityId.ofNullableScheduleId;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class ExpirableTxnRecord implements FCQueueElement<ExpirableTxnRecord> {
	public static final long UNKNOWN_SUBMITTING_MEMBER = -1;

	private static final Logger log = LogManager.getLogger(ExpirableTxnRecord.class);

	static final List<EntityId> NO_TOKENS = null;
	static final List<CurrencyAdjustments> NO_TOKEN_ADJUSTMENTS = null;
	static final EntityId NO_SCHEDULE_REF = null;

	private static final byte[] MISSING_TXN_HASH = new byte[0];

	static final int RELEASE_070_VERSION = 1;
	static final int RELEASE_080_VERSION = 2;
	static final int RELEASE_0120_VERSION = 3;
	static final int MERKLE_VERSION = RELEASE_0120_VERSION;

	static final int MAX_MEMO_BYTES = 32 * 1_024;
	static final int MAX_TXN_HASH_BYTES = 1_024;
	static final int MAX_INVOLVED_TOKENS = 10;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x8b9ede7ca8d8db93L;

	static DomainSerdes serdes = new DomainSerdes();
	static TxnId.Provider legacyTxnIdProvider = TxnId.LEGACY_PROVIDER;
	static TxnReceipt.Provider legacyReceiptProvider = TxnReceipt.LEGACY_PROVIDER;
	static RichInstant.Provider legacyInstantProvider = RichInstant.LEGACY_PROVIDER;
	static CurrencyAdjustments.Provider legacyAdjustmentsProvider = CurrencyAdjustments.LEGACY_PROVIDER;
	static SolidityFnResult.Provider legacyFnResultProvider = SolidityFnResult.LEGACY_PROVIDER;

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

	@Override
	public void release() {
		/* No-op */
	}

	public ExpirableTxnRecord() {
	}

	public ExpirableTxnRecord(
			TxnReceipt receipt,
			byte[] txnHash,
			TxnId txnId,
			RichInstant consensusTimestamp,
			String memo,
			long fee,
			CurrencyAdjustments transferList,
			SolidityFnResult contractCallResult,
			SolidityFnResult createResult
	) {
		this(
				receipt,
				txnHash,
				txnId,
				consensusTimestamp,
				memo,
				fee,
				transferList,
				contractCallResult,
				createResult,
				NO_TOKENS,
				NO_TOKEN_ADJUSTMENTS,
				NO_SCHEDULE_REF);
	}

	public ExpirableTxnRecord(
			TxnReceipt receipt,
			byte[] txnHash,
			TxnId txnId,
			RichInstant consensusTimestamp,
			String memo,
			long fee,
			CurrencyAdjustments transferList,
			SolidityFnResult contractCallResult,
			SolidityFnResult createResult,
			List<EntityId> tokens,
			List<CurrencyAdjustments> tokenTransferLists,
			EntityId scheduleRef
	) {
		this.receipt = receipt;
		this.txnHash = txnHash;
		this.txnId = txnId;
		this.consensusTimestamp = consensusTimestamp;
		this.memo = memo;
		this.fee = fee;
		this.hbarAdjustments = transferList;
		this.contractCallResult = contractCallResult;
		this.contractCreateResult = createResult;
		this.tokens = tokens;
		this.tokenAdjustments = tokenTransferLists;
		this.scheduleRef = scheduleRef;
	}

	/* --- Object --- */

	@Override
	public String toString() {
		var helper = MoreObjects.toStringHelper(this)
				.add("receipt", receipt)
				.add("txnHash", Hex.toHexString(txnHash))
				.add("txnId", txnId)
				.add("consensusTimestamp", consensusTimestamp)
				.add("expiry", expiry)
				.add("submittingMember", submittingMember);
		if (memo != null) {
			helper.add("memo", memo);
		}
		if (contractCreateResult != null) {
			helper.add("contractCreation", contractCreateResult);
		}
		if (contractCallResult != null) {
			helper.add("contractCall", contractCallResult);
		}
		if (hbarAdjustments != null) {
			helper.add("hbarAdjustments", hbarAdjustments);
		}
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
		if (scheduleRef != NO_SCHEDULE_REF) {
			helper.add("scheduleRef", scheduleRef);
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
				Objects.equals(this.scheduleRef, that.scheduleRef);
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
				scheduleRef);
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

	public EntityId getScheduleRef() { return scheduleRef; }

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
				tokens.add(EntityId.ofNullableTokenId(tokenTransfers.getToken()));
				tokenAdjustments.add(CurrencyAdjustments.fromGrpc(tokenTransfers.getTransfersList()));
			}

		}
		return new ExpirableTxnRecord(
				TxnReceipt.fromGrpc(record.getReceipt()),
				record.getTransactionHash().toByteArray(),
				TxnId.fromGrpc(record.getTransactionID()),
				RichInstant.fromGrpc(record.getConsensusTimestamp()),
				record.getMemo(),
				record.getTransactionFee(),
				record.hasTransferList() ? CurrencyAdjustments.fromGrpc(record.getTransferList()) : null,
				record.hasContractCallResult() ? SolidityFnResult.fromGrpc(record.getContractCallResult()) : null,
				record.hasContractCreateResult() ? SolidityFnResult.fromGrpc(record.getContractCreateResult()) : null,
				tokens,
				tokenAdjustments,
				record.hasScheduleRef() ? ofNullableScheduleId(record.getScheduleRef()) : null);
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
		if (txnHash.length > 0) {
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

		return grpc.build();
	}
}

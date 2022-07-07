package com.hedera.services.state.merkle;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.exceptions.UnknownHederaFunctionality;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import com.swirlds.common.merkle.utility.Keyed;

import javax.annotation.Nullable;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.protobuf.ByteString.copyFrom;
import static com.hedera.services.state.serdes.IoUtils.readNullable;
import static com.hedera.services.state.serdes.IoUtils.writeNullable;
import static com.hedera.services.utils.MiscUtils.asTimestamp;
import static com.hedera.services.utils.MiscUtils.describe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NONE;

/**
 * @deprecated Scheduled transactions are now stored in {@link MerkleScheduledTransactions}
 */
@Deprecated(since = "0.27")
public class MerkleSchedule extends AbstractMerkleLeaf implements Keyed<EntityNum> {
	static final int RELEASE_0180_VERSION = 2;
	static final int CURRENT_VERSION = RELEASE_0180_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x8d2b7d9e673285fcL;

	static final int MAX_NUM_PUBKEY_BYTES = 33;

	@Nullable
	private Key grpcAdminKey = null;
	@Nullable
	private JKey adminKey = null;
	private String memo;
	private boolean deleted = false;
	private boolean executed = false;
	@Nullable
	private EntityId payer = null;
	private EntityId schedulingAccount;
	private RichInstant schedulingTXValidStart;
	private long expiry;
	@Nullable
	private RichInstant resolutionTime = null;

	private int number;

	private byte[] bodyBytes;
	private TransactionBody ordinaryScheduledTxn;
	private SchedulableTransactionBody scheduledTxn;

	private final List<byte[]> signatories = new ArrayList<>();
	private final Set<ByteString> notary = ConcurrentHashMap.newKeySet();

	public MerkleSchedule() {
		/* RuntimeConstructable */
	}

	static MerkleSchedule from(byte[] bodyBytes, long consensusExpiry) {
		var to = new MerkleSchedule();
		to.expiry = consensusExpiry;
		to.bodyBytes = bodyBytes;
		to.initFromBodyBytes();

		return to;
	}

	/* Notary functions */
	boolean witnessValidSignature(byte[] key) {
		final var usableKey = copyFrom(key);
		if (notary.contains(usableKey)) {
			return false;
		} else {
			signatories.add(key);
			notary.add(usableKey);
			return true;
		}
	}

	Transaction asSignedTxn() {
		return Transaction.newBuilder()
				.setSignedTransactionBytes(
						SignedTransaction.newBuilder()
								.setBodyBytes(ordinaryScheduledTxn.toByteString())
								.build()
								.toByteString())
				.build();
	}

	TransactionID scheduledTransactionId() {
		if (schedulingAccount == null || schedulingTXValidStart == null) {
			throw new IllegalStateException("Cannot invoke scheduledTransactionId on a content-addressable view!");
		}
		return TransactionID.newBuilder()
				.setAccountID(schedulingAccount.toGrpcAccountId())
				.setTransactionValidStart(asTimestamp(schedulingTXValidStart))
				.setScheduled(true)
				.build();
	}

	boolean hasValidSignatureFor(byte[] key) {
		return notary.contains(copyFrom(key));
	}

	/**
	 * Two {@code MerkleSchedule}s are identical as long as they agree on
	 * the transaction being scheduled, the admin key used to manage it,
	 * and the memo to accompany it.
	 *
	 * @param o
	 * 		the object to check for equality
	 * @return whether {@code this} and {@code o} are identical
	 */
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || MerkleSchedule.class != o.getClass()) {
			return false;
		}

		var that = (MerkleSchedule) o;
		return Objects.equals(this.memo, that.memo) &&
				Objects.equals(this.scheduledTxn, that.scheduledTxn) &&
				Objects.equals(this.grpcAdminKey, that.grpcAdminKey);
	}

	@Override
	public int hashCode() {
		return Objects.hash(memo, grpcAdminKey, scheduledTxn);
	}

	@Override
	public String toString() {
		var helper = MoreObjects.toStringHelper(MerkleSchedule.class)
				.add("number", number + " <-> " + EntityIdUtils.asIdLiteral(number))
				.add("scheduledTxn", scheduledTxn)
				.add("expiry", expiry)
				.add("executed", executed)
				.add("deleted", deleted)
				.add("memo", memo)
				.add("payer", readablePayer())
				.add("schedulingAccount", schedulingAccount)
				.add("schedulingTXValidStart", schedulingTXValidStart)
				.add("signatories", signatories.stream().map(CommonUtils::hex).toList())
				.add("adminKey", describe(adminKey));
		if (resolutionTime != null) {
			helper.add("resolutionTime", resolutionTime);
		}
		return helper.toString();
	}

	private String readablePayer() {
		return Optional.ofNullable(effectivePayer()).map(EntityId::toAbbrevString).orElse("<N/A>");
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		expiry = in.readLong();
		bodyBytes = in.readByteArray(Integer.MAX_VALUE);
		executed = in.readBoolean();
		deleted = in.readBoolean();
		resolutionTime = readNullable(in, RichInstant::from);
		int numSignatories = in.readInt();
		while (numSignatories-- > 0) {
			witnessValidSignature(in.readByteArray(MAX_NUM_PUBKEY_BYTES));
		}
		// Added in 0.18
		number = in.readInt();

		initFromBodyBytes();
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeLong(expiry);
		out.writeByteArray(bodyBytes);
		out.writeBoolean(executed);
		out.writeBoolean(deleted);
		writeNullable(resolutionTime, out, RichInstant::serialize);
		out.writeInt(signatories.size());
		for (byte[] key : signatories) {
			out.writeByteArray(key);
		}
		out.writeInt(number);
	}

	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getMinimumSupportedVersion() {
		return CURRENT_VERSION;
	}

	@Override
	public int getVersion() {
		return CURRENT_VERSION;
	}

	@Override
	public MerkleSchedule copy() {
		setImmutable(true);
		var fc = new MerkleSchedule();

		/* These fields are all immutable or effectively immutable, we can share them between copies */
		fc.grpcAdminKey = grpcAdminKey;
		fc.adminKey = adminKey;
		fc.memo = memo;
		fc.deleted = deleted;
		fc.executed = executed;
		fc.payer = payer;
		fc.schedulingAccount = schedulingAccount;
		fc.schedulingTXValidStart = schedulingTXValidStart;
		fc.expiry = expiry;
		fc.bodyBytes = bodyBytes;
		fc.scheduledTxn = scheduledTxn;
		fc.ordinaryScheduledTxn = ordinaryScheduledTxn;
		fc.resolutionTime = resolutionTime;
		fc.number = number;

		/* Signatories are mutable */
		for (byte[] signatory : signatories) {
			fc.witnessValidSignature(signatory);
		}

		return fc;
	}

	@Override
	public EntityNum getKey() {
		return new EntityNum(number);
	}

	@Override
	public void setKey(EntityNum phi) {
		number = phi.intValue();
	}

	Optional<String> memo() {
		return Optional.ofNullable(this.memo);
	}

	Optional<JKey> adminKey() {
		return Optional.ofNullable(adminKey);
	}

	void setAdminKey(JKey adminKey) {
		throwIfImmutable("Cannot change this schedule's adminKey if it's immutable.");
		this.adminKey = adminKey;
	}

	void setPayer(EntityId payer) {
		throwIfImmutable("Cannot change this schedule's payer if it's immutable.");
		this.payer = payer;
	}

	@VisibleForTesting
	void setBodyBytes(final byte[] bodyBytes) {
		this.bodyBytes = bodyBytes;
	}

	EntityId payer() {
		return payer;
	}

	EntityId effectivePayer() {
		return hasExplicitPayer() ? payer : schedulingAccount;
	}

	boolean hasExplicitPayer() {
		return payer != null;
	}

	EntityId schedulingAccount() {
		return schedulingAccount;
	}

	RichInstant schedulingTXValidStart() {
		return this.schedulingTXValidStart;
	}

	public List<byte[]> signatories() {
		return signatories;
	}

	void setExpiry(long expiry) {
		throwIfImmutable("Cannot change this schedule's expiry time if it's immutable.");
		this.expiry = expiry;
	}

	public long expiry() {
		return expiry;
	}

	void markDeleted(Instant at) {
		throwIfImmutable("Cannot change this schedule to deleted if it's immutable.");
		resolutionTime = RichInstant.fromJava(at);
		deleted = true;
	}

	void markExecuted(Instant at) {
		throwIfImmutable("Cannot change this schedule to executed if it's immutable.");
		resolutionTime = RichInstant.fromJava(at);
		executed = true;
	}

	public boolean isExecuted() {
		return executed;
	}

	public boolean isDeleted() {
		return deleted;
	}

	Timestamp deletionTime() {
		if (!deleted) {
			throw new IllegalStateException("Schedule not deleted, cannot return deletion time!");
		}
		return resolutionTime.toGrpc();
	}

	Timestamp executionTime() {
		if (!executed) {
			throw new IllegalStateException("Schedule not executed, cannot return execution time!");
		}
		return resolutionTime.toGrpc();
	}

	public RichInstant getResolutionTime() {
		return resolutionTime;
	}

	HederaFunctionality scheduledFunction() {
		try {
			return MiscUtils.functionOf(ordinaryScheduledTxn);
		} catch (UnknownHederaFunctionality ignore) {
			return NONE;
		}
	}


	TransactionBody ordinaryViewOfScheduledTxn() {
		return ordinaryScheduledTxn;
	}

	SchedulableTransactionBody scheduledTxn() {
		return scheduledTxn;
	}

	public byte[] bodyBytes() {
		return bodyBytes;
	}

	private void initFromBodyBytes() {
		try {
			var parentTxn = TransactionBody.parseFrom(bodyBytes);
			var creationOp = parentTxn.getScheduleCreate();

			if (!creationOp.getMemo().isEmpty()) {
				memo = creationOp.getMemo();
			}
			if (creationOp.hasPayerAccountID()) {
				payer = EntityId.fromGrpcAccountId(creationOp.getPayerAccountID());
			}
			if (creationOp.hasAdminKey()) {
				MiscUtils.asUsableFcKey(creationOp.getAdminKey()).ifPresent(this::setAdminKey);
				if (adminKey != null) {
					grpcAdminKey = creationOp.getAdminKey();
				}
			}
			scheduledTxn = parentTxn.getScheduleCreate().getScheduledTransactionBody();
			schedulingAccount = EntityId.fromGrpcAccountId(parentTxn.getTransactionID().getAccountID());
			schedulingTXValidStart = RichInstant.fromGrpc(parentTxn.getTransactionID().getTransactionValidStart());
			ordinaryScheduledTxn = MiscUtils.asOrdinary(scheduledTxn, scheduledTransactionId());
		} catch (InvalidProtocolBufferException e) {
			throw new IllegalArgumentException(String.format(
					"Argument bodyBytes=0x%s was not a TransactionBody!", CommonUtils.hex(bodyBytes)));
		}
	}
}

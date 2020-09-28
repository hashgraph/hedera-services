package com.hedera.services.state.merkle;

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
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.swirlds.common.FCMValue;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializedObjectProvider;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.AbstractMerkleInternal;
import com.swirlds.fcqueue.FCQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static com.hedera.services.legacy.logic.ApplicationConstants.P;

public class MerkleAccount extends AbstractMerkleInternal implements FCMValue, MerkleInternal {
	private static final Logger log = LogManager.getLogger(MerkleAccount.class);

	static final FCQueue<ExpirableTxnRecord> IMMUTABLE_EMPTY_FCQ = new FCQueue<>(ExpirableTxnRecord.LEGACY_PROVIDER);
	static {
		IMMUTABLE_EMPTY_FCQ.copy();
	}

	static final int RELEASE_080_VERSION = 1;
	static final int RELEASE_090_VERSION = 2;
	static final int MERKLE_VERSION = RELEASE_090_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x950bcf7255691908L;

	static DomainSerdes serdes = new DomainSerdes();

	@Deprecated
	public static final Provider LEGACY_PROVIDER = new Provider();

	/* Order of Merkle node children */
	static class ChildIndices {
		static final int STATE = 0;
		static final int RECORDS = 1;
		static final int PAYER_RECORDS = 2;
		static final int NUM_V1_CHILDREN = 3;
		static final int ASSOCIATED_TOKENS = 3;
		static final int NUM_V2_CHILDREN = 4;
	}

	public MerkleAccount(List<MerkleNode> children) {
		super(ChildIndices.NUM_V2_CHILDREN);
		addDeserializedChildren(children, MERKLE_VERSION);
	}

	public MerkleAccount() {
		this(List.of(
				new MerkleAccountState(),
				new FCQueue<>(ExpirableTxnRecord.LEGACY_PROVIDER),
				new FCQueue<>(ExpirableTxnRecord.LEGACY_PROVIDER),
				new MerkleAccountTokens()));
	}

	/* --- MerkleInternal --- */
	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return MERKLE_VERSION;
	}

	@Override
	public int getMinimumChildCount(int version) {
		return version == RELEASE_090_VERSION
				? ChildIndices.NUM_V2_CHILDREN
				: ChildIndices.NUM_V1_CHILDREN;
	}

	/* --- FastCopyable --- */
	@Override
	public boolean isImmutable() {
		return records().isImmutable() || payerRecords().isImmutable();
	}

	@Override
	public MerkleAccount copy() {
		if (isImmutable()) {
			var msg = String.format("Copy called on an immutable MerkleAccount by thread '%s'! " +
							"(Records mutable? %s. Payer records mutable? %s.)",
					Thread.currentThread().getName(),
					records().isImmutable() ? "NO" : "YES",
					payerRecords().isImmutable() ? "NO" : "YES");
			log.warn(msg);
			/* Ensure we get this stack trace in case a caller incorrectly suppresses the exception. */
			Thread.dumpStack();
			throw new IllegalStateException("Tried to make a copy of an immutable MerkleAccount!");
		}

		return new MerkleAccount(List.of(
				state().copy(),
				records().copy(),
				payerRecords().copy(),
				tokens().copy()));
	}

	@Override
	public void delete() {
		records().delete();
		payerRecords().delete();
	}

	@Override
	@Deprecated
	public void copyFrom(SerializableDataInputStream in) {
		throw new UnsupportedOperationException();
	}

	@Override
	@Deprecated
	public void copyFromExtra(SerializableDataInputStream in) {
		throw new UnsupportedOperationException();
	}

	/* ---- Object ---- */
	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o == null || MerkleAccount.class != o.getClass()) {
			return false;
		}
		var that = (MerkleAccount) o;
		return this.state().equals(that.state()) &&
				this.records().equals(that.records()) &&
				this.payerRecords().equals(that.payerRecords()) &&
				this.tokens().equals(that.tokens());
	}

	@Override
	public int hashCode() {
		return Objects.hash(state(), records(), payerRecords(), tokens());
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(MerkleAccount.class)
				.add("state", state())
				.add("# records", records().size())
				.add("# payer records", payerRecords().size())
				.add("tokens", tokens().readableTokenIds())
				.toString();
	}

	/* ----  Merkle children  ---- */
	public MerkleAccountState state() {
		return getChild(ChildIndices.STATE);
	}

	public FCQueue<ExpirableTxnRecord> records() {
		return getChild(ChildIndices.RECORDS);
	}

	public FCQueue<ExpirableTxnRecord> payerRecords() {
		return getChild(ChildIndices.PAYER_RECORDS);
	}

	public void setRecords(FCQueue<ExpirableTxnRecord> records) {
		setChild(ChildIndices.RECORDS, records);
	}

	public void setPayerRecords(FCQueue<ExpirableTxnRecord> payerRecords) {
		setChild(ChildIndices.PAYER_RECORDS, payerRecords);
	}

	public MerkleAccountTokens tokens() {
		return getChild(ChildIndices.ASSOCIATED_TOKENS);
	}

	public void setTokens(MerkleAccountTokens tokens) {
		setChild(ChildIndices.ASSOCIATED_TOKENS, tokens);
	}

	/* ----  Bean  ---- */
	public String getMemo() {
		return state().memo();
	}

	public void setMemo(String memo) {
		state().setMemo(memo);
	}

	public boolean isSmartContract() {
		return state().isSmartContract();
	}

	public void setSmartContract(boolean smartContract) {
		state().setSmartContract(smartContract);
	}

	public long getBalance() {
		return state().balance();
	}

	public void setBalance(long balance) throws NegativeAccountBalanceException {
		if (balance < 0) {
			throw new NegativeAccountBalanceException(String.format("Illegal balance: %d!", balance));
		}
		state().setHbarBalance(balance);
	}

	public long getReceiverThreshold() {
		return state().receiverThreshold();
	}

	public void setReceiverThreshold(long receiverThreshold) {
		state().setReceiverThreshold(receiverThreshold);
	}

	public long getSenderThreshold() {
		return state().senderThreshold();
	}

	public void setSenderThreshold(long senderThreshold) {
		state().setSenderThreshold(senderThreshold);
	}

	public boolean isReceiverSigRequired() {
		return state().isReceiverSigRequired();
	}

	public void setReceiverSigRequired(boolean receiverSigRequired) {
		state().setReceiverSigRequired(receiverSigRequired);
	}

	public JKey getKey() {
		return state().key();
	}

	public void setKey(JKey key) {
		state().setKey(key);
	}

	public EntityId getProxy() {
		return state().proxy();
	}

	public void setProxy(EntityId proxy) {
		state().setProxy(proxy);
	}

	public long getAutoRenewSecs() {
		return state().autoRenewSecs();
	}

	public void setAutoRenewSecs(long autoRenewSecs) {
		state().setAutoRenewSecs(autoRenewSecs);
	}

	public boolean isDeleted() {
		return state().isDeleted();
	}

	public void setDeleted(boolean deleted) {
		state().setDeleted(deleted);
	}

	public long getExpiry() {
		return state().expiry();
	}

	public void setExpiry(long expiry) {
		state().setExpiry(expiry);
	}

	/* --- Helpers --- */

	public long expiryOfEarliestRecord() {
		var records = records();
		return records.isEmpty() ? -1L : records.peek().getExpiry();
	}

	public List<ExpirableTxnRecord> recordList() {
		return new ArrayList<>(records());
	}

	public Iterator<ExpirableTxnRecord> recordIterator() {
		return records().iterator();
	}

	@Deprecated
	public static class Provider implements SerializedObjectProvider {
		@Override
		public FastCopyable deserialize(DataInputStream in) throws IOException {
			in.readLong();
			in.readLong();

			var balance = in.readLong();
			var senderThreshold = in.readLong();
			var receiverThreshold = in.readLong();
			var receiverSigRequired = (in.readByte() == 1);
			var key = serdes.deserializeKey(in);
			EntityId proxy = null;
			if (in.readChar() == P) {
				in.readLong();
				in.readLong();
				proxy = new EntityId(in.readLong(), in.readLong(), in.readLong());
			}
			var autoRenewSecs = in.readLong();
			var deleted = (in.readByte() == 1);
			var expiry = in.readLong();
			var memo = in.readUTF();
			var smartContract = (in.readByte() == 1);
			var state = new MerkleAccountState(
					key,
					expiry, balance, autoRenewSecs, senderThreshold, receiverThreshold,
					memo,
					deleted, smartContract, receiverSigRequired,
					proxy);

			var records = new FCQueue<>(ExpirableTxnRecord.LEGACY_PROVIDER);
			serdes.deserializeIntoRecords(in, records);
			var payerRecords = new FCQueue<>(ExpirableTxnRecord.LEGACY_PROVIDER);

			return new MerkleAccount(List.of(state, records, payerRecords));
		}
	}
}

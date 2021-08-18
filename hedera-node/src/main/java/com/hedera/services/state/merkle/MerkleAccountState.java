package com.hedera.services.state.merkle;

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
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.submerkle.EntityId;
import com.swirlds.common.MutabilityException;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import static com.hedera.services.legacy.core.jproto.JKey.equalUpToDecodability;
import static com.hedera.services.utils.MiscUtils.describe;

public class MerkleAccountState extends AbstractMerkleLeaf {
	private static final int MAX_CONCEIVABLE_MEMO_UTF8_BYTES = 1_024;

	/* This will mask the most significant 16 bits and just give us the least sig 16
	which represent Max auto associations */
	private static final int ALREADY_USED_AUTOMATIC_ASSOCIATIONS_MASK = (1 << 16) - 1;
	/* This will mask the least significant 16 bits and just give us the most sig 16
	which represent already used auto associations */
	private static final int MAX_AUTOMATIC_ASSOCIATIONS_MASK = ALREADY_USED_AUTOMATIC_ASSOCIATIONS_MASK << 16;

	static final int MAX_CONCEIVABLE_TOKEN_BALANCES_SIZE = 4_096;

	static final int RELEASE_090_VERSION = 4;
	static final int RELEASE_0160_VERSION = 5;
	static final int RELEASE_0180_VERSION = 6;
	private static final int MERKLE_VERSION = RELEASE_0180_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x354cfc55834e7f12L;

	static DomainSerdes serdes = new DomainSerdes();

	public static final String DEFAULT_MEMO = "";

	private JKey key;
	private long expiry;
	private long hbarBalance;
	private long autoRenewSecs;
	private String memo = DEFAULT_MEMO;
	private boolean deleted;
	private boolean smartContract;
	private boolean receiverSigRequired;
	private EntityId proxy;
	private long nftsOwned;
	private int autoAssociationMetadata;

	public MerkleAccountState() {
	}

	public MerkleAccountState(
			JKey key,
			long expiry,
			long hbarBalance,
			long autoRenewSecs,
			String memo,
			boolean deleted,
			boolean smartContract,
			boolean receiverSigRequired,
			EntityId proxy,
			int autoAssociationMetadata
	) {
		this.key = key;
		this.expiry = expiry;
		this.hbarBalance = hbarBalance;
		this.autoRenewSecs = autoRenewSecs;
		this.memo = Optional.ofNullable(memo).orElse(DEFAULT_MEMO);
		this.deleted = deleted;
		this.smartContract = smartContract;
		this.receiverSigRequired = receiverSigRequired;
		this.proxy = proxy;
		this.autoAssociationMetadata = autoAssociationMetadata;
	}

	/* --- MerkleLeaf --- */
	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return MERKLE_VERSION;
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		key = serdes.readNullable(in, serdes::deserializeKey);
		expiry = in.readLong();
		hbarBalance = in.readLong();
		autoRenewSecs = in.readLong();
		memo = in.readNormalisedString(MAX_CONCEIVABLE_MEMO_UTF8_BYTES);
		deleted = in.readBoolean();
		smartContract = in.readBoolean();
		receiverSigRequired = in.readBoolean();
		proxy = serdes.readNullableSerializable(in);
		if (version >= RELEASE_0160_VERSION) {
			/* The number of nfts owned is being saved in the state after RELEASE_0160_VERSION */
			nftsOwned = in.readLong();
		}
		if (version >= RELEASE_0180_VERSION) {
			autoAssociationMetadata = in.readInt();
		}
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		serdes.writeNullable(key, out, serdes::serializeKey);
		out.writeLong(expiry);
		out.writeLong(hbarBalance);
		out.writeLong(autoRenewSecs);
		out.writeNormalisedString(memo);
		out.writeBoolean(deleted);
		out.writeBoolean(smartContract);
		out.writeBoolean(receiverSigRequired);
		serdes.writeNullableSerializable(proxy, out);
		out.writeLong(nftsOwned);
		out.writeInt(autoAssociationMetadata);
	}

	/* --- Copyable --- */
	public MerkleAccountState copy() {
		setImmutable(true);
		var copied = new MerkleAccountState(
				key,
				expiry,
				hbarBalance,
				autoRenewSecs,
				memo,
				deleted,
				smartContract,
				receiverSigRequired,
				proxy,
				autoAssociationMetadata);
		copied.setNftsOwned(nftsOwned);
		return copied;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || MerkleAccountState.class != o.getClass()) {
			return false;
		}

		var that = (MerkleAccountState) o;

		return this.expiry == that.expiry &&
				this.hbarBalance == that.hbarBalance &&
				this.autoRenewSecs == that.autoRenewSecs &&
				Objects.equals(this.memo, that.memo) &&
				this.deleted == that.deleted &&
				this.smartContract == that.smartContract &&
				this.receiverSigRequired == that.receiverSigRequired &&
				Objects.equals(this.proxy, that.proxy) &&
				this.nftsOwned == that.nftsOwned &&
				this.autoAssociationMetadata == that.autoAssociationMetadata &&
				equalUpToDecodability(this.key, that.key);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
				key,
				expiry,
				hbarBalance,
				autoRenewSecs,
				memo,
				deleted,
				smartContract,
				receiverSigRequired,
				proxy,
				nftsOwned,
				autoAssociationMetadata);
	}

	/* --- Bean --- */
	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("key", describe(key))
				.add("expiry", expiry)
				.add("balance", hbarBalance)
				.add("autoRenewSecs", autoRenewSecs)
				.add("memo", memo)
				.add("deleted", deleted)
				.add("smartContract", smartContract)
				.add("receiverSigRequired", receiverSigRequired)
				.add("proxy", proxy)
				.add("nftsOwned", nftsOwned)
				.add("autoAssociationMetadata", autoAssociationMetadata)
				.toString();
	}

	public JKey key() {
		return key;
	}

	public long expiry() {
		return expiry;
	}

	public long balance() {
		return hbarBalance;
	}

	public long autoRenewSecs() {
		return autoRenewSecs;
	}

	public String memo() {
		return memo;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public boolean isSmartContract() {
		return smartContract;
	}

	public boolean isReceiverSigRequired() {
		return receiverSigRequired;
	}

	public EntityId proxy() {
		return proxy;
	}

	public long nftsOwned() {
		return nftsOwned;
	}

	public void setKey(JKey key) {
		assertMutable("key");
		this.key = key;
	}

	public void setExpiry(long expiry) {
		assertMutable("expiry");
		this.expiry = expiry;
	}

	public void setHbarBalance(long hbarBalance) {
		assertMutable("hbarBalance");
		this.hbarBalance = hbarBalance;
	}

	public void setAutoRenewSecs(long autoRenewSecs) {
		assertMutable("autoRenewSecs");
		this.autoRenewSecs = autoRenewSecs;
	}

	public void setMemo(String memo) {
		assertMutable("memo");
		this.memo = memo;
	}

	public void setDeleted(boolean deleted) {
		assertMutable("isSmartContract");
		this.deleted = deleted;
	}

	public void setSmartContract(boolean smartContract) {
		assertMutable("isSmartContract");
		this.smartContract = smartContract;
	}

	public void setReceiverSigRequired(boolean receiverSigRequired) {
		assertMutable("isReceiverSigRequired");
		this.receiverSigRequired = receiverSigRequired;
	}

	public void setProxy(EntityId proxy) {
		assertMutable("proxy");
		this.proxy = proxy;
	}

	public void setNftsOwned(long nftsOwned) {
		assertMutable("nftsOwned");
		this.nftsOwned = nftsOwned;
	}

	public int getMaxAutomaticAssociations() {
		return autoAssociationMetadata & ALREADY_USED_AUTOMATIC_ASSOCIATIONS_MASK;
	}

	public int getAlreadyUsedAutomaticAssociations() {
		return (autoAssociationMetadata & MAX_AUTOMATIC_ASSOCIATIONS_MASK) >> 16;
	}

	public void setMaxAutomaticAssociations(int maxAutomaticAssociations) {
		autoAssociationMetadata = (autoAssociationMetadata & MAX_AUTOMATIC_ASSOCIATIONS_MASK) | maxAutomaticAssociations;
	}

	public void
	setAlreadyUsedAutomaticAssociations(int alreadyUsedCount) {
		autoAssociationMetadata = (alreadyUsedCount << 16) | getMaxAutomaticAssociations();
	}

	private void assertMutable(String proximalField) {
		if (isImmutable()) {
			throw new MutabilityException("Cannot set " + proximalField + " on an immutable account state!");
		}
	}
}

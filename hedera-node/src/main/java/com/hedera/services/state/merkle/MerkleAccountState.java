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
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.submerkle.EntityId;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import static com.hedera.services.legacy.core.jproto.JKey.equalUpToDecodability;
import static com.hedera.services.utils.MiscUtils.describe;

public class MerkleAccountState extends AbstractMerkleLeaf {
	private static final Logger log = LogManager.getLogger(MerkleAccountState.class);

	static final int MAX_CONCEIVABLE_MEMO_UTF8_BYTES = 1_024;
	static final int MAX_CONCEIVABLE_TOKEN_BALANCES_SIZE = 4_096;

	static final int RELEASE_070_VERSION = 1;
	static final int RELEASE_080_VERSION = 2;
	static final int RELEASE_090_VERSION = 3;
	static final int MERKLE_VERSION = RELEASE_090_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x354cfc55834e7f12L;

	static DomainSerdes serdes = new DomainSerdes();

	public static final String DEFAULT_MEMO = "";

	private JKey key;
	private long expiry;
	private long hbarBalance;
	private long autoRenewSecs;
	private long senderThreshold;
	private long receiverThreshold;
	private String memo = DEFAULT_MEMO;
	private boolean deleted;
	private boolean smartContract;
	private boolean receiverSigRequired;
	private EntityId proxy;

	public MerkleAccountState() { }

	public MerkleAccountState(
			JKey key,
			long expiry,
			long hbarBalance,
			long autoRenewSecs,
			long senderThreshold,
			long receiverThreshold,
			String memo,
			boolean deleted,
			boolean smartContract,
			boolean receiverSigRequired,
			EntityId proxy
	) {
		this.key = key;
		this.expiry = expiry;
		this.hbarBalance = hbarBalance;
		this.autoRenewSecs = autoRenewSecs;
		this.senderThreshold = senderThreshold;
		this.receiverThreshold = receiverThreshold;
		this.memo = Optional.ofNullable(memo).orElse(DEFAULT_MEMO);
		this.deleted = deleted;
		this.smartContract = smartContract;
		this.receiverSigRequired = receiverSigRequired;
		this.proxy = proxy;
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
		senderThreshold = in.readLong();
		receiverThreshold = in.readLong();
		memo = in.readNormalisedString(MAX_CONCEIVABLE_MEMO_UTF8_BYTES);
		deleted = in.readBoolean();
		smartContract = in.readBoolean();
		receiverSigRequired = in.readBoolean();
		proxy = serdes.readNullableSerializable(in);
		if (version == RELEASE_080_VERSION) {
			long[] unused = in.readLongArray(MAX_CONCEIVABLE_TOKEN_BALANCES_SIZE);
		}
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		serdes.writeNullable(key, out, serdes::serializeKey);
		out.writeLong(expiry);
		out.writeLong(hbarBalance);
		out.writeLong(autoRenewSecs);
		out.writeLong(senderThreshold);
		out.writeLong(receiverThreshold);
		out.writeNormalisedString(memo);
		out.writeBoolean(deleted);
		out.writeBoolean(smartContract);
		out.writeBoolean(receiverSigRequired);
		serdes.writeNullableSerializable(proxy, out);
	}

	/* --- Copyable --- */
	public MerkleAccountState copy() {
		return new MerkleAccountState(
				key,
				expiry,
				hbarBalance,
				autoRenewSecs,
				senderThreshold,
				receiverThreshold,
				memo,
				deleted,
				smartContract,
				receiverSigRequired,
				proxy);
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
				this.senderThreshold == that.senderThreshold &&
				this.receiverThreshold == that.receiverThreshold &&
				Objects.equals(this.memo, that.memo) &&
				this.deleted == that.deleted &&
				this.smartContract == that.smartContract &&
				this.receiverSigRequired == that.receiverSigRequired &&
				Objects.equals(this.proxy, that.proxy) &&
				equalUpToDecodability(this.key, that.key);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
				key,
				expiry,
				hbarBalance,
				autoRenewSecs,
				senderThreshold,
				receiverThreshold,
				memo,
				deleted,
				smartContract,
				receiverSigRequired,
				proxy);
	}

	/* --- Bean --- */
	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("key", describe(key))
				.add("expiry", expiry)
				.add("balance", hbarBalance)
				.add("autoRenewSecs", autoRenewSecs)
				.add("senderThreshold", senderThreshold)
				.add("receiverThreshold", receiverThreshold)
				.add("memo", memo)
				.add("deleted", deleted)
				.add("smartContract", smartContract)
				.add("receiverSigRequired", receiverSigRequired)
				.add("proxy", proxy)
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

	public long senderThreshold() {
		return senderThreshold;
	}

	public long receiverThreshold() {
		return receiverThreshold;
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

	public void setKey(JKey key) {
		this.key = key;
	}

	public void setExpiry(long expiry) {
		this.expiry = expiry;
	}

	public void setHbarBalance(long hbarBalance) {
		this.hbarBalance = hbarBalance;
	}

	public void setAutoRenewSecs(long autoRenewSecs) {
		this.autoRenewSecs = autoRenewSecs;
	}

	public void setSenderThreshold(long senderThreshold) {
		this.senderThreshold = senderThreshold;
	}

	public void setReceiverThreshold(long receiverThreshold) {
		this.receiverThreshold = receiverThreshold;
	}

	public void setMemo(String memo) {
		this.memo = memo;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	public void setSmartContract(boolean smartContract) {
		this.smartContract = smartContract;
	}

	public void setReceiverSigRequired(boolean receiverSigRequired) {
		this.receiverSigRequired = receiverSigRequired;
	}

	public void setProxy(EntityId proxy) {
		this.proxy = proxy;
	}
}

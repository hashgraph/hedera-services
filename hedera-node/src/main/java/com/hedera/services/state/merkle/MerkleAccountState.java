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
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.utility.AbstractMerkleNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import static com.hedera.services.context.properties.StandardizedPropertySources.MAX_MEMO_UTF8_BYTES;
import static com.hedera.services.legacy.core.jproto.JKey.equalUpToDecodability;
import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hedera.services.utils.MiscUtils.describe;

public class MerkleAccountState extends AbstractMerkleNode implements MerkleLeaf {
	private static final Logger log = LogManager.getLogger(MerkleAccountState.class);

	public static final int MAX_NUM_TOKEN_BALANCES = 1_000;
	public static final long[] NO_TOKEN_BALANCES = new long[0];

	static final int RELEASE_070_VERSION = 1;
	static final int RELEASE_080_VERSION = 2;
	static final int MERKLE_VERSION = RELEASE_080_VERSION;
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
	long[] tokenBalances = NO_TOKEN_BALANCES;

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
			EntityId proxy,
			long[] tokenBalances
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
		this.tokenBalances = tokenBalances;
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
		memo = in.readNormalisedString(MAX_MEMO_UTF8_BYTES);
		deleted = in.readBoolean();
		smartContract = in.readBoolean();
		receiverSigRequired = in.readBoolean();
		proxy = serdes.readNullableSerializable(in);
		if (version >= RELEASE_080_VERSION) {
			tokenBalances = in.readLongArray(MAX_NUM_TOKEN_BALANCES * 4);
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
		out.writeLongArray(tokenBalances);
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
				proxy,
				Arrays.copyOf(tokenBalances, tokenBalances.length));
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
				equalUpToDecodability(this.key, that.key) &&
				Arrays.equals(this.tokenBalances, that.tokenBalances);
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
				proxy,
				Arrays.hashCode(tokenBalances));
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
				.add("tokenBalances", Arrays.toString(tokenBalances))
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

	public long getTokenBalance(TokenID token) {
		int i = logicalIndexOf(token);
		if (i < 0) {
			throw new IllegalArgumentException(String.format("Token %s is missing!", readableId(token)));
		}
		return tokenBalances[i * 2 + 1];
	}

	public void setTokenBalance(TokenID token, long balance) {
		if (balance < 0) {
			throw new IllegalArgumentException(String.format(
					"Token %s cannot have balance %d!",
					readableId(token),
					balance));
		}
		int i = logicalIndexOf(token);
		if (i < 0) {
			int newNumTokens = tokenBalances.length / 2 + 1;

			long[] newTokenBalances = new long[newNumTokens * 2];
			i = -i - 1;
			if (i != 0) {
				System.arraycopy(tokenBalances, 0, newTokenBalances, 0, i * 2);
			}

			newTokenBalances[i * 2] = token.getTokenNum();
			newTokenBalances[i * 2 + 1] = balance;

			if (i != newNumTokens) {
				System.arraycopy(
						tokenBalances,
						i * 2,
						newTokenBalances,
						(i + 1) * 2,
						(newNumTokens - i - 1) * 2);
			}
			tokenBalances = newTokenBalances;
		}
		tokenBalances[i * 2 + 1] = balance;
	}

	int logicalIndexOf(TokenID token) {
		int lo = 0, hi = tokenBalances.length / 2 - 1;
		long num = token.getTokenNum();
		while (lo <= hi) {
			int mid = (lo + (hi - lo) / 2), i = mid * 2;
			long midNum = tokenBalances[i];
			if (midNum == num) {
				return mid;
			} else if (midNum < num) {
				lo = mid + 1;
			} else {
				hi = mid - 1;
			}
		}
		return -(lo + 1);
	}
}

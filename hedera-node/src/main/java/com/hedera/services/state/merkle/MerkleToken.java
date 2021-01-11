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
import com.swirlds.common.FCMValue;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.io.SerializedObjectProvider;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import static com.hedera.services.legacy.core.jproto.JKey.equalUpToDecodability;
import static com.hedera.services.utils.MiscUtils.describe;

public class MerkleToken extends AbstractMerkleLeaf implements FCMValue {
	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0xd23ce8814b35fc2fL;
	static DomainSerdes serdes = new DomainSerdes();

	public static final long UNUSED_AUTO_RENEW_PERIOD = -1L;
	public static final JKey UNUSED_KEY = null;
	public static final EntityId UNUSED_AUTO_RENEW_ACCOUNT = null;
	public static final int UPPER_BOUND_SYMBOL_UTF8_BYTES = 1024;
	public static final int UPPER_BOUND_TOKEN_NAME_UTF8_BYTES = 1024;

	@Deprecated
	public static final MerkleToken.Provider LEGACY_PROVIDER = new MerkleToken.Provider();

	private int decimals;
	private long expiry;
	private long totalSupply;
	private long autoRenewPeriod = UNUSED_AUTO_RENEW_PERIOD;
	private JKey adminKey = UNUSED_KEY;
	private JKey kycKey = UNUSED_KEY;
	private JKey wipeKey = UNUSED_KEY;
	private JKey supplyKey = UNUSED_KEY;
	private JKey freezeKey = UNUSED_KEY;
	private String symbol;
	private String name;
	private boolean deleted;
	private boolean accountsFrozenByDefault;
	private boolean accountsKycGrantedByDefault;
	private EntityId treasury;
	private EntityId autoRenewAccount = UNUSED_AUTO_RENEW_ACCOUNT;

	@Deprecated
	public static class Provider implements SerializedObjectProvider {
		@Override
		public FastCopyable deserialize(DataInputStream _in) throws IOException {
			throw new UnsupportedOperationException();
		}
	}

	public MerkleToken() {
	}

	public MerkleToken(
			long expiry,
			long totalSupply,
			int decimals,
			String symbol,
			String name,
			boolean accountsFrozenByDefault,
			boolean accountKycGrantedByDefault,
			EntityId treasury
	) {
		this.expiry = expiry;
		this.totalSupply = totalSupply;
		this.decimals = decimals;
		this.symbol = symbol;
		this.name = name;
		this.accountsFrozenByDefault = accountsFrozenByDefault;
		this.accountsKycGrantedByDefault = accountKycGrantedByDefault;
		this.treasury = treasury;
	}

	/* Object */
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || MerkleToken.class != o.getClass()) {
			return false;
		}

		var that = (MerkleToken) o;
		return this.expiry == that.expiry &&
				this.autoRenewPeriod == that.autoRenewPeriod &&
				this.deleted == that.deleted &&
				this.totalSupply == that.totalSupply &&
				this.decimals == that.decimals &&
				this.accountsFrozenByDefault == that.accountsFrozenByDefault &&
				this.accountsKycGrantedByDefault == that.accountsKycGrantedByDefault &&
				Objects.equals(this.symbol, that.symbol) &&
				Objects.equals(this.name, that.name) &&
				Objects.equals(this.treasury, that.treasury) &&
				Objects.equals(this.autoRenewAccount, that.autoRenewAccount) &&
				equalUpToDecodability(this.wipeKey, that.wipeKey) &&
				equalUpToDecodability(this.supplyKey, that.supplyKey) &&
				equalUpToDecodability(this.adminKey, that.adminKey) &&
				equalUpToDecodability(this.freezeKey, that.freezeKey) &&
				equalUpToDecodability(this.kycKey, that.kycKey);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
				expiry,
				deleted,
				totalSupply,
				decimals,
				adminKey,
				freezeKey,
				kycKey,
				wipeKey,
				supplyKey,
				symbol,
				name,
				accountsFrozenByDefault,
				accountsKycGrantedByDefault,
				treasury,
				autoRenewAccount,
				autoRenewPeriod);
	}

	/* --- Bean --- */
	@Override
	public String toString() {
		return MoreObjects.toStringHelper(MerkleToken.class)
				.add("deleted", deleted)
				.add("expiry", expiry)
				.add("symbol", symbol)
				.add("name", name)
				.add("treasury", treasury.toAbbrevString())
				.add("totalSupply", totalSupply)
				.add("decimals", decimals)
				.add("autoRenewAccount", readableAutoRenewAccount())
				.add("autoRenewPeriod", autoRenewPeriod)
				.add("adminKey", describe(adminKey))
				.add("kycKey", describe(kycKey))
				.add("wipeKey", describe(wipeKey))
				.add("supplyKey", describe(supplyKey))
				.add("freezeKey", describe(freezeKey))
				.add("accountsKycGrantedByDefault", accountsKycGrantedByDefault)
				.add("accountsFrozenByDefault", accountsFrozenByDefault)
				.toString();
	}

	private String readableAutoRenewAccount() {
		return Optional.ofNullable(autoRenewAccount).map(EntityId::toAbbrevString).orElse("<N/A>");
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
		deleted = in.readBoolean();
		expiry = in.readLong();
		autoRenewAccount = serdes.readNullableSerializable(in);
		autoRenewPeriod = in.readLong();
		symbol = in.readNormalisedString(UPPER_BOUND_SYMBOL_UTF8_BYTES);
		name = in.readNormalisedString(UPPER_BOUND_TOKEN_NAME_UTF8_BYTES);
		treasury = in.readSerializable();
		totalSupply = in.readLong();
		decimals = in.readInt();
		accountsFrozenByDefault = in.readBoolean();
		accountsKycGrantedByDefault = in.readBoolean();
		adminKey = serdes.readNullable(in, serdes::deserializeKey);
		freezeKey = serdes.readNullable(in, serdes::deserializeKey);
		kycKey = serdes.readNullable(in, serdes::deserializeKey);
		supplyKey = serdes.readNullable(in, serdes::deserializeKey);
		wipeKey = serdes.readNullable(in, serdes::deserializeKey);
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeBoolean(deleted);
		out.writeLong(expiry);
		serdes.writeNullableSerializable(autoRenewAccount, out);
		out.writeLong(autoRenewPeriod);
		out.writeNormalisedString(symbol);
		out.writeNormalisedString(name);
		out.writeSerializable(treasury, true);
		out.writeLong(totalSupply);
		out.writeInt(decimals);
		out.writeBoolean(accountsFrozenByDefault);
		out.writeBoolean(accountsKycGrantedByDefault);
		serdes.writeNullable(adminKey, out, serdes::serializeKey);
		serdes.writeNullable(freezeKey, out, serdes::serializeKey);
		serdes.writeNullable(kycKey, out, serdes::serializeKey);
		serdes.writeNullable(supplyKey, out, serdes::serializeKey);
		serdes.writeNullable(wipeKey, out, serdes::serializeKey);
	}

	/* --- FastCopyable --- */
	@Override
	public MerkleToken copy() {
		var fc = new MerkleToken(
				expiry,
				totalSupply,
				decimals,
				symbol,
				name,
				accountsFrozenByDefault,
				accountsKycGrantedByDefault,
				treasury);
		fc.setDeleted(deleted);
		fc.setAutoRenewPeriod(autoRenewPeriod);
		fc.setAutoRenewAccount(autoRenewAccount);
		if (adminKey != UNUSED_KEY) {
			fc.setAdminKey(adminKey);
		}
		if (freezeKey != UNUSED_KEY) {
			fc.setFreezeKey(freezeKey);
		}
		if (kycKey != UNUSED_KEY) {
			fc.setKycKey(kycKey);
		}
		if (wipeKey != UNUSED_KEY) {
			fc.setWipeKey(wipeKey);
		}
		if (supplyKey != UNUSED_KEY) {
			fc.setSupplyKey(supplyKey);
		}
		return fc;
	}

	/* --- Bean --- */
	public long totalSupply() {
		return totalSupply;
	}

	public int decimals() {
		return decimals;
	}

	public boolean hasAdminKey() {
		return adminKey != UNUSED_KEY;
	}

	public Optional<JKey> adminKey() {
		return Optional.ofNullable(adminKey);
	}

	public Optional<JKey> freezeKey() {
		return Optional.ofNullable(freezeKey);
	}

	public boolean hasFreezeKey() {
		return freezeKey != UNUSED_KEY;
	}

	public Optional<JKey> kycKey() {
		return Optional.ofNullable(kycKey);
	}

	public boolean hasKycKey() {
		return kycKey != UNUSED_KEY;
	}

	public void setFreezeKey(JKey freezeKey) {
		this.freezeKey = freezeKey;
	}

	public void setKycKey(JKey kycKey) {
		this.kycKey = kycKey;
	}

	public Optional<JKey> supplyKey() {
		return Optional.ofNullable(supplyKey);
	}

	public boolean hasSupplyKey() {
		return supplyKey != UNUSED_KEY;
	}

	public void setSupplyKey(JKey supplyKey) {
		this.supplyKey = supplyKey;
	}

	public Optional<JKey> wipeKey() {
		return Optional.ofNullable(wipeKey);
	}

	public boolean hasWipeKey() {
		return wipeKey != UNUSED_KEY;
	}

	public void setWipeKey(JKey wipeKey) {
		this.wipeKey = wipeKey;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	public String symbol() {
		return symbol;
	}

	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}

	public String name() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setTreasury(EntityId treasury) {
		this.treasury = treasury;
	}

	public void setAdminKey(JKey adminKey) {
		this.adminKey = adminKey;
	}

	public boolean accountsAreFrozenByDefault() {
		return accountsFrozenByDefault;
	}

	public boolean accountsKycGrantedByDefault() {
		return accountsKycGrantedByDefault;
	}

	public EntityId treasury() {
		return treasury;
	}

	public long expiry() {
		return expiry;
	}

	public void setExpiry(long expiry) {
		this.expiry = expiry;
	}

	public long autoRenewPeriod() {
		return autoRenewPeriod;
	}

	public void setAutoRenewPeriod(long autoRenewPeriod) {
		this.autoRenewPeriod = autoRenewPeriod;
	}

	public EntityId autoRenewAccount() {
		return autoRenewAccount;
	}

	public boolean hasAutoRenewAccount() {
		return autoRenewAccount != UNUSED_AUTO_RENEW_ACCOUNT;
	}

	public void setAutoRenewAccount(EntityId autoRenewAccount) {
		this.autoRenewAccount = autoRenewAccount;
	}

	public void adjustTotalSupplyBy(long amount) {
		var newTotalSupply = totalSupply + amount;
		if (newTotalSupply < 0) {
			throw new IllegalArgumentException(String.format("Cannot set token totalSupply to %d!", newTotalSupply));
		}
		totalSupply += amount;
	}

	void setAccountsFrozenByDefault(boolean accountsFrozenByDefault) {
		this.accountsFrozenByDefault = accountsFrozenByDefault;
	}

	void setAccountsKycGrantedByDefault(boolean accountKycGrantedByDefault) {
		this.accountsKycGrantedByDefault = accountKycGrantedByDefault;
	}
}

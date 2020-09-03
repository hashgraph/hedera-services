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
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.utility.AbstractMerkleNode;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import static com.hedera.services.legacy.core.jproto.JKey.equalUpToDecodability;
import static com.hedera.services.utils.MiscUtils.describe;

public class MerkleToken extends AbstractMerkleNode implements FCMValue, MerkleLeaf  {
	static final int MAX_CONCEIVABLE_SYMBOL_LENGTH = 256;
	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0xd23ce8814b35fc2fL;
	static DomainSerdes serdes = new DomainSerdes();

	public static final JKey UNUSED_KEY = null;

	@Deprecated
	public static final MerkleToken.Provider LEGACY_PROVIDER = new MerkleToken.Provider();

	private int divisibility;
	private long tokenFloat;
	private JKey adminKey;
	private JKey freezeKey = UNUSED_KEY;
	private String symbol;
	private boolean accountsFrozenByDefault;
	private EntityId treasury;

	@Deprecated
	public static class Provider implements SerializedObjectProvider {
		@Override
		public FastCopyable deserialize(DataInputStream _in) throws IOException {
			throw new UnsupportedOperationException();
		}
	}

	public MerkleToken() { }

	public MerkleToken(
			long tokenFloat,
			int divisibility,
			JKey adminKey,
			String symbol,
			boolean accountsFrozenByDefault,
			EntityId treasury
	) {
		this.tokenFloat = tokenFloat;
		this.divisibility = divisibility;
		this.adminKey = adminKey;
		this.symbol = symbol;
		this.accountsFrozenByDefault = accountsFrozenByDefault;
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

		var that = (MerkleToken)o;
		return this.tokenFloat == that.tokenFloat &&
				this.divisibility == that.divisibility &&
				this.accountsFrozenByDefault == that.accountsFrozenByDefault &&
				Objects.equals(this.symbol, that.symbol) &&
				Objects.equals(this.treasury, that.treasury) &&
				equalUpToDecodability(this.adminKey, that.adminKey) &&
				equalUpToDecodability(this.freezeKey, that.freezeKey);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
				tokenFloat,
				divisibility,
				adminKey,
				freezeKey,
				symbol,
				accountsFrozenByDefault,
				treasury);
	}

	/* --- Bean --- */
	@Override
	public String toString() {
		return MoreObjects.toStringHelper(MerkleToken.class)
				.add("symbol", symbol)
				.add("treasury", treasury.toAbbrevString())
				.add("float", tokenFloat)
				.add("divisibility", divisibility)
				.add("adminKey", describe(adminKey))
				.add("freezeKey", describe(freezeKey))
				.add("accountsFrozenByDefault", accountsFrozenByDefault)
				.toString();
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
		adminKey = serdes.deserializeKey(in);
		symbol = in.readNormalisedString(MAX_CONCEIVABLE_SYMBOL_LENGTH);
		treasury = in.readSerializable();
		tokenFloat = in.readLong();
		divisibility = in.readInt();
		accountsFrozenByDefault = in.readBoolean();
		freezeKey = serdes.readNullable(in, serdes::deserializeKey);
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		serdes.serializeKey(adminKey, out);
		out.writeNormalisedString(symbol);
		out.writeSerializable(treasury, true);
		out.writeLong(tokenFloat);
		out.writeInt(divisibility);
		out.writeBoolean(accountsFrozenByDefault);
		serdes.writeNullable(freezeKey, out, serdes::serializeKey);
	}

	/* --- FastCopyable --- */
	@Override
	public MerkleToken copy() {
		var fc = new MerkleToken(
			tokenFloat,
			divisibility,
			adminKey,
			symbol,
			accountsFrozenByDefault,
			treasury);
		if (freezeKey != UNUSED_KEY) {
			fc.setFreezeKey(freezeKey);
		}
		return fc;
	}

	@Override
	public void delete() {
	}

	/* --- Bean --- */
	public long tokenFloat() {
		return tokenFloat;
	}

	public int divisibility() {
		return divisibility;
	}

	public JKey adminKey() {
		return adminKey;
	}

	public Optional<JKey> freezeKey() {
		return Optional.ofNullable(freezeKey);
	}

	public void setFreezeKey(JKey freezeKey) {
		this.freezeKey = freezeKey;
	}

	public String symbol() {
		return symbol;
	}

	public boolean accountsAreFrozenByDefault() {
		return accountsFrozenByDefault;
	}

	public EntityId treasury() {
		return treasury;
	}
}

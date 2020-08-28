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
import com.hedera.services.state.serdes.TopicSerde;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.common.FCMElement;
import com.swirlds.common.FCMValue;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.io.SerializedObjectProvider;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.utility.AbstractMerkleNode;
import com.swirlds.fcmap.internal.FCMLeaf;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongycastle.util.encoders.Hex;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

import static com.hedera.services.legacy.core.jproto.JKey.equalUpToDecodability;
import static com.hedera.services.utils.EntityIdUtils.asAccount;
import static com.hedera.services.utils.EntityIdUtils.asLiteralString;
import static com.hedera.services.utils.MiscUtils.describe;

public class MerkleToken extends AbstractMerkleNode implements FCMValue, MerkleLeaf  {
	static final JKey UNUSED_KEY = null;

	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0xd23ce8814b35fc2fL;

	static DomainSerdes serdes = new DomainSerdes();

	private long tokenFloat;
	private long divisibility;
	private JKey adminKey;
	private JKey freezeKey = UNUSED_KEY;
	private String symbol;
	private boolean accountsFrozenByDefault;
	private EntityId treasury;

	public MerkleToken() {}

	public MerkleToken(
			long tokenFloat,
			long divisibility,
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
		throw new AssertionError("Not implemented");
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		throw new AssertionError("Not implemented");
	}

	/* --- FastCopyable --- */
	@Override
	public MerkleToken copy() {
		throw new AssertionError("Not implemented");
	}

	@Override
	public boolean isImmutable() {
		throw new AssertionError("Not implemented");
	}

	@Override
	public void delete() {
		throw new AssertionError("Not implemented");
	}

	/* --- Bean --- */

	public long getTokenFloat() {
		return tokenFloat;
	}

	public long getDivisibility() {
		return divisibility;
	}

	public JKey getAdminKey() {
		return adminKey;
	}

	public JKey getFreezeKey() {
		return freezeKey;
	}

	public void setFreezeKey(JKey freezeKey) {
		this.freezeKey = freezeKey;
	}

	public String getSymbol() {
		return symbol;
	}

	public boolean areAccountsFrozenByDefault() {
		return accountsFrozenByDefault;
	}

	public EntityId getTreasury() {
		return treasury;
	}
}

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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.common.FCMKey;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.io.SerializedObjectProvider;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.utility.AbstractMerkleNode;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.Map;

public class MerkleEntityAssociation extends AbstractMerkleNode implements FCMKey, MerkleLeaf {
	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0xce8d38caab2e51dcL;

	private long fromShard, fromRealm, fromNum;
	private long toShard, toRealm, toNum;

	@Deprecated
	public static final MerkleEntityAssociation.Provider LEGACY_PROVIDER = new MerkleEntityAssociation.Provider();

	public MerkleEntityAssociation() {
	}

	public MerkleEntityAssociation(
			long fromShard, long fromRealm, long fromNum,
			long toShard, long toRealm, long toNum
	) {
		this.fromShard = fromShard;
		this.fromRealm = fromRealm;
		this.fromNum = fromNum;
		this.toShard = toShard;
		this.toRealm = toRealm;
		this.toNum = toNum;
	}

	public static MerkleEntityAssociation fromAccountTokenRel(AccountID account, TokenID token) {
		return new MerkleEntityAssociation(
				account.getShardNum(), account.getRealmNum(), account.getAccountNum(),
				token.getShardNum(), token.getRealmNum(), token.getTokenNum());
	}

	public Map.Entry<AccountID, TokenID> asAccountTokenRel() {
		return new AbstractMap.SimpleImmutableEntry<>(
				AccountID.newBuilder()
						.setShardNum(fromShard)
						.setRealmNum(fromRealm)
						.setAccountNum(fromNum)
						.build(),
				TokenID.newBuilder()
						.setShardNum(toShard)
						.setRealmNum(toRealm)
						.setTokenNum(toNum)
						.build());
	}

	@Deprecated
	public static class Provider implements SerializedObjectProvider {
		@Override
		public FastCopyable deserialize(DataInputStream in) throws IOException {
			throw new UnsupportedOperationException();
		}
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
		fromShard = in.readLong();
		fromRealm = in.readLong();
		fromNum = in.readLong();
		toShard = in.readLong();
		toRealm = in.readLong();
		toNum = in.readLong();
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeLong(fromShard);
		out.writeLong(fromRealm);
		out.writeLong(fromNum);
		out.writeLong(toShard);
		out.writeLong(toRealm);
		out.writeLong(toNum);
	}

	/* --- Object --- */
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || MerkleEntityAssociation.class != o.getClass()) {
			return false;
		}

		var that = (MerkleEntityAssociation) o;
		return new EqualsBuilder()
				.append(fromShard, that.fromShard).append(fromRealm, that.fromRealm).append(fromNum, that.fromNum)
				.append(toShard, that.toShard).append(toRealm, that.toRealm).append(toNum, that.toNum)
				.isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37)
				.append(fromShard).append(fromRealm).append(fromNum)
				.append(toShard).append(toRealm).append(toNum)
				.toHashCode();
	}

	/* --- FastCopyable --- */
	@Override
	public MerkleEntityAssociation copy() {
		return new MerkleEntityAssociation(fromShard, fromRealm, fromNum, toShard, toRealm, toNum);
	}

	@Override
	public void delete() {
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

	/* --- Bean --- */

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("fromShard", fromShard).add("fromRealm", fromRealm).add("fromNum", fromNum)
				.add("toShard", toShard).add("toRealm", toRealm).add("toNum", toNum)
				.toString();
	}

	public String toAbbrevString() {
		return String.format(
				"%d.%d.%d <-> %d.%d.%d",
				fromShard, fromRealm, fromNum,
				toShard, toRealm, toNum);
	}
}

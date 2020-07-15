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
import java.util.Objects;

public class MerkleEntityId extends AbstractMerkleNode implements FCMKey, MerkleLeaf {
	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0xd5dd2ebaa0bde03L;

	private long shard;
	private long realm;
	private long num;

	public MerkleEntityId() { }

	public MerkleEntityId(long shard, long realm, long num) {
		this.shard = shard;
		this.realm = realm;
		this.num = num;
	}

	public static MerkleEntityId fromPojoAccountId(AccountID pojo) {
		return new MerkleEntityId(pojo.getShardNum(), pojo.getRealmNum(), pojo.getAccountNum());
	}

	public static MerkleEntityId fromPojoTopicId(TopicID pojo) {
		return new MerkleEntityId(pojo.getShardNum(), pojo.getRealmNum(), pojo.getTopicNum());
	}

	public static MerkleEntityId fromPojoContractId(ContractID pojo) {
		return new MerkleEntityId(pojo.getShardNum(), pojo.getRealmNum(), pojo.getContractNum());
	}

	@Deprecated
	public static class Provider implements SerializedObjectProvider {
		@Override
		public FastCopyable deserialize(DataInputStream in) throws IOException {
			var id = new MerkleEntityId();

			in.readLong();
			in.readLong();

			id.realm = in.readLong();
			id.shard = in.readLong();
			id.num = in.readLong();

			return id;
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
		shard = in.readLong();
		realm = in.readLong();
		num = in.readLong();
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeLong(shard);
		out.writeLong(realm);
		out.writeLong(num);
	}

	/* --- Object --- */
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || MerkleEntityId.class != o.getClass()) {
			return false;
		}

		var that = (MerkleEntityId)o;
		return new EqualsBuilder()
				.append(shard, that.shard)
				.append(realm, that.realm)
				.append(num, that.num)
				.isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37)
				.append(shard)
				.append(realm)
				.append(num)
				.toHashCode();
	}

	/* --- FastCopyable --- */
	@Override
	public MerkleEntityId copy() {
		return new MerkleEntityId(shard, realm, num);
	}

	@Override
	public void delete() { }

	@Override
	@Deprecated
	public void copyTo(SerializableDataOutputStream out) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void copyToExtra(SerializableDataOutputStream out) {
		throw new UnsupportedOperationException();
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

	@Override
	public void diffCopyTo(SerializableDataOutputStream out, SerializableDataInputStream in) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void diffCopyFrom(SerializableDataOutputStream out, SerializableDataInputStream in) {
		throw new UnsupportedOperationException();
	}

	/* --- Bean --- */
	public long getShard() {
		return shard;
	}

	public void setShard(final long shard) {
		this.shard = shard;
	}

	public long getRealm() {
		return realm;
	}

	public void setRealm(final long realm) {
		this.realm = realm;
	}

	public long getNum() {
		return num;
	}

	public void setNum(final long num) {
		this.num = num;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("shard", shard)
				.add("realm", realm)
				.add("entity", num)
				.toString();
	}

	public String toAbbrevString() {
		return String.format("%d.%d.%d", shard, realm, num);
	}
}

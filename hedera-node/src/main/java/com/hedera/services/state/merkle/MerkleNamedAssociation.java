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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftID;
import com.swirlds.common.FCMKey;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.tuple.Triple;

import java.io.IOException;

public class MerkleNamedAssociation extends AbstractMerkleLeaf implements FCMKey {
	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x42006afb67c53736L;

	private String name;
	private long fromShard, fromRealm, fromNum;
	private long toShard, toRealm, toNum;

	public MerkleNamedAssociation() {
	}

	public MerkleNamedAssociation(
			long fromShard, long fromRealm, long fromNum,
			long toShard, long toRealm, long toNum,
			String name
	) {
		this.fromShard = fromShard;
		this.fromRealm = fromRealm;
		this.fromNum = fromNum;
		this.toShard = toShard;
		this.toRealm = toRealm;
		this.toNum = toNum;
		this.name = name;
	}

	public static MerkleNamedAssociation fromAccountNftSerialNoOwnership(Triple<AccountID, NftID, String> ownership) {
		return fromAccountNftSerialNoOwnership(ownership.getLeft(), ownership.getMiddle(), ownership.getRight());
	}

	public static MerkleNamedAssociation fromAccountNftSerialNoOwnership(
			AccountID account,
			NftID nft,
			String serialNo
	) {
		return new MerkleNamedAssociation(
				account.getShardNum(), account.getRealmNum(), account.getAccountNum(),
				nft.getShardNum(), nft.getRealmNum(), nft.getNftNum(),
				serialNo);
	}

	public Triple<AccountID, NftID, String> asAccountNftSerialNoOwnership() {
		return Triple.of(
				AccountID.newBuilder()
						.setShardNum(fromShard)
						.setRealmNum(fromRealm)
						.setAccountNum(fromNum)
						.build(),
				NftID.newBuilder()
						.setShardNum(toShard)
						.setRealmNum(toRealm)
						.setNftNum(toNum)
						.build(),
				name);
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
		if (o == null || MerkleNamedAssociation.class != o.getClass()) {
			return false;
		}

		var that = (MerkleNamedAssociation) o;
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
	public MerkleNamedAssociation copy() {
		return new MerkleNamedAssociation(fromShard, fromRealm, fromNum, toShard, toRealm, toNum);
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

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
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.submerkle.EntityId;
import com.swirlds.common.FCMValue;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;

import java.io.IOException;
import java.util.Objects;

public class MerkleNftType extends AbstractMerkleLeaf implements FCMValue {
	static final int RELEASE_0140_VERSION = 1;

	static final int MERKLE_VERSION = RELEASE_0140_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0xcd30ebff06f4861aL;

	static DomainSerdes serdes = new DomainSerdes();

	private int serialNoCount;
	private EntityId treasury;

	public MerkleNftType() {
		/* No-op. */
	}

	public MerkleNftType(
			int serialNoCount,
			EntityId treasury
	) {
		this.treasury = treasury;
		this.serialNoCount = serialNoCount;
	}

	/* Object */
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || MerkleNftType.class != o.getClass()) {
			return false;
		}

		var that = (MerkleNftType) o;
		return this.serialNoCount == that.serialNoCount &&
				Objects.equals(this.treasury, that.treasury);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
				serialNoCount,
				treasury);
	}

	/* --- Bean --- */
	@Override
	public String toString() {
		return MoreObjects.toStringHelper(MerkleNftType.class)
				.add("serialNoCount", serialNoCount)
				.add("treasury", treasury.toAbbrevString())
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
		serialNoCount = in.readInt();
		treasury = in.readSerializable();
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeInt(serialNoCount);
		out.writeSerializable(treasury, true);
	}

	/* --- FastCopyable --- */
	@Override
	public MerkleNftType copy() {
		var fc = new MerkleNftType(
				serialNoCount,
				treasury);
		return fc;
	}

	/* --- Bean --- */

	public int getSerialNoCount() {
		return serialNoCount;
	}

	public EntityId getTreasury() {
		return treasury;
	}
}

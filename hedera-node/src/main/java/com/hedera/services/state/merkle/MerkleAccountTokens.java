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
import com.hedera.services.state.merkle.internals.CopyOnWriteIds;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class MerkleAccountTokens extends AbstractMerkleLeaf {
	static final int MAX_CONCEIVABLE_TOKEN_ID_PARTS = Integer.MAX_VALUE;

	static final int NUM_ID_PARTS = 3;

	private static final int RELEASE_090_VERSION = 1;

	static final int MERKLE_VERSION = RELEASE_090_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x4dd9cde14aae5f8eL;

	private CopyOnWriteIds ids;

	public MerkleAccountTokens() {
		ids = new CopyOnWriteIds();
	}

	public MerkleAccountTokens(CopyOnWriteIds ids) {
		this.ids = ids;
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
		ids.setNativeIds(in.readLongArray(MAX_CONCEIVABLE_TOKEN_ID_PARTS));
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeLongArray(ids.getNativeIds());
	}

	/* --- Copyable --- */
	public MerkleAccountTokens copy() {
		setImmutable(true);
		return new MerkleAccountTokens(ids.copy());
	}

	public MerkleAccountTokens tmpNonMerkleCopy() {
		return new MerkleAccountTokens(ids.copy());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || MerkleAccountTokens.class != o.getClass()) {
			return false;
		}

		var that = (MerkleAccountTokens) o;

		return Objects.equals(this.ids, that.ids);
	}

	@Override
	public int hashCode() {
		return ids.hashCode();
	}

	/* --- Bean --- */
	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("tokens", ids.toReadableIdList())
				.toString();
	}

	String readableTokenIds() {
		return ids.toReadableIdList();
	}

	public List<TokenID> asTokenIds() {
		return ids.getAsIds();
	}

	public CopyOnWriteIds getIds() {
		return ids;
	}

	long[] getRawIds() {
		return ids.getNativeIds();
	}

	/* --- Association Manipulation --- */
	public int numAssociations() {
		return ids.size();
	}

	public boolean includes(TokenID id) {
		return ids.contains(id);
	}

	public void associateAll(Set<TokenID> tokenIds) {
		throwIfImmutable();
		ids.addAll(tokenIds);
	}

	public void shareTokensOf(MerkleAccountTokens other) {
		throwIfImmutable();
		ids = other.getIds();
	}

	public void updateAssociationsFrom(CopyOnWriteIds newIds) {
		throwIfImmutable();
		ids.setNativeIds(newIds.getNativeIds());
	}

	public void associate(Set<Id> modelIds) {
		throwIfImmutable();
		ids.addAllIds(modelIds);
	}

	public void dissociate(Set<Id> modelIds) {
		throwIfImmutable();
		ids.removeAllIds(modelIds);
	}
}

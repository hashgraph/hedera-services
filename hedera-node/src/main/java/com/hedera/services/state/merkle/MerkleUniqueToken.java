package com.hedera.services.state.merkle;

/*
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.common.base.MoreObjects;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;

import java.io.IOException;
import java.util.Objects;

/**
 * Represents an uniqueToken entity. Part of the nft implementation.
 */
public class MerkleUniqueToken extends AbstractMerkleLeaf {

	public static final int UPPER_BOUND_METADATA_BYTES = 1024;
	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x899641dafcc39164L;

	private EntityId owner;
	private RichInstant creationTime;
	private byte[] metadata;

	/**
	 * @param owner        The entity which owns the unique token.
	 * @param metadata     Metadata about the token.
	 * @param creationTime The consensus time at which the token was created.
	 */
	public MerkleUniqueToken(
			EntityId owner,
			byte[] metadata,
			RichInstant creationTime
	) {
		this.owner = owner;
		this.metadata = metadata;
		this.creationTime = creationTime;
	}

	public MerkleUniqueToken() {
		/* No-op. */
	}

	/* Object */
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || MerkleUniqueToken.class != o.getClass()) {
			return false;
		}

		var that = (MerkleUniqueToken) o;
		return this.owner.equals(that.owner) &&
				Objects.deepEquals(this.metadata, that.metadata) &&
				Objects.equals(creationTime, that.creationTime);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
				owner,
				creationTime,
				metadata);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(MerkleUniqueToken.class)
				.add("owner", owner)
				.add("creationTime", creationTime)
				.add("metadata", metadata)
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
	public void deserialize(SerializableDataInputStream in, int i) throws IOException {
		owner = in.readSerializable();
		creationTime = RichInstant.from(in);
		metadata = in.readByteArray(UPPER_BOUND_METADATA_BYTES);
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeSerializable(owner, true);
		creationTime.serialize(out);
		out.writeByteArray(metadata);
	}

	/* --- FastCopyable --- */
	@Override
	public MerkleUniqueToken copy() {
		return new MerkleUniqueToken(owner, metadata, creationTime);
	}

	public void setOwner(EntityId owner) {
		this.owner = owner;
	}

	public EntityId getOwner() {
		return owner;
	}

	public byte[] getMetadata() {
		return metadata;
	}

	public RichInstant getCreationTime() {
		return creationTime;
	}

}
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
import com.hedera.services.state.blob.FileBlobStorage;
import com.swirlds.common.FCMValue;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleExternalLeaf;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;

public class MerkleOptionalBlob extends AbstractMerkleLeaf implements FCMValue, MerkleExternalLeaf {

	// Depends on Migration
//	static final int PRE_RELEASE_0140_VERSION = 1;
	static final int RELEASE_0140_VERSION = 2;
	static final int MERKLE_VERSION = RELEASE_0140_VERSION;

	static final long RUNTIME_CONSTRUCTABLE_ID = 0x4cefb15eb131d9e3L;

	static final Hash MISSING_HASH = new Hash(new byte[] {
			(byte)0x00, (byte)0x01, (byte)0x02, (byte)0x03,
			(byte)0x04, (byte)0x05, (byte)0x06, (byte)0x07,
			(byte)0x08, (byte)0x09, (byte)0x0a, (byte)0x0b,
			(byte)0x0c, (byte)0x0d, (byte)0x0e, (byte)0x0f,
			(byte)0x10, (byte)0x11, (byte)0x12, (byte)0x13,
			(byte)0x14, (byte)0x15, (byte)0x16, (byte)0x17,
			(byte)0x18, (byte)0x19, (byte)0x1a, (byte)0x1b,
			(byte)0x1c, (byte)0x1d, (byte)0x1e, (byte)0x1f,
			(byte)0x20, (byte)0x21, (byte)0x22, (byte)0x23,
			(byte)0x24, (byte)0x25, (byte)0x26, (byte)0x27,
			(byte)0x28, (byte)0x29, (byte)0x2a, (byte)0x2b,
			(byte)0x2c, (byte)0x2d, (byte)0x2e, (byte)0x2f,
	});
	static final byte[] NO_DATA = new byte[0];
	static final Hash NO_HASH = null;

	static Supplier<FileBlobStorage> fileBlobSupplier = FileBlobStorage::getInstance;

	private long id;
	private Hash blobHash;

	public MerkleOptionalBlob() {
		blobHash = NO_HASH;
	}

	public MerkleOptionalBlob(byte[] data) {
		// TODO make it parallel
		id = fileBlobSupplier.get().put(data);
		blobHash = hashOf(data);
	}

	public MerkleOptionalBlob(long otherId, Hash otherFileHash) {
		id = otherId;
		blobHash = otherFileHash;
	}

	public void modify(byte[] newContents) {
		fileBlobSupplier.get().modify(id, newContents);
		blobHash = hashOf(newContents);
	}

	/* --- MerkleExternalLeaf --- */
	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return MERKLE_VERSION;
	}

	@Override
	public Hash getHash() {
		return (blobHash == NO_HASH) ? MISSING_HASH : blobHash;
	}

	@Override
	public void setHash(Hash hash) {
		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 *
	 * Intentionally a no-op method
	 */
	@Override
	public void invalidateHash() {
	}

	// Depends on migration process!
	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		if (blobHash == NO_HASH) {
			out.writeBoolean(false);
		} else {
			out.writeBoolean(true);
			byte[] contents = fileBlobSupplier.get().get(id);
			out.writeInt(contents.length);
			out.write(contents);
		}
	}

	// Depends on migration process!
	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		var hasData = in.readBoolean();
		if (hasData) {
			id = in.readLong();
			int contentLength = in.readInt();
			byte[] content = new byte[contentLength];
			in.readFully(content);

			id = fileBlobSupplier.get().put(content);
			blobHash = hashOf(content);
		}
	}

	// TODO
	@Override
	public void serializeAbbreviated(SerializableDataOutputStream out) { 

	}

	// TODO
	@Override
	public void deserializeAbbreviated(
			SerializableDataInputStream in,
			Hash hash,
			int version
	) {
		if (!MISSING_HASH.equals(hash)) {
			blobHash = hash;
		} else {
			blobHash = NO_HASH;
		}
	}

	/* --- FastCopyable --- */
	@Override
	public MerkleOptionalBlob copy() {
		return new MerkleOptionalBlob(id, blobHash.copy());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || MerkleOptionalBlob.class != o.getClass()) {
			return false;
		}

		var that = (MerkleOptionalBlob)o;
		return this.id == that.id &&
				Objects.equals(this.blobHash, that.blobHash);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, blobHash);
	}

	/* --- Bean --- */
	public byte[] getData() {
		return (blobHash == NO_HASH) ? NO_DATA : fileBlobSupplier.get().get(id);
	}

	public Hash getBlobHash() {
		return blobHash;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(MerkleOptionalBlob.class)
				.add("id", id)
				.add("blobHash", blobHash)
				.toString();
	}

	@Override
	public void onRelease() {
		if (blobHash != NO_HASH) {
			fileBlobSupplier.get().delete(id);
		}
	}

	private Hash hashOf(byte[] content) {
		return new Hash(CryptoFactory.getInstance().digestSync(content));
	}
}

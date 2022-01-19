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

import com.hedera.services.state.merkle.internals.BlobKey;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import com.swirlds.common.merkle.utility.Keyed;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public class MerkleBlob extends AbstractMerkleLeaf implements Keyed<BlobKey> {
	static final BlobKey.BlobType[] CHOICES = BlobKey.BlobType.values();
	static final long CLASS_ID = 0x20920cf4ffd2c64cL;
	static final byte[] NO_DATA = new byte[0];
	static final int MERKLE_VERSION = 1;

	private byte[] data;
	private BlobKey blobKey;

	public MerkleBlob() {
		/* RuntimeConstructable */
	}

	public MerkleBlob(byte[] data) {
		this.data = data;
	}

	private MerkleBlob(final MerkleBlob that) {
		this.blobKey = that.blobKey;
		this.data = that.data;
	}

	@Override
	public MerkleBlob copy() {
		return new MerkleBlob(this);
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		blobKey = new BlobKey(CHOICES[in.readInt()], in.readLong());
		data = in.readByteArray(Integer.MAX_VALUE);
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeInt(blobKey.type().ordinal());
		out.writeLong(blobKey.entityNum());
		out.writeByteArray(data);
	}

	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	@Override
	public int getVersion() {
		return 1;
	}

	@Override
	public BlobKey getKey() {
		return blobKey;
	}

	@Override
	public void setKey(BlobKey blobKey) {
		this.blobKey = blobKey;
	}

	public byte[] getData() {
		return data;
	}

	public void setData(byte[] data) {
		this.data = data;
	}

	@Override
	public String toString() {
		return "MerkleBlob{" +
				"data=" + Arrays.toString(data) +
				", blobKey=" + blobKey +
				'}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		MerkleBlob that = (MerkleBlob) o;
		return Arrays.equals(data, that.data) && Objects.equals(blobKey, that.blobKey);
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(blobKey);
		result = 31 * result + Arrays.hashCode(data);
		return result;
	}
}

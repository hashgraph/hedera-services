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

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;

import com.google.common.base.MoreObjects;
import com.swirlds.common.FCMValue;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.blob.BinaryObject;
import com.swirlds.blob.BinaryObjectStore;
import com.swirlds.common.io.SerializedObjectProvider;
import com.swirlds.common.merkle.MerkleExternalLeaf;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;

public class MerkleOptionalBlob extends AbstractMerkleLeaf implements FCMValue, MerkleExternalLeaf {
	static final int MERKLE_VERSION = (int)BinaryObject.ClassVersion.ORIGINAL;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x4cefb15eb131d9e3L;
	static final Hash MISSING_DELEGATE_HASH = new Hash(new byte[] {
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
	static final BinaryObject MISSING_DELEGATE = null;

	static Supplier<BinaryObject> blobSupplier = BinaryObject::new;
	static Supplier<BinaryObjectStore> blobStoreSupplier = BinaryObjectStore::getInstance;

	private BinaryObject delegate;

	public MerkleOptionalBlob() {
		delegate = MISSING_DELEGATE;
	}

	public MerkleOptionalBlob(byte[] data) {
		delegate = blobStoreSupplier.get().put(data);
	}

	public MerkleOptionalBlob(BinaryObject delegate) {
		this.delegate = delegate;
	}

	public void modify(byte[] newContents) {
		var newDelegate = blobStoreSupplier.get().put(newContents);
		if (delegate != MISSING_DELEGATE) {
			delegate.release();
		}
		delegate = newDelegate;
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
		return (delegate == MISSING_DELEGATE) ? MISSING_DELEGATE_HASH : delegate.getHash();
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

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		if (delegate == MISSING_DELEGATE) {
			out.writeBoolean(false);
		} else {
			out.writeBoolean(true);
			delegate.serialize(out);
		}
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		var hasData = in.readBoolean();
		if (hasData) {
			delegate = blobSupplier.get();
			delegate.deserialize(in, MerkleOptionalBlob.MERKLE_VERSION);
		}
	}

	@Override
	public void serializeAbbreviated(SerializableDataOutputStream out) { 
                /* Nothing to do here, since Platform automatically serializes the 
                 * hash of an MerkleExternalLeaf and passes it as an argument to 
                 * deserializeAbbreviated as below. (Our BinaryObject delegate 
                 * doesn't need anything except this hash to deserialize itself.) */
        }

	@Override
	public void deserializeAbbreviated(
			SerializableDataInputStream in,
			Hash hash,
			int version
	) {
		if (!MISSING_DELEGATE_HASH.equals(hash)) {
			delegate = blobSupplier.get();
			delegate.deserializeAbbreviated(in, hash, version);
		} else {
			delegate = MISSING_DELEGATE;
		}
	}

	/* --- FastCopyable --- */
	@Override
	public MerkleOptionalBlob copy() {
		return new MerkleOptionalBlob(delegate.copy());
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

		return Objects.equals(this.delegate, that.delegate);
	}

	@Override
	public int hashCode() {
		return Objects.hash(Objects.hashCode(delegate));
	}

	/* --- Bean --- */
	public byte[] getData() {
		return (delegate == MISSING_DELEGATE) ? NO_DATA : blobStoreSupplier.get().get(delegate);
	}

	public BinaryObject getDelegate() {
		return delegate;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("delegate", delegate)
				.toString();
	}

	@Override
	public void onRelease() {
		if (delegate != MISSING_DELEGATE) {
			delegate.release();
		}
	}
}

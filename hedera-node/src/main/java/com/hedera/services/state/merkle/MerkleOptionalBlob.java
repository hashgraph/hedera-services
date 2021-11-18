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
import com.swirlds.blob.BinaryObject;
import com.swirlds.blob.BinaryObjectStore;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.ExternalSelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.io.SerializationStrategy;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import com.swirlds.common.merkle.utility.Keyed;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import static com.swirlds.common.merkle.io.SerializationStrategy.EXTERNAL_SELF_SERIALIZATION;
import static com.swirlds.common.merkle.io.SerializationStrategy.SELF_SERIALIZATION;

public class MerkleOptionalBlob extends AbstractMerkleLeaf implements ExternalSelfSerializable, Keyed<String> {
	private static boolean inMigration = false;

	public static synchronized void setInMigration(boolean inMigration) {
		MerkleOptionalBlob.inMigration = inMigration;
	}

	static final int PRE_RELEASE_0180_VERSION = 1;
	static final int RELEASE_0180_VERSION = 2;

	static final int CURRENT_VERSION = RELEASE_0180_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x4cefb15eb131d9e3L;

	static final Hash MISSING_DELEGATE_HASH = new Hash(new byte[] {
			(byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03,
			(byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07,
			(byte) 0x08, (byte) 0x09, (byte) 0x0a, (byte) 0x0b,
			(byte) 0x0c, (byte) 0x0d, (byte) 0x0e, (byte) 0x0f,
			(byte) 0x10, (byte) 0x11, (byte) 0x12, (byte) 0x13,
			(byte) 0x14, (byte) 0x15, (byte) 0x16, (byte) 0x17,
			(byte) 0x18, (byte) 0x19, (byte) 0x1a, (byte) 0x1b,
			(byte) 0x1c, (byte) 0x1d, (byte) 0x1e, (byte) 0x1f,
			(byte) 0x20, (byte) 0x21, (byte) 0x22, (byte) 0x23,
			(byte) 0x24, (byte) 0x25, (byte) 0x26, (byte) 0x27,
			(byte) 0x28, (byte) 0x29, (byte) 0x2a, (byte) 0x2b,
			(byte) 0x2c, (byte) 0x2d, (byte) 0x2e, (byte) 0x2f,
	});
	static final byte[] NO_DATA = new byte[0];
	static final BinaryObject MISSING_DELEGATE = null;

	static Supplier<BinaryObject> blobSupplier = BinaryObject::new;
	static Supplier<BinaryObjectStore> blobStoreSupplier = BinaryObjectStore::getInstance;

	private String path;
	private BinaryObject delegate;
	private boolean copiedDuringMigration = false;

	public MerkleOptionalBlob() {
		delegate = MISSING_DELEGATE;
	}

	public MerkleOptionalBlob(final byte[] data) {
		delegate = blobStoreSupplier.get().put(data);
	}

	public MerkleOptionalBlob(final BinaryObject delegate) {
		this.delegate = delegate;
	}

	private static final Set<SerializationStrategy> STRATEGIES = Set.of(SELF_SERIALIZATION, EXTERNAL_SELF_SERIALIZATION);

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Set<SerializationStrategy> supportedSerialization(final int version) {
		return STRATEGIES;
	}

	@Override
	public String getKey() {
		return path;
	}

	@Override
	public void setKey(String path) {
		this.path = path;
	}

	public void modify(final byte[] newContents) {
		throwIfImmutable("Cannot modify the state of this immutable MerkleOptionalBlob.");
		final var newDelegate = blobStoreSupplier.get().put(newContents);
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
		return CURRENT_VERSION;
	}

	@Override
	public Hash getHash() {
		return (delegate == MISSING_DELEGATE) ? MISSING_DELEGATE_HASH : delegate.getHash();
	}

	@Override
	public void setHash(final Hash hash) {
		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 *
	 * Intentionally a no-op method
	 */
	@Override
	public void invalidateHash() {
		/* No-op */
	}

	@Override
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		if (delegate == MISSING_DELEGATE) {
			out.writeBoolean(false);
		} else {
			out.writeBoolean(true);
			delegate.serialize(out);
		}
		out.writeNormalisedString(path);
	}

	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
		final var hasData = in.readBoolean();
		if (hasData) {
			delegate = blobSupplier.get();
			delegate.deserialize(in, BinaryObject.ClassVersion.ORIGINAL);
		}
		if (version >= RELEASE_0180_VERSION) {
			path = in.readNormalisedString(Integer.MAX_VALUE);
		}
	}

	@Override
	public void serializeExternal(final SerializableDataOutputStream out, final File ignored) throws IOException {
		out.writeNormalisedString(path);
		/* Nothing to do here, since Platform automatically serializes the
		 * hash of an MerkleExternalLeaf and passes it as an argument to
		 * deserializeAbbreviated as below. (Our BinaryObject delegate
		 * doesn't need anything except this hash to deserialize itself.) */
	}

	@Override
	public void deserializeExternal(
			final SerializableDataInputStream in,
			final File ignored,
			final Hash hash,
			final int version
	) throws IOException {
		if (!MISSING_DELEGATE_HASH.equals(hash)) {
			delegate = blobSupplier.get();
			delegate.deserializeExternal(in, ignored, hash, BinaryObject.ClassVersion.ORIGINAL);
		} else {
			delegate = MISSING_DELEGATE;
		}
		if (version >= RELEASE_0180_VERSION) {
			path = in.readNormalisedString(Integer.MAX_VALUE);
		}
	}

	/* --- FastCopyable --- */
	@Override
	public MerkleOptionalBlob copy() {
		setImmutable(true);
		copiedDuringMigration = inMigration;
		final var fcDelegate = inMigration ? delegate : delegate.copy();
		final var fc = new MerkleOptionalBlob(fcDelegate);
		fc.setKey(path);
		return fc;
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || MerkleOptionalBlob.class != o.getClass()) {
			return false;
		}

		final var that = (MerkleOptionalBlob) o;

		return Objects.equals(this.delegate, that.delegate) && Objects.equals(this.path, that.path);
	}

	@Override
	public int hashCode() {
		return Objects.hash(Objects.hashCode(delegate), path);
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
				.add("path", path)
				.add("delegate", delegate)
				.toString();
	}

	@Override
	public void onRelease() {
		if (!copiedDuringMigration && delegate != MISSING_DELEGATE) {
			delegate.release();
		}
	}
}

package com.hedera.services.state.merkle;

import com.google.common.base.MoreObjects;
import com.swirlds.common.FCMValue;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;

import java.io.IOException;

public class MerklePlaceholder extends AbstractMerkleLeaf implements FCMValue {
	static final int RELEASE_0140_VERSION = 1;

	static final int MERKLE_VERSION = RELEASE_0140_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x107fd7f8daefd48fL;

	public MerklePlaceholder() {
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
		in.readBoolean();
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeBoolean(Boolean.TRUE);
	}

	/* --- Object --- */
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || MerklePlaceholder.class != o.getClass()) {
			return false;
		}

		return true;
	}

	@Override
	public int hashCode() {
		return 0;
	}


	/* --- FastCopyable --- */
	@Override
	public MerklePlaceholder copy() {
		return this;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.toString();
	}
}

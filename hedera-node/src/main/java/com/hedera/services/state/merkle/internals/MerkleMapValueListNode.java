package com.hedera.services.state.merkle.internals;


import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Implements a {@link MapValueListNode} that lives in a {@link com.swirlds.merkle.map.MerkleMap} whose
 * keys are both {@link SelfSerializable} and {@link FastCopyable}.
 *
 * @param <K>
 * 		the key type of the map
 * @param <V>
 * 		the value type of the map
 */
public class MerkleMapValueListNode<K extends SelfSerializable & FastCopyable, V extends SelfSerializable & FastCopyable>
		extends AbstractMerkleLeaf implements MapValueListNode<K>, MerkleLeaf {

	public MerkleMapValueListNode() {
		/* RuntimeConstructable */
	}

	@Override
	public AbstractMerkleLeaf copy() {
		throw new AssertionError("Not implemented");
	}

	@Override
	public void deserialize(final SerializableDataInputStream din, final int version) throws IOException {
		throw new AssertionError("Not implemented");
	}

	@Override
	public void serialize(final SerializableDataOutputStream dout) throws IOException {
		throw new AssertionError("Not implemented");
	}

	@Override
	public long getClassId() {
		throw new AssertionError("Not implemented");
	}

	@Override
	public int getVersion() {
		throw new AssertionError("Not implemented");
	}

	@Override
	public K prevKey() {
		throw new AssertionError("Not implemented");
	}

	@Override
	public K nextKey() {
		throw new AssertionError("Not implemented");
	}

	@Override
	public void setPrevKey(final @Nullable K prevKey) {
		throw new AssertionError("Not implemented");
	}

	@Override
	public void setNextKey(final @Nullable K nextKey) {
		throw new AssertionError("Not implemented");
	}
}

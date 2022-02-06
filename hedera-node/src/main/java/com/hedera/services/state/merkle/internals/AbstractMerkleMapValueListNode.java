package com.hedera.services.state.merkle.internals;


import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import com.swirlds.common.merkle.utility.Keyed;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Provides implementation assistance for a {@link MapValueListNode} that lives in a
 * {@link com.swirlds.merkle.map.MerkleMap} whose keys are both {@link SelfSerializable} and {@link FastCopyable}.
 *
 * @param <K>
 * 		the key type of the map
 */
public abstract class AbstractMerkleMapValueListNode<
		K extends SelfSerializable & FastCopyable,
		S extends AbstractMerkleMapValueListNode<K, S>>
		extends AbstractMerkleLeaf implements MerkleLeaf, Keyed<K>, MapValueListNode<K, S> {

	private static final int PREV_INDEX = 0;
	private static final int CUR_INDEX = 1;
	private static final int NEXT_INDEX = 2;

	private K[] keys;

	public AbstractMerkleMapValueListNode() {
		keys = orderedKeys(null, null, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public S copy() {
		final var that = newValueCopyOf(self());
		that.setPrevKey(copyOf(keys[PREV_INDEX]));
		that.setKey(copyOf(keys[CUR_INDEX]));
		that.setNextKey(copyOf(keys[NEXT_INDEX]));
		return that;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
		final List<K> wrappedKeys = in.readSerializableList(3);
		keys = orderedKeys(wrappedKeys.get(0), wrappedKeys.get(1), wrappedKeys.get(2));
		deserializeValueFrom(in, version);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		out.writeSerializableList(Arrays.asList(keys), true, true);
		serializeValueTo(out);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public K prevKey() {
		return keys[PREV_INDEX];
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public K nextKey() {
		return keys[NEXT_INDEX];
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setPrevKey(final @Nullable K prevKey) {
		keys[PREV_INDEX] = prevKey;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setNextKey(final @Nullable K nextKey) {
		keys[NEXT_INDEX] = nextKey;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public K getKey() {
		return keys[CUR_INDEX];
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setKey(final K key) {
		keys[CUR_INDEX] = key;
	}

	/* --- Internal helpers -- */
	@SafeVarargs
	private K[] orderedKeys(K... k123) {
		return k123;
	}

	private K copyOf(final K k) {
		return (k == null) ? null : k.copy();
	}
}

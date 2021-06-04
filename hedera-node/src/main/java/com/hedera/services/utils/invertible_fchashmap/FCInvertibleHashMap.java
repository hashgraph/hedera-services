/*
 * (c) 2016-2021 Swirlds, Inc.
 *
 * This software is the confidential and proprietary information of
 * Swirlds, Inc. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Swirlds.
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SWIRLDS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

package com.hedera.services.utils.invertible_fchashmap;

import com.swirlds.common.FastCopyable;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.fchashmap.FCHashMap;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A fast copyable hashmap that is also able to generate inverse mappings between a value and the set of all
 * keys that map to that value. Supports pagination.
 * <p>
 * This data structure does not support null values.
 *
 * @param <K>
 * 		the type of the key. This key must be effectively immutable -- that is, no changes to the key after its
 * 		insertion may have any effect on its {@link Object#equals(Object)} or {@link Object#hashCode()}.
 * @param <V>
 * 		the type of the value
 * @param <I>
 * 		the type of the identifier used for the value, as required by the {@link Identifiable} interface
 */
public class FCInvertibleHashMap<K, V extends Identifiable<I>, I> extends AbstractMap<K, V> implements FastCopyable, MerkleNode {
	/**
	 * A standard mapping between keys and values.
	 */
	private final FCHashMap<K, V> keyToValueMap;

	/**
	 * A map between values and keys.
	 */
	private final FCHashMap<InverseKey<I>, K> valueToKeyMap;

	/**
	 * The number of times that a given value appears in the map.
	 */
	private final FCHashMap<I, Integer> valueCountMap;

	/**
	 * The index of each key in the mutable copy.
	 */
	private final Map<K, Integer> keyIndexMap;

	private boolean immutable;

	private int referenceCount;

	/**
	 * Create a new {@link FCInvertibleHashMap}.
	 */
	public FCInvertibleHashMap() {
		this(0);
	}

	/**
	 * Create a new {@link FCInvertibleHashMap} with an initial capacity.
	 *
	 * @param capacity
	 * 		the initial capacity of the hashmap
	 */
	public FCInvertibleHashMap(final int capacity) {
		keyToValueMap = new FCHashMap<>(capacity);
		valueToKeyMap = new FCHashMap<>();
		valueCountMap = new FCHashMap<>();
		keyIndexMap = new HashMap<>(capacity);
		this.referenceCount = 0;
	}

	/**
	 * Copy constructor.
	 *
	 * @param that
	 * 		the map to copy
	 */
	private FCInvertibleHashMap(final FCInvertibleHashMap<K, V, I> that) {
		keyToValueMap = that.keyToValueMap.copy();
		valueToKeyMap = that.valueToKeyMap.copy();
		valueCountMap = that.valueCountMap.copy();
		keyIndexMap = that.keyIndexMap;
		referenceCount = that.referenceCount;
		that.immutable = true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public FCInvertibleHashMap<K, V, I> copy() {
		return new FCInvertibleHashMap<>(this);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isImmutable() {
		return immutable;
	}

	/**
	 * Given a value, return an iterator that iterates over the set of keys that map to the value.
	 * <p>
	 * This iterator should not used after this copy of the map is updated in any way.
	 * This iterator may exhibit undefined behavior if this copy of the map is updated between
	 * the creation of this iterator and the use of this iterator.
	 *
	 * @return an iterator for the key set
	 */
	public Iterator<K> inverseGet(final V value) {
		return inverseGet(value, 0, getValueCount(value));
	}

	/**
	 * Given a value, return an iterator that iterates over a paginated set of keys that map to the value. Iteration
	 * starts at a given index and continues until the end of the key set.
	 * <p>
	 * This iterator should not used after this copy of the map is updated in any way.
	 * This iterator may exhibit undefined behavior if this copy of the map is updated between
	 * the creation of this iterator and the use of this iterator.
	 *
	 * @param startIndex
	 * 		the index of the first key to return (inclusive).
	 * 		An index of 0 starts iteration at the first key in the set.
	 * @return an iterator for the key set starting at the given index
	 */
	public Iterator<K> inverseGet(final V value, final int startIndex) {
		return inverseGet(value, startIndex, getValueCount(value));
	}

	/**
	 * Given a value, return an iterator that iterates over a paginated set of keys that map to the value.
	 * <p>
	 * This iterator should not used after this copy of the map is updated in any way.
	 * This iterator may exhibit undefined behavior if this copy of the map is updated between
	 * the creation of this iterator and the use of this iterator.
	 *
	 * @param startIndex
	 * 		the index of the first key to return (inclusive).
	 * 		An index of 0 starts iteration at the first key in the set.
	 * @param endIndex
	 * 		the index bounding the last key to return (exclusive).
	 * 		An index of N+1 will cause the set to include the last element if there are N elements.
	 * @return an iterator for the key set starting at the given index
	 */
	public Iterator<K> inverseGet(final V value, final int startIndex, final int endIndex) {
		return new FCInvertibleHashMapIterator<>(this, value, startIndex, endIndex);
	}

	/**
	 * Get a key that maps to a given value at a particular index.
	 * <p>
	 * This method is intentionally package private and is intended for use by {@link FCInvertibleHashMapIterator}.
	 *
	 * @param value
	 * 		the value that the key maps to
	 * @param index
	 * 		the index of the key
	 * @return the key that matches the value and index
	 */
	K getKeyAtIndex(final V value, final int index) {
		return valueToKeyMap.get(new InverseKey<>(value.getIdentity(), index));
	}

	/**
	 * Return a count of the number of keys that reference a given value, i.e. the number of times that a value
	 * appears in the map.
	 *
	 * @param value
	 * 		the value in question
	 * @return the number of keys that reference the value
	 */
	public int getValueCount(final V value) {
		return valueCountMap.get(value.getIdentity());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void release() {
		keyToValueMap.release();
		valueToKeyMap.release();
		valueCountMap.release();
	}

	/**
	 * Update data structures maintaining the mapping values to keys to include a new value to key association.
	 *
	 * @param key
	 * 		the key that references the value
	 * @param value
	 * 		the value that the key references
	 */
	private void associateKeyAndValue(final K key, final V value) {
		final int index = valueCountMap.getOrDefault(value.getIdentity(), 0);
		valueToKeyMap.put(new InverseKey<>(value.getIdentity(), index), key);
		valueCountMap.put(value.getIdentity(), index + 1);
		keyIndexMap.put(key, index);
	}

	/**
	 * Update data structures maintaining the mapping values to keys to no
	 * longer include a new value to key association.
	 *
	 * @param key
	 * 		the key that references the value
	 * @param value
	 * 		the value that the key references
	 */
	private void disassociateKeyAndValue(final K key, final V value, final boolean replacingValue) {
		final int keyIndex = keyIndexMap.get(key);
		final int maxIndex = valueCountMap.get(value.getIdentity()) - 1;

		valueToKeyMap.remove(new InverseKey<>(value.getIdentity(), keyIndex));
		if (keyIndex != maxIndex) {
			// We need to fill the gap, key indices must be must not skip any values
			final K gapFillingKey = valueToKeyMap.remove(new InverseKey<>(value.getIdentity(), maxIndex));
			valueToKeyMap.put(new InverseKey<>(value.getIdentity(), keyIndex), gapFillingKey);
			keyIndexMap.put(gapFillingKey, keyIndex);
		}

		final int newValueCount = valueCountMap.get(value.getIdentity()) - 1;
		if (newValueCount == 0) {
			valueCountMap.remove(value.getIdentity());
		} else {
			valueCountMap.put(value.getIdentity(), newValueCount);
		}

		if (!replacingValue) {
			keyIndexMap.remove(key);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public V put(final K key, final V value) {
		throwIfImmutable();
		if (value == null) {
			throw new IllegalArgumentException("FCInvertibleHashMap does not support null values");
		}
		final V previousValue = keyToValueMap.get(key);
		if (Objects.equals(previousValue, value)) {
			return previousValue;
		}

		if (keyToValueMap.containsKey(key)) {
			disassociateKeyAndValue(key, previousValue, true);
		}

		keyToValueMap.put(key, value);
		associateKeyAndValue(key, value);

		return previousValue;
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	@Override
	public V remove(final Object key) {
		throwIfImmutable();
		final V value = keyToValueMap.remove(key);
		disassociateKeyAndValue((K) key, value, false);
		return value;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Set<Entry<K, V>> entrySet() {
		return keyToValueMap.entrySet();
	}

	@Override
	public boolean isLeaf() {
		return false;
	}

	@Override
	public void incrementReferenceCount() {
		referenceCount++;
	}

	@Override
	public void decrementReferenceCount() {
		referenceCount--;
	}

	@Override
	public int getReferenceCount() {
		return referenceCount;
	}

	@Override
	public long getClassId() {
		return 0;
	}

	@Override
	public Hash getHash() {
		return new Hash();
	}

	@Override
	public void setHash(final Hash hash) {
	}

	@Override
	public int getVersion() {
		return 1;
	}
}

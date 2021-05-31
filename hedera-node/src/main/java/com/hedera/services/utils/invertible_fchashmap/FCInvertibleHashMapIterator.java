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



import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * This object is capable of iterating over all keys that map to a given value
 *
 * @param <K>
 * 		the type of the key
 * @param <V>
 * 		the type of the value
 * @param <I>
 * 		the type of the identifier for the value
 */
public class FCInvertibleHashMapIterator<K, V extends Identifiable<I>, I> implements Iterator<K> {

	private final FCInvertibleHashMap<K, V, I> map;
	private final V value;
	private final int endIndex;

	private int nextIndex;

	/**
	 * Create a new iterator.
	 *
	 * @param map
	 * 		the map to iterate over
	 * @param value
	 * 		the value whose keys this will iterate
	 * @param startIndex
	 * 		the index where iteration starts (inclusive)
	 * @param endIndex
	 * 		the index where iteration ends (exclusive)
	 */
	public FCInvertibleHashMapIterator(
			final FCInvertibleHashMap<K, V, I> map,
			final V value,
			final int startIndex,
			final int endIndex) {

		this.map = map;
		this.value = value;

		final int valueCount = map.getValueCount(value);
		if (valueCount < endIndex) {
			throw new IllegalArgumentException(
					"requested end index " + endIndex + " exceeds number of elements " + valueCount);
		}

		this.endIndex = endIndex;
		this.nextIndex = startIndex;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasNext() {
		return nextIndex < endIndex;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public K next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}
		final int index = nextIndex;
		nextIndex++;
		return map.getKeyAtIndex(value, index);
	}
}
package com.hedera.services.state.merkle.internals;

import javax.annotation.Nullable;

/**
 * Defines a simple type that serves as a node in a doubly-linked list of map values.
 *
 * @param <K>
 * 		the type of the key in the map
 */
public interface MapValueListNode<K> {
	/**
	 * Gets the key of the previous value in the map value list.
	 *
	 * @return the previous key, or null if this node is the root of the list
	 */
	K prevKey();

	/**
	 * Gets the key of the next value in the map value list.
	 *
	 * @return the next key, or null if this node is the last of the list
	 */
	K nextKey();

	/**
	 * Updates the key of the previous value in the map value list.
	 */
	void setPrevKey(@Nullable K prevKey);

	/**
	 * Updates the key of the next value in the map value list.
	 */
	void setNextKey(@Nullable K nextKey);
}

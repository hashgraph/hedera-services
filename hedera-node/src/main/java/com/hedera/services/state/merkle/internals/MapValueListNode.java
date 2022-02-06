package com.hedera.services.state.merkle.internals;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Defines a type that serves as a node in a doubly-linked list of map values.
 *
 * @param <K>
 * 		the type of the key in the map
 */
public interface MapValueListNode<K, S extends MapValueListNode<K, S>> {
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

	/**
	 * Serializes the value of this node to the given output stream.
	 *
	 * @param out the stream to write the value to
	 * @throws IOException if serialization fails
	 */
	void serializeValueTo(SerializableDataOutputStream out) throws IOException;

	/**
	 * Deserializes the value of this node from the given input stream.
	 *
	 * @param in the stream to read the value from
	 * @throws IOException if deserialization fails
	 */
	void deserializeValueFrom(SerializableDataInputStream in, int version) throws IOException;

	/**
	 * Makes a copy of this node with the value deep-copied.
	 *
	 * @return the new instance with the value copied
	 */
	S newValueCopyOf(S that);

	/**
	 * Returns this instance in its most specialized type.
	 *
	 * @return this instance
	 */
	S self();
}
package com.hedera.services.utils;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.swirlds.common.FastCopyable;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Objects;

public class MapValueListUtils {
	/**
	 * Inserts the given key/value at the front of the linked list in the map represented by the given
	 * {@link MapValueListMutation}, updating the doubly-linked list to maintain the prev/next keys of
	 * the "adjacent" value(s) as needed. Uses {@link MapValueListMutation#getForModify(Object)}.
	 *
	 * @param key
	 * 		the key of the new mapping
	 * @param value
	 * 		the value of the new mapping
	 * @param rootKey
	 * 		the root of the in-scope linked list
	 * @param rootValue
	 * 		the mutable value at the root of the in-scope linked list
	 * @param listMutation
	 * 		the facilitator representing the map that contains the linked list
	 * @param insertIntoMap
	 * 		Adds the new `key` -> `value` pair to the merkleMap.
	 * @param <K> the type of key in the map
	 * @param <V> the type of value in the map
	 * @return the new root of the list, for convenience
	 */
	@NotNull
	public static <K, V extends FastCopyable> K inPlaceInsertAtMapValueListHead(
			@NotNull final K key,
			@NotNull final V value,
			@Nullable final K rootKey,
			@Nullable final V rootValue,
			@NotNull final MapValueListMutation<K, V> listMutation,
			final boolean insertIntoMap
	) {
		if (insertIntoMap) {
			listMutation.put(key, value);
		}
		if (rootKey != null) {
			final V nextValue = (rootValue == null) ? listMutation.getForModify(rootKey) : rootValue;
			listMutation.updateNext(value, rootKey);
			listMutation.updatePrev(nextValue, key);
		}
		return key;
	}

	/**
	 * Removes the key/value pair with the given key from its containing linked list in the map represented by the
	 * given {@link MapValueListMutation}, updating the doubly-linked list to maintain the prev/next keys of the
	 * "adjacent" value(s) as needed. Uses {@link MapValueListMutation#getForModify(Object)}.
         *
	 * @param key
	 * 		the key of the mapping to remove
	 * @param root
	 * 		the key of the root mapping in the affected node's list
	 * @param listRemoval
	 * 		the facilitator representing the underlying map
	 * @return the new root key, for convenience
	 */
	public static @Nullable
	<K, V extends FastCopyable> K inPlaceRemoveFromMapValueList(
			@NotNull final K key,
			@NotNull final K root,
			@NotNull final MapValueListMutation<K, V> listRemoval
	) {
		return internalMutateFromMapValueList(key, root, listRemoval, true, true, false);
	}

	/**
	 * Removes the key/value pair with the given key from its containing linked list in the map represented by the
	 * given {@link MapValueListMutation}, updating the doubly-linked list to maintain the prev/next keys of the
	 * "adjacent" value(s) as needed. Does <i>not</i> use {@link MapValueListMutation#getForModify(Object)}.
	 *
	 * @param key
	 * 		the key of the mapping to remove
	 * @param root
	 * 		the key of the root mapping in the affected node's list
	 * @param listRemoval
	 * 		the facilitator representing the underlying map
	 * @return the new root key, for convenience
	 */
	public static @Nullable
	<K, V extends FastCopyable> K overwritingRemoveFromMapValueList(
			@NotNull final K key,
			@NotNull final K root,
			@NotNull final MapValueListMutation<K, V> listRemoval
	) {
		return internalMutateFromMapValueList(key, root, listRemoval, false, true, false);
	}

	/**
	 * Unlinks the value of the given key from its containing linked list in the map represented by the
	 * given {@link MapValueListMutation}, updating the doubly-linked list to maintain the prev/next keys of the
	 * "adjacent" value(s) as needed and resets the next and prev pointers of this value.
	 * uses {@link MapValueListMutation#getForModify(Object)}.
	 *
	 * @param key
	 * 		the key of the mapping to unlink
	 * @param root
	 * 		the key of the root mapping in the affected node's list
	 * @param listRemoval
	 * 		the facilitator representing the underlying map
	 * @return the new root key, for convenience
	 */
	public static @Nullable
	<K, V extends FastCopyable> K unlinkFromMapValueLink(
			@NotNull final K key,
			@NotNull final K root,
			@NotNull final MapValueListMutation<K, V> listRemoval
	) {
		return internalMutateFromMapValueList(key, root, listRemoval, true, false, true);
	}

	private static @Nullable
	<K, V extends FastCopyable> K internalMutateFromMapValueList(
			@NotNull final K key,
			@NotNull final K root,
			@NotNull final MapValueListMutation<K, V> listRemoval,
			final boolean useGetForModify,
			final boolean removeFromMap,
			final boolean resetPointers
	) {
		final var value = Objects.requireNonNull(listRemoval.get(key), () -> "Missing key " + key);
		if (removeFromMap) {
			listRemoval.remove(key);
		}

		final var nextKey = listRemoval.next(value);
		final var prevKey = listRemoval.prev(value);

		if (resetPointers) {
			// reset the next and prev pointers on the node that we are unlinking.
			V valueCopy = value.copy();
			listRemoval.markAsTail(valueCopy);
			listRemoval.markAsHead(valueCopy);
			listRemoval.put(key, valueCopy);
		}

		if (nextKey != null) {
			final var nextValue = useGetForModify
					? Objects.requireNonNull(listRemoval.getForModify(nextKey), () -> "Missing next key " + nextKey)
					// It is ONLY safe to call copy() here---making the map's value immutable!---because
					// we immediately put() the mutable value back into the map below
					: Objects.requireNonNull(listRemoval.get(nextKey), () -> "Missing next key " + nextKey).<V>copy();
			if (prevKey == null) {
				listRemoval.markAsHead(nextValue);
			} else {
				listRemoval.updatePrev(nextValue, prevKey);
			}
			if (!useGetForModify) {
				listRemoval.put(nextKey, nextValue);
			}
		}
		if (prevKey != null) {
			final var prevValue = useGetForModify
					? Objects.requireNonNull(listRemoval.getForModify(prevKey), () -> "Missing prev key " + prevKey)
					// Note it is ONLY safe to call copy() here---making the map's value
					// immutable!---because we immediately put() the mutable value below
					: Objects.requireNonNull(listRemoval.get(prevKey), () -> "Missing prev key " + prevKey).<V>copy();
			if (nextKey == null) {
				listRemoval.markAsTail(prevValue);
			} else {
				listRemoval.updateNext(prevValue, nextKey);
			}
			if (!useGetForModify) {
				listRemoval.put(prevKey, prevValue);
			}
		}
		return key.equals(root) ? nextKey : root;
	}

	private MapValueListUtils() {
		throw new UnsupportedOperationException("Utility Class");
	}
}

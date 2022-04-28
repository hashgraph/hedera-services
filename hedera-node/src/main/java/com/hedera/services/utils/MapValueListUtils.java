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
	 * @param <K> the type of key in the map
	 * @param <V> the type of value in the map
	 * @return the new root of the list, for convenience
	 */
	public static @Nullable
	<K, V extends FastCopyable> K inPlaceInsertAtMapValueListHead(
			@NotNull final K key,
			@NotNull final V value,
			@Nullable final K rootKey,
			@Nullable final V rootValue,
			@NotNull final MapValueListMutation<K, V> listMutation
	) {
		listMutation.put(key, value);
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
	 * 		the key of the new mapping
	 * @param value
	 * 		the value of the new mapping
	 * @param rootKey
	 * 		the root of the in-scope linked list
	 * @param rootValue
	 * 		the mutable value at the root of the in-scope linked list
	 * @param listMutation
	 * 		the facilitator representing the map that contains the linked list
	 * @param <K> the type of key in the map
	 * @param <V> the type of value in the map
	 * @return the new root of the list, for convenience
	 */
	public static @Nullable
	<K, V> K inPlaceInsertAtMapValueListHead(
			@NotNull final K key,
			@NotNull final V value,
			@Nullable final K rootKey,
			@Nullable final V rootValue,
			@NotNull final MapValueListMutation<K, V> listMutation
	) {
		listMutation.put(key, value);
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
	 * "adjacent" value(s) as needed.
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
	<K, V> K removeFromMapValueList(
			@NotNull final K key,
			@NotNull final K root,
			@NotNull final MapValueListMutation<K, V> listRemoval
	) {
		final var value = listRemoval.get(key);
		Objects.requireNonNull(value, "The removed mapping had no value for key " + key);
		listRemoval.remove(key);

		final var nextKey = listRemoval.next(value);
		final var prevKey = listRemoval.prev(value);
		if (nextKey != null) {
			final var nextValue = listRemoval.getForModify(nextKey);
			Objects.requireNonNull(nextValue, "The next mapping had no value for key " + nextKey);
			if (prevKey == null) {
				listRemoval.markAsHead(nextValue);
			} else {
				listRemoval.updatePrev(nextValue, prevKey);
			}
		}
		if (prevKey != null) {
			final var prevValue = listRemoval.getForModify(prevKey);
			Objects.requireNonNull(prevValue, "The previous mapping had no value for key " + prevKey);
			if (nextKey == null) {
				listRemoval.markAsTail(prevValue);
			} else {
				listRemoval.updateNext(prevValue, nextKey);
			}
		}
		return key.equals(root) ? nextKey : root;
	}

	private MapValueListUtils() {
		throw new UnsupportedOperationException("Utility Class");
	}
}

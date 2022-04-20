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
	 * Removes the key/value pair with the given key from its containing linked list in the map represented by the
	 * given {@link MapValueListRemoval}, updating the doubly-linked list to maintain the prev/next keys of the
	 * "adjacent" value(s) as needed. Uses {@link MapValueListRemoval#getForModify(Object)}.
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
			@NotNull final MapValueListRemoval<K, V> listRemoval
	) {
		return internalRemoveFromMapValueList(key, root, listRemoval, true);
	}

	/**
	 * Removes the key/value pair with the given key from its containing linked list in the map represented by the
	 * given {@link MapValueListRemoval}, updating the doubly-linked list to maintain the prev/next keys of the
	 * "adjacent" value(s) as needed. Does <i>not</i> use {@link MapValueListRemoval#getForModify(Object)}.
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
			@NotNull final MapValueListRemoval<K, V> listRemoval
	) {
		return internalRemoveFromMapValueList(key, root, listRemoval, false);
	}

	private static @Nullable
	<K, V extends FastCopyable> K internalRemoveFromMapValueList(
			@NotNull final K key,
			@NotNull final K root,
			@NotNull final MapValueListRemoval<K, V> listRemoval,
			boolean useGetForModify
	) {
		final var value = Objects.requireNonNull(listRemoval.get(key), () -> "Missing key " + key);
		listRemoval.remove(key);

		final var nextKey = listRemoval.next(value);
		final var prevKey = listRemoval.prev(value);
		if (nextKey != null) {
			final var nextValue = useGetForModify
					? Objects.requireNonNull(listRemoval.getForModify(nextKey), () -> "Missing next key " + nextKey)
					// Note it is ONLY safe to call copy() here---making the map's value
					// immutable!---because we immediately put() the mutable value below
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

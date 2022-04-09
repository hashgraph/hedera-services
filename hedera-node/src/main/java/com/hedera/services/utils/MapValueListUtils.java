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

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Objects;

public class MapValueListUtils {
	/**
	 * Removes the key/value pair with the given key from its containing linked list in the map represented by the
	 * given {@link MapValueListRemoval}, updating the affected doubly-linked list to maintain the prev/next keys
	 * of the "adjacent" value(s) as needed.
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
			@NotNull final MapValueListRemoval<K, V> listRemoval
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
}

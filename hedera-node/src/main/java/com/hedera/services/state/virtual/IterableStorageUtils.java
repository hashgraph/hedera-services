package com.hedera.services.state.virtual;

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

import com.hedera.services.utils.MapValueListUtils;
import com.swirlds.common.CommonUtils;
import com.swirlds.virtualmap.VirtualMap;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Objects;

public class IterableStorageUtils {
	private static final String NO_ITERABLE_STORAGE = "[]";

	private IterableStorageUtils() {
		throw new UnsupportedOperationException("Utility Class");
	}

	public static String joinedStorageMappings(
			final ContractKey firstKey,
			final VirtualMap<ContractKey, ContractValue> storage
	) {
		if (firstKey == null) {
			return NO_ITERABLE_STORAGE;
		}

		final var sb = new StringBuilder("[");
		var isFirstValue = true;
		final long contractId = firstKey.getContractId();
		ContractKey nextKey = firstKey;
		while (nextKey != null) {
			final var value = storage.get(nextKey);
			Objects.requireNonNull(value, "Linked key " + nextKey + " had no mapped value");
			sb
					.append(isFirstValue ? "" : ", ")
					.append(nextKey)
					.append(" -> ")
					.append(" @").append(System.identityHashCode(value)).append(" ")
					.append(CommonUtils.hex(value.getValue()));
			isFirstValue = false;
			nextKey = value.getNextKeyScopedTo(contractId);
		}
		return sb.append("]").toString();
	}

	/**
	 * "Upserts" a key/value pair in the given {@code VirtualMap}, as follows:
	 * <ol>
	 *     <li>If the key is already present, simply updates its value with no other actions.</li>
	 *     <li>Otherwise, inserts a new key/value pair in the given {@code VirtualMap} at the front of the doubly-linked
	 *     list of the relevant contract's storage, updating the prev/next keys of the "adjacent" values as needed.</li>
	 * </ol>
	 *
	 * @param key
	 * 		the key of the new mapping
	 * @param value
	 * 		the value of the new mapping
	 * @param rootKey
	 * 		the key of the root mapping in the storage list
	 * @param rootValue
	 * 		if pre-fetched, the value of the root mapping in the storage list
	 * @param storage
	 * 		the working copy of the storage map
	 * @return the new root key, for convenience
	 */
	public static ContractKey upsertMapping(
			@NotNull final ContractKey key,
			@NotNull final ContractValue value,
			@Nullable final ContractKey rootKey,
			@Nullable final ContractValue rootValue,
			final VirtualMap<ContractKey, ContractValue> storage
	) {
		final var oldValue = storage.getForModify(key);
		if (oldValue != null) {
			oldValue.setValue(value.getValue());
			return rootKey;
		} else {
			storage.put(key, value);
			if (rootKey != null) {
				value.setNextKey(rootKey.getKey());
				final var nextValue = rootValue == null ? storage.getForModify(rootKey) : rootValue;
				Objects.requireNonNull(nextValue, "The root mapping had no value for key " + rootKey);
				nextValue.setPrevKey(key.getKey());
			}
			return key;
		}
	}

	/**
	 * Removes the key/value pair with the given key in the given {@code VirtualMap}, and updates the doubly-linked
	 * list of the relevant contract's storage to maintain the prev/next keys of the "adjacent" value(s) as needed.
	 *
	 * @param key
	 * 		the key of the mapping to remove
	 * @param root
	 * 		the key of the root mapping in the storage list
	 * @param storage
	 * 		the working copy of the storage map
	 * @return the new root key, for convenience
	 */
	public static @Nullable ContractKey removeMapping(
			@NotNull final ContractKey key,
			@NotNull final ContractKey root,
			final VirtualMap<ContractKey, ContractValue> storage
	) {
		return MapValueListUtils.removeFromMapValueList(key, root, new ContractStorageListRemoval(key.getContractId(), storage));
	}

}

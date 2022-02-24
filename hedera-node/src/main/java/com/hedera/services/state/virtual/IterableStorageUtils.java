package com.hedera.services.state.virtual;

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
	 * @param rootKey
	 * 		the key of the root mapping in the storage list
	 * @param storage
	 * 		the working copy of the storage map
	 * @return the new root key, for convenience
	 */
	public static ContractKey removeMapping(
			@NotNull final ContractKey key,
			@NotNull final ContractKey rootKey,
			final VirtualMap<ContractKey, ContractValue> storage
	) {
		final var removedValue = storage.get(key);
		Objects.requireNonNull(removedValue, "The removed mapping had no value for key " + key);
		storage.remove(key);

		final var contractId = key.getContractId();
		final var nextKey = removedValue.getNextKeyScopedTo(contractId);
		final var prevKey = removedValue.getPrevKeyScopedTo(contractId);
		if (nextKey != null) {
			final var nextValue = storage.getForModify(nextKey);
			Objects.requireNonNull(nextValue, "The next mapping had no value for key " + nextKey);
			if (prevKey == null) {
				nextValue.markAsRootMapping();
			} else {
				nextValue.setPrevKey(prevKey.getKey());
			}
		}
		if (prevKey != null) {
			final var prevValue = storage.getForModify(prevKey);
			Objects.requireNonNull(prevValue, "The previous mapping had no value for key " + prevKey);
			if (nextKey == null) {
				prevValue.markAsLastMapping();
			} else {
				prevValue.setNextKey(nextKey.getKey());
			}
		}
		return key.equals(rootKey) ? nextKey : rootKey;
	}
}

package com.hedera.services.store.contracts;

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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import org.apache.tuweni.units.bigints.UInt256;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public class SizeLimitedStorage {
	public static final ContractValue ZERO_VALUE = ContractValue.from(UInt256.ZERO);

	private final GlobalDynamicProperties dynamicProperties;
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
	private final Supplier<VirtualMap<ContractKey, ContractValue>> storage;

	private Map<Long, Long> currentUsages = new HashMap<>();
	private Map<Long, AtomicLong> usageDeltas = new HashMap<>();
	private Map<Long, TreeSet<ContractKey>> updatedKeys = new TreeMap<>();
	private Map<Long, TreeSet<ContractKey>> removedKeys = new TreeMap<>();
	private Map<ContractKey, ContractValue> newMappings = new HashMap<>();

	public SizeLimitedStorage(
			final GlobalDynamicProperties dynamicProperties,
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final Supplier<VirtualMap<ContractKey, ContractValue>> storage
	) {
		this.dynamicProperties = dynamicProperties;
		this.accounts = accounts;
		this.storage = storage;
	}

	public void reset() {
		throw new AssertionError("Not implemented");
	}

	public void commit() {
		throw new AssertionError("Not implemented");
	}

	public UInt256 getStorage(final AccountID id, final UInt256 key) {
		throw new AssertionError("Not implemented");
	}

	public void putStorage(final AccountID id, final UInt256 key, final UInt256 value) {
		throw new AssertionError("Not implemented");
	}

	static int incorporateKvImpact(
			final ContractKey key,
			final ContractValue value,
			final Map<Long, TreeSet<ContractKey>> updatedKeys,
			final Map<Long, TreeSet<ContractKey>> removedKeys,
			final Map<ContractKey, ContractValue> newMappings,
			final VirtualMap<ContractKey, ContractValue> storage
	) {
		if (value == ZERO_VALUE) {
			return incorporateZeroingOf(key, updatedKeys, removedKeys, newMappings, storage);
		} else {
			return incorporateSettingOf(key, value, updatedKeys, removedKeys, newMappings, storage);
		}
	}

	private static int incorporateSettingOf(
			final ContractKey key,
			final ContractValue value,
			final Map<Long, TreeSet<ContractKey>> updatedKeys,
			final Map<Long, TreeSet<ContractKey>> removedKeys,
			final Map<ContractKey, ContractValue> newMappings,
			final VirtualMap<ContractKey, ContractValue> storage
	) {
		final Long contractId = key.getContractId();
		final var hasPendingUpdate = newMappings.containsKey(key);
		final var wasAlreadyPresent = storage.containsKey(key);
		/* We always buffer the new mapping. */
		newMappings.put(key, value);
		if (hasPendingUpdate) {
			/* If there was already a pending update, nothing has changed. */
			return 0;
		} else {
			/* Otherwise update the contract's change set. */
			updatedKeys.computeIfAbsent(contractId, TREE_SET_FACTORY).add(key);
			/* And drop any pending removal, returning 1 since a pending removal implies we
			 * were about to reduce the storage used by a mapping. */
			final var scopedRemovals = removedKeys.get(contractId);
			if (scopedRemovals != null) {
				scopedRemovals.remove(key);
				return 1;
			}
			return wasAlreadyPresent ? 0 : 1;
		}
	}

	private static int incorporateZeroingOf(
			final ContractKey key,
			final Map<Long, TreeSet<ContractKey>> updatedKeys,
			final Map<Long, TreeSet<ContractKey>> removedKeys,
			final Map<ContractKey, ContractValue> newMappings,
			final VirtualMap<ContractKey, ContractValue> storage
	) {
		final Long contractId = key.getContractId();
		final var hasPendingUpdate = newMappings.containsKey(key);
		final var wasAlreadyPresent = storage.containsKey(key);
		if (hasPendingUpdate || wasAlreadyPresent) {
			if (hasPendingUpdate) {
				/* We need to drop any pending removal from our auxiliary data structures. */
				final var scopedAdditions = updatedKeys.get(contractId);
				requireNonNull(scopedAdditions,
						() -> "A new mapping " + key + " -> " + newMappings.get(key)
								+ " did not belong to a key addition set");
				scopedAdditions.remove(key);
				newMappings.remove(key);
			}
			if (wasAlreadyPresent) {
				/* If there was no extant mapping for this key, no reason to explicitly remove it when we commit. */
				removedKeys.computeIfAbsent(key.getContractId(), TREE_SET_FACTORY).add(key);
			}
			/* But no matter what, _relative to our existing change set_, this removed one mapping. */
			return -1;
		} else {
			/* If this key didn't have a mapping or a pending change, it doesn't affect the size,
			 * and there is also no reason to explicitly remove it when we commit. */
			return 0;
		}
	}

	public static Function<Long, TreeSet<ContractKey>> TREE_SET_FACTORY = ignore -> new TreeSet<>();

	private static ContractValue valueOf(final UInt256 evmWord) {
		return evmWord.isZero() ? ZERO_VALUE : ContractValue.from(evmWord);
	}
}

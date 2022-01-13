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

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_CONTRACT_STORAGE_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED;
import static java.util.Objects.requireNonNull;
import static org.apache.tuweni.units.bigints.UInt256.ZERO;

@Singleton
public class SizeLimitedStorage {
	public static final ContractValue ZERO_VALUE = ContractValue.from(ZERO);

	private final GlobalDynamicProperties dynamicProperties;
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
	private final Supplier<VirtualMap<ContractKey, ContractValue>> storage;

	private final Map<Long, AtomicInteger> newUsages = new TreeMap<>();
	private final Map<Long, TreeSet<ContractKey>> updatedKeys = new TreeMap<>();
	private final Map<Long, TreeSet<ContractKey>> removedKeys = new TreeMap<>();
	private final Map<ContractKey, ContractValue> newMappings = new HashMap<>();

	private long totalKvPairs;

	@Inject
	public SizeLimitedStorage(
			final GlobalDynamicProperties dynamicProperties,
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final Supplier<VirtualMap<ContractKey, ContractValue>> storage
	) {
		this.dynamicProperties = dynamicProperties;
		this.accounts = accounts;
		this.storage = storage;
	}

	public void beginSession() {
		newUsages.clear();
		updatedKeys.clear();
		removedKeys.clear();
		newMappings.clear();

		totalKvPairs = storage.get().size();
	}

	public void validateAndCommit() {
		validatePendingSizeChanges();

		commitPendingRemovals();
		commitPendingUpdates();
	}

	public UInt256 getStorage(final AccountID id, final UInt256 key) {
		final var contractKey = ContractKey.from(id, key);

		final var zeroedOut = removedKeys.get(id.getAccountNum());
		if (zeroedOut != null && zeroedOut.contains(contractKey)) {
			return ZERO;
		}

		var effectiveValue = newMappings.get(contractKey);
		if (effectiveValue == null) {
			effectiveValue = storage.get().get(contractKey);
		}
		return (effectiveValue == null) ? ZERO : effectiveValue.asUInt256();
	}

	public void putStorage(final AccountID id, final UInt256 key, final UInt256 value) {
		final var contractKey = ContractKey.from(id, key);
		final var contractValue = virtualValueFrom(value);
		final var kvCountImpact = incorporateKvImpact(
				contractKey, contractValue, updatedKeys, removedKeys, newMappings, storage.get());
		if (kvCountImpact != 0) {
			newUsages.computeIfAbsent(id.getAccountNum(), this::kvPairsLookup).getAndAdd(kvCountImpact);
			totalKvPairs += kvCountImpact;
		}
	}

	private AtomicInteger kvPairsLookup(final Long num) {
		final var account = accounts.get().get(EntityNum.fromLong(num));
		if (account == null) {
			return new AtomicInteger(0);
		}
		return new AtomicInteger(account.getNumContractKvPairs());
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
			/* But no matter what, relative to our existing change set, this removed one mapping. */
			return -1;
		} else {
			/* If this key didn't have a mapping or a pending change, it doesn't affect the size,
			 * and there is also no reason to explicitly remove it when we commit. */
			return 0;
		}
	}

	private void validatePendingSizeChanges() {
		validateTrue(
				totalKvPairs <= dynamicProperties.maxAggregateContractKvPairs(),
				MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED);
		final var perContractMax = dynamicProperties.maxIndividualContractKvPairs();
		newUsages.forEach((id, newKvPairs) ->
				validateTrue(
						newKvPairs.get() <= perContractMax,
						MAX_CONTRACT_STORAGE_EXCEEDED));
	}

	private void commitPendingUpdates() {
		if (newMappings.isEmpty()) {
			throw new AssertionError("Not implemented");
		}
		final var curStorage = storage.get();
		updatedKeys.forEach((id, changeSet) -> changeSet.forEach(k -> curStorage.put(k, newMappings.get(k))));
	}

	private void commitPendingRemovals() {
		if (removedKeys.isEmpty()) {
			return;
		}
		final var curStorage = storage.get();
		removedKeys.forEach((id, zeroedOut) -> zeroedOut.forEach(curStorage::remove));
	}

	static Function<Long, TreeSet<ContractKey>> TREE_SET_FACTORY = ignore -> new TreeSet<>();

	private static ContractValue virtualValueFrom(final UInt256 evmWord) {
		return evmWord.isZero() ? ZERO_VALUE : ContractValue.from(evmWord);
	}

	/* --- Only used by unit tests --- */
	int usageSoFar(final AccountID id) {
		return newUsages.computeIfAbsent(id.getAccountNum(), this::kvPairsLookup).get();
	}

	Map<Long, AtomicInteger> getNewUsages() {
		return newUsages;
	}

	Map<Long, TreeSet<ContractKey>> getUpdatedKeys() {
		return updatedKeys;
	}

	Map<Long, TreeSet<ContractKey>> getRemovedKeys() {
		return removedKeys;
	}

	Map<ContractKey, ContractValue> getNewMappings() {
		return newMappings;
	}
}

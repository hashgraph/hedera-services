/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.store.contracts;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.ledger.properties.AccountProperty.FIRST_CONTRACT_STORAGE_KEY;
import static com.hedera.services.ledger.properties.AccountProperty.NUM_CONTRACT_KV_PAIRS;
import static com.hedera.services.utils.EntityNum.fromLong;
import static org.apache.tuweni.units.bigints.UInt256.ZERO;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.validation.ContractStorageLimits;
import com.hedera.services.state.validation.UsageLimits;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * Buffers a set of changes to the key/value pairs in contract storage into a <i>session</i>, and
 * validates that their net effect will not cause any individual contract to exceed the limit set by
 * {@link GlobalDynamicProperties#maxIndividualContractKvPairs()}; nor the aggregate storage to
 * exceed {@link GlobalDynamicProperties#maxAggregateContractKvPairs()}.
 *
 * <p>Note that writing {@link UInt256#ZERO} to a key removes it from the map; so it is possible for
 * a change to decrease the number of key/value pairs used.
 */
@Singleton
public class SizeLimitedStorage {
    public static final IterableContractValue ZERO_VALUE = IterableContractValue.from(ZERO);

    private final ContractStorageLimits usageLimits;

    // Used to upsert to a contract's doubly-linked list of storage mappings
    private final IterableStorageUpserter storageUpserter;
    // Used to remove from a contract's doubly-linked list of storage mappings
    private final IterableStorageRemover storageRemover;

    // Used to look up the initial key/value counts for the contracts involved in a change set
    private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
    // Used to both read and write key/value pairs throughout the lifecycle of a change set
    private final Supplier<VirtualMap<ContractKey, IterableContractValue>> storage;

    private final Map<Long, ContractKey> newFirstKeys = new HashMap<>();
    private final Map<Long, AtomicInteger> newUsages = new TreeMap<>();
    private final Map<Long, TreeSet<ContractKey>> updatedKeys = new TreeMap<>();
    private final Map<Long, TreeSet<ContractKey>> removedKeys = new TreeMap<>();
    private final Map<ContractKey, IterableContractValue> newMappings = new HashMap<>();

    private long totalKvPairs;

    @Inject
    public SizeLimitedStorage(
            final ContractStorageLimits usageLimits,
            final IterableStorageUpserter storageUpserter,
            final IterableStorageRemover storageRemover,
            final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
            final Supplier<VirtualMap<ContractKey, IterableContractValue>> storage) {
        this.storageRemover = storageRemover;
        this.storageUpserter = storageUpserter;
        this.usageLimits = usageLimits;
        this.accounts = accounts;
        this.storage = storage;
    }

    /** Clears all buffers and prepares for a new change-set of key/value pairs. */
    public void beginSession() {
        newUsages.clear();
        updatedKeys.clear();
        removedKeys.clear();
        newMappings.clear();
        newFirstKeys.clear();
        /* We will update this count as changes are buffered throughout the session. */
        totalKvPairs = storage.get().size();
    }

    /**
     * Validates that the pending key/value changes will not exceed any storage limits, and then
     * commits them to the underlying data source.
     *
     * @throws com.hedera.services.exceptions.InvalidTransactionException if a storage limit is
     *     exceeded
     */
    public void validateAndCommit() {
        validatePendingSizeChanges();

        commitPendingRemovals();
        commitPendingUpdates();

        if (!newUsages.isEmpty()) {
            usageLimits.refreshStorageSlots();
        }
    }

    /**
     * Records the new mapping counts and/or first storage keys of any contracts whose storage
     * changed in this session.
     *
     * @param accountsLedger the ledger to use to record the new counts
     */
    public void recordNewKvUsageTo(
            final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger) {
        if (newUsages.isEmpty()) {
            return;
        }
        newUsages.forEach(
                (contractNum, kvPairs) -> {
                    final var id = STATIC_PROPERTIES.scopedAccountWith(contractNum);
                    accountsLedger.set(id, NUM_CONTRACT_KV_PAIRS, kvPairs.get());
                    final var newFirstKey = newFirstKeys.get(contractNum);
                    accountsLedger.set(
                            id,
                            FIRST_CONTRACT_STORAGE_KEY,
                            newFirstKey == null ? null : newFirstKey.getKey());
                });
    }

    /**
     * Returns the requested storage value for the given contract, <i>taking into account</i> all
     * changes buffered so far in the session.
     *
     * @param id the contract of interest
     * @param key the key of the desired storage value
     * @return the value if it exists, zero if it does not
     */
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

    /**
     * Adds a pending key/value storage change to the current session, but <i>does not</i> commit it
     * to the underlying data source.
     *
     * @param id the contract of interest
     * @param key the key of the storage value to be changed
     * @param value the desired storage value
     */
    public void putStorage(final AccountID id, final UInt256 key, final UInt256 value) {
        final var contractKey = ContractKey.from(id, key);
        final var contractValue = virtualValueFrom(value);
        final var kvCountImpact =
                incorporateKvImpact(
                        contractKey,
                        contractValue,
                        updatedKeys,
                        removedKeys,
                        newMappings,
                        storage.get());
        if (kvCountImpact != 0) {
            newUsages
                    .computeIfAbsent(id.getAccountNum(), this::kvPairsLookup)
                    .getAndAdd(kvCountImpact);
            totalKvPairs += kvCountImpact;
        }
    }

    @FunctionalInterface
    public interface IterableStorageUpserter {
        ContractKey upsertMapping(
                ContractKey key,
                IterableContractValue value,
                ContractKey rootKey,
                IterableContractValue rootValue,
                VirtualMap<ContractKey, IterableContractValue> storage);
    }

    @FunctionalInterface
    public interface IterableStorageRemover {
        ContractKey removeMapping(
                ContractKey key,
                ContractKey rootKey,
                VirtualMap<ContractKey, IterableContractValue> storage);
    }

    private AtomicInteger kvPairsLookup(final Long num) {
        final var account = accounts.get().get(fromLong(num));
        if (account == null) {
            return new AtomicInteger(0);
        }
        return new AtomicInteger(account.getNumContractKvPairs());
    }

    private ContractKey firstKeyLookup(final Long num) {
        final var account = accounts.get().get(fromLong(num));
        if (account == null) {
            return null;
        }
        return account.getFirstContractStorageKey();
    }

    /**
     * Given as input,
     *
     * <ul>
     *   <li>Dynamic data structures that reflect the key/value changes in this session so far; and,
     *   <li>A {@link VirtualMap} data source for the key/value storage; and,
     *   <li>A new {@code key}/{@code value} mapping;
     * </ul>
     *
     * this method incorporates the new key/value mapping into the dynamic data structures, and
     * returns the impact that this change had on the total count of key/value pairs; <i>taking into
     * account</i> all changes buffered so far in the session.
     *
     * @param key the key of the storage value to be changed
     * @param value the desired storage value
     * @param updatedKeys the keys updated so far in this session
     * @param removedKeys the keys removed (that is, zeroed out) so far this session
     * @param newMappings the net new key/value mappings from this session
     * @param storage the data source for key/value storage
     * @return the impact this change has on total key/value pairs count
     */
    static int incorporateKvImpact(
            final ContractKey key,
            final IterableContractValue value,
            final Map<Long, TreeSet<ContractKey>> updatedKeys,
            final Map<Long, TreeSet<ContractKey>> removedKeys,
            final Map<ContractKey, IterableContractValue> newMappings,
            final VirtualMap<ContractKey, IterableContractValue> storage) {
        if (value == ZERO_VALUE) {
            return incorporateZeroingOf(key, updatedKeys, removedKeys, newMappings, storage);
        } else {
            return incorporateSettingOf(key, value, updatedKeys, removedKeys, newMappings, storage);
        }
    }

    private static int incorporateSettingOf(
            final ContractKey key,
            final IterableContractValue value,
            final Map<Long, TreeSet<ContractKey>> updatedKeys,
            final Map<Long, TreeSet<ContractKey>> removedKeys,
            final Map<ContractKey, IterableContractValue> newMappings,
            final VirtualMap<ContractKey, IterableContractValue> storage) {
        final Long contractId = key.getContractId();
        final var hasPendingUpdate = newMappings.containsKey(key);
        final var wasAlreadyPresent = storage.containsKey(key);
        // We always buffer the new mapping
        newMappings.put(key, value);
        if (hasPendingUpdate) {
            // If there was already a pending update, net storage usage hasn't changed
            return 0;
        } else {
            // Otherwise update the contract's change set
            updatedKeys.computeIfAbsent(contractId, treeSetFactory).add(key);
            // Was this key about to be removed?
            final var scopedRemovals = removedKeys.get(contractId);
            if (scopedRemovals != null && scopedRemovals.remove(key)) {
                // No longer, and net storage usage goes back up by 1
                return 1;
            }
            return wasAlreadyPresent ? 0 : 1;
        }
    }

    private static int incorporateZeroingOf(
            final ContractKey key,
            final Map<Long, TreeSet<ContractKey>> updatedKeys,
            final Map<Long, TreeSet<ContractKey>> removedKeys,
            final Map<ContractKey, IterableContractValue> newMappings,
            final VirtualMap<ContractKey, IterableContractValue> storage) {
        final Long contractId = key.getContractId();
        final var hasPendingUpdate = newMappings.containsKey(key);
        final var wasAlreadyPresent = storage.containsKey(key);
        if (hasPendingUpdate || wasAlreadyPresent) {
            if (hasPendingUpdate) {
                // We need to drop any pending update from our auxiliary data structures.
                final var scopedAdditions = updatedKeys.get(contractId);
                if (scopedAdditions == null) {
                    final var detailMsg =
                            "A new mapping "
                                    + key
                                    + " -> "
                                    + newMappings.get(key)
                                    + " did not belong to a key addition set";
                    throw new IllegalStateException(detailMsg);
                }
                scopedAdditions.remove(key);
                newMappings.remove(key);
            }
            if (wasAlreadyPresent) {
                // If there was no extant mapping for this key, no reason to explicitly remove it
                // when we commit.
                removedKeys.computeIfAbsent(key.getContractId(), treeSetFactory).add(key);
            }
            // But no matter what, relative to our existing change set, this removed one mapping.
            return -1;
        } else {
            // If this key didn't have a mapping or a pending change, it doesn't affect the size,
            // and there is also no reason to explicitly remove it when we commit
            return 0;
        }
    }

    private void validatePendingSizeChanges() {
        usageLimits.assertUsableTotalSlots(totalKvPairs);
        newUsages.forEach(
                (id, newKvPairs) -> usageLimits.assertUsableContractSlots(newKvPairs.get()));
    }

    private void commitPendingUpdates() {
        if (newMappings.isEmpty()) {
            return;
        }
        final var curStorage = storage.get();
        updatedKeys.forEach(
                (id, changeSet) -> {
                    IterableContractValue firstValue = null;
                    // We can't use newFirstKeys.computeIfAbsent() below, since that method treats
                    // an id->null mapping as
                    // ABSENT(!)---but if newFirstKeys contains an id->null mapping, it means that
                    // all the existing key/value
                    // pairs were removed for that contract, and we must ignore any existing first
                    // key in the accounts map
                    var firstKey =
                            newFirstKeys.containsKey(id)
                                    ? newFirstKeys.get(id)
                                    : firstKeyLookup(id);
                    for (final var changedKey : changeSet) {
                        final var newValue = newMappings.get(changedKey);
                        firstKey =
                                storageUpserter.upsertMapping(
                                        changedKey, newValue, firstKey, firstValue, curStorage);
                        firstValue = firstKey.equals(changedKey) ? newValue : null;
                    }
                    newFirstKeys.put(id, firstKey);
                });
    }

    private void commitPendingRemovals() {
        if (removedKeys.isEmpty()) {
            return;
        }
        final var curStorage = storage.get();
        removedKeys.forEach(
                (id, zeroedOut) -> {
                    var firstKey = firstKeyLookup(id);
                    for (final var removedKey : zeroedOut) {
                        firstKey = storageRemover.removeMapping(removedKey, firstKey, curStorage);
                    }
                    newFirstKeys.put(id, firstKey);
                });
    }

    static Function<Long, TreeSet<ContractKey>> treeSetFactory = ignore -> new TreeSet<>();

    private static IterableContractValue virtualValueFrom(final UInt256 evmWord) {
        return evmWord.isZero() ? ZERO_VALUE : IterableContractValue.from(evmWord);
    }

    // --- Only used by unit tests ---
    @VisibleForTesting
    int usageSoFar(final AccountID id) {
        return newUsages.computeIfAbsent(id.getAccountNum(), this::kvPairsLookup).get();
    }

    @VisibleForTesting
    Map<Long, AtomicInteger> getNewUsages() {
        return newUsages;
    }

    @VisibleForTesting
    Map<Long, ContractKey> getNewFirstKeys() {
        return newFirstKeys;
    }

    @VisibleForTesting
    Map<Long, TreeSet<ContractKey>> getUpdatedKeys() {
        return updatedKeys;
    }

    @VisibleForTesting
    Map<Long, TreeSet<ContractKey>> getRemovedKeys() {
        return removedKeys;
    }

    @VisibleForTesting
    Map<ContractKey, IterableContractValue> getNewMappings() {
        return newMappings;
    }
}

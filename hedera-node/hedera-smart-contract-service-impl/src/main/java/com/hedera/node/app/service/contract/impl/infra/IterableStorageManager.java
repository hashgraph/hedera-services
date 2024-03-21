/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.infra;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleHederaOperations;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater.Enhancement;
import com.hedera.node.app.service.contract.impl.state.ContractStateStore;
import com.hedera.node.app.service.contract.impl.state.StorageAccess.StorageAccessType;
import com.hedera.node.app.service.contract.impl.state.StorageAccesses;
import com.hedera.node.app.service.contract.impl.state.StorageSizeChange;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Provides the logic for maintaining per-contract linked lists of owned storage, and keeping the
 * number of slots used per contract up to date; i.e., the logic for keeping per-contract storage
 * "legible" even though all slots are stored in a single map.
 */
@Singleton
public class IterableStorageManager {
    private static final Logger log = LogManager.getLogger(IterableStorageManager.class);

    @Inject
    public IterableStorageManager() {
        // Dagger2
    }

    /**
     * Given a writable storage K/V state and the pending changes to storage values and sizes made in this
     * scope, "rewrites" the pending changes to maintain per-contract linked lists of owned storage. (The
     * linked lists are used to purge all the contract's storage from state when it expires.)
     *
     * <p>Besides updating the first keys of these linked lists in the scoped accounts, also updates the
     * slots used per contract via
     * {@link HandleHederaOperations#updateStorageMetadata(ContractID, Bytes, int)}.
     *
     * @param enhancement the enhancement for the current transaction
     * @param allAccesses the pending changes to storage values
     * @param allSizeChanges the pending changes to storage sizes
     * @param store the writable state store
     */
    public void persistChanges(
            @NonNull final Enhancement enhancement,
            @NonNull final List<StorageAccesses> allAccesses,
            @NonNull final List<StorageSizeChange> allSizeChanges,
            @NonNull final ContractStateStore store) {
        // map to store the first storage key for each contract
        final Map<ContractID, Bytes> firstKeys = new HashMap<>();

        // Adjust the storage linked lists for each contract
        allAccesses.forEach(contractAccesses -> contractAccesses.accesses().forEach(access -> {
            if (access.isUpdate()) {
                var firstContractKey = contractFirstKeyOf(enhancement, contractAccesses.contractID());

                switch (StorageAccessType.getAccessType(access)) {
                    case REMOVAL -> firstContractKey = removeAccessedValue(
                            store, firstContractKey, contractAccesses.contractID(), tuweniToPbjBytes(access.key()));
                    case INSERTION -> firstContractKey = insertAccessedValue(
                            store,
                            firstContractKey,
                            tuweniToPbjBytes(access.writtenValue()),
                            contractAccesses.contractID(),
                            tuweniToPbjBytes(access.key()));
                }
                firstKeys.put(contractAccesses.contractID(), firstContractKey);
            }
        }));

        // Update contract metadata with the net change in slots used
        allSizeChanges.forEach(change -> {
            if (change.netChange() != 0) {
                enhancement
                        .operations()
                        .updateStorageMetadata(
                                change.contractID(),
                                firstKeys.getOrDefault(change.contractID(), Bytes.EMPTY),
                                change.netChange());
            }
        });
    }

    /**
     * Returns the first storage key for the contract or Bytes.Empty if none exists.
     * @param enhancement the enhancement for the current transaction
     * @param contractNumber the contract number
     * @return the first storage key for the contract or null if none exists.
     */
    @NonNull
    private Bytes contractFirstKeyOf(@NonNull final Enhancement enhancement, ContractID contractID) {
        final var account = enhancement.nativeOperations().getAccount(contractID);
        return account != null && account.firstContractStorageKey() != null
                ? account.firstContractStorageKey()
                : Bytes.EMPTY;
    }

    /**
     * Removes the given key from the slot storage and from the linked list of storage for the given contract, and removes the
     *
     * @param store Contract storage store
     * @param firstContractKey The first key in the linked list of storage for the given contract
     * @param contractID The contract id under consideration
     * @param key The slot key to remove
     * @return the new first key in the linked list of storage for the given contract
     */
    @NonNull
    private Bytes removeAccessedValue(
            @NonNull final ContractStateStore store,
            @NonNull final Bytes firstContractKey,
            ContractID contractID,
            @NonNull final Bytes key) {
        try {
            Objects.requireNonNull(store);
            Objects.requireNonNull(key);
            final var slotKey = newSlotKeyFor(contractID, key);
            final var slotValue = slotValueFor(store, false, slotKey, "Missing key ");
            final var nextKey = slotValue.nextKey();
            final var prevKey = slotValue.previousKey();

            if (!nextKey.equals(Bytes.EMPTY)) {
                // Look up the next slot value
                final var nextSlotKey = newSlotKeyFor(contractID, nextKey);
                final var nextValue = slotValueFor(store, true, nextSlotKey, "Missing next key ");

                // Create new next value and put into the store
                final var newNextValue =
                        nextValue.copyBuilder().previousKey(prevKey).build();
                store.putSlot(nextSlotKey, newNextValue);
            }
            if (!prevKey.equals(Bytes.EMPTY)) {
                // Look up the previous slot value
                final var prevSlotKey = newSlotKeyFor(contractID, prevKey);
                final var prevValue = slotValueFor(store, true, prevSlotKey, "Missing previous key ");

                // Create new previous value and put into the store
                final var newPrevValue =
                        prevValue.copyBuilder().nextKey(nextKey).build();
                store.putSlot(prevSlotKey, newPrevValue);
            }
            store.removeSlot(slotKey);
            return key.equals(firstContractKey) ? slotValue.nextKey() : firstContractKey;
        } catch (Exception irreparable) {
            log.error(
                    "Failed link management when removing {}; will be unable to"
                            + " expire all slots for this contract",
                    key,
                    irreparable);
        }
        return firstContractKey;
    }

    /**
     * Inserts the given key into the slot storage and into the linked list of storage for the given contract.
     *
     * @param store Contract storage store
     * @param firstContractKey The first key in the linked list of storage for the given contract
     * @param newValue The new value for the slot
     * @param contractID The contract id under consideration
     * @param newKey The slot key to insert
     * @return the new first key in the linked list of storage for the given contract
     */
    @NonNull
    private Bytes insertAccessedValue(
            @NonNull final ContractStateStore store,
            @NonNull final Bytes firstContractKey,
            @NonNull final Bytes newValue,
            ContractID contractID,
            @NonNull final Bytes newKey) {
        try {
            Objects.requireNonNull(store);
            Objects.requireNonNull(newValue);
            Objects.requireNonNull(newKey);
            // Create new slot key and value and put into the store
            final var newSlotKey = newSlotKeyFor(contractID, newKey);
            final var newSlotValue = SlotValue.newBuilder()
                    .value(newValue)
                    .previousKey(Bytes.EMPTY)
                    .nextKey(firstContractKey)
                    .build();
            store.putSlot(newSlotKey, newSlotValue);
            return newKey;
        } catch (Exception irreparable) {
            log.error("Failed link management when inserting {}", newKey, irreparable);
        }
        return firstContractKey;
    }

    @NonNull
    private SlotKey newSlotKeyFor(ContractID contractNumber, @NonNull final Bytes key) {
        return new SlotKey(contractNumber, key);
    }

    @NonNull
    private SlotValue slotValueFor(
            @NonNull final ContractStateStore store,
            final boolean forModify,
            @NonNull final SlotKey slotKey,
            @NonNull final String msgOnError) {
        return forModify
                ? Objects.requireNonNull(store.getSlotValueForModify(slotKey), () -> msgOnError + slotKey.key())
                : Objects.requireNonNull(store.getSlotValue(slotKey), () -> msgOnError + slotKey.key());
    }
}

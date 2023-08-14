/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleHederaOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaOperations;
import com.hedera.node.app.service.contract.impl.state.ContractStateStore;
import com.hedera.node.app.service.contract.impl.state.StorageAccesses;
import com.hedera.node.app.service.contract.impl.state.StorageSizeChange;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Provides the logic for maintaining per-contract linked lists of owned storage, and keeping the
 * number of slots used per contract up to date; i.e., the logic for keeping per-contract storage
 * "legible" even though all slots are stored in a single map.
 */
@Singleton
public class IterableStorageManager {
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
     * {@link HandleHederaOperations#updateStorageMetadata(long, Bytes, int)}.
     *
     * @param hederaOperations the scope of the current transaction
     * @param allAccesses the pending changes to storage values
     * @param allSizeChanges the pending changes to storage sizes
     * @param store the writable state store
     */
    public void persistChanges(
            @NonNull final HederaOperations hederaOperations,
            @NonNull final List<StorageAccesses> allAccesses,
            @NonNull final List<StorageSizeChange> allSizeChanges,
            @NonNull final ContractStateStore store) {
        // TODO - include storage linked list management before performance testing

        // Remove all zeroed-out slots from the linked lists
        allAccesses.forEach(contractAccesses -> contractAccesses.accesses().forEach(access -> {
            if (access.isRemoval()) {
                store.removeSlot(new SlotKey(contractAccesses.contractNumber(), tuweniToPbjBytes(access.key())));
            }
        }));
        // Update contract metadata with the net change in slots used
        allSizeChanges.forEach(change -> {
            if (change.netChange() != 0) {
                hederaOperations.updateStorageMetadata(change.contractNumber(), Bytes.EMPTY, change.netChange());
            }
        });
    }
}

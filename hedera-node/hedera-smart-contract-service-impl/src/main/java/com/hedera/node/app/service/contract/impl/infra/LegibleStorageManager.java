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

import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.node.app.service.contract.impl.state.StorageChanges;
import com.hedera.node.app.service.contract.impl.state.StorageSizeChange;
import com.hedera.node.app.spi.meta.bni.Scope;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import javax.inject.Singleton;

/**
 * Provides the logic for maintaining per-contract linked lists of owned storage, and keeping the
 * number of slots used per contract up to date; i.e., the logic for keeping per-contract storage
 * "legible" even though all slots are stored in a single map.
 */
@Singleton
public class LegibleStorageManager {
    @Singleton
    public LegibleStorageManager() {}

    /**
     * Given a writable storage K/V state and the pending changes to storage values and sizes made in this
     * scope, "rewrites" the pending changes to maintain per-contract linked lists of owned storage. (The
     * linked lists are used to purge all the contract's storage from state when it expires.)
     *
     * <p>Besides updating the first keys of these linked lists in the scoped accounts, also updates the
     * slots used per contract via
     * {@link com.hedera.node.app.spi.meta.bni.Dispatch#updateStorageMetadata(long, Bytes, int)}.
     *
     * @param scope the scope of the current transaction
     * @param changes the pending changes to storage values
     * @param sizeChanges the pending changes to storage sizes
     * @param storage the writable storage K/V state
     */
    public void rewrite(
            @NonNull final Scope scope,
            @NonNull final List<StorageChanges> changes,
            @NonNull final List<StorageSizeChange> sizeChanges,
            @NonNull final WritableKVState<SlotKey, SlotValue> storage) {
        throw new AssertionError("Not implemented");
    }
}

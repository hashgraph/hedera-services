package com.hedera.node.app.service.contract.impl.infra;

import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.node.app.service.contract.impl.state.StorageChanges;
import com.hedera.node.app.service.contract.impl.state.StorageSizeChange;
import com.hedera.node.app.spi.meta.bni.Scope;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Singleton;
import java.util.List;

@Singleton
public class StorageLinkedLists {
    @Singleton
    public StorageLinkedLists() {
    }

    /**
     * Given a writable storage K/V state and the pending changes to storage values and sizes made in this
     * scope, "rewrites" the pending changes to maintain per-contract linked lists of owned storage. (The
     * linked lists are used to purge all the contract's storage from state when it expires.)
     *
     * <p>Besides updating the first keys of these linked lists in the scoped accounts, also updates the
     * slots used per contract via
     * {@link com.hedera.node.app.spi.meta.bni.Dispatch#updateStorageMetadata(long, Bytes, int)}.
     *
     * @param scope
     * @param changes
     * @param sizeChanges
     * @param storage
     */
    public void rewritePendingChanges(
            @NonNull final Scope scope,
            @NonNull final List<StorageChanges> changes,
            @NonNull final List<StorageSizeChange> sizeChanges,
            @NonNull final WritableKVState<SlotKey, SlotValue> storage) {
        throw new AssertionError("Not implemented");
    }
}

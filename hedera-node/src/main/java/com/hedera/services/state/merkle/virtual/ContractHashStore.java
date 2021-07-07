package com.hedera.services.state.merkle.virtual;

import com.hedera.services.state.merkle.virtual.persistence.FCSlotIndex;
import com.hedera.services.state.merkle.virtual.persistence.FCVirtualMapHashStore;
import com.hedera.services.state.merkle.virtual.persistence.fcmmap.*;
import com.hedera.services.store.models.Id;
import com.swirlds.common.crypto.Hash;
import com.swirlds.fcmap.FCHashStore;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import org.eclipse.collections.impl.set.mutable.primitive.LongHashSet;

import java.io.IOException;
import java.nio.file.Path;

public class ContractHashStore implements FCHashStore {
    private final Id contractId;
    private final FCVirtualMapHashStore<ContractPath> dataStore;
    private FCSlotIndex<ContractPath> index;

    public ContractHashStore(Id contractId) {
        this(contractId,false,false);
    }

    public ContractHashStore(Id contractId, boolean inMemoryIndex, boolean inMemoryStore) {
        this.contractId = contractId;

        try {
            this.index = inMemoryIndex ?
                    new FCSlotIndexUsingFCHashMap<ContractPath>() :
                    new FCSlotIndexUsingMemMapFile<ContractPath>(
                            Path.of("data/contract-storage/hash-index"),
                            "hash-index",
                            1024*1024,
                            32,
                            ContractPath.SERIALIZED_SIZE,
                            16,
                            20,
                            8);

            this.dataStore = new FCVirtualMapHashStoreImpl<ContractPath>(
                    Path.of("data/contract-storage"),
                    8,
                    ContractPath.SERIALIZED_SIZE,
                    index,
                    inMemoryStore ? InMemorySlotStore::new : MemMapSlotStore::new);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private ContractHashStore(ContractHashStore copyFrom) {
        this.contractId = copyFrom.contractId;
        this.dataStore = copyFrom.dataStore.copy();
    }

    @Override
    public Hash loadHash(long path) {
        try {
            return dataStore.loadHash(new ContractPath(contractId, path));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void saveHash(long path, Hash hash) {
        try {
            dataStore.saveHash(new ContractPath(contractId, path), hash);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deleteHash(long path) {
        try {
            dataStore.deleteHash(new ContractPath(contractId, path));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public ContractHashStore copy() {
        return new ContractHashStore(this);
    }

    @Override
    public void release() {
        dataStore.release();
    }

    /**
     * Debug print
     */
    public void printMutationQueueStats() {
        if (index instanceof FCSlotIndexUsingMemMapFile) {
            ((FCSlotIndexUsingMemMapFile)index).printMutationQueueStats();
        }
    }
}

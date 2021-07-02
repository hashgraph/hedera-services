package com.hedera.services.state.merkle.virtual;

import com.hedera.services.state.merkle.virtual.persistence.FCVirtualMapHashStore;
import com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCSlotIndexUsingMemMapFile;
import com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCVirtualMapHashStoreImpl;
import com.hedera.services.state.merkle.virtual.persistence.fcmmap.MemMapSlotStore;
import com.hedera.services.store.models.Id;
import com.swirlds.common.crypto.Hash;
import com.swirlds.fcmap.FCHashStore;

import java.io.IOException;
import java.nio.file.Path;

public class ContractHashStore implements FCHashStore {
    private final Id contractId;
    private final FCVirtualMapHashStore<ContractPath> dataStore;

    public ContractHashStore(Id contractId) {
        this.contractId = contractId;

        try {
            final var index = new FCSlotIndexUsingMemMapFile<ContractPath>(
                    Path.of("data/contract-storage/hash-index"),
                    "hash-index",
                    1024*1024,
                    32,
                    ContractPath.SERIALIZED_SIZE,
                    16,
                    20,
                    8);

            this.dataStore = new FCVirtualMapHashStoreImpl<>(
                    Path.of("data/contract-storage"),
                    8,
                    ContractPath.SERIALIZED_SIZE,
                    index,
                    MemMapSlotStore::new);
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
}

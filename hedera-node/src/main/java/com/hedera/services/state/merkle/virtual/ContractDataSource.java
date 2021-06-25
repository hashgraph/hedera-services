package com.hedera.services.state.merkle.virtual;

import com.hedera.services.state.merkle.virtual.persistence.FCVirtualMapDataStore;
import com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCSlotIndexUsingFCHashMap;
import com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCVirtualMapDataStoreImpl;
import com.hedera.services.state.merkle.virtual.persistence.fcmmap.MemMapSlotStore;
import com.hedera.services.store.models.Id;
import com.swirlds.common.crypto.Hash;
import com.swirlds.fcmap.FCDataSource;
import com.swirlds.fcmap.FCVirtualRecord;

import java.io.IOException;
import java.nio.file.Path;

public class ContractDataSource implements FCDataSource<ContractUint256, ContractUint256> {
    private final Id contractId;
    private final FCVirtualMapDataStore<ContractPath, ContractKey, ContractPath, ContractUint256> dataStore;

    public ContractDataSource(Id contractId) {
        this.contractId = contractId;
        this.dataStore = new FCVirtualMapDataStoreImpl<>(
                Path.of("data/contract-storage"),
                256,
                Long.BYTES+(Long.BYTES*3), // Count it up...
                (256/8) + (Long.BYTES*3), // Count it up, gotta include overhead
                Long.BYTES+(Long.BYTES*3), // Count it up...
                (256/8), // Count it up... overhead?
                FCSlotIndexUsingFCHashMap::new,
                FCSlotIndexUsingFCHashMap::new,
                FCSlotIndexUsingFCHashMap::new,
                ContractUint256::new,
                MemMapSlotStore::new);

        try {
            this.dataStore.open();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
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
        dataStore.deleteHash(new ContractPath(contractId, path));
    }

    @Override
    public long leafCount() {
        return dataStore.leafCount();
    }

    @Override
    public FCVirtualRecord<ContractUint256, ContractUint256> loadLeafRecordByPath(long l) {
        // TODO
        return null;
    }

    @Override
    public Long loadLeafPathByKey(ContractUint256 contractUint256) {
        // TODO
        return null;
    }

    @Override
    public ContractUint256 loadLeafValueByKey(ContractUint256 contractUint256) {
        // TODO
        return null;
    }

    @Override
    public void saveLeaf(ContractUint256 key, long path, ContractUint256 value) {
        try {
            dataStore.saveLeaf(new ContractKey(contractId, key), new ContractPath(contractId, path), value);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deleteLeaf(ContractUint256 key, long path) {
        dataStore.deleteLeaf(new ContractKey(contractId, key), new ContractPath(contractId, path));
    }

    @Override
    public void updatePath(long oldPath, long newPath) {
        // TODO
    }

    @Override
    public FCDataSource<ContractUint256, ContractUint256> copy() {
        // TODO
        return null;
    }

    @Override
    public void release() {
        // TODO
    }
}

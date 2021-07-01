package com.hedera.services.state.merkle.virtual;

import com.hedera.services.state.merkle.virtual.persistence.FCVirtualMapLeafStore;
import com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCSlotIndexUsingFCHashMap;
import com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCSlotIndexUsingMemMapFile;
import com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCVirtualMapLeafStoreImpl;
import com.hedera.services.state.merkle.virtual.persistence.fcmmap.MemMapSlotStore;
import com.hedera.services.store.models.Id;
import com.swirlds.fcmap.FCLeafStore;
import com.swirlds.fcmap.FCVirtualRecord;

import java.io.IOException;
import java.nio.file.Path;

public class ContractLeafStore implements FCLeafStore<ContractUint256, ContractUint256> {
    private final Id contractId;
    private final FCVirtualMapLeafStore<ContractKey, ContractPath, ContractUint256> dataStore;

    public ContractLeafStore(Id contractId) {
        this.contractId = contractId;
        try {
            final var leafPathIndex = new FCSlotIndexUsingMemMapFile<ContractPath>(
                    Path.of("data/contract-storage/leaf-path-index"),
                    "leaf-path-index",
                    256*1024,
                    1024,
                    ContractPath.SERIALIZED_SIZE,
                    16,
                    20);

            final var leafKeyIndex = new FCSlotIndexUsingMemMapFile<ContractKey>(
                    Path.of("data/contract-storage/leaf-key-index"),
                    "leaf-key-index",
                    256*1024,
                    1024,
                    ContractKey.SERIALIZED_SIZE,
                    256,
                    20);

            this.dataStore = new FCVirtualMapLeafStoreImpl<>(
                    Path.of("data/contract-storage"),
                    32,
                    ContractKey.SERIALIZED_SIZE,
                    ContractPath.SERIALIZED_SIZE,
                    ContractUint256.SERIALIZED_SIZE,
                    leafPathIndex,
                    leafKeyIndex,
                    ContractKey::new,
                    ContractPath::new,
                    ContractUint256::new,
                    MemMapSlotStore::new);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private ContractLeafStore(ContractLeafStore copyFrom) {
        this.contractId = copyFrom.contractId;
        this.dataStore = copyFrom.dataStore.copy();
    }

    @Override
    public long leafCount() {
        return dataStore.leafCount();
    }

    @Override
    public FCVirtualRecord<ContractUint256, ContractUint256> loadLeafRecordByPath(long l) {
        try {
            var record = dataStore.loadLeafRecordByPath(new ContractPath(contractId, l));
            return record == null ? null : new FCVirtualRecord<>(record.getKey().getKey(),record.getValue());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Long loadLeafPathByKey(ContractUint256 contractUint256) {
        try {
            ContractPath contractPath = dataStore.loadLeafPathByKey(new ContractKey(contractId, contractUint256));
            return contractPath == null ? null : contractPath.getPath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public ContractUint256 loadLeafValueByKey(ContractUint256 contractUint256) {
        try {
            return dataStore.loadLeafValueByKey(new ContractKey(contractId,contractUint256));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
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
        try {
            dataStore.deleteLeaf(new ContractKey(contractId, key), new ContractPath(contractId, path));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void updatePath(long oldPath, long newPath) {
        try {
            dataStore.updateLeafPath(new ContractPath(contractId, oldPath), new ContractPath(contractId, newPath));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public FCLeafStore<ContractUint256, ContractUint256> copy() {
        return new ContractLeafStore(this);
    }

    @Override
    public void release() {
        dataStore.release();
    }
}

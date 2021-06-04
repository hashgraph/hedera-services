package com.hedera.services.state.merkle.virtual.persistence.mmap;

import com.hedera.services.state.merkle.virtual.Account;
import com.hedera.services.state.merkle.virtual.VirtualKey;
import com.hedera.services.state.merkle.virtual.VirtualValue;
import com.hedera.services.state.merkle.virtual.persistence.VirtualDataSource;
import com.hedera.services.state.merkle.virtual.persistence.VirtualRecord;
import com.swirlds.common.crypto.Hash;

import java.io.IOException;
import java.util.Objects;

public class MemMapDataSource implements VirtualDataSource {
    private VirtualMapDataStore store;
    private Account account;

    public MemMapDataSource(VirtualMapDataStore store, Account account) {
        this.store = Objects.requireNonNull(store);
        this.account = Objects.requireNonNull(account);
    }

    @Override
    public Hash loadParentHash(long parentPath) {
        return store.loadParentHash(account, parentPath);
    }

    @Override
    public VirtualRecord loadLeaf(VirtualKey leafKey) {
        return store.loadLeaf(account, leafKey);
    }

    @Override
    public VirtualRecord loadLeaf(long leafPath) {
        return store.loadLeaf(account, leafPath);
    }

    @Override
    public VirtualValue getLeafValue(VirtualKey leafKey) {
        return store.get(account, leafKey);
    }

    @Override
    public void saveParent(long parentPath, Hash hash) {
        store.save(account, parentPath, hash);
    }

    @Override
    public void saveLeaf(VirtualRecord leaf) {
        store.save(account, leaf);
    }

    @Override
    public void deleteParent(long parentPath) {
        store.delete(account, parentPath);
    }

    @Override
    public void deleteLeaf(VirtualRecord leaf) {
        store.delete(account, leaf);
    }

    @Override
    public void writeLastLeafPath(long path) {
        store.save(account, (byte)1, path);
    }

    @Override
    public long getLastLeafPath() {
        return store.load(account, (byte)1);
    }

    @Override
    public void writeFirstLeafPath(long path) {
        store.save(account, (byte)2, path);
    }

    @Override
    public long getFirstLeafPath() {
        return store.load(account, (byte)2);
    }

    @Override
    public void close() throws IOException {
        store.close();
    }
}

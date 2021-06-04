package com.hedera.services.state.merkle.virtual.persistence.mmap;

import com.hedera.services.state.merkle.virtual.Account;
import com.hedera.services.state.merkle.virtual.VirtualKey;
import com.hedera.services.state.merkle.virtual.VirtualValue;
import com.hedera.services.state.merkle.virtual.persistence.VirtualDataSource;
import com.hedera.services.state.merkle.virtual.tree.VirtualTreeInternal;
import com.hedera.services.state.merkle.virtual.tree.VirtualTreeLeaf;
import com.hedera.services.state.merkle.virtual.tree.VirtualTreePath;

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
    public VirtualTreeInternal load(VirtualTreePath parentPath) {
        return store.loadParent(account, parentPath);
    }

    @Override
    public VirtualTreeLeaf load(VirtualKey leafKey) {
        return store.loadLeaf(account, leafKey);
    }

    @Override
    public VirtualValue get(VirtualKey leafKey) {
        return store.get(account, leafKey);
    }

    @Override
    public void save(VirtualTreeInternal parent) {
        store.save(account, parent);
    }

    @Override
    public void save(VirtualTreeLeaf leaf) {
        store.save(account, leaf);
    }

    @Override
    public void delete(VirtualTreeInternal parent) {
        store.delete(account, parent);
    }

    @Override
    public void delete(VirtualTreeLeaf leaf) {
        store.delete(account, leaf);
    }

    @Override
    public void writeLastLeafPath(VirtualTreePath path) {
        store.save(account, (byte)1, path);
    }

    @Override
    public VirtualTreePath getLastLeafPath() {
        return store.load(account, (byte)1);
    }

    @Override
    public void writeFirstLeafPath(VirtualTreePath path) {
        store.save(account, (byte)2, path);
    }

    @Override
    public VirtualTreePath getFirstLeafPath() {
        return store.load(account, (byte)2);
    }

    @Override
    public void close() throws IOException {
        store.close();
    }
}

package com.hedera.services.state.merkle.virtual;

import com.hedera.services.state.merkle.virtual.persistence.VirtualDataSource;
import com.hedera.services.state.merkle.virtual.tree.VirtualTreeInternal;
import com.hedera.services.state.merkle.virtual.tree.VirtualTreeLeaf;
import com.hedera.services.state.merkle.virtual.tree.VirtualTreePath;
import com.swirlds.common.crypto.Hash;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;


public class VirtualMapTest {

    @Test
    public void nullDataSourceThrows() {
        assertThrows(NullPointerException.class, () -> new VirtualMap(null));
    }

    @Test
    public void lookupNonExistent() {
        final var ds = new InMemoryDataSource();
        final var v = new VirtualMap(ds);

        final var key = asKey("DoesNotExist");
        Assertions.assertNull(v.getValue(key));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17})
    public void putAndGetItems(int numItems) {
        final var ds = new InMemoryDataSource();
        final var v = new VirtualMap(ds);

        for (int i=0; i<numItems; i++) {
            final var key = asKey(i);
            final var value = asValue(i);
            v.putValue(key, value);
        }

        for (int i=0; i<numItems; i++) {
            Assertions.assertEquals(asValue(i), v.getValue(asKey(i)));
        }
    }

    @Test
    public void replaceItem() {
        final var ds = new InMemoryDataSource();
        final var v = new VirtualMap(ds);

        final var key = asKey("fruit");
        final var value = asValue("apple");
        final var value2 = asValue("banana");

        v.putValue(key, value);
        v.putValue(key, value2);
        Assertions.assertEquals(value2, v.getValue(key));
    }

    @Test
    public void addManyItemsAndFindThemAll() {
        final var ds = new InMemoryDataSource();
        final var v = new VirtualMap(ds);

        final var expected = new HashMap<VirtualKey, VirtualValue>();
        for (int i=0; i<1_000_000; i++) {
            final var key = asKey(i + "");
            final var value = asValue((i + 100_000_000) + "");
            expected.put(key, value);
            v.putValue(key, value);
        }

        expected.forEach((key, value) -> Assertions.assertEquals(value, v.getValue(key)));
    }

    @Test
    public void addManyItemsUsingManyMapsAndReadThemAllFromOne() {
        final var ds = new InMemoryDataSource();
        var v = new VirtualMap(ds);

        final var expected = new HashMap<VirtualKey, VirtualValue>();
        for (int i=0; i<1_000_000; i++) {
            if (i > 0 && i % 15 == 0) {
                v.commit();
                v = new VirtualMap(ds);
            }
            final var key = asKey(i + "");
            final var value = asValue((i + 100_000_000) + "");
            expected.put(key, value);
            v.putValue(key, value);
//            System.out.println(v.getAsciiArt());
        }

        final var fv = v;
        expected.forEach((key, value) -> Assertions.assertEquals(value, fv.getValue(key)));
    }

    @Test
    public void quickAndDirty() {
//        final var ds = new InMemoryDataSource();
//        VirtualMap map = new VirtualMap<>();
//        map.setDataSource(ds);
//        for (int i=0; i<1_000_000; i++) {
//            final var key = asKey(i);
//            final var value = asValue(i);
//            map.putValue(key, value);
////            System.out.println(map.getAsciiArt());
//        }
//
//        Random rand = new Random();
//        for (int idx=0; idx<10_000; idx++) {
//            map = new VirtualMap();
//            map.setDataSource(ds);
//            for (int j = 0; j < 100; j++) {
//                final var i = rand.nextInt(1_000_000);
//                map.putValue(asKey(i), asValue(i + 1_000_000));
//            }
//        }
    }

    private VirtualKey asKey(String txt) {
        return new VirtualKey(Arrays.copyOf(txt.getBytes(), 32));
    }

    private VirtualKey asKey(int index) {
        return new VirtualKey(Arrays.copyOf(("key" + index).getBytes(), 32));
    }

    private VirtualValue asValue(String txt) {
        return new VirtualValue(Arrays.copyOf(txt.getBytes(), 32));
    }

    private VirtualValue asValue(int index) {
        return new VirtualValue(Arrays.copyOf(("val" + index).getBytes(), 32));
    }

    // TODO Delete items... how to do that? Set them to null I guess...
    // TODO Test hashing the tree

//    private EthValue asBlock(String s) {
//        return new EthValue(Arrays.copyOf(s.getBytes(StandardCharsets.UTF_8), 32));
//    }

    // Simple implementation for testing purposes
    private static final class LeafRecord {
        private VirtualKey key;
        private Hash hash;
        private VirtualTreePath path;
        private VirtualValue value;
    }

    private static final class InMemoryDataSource implements VirtualDataSource {
        private Map<VirtualKey, LeafRecord> leaves = new HashMap<>();
        private Map<VirtualTreePath, Hash> parents = new HashMap<>();
        private VirtualTreePath firstLeafPath;
        private VirtualTreePath lastLeafPath;
        private boolean closed = false;

        @Override
        public VirtualTreeInternal load(VirtualTreePath parentPath) {
            final var hash = parents.get(parentPath);
            return hash == null ? null : new VirtualTreeInternal(hash, parentPath);
        }

        @Override
        public VirtualTreeLeaf load(VirtualKey leafKey) {
            final var rec = leaves.get(leafKey);
            return rec == null ? null : new VirtualTreeLeaf(rec.hash, rec.path, rec.key, rec.value);
        }

        @Override
        public void save(VirtualTreeInternal parent) {
            parents.put(parent.getPath(), parent.hash());
        }

        @Override
        public void save(VirtualTreeLeaf leaf) {
            final var rec = new LeafRecord();
            rec.hash = leaf.hash();
            rec.key = leaf.getKey();
            rec.path = leaf.getPath();
            rec.value = leaf.getData();
            leaves.put(leaf.getKey(), rec);
        }

        @Override
        public void delete(VirtualTreeInternal parent) {
            // TODO potentially dangerous, what if a new parent has been put here??
            parents.remove(parent.getPath());
        }

        @Override
        public void delete(VirtualTreeLeaf leaf) {
            leaves.remove(leaf.getKey()); // Always safe.
        }

        @Override
        public void writeLastLeafPath(VirtualTreePath path) {
            this.lastLeafPath = path;
        }

        @Override
        public VirtualTreePath getLastLeafPath() {
            return lastLeafPath;
        }

        @Override
        public void writeFirstLeafPath(VirtualTreePath path) {
            this.firstLeafPath = path;
        }

        @Override
        public VirtualTreePath getFirstLeafPath() {
            return firstLeafPath;
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                throw new IOException("Already closed");
            }
            closed = true;
        }
    }
}

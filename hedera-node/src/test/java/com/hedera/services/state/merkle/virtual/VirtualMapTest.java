package com.hedera.services.state.merkle.virtual;

import com.hedera.services.state.merkle.virtual.persistence.VirtualDataSource;
import com.hedera.services.state.merkle.virtual.persistence.VirtualRecord;
import com.hedera.services.state.merkle.virtual.persistence.mmap.MemMapDataSource;
import com.hedera.services.state.merkle.virtual.persistence.mmap.VirtualMapDataStore;
import com.swirlds.common.FCMKey;
import com.swirlds.common.FCMValue;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.engine.CryptoEngine;
import com.swirlds.common.merkle.utility.MerkleLong;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static com.hedera.services.state.merkle.virtual.VirtualTreePath.INVALID_PATH;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

        for (int i=0; i<1_000_000; i++) {
            final var key = asKey(i + "");
            final var value = asValue((i + 1_000_000) + "");
            v.putValue(key, value);
        }
        v.commit();

        final var rand = new Random();

        v = v.copy();
        final var expected = new HashMap<VirtualKey, VirtualValue>();
        for (int i=0; i<1_000_000; i++) {
            if (i > 0 && i % 25 == 0) {
                v.commit();
                v = v.copy();
            }

            final var r = rand.nextInt(1_000_000);
            final var key = asKey(r + "");
            final var value = asValue((r + 1_000_000) + "");
            expected.put(key, value);
            v.putValue(key, value);
        }

        final var fv = v;
        expected.forEach((key, value) -> Assertions.assertEquals(value, fv.getValue(key)));
    }

    @Test
    public void mmapBackend() {
        final var storeDir = new File("./store").toPath();
        if (Files.exists(storeDir)) {
            try {
                //noinspection ResultOfMethodCallIgnored
                Files.walk(storeDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        final var store = new VirtualMapDataStore(storeDir, 32, 32);
        store.open();
        final var ds = new MemMapDataSource(store, new Account(0, 0, 100));
        var v = new VirtualMap(ds);

        final var expected = new HashMap<VirtualKey, VirtualValue>();
        for (int i=0; i<1_000_000; i++) {
            if (i > 0 && i % 15 == 0) {
                v.commit();
                v = new VirtualMap(ds);
            }
            final var key = asKey(i);
            final var value = asValue((i + 100_000_000));
            expected.put(key, value);
            v.putValue(key, value);
        }

        final var fv = v;
        expected.forEach((key, value) -> Assertions.assertEquals(value, fv.getValue(key)));
    }

//    @Test
//    public void hashingTest() {
//        final var ds = new InMemoryDataSource();
//        var v = new VirtualMap(ds);
//        var fcmap = new FCMap<MerkleLong, MerkleLong>();
//        var engine = new CryptoEngine();
//
//        for (int i=0; i<1_000_000; i++) {
//            if (i > 0 && i % 10 == 0) {
//                v.commit();
//                final var tree = fcmap.getChild(0);
//                engine.digestTreeSync(fcmap);
//
//                assertEquals(fcmap.getRootHash(), v.getHash());
//
//                v = new VirtualMap(ds);
//                fcmap = fcmap.copy();
//            }
//            final var key = asKey(i + "");
//            final var value = asValue((i + 100_000_000) + "");
//            fcmap.put(new MerkleLong(i), new MerkleLong(i + 100_000_000));
//            v.putValue(key, value);
//        }
//    }

//    @Test
//    public void quickAndDirty() {
//        final var ds = new InMemoryDataSource();
//        VirtualMap map = new VirtualMap(ds);
//        for (int i=0; i<1_000_000; i++) {
//            final var key = asKey(i);
//            final var value = asValue(i);
//            map.putValue(key, value);
////            System.out.println(map.getAsciiArt());
//        }
//
//        Random rand = new Random();
//        for (int idx=0; idx<10_000; idx++) {
//            map = new VirtualMap(ds);
//            for (int j = 0; j < 100; j++) {
//                final var i = rand.nextInt(1_000_000);
//                map.putValue(asKey(i), asValue(i + 1_000_000));
//            }
//        }
//    }

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

    private static final class InMemoryDataSource implements VirtualDataSource {
        private Map<VirtualKey, VirtualRecord> leaves = new HashMap<>();
        private Map<Long, VirtualRecord> leavesByPath = new HashMap<>();
        private Map<Long, Hash> parents = new HashMap<>();
        private long firstLeafPath = INVALID_PATH;
        private long lastLeafPath = INVALID_PATH;
        private boolean closed = false;

        @Override
        public Hash loadParentHash(long parentPath) {
            return parents.get(parentPath);
        }

        @Override
        public VirtualRecord loadLeaf(long leafPath) {
            return leavesByPath.get(leafPath);
        }

        @Override
        public VirtualRecord loadLeaf(VirtualKey leafKey) {
            return leaves.get(leafKey);
        }

        @Override
        public VirtualValue getLeafValue(VirtualKey leafKey) {
            final var rec = leaves.get(leafKey);
            return rec == null ? null : rec.getValue();
        }

        @Override
        public void saveParent(long parentPath, Hash hash) {
            parents.put(parentPath, hash);
        }

        @Override
        public void saveLeaf(VirtualRecord leaf) {
            leaves.put(leaf.getKey(), leaf);
            leavesByPath.put(leaf.getPath(), leaf);
        }

        @Override
        public void deleteParent(long parentPath) {
            parents.remove(parentPath);
        }

        @Override
        public void deleteLeaf(VirtualRecord leaf) {
            leaves.remove(leaf.getKey());
            leavesByPath.remove(leaf.getPath());
        }

        @Override
        public void writeLastLeafPath(long path) {
            this.lastLeafPath = path;
        }

        @Override
        public long getLastLeafPath() {
            return lastLeafPath;
        }

        @Override
        public void writeFirstLeafPath(long path) {
            this.firstLeafPath = path;
        }

        @Override
        public long getFirstLeafPath() {
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

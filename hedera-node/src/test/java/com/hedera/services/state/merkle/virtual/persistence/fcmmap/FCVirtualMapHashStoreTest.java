package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import com.swirlds.common.crypto.Hash;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;

import static com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCVirtualMapTestUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class FCVirtualMapHashStoreTest {
    private static final Random RANDOM = new Random(1234);
    public static final Path STORE_PATH = Path.of("store");
    static { System.out.println("STORE_PATH = " + STORE_PATH.toAbsolutePath()); }

    /**
     * TEST HASH FUNCTIONALITY
     */
    @Test
    public void createSomeHashDataAndReadBack() throws IOException {
        final int COUNT = 10_000;
        // delete old store if it exists
        deleteDirectoryAndContents(STORE_PATH);
        // create and open store
        final var hashStore = new FCVirtualMapHashStoreImpl<>(
                STORE_PATH, 8, 8,
                new FCSlotIndexUsingFCHashMap<>(), MemMapSlotStore::new);

        // create some data for a number of accounts
        for (int i = 0; i < COUNT; i++) {
            hashStore.saveHash(new LongVKey(i),hash(i));
        }
        // read back and check that data
        for (int i = 0; i < COUNT; i++) {
            LongVKey l = new LongVKey(i);
            var expectedHash = hash(i);

            Hash parentHash = hashStore.loadHash(l);
            assertEquals(expectedHash,parentHash);
        }

        // read back random and check that data
        for (int j = 0; j < COUNT; j++) {
            int i = RANDOM.nextInt(COUNT);
            LongVKey l = new LongVKey(i);
            var expectedHash = hash(i);
            Hash parentHash = hashStore.loadHash(l);
            assertEquals(expectedHash,parentHash);
        }

        // delete a leaf and parent and check
        LongVKey key = new LongVKey(COUNT/2);
        hashStore.deleteHash(key);
        assertFalse(hashStore.containsHash(key));

        hashStore.release();
    }



// TODO once fast copy is working add back in
//    @Test
//    public void testFastCopy() throws IOException {
//        final int COUNT = 1000;
//        FCVirtualMapDataStore<LongVKey,LongVKey,LongVKey,TestLeafData> store
//                = new FCVirtualMapDataStoreImpl<>(STORE_PATH,10,
//                8,
//                8,8,HASH_DATA_SIZE+1024,
//                FCSlotIndexUsingFCHashMap::new, FCSlotIndexUsingFCHashMap::new, FCSlotIndexUsingFCHashMap::new,
//                LongVKey::new, LongVKey::new, TestLeafData::new, MemMapSlotStore::new);
//
//        store.open();
//        // create some data for a number of accounts
//        for (int i = 0; i < COUNT; i++) {
//            LongVKey l = new LongVKey(i);
//            store.saveLeaf(l,l,new TestLeafData(i));
//            store.saveHash(l,hash(i));
//        }
//        var store2 = store.copy();
//        // change the data for 500-999 adding 1000 to data value, writing to new store2
//        for (int i = 500; i < COUNT; i++) {
//            int id = i + 1000;
//            LongVKey l = new LongVKey(id);
//            store2.saveLeaf(l,l,new TestLeafData(id));
//            store2.saveHash(l,hash(id));
//        }
//        // read back and check all data has original values from copy 1
//        for (int i = 0; i < COUNT; i++) {
//            LongVKey l = new LongVKey(i);
//            var expectedHash = hash(i);
//            var expectedLeafData = new TestLeafData(i);
//
//            Hash parentHash = store.loadHash(l);
//            assertEquals(expectedHash,parentHash);
//
//            TestLeafData leafData = store.loadLeafValueByKey(l);
//            assertEquals(expectedLeafData,leafData);
//
//            TestLeafData leafData2 = store.loadLeafValueByPath(l);
//            assertEquals(expectedLeafData,leafData2);
//        }
//        // read back and check all data has original new values from copy 2
//        for (int i = 0; i < COUNT; i++) {
//            int id = i<500 ? i : i + 1000;
//            LongVKey l = new LongVKey(id);
//            var expectedHash = hash(id);
//            var expectedLeafData = new TestLeafData(id);
//
//            Hash parentHash = store2.loadHash(l);
//            assertEquals(expectedHash,parentHash);
//
//            TestLeafData leafData = store2.loadLeafValueByKey(l);
//            assertEquals(expectedLeafData,leafData);
//
//            TestLeafData leafData2 = store2.loadLeafValueByPath(l);
//            assertEquals(expectedLeafData,leafData2);
//        }
//
//        store.release();
//        store2.release();
//    }
}

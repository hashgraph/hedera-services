package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import com.hedera.services.state.merkle.MerkleAccountState;
import com.hedera.services.state.merkle.virtual.persistence.FCVirtualMapDataStore;
import com.hedera.services.state.submerkle.EntityId;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.utility.SerializableLong;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCVirtualMapTestUtils.measureLengthOfSerializable;
import static org.junit.jupiter.api.Assertions.*;
import static com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCVirtualMapTestUtils.*;

public class FCVirtualMapDataStoreTest {
    public static final Path STORE_PATH = Path.of("store");

    static {
        System.out.println("STORE_PATH = " + STORE_PATH.toAbsolutePath());
    }
    private static final Random RANDOM = new Random(1234);


    @Test
    public void createSomeDataAndReadBack() throws IOException {
        final int COUNT = 10_000;
        // delete old store if it exists
        deleteDirectoryAndContents(STORE_PATH);
        // create and open store
        FCVirtualMapDataStore<SerializableLong,SerializableLong,SerializableLong,TestLeafData> store
                = new FCVirtualMapDataStoreImpl<>(STORE_PATH,10,
                        8,8,8,TestLeafData.SIZE_BYTES,
                        FCSlotIndexUsingFCHashMap::new, FCSlotIndexUsingFCHashMap::new, FCSlotIndexUsingFCHashMap::new,
                        TestLeafData::new, MemMapSlotStore::new);

        store.open();
        System.out.println("Files.exists(STORE_PATH) = " + Files.exists(STORE_PATH));
        // create some data for a number of accounts
        for (int i = 0; i < COUNT; i++) {
            SerializableLong l = new SerializableLong(i);
            store.saveLeaf(l,l,new TestLeafData(i));
            store.saveHash(l,hash(i));
        }
        // read back and check that data
        for (int i = 0; i < COUNT; i++) {
            SerializableLong l = new SerializableLong(i);
            var expectedHash = hash(i);
            var expectedLeafData = new TestLeafData(i);

            Hash parentHash = store.loadHash(l);
            assertEquals(expectedHash,parentHash);

            TestLeafData leafData = store.loadLeafByKey(l);
            assertEquals(expectedLeafData,leafData);

            TestLeafData leafData2 = store.loadLeafByPath(l);
            assertEquals(expectedLeafData,leafData2);
        }

        // check leaf count
        assertEquals(COUNT,store.leafCount());

        // read back random and check that data
        for (int j = 0; j < COUNT; j++) {
            int i = RANDOM.nextInt(COUNT);
            SerializableLong l = new SerializableLong(i);
            var expectedHash = hash(i);
            var expectedLeafData = new TestLeafData(i);

            Hash parentHash = store.loadHash(l);
            assertEquals(expectedHash,parentHash);

            TestLeafData leafData = store.loadLeafByKey(l);
            assertEquals(expectedLeafData,leafData);

            TestLeafData leafData2 = store.loadLeafByPath(l);
            assertEquals(expectedLeafData,leafData2);

            // check contains leaf
            assertTrue(store.containsLeafKey(l));
            // check contains parent
            assertTrue(store.containsHash(l));
        }

        // delete a leaf and parent and check
        SerializableLong key = new SerializableLong(COUNT/2);
        store.deleteLeaf(key,key);
        assertFalse(store.containsLeafKey(key));
        store.deleteHash(key);
        assertFalse(store.containsHash(key));

        store.release();
    }

    @Test
    public void createSomeDataAndReadBackComplexLeaf() throws IOException {
        try {
            ConstructableRegistry.registerConstructable(new ClassConstructorPair(EntityId.class,EntityId::new));
        } catch (ConstructableRegistryException e) {
            e.printStackTrace();
        }

//        final int COUNT = 10_000;
        final int COUNT = 5;
        // delete old store if it exists
        deleteDirectoryAndContents(STORE_PATH);
        // measure the size of a MerkleAccountState
        int sizeOfMerkleAccountState = measureLengthOfSerializable(createRandomMerkleAccountState(1, RANDOM));
        System.out.println("sizeOfMerkleAccountState = " + sizeOfMerkleAccountState);
        // create and open store
        FCVirtualMapDataStore<SerializableLong,SerializableAccount,SerializableLong,MerkleAccountState> store
                = new FCVirtualMapDataStoreImpl<>(STORE_PATH,10,
                8,
                8 * 3,8,sizeOfMerkleAccountState,
                FCSlotIndexUsingFCHashMap::new, FCSlotIndexUsingFCHashMap::new, FCSlotIndexUsingFCHashMap::new,
                MerkleAccountState::new, MemMapSlotStore::new);

        store.open();
        System.out.println("Files.exists(STORE_PATH) = " + Files.exists(STORE_PATH));
        // create some data for a number of accounts
        for (int i = 0; i < COUNT; i++) {
            SerializableLong l = new SerializableLong(i);
            MerkleAccountState merkleAccountState = createRandomMerkleAccountState(i, RANDOM);
            int size = measureLengthOfSerializable(merkleAccountState);
            assertEquals(sizeOfMerkleAccountState,size,"Serialized MerkleAccountState size is not as expected.");
            store.saveLeaf(new SerializableAccount(i,i,i),l,merkleAccountState);
            store.saveHash(l,hash(i));
        }
        // read back and check that data
        for (int i = 0; i < COUNT; i++) {
            SerializableLong l = new SerializableLong(i);
            var expectedHash = hash(i);
//            var expectedLeafData = createRandomMerkleAccountState(i, RANDOM);

            Hash parentHash = store.loadHash(l);
            assertEquals(expectedHash,parentHash);

            try {
                MerkleAccountState leafData2 = store.loadLeafByPath(l);
                assertEquals(i,leafData2.balance());
                assertEquals(i,leafData2.expiry());
            } catch (IOException e) {
                System.err.println("l = "+l.getValue());
                e.printStackTrace();
            }

            MerkleAccountState leafData = store.loadLeafByKey(new SerializableAccount(i,i,i));
            assertEquals(i,leafData.balance());
            assertEquals(i,leafData.expiry());
        }

        // check leaf count
        assertEquals(COUNT,store.leafCount());

        store.release();
    }

    @Test
    public void testFastCopy() throws IOException {
        final int COUNT = 1000;
        FCVirtualMapDataStore<SerializableLong,SerializableLong,SerializableLong,TestLeafData> store
                = new FCVirtualMapDataStoreImpl<>(STORE_PATH,10,
                8,
                8,8,HASH_DATA_SIZE+1024,
                FCSlotIndexUsingFCHashMap::new, FCSlotIndexUsingFCHashMap::new, FCSlotIndexUsingFCHashMap::new,
                TestLeafData::new, MemMapSlotStore::new);

        store.open();
        // create some data for a number of accounts
        for (int i = 0; i < COUNT; i++) {
            SerializableLong l = new SerializableLong(i);
            store.saveLeaf(l,l,new TestLeafData(i));
            store.saveHash(l,hash(i));
        }
        var store2 = store.copy();
        // change the data for 500-999 adding 1000 to data value, writing to new store2
        for (int i = 500; i < COUNT; i++) {
            int id = i + 1000;
            SerializableLong l = new SerializableLong(id);
            store2.saveLeaf(l,l,new TestLeafData(id));
            store2.saveHash(l,hash(id));
        }
        // read back and check all data has original values from copy 1
        for (int i = 0; i < COUNT; i++) {
            SerializableLong l = new SerializableLong(i);
            var expectedHash = hash(i);
            var expectedLeafData = new TestLeafData(i);

            Hash parentHash = store.loadHash(l);
            assertEquals(expectedHash,parentHash);

            TestLeafData leafData = store.loadLeafByKey(l);
            assertEquals(expectedLeafData,leafData);

            TestLeafData leafData2 = store.loadLeafByPath(l);
            assertEquals(expectedLeafData,leafData2);
        }
        // read back and check all data has original new values from copy 2
        for (int i = 0; i < COUNT; i++) {
            int id = i<500 ? i : i + 1000;
            SerializableLong l = new SerializableLong(id);
            var expectedHash = hash(id);
            var expectedLeafData = new TestLeafData(id);

            Hash parentHash = store2.loadHash(l);
            assertEquals(expectedHash,parentHash);

            TestLeafData leafData = store2.loadLeafByKey(l);
            assertEquals(expectedLeafData,leafData);

            TestLeafData leafData2 = store2.loadLeafByPath(l);
            assertEquals(expectedLeafData,leafData2);
        }

        store.release();
        store2.release();
    }



}

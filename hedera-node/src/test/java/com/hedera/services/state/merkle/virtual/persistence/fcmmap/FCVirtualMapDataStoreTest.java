package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import com.hedera.services.state.merkle.virtual.persistence.FCVirtualMapDataStore;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.SerializableLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class FCVirtualMapDataStoreTest {
    private static final int HASH_DATA_SIZE = DigestType.SHA_384.digestLength() + Integer.BYTES + Integer.BYTES; // int for digest type and int for byte array length
    private static final int MB = 1024*1024;
    public static final Path STORE_PATH = Path.of("store");
    public static final int KEY_SIZE_BYTES = 32;
    public static final int DATA_SIZE_BYTES = 32;

    static {
        System.out.println("STORE_PATH = " + STORE_PATH.toAbsolutePath());
    }
    private static final Random RANDOM = new Random(1234);

    @BeforeEach
    public void deleteAnyOld() {
        if (Files.exists(STORE_PATH)) {
            try {
                Files.walk(STORE_PATH)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (Exception e) {
                System.err.println("Failed to delete test store directory");
                e.printStackTrace();
            }
            System.out.println("Deleted data files");
        }
    }

    @AfterEach
    public void printSize() {
        if (Files.exists(STORE_PATH)) {
            try {
                long size = Files.walk(STORE_PATH)
                        .filter(p -> p.toFile().isFile())
                        .mapToLong(p -> p.toFile().length())
                        .sum();
                System.out.printf("Test data storage size: %,.1f Mb\n",(double)size/(1024d*1024d));
            } catch (Exception e) {
                System.err.println("Failed to measure size of directory");
                e.printStackTrace();
            }
        }
    }

    @Test
    public void createSomeDataAndReadBack() throws IOException {
        final int COUNT = 10_000;
        FCVirtualMapDataStore<SerializableLong,Hash,SerializableLong,SerializableLong,TestLeafData> store
                = new FCVirtualMapDataStoreImpl<>(STORE_PATH,10,
                        8,HASH_DATA_SIZE,
                        8,8,HASH_DATA_SIZE+1024,
                        FCSlotIndexUsingFCHashMap::new, FCSlotIndexUsingFCHashMap::new, FCSlotIndexUsingFCHashMap::new,
                        Hash::new, TestLeafData::new, MemMapSlotStore::new);

        store.open();
        System.out.println("Files.exists(STORE_PATH) = " + Files.exists(STORE_PATH));
        // create some data for a number of accounts
        for (int i = 0; i < COUNT; i++) {
            SerializableLong l = new SerializableLong(i);
            store.saveLeaf(l,l,new TestLeafData(i));
            store.saveParentHash(l,hash(i));
        }
        // read back and check that data
        for (int i = 0; i < COUNT; i++) {
            SerializableLong l = new SerializableLong(i);
            var expectedHash = hash(i);
            var expectedLeafData = new TestLeafData(i);

            Hash parentHash = store.loadParent(l);
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

            Hash parentHash = store.loadParent(l);
            assertEquals(expectedHash,parentHash);

            TestLeafData leafData = store.loadLeafByKey(l);
            assertEquals(expectedLeafData,leafData);

            TestLeafData leafData2 = store.loadLeafByPath(l);
            assertEquals(expectedLeafData,leafData2);

            // check contains leaf
            assertTrue(store.containsLeafKey(l));
            // check contains parent
            assertTrue(store.containsParentKey(l));
        }

        // delete a leaf and parent and check
        SerializableLong key = new SerializableLong(COUNT/2);
        store.deleteLeaf(key,key);
        assertFalse(store.containsLeafKey(key));
        store.deleteParent(key);
        assertFalse(store.containsParentKey(key));

        store.release();
    }

    @Test
    public void testFastCopy() throws IOException {
        final int COUNT = 1000;
        FCVirtualMapDataStore<SerializableLong,Hash,SerializableLong,SerializableLong,TestLeafData> store
                = new FCVirtualMapDataStoreImpl<>(STORE_PATH,10,
                8,HASH_DATA_SIZE,
                8,8,HASH_DATA_SIZE+1024,
                FCSlotIndexUsingFCHashMap::new, FCSlotIndexUsingFCHashMap::new, FCSlotIndexUsingFCHashMap::new,
                Hash::new, TestLeafData::new, MemMapSlotStore::new);

        store.open();
        // create some data for a number of accounts
        for (int i = 0; i < COUNT; i++) {
            SerializableLong l = new SerializableLong(i);
            store.saveLeaf(l,l,new TestLeafData(i));
            store.saveParentHash(l,hash(i));
        }
        var store2 = store.copy();
        // change the data for 500-999 adding 1000 to data value, writing to new store2
        for (int i = 500; i < COUNT; i++) {
            int id = i + 1000;
            SerializableLong l = new SerializableLong(id);
            store2.saveLeaf(l,l,new TestLeafData(id));
            store2.saveParentHash(l,hash(id));
        }
        // read back and check all data has original values from copy 1
        for (int i = 0; i < COUNT; i++) {
            SerializableLong l = new SerializableLong(i);
            var expectedHash = hash(i);
            var expectedLeafData = new TestLeafData(i);

            Hash parentHash = store.loadParent(l);
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

            Hash parentHash = store2.loadParent(l);
            assertEquals(expectedHash,parentHash);

            TestLeafData leafData = store2.loadLeafByKey(l);
            assertEquals(expectedLeafData,leafData);

            TestLeafData leafData2 = store2.loadLeafByPath(l);
            assertEquals(expectedLeafData,leafData2);
        }

        store.release();
        store2.release();
    }



        /**
         * Creates a hash containing a int repeated 6 times as longs
         *
         * @return byte array of 6 longs
         */
    private static final Hash hash(int value) {
        byte b0 = (byte)(value >>> 24);
        byte b1 = (byte)(value >>> 16);
        byte b2 = (byte)(value >>> 8);
        byte b3 = (byte)value;
        return new TestHash(new byte[] {
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3
        });
    }

    public static String toLongsString(Hash hash) {
        final var bytes = hash.getValue();
        LongBuffer longBuf =
                ByteBuffer.wrap(bytes)
                        .order(ByteOrder.BIG_ENDIAN)
                        .asLongBuffer();
        long[] array = new long[longBuf.remaining()];
        longBuf.get(array);
        return Arrays.toString(array);
    }

    private static final class TestHash extends Hash {
        public TestHash(byte[] bytes) {
            super(bytes, DigestType.SHA_384, true, false);
        }
    }

    private static final class TestLeafData implements SelfSerializable {
        static final byte[] RANDOM_1K = new byte[1024];
        static {
            RANDOM.nextBytes(RANDOM_1K);
        }
        private Hash hash;
        private byte[] data;

        public TestLeafData() {}

        public TestLeafData(int id) {
            this.hash = hash(id);
            this.data = RANDOM_1K;
        }

        @Override
        public void deserialize(SerializableDataInputStream serializableDataInputStream, int i) throws IOException {
            hash = new Hash();
            hash.deserialize(serializableDataInputStream,i);
            data = new byte[1024];
            serializableDataInputStream.read(data);
        }

        @Override
        public void serialize(SerializableDataOutputStream serializableDataOutputStream) throws IOException {
            hash.serialize(serializableDataOutputStream);
            serializableDataOutputStream.write(data);
        }

        @Override
        public long getClassId() {
            return 1234;
        }

        @Override
        public int getVersion() {
            return 1;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TestLeafData that = (TestLeafData) o;
            return Objects.equals(hash, that.hash) && Arrays.equals(data, that.data);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(hash);
            result = 31 * result + Arrays.hashCode(data);
            return result;
        }
    }
}

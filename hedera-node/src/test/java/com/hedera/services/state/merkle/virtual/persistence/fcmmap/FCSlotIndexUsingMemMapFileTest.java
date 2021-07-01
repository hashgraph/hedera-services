package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import com.hedera.services.state.merkle.virtual.persistence.FCSlotIndex;
import com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCVirtualMapTestUtils.LongVKey;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.LongSupplier;

import static com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCVirtualMapTestUtils.deleteDirectoryAndContents;
import static com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCVirtualMapTestUtils.printDirectorySize;
import static java.util.stream.IntStream.range;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FCSlotIndexUsingMemMapFileTest {
    public static final Path STORE_PATH = Path.of("store");
    private static final Random RANDOM = new Random(1234);

    @Test
    public void basicTest() throws IOException {
        // delete old store if it exists
        deleteDirectoryAndContents(STORE_PATH);
        // create initial indexes
        FCSlotIndexUsingFCHashMap<LongVKey> fcHashMap = new FCSlotIndexUsingFCHashMap<>();
        FCSlotIndexUsingMemMapFile<LongVKey> memMapFile = new FCSlotIndexUsingMemMapFile<>(
                STORE_PATH,"FCSlotIndexUsingMemMapFileTest",256,16,Long.BYTES,100,10);
        var index_0 = new PairFCSlotIndex(fcHashMap, memMapFile, 0);
        // create 1000 entries
        range(0,1000).forEach(i -> index_0.putSlot(i, 10000+i));
        // check them
        range(0,1000).forEach(i -> assertEquals(10000 + i, index_0.getSlot(i)));
        // create new version
        var index_1 = index_0.copy();
        // write 500-1000 entries for index 1
        range(500,1000).forEach(i -> index_1.putSlot(i, 20000+i));
        // check index 0 and index 1 have correct data
        range(0,1000).forEach(i -> assertEquals(10000 + i, index_0.getSlot(i)));
        range(0,500).forEach(i -> assertEquals(10000 + i, index_1.getSlot(i)));
        range(500,1000).forEach(i -> assertEquals(20000 + i, index_1.getSlot(i)));
        // delete 250 and check
        index_1.removeSlot(250);
        index_1.getSlot(250);
        index_0.getSlot(250);
        // delete 750 and check
        index_1.removeSlot(750);
        index_1.getSlot(750);
        index_0.getSlot(750);
        // check non existent key
        index_1.getSlot(1001);
        index_0.getSlot(1001);
        // delete non existent key 1001 and check
        index_1.removeSlot(1001);
        index_1.getSlot(1001);
        index_0.getSlot(1001);
        // release index
        index_0.release();
        // print directory size
        printDirectorySize(STORE_PATH);
    }

    @Test
    public void randomOpTest() throws IOException {
        final int MAX_INDEX = 100_000;
        // delete old store if it exists
        deleteDirectoryAndContents(STORE_PATH);
        // create initial indexes
        FCSlotIndexUsingFCHashMap<LongVKey> fcHashMap = new FCSlotIndexUsingFCHashMap<>();
        FCSlotIndexUsingMemMapFile<LongVKey> memMapFile = new FCSlotIndexUsingMemMapFile<>(
                STORE_PATH,"FCSlotIndexUsingMemMapFileTest",512*128,128,Long.BYTES,100,10);
        var currentIndex = new PairFCSlotIndex(fcHashMap, memMapFile, 0);
        List<PairFCSlotIndex> indexes = new ArrayList<>();
        indexes.add(currentIndex);
        for (int i = 0; i < 100_000; i++) {
            currentIndex.keyCount();
            switch(RANDOM.nextInt(11)) {
                case 0:
                case 1:
                case 2:
                case 3:
                    indexes.forEach(pairFCSlotIndex -> pairFCSlotIndex.getSlot(RANDOM.nextInt(MAX_INDEX)));
                    break;
                case 4:
                case 5:
                case 6:
                    currentIndex.putSlot(RANDOM.nextInt(MAX_INDEX), randomPositiveLong());
                    break;
                case 7:
                    currentIndex.removeSlot(RANDOM.nextInt(MAX_INDEX));
                    break;
                case 8:
                    currentIndex.keyCount();
                    break;
                case 9: // fast copy
                    currentIndex = currentIndex.copy();
                    indexes.add(currentIndex);
                    break;
                case 10: // release old copy
                    if (RANDOM.nextDouble() > 0.75 && indexes.size() > 1) {
                        //noinspection ResultOfMethodCallIgnored
                        indexes.get(RANDOM.nextInt(indexes.size() - 1));
                    }
                    break;
            }
        }
        // print directory size
        printDirectorySize(STORE_PATH);
    }

    public static long randomPositiveLong() {
        // it's okay that the bottom word remains signed.
        long value = (long)(RANDOM.nextDouble()*Long.MAX_VALUE);
        assertTrue(value >= 0);
        return value;
    }

    public static class PairFCSlotIndex implements FCSlotIndex<LongVKey> {
        private final FCSlotIndexUsingFCHashMap<LongVKey> fcHashMap;
        private final FCSlotIndexUsingMemMapFile<LongVKey> memMapFile;
        private final int version;

        public PairFCSlotIndex(FCSlotIndexUsingFCHashMap<LongVKey> fcHashMap, FCSlotIndexUsingMemMapFile<LongVKey> memMapFile, int version) {
            this.fcHashMap = fcHashMap;
            this.memMapFile = memMapFile;
            this.version = version;
        }

        public long getSlot(long key) {
            return getSlot(new LongVKey(key));
        }
        public void putSlot(long key, long slot) { putSlot(new LongVKey(key), slot); }
        public void removeSlot(long key) throws IOException { removeSlot(new LongVKey(key)); }

        @Override
        public long getSlot(LongVKey key) {
            try {
                long fcHashMapValue = fcHashMap.getSlot(key);
                long memMapFileValue = memMapFile.getSlot(key);
                assertEquals(fcHashMapValue, memMapFileValue);
                return memMapFileValue;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public long getSlotIfAbsentPut(LongVKey key, LongSupplier newValueSupplier) throws IOException {
            LongSupplier longSupplier = new LongSupplier() {
                boolean newValueAvailable = false;
                long newValue;
                @Override
                public synchronized long getAsLong() {
                    if (!newValueAvailable) {
                        newValue = newValueSupplier.getAsLong();
                        newValueAvailable = true;
                    }
                    return newValue;
                }
            };
            long fcHashMapValue = fcHashMap.getSlotIfAbsentPut(key,longSupplier);
            long memMapFileValue = memMapFile.getSlotIfAbsentPut(key,longSupplier);
            assertEquals(fcHashMapValue, memMapFileValue);
            return memMapFileValue;
        }

        @Override
        public void putSlot(LongVKey key, long slot) {
            try {
                fcHashMap.putSlot(key, slot);
                memMapFile.putSlot(key, slot);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public long removeSlot(LongVKey key) throws IOException {
            long fcHashMapValue = fcHashMap.removeSlot(key);
            long memMapFileValue = memMapFile.removeSlot(key);
            assertEquals(fcHashMapValue, memMapFileValue,"removeSlot returned different values for key["+toString(key)+"] and version["+version+"]");
            return memMapFileValue;
        }

        @Override
        public int keyCount() {
            int fcHashMapCount = fcHashMap.keyCount();
            int memMapFileCount = memMapFile.keyCount();
            if (fcHashMapCount != memMapFileCount) {
                System.out.println("Oops");
            }
            assertEquals(fcHashMapCount, memMapFileCount);
            return 0;
        }

        @Override
        public Object acquireWriteLock(int keyHash) {
            return keyHash;
        }

        @Override
        public void releaseWriteLock(int keyHash, Object lockStamp) {
            // No-op
        }

        @Override
        public Object acquireReadLock(int keyHash) {
            return keyHash;
        }

        @Override
        public void releaseReadLock(int keyHash, Object lockStamp) {
            // No-op
        }

        @Override
        public PairFCSlotIndex copy() {
            return new PairFCSlotIndex(fcHashMap.copy(), memMapFile.copy(), version+1);
        }

        @Override
        public void release() {
            fcHashMap.release();
            memMapFile.release();
        }

        private String toString(LongVKey LongVKey) {
            return LongVKey == null ? "null" : Long.toString(LongVKey.getValue());
        }
    }
}

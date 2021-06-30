package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import com.hedera.services.state.merkle.virtual.persistence.FCSlotIndex;
import com.swirlds.common.merkle.utility.SerializableLong;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCVirtualMapTestUtils.deleteDirectoryAndContents;
import static com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCVirtualMapTestUtils.printDirectorySize;
import static java.util.stream.IntStream.range;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FCSlotIndexUsingMemMapFileTest {
    public static final Path STORE_PATH = Path.of("store");
    private static final Random RANDOM = new Random(1234);

    @Test
    public void basicTest() throws IOException {
        // delete old store if it exists
        deleteDirectoryAndContents(STORE_PATH);
        // create initial indexes
        FCSlotIndexUsingFCHashMap<SerializableLong> fcHashMap = new FCSlotIndexUsingFCHashMap<>();
        FCSlotIndexUsingMemMapFile<SerializableLong> memMapFile = new FCSlotIndexUsingMemMapFile<>(
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

    public static class PairFCSlotIndex implements FCSlotIndex<SerializableLong> {
        private final FCSlotIndexUsingFCHashMap<SerializableLong> fcHashMap;
        private final FCSlotIndexUsingMemMapFile<SerializableLong> memMapFile;
        private final int version;

        public PairFCSlotIndex(FCSlotIndexUsingFCHashMap<SerializableLong> fcHashMap, FCSlotIndexUsingMemMapFile<SerializableLong> memMapFile, int version) {
            this.fcHashMap = fcHashMap;
            this.memMapFile = memMapFile;
            this.version = version;
        }

        public long getSlot(long key) {
            return getSlot(new SerializableLong(key));
        }
        public void putSlot(long key, long slot) { putSlot(new SerializableLong(key),slot); }
        public void removeSlot(long key) { removeSlot(new SerializableLong(key)); }

        @Override
        public long getSlot(SerializableLong key) {
            long fcHashMapValue = fcHashMap.getSlot(key);
            long memMapFileValue = memMapFile.getSlot(key);
            assertEquals(fcHashMapValue, memMapFileValue);
            return memMapFileValue;
        }

        @Override
        public void putSlot(SerializableLong key, long slot) {
            fcHashMap.putSlot(key,slot);
            memMapFile.putSlot(key,slot);
        }

        @Override
        public long removeSlot(SerializableLong key) {
            long fcHashMapValue = fcHashMap.removeSlot(key);
            long memMapFileValue = memMapFile.removeSlot(key);
            assertEquals(fcHashMapValue, memMapFileValue,"removeSlot returned different values for key["+toString(key)+"] and version["+version+"]");
            return memMapFileValue;
        }

        @Override
        public int keyCount() {
            int fcHashMapCount = fcHashMap.keyCount();
            int memMapFileCount = memMapFile.keyCount();
            assertEquals(fcHashMapCount, memMapFileCount);
            return 0;
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

        public void assertEqualsForKeys(Collection<SerializableLong> keys) {
            for (SerializableLong key : keys) {
                getSlot(key);
            }
        }

        private String toString(SerializableLong serializableLong) {
            return serializableLong == null ? "null" : Long.toString(serializableLong.getValue());
        }
    }
}

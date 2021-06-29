package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import com.hedera.services.state.merkle.virtual.persistence.FCSlotIndex;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.utility.SerializableLong;
import com.swirlds.fchashmap.FCHashMap;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCVirtualMapTestUtils.deleteDirectoryAndContents;
import static com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCVirtualMapTestUtils.hash;
import static org.junit.jupiter.api.Assertions.*;

public class BinFileTest {
    public static final Path STORE_PATH = Path.of("store");

    static {
        System.out.println("STORE_PATH = " + STORE_PATH.toAbsolutePath());
    }

    private static final Random RANDOM = new Random(1234);

    private static Path getTempFile() throws IOException {
        File tempFile = File.createTempFile("BinFileTest",".dat");
        System.out.println("tempFile = " + tempFile.getAbsolutePath());
        tempFile.deleteOnExit();
        return tempFile.toPath();
    }

    /**
     * test basic data storage and retrieval for a single version
     */
    @Test
    public void createSomeDataAndReadBack() throws IOException {
        final int COUNT = 10_000;
//        final int COUNT = 1;
        // create and open file
        Path file = getTempFile();
        BinFile<SerializableLong> binFile = new BinFile<>(file,8,150,100,20);
        System.out.printf("BinFile size: %,.1f Mb\n",(double) Files.size(file)/(1024d*1024d));
        // create some data for a number of accounts
        for (int i = 0; i < COUNT; i++) {
            SerializableLong key = new SerializableLong(i);
            binFile.putSlot(0,key.hashCode(), key,i);
        }
        // read back and check that data
        for (int i = 0; i < COUNT; i++) {
            SerializableLong key = new SerializableLong(i);
            long result = binFile.getSlot(0,key.hashCode(), key);
            assertEquals(i, result);
        }

        // read back random and check that data
        for (int j = 0; j < COUNT; j++) {
            int i = RANDOM.nextInt(COUNT);
            SerializableLong key = new SerializableLong(i);
            long result = binFile.getSlot(0,key.hashCode(), key);
            assertEquals(i, result);
        }
        // close
        binFile.close();
    }

    @Test
    public void testDelete() throws IOException {
        // create and open file
        Path file = getTempFile();
        BinFile<SerializableLong> binFile = new BinFile<>(file,8,5,5,20);
        // create some data for a number of accounts
        for (int i = 0; i < 10; i++) {
            SerializableLong key = new SerializableLong(i);
            binFile.putSlot(0,key.hashCode(), key,i);
        }
        // read back and check that data
        for (int i = 0; i < 10; i++) {
            SerializableLong key = new SerializableLong(i);
            long result = binFile.getSlot(0,key.hashCode(), key);
            assertEquals(i, result);
        }
        // delete 3 and 7
        SerializableLong key3 = new SerializableLong(3);
        binFile.removeKey(0,key3.hashCode(), key3);
        SerializableLong key7 = new SerializableLong(7);
        binFile.removeKey(0,key7.hashCode(), key7);
        // check data
        for (int i = 0; i < 10; i++) {
            SerializableLong key = new SerializableLong(i);
            long result = binFile.getSlot(0, key.hashCode(), key);
            assertEquals((i != 3 && i != 7) ? i : FCSlotIndex.NOT_FOUND_LOCATION, result);
        }
        // close
        binFile.close();
    }


    @Test
    public void versionsTest() throws IOException {
        // create and open file
        Path file = getTempFile();
        BinFile<SerializableLong> binFile = new BinFile<>(file,8,20,20,20);
        System.out.printf("BinFile size: %,.1f Mb\n",(double) Files.size(file)/(1024d*1024d));
        // create key array
        SerializableLong[] keys = new SerializableLong[20];
        int[] keyHashs = new int[20];
        for (int i = 0; i < 20; i++) {
            keys[i] = new SerializableLong(i);
            keyHashs[i] = keys[i].hashCode();
        }
        // create 20 values in version 100
        for (int i = 0; i < 20; i++) {
            binFile.putSlot(100,keyHashs[i], keys[i],100+i);
        }
        // read back and check that data
        for (int i = 0; i < 20; i++) {
            assertEquals(100+i, binFile.getSlot(100,keyHashs[i], keys[i]));
        }
        // create 10 values in version 200
        binFile.versionChanged(100,200);
        for (int i = 10; i < 20; i++) {
            binFile.putSlot(200,keyHashs[i], keys[i],200+i);
        }
        // check the data is still correct for version 100
        for (int i = 0; i < 20; i++) {
            assertEquals(100+i, binFile.getSlot(100,keyHashs[i], keys[i]));
        }
        // check the data is correct for version 200
        for (int i = 0; i < 20; i++) {
            long result = binFile.getSlot(200,keyHashs[i], keys[i]);
            assertEquals((i<10) ? 100+i : 200+i, result);
        }
        // delete one from version 200
        binFile.removeKey(200,keyHashs[5], keys[5]);
        // check it is deleted in 200
        assertEquals(FCSlotIndex.NOT_FOUND_LOCATION, binFile.getSlot(200,keyHashs[5], keys[5]));
        // check it is not deleted in 100
        assertEquals(105, binFile.getSlot(100,keyHashs[5], keys[5]));
        // add version 300 for key 5
        {
            binFile.versionChanged(200,300);
            binFile.putSlot(300, keyHashs[5], keys[5], 305);
            // check all values again
            assertEquals(305, binFile.getSlot(300,keyHashs[5], keys[5]));
            assertEquals(FCSlotIndex.NOT_FOUND_LOCATION, binFile.getSlot(200,keyHashs[5], keys[5]));
            assertEquals(105, binFile.getSlot(100,keyHashs[5], keys[5]));
        }
        // now release versions 100 and 200 and check all data is the same
        {
            binFile.releaseVersion(100);
            binFile.releaseVersion(200);
            binFile.versionChanged(300,400);
            // add new data for all keys for version 400 , this should cause all mutations for 100 and 200 to be cleaned up
            for (int i = 0; i < 20; i++) {
                binFile.putSlot(400,keyHashs[i], keys[i],400+i);
            }
            // check all data in 400 is correct
            for (int i = 0; i < 20; i++) {
                assertEquals(400+i, binFile.getSlot(400,keyHashs[i], keys[i]));
            }
            // check all data in 300 is correct
            for (int i = 0; i < 20; i++) {
                long result = binFile.getSlot(200,keyHashs[i], keys[i]);
                if (i == 5) {
                    assertEquals(305, binFile.getSlot(300,keyHashs[5], keys[5]));
                } else {
                    assertEquals((i < 10) ? 100 + i : 200 + i, result);
                }
            }
        }
        // close
        binFile.close();
    }

}

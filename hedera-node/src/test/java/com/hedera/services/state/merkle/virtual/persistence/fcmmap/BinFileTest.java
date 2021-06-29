package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.utility.SerializableLong;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

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
    }

}

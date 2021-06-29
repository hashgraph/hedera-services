package com.hedera.services.state.merkle.vitrualh;

import com.hedera.services.state.merkle.virtualh.persistence.mmap.MemMapDataStore;
import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MemMapDataStoreTest {
    private static final int MB = 1024*1024;
    public static final Path STORE_PATH = Path.of("store");
    public static final int DATA_SIZE = 32;

    @AfterEach
    public void closeAndDeleteDataStore() {
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
            try {
                Files.walk(STORE_PATH)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (Exception e) {
                System.err.println("Failed to delete test store directory");
                e.printStackTrace();
            }
        }
    }

    @Test
    public void createSomeDataAndReadBack() {
        MemMapDataStore store = new MemMapDataStore(DATA_SIZE, 100 * MB, STORE_PATH,"test_","dat");
        store.open(null);
        final int COUNT = 1000;
        List<Long> locations = new ArrayList<>(COUNT);
        for (long i = 0; i < COUNT; i++) {
            long location = store.getNewSlot();
            locations.add(location);
            ByteBuffer buffer = store.accessSlot(location);
            buffer.putLong(i);
        }
        // check all the data is there
        for (long i = 0; i < 10_000; i++) {
            int index = (int)(Math.random()*COUNT);
            long location = locations.get(index);
            ByteBuffer buffer = store.accessSlot(location);
            long value = buffer.getLong();
            Assertions.assertEquals(value,(long)index);
        }
        store.close();
    }

    @Test
    public void createSomeDataAndReadBackAfterClose() {
        MemMapDataStore store = new MemMapDataStore(32, 100 * MB, STORE_PATH,"test_","dat");
        store.open(null);
        final int COUNT = 1000;
        List<Long> locations = new ArrayList<>(COUNT);
        for (long i = 0; i < COUNT; i++) {
            long location = store.getNewSlot();
            locations.add(location);
            ByteBuffer buffer = store.accessSlot(location);
            buffer.putLong(i);
        }
        store.sync();
        store.close();

        System.out.println("locations = " + locations);
        // check all the data is there
        store = new MemMapDataStore(32, 100 * MB, STORE_PATH,"test_","dat");
        store.open(null);
        for (long i = 0; i < 10_000; i++) {
            int index = (int)(Math.random()*COUNT);
            long location = locations.get(index);
            ByteBuffer buffer = store.accessSlot(location);
            long value = buffer.getLong();
            Assertions.assertEquals(value,(long)index);
        }
        store.close();
    }

}

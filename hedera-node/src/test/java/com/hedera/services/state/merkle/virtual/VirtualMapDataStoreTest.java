package com.hedera.services.state.merkle.virtual;

import com.swirlds.common.crypto.Hash;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Random;

public class VirtualMapDataStoreTest {
    private static final int MB = 1024*1024;
    private static final Path STORE_PATH = Path.of("store");

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
//        final int ACCOUNT_COUNT = 10;
//        final int COUNT = 1000;
//        final Hash HASH = createConstantHash();
//        VirtualMapDataStore store = new VirtualMapDataStore(STORE_PATH,32,32);
//        store.open();
//        // create some data for a number of accounts
//        for (int a = 0; a < ACCOUNT_COUNT; a++) {
//            Account account = new Account(0,0,a);
//            for (int i = 0; i < COUNT; i++) {
//                store.save(account, new VirtualTreeInternal(HASH,i));
//                store.save(account, new VirtualTreeLeaf(HASH,i, new VirtualKey(get32Bytes(i)), new VirtualValue(get32Bytes(i))));
//                if (i < 100) store.save(account, (byte)i,i);
//            }
//        }
//        // read back and check that data
//        for (int a = 0; a < ACCOUNT_COUNT; a++) {
//            Account account = new Account(0,0,a);
//            for (int i = 0; i < COUNT; i++) {
//                VirtualTreeInternal parent = store.loadParent(account,i);
//                Assertions.assertEquals(parent.hash(), );
//                store.save(account, new VirtualTreeLeaf(HASH,i, new VirtualKey(get32Bytes(i)), new VirtualValue(get32Bytes(i))));
//                if (i < 100) store.save(account, (byte)i,i);
//            }
//        }
//
//        store.close();
    }

    private byte[] get32Bytes(int value) {
        return new byte[] {
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value,
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value,
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value,
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value
        };
    }

    private static final Random RANDOM = new Random(187737129873728974L);

    /**
     * Creates a random hash
     * @return
     */
    private Hash createConstantHash() {
        byte[] hashData = new byte[384/8];
        RANDOM.nextBytes(hashData);
        return new Hash(hashData);
    }
}

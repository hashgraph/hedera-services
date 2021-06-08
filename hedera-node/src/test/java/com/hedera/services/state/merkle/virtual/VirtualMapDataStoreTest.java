package com.hedera.services.state.merkle.virtual;

import com.hedera.services.state.merkle.virtual.persistence.VirtualRecord;
import com.hedera.services.state.merkle.virtual.persistence.mmap.VirtualMapDataStore;
import com.swirlds.common.crypto.Hash;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class VirtualMapDataStoreTest {
    private static final int MB = 1024*1024;
    private static final Path STORE_PATH = Path.of("store");
    static {
        System.out.println("STORE_PATH = " + STORE_PATH.toAbsolutePath());
    }

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
    public void createSomeDataAndReadBack() {
        final int ACCOUNT_COUNT = 10;
        final int COUNT = 10_000;
        VirtualMapDataStore store = new VirtualMapDataStore(STORE_PATH,32,32,3);
        store.open();
        System.out.println("Files.exists(STORE_PATH) = " + Files.exists(STORE_PATH));
        // create some data for a number of accounts
        for (int a = 0; a < ACCOUNT_COUNT; a++) {
            Account account = new Account(0,0,a);
            for (int i = 0; i < COUNT; i++) {
                byte[] hash = hash(i);
                // write parent
                store.saveParentHash(account, i, hash);
                // write leaf
                store.saveLeaf(account, new VirtualRecord(hash,i, new VirtualKey(get32Bytes(i)), new VirtualValue(get32Bytes(i))));
                // write path
                store.savePath(account, i,i);
            }
        }
        // read back and check that data
        for (int a = 0; a < ACCOUNT_COUNT; a++) {
            Account account = new Account(0,0,a);
            for (int i = 0; i < COUNT; i++) {
                byte[] hash = hash(i);
                VirtualKey key = new VirtualKey(get32Bytes(i));
                VirtualValue value = new VirtualValue(get32Bytes(i));

                // read parent
                byte[] parentHash = store.loadParentHash(account,i);
                Assertions.assertArrayEquals(parentHash, hash);
                // read leaf by key
                VirtualRecord record = store.loadLeaf(account,key);
                Assertions.assertArrayEquals(record.getHash(), hash);
                Assertions.assertEquals(record.getPath(), i);
                Assertions.assertEquals(record.getKey(), key);
                Assertions.assertEquals(record.getValue(), value);
                // read leaf by path
                record = store.loadLeaf(account,i);
                Assertions.assertArrayEquals(record.getHash(), hash);
                Assertions.assertEquals(record.getPath(), i);
                Assertions.assertEquals(record.getKey(), key);
                Assertions.assertEquals(record.getValue(), value);
                // load path
                long path = store.loadPath(account, i);
                Assertions.assertEquals(path, i);
            }
        }

        store.close();
    }

    @Test
    public void createSomeDataAndReadBackAfterClose() {
        final int ACCOUNT_COUNT = 10;
        final int COUNT = 10_000;
        VirtualMapDataStore store = new VirtualMapDataStore(STORE_PATH,32,32,2);
        store.open();
        // create some data for a number of accounts
        for (int a = 0; a < ACCOUNT_COUNT; a++) {
            Account account = new Account(0,0,a);
            for (int i = 0; i < COUNT; i++) {
                byte[] hash = hash(i);
                // write parent
                store.saveParentHash(account, i, hash);
                // write leaf
                store.saveLeaf(account, new VirtualRecord(hash,i, new VirtualKey(get32Bytes(i)), new VirtualValue(get32Bytes(i))));
                // write path
                store.savePath(account, i,i);
            }
        }
        store.sync();
        store.close();

        // read back and check that data
        store = new VirtualMapDataStore(STORE_PATH,32,32, 2);
        store.open();
        for (int a = 0; a < ACCOUNT_COUNT; a++) {
            Account account = new Account(0,0,a);
            for (int i = 0; i < COUNT; i++) {
                byte[] hash = hash(i);
                VirtualKey key = new VirtualKey(get32Bytes(i));
                VirtualValue value = new VirtualValue(get32Bytes(i));

                // read parent
                byte[] parentHash = store.loadParentHash(account,i);
                Assertions.assertArrayEquals(parentHash, hash);
                // read leaf by key
                VirtualRecord record = store.loadLeaf(account,key);
                Assertions.assertArrayEquals(record.getHash(), hash);
                Assertions.assertEquals(record.getPath(), i);
                Assertions.assertEquals(record.getKey(), key);
                Assertions.assertEquals(record.getValue(), value);
                // read leaf by path
                record = store.loadLeaf(account,i);
                Assertions.assertArrayEquals(record.getHash(), hash);
                Assertions.assertEquals(record.getPath(), i);
                Assertions.assertEquals(record.getKey(), key);
                Assertions.assertEquals(record.getValue(), value);
                // load path
                long path = store.loadPath(account, i);
                Assertions.assertEquals(path, i);
            }
        }

        store.close();
    }

//
//    @Test
//    public void createDeleteCreate() {
//        final int ACCOUNT_COUNT = 10;
//        final int COUNT = 10_000;
//        VirtualMapDataStore store = new VirtualMapDataStore(STORE_PATH,32,32,3);
//        store.open();
//        Map<Account,List<Integer>> validLocations = new HashMap<>();
//        // create some data for a number of accounts
//        for (int a = 0; a < ACCOUNT_COUNT; a++) {
//            Account account = new Account(0,0,a);
//            List<Integer> list = new ArrayList<>(COUNT);
//            validLocations.put(account,list);
//            for (int i = 0; i < COUNT; i++) {
//                list.add(i);
//                Hash hash = hash(i);
//                // write parent
//                store.saveParentHash(account, i, hash);
//                // write leaf
//                store.saveLeaf(account, new VirtualRecord(hash,i, new VirtualKey(get32Bytes(i)), new VirtualValue(get32Bytes(i))));
//                // write path
//                store.savePath(account, i,i);
//            }
//        }
//        // now delete some and add some more
//        for (int i = COUNT; i < (COUNT *2); i++) {
//            Account account = new Account(0,0,(int)(Math.random()*(ACCOUNT_COUNT-1)));
//            List<Integer> list = validLocations.get(account);
//            if (Math.random() < 0.7) {
//                // delete one
//                int listIndex = (int)(Math.random()*(list.size()-1));
//                int index = list.remove(listIndex);
//                System.out.println("list.contains(index) = " + list.contains(index)+" -- "+list.size()+" listIndex="+listIndex+" index="+index);
//                // delete parent
//                store.deleteParent(account, index);
//                // write leaf
//                store.deleteLeaf(account, new VirtualKey(get32Bytes(index)));
//                // write path
//                store.deletePath(account,index);
//            } else {
//                // add one
//                list.add(i);
//                Hash hash = hash(i);
//                // write parent
//                store.saveParentHash(account, i, hash);
//                // write leaf
//                store.saveLeaf(account, new VirtualRecord(hash,i, new VirtualKey(get32Bytes(i)), new VirtualValue(get32Bytes(i))));
//                // write path
//                store.savePath(account, i,i);
//            }
//        }
//
//        // read back and check that data
//        for (int a = 0; a < ACCOUNT_COUNT; a++) {
//            Account account = new Account(0,0,a);
//            List<Integer> list = validLocations.get(account);
//            System.out.println(Arrays.toString(list.toArray()));
//            System.out.println("list.size = " +list.size());
//            for (int i:list) {
//                Hash hash = hash(i);
//                System.out.println("account = " + account+"  i = " + i);
//                VirtualKey key = new VirtualKey(get32Bytes(i));
//                VirtualValue value = new VirtualValue(get32Bytes(i));
//
//                // read parent
//                Hash parentHash = store.loadParentHash(account,i);
//                Assertions.assertEquals(parentHash, hash);
//                // read leaf by key
//                VirtualRecord record = store.loadLeaf(account,key);
////                Assertions.assertEquals(record.getHash(), hash);
//                Assertions.assertEquals(record.getPath(), i);
//                Assertions.assertEquals(record.getKey(), key);
//                Assertions.assertEquals(record.getValue(), value);
//                // read leaf by path
//                record = store.loadLeaf(account,i);
////                Assertions.assertEquals(record.getHash(), hash);
//                Assertions.assertEquals(record.getPath(), i);
//                Assertions.assertEquals(record.getKey(), key);
//                Assertions.assertEquals(record.getValue(), value);
//                // load path
//                long path = store.loadPath(account, i);
//                Assertions.assertEquals(path, i);
//            }
//        }
//
//        store.close();
//    }


    private byte[] get32Bytes(int value) {
        return new byte[] {
                28,
                23,
                119,
                29,
                92,
                13,
                83,
                110,
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


    /**
     * Creates a hash containing a int
     * @return
     */
    private byte[] hash(int value) {
        byte[] hashData = new byte[384/8];
        System.arraycopy(get32Bytes(value),0, hashData,0,32);
        return hashData;
    }
}

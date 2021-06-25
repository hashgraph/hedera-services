package com.hedera.services.state.merkle.virtual;

import com.google.common.base.Stopwatch;
import com.hedera.services.state.merkle.virtualh.Account;
import com.hedera.services.state.merkle.virtualh.persistence.VirtualRecord;
import com.hedera.services.state.merkle.virtualh.persistence.mmap.VirtualMapDataStore;
import com.hedera.services.state.merkle.virtualh.VirtualKey;
import com.hedera.services.state.merkle.virtualh.VirtualValue;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class VirtualMapDataStoreTest {
    private static final int MB = 1024*1024;
    public static final Path STORE_PATH = MemMapDataStoreTest.STORE_PATH;
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
    public void createSomeDataAndReadBack() {
        final int ACCOUNT_COUNT = 10;
        final int COUNT = 10_000;
        VirtualMapDataStore store = new VirtualMapDataStore(STORE_PATH, KEY_SIZE_BYTES, DATA_SIZE_BYTES,3);
        store.open();
        System.out.println("Files.exists(STORE_PATH) = " + Files.exists(STORE_PATH));
        // create some data for a number of accounts
        for (int a = 0; a < ACCOUNT_COUNT; a++) {
            Account account = new Account(0,0,a);
            for (int i = 0; i < COUNT; i++) {
                var hash = hash(i);
                // write parent
                store.saveParentHash(account, i, hash);
                // write leaf
                store.saveLeaf(account, new VirtualRecord(hash, i, new VirtualKey(get32Bytes(i)), new VirtualValue(get32Bytes(i))));
                // write path
                store.savePath(account, i,i);
            }
        }
        // read back and check that data
        for (int a = 0; a < ACCOUNT_COUNT; a++) {
            Account account = new Account(0,0,a);
            for (int i = 0; i < COUNT; i++) {
                var hash = hash(i);
                VirtualKey key = new VirtualKey(get32Bytes(i));
                VirtualValue value = new VirtualValue(get32Bytes(i));

                // read parent
                var parentHash = store.loadParentHash(account,i);
                Assertions.assertEquals(parentHash, hash);
                // read leaf by key
                VirtualRecord record = store.loadLeaf(account,key);
                Assertions.assertEquals(record.getHash(), hash);
                Assertions.assertEquals(record.getPath(), i);
                Assertions.assertEquals(record.getKey(), key);
                Assertions.assertEquals(record.getValue(), value);
                // read leaf by path
                record = store.loadLeaf(account,i);
                Assertions.assertEquals(record.getHash(), hash);
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
                var hash = hash(i);
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
        Stopwatch stopwatch = Stopwatch.createStarted();
        store.open();
        stopwatch.stop(); // optional
        System.out.println("Time elapsed: "+ (stopwatch.elapsed(TimeUnit.MILLISECONDS)/1000d)+" seconds");
        for (int a = 0; a < ACCOUNT_COUNT; a++) {
            Account account = new Account(0,0,a);
            for (int i = 0; i < COUNT; i++) {
                var hash = hash(i);
                VirtualKey key = new VirtualKey(get32Bytes(i));
                VirtualValue value = new VirtualValue(get32Bytes(i));

                // read parent
                var parentHash = store.loadParentHash(account,i);
                Assertions.assertEquals(parentHash, hash);
                // read leaf by key
                VirtualRecord record = store.loadLeaf(account,key);
                Assertions.assertEquals(record.getHash(), hash);
                Assertions.assertEquals(record.getPath(), i);
                Assertions.assertEquals(record.getKey(), key);
                Assertions.assertEquals(record.getValue(), value);
                // read leaf by path
                record = store.loadLeaf(account,i);
                Assertions.assertEquals(record.getHash(), hash);
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

    @Test
    public void createSomeDataAndReadBackConcurrent() {
        final int ACCOUNT_COUNT = 10;
        final int COUNT = 10_000;
        VirtualMapDataStore store = new VirtualMapDataStore(STORE_PATH,32,32,3);
        store.open();
        System.out.println("Files.exists(STORE_PATH) = " + Files.exists(STORE_PATH));
        // create some data for a number of accounts
        Stopwatch stopwatch = Stopwatch.createStarted();
        IntStream.range(0, ACCOUNT_COUNT).parallel().forEach(a -> {
            Account account = new Account(0,0,a);
            Stream.of(0,1,2).parallel().forEach(op -> {
                switch (op) {
                    case 0:
                        IntStream.range(0, COUNT).parallel().forEach(i -> {
                            var hash = hash(i);
                            // write parent
                            store.saveParentHash(account, i, hash);
                        });
                        break;
                    case 1:
                        IntStream.range(0, COUNT).parallel().forEach(i -> {
                            var hash = hash(i);
                            // write leaf
                            store.saveLeaf(account, new VirtualRecord(hash,i, new VirtualKey(get32Bytes(i)), new VirtualValue(get32Bytes(i))));
                        });
                        break;
                    case 2:
                        // write parent
                        IntStream.range(0, COUNT).parallel().forEach(i -> {
                            // write path
                            store.savePath(account, i,i);
                        });
                        break;
                }
            });
        });
        stopwatch.stop(); // optional
        System.out.println("Create 10,000 leaves, parents and paths took : "+ (stopwatch.elapsed(TimeUnit.MILLISECONDS)/1000d)+" seconds");

        System.out.println("check counts");
        // check counts
        for (int a = 0; a < ACCOUNT_COUNT; a++) {
            Account account = new Account(0, 0, a);
            Assertions.assertEquals(COUNT, store.leafCount(account));
        }

        System.out.println("check all the data");
        // check all the data
        for (int a = 0; a < ACCOUNT_COUNT; a++) {
            Account account = new Account(0,0,a);
            for (int i = 0; i < COUNT; i++) {
                var hash = hash(i);
                VirtualKey key = new VirtualKey(get32Bytes(i));
                VirtualValue value = new VirtualValue(get32Bytes(i));

                // read parent
                var parentHash = store.loadParentHash(account,i);
                Assertions.assertEquals(parentHash, hash, "a="+account.accountNum()+" i="+i+" parentHash="+toLongsString(parentHash)+" hash="+toLongsString(hash));
                // read leaf by key
                VirtualRecord record = store.loadLeaf(account,key);
                Assertions.assertEquals(record.getHash(), hash,"a="+account.accountNum()+" i="+i+" record.getHash()="+toLongsString(record.getHash())+" hash="+toLongsString(hash));
                Assertions.assertEquals(record.getPath(), i);
                Assertions.assertEquals(record.getKey(), key);
                Assertions.assertEquals(record.getValue(), value);
                // read leaf by path
                record = store.loadLeaf(account,i);
                Assertions.assertEquals(record.getHash(), hash, "a="+account.accountNum()+" i="+i+" record.getHash()="+toLongsString(record.getHash())+" hash="+toLongsString(hash));
                Assertions.assertEquals(record.getPath(), i);
                Assertions.assertEquals(record.getKey(), key);
                Assertions.assertEquals(record.getValue(), value);
                // load path
                long path = store.loadPath(account, i);
                Assertions.assertEquals(path, i);
            }
        }

        System.out.println("do a bunch of concurrent reads and updates");
        // do a bunch of concurrent reads and updates
        RANDOM.ints(10_000,0, COUNT).parallel().forEach(i -> {
            Account account = new Account(0,0,RANDOM.nextInt(ACCOUNT_COUNT));
            var hash = hash(i);
            VirtualKey key = new VirtualKey(get32Bytes(i));
            VirtualValue value = new VirtualValue(get32Bytes(i));
            VirtualRecord record;
            System.out.print(',');

            switch(RANDOM.nextInt(3)) {
                case 0:
                    // read parent
                    var parentHash = store.loadParentHash(account,i);
                    Assertions.assertEquals(hash, parentHash,() -> {
                        return "a="+account.accountNum()+" i="+i+" parentHash="+toLongsString(parentHash)+" hash="+toLongsString(hash)+"\n";
                    });
                    break;
                case 1:
                    // read leaf by key
                    record = store.loadLeaf(account,key);
                    Assertions.assertEquals(hash, record.getHash(),() -> {
                        return "a="+account.accountNum()+" i="+i+" leafHash="+toLongsString(record.getHash())+" hash="+toLongsString(hash)+"\n";
                    });
                    Assertions.assertEquals(i, record.getPath());
                    Assertions.assertEquals(key, record.getKey());
                    Assertions.assertEquals(value, record.getValue());
                    break;
                case 2:
                    // read leaf by path
                    record = store.loadLeaf(account,i);
                    Assertions.assertEquals(hash, record.getHash(),() -> {
                        return "a="+account.accountNum()+" i="+i+" leafHash="+toLongsString(record.getHash())+" hash="+toLongsString(hash)+"\n";
                    });
                    Assertions.assertEquals(i, record.getPath());
                    Assertions.assertEquals(key, record.getKey());
                    Assertions.assertEquals(value, record.getValue());
                    break;
                case 3:
                    // load path
                    long path = store.loadPath(account, i);
                    Assertions.assertEquals(i,path);
                    break;
            }
        });

        System.out.println("\n----------------------------------\nDONE");
        store.close();
    }

    /**
     * Create a byte containing the given value as 4 longs
     *
     * @param value the value to store in array
     * @return byte array of 4 longs
     */
    private byte[] get32Bytes(int value) {
        byte b0 = (byte)(value >>> 24);
        byte b1 = (byte)(value >>> 16);
        byte b2 = (byte)(value >>> 8);
        byte b3 = (byte)value;
        return new byte[] {
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3
        };
    }


    /**
     * Creates a hash containing a int repeated 6 times as longs
     *
     * @return byte array of 6 longs
     */
    private Hash hash(int value) {
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
}

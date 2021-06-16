package com.hedera.services.state.merkle.virtual;

import com.google.common.base.Stopwatch;
import com.hedera.services.state.merkle.virtual.persistence.VirtualRecord;
import com.hedera.services.state.merkle.virtual.persistence.mmap.VirtualMapDataStore;
import org.junit.jupiter.api.Assertions;

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

public class VirtualMapDataStoreFuzzer {
    private static final Path STORE_PATH = Path.of("store");
    static {
        System.out.println("STORE_PATH = " + STORE_PATH.toAbsolutePath());
    }
    private static final Random RANDOM = new Random(1234);

    public static void main(String[] args) {
        // delete old files
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
                            byte[] hash = hash(i);
                            // write parent
                            store.saveParentHash(account, i, hash);
                        });
                        break;
                    case 1:
                        IntStream.range(0, COUNT).parallel().forEach(i -> {
                            byte[] hash = hash(i);
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
                byte[] hash = hash(i);
                VirtualKey key = new VirtualKey(get32Bytes(i));
                VirtualValue value = new VirtualValue(get32Bytes(i));

                // read parent
                byte[] parentHash = store.loadParentHash(account,i);
                Assertions.assertArrayEquals(parentHash, hash, "a="+account.accountNum()+" i="+i+" parentHash="+toLongsString(parentHash)+" hash="+toLongsString(hash));
                // read leaf by key
                VirtualRecord record = store.loadLeaf(account,key);
                Assertions.assertArrayEquals(record.getHash(), hash,"a="+account.accountNum()+" i="+i+" record.getHash()="+toLongsString(record.getHash())+" hash="+toLongsString(hash));
                Assertions.assertEquals(record.getPath(), i);
                Assertions.assertEquals(record.getKey(), key);
                Assertions.assertEquals(record.getValue(), value);
                // read leaf by path
                record = store.loadLeaf(account,i);
                Assertions.assertArrayEquals(record.getHash(), hash, "a="+account.accountNum()+" i="+i+" record.getHash()="+toLongsString(record.getHash())+" hash="+toLongsString(hash));
                Assertions.assertEquals(record.getPath(), i);
                Assertions.assertEquals(record.getKey(), key);
                Assertions.assertEquals(record.getValue(), value);
                // load path
                long path = store.loadPath(account, i);
                Assertions.assertEquals(path, i);
            }
        }

        // do a bunch of concurrent reads and updates
        System.out.println("do a bunch of concurrent reads and updates");
        stopwatch.reset();
        stopwatch.start();
        final long OPS = 100_000_000l;
        RANDOM.ints(OPS,0, COUNT).parallel().forEach(i -> {
            Account account = new Account(0,0,RANDOM.nextInt(ACCOUNT_COUNT));
            byte[] hash = hash(i);
            VirtualKey key = new VirtualKey(get32Bytes(i));
            VirtualValue value = new VirtualValue(get32Bytes(i));
            VirtualRecord record;

            switch(RANDOM.nextInt(7)) {
                case 0:
                    // read parent
                    byte[] parentHash = store.loadParentHash(account,i);
                    if (!Arrays.equals(hash,parentHash)){
                        System.err.println("Parent Hash Doesn't Match :: a="+account.accountNum()+" i="+i+" parentHash="+toLongsString(parentHash)+" hash="+toLongsString(hash));
                    }
                    break;
                case 1:
                    // read leaf by key
                    record = store.loadLeaf(account,key);
                    if (!Arrays.equals(hash,record.getHash())){
                        System.err.println("Leaf Hash Doesn't Match :: a="+account.accountNum()+" i="+i+" parentHash="+toLongsString(record.getHash())+" hash="+toLongsString(hash));
                    }
                    Assertions.assertEquals(i, record.getPath());
                    Assertions.assertEquals(key, record.getKey());
                    Assertions.assertEquals(value, record.getValue());
                    break;
                case 2:
                    // read leaf by path
                    record = store.loadLeaf(account,i);
                    if (!Arrays.equals(hash,record.getHash())){
                        System.err.println("Leaf Hash Doesn't Match :: a="+account.accountNum()+" i="+i+" parentHash="+toLongsString(record.getHash())+" hash="+toLongsString(hash));
                    }
                    Assertions.assertEquals(i, record.getPath());
                    Assertions.assertEquals(key, record.getKey());
                    Assertions.assertEquals(value, record.getValue());
                    break;
                case 3:
                    // load path
                    long path = store.loadPath(account, i);
                    Assertions.assertEquals(i,path);
                    break;
                case 4:
                    // write parent
                    store.saveParentHash(account, i, hash);
                    break;
                case 5:
                    // write leaf
                    store.saveLeaf(account, new VirtualRecord(hash,i, new VirtualKey(get32Bytes(i)), new VirtualValue(get32Bytes(i))));
                    break;
                case 6:
                    // write path
                    store.savePath(account, i,i);
                    break;
            }
        });
        stopwatch.stop();
        double tookSeconds = ((double)stopwatch.elapsed(TimeUnit.MILLISECONDS)/1000d);
        System.out.println(OPS+" Random Ops Took : "+ tookSeconds+" seconds at "+(int)(OPS/tookSeconds)+" ops/sec");

        System.out.println("\n----------------------------------\nDONE");
        store.close();
    }


    /**
     * Create a byte containing the given value as 4 longs
     *
     * @param value the value to store in array
     * @return byte array of 4 longs
     */
    private static byte[] get32Bytes(int value) {
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
    private static byte[] hash(int value) {
        byte b0 = (byte)(value >>> 24);
        byte b1 = (byte)(value >>> 16);
        byte b2 = (byte)(value >>> 8);
        byte b3 = (byte)value;
        return new byte[] {
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3
        };
    }

    public static String toLongsString(byte[] bytes) {
        LongBuffer longBuf =
                ByteBuffer.wrap(bytes)
                        .order(ByteOrder.BIG_ENDIAN)
                        .asLongBuffer();
        long[] array = new long[longBuf.remaining()];
        longBuf.get(array);
        return Arrays.toString(array);
    }
}

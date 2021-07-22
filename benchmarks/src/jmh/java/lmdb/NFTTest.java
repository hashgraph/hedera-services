package lmdb;

import fcmmap.FCVirtualMapTestUtils;
import org.lmdbjava.*;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import static fcmmap.FCVirtualMapTestUtils.toLongsString;
import static org.lmdbjava.DbiFlags.MDB_CREATE;
import static org.lmdbjava.DbiFlags.MDB_INTEGERKEY;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class NFTTest {
    private final static long GB = 1024*1024*1024;
    private final static long TB = GB*1024;
    private final static Random RANDOM = new Random(1234);
    private final static long DATA_SIZE = 1000_000_000;
    private final static long CHUNK_SIZE = 1_000_000;
//    private final static long DATA_SIZE = 1000;
//    private final static long CHUNK_SIZE = 100;
    private final static int NFT_SIZE = 1500;

    public static void main(String[] args) {
        File dir = new File("NFTTest");
        boolean dirExists = dir.exists();

//        FCVirtualMapTestUtils.deleteDirectoryAndContents(dir.toPath());
        if (!dirExists)dir.mkdirs();
        // random NFT Data
        ByteBuffer randomData = ByteBuffer.allocateDirect(NFT_SIZE);
//        randomData.order(ByteOrder.LITTLE_ENDIAN);
//        byte[] randomDataBytes = new byte[randomData.limit()];
//        RANDOM.nextBytes(randomDataBytes);
//        randomData.put(randomDataBytes);
        while (randomData.remaining() > 8) randomData.putLong(-1); // fill with -1s
        // open env
        try (Env<ByteBuffer> env = Env.create(ByteBufferProxy.PROXY_OPTIMAL)
                // LMDB also needs to know how large our DB might be. Over-estimating is OK.
                .setMapSize((long) (1.9d * TB))
                // LMDB also needs to know how many DBs (Dbi) we want to store in this Env.
                .setMaxDbs(4)
                // assume max readers is max number of cores
                .setMaxReaders(Runtime.getRuntime().availableProcessors())
                // Now let's open the Env. The same path can be concurrently opened and
                // used in different processes, but do not open the same path twice in
                // the same process at the same time.
                .open(dir)) {
//                    .open(dir, EnvFlags.MDB_WRITEMAP, EnvFlags.MDB_NOSYNC, EnvFlags.MDB_NOMETASYNC, EnvFlags.MDB_NORDAHEAD);
            // We need a Dbi for each DB. A Dbi roughly equates to a sorted map. The
            // MDB_CREATE flag causes the DB to be created if it doesn't already exist.
            Dbi<ByteBuffer> db = env.openDbi("db", MDB_CREATE, MDB_INTEGERKEY);

            // key buffer
            ByteBuffer key = ByteBuffer.allocateDirect(Long.BYTES);
            key.order(ByteOrder.LITTLE_ENDIAN);
            // create random data
            if (!dirExists){
                List<Double> perSecs = new CopyOnWriteArrayList<>();
                long START = System.currentTimeMillis();
                var txn = env.txnWrite();
                for (long i = 0; i < DATA_SIZE; i++) {
                    if (i != 0 && i % CHUNK_SIZE == 0) {
                        txn.commit();
                        txn.close();
                        perSecs.add(printTestUpdate(START, i, DATA_SIZE,CHUNK_SIZE,"Written"));
                        START = System.currentTimeMillis();
                        txn = env.txnWrite();
                    }
                    key.rewind();
                    key.putLong(i);
                    key.flip();
                    randomData.putLong(0,i);
                    randomData.rewind();
                    db.put(txn, key, randomData, PutFlags.MDB_APPEND);
                }
                txn.commit();
                txn.close();
                System.out.println("Written " + DATA_SIZE + " NFTS results: " + perSecs.stream().mapToDouble(d -> d).summaryStatistics());
            }
            // read data
            readWithGet(env,db);
            readWithCursor(env,db);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void readWithGet(Env<ByteBuffer> env, Dbi<ByteBuffer> db) throws Exception {
        System.out.println("======== STARTING READING WITH GET ===========================================");
        // key buffer
        ByteBuffer key = ByteBuffer.allocateDirect(Long.BYTES);
        key.order(ByteOrder.LITTLE_ENDIAN);
        // read data
        long readChunkSize = CHUNK_SIZE/1000;
        List<Double> perSecs = new CopyOnWriteArrayList<>();
        long START = System.currentTimeMillis();
        try (Txn<ByteBuffer> txn = env.txnRead()) {
            for (long j = 0; j < DATA_SIZE; j++) {
                int i = RANDOM.nextInt((int) DATA_SIZE);
                if (j != 0 && j % readChunkSize == 0) {
                    perSecs.add(printTestUpdate(START, j, DATA_SIZE, readChunkSize, "Read"));
                    START = System.currentTimeMillis();
                }
                key.rewind();
                key.putLong(i);
                key.flip();
                // get value
                ByteBuffer resultBuf = db.get(txn, key);
                // check the first 8 bytes
                long readLong = resultBuf.getLong(0);
                if (readLong != (long) i) {
                    System.err.println("Bad data readLong " + readLong + " not equal to i " + i + " -- " + toLongsString(resultBuf));
                    throw new RuntimeException();
                }
            }
        }
        System.out.println("Read " + DATA_SIZE + " NFTS results: " + perSecs.stream().mapToDouble(d -> d).summaryStatistics());
    }

    private static void readWithCursor(Env<ByteBuffer> env, Dbi<ByteBuffer> db) throws Exception {
        System.out.println("======== STARTING READING WITH CURSOR ===========================================");
        // key buffer
        ByteBuffer key = ByteBuffer.allocateDirect(Long.BYTES);
        key.order(ByteOrder.LITTLE_ENDIAN);
        // read data
        long readChunkSize = CHUNK_SIZE/1000;
        List<Double> perSecs = new CopyOnWriteArrayList<>();
        long START = System.currentTimeMillis();
        try (Txn<ByteBuffer> txn = env.txnRead()) {
            Cursor<ByteBuffer> cursor = db.openCursor(txn);
            for (long j = 0; j < DATA_SIZE; j++) {
                int i = RANDOM.nextInt((int) DATA_SIZE);
                if (j != 0 && j % readChunkSize == 0) {
                    perSecs.add(printTestUpdate(START, j, DATA_SIZE, readChunkSize, "Read"));
                    START = System.currentTimeMillis();
                }
                key.rewind();
                key.putLong(i);
                key.flip();
                // get value
//                    ByteBuffer resultBuf = db.get(txn, key);
                // read into random data
//                    resultBuf.rewind();
//                    Cursor cursor = db.openCursor(txn);
                boolean found = cursor.get(key, GetOp.MDB_SET);
                if (!found) {
                    System.err.println("Not Found i " + i);
                    throw new RuntimeException();
                }
                ByteBuffer resultBuf = cursor.val();
//                    cursor.close();
                // check the first 8 bytes
                long readLong = resultBuf.getLong(0);
                if (readLong != (long) i) {
                    System.err.println("Bad data readLong " + readLong + " not equal to i " + i + " -- " + toLongsString(resultBuf));
                    throw new RuntimeException();
                }
            }
        }
        System.out.println("Read " + DATA_SIZE + " NFTS results: " + perSecs.stream().mapToDouble(d -> d).summaryStatistics());
    }

    private static double printTestUpdate(long start, long count, long totalCount,long chunkSize, String msg) {
        long took = System.currentTimeMillis() - start;
        double timeSeconds = (double)took/1000d;
        double perSecond = (double)chunkSize / timeSeconds;
        System.out.printf("%s : [%,d] of [%,d] at %,.0f per/sec, took %,.2f seconds\n",msg,count,totalCount, perSecond, timeSeconds);
        return perSecond;
    }

}

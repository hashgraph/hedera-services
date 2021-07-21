package lmdb;

import fcmmap.FCVirtualMapTestUtils;
import org.lmdbjava.*;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.lmdbjava.DbiFlags.MDB_CREATE;
import static org.lmdbjava.DbiFlags.MDB_INTEGERKEY;

public class NFTTest {
    private final static long GB = 1024*1024*1024;
    private final static long TB = GB*1024;
    private final static Random RANDOM = new Random(1234);
    private final static long DATA_SIZE = 10_000_000;
    private final static long CHUNK_SIZE = 1_000_000;
    private final static int NFT_SIZE = 1500;

    public static void main(String[] args) {
        try {
            File dir = new File("NFTTest");
            FCVirtualMapTestUtils.deleteDirectoryAndContents(dir.toPath());
            dir.mkdirs();
            Env env = Env.create(ByteBufferProxy.PROXY_OPTIMAL)
                    // LMDB also needs to know how large our DB might be. Over-estimating is OK.
                    .setMapSize((long) (0.5d * TB))
                    // LMDB also needs to know how many DBs (Dbi) we want to store in this Env.
                    .setMaxDbs(4)
                    // assume max readers is max number of cores
                    .setMaxReaders(Runtime.getRuntime().availableProcessors())
                    // Now let's open the Env. The same path can be concurrently opened and
                    // used in different processes, but do not open the same path twice in
                    // the same process at the same time.
                    .open(dir);
//                    .open(dir, EnvFlags.MDB_WRITEMAP, EnvFlags.MDB_NOSYNC, EnvFlags.MDB_NOMETASYNC, EnvFlags.MDB_NORDAHEAD);
            // We need a Dbi for each DB. A Dbi roughly equates to a sorted map. The
            // MDB_CREATE flag causes the DB to be created if it doesn't already exist.
            Dbi db = env.openDbi("db", MDB_CREATE, MDB_INTEGERKEY);

            // random NFT Data
            ByteBuffer randomData = ByteBuffer.allocateDirect(NFT_SIZE);
            byte[] randomDataBytes = new byte[randomData.limit()];
            RANDOM.nextBytes(randomDataBytes);
            randomData.put(randomDataBytes);
            // key buffer
            ByteBuffer key = ByteBuffer.allocateDirect(Long.BYTES);
            key.order(ByteOrder.LITTLE_ENDIAN);
            // create random data
            {
                List<Double> perSecs = new CopyOnWriteArrayList<>();
                long START = System.currentTimeMillis();
                var txn = env.txnWrite();
                for (long i = 0; i < DATA_SIZE; i++) {
                    if (i != 0 && i % CHUNK_SIZE == 0) {
                        txn.commit();
                        perSecs.add(printTestUpdate(START, i, DATA_SIZE,"Written"));
                        START = System.currentTimeMillis();
                        txn = env.txnWrite();
                    }
                    key.rewind();
                    key.putLong(i);
                    key.flip();
                    randomData.rewind();
                    randomData.putLong(i);
                    db.put(txn, key, randomData, PutFlags.MDB_APPEND);
                }
                txn.commit();
                System.out.println("Written " + DATA_SIZE + " NFTS results: " + perSecs.stream().mapToDouble(d -> d).summaryStatistics());
            }
            // read data
            {
                List<Double> perSecs = new CopyOnWriteArrayList<>();
                long START = System.currentTimeMillis();
                Txn<ByteBuffer> txn = env.txnRead();
                for (long j = 0; j < DATA_SIZE; j++) {
                    int i = RANDOM.nextInt((int)DATA_SIZE);
                    if (j != 0 && j % CHUNK_SIZE == 0) {
                        txn.commit();
                        perSecs.add(printTestUpdate(START, j, DATA_SIZE,"Read"));
                        START = System.currentTimeMillis();
                        txn = env.txnWrite();
                    }
                    key.rewind();
                    key.putLong(i);
                    key.flip();
//                    db.get(txn, key);
                    // read into random data
                    randomData.rewind();
                    Cursor cursor = db.openCursor(txn);
                    cursor.get(key,randomData,SeekOp.MDB_FIRST);
                    // check the first 8 bytes
                    randomData.rewind();
                    long readLong = randomData.getLong();
                    if (readLong != i) System.err.println("Bad data readLong "+readLong+" not equal to i "+i);
                    cursor.close();
                }
                txn.commit();
                System.out.println("Read " + DATA_SIZE + " NFTS results: " + perSecs.stream().mapToDouble(d -> d).summaryStatistics());
            }

            env.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static double printTestUpdate(long start, long count, long totalCount, String msg) {
        long took = System.currentTimeMillis() - start;
        double timeSeconds = (double)took/1000d;
        double perSecond = (double)CHUNK_SIZE / timeSeconds;
        System.out.printf("%s : [%,d] of [%,d] at %,.0f per/sec, took %,.2f seconds\n",msg,count,totalCount, perSecond, timeSeconds);
        return perSecond;
    }

}

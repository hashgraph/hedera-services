package contract;

import com.hedera.services.state.merkle.virtual.ContractKey;
import com.hedera.services.state.merkle.virtual.ContractUint256;
import com.hedera.services.store.models.Id;
import fcmmap.FCVirtualMapTestUtils;
import org.lmdbjava.Txn;
import org.openjdk.jmh.annotations.*;
import rockdb.VFCDataSourceLmdb;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@SuppressWarnings("jol")
@State(Scope.Thread)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class VFCDataSourceLMDBGetBench {
    private static final long MB = 1024*1024;
    public static final Path STORE_PATH = Path.of("store");
    public static final Id ID = new Id(1,2,3);

    @Param({"1000000"})
    public long numEntities;

    // state
    public VFCDataSourceLmdb<ContractKey,ContractUint256> dataSource;
    public Random random = new Random(1234);
    public int iteration = 0;
    private ContractKey key1 = null;
    private ContractKey key2 = null;
    private long randomLeafIndex1;
    private long randomLeafIndex2;
    private long randomNodeIndex1;
    private long randomNodeIndex2;
    private long nextLeafIndex;

    @Setup(Level.Trial)
    public void setup() {
        System.out.println("LMDB Benchmark");
        try {
            // delete any old store
//            FCVirtualMapTestUtils.deleteDirectoryAndContents(STORE_PATH);
            final boolean storeExists = Files.exists(STORE_PATH);
            // get slot index suppliers
//            dataSource = VFCDataSourceImpl.createOnDisk(STORE_PATH,numEntities*2,
//                    ContractUint256.SERIALIZED_SIZE, ContractUint256::new,
//                    ContractUint256.SERIALIZED_SIZE, ContractUint256::new, true); //ContractKey
//            dataSource = new VFCDataSourceRocksDb(
//                    ContractKey.SERIALIZED_SIZE, ContractKey::new,
//                    ContractUint256.SERIALIZED_SIZE, ContractUint256::new,
//                    STORE_PATH);
            dataSource = new VFCDataSourceLmdb<>(
                    ContractKey.SERIALIZED_SIZE, ContractKey::new,
                    ContractUint256.SERIALIZED_SIZE, ContractUint256::new,
                    STORE_PATH);
            // create data
            if (!storeExists) {

                long printStep = Math.min(1_000_000, numEntities / 4);
                long START = System.currentTimeMillis();
                Object transaction = dataSource.startTransaction();
                for (long i = 0; i < numEntities; i++) {
                    if (i != 0 && i % printStep == 0) {
                        dataSource.commitTransaction(transaction);
                        printUpdate(START, printStep, ContractUint256.SERIALIZED_SIZE, "Created " + i + " Nodes");
                        START = System.currentTimeMillis();
                        transaction = dataSource.startTransaction();
                    }
                    dataSource.saveInternal(transaction, i, FCVirtualMapTestUtils.hash((int) i));
                }
                dataSource.commitTransaction(transaction);

                START = System.currentTimeMillis();
                transaction = dataSource.startTransaction();
                for (long i = 0; i < numEntities; i++) {
                    if (i != 0 && i % printStep == 0) {
                        dataSource.commitTransaction(transaction);
                        printUpdate(START, printStep, ContractUint256.SERIALIZED_SIZE, "Created " + i + " Leaves");
                        START = System.currentTimeMillis();
                        transaction = dataSource.startTransaction();
                    }
                    dataSource.addLeaf(transaction,numEntities + i, new ContractKey(new Id(0,0,i),new ContractUint256(i)), new ContractUint256(i), FCVirtualMapTestUtils.hash((int) i));
                }
                dataSource.commitTransaction(transaction);
                System.out.println("================================================================================");
                dataSource.printStats();
                System.out.println("================================================================================");
                nextLeafIndex = numEntities;
                // reset iteration counter
                iteration = 0;
            } else {
                System.out.println("Loaded existing data");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void printUpdate(long START, long count, int size, String msg) {
        long took = System.currentTimeMillis() - START;
        double timeSeconds = (double)took/1000d;
        double perSecond = (double)count / timeSeconds;
        double mbPerSec = (perSecond*size)/MB;
        System.out.printf("%s : [%,d] writes at %,.0f per/sec %,.1f Mb/sec, took %,.2f seconds\n",msg, count, perSecond,mbPerSec, timeSeconds);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        dataSource.close();
        FCVirtualMapTestUtils.printDirectorySize(STORE_PATH);
    }

    @Setup(Level.Invocation)
    public void randomIndex(){
        randomNodeIndex1 = (long)(random.nextDouble()*numEntities);
        randomNodeIndex2 = (long)(random.nextDouble()*numEntities);
        randomLeafIndex1 = numEntities + randomNodeIndex1;
        randomLeafIndex2 = numEntities + randomNodeIndex2;
        key1 = new ContractKey(new Id(0,0,randomNodeIndex1),new ContractUint256(randomNodeIndex1));
        key2 = new ContractKey(new Id(0,0,randomNodeIndex2),new ContractUint256(randomNodeIndex2));
    }

    @Benchmark
    public void w0_updateHash() throws Exception {
        dataSource.saveInternal(randomLeafIndex1, FCVirtualMapTestUtils.hash((int) randomLeafIndex1));
    }

    @Benchmark
    public void w1_updateLeafValue() throws Exception {
        dataSource.updateLeaf(randomLeafIndex1,new ContractUint256(randomNodeIndex2), FCVirtualMapTestUtils.hash((int) randomNodeIndex2));
    }

    @Benchmark
    public void w2_addLeaf() throws Exception {
        dataSource.addLeaf(numEntities + nextLeafIndex, new ContractKey(new Id(0,0,nextLeafIndex),new ContractUint256(nextLeafIndex)), new ContractUint256(nextLeafIndex), FCVirtualMapTestUtils.hash((int) nextLeafIndex));
        nextLeafIndex++;
    }

    @Benchmark
    public void w3_moveLeaf() throws Exception {
        dataSource.updateLeaf(randomLeafIndex1,randomLeafIndex2,key1);
    }

    /**
     * This is designed to mimic our transaction round
     */
    @Benchmark
    public void t_transaction() throws Exception {
        IntStream.range(0,3).parallel().forEach(thread -> {
            try {
                switch (thread) {
                    case 0 -> {
                        // this is the transaction thread that reads leaf values
                        for (int i = 0; i < 10_000; i++) {
                            randomNodeIndex1 = (long) (random.nextDouble() * numEntities);
                            key2 = new ContractKey(new Id(0, 0, randomNodeIndex1), new ContractUint256(randomNodeIndex1));
                            dataSource.loadLeafValue(key2);
                        }
                    }
                    case 1 -> {
                        // this is the hashing thread that reads hashes
                        for (int i = 0; i < 10_000; i++) {
                            randomNodeIndex1 = (long) (random.nextDouble() * numEntities);
                            dataSource.loadHash(randomNodeIndex1);
                        }
                    }
                    case 2 -> {
                        // this is the archive thread that writes nodes and leaves
                        final Object transaction = dataSource.startTransaction();
                        for (int i = 0; i < 10_000; i++) { // update 10k internal hashes
                            randomNodeIndex1 = (long) (random.nextDouble() * numEntities);
                            dataSource.saveInternal(transaction,randomLeafIndex1, FCVirtualMapTestUtils.hash((int) randomLeafIndex1));
                        }
                        for (int i = 0; i < 10_000; i++) { // update 10k leaves
                            randomNodeIndex1 = (long) (random.nextDouble() * numEntities);
                            randomLeafIndex1 = numEntities + randomNodeIndex1;
                            dataSource.updateLeaf(transaction,randomLeafIndex1, new ContractUint256(randomNodeIndex1), FCVirtualMapTestUtils.hash((int) randomNodeIndex1));
                        }
                        dataSource.commitTransaction(transaction);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        dataSource.updateLeaf(randomLeafIndex1,randomLeafIndex2,key1);
    }
    @Benchmark
    public void r_loadLeafPath() throws Exception {
        dataSource.loadLeafPath(key1);
    }

    @Benchmark
    public void r_loadLeafKey() throws Exception {
        dataSource.loadLeafKey(randomLeafIndex1);
    }

    @Benchmark
    public void r_loadLeafValueByPath() throws Exception {
        dataSource.loadLeafValue(randomLeafIndex2);
    }

    @Benchmark
    public void r_loadLeafValueByKey() throws Exception {
        dataSource.loadLeafValue(key2);
    }

    @Benchmark
    public void r_loadHash() throws Exception {
        dataSource.loadHash(randomNodeIndex1);
    }

}

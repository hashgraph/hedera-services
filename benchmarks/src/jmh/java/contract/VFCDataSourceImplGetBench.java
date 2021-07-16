package contract;

import com.hedera.services.state.merkle.virtual.ContractKey;
import com.hedera.services.state.merkle.virtual.ContractUint256;
import com.hedera.services.store.models.Id;
import com.swirlds.fcmap.VFCDataSource;
import fcmmap.FCVirtualMapTestUtils;
import org.openjdk.jmh.annotations.*;
import rockdb.VFCDataSourceLmdbConcurrent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("jol")
@State(Scope.Thread)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class VFCDataSourceImplGetBench {
    private static final long MB = 1024*1024;
    public static final Path STORE_PATH = Path.of("store");
    public static final Id ID = new Id(1,2,3);

    @Param({"1000000"})
    public long numEntities;

    // state
    public VFCDataSource<ContractKey,ContractUint256> dataSource;
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
        System.out.println("Clean Setup");
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
            dataSource = new VFCDataSourceLmdbConcurrent(
                    ContractKey.SERIALIZED_SIZE, ContractKey::new,
                    ContractUint256.SERIALIZED_SIZE, ContractUint256::new,
                    STORE_PATH);
            // create data
            if (!storeExists) {

                long printStep = Math.min(1_000_000, numEntities / 4);
                long START = System.currentTimeMillis();
                for (long i = 0; i < numEntities; i++) {
                    if (i != 0 && i % printStep == 0) {
                        printUpdate(START, printStep, ContractUint256.SERIALIZED_SIZE, "Created " + i + " Nodes");
                        START = System.currentTimeMillis();
                    }
                    dataSource.saveInternal(i, FCVirtualMapTestUtils.hash((int) i));
                }
                START = System.currentTimeMillis();
                for (long i = 0; i < numEntities; i++) {
                    if (i != 0 && i % printStep == 0) {
                        printUpdate(START, printStep, ContractUint256.SERIALIZED_SIZE, "Created " + i + " Leaves");
                        START = System.currentTimeMillis();
                    }
                    dataSource.addLeaf(numEntities + i, new ContractKey(new Id(0,0,i),new ContractUint256(i)), new ContractUint256(i), FCVirtualMapTestUtils.hash((int) i));
                }
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
        try {
            dataSource.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
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
    public void w3_MoveLeaf() throws Exception {
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

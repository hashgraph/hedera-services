package contract;

import com.hedera.services.state.merkle.virtual.ContractKey;
import com.hedera.services.state.merkle.virtual.ContractUint256;
import com.hedera.services.store.models.Id;
import fcmmap.FCVirtualMapTestUtils;
import org.openjdk.jmh.annotations.*;
import lmdb.SequentialInsertsVFCDataSource;
import lmdb.VFCDataSourceLmdb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@SuppressWarnings({"jol", "DuplicatedCode", "DefaultAnnotationParam", "SameParameterValue"})
@State(Scope.Thread)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class VFCDataSource4LmdbBench {
    private static final long MB = 1024*1024;

    @Param({"10000000"})
    public long numEntities;
    @Param({"5"})
    public int hashThreads;
    @Param({"4"})
    public int numDataSources;

    // state
    public Path storePath;
    public SequentialInsertsVFCDataSource<ContractKey,ContractUint256>[] dataSources;
    public Random random = new Random(1234);
    public int iteration = 0;
    private ContractKey key1 = null;
    private ContractKey key2 = null;
    private int randomDataSource;
    private long randomLeafIndex1;
    private long randomLeafIndex2;
    private long randomNodeIndex1;
    private long randomNodeIndex2;
    private long nextLeafIndex;

    @Setup(Level.Trial)
    public void setup() {
        storePath = Path.of("store-lmdb4");
        System.out.println("Clean Setup");
        try {
            // delete any old store
//            FCVirtualMapTestUtils.deleteDirectoryAndContents(STORE_PATH);
            final boolean storeExists = Files.exists(storePath);
            // get slot index suppliers
            //noinspection unchecked
            dataSources = new SequentialInsertsVFCDataSource[numDataSources];
            for (int i = 0; i < dataSources.length; i++) {
                dataSources[i] = new VFCDataSourceLmdb<>(
                                    ContractKey.SERIALIZED_SIZE, ContractKey::new,
                                    ContractUint256.SERIALIZED_SIZE, ContractUint256::new,
                                    storePath.resolve("store_"+i));
            }
            // create data
            if (!storeExists) {
                System.out.println("================================================================================");
                System.out.println("Creating data ...");
                long printStep = Math.min(500_000, numEntities / 4);

                IntStream.range(0,numDataSources).parallel().forEach(dataSourceIndex -> {
                    final var  dataSource = dataSources[dataSourceIndex];
                    long START = System.currentTimeMillis();
                    Object transaction = dataSource.startTransaction();
                    for (int i = 0; i < numEntities; i++) {
                        if (i != 0 && i % printStep == 0) {
                            printUpdate(START, printStep, ContractUint256.SERIALIZED_SIZE, "Created " + i + " Nodes in store "+dataSourceIndex);
                            START = System.currentTimeMillis();
                            // next transaction
                            dataSource.commitTransaction(transaction);
                            transaction = dataSource.startTransaction();
                        }
                        try {
                            dataSource.saveInternalSequential(transaction, i, FCVirtualMapTestUtils.hash(i));
                        } catch (Exception e) {
                            System.err.println("Error i= "+i);
                            e.printStackTrace();
                        }
                    }
                    dataSource.commitTransaction(transaction);
                });

                // start leaves
                IntStream.range(0,numDataSources).parallel().forEach(dataSourceIndex -> {
                    final var dataSource = dataSources[dataSourceIndex];
                    long START = System.currentTimeMillis();
                    Object transaction = dataSource.startTransaction();
                    for (int i = 0; i < numEntities; i++) {
                        if (i != 0 && i % printStep == 0) {
                            printUpdate(START, printStep, ContractUint256.SERIALIZED_SIZE, "Created " + i + " Nodes in store "+dataSourceIndex);
                            START = System.currentTimeMillis();
                            // next transaction
                            dataSource.commitTransaction(transaction);
                            transaction = dataSource.startTransaction();
                        }
                        try {
                            dataSource.addLeafSequential(transaction, numEntities + i, new ContractKey(new Id(0, 0, i), new ContractUint256(i)), new ContractUint256(i), FCVirtualMapTestUtils.hash(i));
                        } catch (Exception e) {
                            System.err.println("Error i= "+i);
                            e.printStackTrace();
                        }
                    }
                    dataSource.commitTransaction(transaction);
                });

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
        try {
            for (var dataSource : dataSources) {
                dataSource.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        FCVirtualMapTestUtils.printDirectorySize(storePath);
    }

    @Setup(Level.Invocation)
    public void randomIndex(){
        randomDataSource = random.nextInt(numDataSources);
        randomNodeIndex1 = (long)(random.nextDouble()*numEntities);
        randomNodeIndex2 = (long)(random.nextDouble()*numEntities);
        randomLeafIndex1 = numEntities + randomNodeIndex1;
        randomLeafIndex2 = numEntities + randomNodeIndex2;
        key1 = new ContractKey(new Id(0,0,randomNodeIndex1),new ContractUint256(randomNodeIndex1));
        key2 = new ContractKey(new Id(0,0,randomNodeIndex2),new ContractUint256(randomNodeIndex2));
    }


    @Benchmark
    public void w0_updateHash() throws Exception {
        dataSources[randomDataSource].saveInternal(randomLeafIndex1, FCVirtualMapTestUtils.hash((int) randomLeafIndex1));
    }

    @Benchmark
    public void w1_updateLeafValue() throws Exception {
        dataSources[randomDataSource].updateLeaf(randomLeafIndex1,new ContractKey(new Id(0,0,randomLeafIndex1),new ContractUint256(randomLeafIndex1)),
                new ContractUint256(randomNodeIndex2), FCVirtualMapTestUtils.hash((int) randomNodeIndex2));
    }

    @Benchmark
    public void w2_addLeaf() throws Exception {
        dataSources[randomDataSource].addLeaf(numEntities + nextLeafIndex, new ContractKey(new Id(0,0,nextLeafIndex),new ContractUint256(nextLeafIndex)), new ContractUint256(nextLeafIndex), FCVirtualMapTestUtils.hash((int) nextLeafIndex));
        nextLeafIndex++;
    }

    @Benchmark
    public void w3_MoveLeaf() throws Exception {
        dataSources[randomDataSource].updateLeaf(randomLeafIndex1,randomLeafIndex2,key1, FCVirtualMapTestUtils.hash((int) randomLeafIndex1));
    }

    /**
     * This is designed to mimic our transaction round
     */
    @Benchmark
    public void t_transaction() {
        IntStream.range(0,3).parallel().forEach(thread -> {
            try {
                switch (thread) {
                    case 0 -> {
                        Thread.currentThread().setName("transaction");
                        // this is the transaction thread that reads leaf values
                        for (int i = 0; i < 20_000; i++) {
                            randomDataSource = random.nextInt(numDataSources);
                            randomNodeIndex1 = (long) (random.nextDouble() * numEntities);
                            key2 = new ContractKey(new Id(0, 0, randomNodeIndex1), new ContractUint256(randomNodeIndex1));
                            dataSources[randomDataSource].loadLeafValue(key2);
                        }
                    }
                    case 1 -> {
                        // this is the hashing thread that reads hashes
                        final int chunk = 20_000/hashThreads;
                        IntStream.range(0,hashThreads).parallel().forEach(hashChunk -> {
                            Thread.currentThread().setName("hashing "+hashChunk);
                            for (int i = 0; i < chunk; i++) {
                                randomDataSource = random.nextInt(numDataSources);
                                randomNodeIndex1 = (long) (random.nextDouble() * numEntities);
                                try {
                                    dataSources[randomDataSource].loadInternalHash(randomNodeIndex1);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    }
                    case 2 -> {
                        Thread.currentThread().setName("archiver");
                        // this is the archive thread that writes nodes and leaves
                        final int chunk = 20_000/numDataSources;
                        IntStream.range(0,numDataSources).parallel().forEach(dataSourceIndex -> {
                            final var dataSource = dataSources[dataSourceIndex];
                            Thread.currentThread().setName("writing internals in store "+dataSourceIndex);
                            final Object transaction = dataSource.startTransaction();
                            for (int i = 0; i < chunk; i++) { // update 10k internal hashes
                                randomNodeIndex1 = (long) (random.nextDouble() * numEntities);
                                try {
                                    dataSource.saveInternal(transaction,randomLeafIndex1, FCVirtualMapTestUtils.hash((int) randomLeafIndex1));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                            dataSource.commitTransaction(transaction);
                        });
                        IntStream.range(0,numDataSources).parallel().forEach(dataSourceIndex -> {
                            final var dataSource = dataSources[dataSourceIndex];
                            Thread.currentThread().setName("writing leaves in store "+dataSourceIndex);
                            final Object transaction = dataSource.startTransaction();
                            for (int i = 0; i < chunk; i++) { // update 10k leaves
                                randomNodeIndex1 = (long) (random.nextDouble() * numEntities);
                                randomLeafIndex1 = numEntities + randomNodeIndex1;
                                key1 = new ContractKey(new Id(0, 0, randomNodeIndex1), new ContractUint256(randomNodeIndex1));
                                try {
                                    dataSource.updateLeaf(transaction,randomLeafIndex1,key1, new ContractUint256(randomNodeIndex1), FCVirtualMapTestUtils.hash((int) randomNodeIndex1));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                            dataSource.commitTransaction(transaction);
                        });
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    @Benchmark
    public void r_loadLeafPath() throws Exception {
        dataSources[randomDataSource].loadLeafPath(key1);
    }

    @Benchmark
    public void r_loadLeafKey() throws Exception {
        dataSources[randomDataSource].loadLeafKey(randomLeafIndex1);
    }

    @Benchmark
    public void r_loadLeafValueByPath() throws Exception {
        dataSources[randomDataSource].loadLeafValue(randomLeafIndex2);
    }

    @Benchmark
    public void r_loadLeafValueByKey() throws Exception {
        dataSources[randomDataSource].loadLeafValue(key2);
    }

    @Benchmark
    public void r_loadInternalHash() throws Exception {
        dataSources[randomDataSource].loadInternalHash(randomNodeIndex1);
    }

    @Benchmark
    public void r_loadLeafHash() throws Exception {
        dataSources[randomDataSource].loadLeafHash(randomLeafIndex1);
    }

}

package contract;

import com.hedera.services.state.merkle.v2.VFCDataSourceImpl;
import com.hedera.services.state.merkle.v3.VFCDataSourceImplV3;
import com.hedera.services.state.merkle.virtual.ContractKey;
import com.hedera.services.state.merkle.virtual.ContractUint256;
import com.hedera.services.store.models.Id;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import fcmmap.FCVirtualMapTestUtils;
import lmdb.SequentialInsertsVFCDataSource;
import lmdb.VFCDataSourceLmdb;
import lmdb.VFCDataSourceLmdbHashesRam;
import lmdb.VFCDataSourceLmdbTwoIndexes;
import org.eclipse.collections.impl.list.mutable.primitive.LongArrayList;
import org.openjdk.jmh.annotations.*;
import rockdb.VFCDataSourceRocksDb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static fcmmap.FCVirtualMapTestUtils.hash;

@SuppressWarnings({"jol", "DuplicatedCode", "DefaultAnnotationParam", "SameParameterValue", "SpellCheckingInspection"})
@State(Scope.Thread)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 120, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class VirtualDataSourceNewAPIBench {
    private static final long MB = 1024*1024;
    private static final int WRITE_BATCH_SIZE = 100_000;

    @Param({"10000"})
    public long numEntities;
    @Param({"memmap","lmdb","lmdb2","lmdb-ns","lmdb2-ns","rocksdb","lmdb-ram","v3"})
    public String impl;

    // state
    public Path storePath;
    public VirtualDataSource<ContractKey,ContractUint256> dataSource;
    public Random random = new Random(1234);
    public long nextPath = 0;
    private ContractKey key1 = null;
    private long randomLeafIndex1;
    private long randomNodeIndex1;
    private final LongArrayList random10kLeafPaths = new LongArrayList(10_000);

    @Setup(Level.Trial)
    public void setup() {
        System.out.println("------- Setup -----------------------------");
        storePath = Path.of("store-"+impl);
        try {
            final boolean storeExists = Files.exists(storePath);
            // get slot index suppliers
            dataSource = switch (impl) {
                case "memmap" ->
                    VFCDataSourceImpl.createOnDisk(storePath,numEntities*2,
                            ContractKey.SERIALIZED_SIZE, ContractKey::new,
                            ContractUint256.SERIALIZED_SIZE, ContractUint256::new, true);
                case "lmdb","lmdb-ns" ->
                    new VFCDataSourceLmdb<>(
                            ContractKey.SERIALIZED_SIZE, ContractKey::new,
                            ContractUint256.SERIALIZED_SIZE, ContractUint256::new,
                            storePath);
                case "lmdb2","lmdb2-ns" ->
                    new VFCDataSourceLmdbTwoIndexes<>(
                            ContractKey.SERIALIZED_SIZE, ContractKey::new,
                            ContractUint256.SERIALIZED_SIZE, ContractUint256::new,
                            storePath);
                case "lmdb-ram" ->
                    new VFCDataSourceLmdbHashesRam<>(
                            ContractKey.SERIALIZED_SIZE, ContractKey::new,
                            ContractUint256.SERIALIZED_SIZE, ContractUint256::new,
                            storePath);
                case "rocksdb" ->
                    new VFCDataSourceRocksDb<>(
                            ContractKey.SERIALIZED_SIZE, ContractKey::new,
                            ContractUint256.SERIALIZED_SIZE, ContractUint256::new,
                            storePath);
                case "v3" ->
                    new VFCDataSourceImplV3<>(
                            ContractKey.SERIALIZED_SIZE, ContractKey::new,
                            ContractUint256.SERIALIZED_SIZE, ContractUint256::new,
                            storePath,
                            numEntities+10_000_000); // TODO see if 10 millionls extra is enough for add method
                default ->
                    throw new IllegalStateException("Unexpected value: " + impl);
            };
            // create data
            if (!storeExists) {
                final SequentialInsertsVFCDataSource<ContractKey,ContractUint256> sequentialDataSource =
                    (dataSource instanceof SequentialInsertsVFCDataSource && (impl.equals("lmdb") || impl.equals("lmdb2")))
                            ? (SequentialInsertsVFCDataSource<ContractKey, ContractUint256>)dataSource : null;
                System.out.println("================================================================================");
                System.out.println("Creating data ...");
                // create internal nodes and leaves
                long iHaveWritten = 0;
                while (iHaveWritten < numEntities) {
                    final long start = System.currentTimeMillis();
                    final long batchSize = Math.min(WRITE_BATCH_SIZE, numEntities-iHaveWritten);
                    dataSource.saveRecords(
                            numEntities,numEntities*2,
                            LongStream.range(iHaveWritten,iHaveWritten+batchSize).mapToObj(i -> new VirtualInternalRecord(i,hash((int)i))).collect(Collectors.toList()),
                            LongStream.range(iHaveWritten,iHaveWritten+batchSize).mapToObj(i -> new VirtualLeafRecord<>(
                                    i+numEntities,hash((int)i),new ContractKey(new Id(0, 0, i), new ContractUint256(i)), new ContractUint256(i) )
                            ).collect(Collectors.toList())
                    );
                    iHaveWritten += batchSize;
                    printUpdate(start, batchSize, ContractUint256.SERIALIZED_SIZE, "Created " + iHaveWritten + " Nodes");
                }
                System.out.println("================================================================================");
                // set nextPath
                nextPath = numEntities;
                // let merge catch up
                try {
                    System.out.println("Waiting for merge");
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
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
            // v3 data sorce needs a transaction
            dataSource.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        FCVirtualMapTestUtils.printDirectorySize(storePath);
    }

    @Setup(Level.Invocation)
    public void randomIndex(){
        randomNodeIndex1 = (long)(random.nextDouble()*numEntities);
        randomLeafIndex1 = numEntities + randomNodeIndex1;
        key1 = new ContractKey(new Id(0,0,randomNodeIndex1),new ContractUint256(randomNodeIndex1));
        random10kLeafPaths.clear();
        for (int i = 0; i < 10_000; i++) {
            random10kLeafPaths.add(numEntities + ((long)(random.nextDouble()*numEntities)));
        }
    }

    /**
     * Updates the first 10k internal node hashes to new values
     */
    @Benchmark
    public void w0_updateFirst10kInternalHashes() throws Exception {
        dataSource.saveRecords(
                numEntities,numEntities*2,
                LongStream.range(0,Math.min(10_000,numEntities)).mapToObj(i -> new VirtualInternalRecord(i,hash((int)i))).collect(Collectors.toList()),
                null
        );
    }

    /**
     * Updates the first 10k leaves with new random values
     */
    @Benchmark
    public void w1_updateFirst10kLeafValues() throws Exception {
        dataSource.saveRecords(
                numEntities,numEntities*2,
                null,
                LongStream.range(0,Math.min(10_000,numEntities)).mapToObj(i -> new VirtualLeafRecord<>(
                        i+numEntities,hash((int)i),new ContractKey(new Id(0, 0, i), new ContractUint256(i)), new ContractUint256(randomNodeIndex1) )
                ).collect(Collectors.toList())
        );
        // add a small delay between iterations for merging to get a chance on write heavy benchmarks
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
    }

    /**
     * Updates the randomly selected 10k leaves with new random values
     */
    @Benchmark
    public void w1_updateRandom10kLeafValues() throws Exception {
        List<VirtualLeafRecord<ContractKey,ContractUint256>> changes = new ArrayList<>(10_000);
        for (int i = 0; i < 10_000; i++) {
            long path = random10kLeafPaths.get(i);
            changes.add(new VirtualLeafRecord<>(
                    path,hash((int)path),new ContractKey(new Id(0, 0, path), new ContractUint256(path)), new ContractUint256(i) ));
        }
        dataSource.saveRecords(
                numEntities,numEntities*2,
                null,
                changes
        );
    }

    /**
     * Updates the first 10k internal node hashes to new values
     */
    @Benchmark
    public void w2_add10kInternalHashes() throws Exception {
        dataSource.saveRecords(
                numEntities,numEntities*2,
                LongStream.range(nextPath,nextPath+10_000).mapToObj(i -> new VirtualInternalRecord(i,hash((int)i))).collect(Collectors.toList()),
                null
        );
        nextPath += 10_000;
    }

    /**
     * Updates the first 10k leaves with new random values
     */
    @Benchmark
    public void w3_add10kLeafValues() throws Exception {
        dataSource.saveRecords(
                numEntities,numEntities*2,
                null,
                LongStream.range(nextPath,nextPath+10_000).mapToObj(i -> new VirtualLeafRecord<>(
                        i+numEntities,hash((int)i),new ContractKey(new Id(0, 0, i), new ContractUint256(i)), new ContractUint256(i) )
                ).collect(Collectors.toList())
        );
        nextPath += 10_000;
    }

    @Benchmark
    public void r_loadloadLeafRecordByPath() throws Exception {
        dataSource.loadLeafRecord(randomLeafIndex1);
    }

    @Benchmark
    public void r_loadLeafRecordByKey() throws Exception {
        dataSource.loadLeafRecord(key1);
    }

    @Benchmark
    public void r_loadInternalHash() throws Exception {
        dataSource.loadInternalHash(randomNodeIndex1);
    }

    @Benchmark
    public void r_loadLeafHash() throws Exception {
        dataSource.loadLeafHash(randomLeafIndex1);
    }

//
//    /**
//     * This is designed to mimic our transaction round
//     */
//    @Benchmark
//    public void t_transaction() {
//        IntStream.range(0,3).parallel().forEach(thread -> {
//            try {
//                switch (thread) {
//                    case 0 -> {
//                        Thread.currentThread().setName("transaction");
//                        // this is the transaction thread that reads leaf values
//                        for (int i = 0; i < 20_000; i++) {
//                            randomNodeIndex1 = (long) (random.nextDouble() * numEntities);
//                            key2 = new ContractKey(new Id(0, 0, randomNodeIndex1), new ContractUint256(randomNodeIndex1));
//                            dataSource.loadLeafValue(key2);
//                        }
//                    }
//                    case 1 -> {
//                        // this is the hashing thread that reads hashes
//                        final int chunk = 20_000/hashThreads;
//                        IntStream.range(0,hashThreads).parallel().forEach(hashChunk -> {
//                            Thread.currentThread().setName("hashing "+hashChunk);
//                            for (int i = 0; i < chunk; i++) {
//                                randomNodeIndex1 = (long) (random.nextDouble() * numEntities);
//                                try {
//                                    dataSource.loadInternalHash(randomNodeIndex1);
//                                } catch (IOException e) {
//                                    e.printStackTrace();
//                                }
//                            }
//                        });
//                    }
//                    case 2 -> {
//                        Thread.currentThread().setName("archiver");
//                        // this is the archive thread that writes nodes and leaves
//                        final int chunk = 20_000/writeThreads;
//                        IntStream.range(0,writeThreads/2).parallel().forEach(c -> {
//                            Thread.currentThread().setName("writing internals "+c);
//                            final Object transaction = dataSource.startTransaction();
//                            for (int i = 0; i < chunk; i++) { // update 10k internal hashes
//                                randomNodeIndex1 = (long) (random.nextDouble() * numEntities);
//                                try {
//                                    dataSource.saveInternal(transaction,randomLeafIndex1, FCVirtualMapTestUtils.hash((int) randomLeafIndex1));
//                                } catch (IOException e) {
//                                    e.printStackTrace();
//                                }
//                            }
//                            dataSource.commitTransaction(transaction);
//                        });
//                        IntStream.range(0,writeThreads/2).parallel().forEach(c -> {
//                            Thread.currentThread().setName("writing leaves "+c);
//                            final Object transaction = dataSource.startTransaction();
//                            for (int i = 0; i < chunk; i++) { // update 10k leaves
//                                randomNodeIndex1 = (long) (random.nextDouble() * numEntities);
//                                randomLeafIndex1 = numEntities + randomNodeIndex1;
//                                key1 = new ContractKey(new Id(0, 0, randomNodeIndex1), new ContractUint256(randomNodeIndex1));
//                                try {
//                                    dataSource.updateLeaf(transaction,randomLeafIndex1,key1, new ContractUint256(randomNodeIndex1), FCVirtualMapTestUtils.hash((int) randomNodeIndex1));
//                                } catch (IOException e) {
//                                    e.printStackTrace();
//                                }
//                            }
//                            dataSource.commitTransaction(transaction);
//                        });
//                    }
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        });
//    }

}

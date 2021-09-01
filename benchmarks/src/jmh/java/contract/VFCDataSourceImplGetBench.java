package contract;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@SuppressWarnings({"jol", "DuplicatedCode", "DefaultAnnotationParam", "SameParameterValue", "SpellCheckingInspection"})
@State(Scope.Thread)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class VFCDataSourceImplGetBench {
//    private static final long MB = 1024*1024;
//
//    @Param({"10000"})
//    public long numEntities;
//    @Param({"5"})
//    public int hashThreads;
//    @Param({"4"})
//    public int writeThreads;
//    @Param({"memmap","lmdb","lmdb2","lmdb-ns","lmdb2-ns","rocksdb","lmdb-ram","v3"})
//    public String impl;
//
//    // state
//    public Path storePath;
//    public VirtualDataSource<ContractKey,ContractUint256> dataSource;
//    public Random random = new Random(1234);
//    public int iteration = 0;
//    private ContractKey key1 = null;
//    private ContractKey key2 = null;
//    private long randomLeafIndex1;
//    private long randomLeafIndex2;
//    private long randomNodeIndex1;
//    private long randomNodeIndex2;
//    private long nextLeafIndex;
//
//    @Setup(Level.Trial)
//    public void setup() {
//        storePath = Path.of("store-"+impl);
//        System.out.println("Clean Setup");
//        try {
//            // delete any old store
//            if (impl.equals("v3")) FCVirtualMapTestUtils.deleteDirectoryAndContents(storePath);
//            final boolean storeExists = Files.exists(storePath);
//            // get slot index suppliers
//            dataSource = switch (impl) {
//                case "lmdb","lmdb-ns" ->
//                    new VFCDataSourceLmdb<>(
//                            ContractKey.SERIALIZED_SIZE, ContractKey::new,
//                            ContractUint256.SERIALIZED_SIZE, ContractUint256::new,
//                            storePath);
//                case "lmdb2","lmdb2-ns" ->
//                    new VFCDataSourceLmdbTwoIndexes<>(
//                            ContractKey.SERIALIZED_SIZE, ContractKey::new,
//                            ContractUint256.SERIALIZED_SIZE, ContractUint256::new,
//                            storePath);
//                case "lmdb-ram" ->
//                    new VFCDataSourceLmdbHashesRam<>(
//                            ContractKey.SERIALIZED_SIZE, ContractKey::new,
//                            ContractUint256.SERIALIZED_SIZE, ContractUint256::new,
//                            storePath);
//                case "rocksdb" ->
//                    new VFCDataSourceRocksDb<>(
//                            ContractKey.SERIALIZED_SIZE, ContractKey::new,
//                            ContractUint256.SERIALIZED_SIZE, ContractUint256::new,
//                            storePath);
//                case "jasperdb" ->
//                    new VirtualDataSourceJasperDB<>(
//                            ContractKey.SERIALIZED_SIZE, ContractKey::new,
//                            ContractUint256.SERIALIZED_SIZE, ContractUint256::new,
//                            storePath,
//                            numEntities,
//                            Long.MAX_VALUE);
//                default ->
//                    throw new IllegalStateException("Unexpected value: " + impl);
//            };
//            // create data
//            if (!storeExists) {
//                final SequentialInsertsVFCDataSource<ContractKey,ContractUint256> sequentialDataSource =
//                    (dataSource instanceof SequentialInsertsVFCDataSource && (impl.equals("lmdb") || impl.equals("lmdb2")))
//                            ? (SequentialInsertsVFCDataSource<ContractKey, ContractUint256>)dataSource : null;
//                System.out.println("================================================================================");
//                System.out.println("Creating data ...");
//                long printStep = Math.min(500_000, numEntities / 4);
//                final AtomicLong I = new AtomicLong(0);
//                final AtomicLong START = new AtomicLong( System.currentTimeMillis());
//
//                IntStream.range(0,writeThreads).parallel().forEach(c -> {
//                    long i;
//                    long iHaveWritten = 0;
//                    Object transaction = dataSource.startTransaction();
//                    while ((i = I.getAndIncrement()) < numEntities) {
//                        if (i != 0 && i % printStep == 0) {
//                            long start = START.getAndSet(System.currentTimeMillis());
//                            printUpdate(start, printStep, ContractUint256.SERIALIZED_SIZE, "Created " + i + " Nodes");
//                        }
//                        if (iHaveWritten != 0 && iHaveWritten % printStep == 0) { // next transaction
//                            dataSource.commitTransaction(transaction);
//                            transaction = dataSource.startTransaction();
//                        }
//                        try {
//                            if (sequentialDataSource != null) {
//                                sequentialDataSource.saveInternalSequential(transaction, i, FCVirtualMapTestUtils.hash((int) i));
//                            } else {
//                                dataSource.saveInternal(transaction, i, FCVirtualMapTestUtils.hash((int) i));
//                            }
//                        } catch (Exception e) {
//                            System.err.println("Error i= "+i);
//                            e.printStackTrace();
//                        }
//                        iHaveWritten ++;
//                    }
//                    dataSource.commitTransaction(transaction);
//                });
//
//                // start leaves
//                I.set(0);
//                START.set(System.currentTimeMillis());
//                IntStream.range(0,writeThreads).parallel().forEach(c -> {
//                    long i;
//                    long iHaveWritten = 0;
//                    Object transaction = dataSource.startTransaction();
//                    while ((i = I.getAndIncrement()) < numEntities) {
//                        if (i != 0 && i % printStep == 0) {
//                            long start = START.getAndSet(System.currentTimeMillis());
//                            printUpdate(start, printStep, ContractUint256.SERIALIZED_SIZE, "Created " + i + " Nodes");
//                        }
//                        if (iHaveWritten != 0 && iHaveWritten % printStep == 0) {
//                            dataSource.commitTransaction(transaction);
//                            transaction = dataSource.startTransaction();
//                        }
//                        try {
//                            if (sequentialDataSource != null) {
//                                ((SequentialInsertsVFCDataSource<ContractKey, ContractUint256>) dataSource).addLeafSequential(transaction, numEntities + i, new ContractKey(new Id(0, 0, i), new ContractUint256(i)), new ContractUint256(i), FCVirtualMapTestUtils.hash((int) i));
//                            } else {
//                                dataSource.addLeaf(transaction, numEntities + i, new ContractKey(new Id(0, 0, i), new ContractUint256(i)), new ContractUint256(i), FCVirtualMapTestUtils.hash((int) i));
//                            }
//                        } catch (Exception e) {
//                            System.err.println("Error i= "+i);
//                            e.printStackTrace();
//                        }
//                        iHaveWritten ++;
//                    }
//                    dataSource.commitTransaction(transaction);
//                });
//
//                System.out.println("================================================================================");
//                nextLeafIndex = numEntities;
//                // reset iteration counter
//                iteration = 0;
//            } else {
//                System.out.println("Loaded existing data");
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        // v3 data sorce needs a transaction
//        if (impl.equals("v3")) dataSource.startTransaction();
//    }
//
//    private static void printUpdate(long START, long count, int size, String msg) {
//        long took = System.currentTimeMillis() - START;
//        double timeSeconds = (double)took/1000d;
//        double perSecond = (double)count / timeSeconds;
//        double mbPerSec = (perSecond*size)/MB;
//        System.out.printf("%s : [%,d] writes at %,.0f per/sec %,.1f Mb/sec, took %,.2f seconds\n",msg, count, perSecond,mbPerSec, timeSeconds);
//    }
//
//    @TearDown(Level.Trial)
//    public void tearDown() {
//        try {
//            // v3 data sorce needs a transaction
//            if (impl.equals("v3")) dataSource.commitTransaction(null);
//            dataSource.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        FCVirtualMapTestUtils.printDirectorySize(storePath);
//    }
//
//    @Setup(Level.Invocation)
//    public void randomIndex(){
//        randomNodeIndex1 = (long)(random.nextDouble()*numEntities);
//        randomNodeIndex2 = (long)(random.nextDouble()*numEntities);
//        randomLeafIndex1 = numEntities + randomNodeIndex1;
//        randomLeafIndex2 = numEntities + randomNodeIndex2;
//        key1 = new ContractKey(new Id(0,0,randomNodeIndex1),new ContractUint256(randomNodeIndex1));
//        key2 = new ContractKey(new Id(0,0,randomNodeIndex2),new ContractUint256(randomNodeIndex2));
//    }
//
//
//    @Benchmark
//    public void w0_updateHash() throws Exception {
//        dataSource.saveInternal(randomLeafIndex1, FCVirtualMapTestUtils.hash((int) randomLeafIndex1));
//    }
//
//    @Benchmark
//    public void w1_updateLeafValue() throws Exception {
//        dataSource.updateLeaf(randomLeafIndex1,new ContractKey(new Id(0,0,randomLeafIndex1),new ContractUint256(randomLeafIndex1)),
//                new ContractUint256(randomNodeIndex2), FCVirtualMapTestUtils.hash((int) randomNodeIndex2));
//    }
//
//    @Benchmark
//    public void w2_addLeaf() throws Exception {
//        dataSource.addLeaf(numEntities + nextLeafIndex, new ContractKey(new Id(0,0,nextLeafIndex),new ContractUint256(nextLeafIndex)), new ContractUint256(nextLeafIndex), FCVirtualMapTestUtils.hash((int) nextLeafIndex));
//        nextLeafIndex++;
//    }
//
//    @Benchmark
//    public void w3_MoveLeaf() throws Exception {
//        dataSource.updateLeaf(randomLeafIndex1,randomLeafIndex2,key1, FCVirtualMapTestUtils.hash((int) randomLeafIndex1));
//    }
//
//    @Benchmark
//    public void r_loadLeafPath() throws Exception {
//        dataSource.loadLeafPath(key1);
//    }
//
//    @Benchmark
//    public void r_loadLeafKey() throws Exception {
//        dataSource.loadLeafKey(randomLeafIndex1);
//    }
//
//    @Benchmark
//    public void r_loadLeafValueByPath() throws Exception {
//        dataSource.loadLeafValue(randomLeafIndex2);
//    }
//
//    @Benchmark
//    public void r_loadLeafValueByKey() throws Exception {
//        dataSource.loadLeafValue(key2);
//    }
//
//    @Benchmark
//    public void r_loadInternalHash() throws Exception {
//        dataSource.loadInternalHash(randomNodeIndex1);
//    }
//
//    @Benchmark
//    public void r_loadLeafHash() throws Exception {
//        dataSource.loadLeafHash(randomLeafIndex1);
//    }
//
//    /**
//     * This is designed to mimic our transaction round
//     */
//    @Benchmark
//    public void t_transaction(Blackhole blackHole) {
//        IntStream.range(0,7).parallel().forEach(thread -> {
//            try {
//                switch (thread) {
//                    case 0: // this is the transaction 5 threads that reads leaf values
//                    case 1:
//                    case 2:
//                    case 3:
//                    case 4:
//                    {
//                        Thread.currentThread().setName("transaction");
//                        for (int i = 0; i < 4_000; i++) {
//                            randomNodeIndex1 = (long) (random.nextDouble() * numEntities);
//                            key2 = new ContractKey(new Id(0, 0, randomNodeIndex1), new ContractUint256(randomNodeIndex1));
//                            blackHole.consume(dataSource.loadLeafValue(key2));
//                        }
//                        break;
//                    }
//                    case 5:
//                    {
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
//                        break;
//                    }
//                    case 6 :
//                    {
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
//                        break;
//                    }
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        });
//    }

}

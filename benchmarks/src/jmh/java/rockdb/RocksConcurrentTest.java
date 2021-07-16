package rockdb;

import com.hedera.services.state.merkle.virtual.ContractKey;
import com.hedera.services.state.merkle.virtual.ContractUint256;
import com.hedera.services.store.models.Id;
import com.swirlds.common.crypto.DigestType;
import fcmmap.FCVirtualMapTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.IntStream;

@SuppressWarnings("DuplicatedCode")
public class RocksConcurrentTest {
    private final static int HASH_SIZE = Long.BYTES+ DigestType.SHA_384.digestLength();
    private final static int INTERNAL_SIZE_APPROX = Long.BYTES+HASH_SIZE;
    private final static int LEAF_SIZE_APPROX = Long.BYTES+HASH_SIZE+Integer.BYTES+ContractKey.SERIALIZED_SIZE+
            Long.BYTES+Long.BYTES+Integer.BYTES+ContractKey.SERIALIZED_SIZE+Integer.BYTES+ContractKey.SERIALIZED_SIZE+
            Integer.BYTES+ContractUint256.SERIALIZED_SIZE;
    private static final long MB = 1024*1024;
    private static final Path STORE_PATH = Path.of("store");
    private static final Random random = new Random(1234);

    private static final long numEntities = 100_000_000;
    private static final long testIterations = 64_000_000;
    private static final int numOfThreads = 32;

    final static AtomicLong counter = new AtomicLong(0);
    final static AtomicLong START = new AtomicLong(0);
    final static AtomicLong nextLeafIndexCounter = new AtomicLong(numEntities);

    public static void main(String[] args) throws Exception {
        final boolean storeExists = Files.exists(STORE_PATH);
        final VFCDataSourceRocksDbConcurrent<ContractKey,ContractUint256> dataSource = new VFCDataSourceRocksDbConcurrent<>(
                ContractKey.SERIALIZED_SIZE, ContractKey::new,
                ContractUint256.SERIALIZED_SIZE, ContractUint256::new,
                STORE_PATH);
        // create data
        if (!storeExists) {
            // start bulk update
            dataSource.startBulk();

            final long chunkNumEntities = numEntities/numOfThreads;
            final long printStep = Math.min(1_000_000, chunkNumEntities / 4);
            counter.set(-1);
            START.set(System.currentTimeMillis());
            IntStream.range(0,numOfThreads).parallel().forEach(chunkIndex -> {
                final long start = chunkIndex*chunkNumEntities;
                final long end = start+chunkNumEntities;
                for (long i = start; i < end; i++) {
                    final long count = counter.incrementAndGet();
                    if (count != 0 && count % printStep == 0) {
                        printUpdate(START, printStep, INTERNAL_SIZE_APPROX,count, "Created nodes");
                    }
                    try {
                        dataSource.saveInternal(i, FCVirtualMapTestUtils.hash((int) i));

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });

            counter.set(-1);
            START.set(System.currentTimeMillis());
            IntStream.range(0,numOfThreads).parallel().forEach(chunkIndex -> {
                final long start = chunkIndex*chunkNumEntities;
                final long end = start+chunkNumEntities;
                for (long i = start; i < end; i++) {
                    final long count = counter.incrementAndGet();
                    if (count != 0 && count % printStep == 0) {
                        printUpdate(START, printStep, LEAF_SIZE_APPROX,count, "Created leaves");
                    }
                    try {
                        dataSource.addLeaf(numEntities + i, new ContractKey(new Id(0,0,i),new ContractUint256(i)), new ContractUint256(i), FCVirtualMapTestUtils.hash((int) i));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            });
            // end bulk update
            dataSource.endBulk();

        } else {
            System.out.println("Loaded existing data");
        }
        List<Test> tests = List.of(
                new Test("loadHash",RocksConcurrentTest::r_loadHash),
                new Test("loadLeafValueByKey",RocksConcurrentTest::r_loadLeafValueByKey),
                new Test("loadLeafValueByPath",RocksConcurrentTest::r_loadLeafValueByPath),
                new Test("loadLeafKey",RocksConcurrentTest::r_loadLeafKey),
                new Test("loadLeafPath",RocksConcurrentTest::r_loadLeafPath),
                new Test("updateLeafValue",RocksConcurrentTest::w1_updateLeafValue),
                new Test("addLeaf",RocksConcurrentTest::w2_addLeaf),
                new Test("moveLeaf",RocksConcurrentTest::w3_MoveLeaf)
        );
        long chunkSize = testIterations/numOfThreads;
        final long testPrintStep = Math.min(1_000_000, chunkSize / 4);
        List<String> allResults = new CopyOnWriteArrayList<>();
        tests.forEach(test -> {
            System.out.println("================================================================================");
            System.out.println("Starting Test:  "+test.name+"\n");
            counter.set(-1);
            START.set(System.currentTimeMillis());
            List<Double> perSecs = new CopyOnWriteArrayList<>();
            IntStream.range(0,numOfThreads).parallel().forEach(chunkIndex -> {
                for (int i = 0; i < chunkSize; i++) {
                    final long count = counter.incrementAndGet();
                    if (count != 0 && count % testPrintStep == 0) {
                        perSecs.add(printTestUpdate(START, testPrintStep,count, test.name));
                    }
                    test.testFunction.accept(dataSource);
                }
            });
            final String resultsStr = test.name+" results: "+perSecs.stream().mapToDouble(d -> d).summaryStatistics();
            System.out.println(resultsStr);
            allResults.add(resultsStr);
        });
        System.out.println("================================================================================");
        allResults.forEach(System.out::println);
    }

    private static void printUpdate(AtomicLong start, long count, int size, long totalCount, String msg) {
        long took = System.currentTimeMillis() - start.getAndSet(System.currentTimeMillis());
        double timeSeconds = (double)took/1000d;
        double perSecond = (double)count / timeSeconds;
        double mbPerSec = (perSecond*size)/MB;
        System.out.printf("%s %,d : [%,d] at %,.0f per/sec %,.1f Mb/sec, took %,.2f seconds\n",msg,totalCount, count, perSecond,mbPerSec, timeSeconds);
    }

    private static double printTestUpdate(AtomicLong start, long count, long totalCount, String msg) {
        long took = System.currentTimeMillis() - start.getAndSet(System.currentTimeMillis());
        double timeSeconds = (double)took/1000d;
        double perSecond = (double)count / timeSeconds;
        System.out.printf("%s x %,d : [%,d] at %,.0f per/sec, took %,.2f seconds\n",msg,totalCount, count, perSecond, timeSeconds);
        return perSecond;
    }

    public static class Test {
        public final String name;
        public final Consumer<VFCDataSourceRocksDbConcurrent<ContractKey,ContractUint256>> testFunction;

        public Test(String name, Consumer<VFCDataSourceRocksDbConcurrent<ContractKey, ContractUint256>> testFunction) {
            this.name = name;
            this.testFunction = testFunction;
        }
    }


    public static void w0_updateHash(VFCDataSourceRocksDbConcurrent<ContractKey,ContractUint256> dataSource) {
        long randomLeafPath1 = numEntities + (long)(random.nextDouble()*numEntities);
        try {
            dataSource.saveInternal(randomLeafPath1, FCVirtualMapTestUtils.hash((int) randomLeafPath1));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void w1_updateLeafValue(VFCDataSourceRocksDbConcurrent<ContractKey,ContractUint256> dataSource) {
        long randomNodeIndex1 = (long)(random.nextDouble()*numEntities);
        long randomLeafPath1 = numEntities + randomNodeIndex1;
        try {
            dataSource.updateLeaf(randomLeafPath1,new ContractUint256(randomNodeIndex1), FCVirtualMapTestUtils.hash((int) randomNodeIndex1));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void w2_addLeaf(VFCDataSourceRocksDbConcurrent<ContractKey,ContractUint256> dataSource) {
        long nextLeafIndex = nextLeafIndexCounter.getAndIncrement();
        try {
            dataSource.addLeaf(numEntities + nextLeafIndex, new ContractKey(new Id(0,0,nextLeafIndex),new ContractUint256(nextLeafIndex)), new ContractUint256(nextLeafIndex), FCVirtualMapTestUtils.hash((int) nextLeafIndex));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void w3_MoveLeaf(VFCDataSourceRocksDbConcurrent<ContractKey,ContractUint256> dataSource) {
        long randomNodeIndex1 = (long)(random.nextDouble()*numEntities);
        long randomLeafPath1 = numEntities + randomNodeIndex1;
        long randomLeafPath2 = numEntities + (long)(random.nextDouble()*numEntities);
        ContractKey key1 = new ContractKey(new Id(0,0,randomNodeIndex1),new ContractUint256(randomNodeIndex1));
        try {
            dataSource.updateLeaf(randomLeafPath1,randomLeafPath2,key1);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void r_loadLeafPath(VFCDataSourceRocksDbConcurrent<ContractKey,ContractUint256> dataSource) {
        long randomNodeIndex1 = (long)(random.nextDouble()*numEntities);
        ContractKey key1 = new ContractKey(new Id(0,0,randomNodeIndex1),new ContractUint256(randomNodeIndex1));
        try {
            dataSource.loadLeafPath(key1);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void r_loadLeafKey(VFCDataSourceRocksDbConcurrent<ContractKey,ContractUint256> dataSource) {
        long randomLeafPath1 = numEntities + (long)(random.nextDouble()*numEntities);
        try {
            dataSource.loadLeafKey(randomLeafPath1);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void r_loadLeafValueByPath(VFCDataSourceRocksDbConcurrent<ContractKey,ContractUint256> dataSource) {
        long randomLeafPath1 = numEntities + (long)(random.nextDouble()*numEntities);
        try {
            dataSource.loadLeafValue(randomLeafPath1);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void r_loadLeafValueByKey(VFCDataSourceRocksDbConcurrent<ContractKey,ContractUint256> dataSource) {
        long randomNodeIndex1 = (long)(random.nextDouble()*numEntities);
        ContractKey key1 = new ContractKey(new Id(0,0,randomNodeIndex1),new ContractUint256(randomNodeIndex1));
        try {
            dataSource.loadLeafValue(key1);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void r_loadHash(VFCDataSourceRocksDbConcurrent<ContractKey,ContractUint256> dataSource) {
        long randomNodeIndex1 = (long)(random.nextDouble()*numEntities);
        try {
            dataSource.loadHash(randomNodeIndex1);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

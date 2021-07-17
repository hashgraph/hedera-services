package contract;

import com.hedera.services.state.merkle.virtual.ContractUint256;
import com.swirlds.common.crypto.Hash;
import com.swirlds.fcmap.VFCMap;
import org.openjdk.jmh.annotations.*;
import rockdb.VFCDataSourceRocksDb;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.*;

/**
 * Microbenchmark tests for the VirtualMap. These benchmarks are just of the tree itself,
 * and not of any underlying storage mechanism. In fact, it just uses an in-memory set of
 * hash maps as the backing VirtualDataSource, and benchmarks those too just so we have
 * baseline numbers.
 */
@SuppressWarnings("jol")
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class VFCMapBench {
    private static final int FILE_SIZE = 32*1024*1024;

//    @Param({"5", "10", "15", "20", "25"})
    @Param({"5"})
    public int numUpdatesPerOperation;

    @Param({"10000"})
    public int targetOpsPerSecond;

    @Param({"1000"})
    public long maxMillisPerHashRound;

    @Param({"10000", "100000", "1000000", "10000000", "100000000", "1000000000"})
    public long numEntities;

    @Param("true")
    public boolean preFill;
//
//    @Param({"true", "false"})
//    public boolean inMemoryIndex;
//
//    @Param({"true", "false"})
//    public boolean inMemoryStore;

    private final Random rand = new Random(1234);
    private VFCMap<ContractUint256, ContractUint256> contractMap;

    private final Exchanger<Blar> hashingExchanger = new Exchanger<>();
    private final Exchanger<VFCMap<?, ?>> archiveExchanger = new Exchanger<>();
    private final Exchanger<VFCMap<?, ?>> releaseExchanger = new Exchanger<>();
    private final ExecutorService releaseService = Executors.newSingleThreadExecutor(threadFactory("ReleaseService"));
    private final ExecutorService archiveService = Executors.newSingleThreadExecutor(threadFactory("ArchiveService"));
    private final ExecutorService hashingService = Executors.newSingleThreadExecutor(threadFactory("HashingService"));

    private Future<Hash> hashingFuture = null;

    @Setup
    public void prepare() throws Exception {
        releaseService.submit(() -> {
            while (true) {
                final var map = releaseExchanger.exchange(null);
//                map.release();
            }
        });

        archiveService.submit(() -> {
            while (true) {
                final var map = archiveExchanger.exchange(null);
                try {
                    map.archive();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                releaseExchanger.exchange(map);
            }
        });

        hashingService.submit(() -> {
            while (true) {
                final var cf = new CompletableFuture<Hash>();
                final var map = hashingExchanger.exchange(new Blar(cf)).map;
                try {
                    final var hashingFuture = map.hash();
                    cf.complete(hashingFuture.get());
                    archiveExchanger.exchange(map);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        });
//        final var numBinsAsPowerOf2 = Long.highestOneBit(numEntities);
//        final var keysPerBin = 4;
//        final var keySize = ContractUint256.SERIALIZED_SIZE;
//        final var sizeOfBin = (Integer.BYTES+(keysPerBin*(Integer.BYTES+Long.BYTES+keySize)));
//        final var numFilesForIndex = (numBinsAsPowerOf2 * sizeOfBin) / (1024*1024*1024);
//        final var numFilesAsPowerOf2 = Math.max(2, Long.highestOneBit(numFilesForIndex * 2));
        final var ds = new VFCDataSourceRocksDb<>(
                ContractUint256.SERIALIZED_SIZE,
                ContractUint256::new,
                ContractUint256.SERIALIZED_SIZE,
                ContractUint256::new,
                Path.of("data"));
        contractMap = new VFCMap<>(ds);

//        System.out.println("\nUsing inMemoryIndex = " + inMemoryIndex+"  -- inMemoryStore = " + inMemoryStore);

        if (preFill) {
            for (int i = 0; i < numEntities; i++) {
                if (i % 100000 == 0 && i > 0) {
                    System.out.println("Completed: " + i);
                    hash();
                }

                final var key = asContractKey(i);
                final var value = asContractValue(i);
                try {
                    contractMap.put(key, value);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println(i);
                    throw e;
                }
            }

            System.out.println("Completed: " + numEntities);

            // During setup we perform the full hashing and release the old copy. This way,
            // during the tests, we don't have an initial slow hash.
            hash();
        }

        printDataStoreSize();
    }

    @TearDown
    public void destroy() {
        printDataStoreSize();
        /*store.close();*/
    }

    /**
     * Benchmarks update operations of an existing tree.
     *
     * @throws Exception Any exception should be treated as fatal.
     */
    @Benchmark
    public void update() {
        // Start modifying the new fast copy
        final var iterationsPerRound = numUpdatesPerOperation * targetOpsPerSecond;
        for (int j=0; j<iterationsPerRound; j++) {
            final var i = rand.nextInt((int)numEntities);
            contractMap.put(asContractKey(i), asContractValue(i + numEntities));
        }

        hash();
    }

    private ContractUint256 asContractKey(long index) {
        return new ContractUint256(BigInteger.valueOf(index));
    }

    private ContractUint256 asContractValue(long index) {
        return new ContractUint256(BigInteger.valueOf(index));
    }

    private void printDataStoreSize() {
        // print data dir size
        Path dir =  Path.of("data");
        if (Files.exists(dir) && Files.isDirectory(dir)) {
            try {
                long size = Files.walk(dir)
                        .filter(p -> p.toFile().isFile())
                        .mapToLong(p -> p.toFile().length())
                        .sum();
                long count = Files.walk(dir)
                        .filter(p -> p.toFile().isFile())
                        .count();
                System.out.printf("\nTest data storage %d files totalling size: %,.1f Mb\n",count,(double)size/(1024d*1024d));
            } catch (Exception e) {
                System.err.println("Failed to measure size of directory. ["+dir.toFile().getAbsolutePath()+"]");
                e.printStackTrace();
            }
        }
    }

    private void hash() {
        try {
            // Block on a previous hash job, if there is one
            if (hashingFuture != null) {
                hashingFuture.get();
            }

            // Make our fast copy
            final var completedMap = contractMap;
            contractMap = completedMap.copy();

            // Exchange our fast copy for a new hashing future
            hashingFuture = hashingExchanger.exchange(new Blar(completedMap)).hashingFuture;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    private ThreadFactory threadFactory(String namePrefix) {
        return r -> {
            Thread th = new Thread(r);
            th.setName(namePrefix);
            th.setDaemon(true);
            th.setUncaughtExceptionHandler((t, e) -> {
                e.printStackTrace();
            });
            return th;
        };
    }

    private static final class Blar {
        Future<Hash> hashingFuture;
        VFCMap<?, ?> map;

        public Blar(VFCMap<?, ?> map) {
            this.hashingFuture = null;
            this.map = map;
        }

        public Blar(Future<Hash> hashingFuture) {
            this.hashingFuture = hashingFuture;
            this.map = map;
        }
    }
}

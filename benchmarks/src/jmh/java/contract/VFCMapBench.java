package contract;

import com.hedera.services.state.merkle.virtual.ContractHashStore;
import com.hedera.services.state.merkle.virtual.ContractLeafStore;
import com.hedera.services.state.merkle.virtual.ContractUint256;
import com.hedera.services.store.models.Id;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.fcmap.VFCMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Microbenchmark tests for the VirtualMap. These benchmarks are just of the tree itself,
 * and not of any underlying storage mechanism. In fact, it just uses an in-memory set of
 * hash maps as the backing VirtualDataSource, and benchmarks those too just so we have
 * baseline numbers.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class VFCMapBench {
    @Param({"5", "10", "15", "20", "25"})
    public int numUpdatesPerOperation;

    @Param({"10000"})
    public int targetOpsPerSecond;

    @Param({"1000"})
    public long maxMillisPerHashRound;

    @Param({"10000", "100000", "1000000", "10000000", "100000000", "1000000000"})
    public long numEntities;

    @Param("0")
    public int preFill;

    private final Random rand = new Random();
    private VFCMap<ContractUint256, ContractUint256> contractMap;
    private Future<?> prevIterationHashingFuture;
    private VFCMap<ContractUint256, ContractUint256> unmodifiableContractMap;
    private ExecutorService background = Executors.newSingleThreadScheduledExecutor();

    @Setup
    public void prepare() throws Exception {
        final var ls = new ContractLeafStore(new Id(1, 2, 3));
        final var hs = new ContractHashStore(new Id(1, 2, 3));
        contractMap = new VFCMap<>(ls, hs);

        if (preFill > 0) {
            for (int i = 0; i < numEntities; i++) {
                if (i % 100000 == 0) {
                    System.out.println("Completed: " + i);
                    if (prevIterationHashingFuture != null) {
                        prevIterationHashingFuture.get();
                    }
                    unmodifiableContractMap = contractMap;
                    contractMap = contractMap.copy();
                    prevIterationHashingFuture = CryptoFactory.getInstance().digestTreeAsync2(unmodifiableContractMap);
                }
                final var key = asContractKey(i);
                final var value = asContractValue(i);
                contractMap.put(key, value);
            }

            // During setup we perform the full hashing and release the old copy. This way,
            // during the tests, we don't have an initial slow hash.
            unmodifiableContractMap = contractMap;
            contractMap = contractMap.copy();
            prevIterationHashingFuture = CryptoFactory.getInstance().digestTreeAsync2(unmodifiableContractMap);
            prevIterationHashingFuture.get();
            unmodifiableContractMap = null;
        }

        printDataStoreSize();
    }

    private Future<?> hashAndRelease() {
        return background.submit(() -> {
            CryptoFactory.getInstance().digestTreeAsync2(unmodifiableContractMap);
            background.submit(() -> unmodifiableContractMap.release());
        });
    }

    @TearDown
    public void destroy() {
        printDataStoreSize();
        /*store.close();*/
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

    /**
     * Benchmarks update operations of an existing tree.
     *
     * @throws Exception Any exception should be treated as fatal.
     */
    @Benchmark
    public void update() throws Exception {
        // Release the old contract after hashing has finished. We specifically block here waiting
        // for the old hashing to complete because in the real world we cannot have hashing fall
        // behind, so the fastest we can go is the max of the speed of modifying the latest and
        // hashing the previous.
        if (unmodifiableContractMap != null) {
            // Wait for the hashing of the previous iteration to complete. If it doesn't complete
            // in maxMillisPerHashRound, then a TimeoutException is raised and the test fails.
            prevIterationHashingFuture.get(maxMillisPerHashRound, TimeUnit.MILLISECONDS);
        }

        // Create a new fast copy and start hashing the old one in a background thread.
        unmodifiableContractMap = contractMap;
        contractMap = contractMap.copy();
        prevIterationHashingFuture = CryptoFactory.getInstance().digestTreeAsync2(unmodifiableContractMap);

        // Start modifying the new fast copy
        final var iterationsPerRound = numUpdatesPerOperation * targetOpsPerSecond;
        for (int j=0; j<iterationsPerRound; j++) {
            final var i = rand.nextInt((int)numEntities);
            contractMap.put(asContractKey(i), asContractValue(i + numEntities));
        }
    }
}

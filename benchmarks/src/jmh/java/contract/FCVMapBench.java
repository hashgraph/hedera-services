package contract;

import com.hedera.services.state.merkle.virtual.ContractDataSource;
import com.hedera.services.state.merkle.virtual.ContractUint256;
import com.hedera.services.store.models.Id;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.merkle.utility.SerializableLong;
import com.swirlds.fcmap.VFCMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Microbenchmark tests for the VirtualMap. These benchmarks are just of the tree itself,
 * and not of any underlying storage mechanism. In fact, it just uses an in-memory set of
 * hash maps as the backing VirtualDataSource, and benchmarks those too just so we have
 * baseline numbers.
 */
@State(Scope.Thread)
@Warmup(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 150, timeUnit = TimeUnit.SECONDS)
@Fork(1)
//@Warmup(iterations = 1, time = 10, timeUnit = TimeUnit.SECONDS)
//@Measurement(iterations = 1, time = 15, timeUnit = TimeUnit.SECONDS)
//@Fork(1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class FCVMapBench {
    private Random rand = new Random();
    private VFCMap<SerializableLong, SerializableLong> inMemoryMap;
    private VFCMap<ContractUint256, ContractUint256> contractMap;
    private Future<?> prevIterationHashingFuture;
    private ExecutorService executorService;

    @Setup
    public void prepare() throws Exception {
        executorService = Executors.newSingleThreadExecutor((r) -> {
            Thread th = new Thread(r);
            th.setName("Hashing Executor");
            th.setDaemon(true);
            return th;
        });

        final var contractDS = new ContractDataSource(new Id(1, 2, 3));
        contractMap = new VFCMap<>(contractDS);

        // Populate the data source with one million items.
//        inMemoryMap = new VFCMap<>();
//        for (int i=0; i<1_000_000; i++) {
//            final var key = asKey(i);
//            final var value = asValue(i);
//            inMemoryMap.put(key, value);
//        }

        for (int i=0; i<1_000_000; i++) {
            final var key = asContractKey(i);
            final var value = asContractValue(i);
            contractMap.put(key, value);
        }

        final var cf = new CompletableFuture<>();
        cf.complete(CryptoFactory.getInstance().getNullHash());
        prevIterationHashingFuture = cf;
    }

    @TearDown
    public void destroy() {
        /*store.close();*/
    }

//    @Benchmark
//    public void baselineDataSource_read(Blackhole bh) {
//        final var i = rand.nextInt(1_000_000);
//        final var key = asKey(i);
//        bh.consume(ds.getData(key));
//    }

//    @Benchmark
//    public void baselineDataSource_update(Blackhole bh) {
//        final var i = rand.nextInt(1_000_000);
//        final var key = asKey(i);
//        final var value = asValue(i + 1_000_000);
//        ds.writeData(key, value);
//    }

//    @Benchmark
//    public void read_10000PerVirtualMap(Blackhole bh) {
//        final var map = new VirtualMap<ByteChunk, ByteChunk>();
//        map.setDataSource(ds);
//        for (int j=0; j<10_000; j++) {
//            final var i = rand.nextInt(1_000_000);
//            final var key = asKey(i);
//            bh.consume(map.getValue(key));
//        }
//    }

//    @Benchmark
//    public void writeOneMillion_25PerVirtualMap() {
//
//    }

//    @Benchmark
//    public void update_LimitedPerVirtualMap_5() {
//        inMemoryMap = inMemoryMap.copy();
//        for (int j=0; j<5; j++) {
//            final var i = rand.nextInt(1_000_000);
//            inMemoryMap.putValue(asKey(i), asValue(i + 1_000_000));
//        }
//        inMemoryMap.commit();
//    }
//
//    @Benchmark
//    public void update_LimitedPerVirtualMap_10() {
//        inMemoryMap = inMemoryMap.copy();
//        for (int j=0; j<10; j++) {
//            final var i = rand.nextInt(1_000_000);
//            inMemoryMap.putValue(asKey(i), asValue(i + 1_000_000));
//        }
//        inMemoryMap.commit();
//    }
//
//    @Benchmark
//    public void update_LimitedPerVirtualMap_15() {
//        inMemoryMap = inMemoryMap.copy();
//        for (int j=0; j<15; j++) {
//            final var i = rand.nextInt(1_000_000);
//            inMemoryMap.putValue(asKey(i), asValue(i + 1_000_000));
//        }
//        inMemoryMap.commit();
//    }
//
//    @Benchmark
//    public void update_LimitedPerVirtualMap_20() {
//        inMemoryMap = inMemoryMap.copy();
//        for (int j=0; j<20; j++) {
//            final var i = rand.nextInt(1_000_000);
//            inMemoryMap.putValue(asKey(i), asValue(i + 1_000_000));
//        }
//        inMemoryMap.commit();
//    }
//
//    @Benchmark
//    public void update_LimitedPerVirtualMap_25() {
//        inMemoryMap = inMemoryMap.copy();
//        for (int j=0; j<25; j++) {
//            final var i = rand.nextInt(1_000_000);
//            inMemoryMap.putValue(asKey(i), asValue(i + 1_000_000));
//        }
//        inMemoryMap.commit();
//    }

    @Benchmark
    public void update_LimitedPerVirtualMap_25k_Contract_Files() throws ExecutionException, InterruptedException {
        // Wait for the hashing of the previous iteration to complete
        try {
            prevIterationHashingFuture.get(1, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            System.err.println("Failed to hash within 1 second! Probably deadlocked?");
            e.printStackTrace();
        }

        // Create a new fast copy and start hashing the old one
        final var old = contractMap;
        contractMap = old.copy();
        prevIterationHashingFuture = executorService.submit(() -> {
            CryptoFactory.getInstance().digestTreeSync(old);
            old.release();
        });

        // Start modifying the new fast copy
        for (int j=0; j<50_000; j++) {
            final var i = rand.nextInt(1_000_000);
            contractMap.put(asContractKey(i), asContractValue(i + 1_000_000));
        }
    }

//    @Benchmark
//    public void update_LimitedPerVirtualMap_25k() throws ExecutionException, InterruptedException {
//        // Wait for the hashing of the previous iteration to complete
//        prevIterationHashingFuture.get();
//
//        // Create a new fast copy and start hashing the old one
//        final var old = inMemoryMap;
//        inMemoryMap = old.copy();
////        prevIterationHashingFuture = executorService.submit(() -> {
////            final var f = CryptoFactory.getInstance().digestTreeAsync(old);
////            try {
////                f.get();
////            } catch (InterruptedException | ExecutionException e) {
////                e.printStackTrace();
////                System.exit(1);
////            }
//            old.release();
////        });
//
//        // Start modifying the new fast copy
//        for (int j=0; j<50_000; j++) {
//            final var i = rand.nextInt(1_000_000);
//            inMemoryMap.put(asKey(i), asValue(i + 1_000_000));
//        }
//    }

//    @Benchmark
//    public void update_LimitedPerVirtualMap_5_NoHashing() {
//        inMemoryMap = inMemoryMap.copy();
//        for (int j=0; j<5; j++) {
//            final var i = rand.nextInt(1_000_000);
//            inMemoryMap.putValue(asKey(i), asValue(i + 1_000_000));
//        }
//        inMemoryMap.commit();
//    }
//
//    @Benchmark
//    public void update_LimitedPerVirtualMap_10_NoHashing() {
//        inMemoryMap = inMemoryMap.copy();
//        for (int j=0; j<10; j++) {
//            final var i = rand.nextInt(1_000_000);
//            inMemoryMap.putValue(asKey(i), asValue(i + 1_000_000));
//        }
//        inMemoryMap.commit();
//    }
//
//    @Benchmark
//    public void update_LimitedPerVirtualMap_15_NoHashing() {
//        inMemoryMap = inMemoryMap.copy();
//        for (int j=0; j<15; j++) {
//            final var i = rand.nextInt(1_000_000);
//            inMemoryMap.putValue(asKey(i), asValue(i + 1_000_000));
//        }
//        inMemoryMap.commit();
//    }
//
//    @Benchmark
//    public void update_LimitedPerVirtualMap_20_NoHashing() {
//        inMemoryMap = inMemoryMap.copy();
//        for (int j=0; j<20; j++) {
//            final var i = rand.nextInt(1_000_000);
//            inMemoryMap.putValue(asKey(i), asValue(i + 1_000_000));
//        }
//        inMemoryMap.commit();
//    }
//
//    @Benchmark
//    public void update_LimitedPerVirtualMap_25_NoHashing() {
//        inMemoryMap = inMemoryMap.copy();
//        for (int j=0; j<25; j++) {
//            final var i = rand.nextInt(1_000_000);
//            inMemoryMap.putValue(asKey(i), asValue(i + 1_000_000));
//        }
//        inMemoryMap.commit();
//    }


//    @Benchmark
//    public void update_LimitedPerVirtualMap_25_1000K_Elements() {
//        inMemoryMap = inMemoryMap.copy();
//        for (int j=0; j<25; j++) {
//            final var i = rand.nextInt(1_000_000);
//            inMemoryMap.putValue(asKey(i), asValue(i + 1_000_000));
//        }
//        inMemoryMap.commit();
//    }

//    @Benchmark
//    public void read_LimitedPerVirtualMap_5(Blackhole blackhole) {
////        inMemoryMap = inMemoryMap.copy();
//        for (int j=0; j<5; j++) {
//            final var i = rand.nextInt(1_000_000);
//            blackhole.consume(inMemoryMap.getValue(asKey(i)));
//        }
////        inMemoryMap.commit();
//    }
//
//    @Benchmark
//    public void read_LimitedPerVirtualMap_10(Blackhole blackhole) {
////        inMemoryMap = inMemoryMap.copy();
//        for (int j=0; j<10; j++) {
//            final var i = rand.nextInt(1_000_000);
//            blackhole.consume(inMemoryMap.getValue(asKey(i)));
//        }
////        inMemoryMap.commit();
//    }
//
//    @Benchmark
//    public void read_LimitedPerVirtualMap_15(Blackhole blackhole) {
////        inMemoryMap = inMemoryMap.copy();
//        for (int j=0; j<15; j++) {
//            final var i = rand.nextInt(1_000_000);
//            blackhole.consume(inMemoryMap.getValue(asKey(i)));
//        }
////        inMemoryMap.commit();
//    }
//
//    @Benchmark
//    public void read_LimitedPerVirtualMap_20(Blackhole blackhole) {
////        inMemoryMap = inMemoryMap.copy();
//        for (int j=0; j<20; j++) {
//            final var i = rand.nextInt(1_000_000);
//            blackhole.consume(inMemoryMap.getValue(asKey(i)));
//        }
////        inMemoryMap.commit();
//    }
//
//    @Benchmark
//    public void read_LimitedPerVirtualMap_25(Blackhole blackhole) {
////        inMemoryMap = inMemoryMap.copy();
//        for (int j=0; j<25; j++) {
//            final var i = rand.nextInt(1_000_000);
//            blackhole.consume(inMemoryMap.getValue(asKey(i)));
//        }
////        inMemoryMap.commit();
//    }

//    @Benchmark
//    public void update_LimitedPerVirtualMap_Files() {
//        final var map = new VirtualMap(ds2);
//        for (int j=0; j<25; j++) {
//            final var i = rand.nextInt(1_000_000);
//            map.putValue(asKey(i), asValue(i + 1_000_000));
//        }
//        map.commit();
//    }

    private SerializableLong asKey(int index) {
        return new SerializableLong(index);
    }

    private SerializableLong asValue(int index) {
        return new SerializableLong(index);
    }

    private ContractUint256 asContractKey(int index) {
        return new ContractUint256(BigInteger.valueOf(index));
    }

    private ContractUint256 asContractValue(int index) {
        return new ContractUint256(BigInteger.valueOf(index));
    }
}

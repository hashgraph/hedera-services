package contract;

import com.hedera.services.state.merkle.virtual.persistence.mmap.MemMapDataStore;
import org.openjdk.jmh.annotations.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

/**
 * Microbenchmark tests for the VirtualMap. These benchmarks are just of the tree itself,
 * and not of any underlying storage mechanism. In fact, it just uses an in-memory set of
 * hash maps as the backing VirtualDataSource, and benchmarks those too just so we have
 * baseline numbers.
 */
@State(Scope.Thread)
//@Warmup(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
//@Measurement(iterations = 5, time = 30, timeUnit = TimeUnit.SECONDS)
//@Fork(3)
@Warmup(iterations = 1, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 1, time = 15, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class MemMapDataStoreBench {
    private static final int MB = 1024*1024;
//    private InMemoryDataSource ds = new InMemoryDataSource();
//    private MemMapDataSource ds2;
//    private VirtualMapDataStore store;
//    private Random rand = new Random();
    private MemMapDataStore store;
    private long[] locations;

    @Setup
    public void prepare() throws Exception {
        final var storeDir = new File("./store").toPath();
        if (Files.exists(storeDir)) {
            try {
                //noinspection ResultOfMethodCallIgnored
                Files.walk(storeDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        store = new MemMapDataStore(32, 100*MB, storeDir,"test", "dat");
        store.open(null);

        // Populate the data source with one million items.
        final int DATA_SIZE = 1_000_000;
        locations = new long[DATA_SIZE];
        for (int i=0; i<DATA_SIZE; i++) {
            long location = store.getNewSlot();
            locations[i] = location;
            ByteBuffer buffer = store.accessSlot(location);
            buffer.putLong(i);
            buffer.putLong(i);
            buffer.putLong(i);
            buffer.putLong(i);
            buffer.putLong(i);
        }

//
//        List<Long> locations = new ArrayList<>(COUNT);
//        for (long i = 0; i < COUNT; i++) {
//            long location = store.getNewSlot();
//            locations.add(location);
//            ByteBuffer buffer = store.accessSlot(location);
//            buffer.putLong(i);
//        }
//        // check all the data is there
//        for (long i = 0; i < 10_000; i++) {
//            int index = (int)(Math.random()*COUNT);
//            long location = locations.get(index);
//            ByteBuffer buffer = store.accessSlot(location);
//            long value = buffer.getLong();
//            Assertions.assertEquals(value,(long)index);
//        }
        store.close();
    }

    @TearDown
    public void destroy() {
        store.close();
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

}

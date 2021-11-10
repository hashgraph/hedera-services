package virtual;

import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Microbenchmark tests for the VirtualMap. These benchmarks are just of the tree itself,
 * and not of any underlying storage mechanism. In fact, it just uses an in-memory set of
 * hash maps as the backing VirtualDataSource, and benchmarks those too just so we have
 * baseline numbers.
 */
@State(Scope.Thread)
@Warmup(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 30, timeUnit = TimeUnit.SECONDS)
@Fork(1)
//@Warmup(iterations = 1, time = 10, timeUnit = TimeUnit.SECONDS)
//@Measurement(iterations = 1, time = 15, timeUnit = TimeUnit.SECONDS)
//@Fork(1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class VirtualMapBench {
  /*  private InMemoryDataSource ds = new InMemoryDataSource();
    private MemMapDataSource ds2;
    private VirtualMapDataStore store;
    private Random rand = new Random();
    private VirtualMap inMemoryMap;
    private ExecutorService executorService;

    @Setup
    public void prepare() throws Exception {
        executorService = Executors.newSingleThreadExecutor((r) -> {
            Thread th = new Thread(r);
            th.setDaemon(true);
            return th;
        });

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

        store = new VirtualMapDataStore(storeDir, 32, 32);
        store.open();
        ds2 = new MemMapDataSource(store,
                new Account(0, 0, 100));

        // Populate the data source with one million items.
        inMemoryMap = new VirtualMap(ds);
//        VirtualMap map2 = new VirtualMap(ds2);
        for (int i=0; i<1_000_000; i++) {
            final var key = asKey(i);
            final var value = asValue(i);
            inMemoryMap.putValue(key, value);
//            map2.putValue(key, value);
        }
//        map2.commit();
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
//
    @Benchmark
    public void update_LimitedPerVirtualMap_25k() throws ExecutionException, InterruptedException {
        final var old = inMemoryMap;
        inMemoryMap = old.copy();
        final var future = executorService.submit(old::release);
        for (int j=0; j<25_000; j++) {
            final var i = rand.nextInt(1_000_000);
            inMemoryMap.putValue(asKey(i), asValue(i + 1_000_000));
        }

        // Block until the release has completed.
        future.get();
    }

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

    private VirtualKey asKey(int index) {
        return new VirtualKey(Arrays.copyOf(("key" + index).getBytes(), 32));
    }

    private VirtualValue asValue(int index) {
        return new VirtualValue(Arrays.copyOf(("val" + index).getBytes(), 32));
    }

    private static final class InMemoryDataSource implements VirtualDataSource {
        private Map<VirtualKey, VirtualRecord> leaves = new ConcurrentHashMap<>();
        private Map<Long, VirtualRecord> leavesByPath = new ConcurrentHashMap<>();
        private Map<Long, Hash> parents = new ConcurrentHashMap<>();
        private long firstLeafPath = INVALID_PATH;
        private long lastLeafPath = INVALID_PATH;
        private boolean closed = false;

        @Override
        public Hash loadParentHash(long parentPath) {
            return parents.get(parentPath);
        }

        @Override
        public VirtualRecord loadLeaf(long leafPath) {
            return leavesByPath.get(leafPath);
        }

        @Override
        public VirtualRecord loadLeaf(VirtualKey leafKey) {
            return leaves.get(leafKey);
        }

        @Override
        public VirtualValue getLeafValue(VirtualKey leafKey) {
            final var rec = leaves.get(leafKey);
            return rec == null ? null : rec.getValue();
        }

        @Override
        public void saveParent(long parentPath, Hash hash) {
            parents.put(parentPath, hash);
        }

        @Override
        public void saveLeaf(VirtualRecord leaf) {
            leaves.put(leaf.getKey(), leaf);
            leavesByPath.put(leaf.getPath(), leaf);
        }

        @Override
        public void deleteParent(long parentPath) {
            parents.remove(parentPath);
        }

        @Override
        public void deleteLeaf(VirtualRecord leaf) {
            leaves.remove(leaf.getKey());
            leavesByPath.remove(leaf.getPath());
        }

        @Override
        public void writeLastLeafPath(long path) {
            this.lastLeafPath = path;
        }

        @Override
        public long getLastLeafPath() {
            return lastLeafPath;
        }

        @Override
        public void writeFirstLeafPath(long path) {
            this.firstLeafPath = path;
        }

        @Override
        public long getFirstLeafPath() {
            return firstLeafPath;
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                throw new IOException("Already closed");
            }
            closed = true;
        }
    }*/
}

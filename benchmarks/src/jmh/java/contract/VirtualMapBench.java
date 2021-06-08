package contract;

import com.hedera.services.state.merkle.virtual.Account;
import com.hedera.services.state.merkle.virtual.VirtualKey;
import com.hedera.services.state.merkle.virtual.VirtualMap;
import com.hedera.services.state.merkle.virtual.VirtualValue;
import com.hedera.services.state.merkle.virtual.persistence.VirtualDataSource;
import com.hedera.services.state.merkle.virtual.persistence.VirtualRecord;
import com.hedera.services.state.merkle.virtual.persistence.mmap.MemMapDataSource;
import com.hedera.services.state.merkle.virtual.persistence.mmap.VirtualMapDataStore;
import com.swirlds.common.crypto.Hash;
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
import org.openjdk.jmh.infra.Blackhole;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.hedera.services.state.merkle.virtual.VirtualTreePath.INVALID_PATH;

/**
 * Microbenchmark tests for the VirtualMap. These benchmarks are just of the tree itself,
 * and not of any underlying storage mechanism. In fact, it just uses an in-memory set of
 * hash maps as the backing VirtualDataSource, and benchmarks those too just so we have
 * baseline numbers.
 */
@State(Scope.Thread)
@Warmup(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 30, timeUnit = TimeUnit.SECONDS)
@Fork(3)
//@Warmup(iterations = 1, time = 10, timeUnit = TimeUnit.SECONDS)
//@Measurement(iterations = 1, time = 15, timeUnit = TimeUnit.SECONDS)
//@Fork(1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class VirtualMapBench {
    private InMemoryDataSource ds = new InMemoryDataSource();
    private MemMapDataSource ds2;
    private VirtualMapDataStore store;
    private Random rand = new Random();
    private VirtualMap inMemoryMap;

    @Setup
    public void prepare() throws Exception {
        System.out.println("PREPARING");
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
        VirtualMap map2 = new VirtualMap(ds2);
        for (int i=0; i<1_000_000; i++) {
            final var key = asKey(i);
            final var value = asValue(i);
            inMemoryMap.putValue(key, value);
            map2.putValue(key, value);
        }
        inMemoryMap.commit();
        map2.commit();
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

    @Benchmark
    public void update_100PerVirtualMap() {
        inMemoryMap = inMemoryMap.copy();
        for (int j=0; j<25; j++) {
            final var i = rand.nextInt(1_000_000);
            inMemoryMap.putValue(asKey(i), asValue(i + 1_000_000));
        }
        inMemoryMap.commit();
    }

    @Benchmark
    public void update_100PerVirtualMap_Files() {
        final var map = new VirtualMap(ds2);
        for (int j=0; j<25; j++) {
            final var i = rand.nextInt(1_000_000);
            map.putValue(asKey(i), asValue(i + 1_000_000));
        }
        map.commit();
    }

    private VirtualKey asKey(int index) {
        return new VirtualKey(Arrays.copyOf(("key" + index).getBytes(), 32));
    }

    private VirtualValue asValue(int index) {
        return new VirtualValue(Arrays.copyOf(("val" + index).getBytes(), 32));
    }

    private static final class InMemoryDataSource implements VirtualDataSource {
        private Map<VirtualKey, VirtualRecord> leaves = new HashMap<>();
        private Map<Long, VirtualRecord> leavesByPath = new HashMap<>();
        private Map<Long, Hash> parents = new HashMap<>();
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
    }
}

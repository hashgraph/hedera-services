package contract;

import com.hedera.services.state.merkle.virtual.ByteChunk;
import com.hedera.services.state.merkle.virtual.Path;
import com.hedera.services.state.merkle.virtual.VirtualMap;
import com.hedera.services.state.merkle.virtual.persistence.VirtualDataSource;
import com.hedera.services.state.merkle.virtual.persistence.VirtualRecord;
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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
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
@Fork(3)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class VirtualMapBench {
    private InMemoryDataSource ds = new InMemoryDataSource();
    private Random rand = new Random();

    @Setup
    public void prepare() throws Exception {
        // Populate the data source with one million items.
        VirtualMap<ByteChunk, ByteChunk> map = new VirtualMap<>();
        map.setDataSource(ds);
        for (int i=0; i<1_000_000; i++) {
            final var key = asKey(i);
            final var value = asValue(i);
            map.putValue(key, value);
        }
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
        final var map = new VirtualMap<ByteChunk, ByteChunk>();
        map.setDataSource(ds);
        for (int j=0; j<25; j++) {
            final var i = rand.nextInt(1_000_000);
            map.putValue(asKey(i), asValue(i + 1_000_000));
        }
    }

    private ByteChunk asKey(int index) {
        return new ByteChunk(Arrays.copyOf(("key" + index).getBytes(), 32));
    }

    private ByteChunk asValue(int index) {
        return new ByteChunk(Arrays.copyOf(("val" + index).getBytes(), 32));
    }


    private static final class InMemoryDataSource implements VirtualDataSource<ByteChunk, ByteChunk> {
        private Map<ByteChunk, VirtualRecord<ByteChunk, ByteChunk>> recordsByKey = new HashMap<>();
        private Map<Path, VirtualRecord<ByteChunk, ByteChunk>> recordsByPath = new HashMap<>();
        private Path firstLeafPath;
        private Path lastLeafPath;
        private boolean closed = false;

        @Override
        public VirtualRecord<ByteChunk, ByteChunk> getRecord(ByteChunk key) {
            return recordsByKey.get(key);
        }

        @Override
        public VirtualRecord<ByteChunk, ByteChunk> getRecord(Path path) {
            return recordsByPath.get(path);
        }

        @Override
        public void deleteRecord(VirtualRecord<ByteChunk, ByteChunk> record) {
            if (record != null) {
                recordsByPath.remove(record.getPath());
                recordsByKey.remove(record.getKey());
            }
        }

        @Override
        public void setRecord(VirtualRecord<ByteChunk, ByteChunk> record) {
            if (record != null) {
                if (record.isLeaf()) {
                    recordsByKey.put(record.getKey(), record);
                }
                recordsByPath.put(record.getPath(), record);
            }
        }

        @Override
        public void writeLastLeafPath(Path path) {
            this.lastLeafPath = path;
        }

        @Override
        public Path getLastLeafPath() {
            return lastLeafPath;
        }

        @Override
        public void writeFirstLeafPath(Path path) {
            this.firstLeafPath = path;
        }

        @Override
        public Path getFirstLeafPath() {
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

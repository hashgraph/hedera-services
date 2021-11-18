package jasperdb.virtualdatasource;

import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static utils.CommonTestUtils.hash;

@State(Scope.Thread)
@SuppressWarnings("DefaultAnnotationParam")
@Warmup(iterations = 2, time = 15, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 60, time = 30, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class UpdateBench {
    public static final int NUMBER_OF_LEAVES_CHANGED_PER_ROUND = 1000; // 2x 500 required
    public static final int NUMBER_OF_INTERNAL_NODES_CHANGED_PER_LEAF = 25;
    public static final int NUMBER_OF_ROUNDS_PER_FLUSH = 20; 
    public static final int NUMBER_OF_LEAVES_CHANGED_PER_FLUSH =
            NUMBER_OF_ROUNDS_PER_FLUSH * NUMBER_OF_LEAVES_CHANGED_PER_ROUND;
    public static final int NUMBER_OF_INTERNALS_CHANGED_PER_FLUSH = 
            NUMBER_OF_LEAVES_CHANGED_PER_FLUSH * NUMBER_OF_INTERNAL_NODES_CHANGED_PER_LEAF;
    public Random random;
    private final long[] randomInternalPaths = new long[NUMBER_OF_INTERNALS_CHANGED_PER_FLUSH];
    private final long[] randomLeafPaths = new long[NUMBER_OF_LEAVES_CHANGED_PER_FLUSH];

    @Setup(Level.Trial)
    public void setup() throws IOException {
        random = new Random(1234);
    }

    @Setup(Level.Invocation)
    public void setupInvocation(DatabaseState databaseState) {
        for (int i = 0; i < NUMBER_OF_INTERNALS_CHANGED_PER_FLUSH; i++) {
            randomInternalPaths[i] = random.nextInt(databaseState.initialDataSize);
        }
        for (int i = 0; i < NUMBER_OF_LEAVES_CHANGED_PER_FLUSH; i++) {
            randomLeafPaths[i] = databaseState.initialDataSize + randomInternalPaths[i];
        }
    }

    @Benchmark
    public void updateLeavesAndInternals(DatabaseMergingState databaseState) throws IOException {
        var internalRecordStream = IntStream.range(0,NUMBER_OF_INTERNALS_CHANGED_PER_FLUSH)
                .mapToObj(i -> new VirtualInternalRecord(randomInternalPaths[i], hash((int)randomInternalPaths[i])));
        var leafRecordStream = IntStream.range(0,NUMBER_OF_LEAVES_CHANGED_PER_FLUSH)
                .mapToObj(i -> {
                    final long path = randomLeafPaths[i];
                    return new VirtualLeafRecord<>(
                            path,
                            hash((int)path),
                            new ContractKey(path,path),
                            new ContractValue(path)
                    );
                });
        databaseState.dataSource.saveRecords(
                databaseState.dataSource.getFirstLeafPath(), databaseState.dataSource.getLastLeafPath(),
                internalRecordStream, leafRecordStream, Stream.empty());
    }
}

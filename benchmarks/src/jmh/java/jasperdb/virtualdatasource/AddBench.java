package jasperdb.virtualdatasource;

import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static utils.CommonTestUtils.hash;

@SuppressWarnings("DefaultAnnotationParam")
@Warmup(iterations = 2, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 60, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class AddBench {
    // This is much larger than realistic but keeps files big enough to measure writing performance
    public static final int NUMBER_OF_LEAVES_ADDED_PER_ROUND = 10_000;
    public static final int NUMBER_OF_ROUNDS_PER_FLUSH = 20;
    public static final int NUMBER_OF_LEAVES_ADDED_PER_FLUSH =
            NUMBER_OF_ROUNDS_PER_FLUSH * NUMBER_OF_LEAVES_ADDED_PER_ROUND;

    @Benchmark
    public void add500Leaves(DatabaseMergingState databaseState) throws IOException {
        final long firstLeafPath = databaseState.dataSource.getFirstLeafPath();
        final long lastLeafPath = databaseState.dataSource.getLastLeafPath();
        final long newFirstLeafPath = firstLeafPath + NUMBER_OF_LEAVES_ADDED_PER_FLUSH;
        final long newLastLeafPath = lastLeafPath + NUMBER_OF_LEAVES_ADDED_PER_FLUSH + NUMBER_OF_LEAVES_ADDED_PER_FLUSH;
        var internalRecordStream = LongStream.range(firstLeafPath,newFirstLeafPath)
                .mapToObj(path -> new VirtualInternalRecord(path, hash((int)path)));
        var leafRecordStream = LongStream.range(lastLeafPath,newLastLeafPath+1)
                .mapToObj(path -> new VirtualLeafRecord<>(
                        path,
                        hash((int)path),
                        new ContractKey(path,path),
                        new ContractValue(path)
                ));
        databaseState.dataSource.saveRecords(
                newFirstLeafPath, newLastLeafPath, internalRecordStream, leafRecordStream, Stream.empty());
    }
}

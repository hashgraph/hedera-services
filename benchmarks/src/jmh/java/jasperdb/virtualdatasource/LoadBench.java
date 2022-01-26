package jasperdb.virtualdatasource;


import com.hedera.services.state.virtual.ContractKey;
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

@State(Scope.Thread)
@SuppressWarnings("DefaultAnnotationParam")
@Warmup(iterations = 2, time = 15, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 40, time = 30, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class LoadBench {
    public Random random;
    private long randomInternalPath;
    private long randomLeafPath;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        random = new Random(1234);
    }

    @Setup(Level.Invocation)
    public void setupInvocation(DatabaseState databaseState) {
        randomInternalPath = random.nextInt(databaseState.initialDataSize);
        randomLeafPath = databaseState.initialDataSize + randomInternalPath;
    }

    @Benchmark
    public void randomLoadInternalRecord(DatabaseState databaseState) throws IOException {
        databaseState.dataSource.loadInternalRecord(randomInternalPath);
    }

    @Benchmark
    public void randomLoadLeafRecordByPath(DatabaseState databaseState) throws IOException {
        databaseState.dataSource.loadLeafRecord(randomLeafPath);
    }

    @Benchmark
    public void randomLoadLeafRecordByKey(DatabaseState databaseState) throws IOException {
        databaseState.dataSource.loadLeafRecord(new ContractKey(randomLeafPath,randomLeafPath));
    }
}

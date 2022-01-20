package jasperdb.virtualdatasource;

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
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import static utils.CommonTestUtils.deleteDirectoryAndContents;

@State(Scope.Thread)
@SuppressWarnings("DefaultAnnotationParam")
@Warmup(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 6, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.SECONDS)
public class SnapshotBench {

    @Setup(Level.Invocation)
    public void setupInvocation() {
        if (Files.exists(DatabaseState.dataSourceSnapshotPath)) {
            deleteDirectoryAndContents(DatabaseState.dataSourceSnapshotPath);
        }
    }

    @Benchmark
    public void snapshot(DatabaseState databaseState) throws IOException {
        databaseState.dataSource.snapshot(DatabaseState.dataSourceSnapshotPath);
    }
}

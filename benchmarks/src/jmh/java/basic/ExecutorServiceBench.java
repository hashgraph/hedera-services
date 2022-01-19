package basic;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ExecutorServiceBench {
    private final ExecutorService pool = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(), (r) -> {
        final var thread = new Thread(r);
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((t, e) -> {
            e.printStackTrace();
        });
        return thread;
    });

    @Benchmark
    public void executeOverhead(Blackhole blackhole) {
        pool.execute(() -> { blackhole.consume(Math.random()); });
    }
}

package jasperdb;

import com.swirlds.jasperdb.collections.HashList;
import com.swirlds.jasperdb.collections.HashListByteBuffer;
import org.openjdk.jmh.annotations.*;
import utils.CommonTestUtils;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static utils.CommonTestUtils.hash;

/**
 * Benchmark for testing relative performance of HashList implementations
 */
@SuppressWarnings("DefaultAnnotationParam")
@State(Scope.Thread)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class HashListBench {
    public static final int INITIAL_DATA_SIZE = 10_000_000;
    public Random random;
    private int randomIndex;
    private HashList list;

    @Param({"HashListHeap","HashListOffHeap"})
    public String listImpl;

    @Setup(Level.Trial)
    public void setup() {
        random = new Random(1234);
        list = new HashListByteBuffer(100_000,INITIAL_DATA_SIZE,"HashListOffHeap".equals(listImpl));
        // fill with some data
        for (int i = 0; i < INITIAL_DATA_SIZE; i++) {
            list.put(i, hash(i));
        }
        // print memory usage
        System.out.printf("Memory for initial %,d accounts:\n",INITIAL_DATA_SIZE);
        CommonTestUtils.printMemoryUsage();
    }

    @Setup(Level.Invocation)
    public void randomIndex(){
        randomIndex = random.nextInt(INITIAL_DATA_SIZE);
    }

    @Benchmark
    public void a_randomGet() throws IOException {
        list.get(randomIndex);
    }

    @Benchmark
    public void b_randomSet() {
        list.put(randomIndex, hash(randomIndex*2));
    }

    @Benchmark
    public void a_multiThreadedRead10k() {
        IntStream.range(0,5).parallel().forEach(jobID -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 2000; i++) {
                try {
                    list.get(random.nextInt(INITIAL_DATA_SIZE));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}

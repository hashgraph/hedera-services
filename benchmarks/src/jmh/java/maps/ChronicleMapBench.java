package maps;

import net.openhft.chronicle.core.values.LongValue;
import net.openhft.chronicle.map.ChronicleMapBuilder;
import net.openhft.chronicle.values.Values;
import org.openjdk.jmh.annotations.*;
import virtual.VFCMapBenchBase;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.Random;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static virtual.VFCMapBenchBase.asAccount;

@State(Scope.Thread)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(0)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ChronicleMapBench {
    public static final int INITIAL_DATA_SIZE = 10_000_000;
    public Random random = new Random(1234);
    private long randomIndex;
    private ConcurrentMap<LongValue, VFCMapBenchBase.Account> accountMap;
    private final LongValue key = Values.newHeapInstance(LongValue.class);
    private int nextIndex = INITIAL_DATA_SIZE;

    @Setup(Level.Trial)
    public void setup() {
        accountMap = ChronicleMapBuilder
                        .of(LongValue.class, VFCMapBenchBase.Account.class)
                        .name("account-map")
                        .entries(50_000_000)
                        .averageValue(asAccount(1))
                        .create();
        // fill with some data
        for (int i = 0; i < INITIAL_DATA_SIZE; i++) {
            key.setValue(i);
            accountMap.put(key, asAccount(i));
        }
        // print memory usage
        System.out.printf("Memory for initial %,d accounts:\n",INITIAL_DATA_SIZE);
        printMemoryUsage();
    }

    @Setup(Level.Invocation)
    public void randomIndex(){
        randomIndex = random.nextInt(INITIAL_DATA_SIZE);
    }

    @Benchmark
    public void getRandom() {
        key.setValue(randomIndex);
        accountMap.get(key);
    }

    @Benchmark
    public void updateRandom() {
        key.setValue(randomIndex);
        accountMap.put(key,asAccount(randomIndex*2));
    }

    @Benchmark
    public void add() throws Exception {
        key.setValue(nextIndex);
        accountMap.put(key,asAccount(nextIndex));
        nextIndex ++;
    }

    @Benchmark
    public void multiThreadedRead10k() {
        IntStream.range(0,5).parallel().forEach(jobID -> {
            final LongValue key = Values.newHeapInstance(LongValue.class);
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 2000; i++) {
                key.setValue(random.nextInt(INITIAL_DATA_SIZE));
                accountMap.get(key);
            }
        });
    }

    @Benchmark
    public void multiThreadedReadPut10kEach() {
        IntStream.range(0,5).parallel().forEach(jobID -> {
            final LongValue key = Values.newHeapInstance(LongValue.class);
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 2000; i++) {
                key.setValue(random.nextInt(INITIAL_DATA_SIZE));
                accountMap.get(key);
                key.setValue(random.nextInt(INITIAL_DATA_SIZE));
                accountMap.put(key, asAccount(random.nextLong()));
            }
        });
    }

    public void printMemoryUsage() {
        for (MemoryPoolMXBean mpBean : ManagementFactory.getMemoryPoolMXBeans()) {
            if (mpBean.getType() == MemoryType.HEAP) {
                System.out.printf("     Name: %s: %s\n",mpBean.getName(), mpBean.getUsage());
            }
        }
    }

}

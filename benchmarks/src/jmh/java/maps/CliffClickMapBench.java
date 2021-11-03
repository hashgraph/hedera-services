package maps;

import com.hedera.services.state.virtual.ContractValue;
import org.cliffc.high_scale_lib.NonBlockingHashMapLong;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@State(Scope.Thread)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-XX:+UseZGC"})
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class CliffClickMapBench {
    public Random random = new Random(1234);
    private long randomIndex;
    private NonBlockingHashMapLong<ContractValue> map;
    private int nextIndex;

    @Param({"2000000"})
    public int numEntities;

    @Setup(Level.Trial)
    public void setup() {
        nextIndex = numEntities;
        map = new NonBlockingHashMapLong<>(false);
        // fill with some data
        for (int i = 0; i < numEntities; i++) {
            map.put(i, new ContractValue(i));
        }
        // print memory usage
        System.out.printf("Memory for initial %,d accounts:\n", numEntities);
        printMemoryUsage();
    }

    @Setup(Level.Invocation)
    public void randomIndex(){
        randomIndex = random.nextInt(numEntities);
    }

    @TearDown(Level.Iteration)
    public void printSize(){
        System.out.println("map.size() = " + map.size());
        map.clear();
    }

    @Benchmark
    public void getRandom(Blackhole blackHole) {
        blackHole.consume(map.get(randomIndex));
    }

    @Benchmark
    public void updateRandom() {
        map.put(randomIndex,new ContractValue(randomIndex*2));
    }

    @Benchmark
    public void add() throws Exception {
        map.put(nextIndex,new ContractValue(nextIndex));
        nextIndex ++;
    }

    @Benchmark
    public void putIfAbsent() throws Exception {
        map.putIfAbsent(nextIndex,new ContractValue(nextIndex));
        nextIndex ++;
    }

    @Benchmark
    public void multiThreadedRead10k(Blackhole blackHole) {
        IntStream.range(0,5).parallel().forEach(jobID -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 2000; i++) {
                blackHole.consume(
                        map.get(random.nextInt(numEntities))
                );
            }
        });
    }

    @Benchmark
    public void multiThreadedReadPut10kEach(Blackhole blackHole) {
        IntStream.range(0,5).parallel().forEach(jobID -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 2000; i++) {
                blackHole.consume(map.get(random.nextInt(numEntities)));
                map.put(random.nextInt(numEntities), new ContractValue(random.nextLong()));
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

package maps;

import com.hedera.services.state.merkle.virtual.ContractUint256;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@SuppressWarnings("DefaultAnnotationParam")
@State(Scope.Thread)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-XX:+UseZGC"})
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ConcurrentMapBench {
    public Random random = new Random(1234);
    private long randomIndex;
    private Map<Long,ContractUint256> map;
    private long nextIndex;


    @Param({"2000000"})
    public int numEntities;
    @Param({"ConcurrentHashMap","ConcurrentSkipListMap","SynchronizedHashMap"})
    public String impl;

    @Setup(Level.Trial)
    public void setup() {
        nextIndex = numEntities;
        map = switch(impl) {
            case "SynchronizedHashMap" -> Collections.synchronizedMap(new HashMap<>());
            case "ConcurrentSkipListMap" -> new ConcurrentSkipListMap<>();
            default -> new ConcurrentHashMap<>();
        };
        // fill with some data
        for (long i = 0; i < numEntities; i++) {
            map.put(i, new ContractUint256(i));
        }
        // print memory usage
        System.out.printf("Memory for initial %,d accounts:\n", numEntities);
        printMemoryUsage();
    }

    @Setup(Level.Invocation)
    public void randomIndex(){
        randomIndex = random.nextInt(numEntities);
    }

    @Benchmark
    public void getRandom(Blackhole blackHole) {
        blackHole.consume(map.get(randomIndex));
    }

    @Benchmark
    public void updateRandom() {
        map.put(randomIndex,new ContractUint256(randomIndex*2));
    }

    @Benchmark
    public void add() throws Exception {
        map.put(nextIndex,new ContractUint256(nextIndex));
        nextIndex ++;
    }

    @Benchmark
    public void multiThreadedRead10k(Blackhole blackHole) {
        IntStream.range(0,5).parallel().forEach(jobID -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 2000; i++) {
                blackHole.consume(
                        map.get((long)random.nextInt(numEntities))
                );
            }
        });
    }

    @Benchmark
    public void multiThreadedReadPut10kEach(Blackhole blackHole) {
        IntStream.range(0,5).parallel().forEach(jobID -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 2000; i++) {
                blackHole.consume(map.get((long)random.nextInt(numEntities)));
                map.put((long)random.nextInt(numEntities), new ContractUint256(random.nextLong()));
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

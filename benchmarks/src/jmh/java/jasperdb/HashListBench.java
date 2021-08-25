package jasperdb;

import com.hedera.services.state.jasperdb.collections.*;
import com.swirlds.common.crypto.Hash;
import fcmmap.FCVirtualMapTestUtils;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
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
@Fork(1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class HashListBench {
    public static final int INITIAL_DATA_SIZE = 10_000_000;
    public Random random;
    private int randomIndex;
    private HashList list;
    private int nextIndex = INITIAL_DATA_SIZE;

    @Param({"HashListHeap","HashListOffHeap","HashListHeapArrays","HashListOffHeapPrivate"})
    public String listImpl;

    @Setup(Level.Trial)
    public void setup() {
        random = new Random(1234);
        list = switch (listImpl) {
            default -> new HashListHeap();
            case "HashListOffHeap" -> new HashListOffHeap();
            case "HashListHeapArrays" -> new HashListHeapArrays();
            case "HashListOffHeapPrivate" -> new HashListOffHeapPrivate();
        };
        // fill with some data
        for (int i = 0; i < INITIAL_DATA_SIZE; i++) {
            list.put(i, hash(i));
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
    public void a_randomGet() throws IOException {
        list.get(randomIndex);
    }

    @Benchmark
    public void b_randomSet() {
        list.put(randomIndex, hash(randomIndex*2));
    }
//
//    @Benchmark
//    public void b_add() {
//        list.put(nextIndex,hash(nextIndex));
//        nextIndex ++;
//    }

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

    public void printMemoryUsage() {
        for (MemoryPoolMXBean mpBean : ManagementFactory.getMemoryPoolMXBeans()) {
            if (mpBean.getType() == MemoryType.HEAP) {
                System.out.printf("     Name: %s: %s\n",mpBean.getName(), mpBean.getUsage());
            }
        }
        System.out.println("    Runtime.getRuntime().totalMemory() = " + Runtime.getRuntime().totalMemory());
    }

    /**
     * Creates a hash containing a int repeated 6 times as longs
     *
     * @return byte array of 6 longs
     */
    public static Hash hash(int value) {
        value++;
        byte b0 = (byte)(value >>> 24);
        byte b1 = (byte)(value >>> 16);
        byte b2 = (byte)(value >>> 8);
        byte b3 = (byte)value;
        return new FCVirtualMapTestUtils.TestHash(new byte[] {
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3
        });
    }
}

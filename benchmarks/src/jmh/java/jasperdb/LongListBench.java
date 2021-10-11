package jasperdb;

import com.swirlds.jasperdb.collections.LongList;
import com.swirlds.jasperdb.collections.LongListHeap;
import com.swirlds.jasperdb.collections.LongListOffHeap;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
public class LongListBench {
    public static final int INITIAL_DATA_SIZE = 10_000_000;
    public Random random;
    private int randomIndex;
    private LongList list;
    private int nextIndex = INITIAL_DATA_SIZE;

    @Param({"LongListHeap","LongListOffHeap"/*,"SynchronizedArrayList"*/})
    public String listImpl;

    @Setup(Level.Trial)
    public void setup() {
        random = new Random(1234);
        list = switch (listImpl) {
            default -> new LongListHeap();
            case "LongListOffHeap" -> new LongListOffHeap();
//            case "SynchronizedArrayList" -> new SynchronizedArrayList();
        };
        // fill with some data
        for (int i = 0; i < INITIAL_DATA_SIZE; i++) {
            list.put(i, i);
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
    public void a_randomGet() {
        list.get(randomIndex,-1);
    }

    @Benchmark
    public void b_randomSet() {
        list.put(randomIndex,randomIndex*2L);
    }

    @Benchmark
    public void a_randomCompareAndSet() {
        list.putIfEqual(randomIndex,randomIndex,randomIndex*2L);
    }

    @Benchmark
    public void b_add() throws Exception {
        list.put(nextIndex,nextIndex);
        nextIndex ++;
    }

    @Benchmark
    public void a_multiThreadedRead10k() {
        IntStream.range(0,5).parallel().forEach(jobID -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 2000; i++) {
                list.get(random.nextInt(INITIAL_DATA_SIZE),-1);
            }
        });
    }

    @Benchmark
    public void b_multiThreadedReadPut10kEach() {
        IntStream.range(0,5).parallel().forEach(jobID -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 2000; i++) {
                list.get(random.nextInt(INITIAL_DATA_SIZE),-1);
                list.put(random.nextInt(INITIAL_DATA_SIZE),random.nextLong());
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

//    public static class SynchronizedArrayList implements LongList {
//        private final List<Long> data = new ArrayList<>();
//
//        @Override
//        public synchronized long get(long index, long notFoundValue) {
//            Long value =  data.get((int)index);
//            return value == null ? notFoundValue : value;
//        }
//
//        @Override
//        public synchronized void put(long index, long value) {
//            int iIndex = (int) index;
//            while (data.size() <= iIndex) {
//                data.add(0L);
//            }
//            data.set(iIndex,value);
//        }
//
//        @Override
//        public synchronized boolean putIfEqual(long index, long oldValue, long newValue) {
//            Long value =  data.get((int)index);
//            if (value != null && value == oldValue) {
//                data.set((int) index,newValue);
//                return true;
//            } else {
//                return false;
//            }
//        }
//
//        @Override
//        public synchronized long capacity() {
//            return data.size();
//        }
//
//        @Override
//        public synchronized long size() {
//            return data.size();
//        }
//
//        @Override
//        public int getNumLongsPerChunk() {
//            return 0;
//        }
//
//        @Override
//        public void writeToFile(Path path) throws IOException {
//
//        }
//    }
}

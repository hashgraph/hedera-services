package jasperdb;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.swirlds.jasperdb.collections.LongList;
import com.swirlds.jasperdb.collections.LongListOffHeap;
import org.cliffc.high_scale_lib.NonBlockingHashMapLong;
import org.eclipse.collections.api.map.primitive.MutableLongLongMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import org.openjdk.jmh.annotations.*;

import javax.management.MBeanServer;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@State(Scope.Thread)
@Warmup(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 6, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ConcurrentLongMapBench {

    @Param({"1000000"})
    public int numOfChanges;

    @Param({"ConcurrentHashMap","LongLongHashMap","NonBlockingHashMapLong"})
    public String mapType;

    final LongList longListIndex = new LongListOffHeap();
    final MutableLongLongMap filledLongLongHashMap = new LongLongHashMap(numOfChanges).asSynchronized();
    final ConcurrentHashMap<Long,Long> filledConcurrentHashMap = new ConcurrentHashMap<>(numOfChanges);
    final NonBlockingHashMapLong<Long> filledNonBlockingHashMapLong = new NonBlockingHashMapLong<>(numOfChanges);

    @Setup(Level.Trial)
    public void setup() throws Exception {
        final int numOfThreads = Runtime.getRuntime().availableProcessors();
        final int valuesPerThread = numOfChanges / numOfThreads;
        IntStream.range(0,numOfThreads).parallel().forEach(threadNum -> {
            for (long i = 0; i < valuesPerThread; i++) {
                filledLongLongHashMap.put(threadNum*i,threadNum*i);
                filledConcurrentHashMap.put(threadNum*i,threadNum*i);
                filledNonBlockingHashMapLong.put(threadNum*i,Long.valueOf(threadNum*i));
            }
        });
        if (!Files.exists(Path.of("initial-memory.hprof"))) {
            HeapDumper.dumpHeap("initial-memory.hprof", true);
        }
    }

    @Benchmark
    public void fillMapSingleThreaded() {
        if ("LongLongHashMap".equals(mapType)){
            MutableLongLongMap longLongHashMap = new LongLongHashMap(numOfChanges).asSynchronized();
            for (long i = 0; i < numOfChanges; i++) {
                longLongHashMap.put(i,i);
            }
        } else if ("ConcurrentHashMap".equals(mapType)){
            ConcurrentHashMap<Long,Long> concurrentHashMap = new ConcurrentHashMap<>(numOfChanges);
            for (long i = 0; i < numOfChanges; i++) {
                concurrentHashMap.put(i,i);
            }
        } else if ("NonBlockingHashMapLong".equals(mapType)){
            NonBlockingHashMapLong<Long> nonBlockingHashMapLong = new NonBlockingHashMapLong<>(numOfChanges);
            for (long i = 0; i < numOfChanges; i++) {
                nonBlockingHashMapLong.put(i,Long.valueOf(i));
            }
        }
    }

    @Benchmark
    public void fillMapMultiThreaded() {
        final int numOfThreads = 20;
        final int valuesPerThread = numOfChanges / numOfThreads;
        if ("LongLongHashMap".equals(mapType)){
            MutableLongLongMap longLongHashMap = new LongLongHashMap(numOfChanges).asSynchronized();
            IntStream.range(0,numOfThreads).parallel().forEach(threadNum -> {
                for (long i = 0; i < valuesPerThread; i++) {
                    longLongHashMap.put(threadNum*i,threadNum*i);
                }
            });
        } else if ("ConcurrentHashMap".equals(mapType)){
            ConcurrentHashMap<Long,Long> concurrentHashMap = new ConcurrentHashMap<>(numOfChanges);
            IntStream.range(0,numOfThreads).parallel().forEach(threadNum -> {
                for (long i = 0; i < valuesPerThread; i++) {
                    concurrentHashMap.put(threadNum*i,threadNum*i);
                }
            });
        } else if ("NonBlockingHashMapLong".equals(mapType)){
            NonBlockingHashMapLong<Long> nonBlockingHashMapLong = new NonBlockingHashMapLong<>(numOfChanges);
            IntStream.range(0,numOfThreads).parallel().forEach(threadNum -> {
                for (long i = 0; i < valuesPerThread; i++) {
                    nonBlockingHashMapLong.put(threadNum*i,Long.valueOf(threadNum*i));
                }
            });
        }
    }

    @Benchmark
    public void readSingleThreaded() {
        if ("LongLongHashMap".equals(mapType)){
            for (long i = 0; i < numOfChanges; i++) {
                filledLongLongHashMap.get(i);
            }
        } else if ("ConcurrentHashMap".equals(mapType)){
            for (long i = 0; i < numOfChanges; i++) {
                filledConcurrentHashMap.get(i);
            }
        } else if ("NonBlockingHashMapLong".equals(mapType)){
            for (long i = 0; i < numOfChanges; i++) {
                filledNonBlockingHashMapLong.get(i);
            }
        }
    }

    @Benchmark
    public void readMultiThreaded() {
        final int numOfThreads = 20;
        final int valuesPerThread = numOfChanges / numOfThreads;
        if ("LongLongHashMap".equals(mapType)){
            IntStream.range(0,numOfThreads).parallel().forEach(threadNum -> {
                for (long i = 0; i < valuesPerThread; i++) {
                    filledLongLongHashMap.get(threadNum*i);
                }
            });
        } else if ("ConcurrentHashMap".equals(mapType)){
            IntStream.range(0,numOfThreads).parallel().forEach(threadNum -> {
                for (long i = 0; i < valuesPerThread; i++) {
                    filledConcurrentHashMap.get(threadNum*i);
                }
            });
        } else if ("NonBlockingHashMapLong".equals(mapType)){
            IntStream.range(0,numOfThreads).parallel().forEach(threadNum -> {
                for (long i = 0; i < valuesPerThread; i++) {
                    filledNonBlockingHashMapLong.get(threadNum*i);
                }
            });
        }
    }

    @Benchmark
    public void mergeToLongList() {
        if ("LongLongHashMap".equals(mapType)){
            filledLongLongHashMap.forEachKeyValue(longListIndex::put);
        } else if ("ConcurrentHashMap".equals(mapType)){
            filledConcurrentHashMap.forEachEntry(100_000,longLongEntry -> longListIndex.put(longLongEntry.getKey(), longLongEntry.getValue()));
        } else if ("NonBlockingHashMapLong".equals(mapType)){
            filledNonBlockingHashMapLong.forEach(longListIndex::put);
        }
    }

    public static class HeapDumper {
        // This is the name of the HotSpot Diagnostic MBean
        private static final String HOTSPOT_BEAN_NAME = "com.sun.management:type=HotSpotDiagnostic";
        // field to store the hotspot diagnostic MBean
        private static volatile HotSpotDiagnosticMXBean hotspotMBean;
    /**
     * Call this method from your application whenever you want to dump the heap snapshot into a file.
     *
     * @param fileName name of the heap dump file
     * @param live flag that tells whether to dump only the live objects
     */
        static void dumpHeap(String fileName, boolean live) {
            // initialize hotspot diagnostic MBean
            initHotspotMBean();
            try {
                hotspotMBean.dumpHeap(fileName, live);
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception exp) {
                throw new RuntimeException(exp);
            }
        }

        // initialize the hotspot diagnostic MBean field
        private static void initHotspotMBean() {
            if (hotspotMBean == null) {
                synchronized (HeapDumper.class) {
                    if (hotspotMBean == null) {
                        hotspotMBean = getHotspotMBean();
                    }
                }
            }
        }

        // get the hotspot diagnostic MBean from the
        // platform MBean server
        private static HotSpotDiagnosticMXBean getHotspotMBean() {
            try {
                MBeanServer server = ManagementFactory.getPlatformMBeanServer();
                HotSpotDiagnosticMXBean bean =
                        ManagementFactory.newPlatformMXBeanProxy(server,
                                HOTSPOT_BEAN_NAME, HotSpotDiagnosticMXBean.class);
                return bean;
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception exp) {
                throw new RuntimeException(exp);
            }
        }
    }
}

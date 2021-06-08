package contract;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@State(Scope.Group)
public class BlockingQueueBenchmark {
    private static final int WRITING_THREAD_COUNT = 1;
    private static final int JDK_QUEUE_LENGTH    = 4000;

    LinkedBlockingQueue jdkQueue;
    private long jdkCount = 0;

    @Setup()
    public void setup() {
        jdkQueue = new LinkedBlockingQueue(JDK_QUEUE_LENGTH);
    }

//    @Benchmark
//    @Group("jdk")
//    @GroupThreads(WRITING_THREAD_COUNT)
//    public void offerJDK() {
//        try {
//            jdkQueue.put("event");
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    @Benchmark
//    @Group("jdk")
//    @GroupThreads(1)
//    public void takeJDK() {
//        try {
//            jdkQueue.poll(100, TimeUnit.SECONDS);
//            jdkCount++;
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//    }
    @TearDown(Level.Trial)
    public void printCounts() {
        System.out.println("jdkCount " + jdkCount);
    }
}
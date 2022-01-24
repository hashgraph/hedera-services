package basic;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@State(Scope.Group)
public class BlockingQueueBenchmark {
    private static final int JDK_QUEUE_LENGTH    = 4000;

    private LinkedBlockingQueue<String> jdkQueue;
    private long jdkCount = 0;

    @Setup()
    public void setup() {
        jdkQueue = new LinkedBlockingQueue<>(JDK_QUEUE_LENGTH);
    }

    @Benchmark
    @Group("jdk")
    @GroupThreads(5)
    public void offerJDK() {
        try {
            jdkQueue.put("event");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Benchmark
    @Group("jdk")
    @GroupThreads(5)
    public void takeJDK() {
        try {
            jdkQueue.poll(100, TimeUnit.SECONDS);
            jdkCount++;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @TearDown(Level.Trial)
    public void printCounts() {
        System.out.println("jdkCount " + jdkCount);
    }
}
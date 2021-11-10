package jasperdb;

import com.swirlds.jasperdb.collections.LongList;
import com.swirlds.jasperdb.collections.LongListHeap;
import com.swirlds.jasperdb.collections.LongListOffHeap;
import org.openjdk.jmh.annotations.*;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@SuppressWarnings("DuplicatedCode")
@State(Scope.Thread)
@Warmup(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 6, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.SECONDS)
public class IndexDumpConcurrentBench {
    static final long GB = 1024*1024*1024;

    @Param({"heap","offHeap"})
    public String memoryType;

    private LongList data1;
    private LongList data2;
    private LongList data3;
    private Path file1;
    private Path file2;
    private Path file3;
    private Path[] files;
    private LongList[] dataLists;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        // crate a pile of data in RAM
        final long sizeLongs = 1_000_000_000;
        final long sizeLongs3 = 1_000_000;
        Random random = new Random(1234);
        data1 = memoryType.equals("heap") ? new LongListHeap() : new LongListOffHeap();
        data2 = memoryType.equals("heap") ? new LongListHeap() : new LongListOffHeap();
        data3 = memoryType.equals("heap") ? new LongListHeap() : new LongListOffHeap();
        for (long i = 0; i < sizeLongs; i++) {
            data1.put(i,random.nextLong());
            data2.put(i,random.nextLong());
            if (i<sizeLongs3)data3.put(i,random.nextLong());
        }
    }

    @Setup(Level.Invocation)
    public void createFile() throws Exception {
        file1 = Path.of("dumpFile_1_"+System.nanoTime()+"_"+Math.random()+"_.dump");
        file2 = Path.of("dumpFile_2_"+System.nanoTime()+"_"+Math.random()+"_.dump");
        file3 = Path.of("dumpFile_3_"+System.nanoTime()+"_"+Math.random()+"_.dump");
        files = new Path[]{file1,file2,file3};
        dataLists = new LongList[]{data1,data2,data3};
    }

    @Benchmark
    public void writeFile() throws Exception {
        IntStream.range(0,3).parallel().forEach(dataIndex -> {
            try {
                var fc = FileChannel.open(files[dataIndex], StandardOpenOption.CREATE, StandardOpenOption.WRITE);
//                dataLists[dataIndex].writeToFileChannel(fc);
                fc.force(false);
                fc.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @TearDown(Level.Invocation)
    public void deleteFile() throws Exception {
        System.out.printf("file1 = %,2f Gb, file2 = %,2f Gb, file3 = %,2f Gb\n",
                Files.size(file1)/(double)GB,
                Files.size(file2)/(double)GB,
                Files.size(file3)/(double)GB
        );
        Files.delete(file1);
        Files.delete(file2);
        Files.delete(file3);
    }
}

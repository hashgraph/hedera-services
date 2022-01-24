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

@State(Scope.Thread)
@Warmup(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 6, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.SECONDS)
public class IndexDumpBench {

    @Param({"1","2","7"})
    public int dataSizeGb;

    @Param({"heap","offHeap"})
    public String memoryType;

    private LongList data;
    private Path file;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        // crate a pile of data in RAM
        final long GB = 1024*1024*1024;
        final long sizeBytes = dataSizeGb*GB;
        final long sizeLongs = sizeBytes/Long.BYTES;
        System.out.println("sizeBytes = " + sizeBytes);
        System.out.println("sizeLongs = " + sizeLongs);
        Random random = new Random(1234);
        data = memoryType.equals("heap") ? new LongListHeap() : new LongListOffHeap();
        for (long i = 0; i < sizeLongs; i++) {
            data.put(i,random.nextLong());
        }
    }

    @Setup(Level.Invocation)
    public void createFile() throws Exception {
        file = Path.of("dumpFile_"+System.nanoTime()+"_"+Math.random()+"_.dump");
    }

    @Benchmark
    public void writeFile() throws Exception {
        var fc = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
//        data.writeToFileChannel(fc);
        fc.force(false);
        fc.close();
    }

    @TearDown(Level.Invocation)
    public void deleteFile() throws Exception {
        Files.delete(file);
    }
}

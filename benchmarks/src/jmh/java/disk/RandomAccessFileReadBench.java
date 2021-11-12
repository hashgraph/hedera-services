package disk;

import org.openjdk.jmh.annotations.*;

import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("DefaultAnnotationParam")
@State(Scope.Thread)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class RandomAccessFileReadBench {

    @Param({"1","2","4","8","16","32","64","128","256"})
    public int readSizeKb;

    // state
    private RandomAccessFile[] files;
    private byte[] resultBuffer;
    private final Random random = new Random(32146486);

    private int readSizeBytes;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        readSizeBytes = readSizeKb*1024;
        resultBuffer = new byte[readSizeBytes];

        ArrayList<RandomAccessFile> filesList = new ArrayList<>();
        // map 1Tb file
        filesList.add(new RandomAccessFile(Path.of("1TB.bin").toFile(),"r"));
        // map 100Mb files
        for (int i = 1; i <= 15; i++) {
            filesList.add(new RandomAccessFile(Path.of("100MB_"+i+".bin").toFile(),"r"));
        }
        // get fileChannels array
        //noinspection ToArrayCallWithZeroLengthArrayArgument
        files = filesList.toArray(new RandomAccessFile[filesList.size()]);
    }

    @Benchmark
    public void randomRead() throws Exception {
        // pick random file
        RandomAccessFile file = files[random.nextInt(files.length)];
        // pick random offset
        long numOfSlots = (file.length()/(readSizeBytes));
        long offset = readSizeBytes * (long)(random.nextDouble()*(double)numOfSlots);
        // make read
        file.seek(offset);
        file.read(resultBuffer);
    }
}

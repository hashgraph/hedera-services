package disk;

import org.openjdk.jmh.annotations.*;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
public class SeekableFileChannelReadBench {

    @Param({"1","2","4","8","16","32","64","128","256"})
    public int readSizeKb;
    @Param({"heap","direct"})
    public String bufferType;

    // state
    private SeekableByteChannel[] fileChannels;
    private ByteBuffer resultBuffer;
    private final Random random = new Random(32146486);

    private int readSizeBytes;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        readSizeBytes = readSizeKb*1024;
        resultBuffer = bufferType.equals("heap") ? ByteBuffer.allocate(readSizeBytes) : ByteBuffer.allocateDirect(readSizeBytes);

        ArrayList<SeekableByteChannel> fileChannelList = new ArrayList<>();
        // map 1Tb file
        fileChannelList.add(Files.newByteChannel(Path.of("1TB.bin"), StandardOpenOption.READ));
        // map 100Mb files
        for (int i = 1; i <= 15; i++) {
            fileChannelList.add(Files.newByteChannel(Path.of("100MB_"+i+".bin"), StandardOpenOption.READ));
        }
        // get fileChannels array
        //noinspection ToArrayCallWithZeroLengthArrayArgument
        fileChannels = fileChannelList.toArray(new SeekableByteChannel[fileChannelList.size()]);
    }

    @Benchmark
    public void randomRead() throws Exception {
        // pick random file
        SeekableByteChannel fileChannel = fileChannels[random.nextInt(fileChannels.length)];
        // pick random offset
        long numOfSlots = (fileChannel.size()/(readSizeBytes));
        long offset = readSizeBytes * (long)(random.nextDouble()*(double)numOfSlots);
        // make read
        resultBuffer.rewind();
        fileChannel.position(offset);
        fileChannel.read(resultBuffer);
    }
}

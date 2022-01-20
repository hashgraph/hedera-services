package disk;

import org.openjdk.jmh.annotations.*;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"jol", "DuplicatedCode", "DefaultAnnotationParam", "SameParameterValue", "SpellCheckingInspection"})
@State(Scope.Thread)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class RandomMemMapReadBench {

    @Param({"1","2","4","8","16","32","64","128","256"})
    public int readSizeKb;

    // state
    private ByteBuffer[] buffers;
    private byte[] resultBuffer = new byte[256*1024];
    private Random random = new Random(32146486);

    @Setup(Level.Trial)
    public void setup() throws Exception {
        ArrayList<ByteBuffer> bufferList = new ArrayList<>();
        // map 1Tb file
        FileChannel fc = FileChannel.open(Path.of("1TB.bin"), StandardOpenOption.READ);
        long remainingSize = fc.size();
        long offset = 0;
        while(remainingSize > 0) {
            int size = (int)Math.min(Integer.MAX_VALUE,remainingSize);
            bufferList.add(fc.map(FileChannel.MapMode.READ_ONLY, offset, size));
            offset += size;
            remainingSize -= size;
        }
        // map 100Mb files
        for (int i = 1; i <= 15; i++) {
            map100MbFile(bufferList,Path.of("100MB_"+i+".bin"));
        }
        // get buffers array
        //noinspection ToArrayCallWithZeroLengthArrayArgument
        buffers = bufferList.toArray(new ByteBuffer[bufferList.size()]);
    }

    private void map100MbFile(ArrayList<ByteBuffer> bufferList, Path file) throws Exception {
        FileChannel fc = FileChannel.open(file, StandardOpenOption.READ);
        bufferList.add(fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size()));
    }

    @Benchmark
    public void randomRead() throws Exception {
        // pick random buffer
        ByteBuffer buffer = buffers[random.nextInt(buffers.length)];
        // pick random offset
        int readSizeBytes = readSizeKb*1024;
        int numOfSlots = (buffer.limit()/(readSizeBytes));
        int offset = readSizeBytes * random.nextInt(numOfSlots-1);
        // make read
        buffer.position(offset);
        buffer.get(resultBuffer,0,readSizeBytes);
    }
}

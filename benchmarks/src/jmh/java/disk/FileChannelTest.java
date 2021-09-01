package disk;


import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static utils.CommonTestUtils.toLongsString;

@SuppressWarnings("DuplicatedCode")
public class FileChannelTest {
    private static final long numOfWrites = 50_000_000; //50_000_000;
    private static final long numOfReads = 5_000_000; //50_000_000;

    public static void main(String[] args) throws Exception {
        ByteBuffer outputBuffer = ByteBuffer.allocateDirect(4096);
        // fill buffer with numbers staring at 1 billion
        LongBuffer outputLongBuffer = outputBuffer.asLongBuffer();
        long counter = 1_000_000_000;
        while(outputLongBuffer.remaining() >= 8) {
            outputLongBuffer.put(counter);
            counter ++;
        }
        // write file if needed
        Path testFile = Path.of("testFile.dat");
        if (!Files.exists(testFile)) {
            System.out.println("============ WRITING .... =============================");
            FileChannel fileChannel = FileChannel.open(testFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            // fill file with 2k blocks with incremental longs at the beginning of each
            long START = System.currentTimeMillis();
            for (long i = 0; i < numOfWrites; i++) { // about 100Gb of 2k chunks
                outputBuffer.rewind();
                outputBuffer.putLong(0, i);
                i++;
                outputBuffer.putLong(2048, i);
                fileChannel.write(outputBuffer);
            }
            long timeTaken = System.currentTimeMillis()-START;
            System.out.println("timeTaken = " + timeTaken);
            double timeTakenSeconds = (double)timeTaken/1000d;
            System.out.println(numOfWrites+" in timeTakenSeconds = " + timeTakenSeconds);
            double perSecond = (double)numOfWrites / timeTakenSeconds;
            System.out.println("perSecond = " + perSecond);
            double mbPerSecond = (perSecond*4)/1024;
            System.out.println("mbPerSecond = " + mbPerSecond);
            fileChannel.close();
        }
        // now read the file
        if (Files.exists(testFile)) {
            System.out.println("============ READING .... =============================");
            ByteBuffer inputBuffer = ByteBuffer.allocateDirect(4096);
            FileChannel fileChannel = FileChannel.open(testFile, StandardOpenOption.READ);
            // fill file with 2k blocks with incremental longs at the beginning of each
            long START = System.currentTimeMillis();
            for (long i = 0; i < numOfReads; i++) { // about 100Gb of 2k chunks
                long random2kChunkIndex = (long)(Math.random() * numOfWrites);
                long fourKblock = random2kChunkIndex/2;
                boolean lowerChunk = (random2kChunkIndex % 2) == 0;
                long offset = 4096 * fourKblock;
                int chunkOffset = lowerChunk ? 0 : 2048;
                // read 4k block
                inputBuffer.rewind();
                int read = fileChannel.read(inputBuffer,offset);
                // get the index from block
                long readIndex = inputBuffer.getLong(chunkOffset);
                // check readIndex matches
                if (random2kChunkIndex != readIndex) {
                    System.err.println("random2kChunkIndex = "+random2kChunkIndex+" readIndex="+readIndex);
                    System.out.println("FCVirtualMapTestUtils.toLongsString(inputBuffer) = " + toLongsString(inputBuffer));
                    throw new Exception("Oops!");
                }
            }
            long timeTaken = System.currentTimeMillis()-START;
            System.out.println("timeTaken = " + timeTaken);
            double timeTakenSeconds = (double)timeTaken/1000d;
            System.out.println(numOfReads+" in timeTakenSeconds = " + timeTakenSeconds);
            double perSecond = (double)numOfReads / timeTakenSeconds;
            System.out.println("perSecond = " + perSecond);
            double mbPerSecond = (perSecond*4)/1024;
            System.out.println("mbPerSecond = " + mbPerSecond);
            fileChannel.close();
        }
    }
}

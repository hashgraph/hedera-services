package jasperdb;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileChannel2GLimitTest {
    static final int GB = 1024*1024*1024;
    public static void main(String[] args) throws IOException {
        ByteBuffer[] bigBuffers = new ByteBuffer[] {
                ByteBuffer.allocateDirect(GB),
                ByteBuffer.allocateDirect(GB),
                ByteBuffer.allocateDirect(GB)
        };
        for(ByteBuffer buf:bigBuffers) buf.clear();
        Path tempFile = Files.createTempFile("FileChannel2GLimitTest",".dat");
        try {
            System.out.println("Starting to write 3Gb of data...");
            FileChannel fc = FileChannel.open(tempFile, StandardOpenOption.CREATE,StandardOpenOption.WRITE);
            long bytesWritten = fc.write(bigBuffers);
            System.out.printf("Bytes written = %,d\n",bytesWritten);
            System.out.printf("Total bytes   = %,d\n",GB*3L);
        } finally {
            Files.delete(tempFile);
        }
    }
    public static void mainWorkaround(String[] args) throws IOException {
        ByteBuffer bigBuffer = ByteBuffer.allocateDirect(GB);
        bigBuffer.clear();
        Path tempFile = Files.createTempFile("FileChannel2GLimitTest",".dat");
        try {
            System.out.println("Starting to write 3Gb of data...");
            FileChannel fc = FileChannel.open(tempFile, StandardOpenOption.CREATE,StandardOpenOption.WRITE);
            long bytesWritten = fc.write(bigBuffer);
            System.out.printf("Bytes written = %,d\n",bytesWritten);
            bigBuffer.clear();
            bytesWritten = fc.write(bigBuffer);
            System.out.printf("Bytes written = %,d\n",bytesWritten);
            bigBuffer.clear();
            bytesWritten = fc.write(bigBuffer);
            System.out.printf("Bytes written = %,d\n",bytesWritten);
            System.out.printf("Total bytes   = %,d\n",GB*3L);
            fc.close();
        } finally {
            Files.delete(tempFile);
        }
    }
}

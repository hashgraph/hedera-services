package com.hedera.services.state.merkle.virtual;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Random;
import java.util.stream.IntStream;

public class SequentialVsRandom {
    public static final Path STORE_PATH = Path.of("store");
    private static final Random RANDOM = new Random(1234);

    public static void main(String[] args) throws Exception{
        deleteAnyOld();
        Files.createDirectories(STORE_PATH);
        final int SLOT_SIZE_BYTES = 1024;
        final int TEST_SIZE_SLOTS = 1024 * (1024+512);
        final int TEST_SIZE_MB = (SLOT_SIZE_BYTES*TEST_SIZE_SLOTS)/(1024*1024);
        // create random data
        byte[][] data = new byte[TEST_SIZE_SLOTS][];
        for (int i = 0; i < TEST_SIZE_SLOTS; i++) {
            byte[] slot = new byte[SLOT_SIZE_BYTES];
            RANDOM.nextBytes(slot);
            data[i] = slot;
        }
        {
            Path seqFile = STORE_PATH.resolve("seq.dat");
            RandomAccessFile randomAccessFile = new RandomAccessFile(seqFile.toFile(), "rw");
            FileChannel fc = randomAccessFile.getChannel();
            ByteBuffer buffer = ByteBuffer.allocateDirect(SLOT_SIZE_BYTES);
            long START1 = System.currentTimeMillis();
            for (int i = 0; i < TEST_SIZE_SLOTS; i++) {
                buffer.rewind();
                buffer.put(data[i]);
                buffer.rewind();
                fc.write(buffer);
            }
            long END1 = System.currentTimeMillis();
            fc.force(false);
            fc.close();
            long END2 = System.currentTimeMillis();
            double time = (END1 - START1) / 1000d;
            double time2 = (END2 - END1) / 1000d;
            double timeTotal = (END2 - START1) / 1000d;
            System.out.println("File Channel Sequential");
            System.out.printf("    time = %,.3f sec at %,.1f Mb/sec -- force time = %,.3f sec\n",time,TEST_SIZE_MB/time,time2);
            System.out.printf("    total time = %,.3f sec at %,.1f Mb/sec\n", timeTotal,TEST_SIZE_MB/timeTotal);
            System.out.printf("    storage size: %,.1f Mb\n",(double)seqFile.toFile().length()/(1024d*1024d));
        }
        {
            Path seqFile = STORE_PATH.resolve("seq3.dat");
            SeekableByteChannel channel = Files.newByteChannel(seqFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            ByteBuffer buffer = ByteBuffer.allocateDirect(SLOT_SIZE_BYTES);
            long START1 = System.currentTimeMillis();
            for (int i = 0; i < TEST_SIZE_SLOTS; i++) {
                buffer.rewind();
                buffer.put(data[i]);
                buffer.rewind();
                while (buffer.remaining() > 0) {
                    int n = channel.write(buffer);
                    if (n <= 0) throw new RuntimeException("no bytes written");
                }
            }
            long END1 = System.currentTimeMillis();
            channel.close();
            long END2 = System.currentTimeMillis();
            double time = (END1 - START1) / 1000d;
            double time2 = (END2 - END1) / 1000d;
            double timeTotal = (END2 - START1) / 1000d;
            System.out.println(" Files.newByteChannel() Sequential");
            System.out.printf("    time = %,.3f sec at %,.1f Mb/sec -- force time = %,.3f sec\n",time,TEST_SIZE_MB/time,time2);
            System.out.printf("    total time = %,.3f sec at %,.1f Mb/sec\n", timeTotal,TEST_SIZE_MB/timeTotal);
            System.out.printf("    storage size: %,.1f Mb\n",(double)seqFile.toFile().length()/(1024d*1024d));
        }
        {
            Path seqFile = STORE_PATH.resolve("seq-random.dat");
            RandomAccessFile randomAccessFile = new RandomAccessFile(seqFile.toFile(), "rw");
            long START1 = System.currentTimeMillis();
            for (int i = 0; i < TEST_SIZE_SLOTS; i++) {
                randomAccessFile.write(data[i]);
            }
            long END1 = System.currentTimeMillis();
            randomAccessFile.close();
            long END2 = System.currentTimeMillis();
            double time = (END1 - START1) / 1000d;
            double time2 = (END2 - END1) / 1000d;
            double timeTotal = (END2 - START1) / 1000d;
            System.out.println("Random Access File Sequential");
            System.out.printf("    time = %,.3f sec at %,.1f Mb/sec -- force time = %,.3f sec\n",time,TEST_SIZE_MB/time,time2);
            System.out.printf("    total time = %,.3f sec at %,.1f Mb/sec\n", timeTotal,TEST_SIZE_MB/timeTotal);
            System.out.printf("    storage size: %,.1f Mb\n",(double)seqFile.toFile().length()/(1024d*1024d));
        }
        {
            Path seqFile = STORE_PATH.resolve("seq2.dat");
            OutputStream out = Files.newOutputStream(seqFile);
            BufferedOutputStream bout = new BufferedOutputStream(out, 1024*512);
            long START1 = System.currentTimeMillis();
            for (int i = 0; i < TEST_SIZE_SLOTS; i++) {
                bout.write(data[i]);
            }
            long END1 = System.currentTimeMillis();
            bout.flush();
            out.flush();
            bout.close();
            out.close();
            long END2 = System.currentTimeMillis();
            double time = (END1 - START1) / 1000d;
            double time2 = (END2 - END1) / 1000d;
            double timeTotal = (END2 - START1) / 1000d;
            System.out.println("NIO2 Files.newOutputStream() Sequential");
            System.out.printf("    time = %,.3f sec at %,.1f Mb/sec -- force time = %,.3f sec\n",time,TEST_SIZE_MB/time,time2);
            System.out.printf("    total time = %,.3f sec at %,.1f Mb/sec\n", timeTotal,TEST_SIZE_MB/timeTotal);
            System.out.printf("    storage size: %,.1f Mb\n",(double)seqFile.toFile().length()/(1024d*1024d));
        }

        {
            Path randomFile = STORE_PATH.resolve("random.dat");
            RandomAccessFile randomAccessFile = new RandomAccessFile(randomFile.toFile(), "rw");
            // set size for new empty file
            randomAccessFile.setLength((long)SLOT_SIZE_BYTES*(long)TEST_SIZE_SLOTS);
            // get file channel and memory map the file
            FileChannel fileChannel = randomAccessFile.getChannel();
            MappedByteBuffer mappedBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, fileChannel.size());
            long START1 = System.currentTimeMillis();
            for (int i = 0; i < TEST_SIZE_SLOTS; i++) {
                mappedBuffer.put(data[i]);
            }
            long END1 = System.currentTimeMillis();
            mappedBuffer.force();
            fileChannel.close();
            randomAccessFile.close();
            long END2 = System.currentTimeMillis();
            double time = (END1 - START1) / 1000d;
            double time2 = (END2 - END1) / 1000d;
            double timeTotal = (END2 - START1) / 1000d;
            System.out.println("Mem Mapped File Sequential");
            System.out.printf("    time = %,.3f sec at %,.1f Mb/sec -- force time = %,.3f sec\n",time,TEST_SIZE_MB/time,time2);
            System.out.printf("    total time = %,.3f sec at %,.1f Mb/sec\n", timeTotal,TEST_SIZE_MB/timeTotal);
            System.out.printf("    storage size: %,.1f Mb\n",(double)randomFile.toFile().length()/(1024d*1024d));
        }
        {
            Path randomFile = STORE_PATH.resolve("random.dat");
            RandomAccessFile randomAccessFile = new RandomAccessFile(randomFile.toFile(), "rw");
            // set size for new empty file
            randomAccessFile.setLength(SLOT_SIZE_BYTES*TEST_SIZE_SLOTS);
            // get file channel and memory map the file
            FileChannel fileChannel = randomAccessFile.getChannel();
            MappedByteBuffer mappedBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, fileChannel.size());
            // random order ints
            int[] randomIndex = IntStream.range(0,TEST_SIZE_SLOTS).toArray();
            shuffleArray(randomIndex);
            long START1 = System.currentTimeMillis();
            for (int i = 0; i < randomIndex.length; i++) {
                int index = randomIndex[i];
                mappedBuffer.position(index * SLOT_SIZE_BYTES);
                mappedBuffer.put(data[index]);
            }
            long END1 = System.currentTimeMillis();
            mappedBuffer.force();
            fileChannel.close();
            randomAccessFile.close();
            long END2 = System.currentTimeMillis();
            double time = (END1 - START1) / 1000d;
            double time2 = (END2 - END1) / 1000d;
            double timeTotal = (END2 - START1) / 1000d;
            System.out.println("Mem Mapped File Random");
            System.out.printf("    time = %,.3f sec at %,.1f Mb/sec -- force time = %,.3f sec\n",time,TEST_SIZE_MB/time,time2);
            System.out.printf("    total time = %,.3f sec at %,.1f Mb/sec\n", timeTotal,TEST_SIZE_MB/timeTotal);
            System.out.printf("    storage size: %,.1f Mb\n",(double)randomFile.toFile().length()/(1024d*1024d));
        }
    }
    private static void shuffleArray(int[] array){
        int index;
        Random random = new Random();
        for (int i = array.length - 1; i > 0; i--)
        {
            index = random.nextInt(i + 1);
            if (index != i)
            {
                array[index] ^= array[i];
                array[i] ^= array[index];
                array[index] ^= array[i];
            }
        }
    }

    public static void deleteAnyOld() {
        if (Files.exists(STORE_PATH)) {
            try {
                Files.walk(STORE_PATH)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (Exception e) {
                System.err.println("Failed to delete test store directory");
                e.printStackTrace();
            }
            System.out.println("Deleted data files");
        }
    }
}

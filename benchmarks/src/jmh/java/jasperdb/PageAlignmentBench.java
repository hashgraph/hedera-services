package jasperdb;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"DefaultAnnotationParam", "DuplicatedCode"})
//@State(Scope.Thread)
//@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
//@Measurement(iterations = 20, time = 6, timeUnit = TimeUnit.SECONDS)
//@Fork(1)
//@BenchmarkMode(Mode.Throughput)
//@OutputTimeUnit(TimeUnit.SECONDS)
public class PageAlignmentBench {
//    private static final long KB = 1024;
//    private static final long MB = 1024*KB;
//    private static final long GB = 1024*MB;
//    private static final long TB = 1024*GB;
//
//    @Param({"4096","4000"})
//    public int blockSize;
//    @Param({"DataFileReaderSynchronous","DataFileReaderAsynchronous","DataFileReaderThreadLocal"})
//    public String dataFileImpl;
//
//    private final Random random = new Random(32146486);
//    private DataFileCollection dataFileCollection;
//    private int numOfFiles;
//    private int numOfDataItemsPerFile;
//    private long numOfDataItems;
//    private ByteBuffer dataReadBuffer;
//
//    @Setup(Level.Trial)
//    public void setup() throws Exception {
//        dataReadBuffer = ByteBuffer.allocate(blockSize - Long.BYTES - Integer.BYTES);
//        // calculate number of files, number of data items per file
//        numOfFiles = 1;
//        System.out.println("numOfFiles = " + numOfFiles);
//        numOfDataItemsPerFile = (int) ((100 * GB) / blockSize);
//        System.out.println("numOfDataItemsPerFile = " + numOfDataItemsPerFile);
//        numOfDataItems = (long)numOfDataItemsPerFile * (long)numOfFiles;
//        System.out.println("numOfDataItems = " + numOfDataItems);
//        // create 1Tb of data
//        Path dataDir = Path.of("jasperdb_"+blockSize);
//        if (Files.isDirectory(dataDir)) {
//            dataFileCollection = new DataFileCollection(dataDir, "jasperdb", blockSize,
//                    null, dataFileFactory);
//        } else { // new
//            Files.createDirectories(dataDir);
//            //
//            dataFileCollection = new DataFileCollection(dataDir, "jasperdb", blockSize,
//                    null, dataFileFactory);
//            // create some random data to write
//            ByteBuffer dataBuffer = ByteBuffer.allocate(blockSize - Long.BYTES - Integer.BYTES);
//            new Random(123456).nextBytes(dataBuffer.array());
//            // create files
//            long START = System.currentTimeMillis();
//            long count = 0;
//            for (int f = 0; f < numOfFiles; f++) {
//                dataFileCollection.startWriting();
//                for (int i = 0; i < numOfDataItemsPerFile; i++, count++) {
//                    dataBuffer.rewind();
//                    dataFileCollection.storeData(count, dataBuffer);
//                    if (count % 10_000_000 == 0) System.out.printf("count = %,d\n",count);
//                }
//                dataFileCollection.endWriting(0, count);
//            }
//            long timeTaken = System.currentTimeMillis()-START;
//            System.out.println("YAY!! finished writing all data");
//            double timeTakenSeconds = (double)timeTaken/1000d;
//            System.out.printf("%,d in %,.2f seconds\n",count,timeTakenSeconds);
//        }
//    }
//
//    @Benchmark
//    public void randomRead(Blackhole blackHole) throws Exception {
//        // pick random file
//        int fileIndex = 0;
//        long fileIndexShifted = (long)(fileIndex+1) << 32;
//        // pick random offset
//        int blockOffset = random.nextInt(numOfDataItemsPerFile);
//        // data location
//        long dataLocation = fileIndexShifted | blockOffset;
//        // read data
//        dataReadBuffer.clear();
//        blackHole.consume(dataFileCollection.readData(dataLocation,dataReadBuffer, DataFileReader.DataToRead.KEY_VALUE));
//    }

}

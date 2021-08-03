package jasperdb;

import com.hedera.services.state.merkle.v3.files.DataFile;
import com.hedera.services.state.merkle.v3.files.DataFileCollection;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"DefaultAnnotationParam", "DuplicatedCode"})
@State(Scope.Thread)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 20, time = 6, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class SmallVsBigFilesBench {
    private static final long KB = 1024;
    private static final long MB = 1024*KB;
    private static final long GB = 1024*MB;
    private static final int BLOCK_SIZE = 512;

    @Param({"1","128","512"})
    public int fileSizeGb;

    private final Random random = new Random(32146486);
    private DataFileCollection dataFileCollection;
    private int numOfFiles;
    private int numOfDataItemsPerFile;
    private final ByteBuffer dataReadBuffer = ByteBuffer.allocate(BLOCK_SIZE - Long.BYTES - Integer.BYTES);

    @Setup(Level.Trial)
    public void setup() throws Exception {
        // calculate number of files, number of data items per file
        numOfFiles = 512 / fileSizeGb;
        System.out.println("numOfFiles = " + numOfFiles);
        numOfDataItemsPerFile = (int) ((fileSizeGb * GB) / BLOCK_SIZE);
        System.out.println("numOfDataItemsPerFile = " + numOfDataItemsPerFile);
        long numOfDataItems = (long) numOfDataItemsPerFile * (long) numOfFiles;
        System.out.println("numOfDataItems = " + numOfDataItems);
        // create 1Tb of data
        Path dataDir = Path.of("jasperdb_"+fileSizeGb);
        if (Files.isDirectory(dataDir)) {
            dataFileCollection = new DataFileCollection(dataDir, "jasperdb", BLOCK_SIZE);
        } else { // new
            Files.createDirectories(dataDir);
            //
            dataFileCollection = new DataFileCollection(dataDir, "jasperdb", BLOCK_SIZE);
            // create some random data to write
            ByteBuffer dataBuffer = ByteBuffer.allocate(BLOCK_SIZE - Long.BYTES - Integer.BYTES);
            new Random(123456).nextBytes(dataBuffer.array());
            // create files
            long START = System.currentTimeMillis();
            long count = 0;
            for (int f = 0; f < numOfFiles; f++) {
                dataFileCollection.startWriting();
                for (int i = 0; i < numOfDataItemsPerFile; i++, count++) {
                    dataBuffer.rewind();
                    dataFileCollection.storeData(count, dataBuffer);
                    if (count % 10_000_000 == 0) System.out.printf("count = %,d\n",count);
                }
                dataFileCollection.endWriting(0, count);
            }
            long timeTaken = System.currentTimeMillis()-START;
            System.out.println("YAY!! finished writing all data");
            double timeTakenSeconds = (double)timeTaken/1000d;
            System.out.printf("%,d in %,.2f seconds\n",count,timeTakenSeconds);
        }
    }

    @Benchmark
    public void randomRead(Blackhole blackHole) throws Exception {
        // pick random file
        int fileIndex = random.nextInt(numOfFiles);
        long fileIndexShifted = (long)(fileIndex+1) << 32;
        // pick random offset
        int blockOffset = random.nextInt(numOfDataItemsPerFile);
        // data location
        long dataLocation = fileIndexShifted | blockOffset;
        // read data
        dataReadBuffer.clear();
        blackHole.consume(dataFileCollection.readData(dataLocation,dataReadBuffer, DataFile.DataToRead.KEY_VALUE));
    }

}

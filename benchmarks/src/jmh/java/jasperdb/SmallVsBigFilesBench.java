package jasperdb;

import com.hedera.services.state.merkle.v3.files.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

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

    @Param({"1","128","512"})
    public int fileSizeGb;
    @Param({"64","128","256","512"})
    public int dataValueSize;
    @Param({"5","20","40"})
    public int readThreads;
    @Param({"DataFileReaderSynchronous","DataFileReaderAsynchronous","DataFileReaderThreadLocal"})
    public String dataFileImpl;

    private final Random random = new Random(32146486);
    private DataFileCollection dataFileCollection;
    private int numOfFiles;
    private int numOfDataItemsPerFile;
    private final ThreadLocal<ByteBuffer> dataReadBuffer = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(dataValueSize - Long.BYTES - Integer.BYTES));
    private long[] randomDataLocations;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        // calculate number of files, number of data items per file
        numOfFiles = 512 / fileSizeGb;
        System.out.println("numOfFiles = " + numOfFiles);
        numOfDataItemsPerFile = (int) ((fileSizeGb * GB) / dataValueSize);
        System.out.println("numOfDataItemsPerFile = " + numOfDataItemsPerFile);
        long numOfDataItems = (long) numOfDataItemsPerFile * (long) numOfFiles;
        System.out.println("numOfDataItems = " + numOfDataItems);
        // create data file factory
        DataFileReaderFactory dataFileFactory = new DataFileReaderFactory() {
            @Override
            public DataFileReader newDataFileReader(Path path) throws IOException {
                return switch(dataFileImpl) {
                    case "DataFileReaderAsynchronous" -> new DataFileReaderAsynchronous(path);
                    case "DataFileReaderThreadLocal" -> new DataFileReaderThreadLocal(path);
                    default -> new DataFileReaderSynchronous(path);
                };
            }

            @Override
            public DataFileReader newDataFileReader(Path path, DataFileMetadata dataFileMetadata) throws IOException {
                return switch(dataFileImpl) {
                    case "DataFileReaderAsynchronous" -> new DataFileReaderAsynchronous(path,dataFileMetadata);
                    case "DataFileReaderThreadLocal" -> new DataFileReaderThreadLocal(path,dataFileMetadata);
                    default -> new DataFileReaderSynchronous(path,dataFileMetadata);
                };
            }
        };
        // create 1Tb of data
        Path dataDir = Path.of("jasperdb_fs"+fileSizeGb+"_bs"+ dataValueSize);
        if (Files.isDirectory(dataDir)) {
            dataFileCollection = new DataFileCollection(dataDir, "jasperdb", dataValueSize, dataFileFactory);
        } else { // new
            Files.createDirectories(dataDir);
            //
            dataFileCollection = new DataFileCollection(dataDir, "jasperdb", dataValueSize, dataFileFactory);
            // create some random data to write
            ByteBuffer dataBuffer = ByteBuffer.allocate(dataValueSize);
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

                System.out.println("writes done");
                long START2 = System.currentTimeMillis();
                dataFileCollection.endWriting(0, count);

                double timeTakenSeconds = (double)(System.currentTimeMillis()-START2)/1000d;
                System.out.printf("endWriting %,.2f seconds\n",timeTakenSeconds);
            }
            long timeTaken = System.currentTimeMillis()-START;
            System.out.println("YAY!! finished writing all data");
            double timeTakenSeconds = (double)timeTaken/1000d;
            System.out.printf("%,d in %,.2f seconds\n",count,timeTakenSeconds);
        }
    }

    @Setup(Level.Invocation)
    public void random(){
        randomDataLocations = IntStream.range(0,readThreads*200)
                .parallel()
                .mapToLong(i -> {
                    // pick random file
                    int fileIndex = random.nextInt(numOfFiles);
                    // pick random offset
                    long blockOffset = random.nextInt(numOfDataItemsPerFile);
                    // data location
                    return DataFileCommon.dataLocation(fileIndex,blockOffset*dataValueSize);
                }).toArray();
    }

    @Benchmark
    public void randomRead(Blackhole blackHole) throws Exception {
        ByteBuffer dataReadBuffer = this.dataReadBuffer.get();
        // read data
        dataReadBuffer.clear();
        blackHole.consume(dataFileCollection.readData(randomDataLocations[0],dataReadBuffer, DataFileReader.DataToRead.KEY_VALUE));
    }

    @Benchmark
    public void randomReadThreaded(Blackhole blackHole) {
        IntStream.range(0,readThreads).parallel().forEach(thread -> {
            ByteBuffer dataReadBuffer = this.dataReadBuffer.get();
            for (int i = 0; i < 200; i++) {
                // data location
                long dataLocation = randomDataLocations[i*thread];
                // read data
                dataReadBuffer.clear();
                try {
                    blackHole.consume(dataFileCollection.readData(dataLocation,dataReadBuffer, DataFileReader.DataToRead.KEY_VALUE));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

}

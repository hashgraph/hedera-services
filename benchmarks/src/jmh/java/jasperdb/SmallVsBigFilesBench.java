package jasperdb;

import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.DataFileCollection;
import com.swirlds.jasperdb.files.DataFileCommon;
import com.swirlds.jasperdb.files.DataItemHeader;
import com.swirlds.jasperdb.files.DataItemSerializer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

    private final Random random = new Random(32146486);
    private DataFileCollection<DataBlob> dataFileCollection;
    private int numOfFiles;
    private int numOfDataItemsPerFile;
    private final ThreadLocal<ByteBuffer> dataReadBuffer = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(dataValueSize - Long.BYTES - Integer.BYTES));
    private long[] randomDataLocations;
    private Method accessibleReadDataItem;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        accessibleReadDataItem = DataFileCollection.class.getDeclaredMethod("readDataItem", long.class);
        accessibleReadDataItem.setAccessible(true);

        // calculate number of files, number of data items per file
        numOfFiles = 512 / fileSizeGb;
        System.out.println("numOfFiles = " + numOfFiles);
        numOfDataItemsPerFile = (int) ((fileSizeGb * GB) / dataValueSize);
        System.out.println("numOfDataItemsPerFile = " + numOfDataItemsPerFile);
        long numOfDataItems = (long) numOfDataItemsPerFile * (long) numOfFiles;
        System.out.println("numOfDataItems = " + numOfDataItems);
        // create 1Tb of data
        Path dataDir = Path.of("jasperdb_fs"+fileSizeGb+"_bs"+ dataValueSize);
        if (Files.isDirectory(dataDir)) {
            dataFileCollection = new DataFileCollection<>(dataDir, "jasperdb",
                    new ByteArrayDataItemSerializer(),
                    null);
        } else { // new
            Files.createDirectories(dataDir);
            //
            dataFileCollection = new DataFileCollection<>(dataDir, "jasperdb",
                    new ByteArrayDataItemSerializer(),
                    null);
            // create files
            long START = System.currentTimeMillis();
            int count = 0;
            for (int f = 0; f < numOfFiles; f++) {
                dataFileCollection.startWriting();
                for (int i = 0; i < numOfDataItemsPerFile; i++, count++) {
                    // create some random data to write
                    byte[] bytes = new byte[dataValueSize];
                    new Random(123456).nextBytes(bytes);
                    dataFileCollection.storeDataItem(new DataBlob(count, bytes));
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
        final var datum = accessibleReadDataItem.invoke(dataFileCollection, randomDataLocations[0]);
        blackHole.consume(datum);
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
                    final var datum = accessibleReadDataItem.invoke(dataFileCollection, dataLocation);
                    blackHole.consume(datum);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public static class DataBlob {
        public final int num;
        public final byte[] data;

        public DataBlob(int num, byte[] data) {
            this.num = num;
            this.data = data;
        }
    }

    private class ByteArrayDataItemSerializer implements DataItemSerializer<DataBlob> {

        @Override
        public int getHeaderSize() {
            return 4;
        }

        @Override
        public DataItemHeader deserializeHeader(ByteBuffer byteBuffer) {
            return new DataItemHeader(dataValueSize,byteBuffer.getInt());
        }

        @Override
        public int getSerializedSize() {
            return 4+dataValueSize;
        }

        @Override
        public long getCurrentDataVersion() {
            return 1;
        }

        @Override
        public DataBlob deserialize(ByteBuffer byteBuffer, long l) {
            int num = byteBuffer.getInt();
            byte[] data = new byte[dataValueSize];
            byteBuffer.get(data);
            return new DataBlob(num, data);
        }

        @Override
        public int serialize(DataBlob dataBlob, SerializableDataOutputStream serializableDataOutputStream) throws IOException {
            serializableDataOutputStream.writeInt(dataBlob.num);
            serializableDataOutputStream.write(dataBlob.data);
            return 4+dataValueSize;
        }
    }
}

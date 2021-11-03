package contract;

import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractKeySerializer;
import com.hedera.services.state.virtual.ContractKeySupplier;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.ContractValueSupplier;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.jasperdb.VirtualDataSourceJasperDB;
import com.swirlds.jasperdb.VirtualInternalRecordSerializer;
import com.swirlds.jasperdb.VirtualLeafRecordSerializer;
import com.swirlds.jasperdb.files.DataFileCommon;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import lmdb.VFCDataSourceLmdb;
import org.eclipse.collections.impl.list.mutable.primitive.LongArrayList;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static utils.CommonTestUtils.printDirectorySize;

@SuppressWarnings({"jol", "DuplicatedCode", "DefaultAnnotationParam", "SameParameterValue", "SpellCheckingInspection"})
@State(Scope.Thread)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 120, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class VirtualDataSourceNewAPIBench {
    private static final long MB = 1024*1024;
    private static final int WRITE_BATCH_SIZE = 100_000;

    @Param({"10000"})
    public long numEntities;
    @Param({"memmap","lmdb","lmdb2","lmdb-ns","lmdb2-ns","rocksdb","lmdb-ram","v3"})
    public String impl;

    // state
    public Path storePath;
    public VirtualDataSource<ContractKey,ContractValue> dataSource;
    public Random random = new Random(1234);
    public long nextPath = 0;
    private ContractKey key1 = null;
    private long randomLeafIndex1;
    private long randomNodeIndex1;
    private final LongArrayList random10kLeafPaths = new LongArrayList(10_000);

    @Setup(Level.Trial)
    public void setup() {
        System.out.println("------- Setup -----------------------------");
        storePath = Path.of("store-"+impl);
        try {
            final boolean storeExists = Files.exists(storePath);
            // get slot index suppliers
            switch (impl) {
                case "lmdb":
                    dataSource = new VFCDataSourceLmdb<>(
                        1+8+32, ContractKey::new, // max seralized size
                        ContractValue.SERIALIZED_SIZE, ContractValue::new,
                        storePath);
                    break;
//                case "rocksdb" ->
//                    new VFCDataSourceRocksDb<>(
//                            ContractKey.SERIALIZED_SIZE, ContractKey::new,
//                            ContractValue.SERIALIZED_SIZE, ContractValue::new,
//                            storePath);
                case "jasperdb":
                    VirtualLeafRecordSerializer<ContractKey,ContractValue> virtualLeafRecordSerializer =
                            new VirtualLeafRecordSerializer<>(
                                    (short) 1, DigestType.SHA_384,
                                    (short) 1, DataFileCommon.VARIABLE_DATA_SIZE, new ContractKeySupplier(),
                                    (short) 1,ContractValue.SERIALIZED_SIZE, new ContractValueSupplier(),
                                    true);
                    JasperDbBuilder<ContractKey, ContractValue> dbBuilder = new JasperDbBuilder<>();
                    dbBuilder
                            .virtualLeafRecordSerializer(virtualLeafRecordSerializer)
                            .virtualInternalRecordSerializer(new VirtualInternalRecordSerializer())
                            .keySerializer(new ContractKeySerializer())
                            .storageDir(storePath)
                            .maxNumOfKeys(numEntities+10_000_000)
                            .preferDiskBasedIndexes(false)
                            .internalHashesRamToDiskThreshold(Long.MAX_VALUE)
                            .mergingEnabled(true);
                    dataSource = dbBuilder.build("jdb", "4ApiBench");
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + impl);
            };
            // create data
            if (!storeExists) {
                System.out.println("================================================================================");
                System.out.println("Creating data ...");
                // create internal nodes and leaves
                long iHaveWritten = 0;
                while (iHaveWritten < numEntities) {
                    final long start = System.currentTimeMillis();
                    final long batchSize = Math.min(WRITE_BATCH_SIZE, numEntities-iHaveWritten);
                    dataSource.saveRecords(
                            numEntities,numEntities*2,
                            LongStream.range(iHaveWritten,iHaveWritten+batchSize).mapToObj(i -> new VirtualInternalRecord(i,hash((int)i))),
                            LongStream.range(iHaveWritten,iHaveWritten+batchSize).mapToObj(i -> new VirtualLeafRecord<>(
                                    i+numEntities,hash((int)i),new ContractKey(i, i), new ContractValue(i) )
                            ),
                            Stream.empty()
                    );
                    iHaveWritten += batchSize;
                    printUpdate(start, batchSize, ContractValue.SERIALIZED_SIZE, "Created " + iHaveWritten + " Nodes");
                }
                System.out.println("================================================================================");
                // set nextPath
                nextPath = numEntities;
                // let merge catch up
                try {
                    System.out.println("Waiting for merge");
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println("Loaded existing data");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void printUpdate(long START, long count, int size, String msg) {
        long took = System.currentTimeMillis() - START;
        double timeSeconds = (double)took/1000d;
        double perSecond = (double)count / timeSeconds;
        double mbPerSec = (perSecond*size)/MB;
        System.out.printf("%s : [%,d] writes at %,.0f per/sec %,.1f Mb/sec, took %,.2f seconds\n",msg, count, perSecond,mbPerSec, timeSeconds);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        try {
            // v3 data sorce needs a transaction
            dataSource.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        printDirectorySize(storePath);
    }

    @Setup(Level.Invocation)
    public void randomIndex(){
        randomNodeIndex1 = (long)(random.nextDouble()*numEntities);
        randomLeafIndex1 = numEntities + randomNodeIndex1;
        key1 = new ContractKey(randomNodeIndex1,randomNodeIndex1);
        random10kLeafPaths.clear();
        for (int i = 0; i < 10_000; i++) {
            random10kLeafPaths.add(numEntities + ((long)(random.nextDouble()*numEntities)));
        }
    }

    /**
     * Updates the first 10k internal node hashes to new values
     */
    @Benchmark
    public void w0_updateFirst10kInternalHashes() throws Exception {
        dataSource.saveRecords(
                numEntities,numEntities*2,
                LongStream.range(0,Math.min(10_000,numEntities)).mapToObj(i -> new VirtualInternalRecord(i,hash((int)i))),
                Stream.empty(),
                Stream.empty()
        );
    }

    /**
     * Updates the first 10k leaves with new random values
     */
    @Benchmark
    public void w1_updateFirst10kLeafValues() throws Exception {
        dataSource.saveRecords(
                numEntities,numEntities*2,
                Stream.empty(),
                LongStream.range(0,Math.min(10_000,numEntities)).mapToObj(i -> new VirtualLeafRecord<>(
                        i+numEntities,hash((int)i),new ContractKey(i, i), new ContractValue(randomNodeIndex1))),
                Stream.empty()
        );
        // add a small delay between iterations for merging to get a chance on write heavy benchmarks
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
    }

    /**
     * Updates the randomly selected 10k leaves with new random values
     */
    @Benchmark
    public void w1_updateRandom10kLeafValues() throws Exception {
        List<VirtualLeafRecord<ContractKey,ContractValue>> changes = new ArrayList<>(10_000);
        for (int i = 0; i < 10_000; i++) {
            long path = random10kLeafPaths.get(i);
            changes.add(new VirtualLeafRecord<>(
                    path,hash((int)path),new ContractKey(path, path), new ContractValue(i) ));
        }
        dataSource.saveRecords(
                numEntities,numEntities*2,
                Stream.empty(),
                changes.stream(),
                Stream.empty()
        );
    }

    /**
     * Updates the first 10k internal node hashes to new values
     */
    @Benchmark
    public void w2_add10kInternalHashes() throws Exception {
        dataSource.saveRecords(
                numEntities,numEntities*2,
                LongStream.range(nextPath,nextPath+10_000).mapToObj(i -> new VirtualInternalRecord(i,hash((int)i))),
                Stream.empty(),
                Stream.empty()
        );
        nextPath += 10_000;
    }

    /**
     * Updates the first 10k leaves with new random values
     */
    @Benchmark
    public void w3_add10kLeafValues() throws Exception {
        dataSource.saveRecords(
                numEntities,numEntities*2,
                Stream.empty(),
                LongStream.range(nextPath,nextPath+10_000).mapToObj(i -> new VirtualLeafRecord<>(
                        i+numEntities,hash((int)i),new ContractKey(i, i), new ContractValue(i) )
                ),
                Stream.empty()
        );
        nextPath += 10_000;
    }

    @Benchmark
    public void r_loadloadLeafRecordByPath() throws Exception {
        dataSource.loadLeafRecord(randomLeafIndex1);
    }

    @Benchmark
    public void r_loadLeafRecordByKey() throws Exception {
        dataSource.loadLeafRecord(key1);
    }

    @Benchmark
    public void r_loadInternalRecord() throws Exception {
        dataSource.loadInternalRecord(randomNodeIndex1);
    }

    @Benchmark
    public void r_loadLeafHash() throws Exception {
        dataSource.loadLeafHash(randomLeafIndex1);
    }

    /**
     * Creates a hash containing a int repeated 6 times as longs
     *
     * @return byte array of 6 longs
     */
    public static Hash hash(int value) {
        byte b0 = (byte)(value >>> 24);
        byte b1 = (byte)(value >>> 16);
        byte b2 = (byte)(value >>> 8);
        byte b3 = (byte)value;
        return new TestHash(new byte[] {
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3
        });
    }

    public static final class TestHash extends Hash {
        public TestHash(byte[] bytes) {
            super(bytes, DigestType.SHA_384, true, false);
        }
    }
}

package virtual;

import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractKeySerializer;
import com.hedera.services.state.virtual.ContractKeySupplier;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.ContractValueSupplier;
import com.swirlds.common.Units;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.jasperdb.VirtualLeafRecordSerializer;
import com.swirlds.jasperdb.files.DataFileCommon;
import com.swirlds.virtualmap.VirtualMap;
import disruptor.Transaction;
import disruptor.TransactionProcessor;
import disruptor.TransactionPublisher;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import virtual.VFCMapBenchBase.DataSourceType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static virtual.VFCMapBenchBase.createMap;
import static virtual.VFCMapBenchBase.getDataSourcePath;
import static virtual.VFCMapBenchBase.printDataStoreSize;

/**
 */
@SuppressWarnings({"jol", "BusyWait"})
@State(Scope.Thread)
@Measurement(iterations = 60, time = 1, timeUnit = TimeUnit.MINUTES)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ContractBench {
    /** guess at average ContractKey size as it is variable length */
    private static final int ESTIMATED_CONTRACT_KEY_SERIALIZED_SIZE = 10;
    private static final int ESTIMATED_KEY_VALUE_SIZE = ESTIMATED_CONTRACT_KEY_SERIALIZED_SIZE + ContractValue.SERIALIZED_SIZE;

    @Param({"5", "15", "25"})
    public int numUpdatesPerOperation;

    @Param({"500"})
    public int targetOpsPerSecond;

    @Param({"100", "1000", "10000", "15000"})
    public int numContracts;

    @Param({"10", "100", "1000"})
    public int kbPerContract;

    @Param({"100", "1000", "10000"})
    public int kbPerBigContract;

    @Param({"1000", "10000", "100000"})
    public int kbPerHugeContract;

    @Param({"0.001", "0.005", "0.01"})
    public double hugePercent;

    @Param({"0.01", "0.05", "0.10"})
    public double bigPercent;

    @Param("true") // TODO Remove and replace with a benchmark that measures additions?
    public boolean preFill;

    @Param("false")
    public boolean preferDiskBasedIndexes;

    @Param({/*"lmdb",*/ "jasperdbIhRam","jasperdbIhDisk","jasperdbIhHalf"})
    public DataSourceType dsType;

    @Param({"4"})
    public int preFetchEventHandlers;

    // This is the map we will be testing!
    private VirtualMap<ContractKey, ContractValue> virtualMap;
    private int[] keyValuePairsPerContract;

    private TransactionProcessor<ContractKey, ContractValue, Data> txProcessor;

    protected static final Random rand = new Random(1234);
    protected Pipeline<ContractKey, ContractValue> pipeline;

    // Need to wrap in accessor since lambdas need a level of indirection, so they can fetch
    // the latest copy of the map after the copy() call.
    VirtualMap<ContractKey, ContractValue> getVirtualMap() {
        return virtualMap;
    }

    private ContractKey asContractKey(long contractIndex, long index) {
        return new ContractKey(contractIndex, index);
    }

    @Setup
    public void prepare() throws Exception {
        pipeline = new Pipeline<>();
        final long estimatedNumKeyValuePairs =
                (long)(numContracts * (1-bigPercent-hugePercent) * ((kbPerContract * 1024L) / ESTIMATED_KEY_VALUE_SIZE)) +
                (long)(numContracts * bigPercent * ((kbPerBigContract * 1024L) / ESTIMATED_KEY_VALUE_SIZE)) +
                (long)(numContracts * hugePercent * ((kbPerHugeContract * 1024L) / ESTIMATED_KEY_VALUE_SIZE));
        System.out.println("estimatedNumKeyValuePairs = " + estimatedNumKeyValuePairs);
        VirtualLeafRecordSerializer<ContractKey,ContractValue> virtualLeafRecordSerializer =
                new VirtualLeafRecordSerializer<>(
                        (short) 1, DigestType.SHA_384,
                        (short) 1, DataFileCommon.VARIABLE_DATA_SIZE, new ContractKeySupplier(),
                        (short) 1,ContractValue.SERIALIZED_SIZE, new ContractValueSupplier(),
                        true);


        Path dataSourcePath = getDataSourcePath(dsType);
        boolean dataSourceDirExisted = Files.exists(dataSourcePath);
        virtualMap = createMap(dsType,
                virtualLeafRecordSerializer,
                new ContractKeySerializer(),
                estimatedNumKeyValuePairs,
                dataSourcePath, preferDiskBasedIndexes);

        txProcessor = new TransactionProcessor<>(
                preFetchEventHandlers,
                (Transaction<Data> tx) -> {   // preFetch logic
                    VirtualMap<ContractKey, ContractValue> map = getVirtualMap();
                    final Data data = tx.getData();
                    data.value1 = map.getForModify(data.key1);
                    data.value2 = map.getForModify(data.key2);
                },
                (Transaction<Data> tx) -> {   // handleTransaction logic
                    final Data data = tx.getData();
                    data.value1.setValue(data.value1.asLong() - data.transferAmount);
                    data.value2.setValue(data.value2.asLong() + data.transferAmount);
                }
        );

        // We generate a different number of key/value pairs depending on whether it is
        // a huge contract, big contract, or normal contract
        int numBigContracts = (int)(numContracts*bigPercent);
        System.out.println("numBigContracts = " + numBigContracts);
        int numHugeContracts = (int)(numContracts*hugePercent);
        System.out.println("numHugeContracts = " + numHugeContracts);
        keyValuePairsPerContract = new int[numContracts];
        for (int i = 0; i < numContracts; i++) {
            final int kb;
            if (i > 0 && (i % 100) == 0 && numHugeContracts > 0) {
                kb = kbPerHugeContract;
                numHugeContracts--;
            } else if (i > 0 && (i % 10) == 0 && numBigContracts > 0) {
                kb = kbPerBigContract;
                numBigContracts--;
            } else {
                kb = kbPerContract;
            }
            final var numKeyValuePairs = (kb * 1024L) / ESTIMATED_KEY_VALUE_SIZE;
            keyValuePairsPerContract[i] = (int) numKeyValuePairs;
        }

        if (!dataSourceDirExisted && preFill) {
            long countOfKeyValuePairs = 0;
            long lastCountOfKeyValuePairs = 0;
            for (int i = 0; i < numContracts; i++) {
                if ((countOfKeyValuePairs-lastCountOfKeyValuePairs) > 100_000) {
                    lastCountOfKeyValuePairs = countOfKeyValuePairs;
                    System.out.printf("Completed: %,d contracts and %,d key/value pairs\n", i, countOfKeyValuePairs);
                    virtualMap = pipeline.endRound(virtualMap);
                }
                if (i>0 && i%10000==0) {
                    System.out.println("=============== GC =======================");
                    // loading is really intense so give GC a chance to catch up
                    System.gc(); Thread.sleep(1000);
                }
                final int numKeyValuePairs = keyValuePairsPerContract[i];

                for (int j=0; j<numKeyValuePairs; j++) {
                    final var key = asContractKey(i, j);
                    final var value = new ContractValue(j);
                    try {
                        virtualMap.put(key, value);
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.err.println(i + ":" + j);
                        throw e;
                    }
                }
                countOfKeyValuePairs += numKeyValuePairs;
            }

            // During setup, we perform the full hashing and release the old copy. This way,
            // during the tests, we don't have an initial slow hash.
            System.out.printf("Completed: %,d contracts and %,d key/value pairs\n",numContracts,countOfKeyValuePairs);
            virtualMap = pipeline.endRound(virtualMap);
        } else {
            System.out.println("NOT PRE_FILLING AS LOADED FROM FILES OR TURNED OFF WITH FLAG!");
        }

        printDataStoreSize();

        // create a snapshot every 15min
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd--HH-mm");
        ScheduledExecutorService snapshotting = Executors.newScheduledThreadPool(1, runnable -> new Thread(runnable, "Snapshot"));
        snapshotting.scheduleWithFixedDelay(() -> {
            final Path snapshotDir = Path.of("jasperdb_snapshot_"+df.format(new Date()));
            System.out.println("************ STARTING SNAPSHOT ["+snapshotDir.toAbsolutePath()+"] ***********");
            long START = System.currentTimeMillis();
            try {
                virtualMap.getDataSource().snapshot(snapshotDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
            double tookSeconds = (System.currentTimeMillis() - START) * Units.MILLISECONDS_TO_SECONDS;
            System.out.printf("************ SNAPSHOT FINISHED took %,3f seconds [%s] ***********\n", tookSeconds, snapshotDir.toAbsolutePath());
        },0,5,TimeUnit.MINUTES);

    }

    @TearDown
    public void destroy() {
        printDataStoreSize();
        try {
            virtualMap.getDataSource().close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Benchmarks update operations of an existing tree.
     */
    @Benchmark
    public void update() throws Exception {
        TransactionPublisher<Data> publisher = txProcessor.getPublisher();

        // Start modifying the new fast copy
        final var numIterations = targetOpsPerSecond * numUpdatesPerOperation;
        for (int j=0; j<numIterations; j++) {
            // Read the two accounts involved in the token transfer
            // Debit and Credit them for hbar balances
            final var keyIndex = rand.nextInt(numContracts);
            final var kvPairCount = keyValuePairsPerContract[keyIndex];
            final var kvIndex = rand.nextInt(kvPairCount);
            final var key1 = asContractKey(keyIndex, kvIndex);
            final var keyIndex2 = rand.nextInt(numContracts);
            final var kvPairCount2 = keyValuePairsPerContract[keyIndex2];
            final var kvIndex2 = rand.nextInt(kvPairCount2);
            final var key2 = asContractKey(keyIndex2, kvIndex2);
            // transfer a random amount up to 10k between accounts
            publisher.publish(new Data(key1,key2, rand.nextInt(10_000)));
        }
//        // Read the two accounts involved in the token transfer
//        // Debit and Credit them for hbar balances
//        final var keyIndex = rand.nextInt(numContracts);
//        final var kvPairCount = keyValuePairsPerContract[keyIndex];
//        final var kvIndex = rand.nextInt(kvPairCount);
//        final var key = asContractKey(keyIndex, kvIndex);
//        final var value = virtualMap.getForModify(key);
//        value.setValue(asContractValue(rand.nextInt(kvPairCount)));

        // In EventFlow, copy() is called before noMoreTransactions() but since the disruptor
        // cycle is async, we need to be sure we're done with the transactions before moving
        // on to the copy().
        //
        publisher.end();
        virtualMap = pipeline.endRound(virtualMap);
    }

    public static class Data {
        ContractKey key1;
        ContractValue value1;
        ContractKey key2;
        ContractValue value2;
        int transferAmount;

        public Data(ContractKey key1, ContractKey key2, int transferAmount) {
            this.key1 = key1;
            this.key2 = key2;
            this.transferAmount = transferAmount;
        }
    }
}

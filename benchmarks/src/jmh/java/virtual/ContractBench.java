package virtual;

import com.hedera.services.state.jasperdb.files.DataFileCommon;
import com.hedera.services.state.merkle.virtual.ContractKey;
import com.hedera.services.state.merkle.virtual.ContractValue;
import com.swirlds.virtualmap.VirtualMap;
import disruptor.Transaction;
import disruptor.TransactionProcessor;
import disruptor.TransactionPublisher;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 */
@SuppressWarnings({"jol", "BusyWait"})
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ContractBench extends VFCMapBenchBase<ContractKey, ContractValue> {
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

    @Param({"lmdb", "jasperdbIhRam","jasperdbIhDisk","jasperdbIhHalf"})
    public DataSourceType dsType;

    @Param({"4"})
    public int preFetchEventHandlers;

    // This is the map we will be testing!
    private VirtualMap<ContractKey, ContractValue> virtualMap;
    private int[] keyValuePairsPerContract;

    private TransactionProcessor<ContractKey, ContractValue, Data> txProcessor;

    // Need to wrap in accessor since lambdas need a level of indirection, so they can fetch
    // the latest copy of the map after the copy() call.
    VirtualMap<ContractKey, ContractValue> getVirtualMap() {
        return virtualMap;
    }

    @Setup
    public void prepare() throws Exception {
        final long estimatedNumKeyValuePairs =
                (long)(numContracts * (1-bigPercent-hugePercent) * ((kbPerContract * 1024L) / ESTIMATED_KEY_VALUE_SIZE)) +
                (long)(numContracts * bigPercent * ((kbPerBigContract * 1024L) / ESTIMATED_KEY_VALUE_SIZE)) +
                (long)(numContracts * hugePercent * ((kbPerHugeContract * 1024L) / ESTIMATED_KEY_VALUE_SIZE));
        System.out.println("estimatedNumKeyValuePairs = " + estimatedNumKeyValuePairs);
        virtualMap = createMap(dsType,
                DataFileCommon.VARIABLE_DATA_SIZE,
                ContractKey.ESTIMATED_AVERAGE_SIZE,
                ContractKey.MAX_SIZE,
                ContractKey::new,
                ContractKey::readKeySize,
                ContractValue.SERIALIZED_SIZE,
                ContractValue::new,
                estimatedNumKeyValuePairs);

        txProcessor = new TransactionProcessor<>(
                preFetchEventHandlers,
                (Transaction<Data> tx) -> {   // preFetch logic
                    VirtualMap<ContractKey, ContractValue> map = getVirtualMap();

                    final Data data = tx.getData();
                    data.setValue(map.getForModify(data.getKey()));
                },
                (Transaction<Data> tx) -> {   // handleTransaction logic
                    final Data data = tx.getData();
                    final ContractValue value = data.getValue();
                }
        );

        if (preFill) {
            keyValuePairsPerContract = new int[numContracts];
            long countOfKeyValuePairs = 0;
            long lastCountOfKeyValuePairs = 0;
            int numBigContracts = (int)(numContracts*bigPercent);
            System.out.println("numBigContracts = " + numBigContracts);
            int numHugeContracts = (int)(numContracts*hugePercent);
            System.out.println("numHugeContracts = " + numHugeContracts);
            for (int i = 0; i < numContracts; i++) {
                if ((countOfKeyValuePairs-lastCountOfKeyValuePairs) > 100_000) {
                    lastCountOfKeyValuePairs = countOfKeyValuePairs;
                    System.out.printf("Completed: %,d contracts and %,d key/value pairs\n", i, countOfKeyValuePairs);
                    virtualMap = pipeline.endRound(virtualMap);
                }
                if (i>0 && i%50==0) {
                    // loading is really intense so give GC a chance to catch up
                    System.gc(); Thread.sleep(2000);
                }

                // We generate a different number of key/value pairs depending on whether it is
                // a huge contract, big contract, or normal contract
                final int kb;
                if (i>0 && (i%100) == 0 && numHugeContracts > 0) {
                    kb = kbPerHugeContract;
                    numHugeContracts --;
                } else if (i>0 && (i%10) == 0 && numBigContracts > 0) {
                    kb = kbPerBigContract;
                    numBigContracts --;
                } else {
                    kb = kbPerContract;
                }
                final var numKeyValuePairs = (kb * 1024L) / ESTIMATED_KEY_VALUE_SIZE;
                countOfKeyValuePairs += numKeyValuePairs;
                keyValuePairsPerContract[i] = (int)numKeyValuePairs;

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
            }

            // During setup, we perform the full hashing and release the old copy. This way,
            // during the tests, we don't have an initial slow hash.
            System.out.printf("Completed: %,d contracts and %,d key/value pairs\n",numContracts,countOfKeyValuePairs);
            virtualMap = pipeline.endRound(virtualMap);
        }

        printDataStoreSize();
    }

    @TearDown
    public void destroy() {
        printDataStoreSize();
        /*store.close();*/
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
            final var key = asContractKey(keyIndex, kvIndex);

            publisher.publish(new Data(key, rand.nextInt(kvPairCount)));
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
        ContractKey key;
        ContractValue value;

        public Data(ContractKey key, int value) {
            this.key = key;
            this.value = new ContractValue(value);
        }

        public Data(ContractKey key, ContractValue value) {
            this.key = key;
            this.value = value;
        }

        public ContractKey getKey() { return this.key; }
        public ContractValue getValue() { return this.value; }

        public void setValue(ContractValue value) { this.value = value; }
    }
}

package virtual;

import com.hedera.services.state.merkle.virtual.ContractKey;
import com.hedera.services.state.merkle.virtual.ContractValue;
import com.swirlds.virtualmap.VirtualMap;
import disruptor.Transaction;
import disruptor.TransactionProcessor;
import disruptor.TransactionPublisher;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 */
@SuppressWarnings("jol")
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ContractBench extends VFCMapBenchBase<ContractKey, ContractValue> {
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

    // Need to wrap in accessor since lambdas need a level of indirection so they can fetch
    // the latest copy of the map after the copy() call.
    VirtualMap<ContractKey, ContractValue> getVirtualMap() {
        return virtualMap;
    }

    @Setup
    public void prepare() throws Exception {
        virtualMap = createMap(dsType,
                ContractKey.SERIALIZED_SIZE, ContractKey::new,
                ContractValue.SERIALIZED_SIZE, ContractValue::new,
                numContracts);

        final var rand = new Random();
        txProcessor = new TransactionProcessor<ContractKey, ContractValue, Data>(
                preFetchEventHandlers,
                (Transaction<Data> tx) -> {   // preFetch logic
                    VirtualMap<ContractKey, ContractValue> map = getVirtualMap();

                    final Data data = tx.getData();
                    data.setValue(map.getForModify(data.getKey()));
                },
                (Transaction<Data> tx) -> {   // handleTransaction logic
                    final Data data = tx.getData();
                    final ContractValue value = data.getValue();
                    value.setValue(asContractUint256(data.getUint256()));
                }
        );

        if (preFill) {
            keyValuePairsPerContract = new int[numContracts];
            for (int i = 0; i < numContracts; i++) {
                if (i % 100 == 0 && i > 0) {
                    System.out.println("Completed: " + i);
                    virtualMap = pipeline.endRound(virtualMap);
                }

                // We generate a different number of key/value pairs depending on whether it is
                // a huge contract, big contract, or normal contract
                final var randomNumber = (1.0/(double)rand.nextInt(1000)); // Random number from 0.001 to 1
                final var kb = randomNumber < hugePercent ? kbPerHugeContract : randomNumber < bigPercent ? kbPerBigContract : kbPerContract;
                final var numKeyValuePairs = (kb * 1024) / (ContractKey.SERIALIZED_SIZE + ContractValue.SERIALIZED_SIZE);
                keyValuePairsPerContract[i] = numKeyValuePairs;

                for (int j=0; j<numKeyValuePairs; j++) {
                    final var key = asContractKey(i, j);
                    final var value = new ContractValue(asContractUint256(j));
                    try {
                        virtualMap.put(key, value);
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.err.println(i + ":" + j);
                        throw e;
                    }
                }
            }

            // During setup we perform the full hashing and release the old copy. This way,
            // during the tests, we don't have an initial slow hash.
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
//        value.setValue(asContractUint256(rand.nextInt(kvPairCount)));

        // In EventFlow, copy() is called before noMoreTransactions() but since the disruptor
        // cycle is async, we need to be sure we're done with the transactions before moving
        // on to the copy().
        //
        publisher.end();
        virtualMap = pipeline.endRound(virtualMap);
    }

    public static class Data {
        int uint256;
        ContractKey key;
        ContractValue value;

        public Data(ContractKey key, int uint256) {
            this.key = key;
            this.uint256 = uint256;
        }

        public int getUint256() { return this.uint256; }
        public ContractKey getKey() { return this.key; }
        public ContractValue getValue() { return this.value; }

        public void setValue(ContractValue value) { this.value = value; }
    }
}

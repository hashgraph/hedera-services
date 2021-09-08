package virtual;

import com.hedera.services.state.merkle.virtual.ContractKey;
import com.hedera.services.state.merkle.virtual.ContractUint256;
import com.swirlds.virtualmap.VirtualMap;
import disruptor.Transaction;
import disruptor.TransactionProcessor;
import disruptor.TransactionPublisher;
import org.openjdk.jmh.annotations.*;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 */
@SuppressWarnings("jol")
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ContractBench extends VFCMapBenchBase<ContractKey, ContractUint256> {
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
    private VirtualMap<ContractKey, ContractUint256> virtualMap;
    private int[] keyValuePairsPerContract;

    private TransactionProcessor<ContractKey, ContractUint256, Data> txProcessor;

    // Need to wrap in accessor since lambdas need a level of indirection so they can fetch
    // the latest copy of the map after the copy() call.
    VirtualMap<ContractKey, ContractUint256> getVirtualMap() {
        return virtualMap;
    }

    @Setup
    public void prepare() throws Exception {
        virtualMap = createMap(dsType,
                ContractKey.SERIALIZED_SIZE, ContractKey::new,
                ContractUint256.SERIALIZED_SIZE, ContractUint256::new,
                numContracts);

        final var rand = new Random();
        txProcessor = new TransactionProcessor<ContractKey, ContractUint256, Data>(
                preFetchEventHandlers,
                (Transaction<Data> tx) -> {   // preFetch logic
                    VirtualMap<ContractKey, ContractUint256> map = getVirtualMap();

                    final Data data = tx.getData();
                    data.setValue(map.getForModify(data.getKey()));
                },
                (Transaction<Data> tx) -> {   // handleTransaction logic
                    final Data data = tx.getData();
                    final ContractUint256 value = data.getValue();
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
                final var randomNumber = Math.max(0.001+rand.nextDouble(),1); // Random number from 0.001 to 1
                final var kb = randomNumber < hugePercent ? kbPerHugeContract : randomNumber < bigPercent ? kbPerBigContract : kbPerContract;
                final var numKeyValuePairs = (kb * 1024) / (ContractKey.SERIALIZED_SIZE + ContractUint256.SERIALIZED_SIZE);
                keyValuePairsPerContract[i] = numKeyValuePairs;

                for (int j=0; j<numKeyValuePairs; j++) {
                    final var key = asContractKey(i, j);
                    final var value = new ContractUint256(j);
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
        ContractKey key;
        ContractUint256 value;

        public Data(ContractKey key, int value) {
            this.key = key;
            this.value = new ContractUint256(value);
        }

        public Data(ContractKey key, ContractUint256 value) {
            this.key = key;
            this.value = value;
        }

        public ContractKey getKey() { return this.key; }
        public ContractUint256 getValue() { return this.value; }

        public void setValue(ContractUint256 value) { this.value = value; }
    }
}

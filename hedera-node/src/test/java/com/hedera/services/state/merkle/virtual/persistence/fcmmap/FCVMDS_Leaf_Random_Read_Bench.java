package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import com.hedera.services.state.merkle.MerkleAccountState;
import com.hedera.services.state.merkle.virtual.persistence.FCSlotIndex;
import com.hedera.services.state.merkle.virtual.persistence.FCVirtualMapDataStore;
import com.hedera.services.state.merkle.virtual.persistence.SlotStore;
import com.hedera.services.state.submerkle.EntityId;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.merkle.utility.SerializableLong;
import org.openjdk.jmh.annotations.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCVirtualMapTestUtils.*;

/**
 * Microbenchmark tests for the FCVirtualMapDataStore.
 */
@State(Scope.Thread)
@Warmup(iterations = 1, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 2, time = 15, timeUnit = TimeUnit.SECONDS)
@Fork(1)
//@Warmup(iterations = 1, time = 10, timeUnit = TimeUnit.SECONDS)
//@Measurement(iterations = 1, time = 15, timeUnit = TimeUnit.SECONDS)
//@Fork(1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class FCVMDS_Leaf_Random_Read_Bench {
    public static final Path STORE_PATH = Path.of("store");

    @State(Scope.Benchmark)
    public static class BenchmarkState {

        @Param({
                "com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCSlotIndexUsingFCHashMap"
        })
        private String fcSlotIndexImpl;
        @Param({
                "com.hedera.services.state.merkle.virtual.persistence.fcmmap.MemMapSlotStore"
        })
        private String slotStoreImpl;
        @Param({
                "10000000"
        })
        private int dataSetSize;

        // state
        public FCVirtualMapDataStore<SerializableLong, SerializableAccount, SerializableLong, MerkleAccountState> store;
        public Random random = new Random(1234);

        @Setup(Level.Trial)
        public void setup() {
            System.out.println("FCVirtualMapDataStoreBench.cleanAndCreate");
            try {
                ConstructableRegistry.registerConstructable(new ClassConstructorPair(EntityId.class,EntityId::new));
            } catch (ConstructableRegistryException e) {
                e.printStackTrace();
            }
            try {
                // delete any old store
                deleteDirectoryAndContents(STORE_PATH);
                // create new store dir
                Files.createDirectories(STORE_PATH);
                // get slot index suppliers
                Supplier<FCSlotIndex<SerializableLong>> longSlotIndexProvider = supplerFromClass(fcSlotIndexImpl);
                Supplier<FCSlotIndex<SerializableAccount>> accountIndexProvider = supplerFromClass(fcSlotIndexImpl);
                // get slot store suppler
                Supplier<SlotStore> slotStoreSupplier = supplerFromClass(slotStoreImpl);
                // measure the size of a MerkleAccountState
                int sizeOfMerkleAccountState = measureLengthOfSerializable(createRandomMerkleAccountState(1, random));
                // create and open store
                store = new FCVirtualMapDataStoreImpl<SerializableLong, SerializableAccount, SerializableLong, MerkleAccountState>(STORE_PATH, 1024,
                        8,
                        8 * 3, 8, sizeOfMerkleAccountState,
                        longSlotIndexProvider, longSlotIndexProvider, accountIndexProvider,
                        MerkleAccountState::new, slotStoreSupplier);
                store.open();
                // create some data
                for (int i = 0; i < dataSetSize; i++) {
                    if ((i % (dataSetSize/10)) ==0) System.out.println("Created "+i+" leaves...");
                    store.saveLeaf(
                            new SerializableAccount(i,i,i),
                            new SerializableLong(i), createRandomMerkleAccountState(i, random));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            System.out.println("store.leafCount() = " + store.leafCount());
            store.release();
            printDirectorySize(STORE_PATH);
        }
    }



    @Benchmark
    public void _1_randomLoadLeafByPath(BenchmarkState state) throws Exception {
        int index = state.random.nextInt(state.dataSetSize);
        MerkleAccountState merkleAccountState = state.store.loadLeafByPath(new SerializableLong(index));
//        if (merkleAccountState == null || merkleAccountState.balance() != index) System.err.println("Got wrong value back");
    }

}

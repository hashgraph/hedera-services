package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import com.hedera.services.state.merkle.MerkleAccountState;
import com.hedera.services.state.merkle.virtual.persistence.FCSlotIndex;
import com.hedera.services.state.merkle.virtual.persistence.FCVirtualMapDataStore;
import com.hedera.services.state.merkle.virtual.persistence.SlotStore;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.SerializableLong;
import org.openjdk.jmh.annotations.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCVirtualMapTestUtils.*;

/**
 * Microbenchmark tests for the FCVirtualMapDataStore.
 */
@State(Scope.Thread)
@Warmup(iterations = 0, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 1, time = 10, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
//@Warmup(iterations = 1, time = 10, timeUnit = TimeUnit.SECONDS)
//@Measurement(iterations = 1, time = 15, timeUnit = TimeUnit.SECONDS)
//@Fork(1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class FCVirtualMapDataStoreBench {
    private static final int HASH_DATA_SIZE = DigestType.SHA_384.digestLength() + Integer.BYTES + Integer.BYTES; // int for digest type and int for byte array length
    public static final Path STORE_PATH = Path.of("store");

    @State(Scope.Benchmark)
    public static class EmptyState {

        @Param({
                "com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCSlotIndexUsingFCHashMap"
        })
        private String fcSlotIndexImpl;
        @Param({
                "com.hedera.services.state.merkle.virtual.persistence.fcmmap.MemMapSlotStore"
        })
        private String slotStoreImpl;

        // state
        public FCVirtualMapDataStore<SerializableLong, Hash, SerializableAccount, SerializableLong, MerkleAccountState> store;
        public Random random = new Random(1234);
        public int iteration = 0;

        @Setup(Level.Iteration)
        public void setup() {
            System.out.println("FCVirtualMapDataStoreBench.cleanAndCreate");
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
                store = new FCVirtualMapDataStoreImpl<SerializableLong, Hash, SerializableAccount, SerializableLong, MerkleAccountState>(STORE_PATH, 10,
                        8, HASH_DATA_SIZE,
                        8 * 3, 8, sizeOfMerkleAccountState,
                        longSlotIndexProvider, longSlotIndexProvider, accountIndexProvider,
                        Hash::new, MerkleAccountState::new, slotStoreSupplier);
                store.open();
                // reset iteration counter
                iteration = 0;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @TearDown(Level.Iteration)
        public void tearDown() {
            System.out.println("store.leafCount() = " + store.leafCount());
            store.release();
            printDirectorySize(STORE_PATH);
        }
    }

    @State(Scope.Benchmark)
    public static class FullState extends EmptyState {

        @Setup(Level.Iteration)
        public void setup() {
            super.setup();
            try {
                for (int i = 0; i < 1000; i++) {
                    store.saveLeaf(
                            new SerializableAccount(i,i,i),
                            new SerializableLong(i), createRandomMerkleAccountState(i, random));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    @Benchmark
    public void _0_saveLeaf(EmptyState state) throws Exception {
        state.store.saveLeaf(
                new SerializableAccount(state.iteration,state.iteration,state.iteration),
                new SerializableLong(state.iteration), createRandomMerkleAccountState(state.iteration, state.random));
        state.iteration ++;
    }

    @Benchmark
    public void _1_randomLoadLeafByPath(FullState state) throws Exception {
        System.out.println("state.store.leafCount() = " + state.store.leafCount());
        int index = state.random.nextInt(1000);
        MerkleAccountState merkleAccountState = state.store.loadLeafByPath(new SerializableLong(index));
        System.out.println("merkleAccountState = " + merkleAccountState);
        if (merkleAccountState == null || merkleAccountState.balance() != index) System.err.println("Got wrong value back");
    }


}

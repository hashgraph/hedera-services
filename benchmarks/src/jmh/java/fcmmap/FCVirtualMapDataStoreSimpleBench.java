package fcmmap;

import com.hedera.services.state.merkle.virtual.persistence.FCSlotIndex;
import com.hedera.services.state.merkle.virtual.persistence.FCVirtualMapLeafStore;
import com.hedera.services.state.merkle.virtual.persistence.SlotStore;
import com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCVirtualMapLeafStoreImpl;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.merkle.utility.SerializableLong;
import org.openjdk.jmh.annotations.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static fcmmap.FCVirtualMapTestUtils.*;

/**
 * Microbenchmark tests for the FCVirtualMapDataStore.
 */
@State(Scope.Thread)
@Warmup(iterations = 1, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 1, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(1)
//@Warmup(iterations = 1, time = 10, timeUnit = TimeUnit.SECONDS)
//@Measurement(iterations = 1, time = 15, timeUnit = TimeUnit.SECONDS)
//@Fork(1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class FCVirtualMapDataStoreSimpleBench {
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
        public FCVirtualMapLeafStore<SerializableAccount, SerializableLong, TestLeafData> store;
        public Random random = new Random(1234);
        public int iteration = 0;

        @Setup(Level.Iteration)
        public void setup() {
            System.out.println("FCVirtualMapDataStoreBench.cleanAndCreate");
            try {
                // delete any old store
                FCVirtualMapTestUtils.deleteDirectoryAndContents(STORE_PATH);
                // create new store dir
                Files.createDirectories(STORE_PATH);
                // get slot index suppliers
                Supplier<FCSlotIndex<SerializableLong>> longSlotIndexProvider = FCVirtualMapTestUtils.supplerFromClass(fcSlotIndexImpl);
                Supplier<FCSlotIndex<SerializableAccount>> accountIndexProvider = FCVirtualMapTestUtils.supplerFromClass(fcSlotIndexImpl);
                // get slot store suppler
                Supplier<SlotStore> slotStoreSupplier = FCVirtualMapTestUtils.supplerFromClass(slotStoreImpl);
                // create and open store
                store = new FCVirtualMapLeafStoreImpl<>(STORE_PATH, 10,
                        8 * 3, 8, TestLeafData.SIZE_BYTES,
                        longSlotIndexProvider.get(), accountIndexProvider.get(),
                        SerializableAccount::new, SerializableLong::new, TestLeafData::new, slotStoreSupplier);
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
            FCVirtualMapTestUtils.printDirectorySize(STORE_PATH);
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
                            new SerializableLong(i), new TestLeafData(i));
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
                new SerializableLong(state.iteration), new TestLeafData(state.iteration));
        state.iteration ++;
    }

    @Benchmark
    public void _1_randomLoadLeafByPath(FullState state) throws Exception {
        int index = state.random.nextInt(1000);
        TestLeafData testLeafData = state.store.loadLeafValueByPath(new SerializableLong(index));
        if (testLeafData == null ) System.err.println("Got wrong value back");
    }


}

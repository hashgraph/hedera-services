package contract;

import com.hedera.services.state.merkle.MerkleAccountState;
import com.hedera.services.state.merkle.virtual.ContractKey;
import com.hedera.services.state.merkle.virtual.ContractUint256;
import com.hedera.services.state.merkle.virtual.persistence.FCSlotIndex;
import com.hedera.services.state.merkle.virtual.persistence.FCVirtualMapLeafStore;
import com.hedera.services.state.merkle.virtual.persistence.SlotStore;
import com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCSlotIndexUsingFCHashMap;
import com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCSlotIndexUsingMemMapFile;
import com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCVirtualMapLeafStoreImpl;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.models.Id;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.merkle.utility.SerializableLong;
import fcmmap.FCVirtualMapDataStoreBench;
import fcmmap.FCVirtualMapTestUtils;
import org.openjdk.jmh.annotations.*;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@State(Scope.Thread)
@Warmup(iterations = 4, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 50, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class FCSlotIndexGetBench {
    public static final Path STORE_PATH = Path.of("store");
    public static final Id ID = new Id(1,2,3);


    @Param({
//                "FCSlotIndexUsingFCHashMap",
            "FCSlotIndexUsingMemMapFile"
    })
    private String fcSlotIndexImpl;

    @Param({"1000000"})
    public long numEntities;

    // state
    public FCSlotIndex<ContractKey> slotIndex;
    public Random random = new Random(1234);
    public int iteration = 0;
    private ContractKey key = null;

    @Setup(Level.Trial)
    public void setup() {
        System.out.println("EmptyState.setup");
        try {
            ConstructableRegistry.registerConstructable(new ClassConstructorPair(EntityId.class,EntityId::new));
        } catch (ConstructableRegistryException e) {
            e.printStackTrace();
        }
        try {
            // delete any old store
            FCVirtualMapTestUtils.deleteDirectoryAndContents(STORE_PATH);
            // get slot index suppliers
            switch (fcSlotIndexImpl) {
                case "FCSlotIndexUsingMemMapFile" -> slotIndex = new FCSlotIndexUsingMemMapFile<>(STORE_PATH, "FCSlotIndexBench",
                        1024*1024, 32, ContractKey.SERIALIZED_SIZE, 16, 16);
                case "FCSlotIndexUsingFCHashMap" -> slotIndex = new FCSlotIndexUsingFCHashMap<>();
            }
            // create data
            for (long i = 0; i < numEntities; i++) {
                if (i % 100_000 == 0) System.out.println("created = " + i);
                slotIndex.putSlot(new ContractKey(ID,new ContractUint256(BigInteger.valueOf(i))), i);
            }
            System.out.println("\nslotIndex.keyCount() = " + slotIndex.keyCount());
            // reset iteration counter
            iteration = 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        slotIndex.release();
        FCVirtualMapTestUtils.printDirectorySize(STORE_PATH);
    }

    @Setup(Level.Invocation)
    public void randomIndex(){
        final long index = (long)(random.nextDouble()*numEntities);
        key = new ContractKey(ID,new ContractUint256(BigInteger.valueOf(index)));
    }

    @Benchmark
    public void get() throws Exception {
        slotIndex.getSlot(key);
    }

}

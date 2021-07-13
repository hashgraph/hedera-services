package contract;

import com.hedera.services.state.merkle.virtual.ContractKey;
import com.hedera.services.state.merkle.virtual.ContractUint256;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.models.Id;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import fcmmap.FCVirtualMapTestUtils;
import org.openjdk.jmh.annotations.*;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@Warmup(iterations = 1, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class FCSlotIndexPutBench {
//    public static final Path STORE_PATH = Path.of("store");
//    public static final Id ID = new Id(1,2,3);
//
//
//    @Param({
////                "FCSlotIndexUsingFCHashMap",
//            "FCSlotIndexUsingMemMapFile"
//    })
//    private String fcSlotIndexImpl;
//
//    // state
//    public FCSlotIndex<ContractKey> slotIndex;
//    public Random random = new Random(1234);
//    public int iteration = 0;
//
//    @Setup(Level.Trial)
//    public void setup() {
//        System.out.println("EmptyState.setup");
//        try {
//            ConstructableRegistry.registerConstructable(new ClassConstructorPair(EntityId.class,EntityId::new));
//        } catch (ConstructableRegistryException e) {
//            e.printStackTrace();
//        }
//        try {
//            // delete any old store
//            FCVirtualMapTestUtils.deleteDirectoryAndContents(STORE_PATH);
//            // get slot index suppliers
//            switch (fcSlotIndexImpl) {
//                case "FCSlotIndexUsingMemMapFile" -> slotIndex = new FCSlotIndexUsingMemMapFile<>(STORE_PATH, "FCSlotIndexBench",
//                        1024*1024, 32, ContractKey.SERIALIZED_SIZE, 16, 16,256);
//                case "FCSlotIndexUsingFCHashMap" -> slotIndex = new FCSlotIndexUsingFCHashMap<>();
//            }
//            // reset iteration counter
//            iteration = 0;
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    @TearDown(Level.Trial)
//    public void tearDown() {
//        slotIndex.release();
//        FCVirtualMapTestUtils.printDirectorySize(STORE_PATH);
//    }
//
//
//    @TearDown(Level.Iteration)
//    public void count() {
//        System.out.println("slotIndex.keyCount() = " + slotIndex.keyCount());
//    }
//
//
//    @Benchmark
//    public void put() throws Exception {
//        slotIndex.putSlot(new ContractKey(ID,new ContractUint256(BigInteger.valueOf(iteration))), iteration);
//        iteration ++;
//    }

}

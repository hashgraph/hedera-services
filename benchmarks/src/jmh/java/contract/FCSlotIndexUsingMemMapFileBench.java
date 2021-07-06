package contract;

import com.hedera.services.state.merkle.virtual.ContractKey;
import com.hedera.services.state.merkle.virtual.ContractUint256;
import com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCSlotIndexUsingMemMapFile;
import com.hedera.services.store.models.Id;
import fcmmap.FCVirtualMapTestUtils;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("DuplicatedCode")
@State(Scope.Thread)
@Warmup(iterations = 1, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 20, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class FCSlotIndexUsingMemMapFileBench {
    public static final Path STORE_PATH = Path.of("store");
    public static final Id ID = new Id(1,2,3);


    @Param({"8","16","32","256","512","1024"})
    public int numOfFiles;
    @Param({"8","16","32","256"})
    public int numOfKeysPerBin;
    @Param({"1","8","32","256","1024"})
    public int locksPerFile;
    @Param({"10000000"})
    public int totalKeys;
    @Param({"100000"})
    public int numEntities;

    // state
    public FCSlotIndexUsingMemMapFile<ContractKey> slotIndex;
    public int iteration = 0;
    Random RANDOM;
    List<FCSlotIndexUsingMemMapFile<ContractKey>> indexes = new ArrayList<>();

    @Setup(Level.Trial)
    public void setup() {
        System.out.println("\nsetup() ------------------------------------------");
        try {
            RANDOM = new Random(12345);
            // delete any old store
            FCVirtualMapTestUtils.deleteDirectoryAndContents(STORE_PATH);
            // calculate size
            int totalBins = totalKeys / numOfKeysPerBin;
            int numOfBinsPerFile = totalBins/numOfFiles;
            System.out.println("totalBins = " + totalBins);
            totalBins = totalBins == 0 ? 0 : 32 - Integer.numberOfLeadingZeros(totalBins - 1);
            totalBins = (int)(Math.log(totalBins)/Math.log(2));
            int binsPerLock = numOfBinsPerFile/locksPerFile;
            System.out.println("totalBins = " + totalBins);
            System.out.println("numOfBinsPerFile = " + numOfBinsPerFile);
            System.out.println("binsPerLock = " + binsPerLock);
            // get slot index suppliers
            slotIndex = new FCSlotIndexUsingMemMapFile<ContractKey>(STORE_PATH, "FCSlotIndexBench",
                    totalBins, numOfFiles, ContractKey.SERIALIZED_SIZE, numOfKeysPerBin, 16,binsPerLock);
            // create initial data
            for (long i = 0; i < numEntities; i++) {
                if (i % (numEntities/10) == 0) System.out.println("created = " + i);
                slotIndex.putSlot(new ContractKey(ID,new ContractUint256(BigInteger.valueOf(i))), i);
            }
            System.out.println("\nslotIndex.keyCount() = " + slotIndex.keyCount());
            FCVirtualMapTestUtils.printDirectorySize(STORE_PATH);
            indexes.clear();
            indexes.add(slotIndex);
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
//
//    @Benchmark
//    public void randomOps() throws Exception {
//        switch(RANDOM.nextInt(11)) {
//            case 0:
//            case 1:
//            case 2:
//            case 3:
//                indexes.forEach(pairFCSlotIndex -> {
//                    try {
//                        pairFCSlotIndex.getSlot(randomKey(RANDOM));
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                });
//                break;
//            case 4:
//            case 5:
//            case 6:
//                slotIndex.putSlot(randomKey(RANDOM), randomPositiveLong(RANDOM));
//                break;
//            case 7:
//                slotIndex.removeSlot(randomKey(RANDOM));
//                break;
//            case 8:
//                slotIndex.keyCount();
//                break;
//            case 9: // fast copy
//                slotIndex = slotIndex.copy();
//                indexes.add(slotIndex);
//                break;
//            case 10: // release old copy
//                if (RANDOM.nextDouble() > 0.75 && indexes.size() > 1) {
//                    indexes.remove(0).release();
//                }
//                break;
//        }
//    }

    @Benchmark
    public void put() throws Exception {
        slotIndex.putSlot(randomKey(),RANDOM.nextLong());
    }

    @Benchmark
    public void get() throws Exception {
        slotIndex.getSlot(randomKey());
    }

    public ContractKey randomKey() {
        int i = RANDOM.nextInt(numEntities);
        return new ContractKey(ID,new ContractUint256(BigInteger.valueOf(i)));
    }

    public static long randomPositiveLong(Random RANDOM) {
        // it's okay that the bottom word remains signed.
        return (long)(RANDOM.nextDouble()*Long.MAX_VALUE);
    }
}

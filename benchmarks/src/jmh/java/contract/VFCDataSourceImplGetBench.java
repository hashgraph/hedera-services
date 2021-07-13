package contract;

import com.hedera.services.state.merkle.v2.VFCDataSourceImpl;
import com.hedera.services.state.merkle.virtual.ContractKey;
import com.hedera.services.state.merkle.virtual.ContractUint256;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.models.Id;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import fcmmap.FCVirtualMapTestUtils;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("jol")
@State(Scope.Thread)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class VFCDataSourceImplGetBench {
    public static final Path STORE_PATH = Path.of("store");
    public static final Id ID = new Id(1,2,3);

    @Param({"1000000"})
    public long numEntities;

    // state
    public VFCDataSourceImpl<ContractUint256,ContractUint256> dataSource;
    public Random random = new Random(1234);
    public int iteration = 0;
    private ContractUint256 key1 = null;
    private ContractUint256 key2 = null;
    private long randomIndex1;
    private long randomIndex2;
    private long randomIndex3;

    @Setup(Level.Trial)
    public void setup() {
        System.out.println("EmptyState.setup");
        try {
            // delete any old store
            FCVirtualMapTestUtils.deleteDirectoryAndContents(STORE_PATH);
            // get slot index suppliers
            dataSource = VFCDataSourceImpl.createOnDisk(STORE_PATH,numEntities,
                    ContractUint256.SERIALIZED_SIZE, ContractUint256::new,
                    ContractUint256.SERIALIZED_SIZE, ContractUint256::new);
            // create data
            long printStep = Math.min(1_000_000, numEntities/4);
            for (long i = 0; i < numEntities; i++) {
                if (i % printStep == 0) System.out.println("created = " + i);
                dataSource.addLeaf(i,new ContractUint256(i),new ContractUint256(i),FCVirtualMapTestUtils.hash((int)i));
            }
            // reset iteration counter
            iteration = 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        try {
            dataSource.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        FCVirtualMapTestUtils.printDirectorySize(STORE_PATH);
    }

    @Setup(Level.Invocation)
    public void randomIndex(){
        randomIndex1 = (long)(random.nextDouble()*numEntities);
        randomIndex2 = (long)(random.nextDouble()*numEntities);
        randomIndex3 = (long)(random.nextDouble()*numEntities);
        key1 = new ContractUint256((long)(random.nextDouble()*numEntities));
        key2 = new ContractUint256((long)(random.nextDouble()*numEntities));
    }

    @Benchmark
    public void loadLeafPath() throws Exception {
        dataSource.loadLeafPath(key1);
    }

    @Benchmark
    public void loadLeafKey() throws Exception {
        dataSource.loadLeafKey(randomIndex1);
    }

    @Benchmark
    public void loadLeafValueByPath() throws Exception {
        dataSource.loadLeafValue(randomIndex2);
    }

    @Benchmark
    public void loadLeafValueByKey() throws Exception {
        dataSource.loadLeafValue(key2);
    }

    @Benchmark
    public void loadHash() throws Exception {
        dataSource.loadHash(randomIndex3);
    }

}

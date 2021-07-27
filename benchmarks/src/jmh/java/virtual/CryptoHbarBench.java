package virtual;

import com.swirlds.virtualmap.VirtualMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 */
@SuppressWarnings("jol")
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class CryptoHbarBench extends VFCMapBenchBase<VFCMapBenchBase.Id, VFCMapBenchBase.Account> {

    @Param({"10000"})
    public int targetOpsPerSecond;

    @Param({"1000000", "10000000", "25000000", "50000000", "100000000", "250000000", "500000000", "1000000000"})
    public long numEntities;

    @Param("true") // TODO Remove and replace with a benchmark that measures additions?
    public boolean preFill;

    @Param({"lmdbMem", "lmdb", "rocksdb", "jasperdb"})
    public DataSourceType dsType;

    // This is the map we will be testing!
    private VirtualMap<Id, Account> virtualMap;

    @Setup
    public void prepare() throws Exception {
        virtualMap = createMap(dsType, Id.SERIALIZED_SIZE, Id::new, Account.SERIALIZED_SIZE, Account::new, numEntities);

        if (preFill) {
            for (int i = 0; i < numEntities; i++) {
                if (i % 100000 == 0 && i > 0) {
                    System.out.println("Completed: " + i);
                    virtualMap = pipeline.endRound(virtualMap);
                }

                final var key = asId(i);
                final var value = asAccount(i);
                try {
                    virtualMap.put(key, value);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println(i);
                    throw e;
                }
            }

            System.out.println("Completed: " + numEntities);

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
    public void update() {
        // Start modifying the new fast copy
        for (int j=0; j<targetOpsPerSecond; j++) {
            // Read the two accounts involved in the token transfer
            // Debit and Credit them for hbar balances
            final var senderId = new Id(rand.nextInt((int)numEntities));
            final var receiverId = new Id(rand.nextInt((int)numEntities));

            final var sender = virtualMap.getForModify(senderId);
            final var receiver = virtualMap.getForModify(receiverId);

            if (sender == null) {
                try {
                    System.err.println("Sender was null: id=" + senderId.getNum() +
                            ", path=" + virtualMap.getDataSource().loadLeafPath(senderId));
                    virtualMap.getForModify(senderId);
                } catch (Exception e) {
                    System.err.println("Sender was null. Could not load leaf path");
                }
            } else if (receiver == null) {
                try {
                    System.err.println("Receiver was null: id=" + receiverId.getNum() +
                            ", path=" + virtualMap.getDataSource().loadLeafPath(receiverId));
                    ;
                } catch (Exception e) {
                    System.err.println("Receiver was null. Could not load leaf path");
                }
            } else {
                final var tinyBars = rand.nextInt(10);
                sender.setHbarBalance(sender.getHbarBalance() - tinyBars);
                receiver.setHbarBalance(receiver.getHbarBalance() + tinyBars);
            }
        }

        virtualMap = pipeline.endRound(virtualMap);
    }

    public static void main(String[] args) throws Exception {
        final var start = new AtomicLong(System.currentTimeMillis());
        final var test = new CryptoHbarBench();
        test.numEntities = 1000;
        test.dsType = DataSourceType.jasperdb;
        test.preFill = true;
        test.targetOpsPerSecond = 2;
        test.prepare();

        for (int i=0; i< test.numEntities; i++) {
            final var value = test.virtualMap.getForModify(new Id(i));
            if (value == null) {
                test.virtualMap.getForModify(new Id(i));
            }
        }

        int totalCount = 0;
        while (true) {
            test.update();
//            for (int i=0; i<2; i++) {
//                System.gc();
//            }
            totalCount += test.targetOpsPerSecond;
            printTestUpdate(start, test.targetOpsPerSecond, totalCount, "Hey");
        }
    }

    private static double printTestUpdate(AtomicLong start, long count, long totalCount, String msg) {
        long took = System.currentTimeMillis() - start.getAndSet(System.currentTimeMillis());
        double timeSeconds = (double)took/1000d;
        double perSecond = (double)count / timeSeconds;
        System.out.printf("%s x %,d : [%,d] at %,.0f per/sec, took %,.2f seconds\n",msg,totalCount, count, perSecond, timeSeconds);
        return perSecond;
    }
}

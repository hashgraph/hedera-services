package virtual;

import com.swirlds.fcmap.VFCMap;
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
    private VFCMap<Id, Account> virtualMap;

    @Setup
    public void prepare() throws Exception {
        virtualMap = createMap(dsType, Id.SERIALIZED_SIZE, Id::new, Account.SERIALIZED_SIZE, Account::new);

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

            for (int i=0; i<4; i++) {
                virtualMap = pipeline.endRound(virtualMap);
            }

            try {
                Thread.sleep(10000);
            } catch (InterruptedException ignored) {}

            for (int i=0; i<400; i++) {
                virtualMap = pipeline.endRound(virtualMap);
            }

            for (int i=0; i<50; i++) {
                System.gc();
            }

            try {
                Thread.sleep(10000);
            } catch (InterruptedException ignored) {}
        }

        final var ds = virtualMap.getDataSource();

        for (int i=0; i<(numEntities*2 - 1); i++) {
            final var hash = ds.loadInternalHash(i); // TODO Richard: could be leaf hash?
            if (hash == null) {
                System.err.println("Failed to find hash for path " + i);
            }
        }

        for (int i=(int)(numEntities); i<(numEntities*2 -1); i++) {
            final var id = ds.loadLeafKey(i);
            if (id == null) {
                System.err.println("Failed to find leaf key for path " + i);
            } else {
                final var value = ds.loadLeafValue(i);
                if (value == null) {
                    System.err.println("Failed to find leaf value for path " + i);
                }

                final var path = ds.loadLeafPath(id);
                if (path == -1) {
                    System.err.println("Failed to load leaf path for id " + id.getNum());
                }
            }
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

            if (sender == null || receiver == null) {
                System.out.printf("WHAT?");
                try {
                    final var ds = virtualMap.getDataSource();
                    for (int i = 0; i < (numEntities * 2 - 1); i++) {
                        final var hash = ds.loadInternalHash(i); // TODO Richard: maybe could be leaf hash?
                        if (hash == null) {
                            System.err.println("Failed to find hash for path " + i);
                        }
                    }

                    for (int i = (int) (numEntities - 1); i < (numEntities * 2 - 1); i++) {
                        final var id = ds.loadLeafKey(i);
                        if (id == null) {
                            System.err.println("Failed to find leaf key for path " + i);
                        } else {
                            final var value = ds.loadLeafValue(i);
                            if (value == null) {
                                System.err.println("Failed to find leaf value for path " + i);
                            }

                            final var path = ds.loadLeafPath(id);
                            if (path == -1) {
                                System.err.println("Failed to load leaf path for id " + id.getNum());
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                virtualMap.getForModify(senderId);
                virtualMap.getForModify(receiverId);
            }

            final var tinyBars = rand.nextInt(10);
            sender.setHbarBalance(sender.getHbarBalance() - tinyBars);
            receiver.setHbarBalance(receiver.getHbarBalance() + tinyBars);
        }

        virtualMap = pipeline.endRound(virtualMap);
    }
}

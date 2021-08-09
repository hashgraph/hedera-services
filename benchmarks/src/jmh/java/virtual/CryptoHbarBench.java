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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 */
@SuppressWarnings("jol")
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class CryptoHbarBench extends VFCMapBenchBase<VFCMapBenchBase.Id, VFCMapBenchBase.Account> {
    private static final ThreadGroup PREPPER_GROUP = new ThreadGroup("PrepperGroup");

    @Param({"10000"})
    public int targetOpsPerSecond;

    @Param({"1000000", "10000000", "25000000", "50000000", "100000000", "250000000", "500000000", "1000000000"})
    public long numEntities;

    @Param("true") // TODO Remove and replace with a benchmark that measures additions?
    public boolean preFill;

    @Param({"lmdbMem", "lmdb", "rocksdb", "jasperdb"})
    public DataSourceType dsType;

    @Param({"5"})
    public int prepperThreadCount;

    // This is the map we will be testing!
    private VirtualMap<Id, Account> virtualMap;

    private ExecutorService prepService;

    private AtomicReference<CompletableFuture<List<Account[]>>> accountsFutureRef = new AtomicReference<>();

    @Setup
    public void prepare() throws Exception {
        virtualMap = createMap(dsType, Id.SERIALIZED_SIZE, Id::new, Account.SERIALIZED_SIZE, Account::new, numEntities);
        prepService = Executors.newFixedThreadPool(prepperThreadCount, Pipeline.threadFactory("Preppers", PREPPER_GROUP));

        if (preFill) {
            for (int i = 0; i < numEntities; i++) {
                if (i % 100000 == 0 && i > 0) {
                    System.out.println("Completed: " + i);
//                    System.out.println(virtualMap.toDebugString());
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
//            System.out.println(virtualMap.toDebugString());
            virtualMap = pipeline.endRound(virtualMap);
        }

        printDataStoreSize();

        prepNextRound();
    }

    @TearDown
    public void destroy() {
        printDataStoreSize();
        /*store.close();*/
    }

    private void prepNextRound() {
        final var cf = new CompletableFuture<List<Account[]>>();
        accountsFutureRef.set(cf);
        final var accounts = new ArrayList<Account[]>(targetOpsPerSecond);
        final var futures = new ArrayList<Future<List<Account[]>>>(prepperThreadCount);
        for (int i=0; i<prepperThreadCount; i++) {
            final var numOps = targetOpsPerSecond / prepperThreadCount;
            final var future = prepService.submit(() -> {
                List<Account[]> threadAccounts = new ArrayList<>(numOps);
                for (int j=0; j<numOps; j++) {
                    final var senderId = new Id(rand.nextInt((int)numEntities));
                    final var receiverId = new Id(rand.nextInt((int)numEntities));

                    final var sender = virtualMap.getForModify(senderId);
                    final var receiver = virtualMap.getForModify(receiverId);
                    threadAccounts.add(new Account[] {sender, receiver});
                }
                return threadAccounts;
            });
            futures.add(future);
        }

        for (final var future : futures) {
            try {
                accounts.addAll(future.get()); // block on each
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        cf.complete(accounts);
    }

    /**
     * Benchmarks update operations of an existing tree.
     */
    @Benchmark
    public void handleTransactions() throws Exception {
        final var f = accountsFutureRef.getAndSet(null);
        final var accounts = f.get();
        prepNextRound();

        for (final var arr : accounts) {
            final var sender = arr[0];
            final var receiver = arr[1];
            final var tinyBars = rand.nextInt(10);
            sender.setHbarBalance(sender.getHbarBalance() - tinyBars);
            receiver.setHbarBalance(receiver.getHbarBalance() + tinyBars);
        }

        virtualMap = pipeline.endRound(virtualMap);
    }

    public static void main(String[] args) throws Exception {
        final var test = new CryptoHbarBench();
        test.numEntities = 100_000;
        test.dsType = DataSourceType.jasperdb;
        test.preFill = true;
        test.targetOpsPerSecond = 10_000;
        test.prepperThreadCount = 5;
        test.prepare();

        for (int i=0; i< test.numEntities; i++) {
            final var value = test.virtualMap.getForModify(new Id(i));
            if (value == null) {
                test.virtualMap.getForModify(new Id(i));
            }
        }

        int totalCount = 0;
        final var start = new AtomicLong();
        while (true) {
            start.set(System.nanoTime());
            test.handleTransactions();
            totalCount += test.targetOpsPerSecond;
            printTestUpdate(start, test.targetOpsPerSecond, totalCount, "Iteration");
        }
    }

    private static double printTestUpdate(AtomicLong start, long count, long totalCount, String msg) {
        final var stopTime = System.nanoTime();
        long took = stopTime - start.get();
        double timeSeconds = (double)took/1_000_000_000d;
        double perSecond = (double)count / timeSeconds;
        System.out.printf("%s x %,d : [%,d] at %,.0f per/sec, took %,.4f seconds\n",
                msg, totalCount, count, perSecond, timeSeconds);
        return perSecond;
    }
}

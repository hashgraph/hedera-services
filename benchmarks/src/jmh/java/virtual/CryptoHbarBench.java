package virtual;

import com.swirlds.virtualmap.VirtualMap;
import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Benchmark for simulating swirlds/hedera lifecycle doing chunks of 10k crypto transfers. Results are multiplied by 10k
 * to get number of transfers per second. The 10k number can be changed by setting {targetOpsPerSecond}
 */
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.MINUTES)
@Measurement(iterations = 500, time = 1, timeUnit = TimeUnit.MINUTES)
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

    @Param({"lmdb", "jasperdbIhRam","jasperdbIhDisk","jasperdbIhHalf"})
    public DataSourceType dsType;

    @Param({"5"})
    public int prepperThreadCount;

    // This is the map we will be testing!
    private VirtualMap<Id, Account> virtualMap;

    private ExecutorService prepService;

    private final AtomicReference<CompletableFuture<List<Account[]>>> accountsFutureRef = new AtomicReference<>();

    @Setup
    public void prepare() throws Exception {
        virtualMap = createMap(dsType, Id.SERIALIZED_SIZE, Id::new, Account.SERIALIZED_SIZE, Account::new, numEntities);
        prepService = Executors.newFixedThreadPool(prepperThreadCount, Pipeline.threadFactory("Preppers", PREPPER_GROUP));

        long START = System.currentTimeMillis();
        if (preFill) {
            for (int i = 0; i < numEntities; i++) {
                if (i % 100000 == 0 && i > 0) {
                    final long END = System.currentTimeMillis();
                    double tookSeconds = (END - START) / 1000d;
                    START = END;
                    System.out.printf("Completed: %,d in %,.2f seconds\n",i,tookSeconds);
                    virtualMap = pipeline.endRound(virtualMap);
                }
                if (numEntities > 100_000_000) {
                    // for large data loads give the GC a chance as we crate object like a crazy beast!
                    if (i % 1_000_000 == 0 && i > 0) {
                        System.gc();
                        //noinspection BusyWait
                        Thread.sleep(2000);
                    }
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

            System.out.printf("Completed: %,d\n",numEntities);
            virtualMap = pipeline.endRound(virtualMap);
        }

        printDataStoreSize();
        prepNextRound();
    }

    @TearDown
    public void destroy() {
        printDataStoreSize();
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
}

package virtual;

import com.swirlds.virtualmap.VirtualMap;
import disruptor.Transaction;
import disruptor.TransactionProcessor;
import disruptor.TransactionPublisher;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Random;
import java.util.concurrent.TimeUnit;

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
//    private static final ThreadGroup PREPPER_GROUP = new ThreadGroup("PrepperGroup");

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

    @Param({"4"})
    public int preFetchEventHandlers;

    // This is the map we will be testing!
    private VirtualMap<Id, Account> virtualMap;

    private TransactionProcessor<Id, Account, Data> txProcessor;

    // Need to wrap in accessor since lambdas need a level of indirection so they can fetch
    // the latest copy of the map after the copy() call.
    VirtualMap<Id, Account> getVirtualMap() {
        return virtualMap;
    }

    @Setup
    public void prepare() throws Exception {
        virtualMap = createMap(dsType, Id.SERIALIZED_SIZE, Id::new, Account.SERIALIZED_SIZE, Account::new, numEntities);

        final var rand = new Random();
        txProcessor = new TransactionProcessor<VFCMapBenchBase.Id, VFCMapBenchBase.Account, Data>(
                preFetchEventHandlers,
                (Transaction<Data> tx) -> {   // preFetch logic
                    VirtualMap<Id, Account> map = getVirtualMap();

                    final Data data = tx.getData();
                    final Account sender = map.getForModify((Id) data.getSenderId());
                    if (sender == null) {
                        System.out.println("NULL SENDER " + data.getSenderId() + ", last = " + tx.isLast());
                    }

                    final Account receiver = map.getForModify((Id) data.getReceiverId());
                    if (receiver == null)
                        System.out.println("NULL RECEIVER " + data.getReceiverId() + ", last = " + tx.isLast());

                    data.setSender(map.getForModify((Id) data.getSenderId()));
                    data.setReceiver(map.getForModify((Id) data.getReceiverId()));
                },
                (Transaction<Data> tx) -> {   // handleTransaction logic
                    final var tinyBars = rand.nextInt(10);
                    final Data data = tx.getData();
                    final Account sender = (Account) data.getSender();
                    final Account receiver = (Account) data.getReceiver();

                    sender.setHbarBalance(sender.getHbarBalance() - tinyBars);
                    receiver.setHbarBalance(receiver.getHbarBalance() + tinyBars);
                }
        );

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
//        prepNextRound();
    }

    @TearDown
    public void destroy() {
        printDataStoreSize();
    }

    // Not needed anymore, inline transaction generation into handleTransaction since this is
    // how it will behave in EventFlow.
//    private void prepNextRound() {
//        final var cf = new CompletableFuture<List<Transaction>>();
//        txsFutureRef.set(cf);
//
//        // Pre-fetch is done as part of handleTransaction, don't need multi-threaded loading here.
//        final var txs = new ArrayList<Transaction>(targetOpsPerSecond);
//        for (int j=0; j<targetOpsPerSecond; j++) {
//            final var senderId = new Id(rand.nextInt((int)numEntities));
//            final var receiverId = new Id(rand.nextInt((int)numEntities));
//
//            txs.add(new Transaction(senderId, receiverId));
//        }
//        cf.complete(txs);
//
////        final var futures = new ArrayList<Future<List<Transaction>>>(prepperThreadCount);
////        for (int i=0; i<prepperThreadCount; i++) {
////            final var numOps = targetOpsPerSecond / prepperThreadCount;
////            final var future = prepService.submit(() -> {
////                List<Transaction> threadTxs = new ArrayList<>(numOps);
////                for (int j=0; j<numOps; j++) {
////                    final var senderId = new Id(rand.nextInt((int)numEntities));
////                    final var receiverId = new Id(rand.nextInt((int)numEntities));
////
////                    threadTxs.add(new Transaction(senderId, receiverId));
//////                    final var sender = virtualMap.getForModify(senderId);
//////                    final var receiver = virtualMap.getForModify(receiverId);
//////                    threadAccounts.add(new Account[] {sender, receiver});
////                }
////                return threadTxs;
////            });
////            futures.add(future);
////        }
////
////        for (final var future : futures) {
////            try {
////                txs.addAll(future.get()); // block on each
////            } catch (InterruptedException | ExecutionException e) {
////                e.printStackTrace();
////            }
////        }
////
////        cf.complete(txs);
//    }

    /**
     * Benchmarks update operations of an existing tree.
     */
    @Benchmark
    public void handleTransactions() throws Exception {
        TransactionPublisher<Data> publisher = txProcessor.getPublisher();
        for (int j=0; j<targetOpsPerSecond; j++) {
            final var senderId = new Id(rand.nextInt((int)numEntities));
            final var receiverId = new Id(rand.nextInt((int)numEntities));

            publisher.publish(new Data(senderId, receiverId));
        }

        // In EventFlow, copy() is called before noMoreTransactions() but since the disruptor
        // cycle is async, we need to be sure we're done with the transactions before moving
        // on to the copy().
        //
        publisher.end();
        virtualMap = pipeline.endRound(virtualMap);
    }

    public static class Data {
        Id senderId;
        Id receiverId;

        Account sender;
        Account receiver;

        public Data(Id senderId, Id receiverId) {
            this.senderId = senderId;
            this.receiverId = receiverId;
        }

        public Id getSenderId() { return this.senderId; }
        public Id getReceiverId() { return this.receiverId; }

        public Account getSender() { return this.sender; }
        public Account getReceiver() { return this.receiver; }

        public void setSender(Account sender) { this.sender = sender; }
        public void setReceiver(Account receiver) { this.receiver = receiver; }
    }
}

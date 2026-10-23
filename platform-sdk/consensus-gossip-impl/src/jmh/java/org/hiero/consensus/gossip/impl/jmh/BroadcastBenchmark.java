// SPDX-License-Identifier: Apache-2.0
package org.hiero.consensus.gossip.impl.jmh;

import static org.hiero.base.concurrent.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.platform.event.GossipEvent;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.base.time.Time;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.hiero.base.concurrent.pool.CachedPoolParallelExecutor;
import org.hiero.base.concurrent.throttle.StackTraceDeduplicator;
import org.hiero.consensus.benchmark.tools.histogram.LatencyProfiler;
import org.hiero.consensus.benchmark.tools.histogram.LatencyRecorder;
import org.hiero.consensus.event.DefaultIntakeEventCounter;
import org.hiero.consensus.gossip.impl.gossip.Utilities;
import org.hiero.consensus.gossip.impl.gossip.rpc.SyncData;
import org.hiero.consensus.gossip.impl.gossip.shadowgraph.RpcPeerHandler;
import org.hiero.consensus.gossip.impl.gossip.shadowgraph.ShadowgraphSynchronizer;
import org.hiero.consensus.gossip.impl.gossip.sync.SyncMetrics;
import org.hiero.consensus.gossip.impl.network.NetworkMetrics;
import org.hiero.consensus.gossip.impl.network.PeerInfo;
import org.hiero.consensus.gossip.impl.network.protocol.PeerProtocol;
import org.hiero.consensus.gossip.impl.network.protocol.rpc.RpcPeerProtocol;
import org.hiero.consensus.gossip.impl.network.protocol.rpc.RpcProtocol;
import org.hiero.consensus.metrics.config.MetricsConfig;
import org.hiero.consensus.metrics.platform.DefaultPlatformMetrics;
import org.hiero.consensus.metrics.platform.MetricKeyRegistry;
import org.hiero.consensus.metrics.platform.PlatformMetricsFactoryImpl;
import org.hiero.consensus.model.event.EventHashFactory;
import org.hiero.consensus.model.event.PlatformEvent;
import org.hiero.consensus.model.hashgraph.ConsensusConstants;
import org.hiero.consensus.model.hashgraph.EventWindow;
import org.hiero.consensus.model.node.NodeId;
import org.hiero.consensus.model.roster.RosterWrapper;
import org.hiero.consensus.model.status.PlatformStatus;
import org.hiero.consensus.model.test.fixtures.event.TestingEventBuilder;
import org.hiero.consensus.model.test.fixtures.roster.RosterWrapperFactory;
import org.hiero.consensus.monitoring.FallenBehindMonitor;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Measures the latency of broadcast: how a node sends each event it creates to all its peers at once, without waiting
 * for the next sync with each peer, and how a peer handles such an event.
 * <ul>
 *   <li>{@link #broadcast(Sender)} times the calls of {@link RpcProtocol#addEvent(PlatformEvent)} with an event the
 *   node created. For every peer, the call asks the peer's {@link RpcPeerHandler} whether broadcast to that peer is
 *   running; if it is, the peer's {@link RpcPeerProtocol} puts a task that writes the event into its output
 *   queue.</li>
 *   <li>{@link #receive(Receiver)} times the calls of {@link RpcPeerHandler#receiveBroadcastEvent(GossipEvent)} with
 *   an event the peer created. The call turns the event into a {@link PlatformEvent} and passes it on to the
 *   intake.</li>
 * </ul>
 *
 * <p>In production, these calls run on different threads and nodes: a thread of the wiring framework calls
 * {@code addEvent}; for each peer, a writer thread takes the task from the queue, serializes the event, and sends it;
 * on the peer, a reader thread parses the event and a dispatch thread calls {@code receiveBroadcastEvent}. The
 * benchmark makes both calls on one thread, without the other threads and without a network. It does not measure:
 * <ul>
 *   <li>serializing and parsing the event, which the event serialization benchmark measures;</li>
 *   <li>waking the writer threads: in production, a writer thread with an empty queue waits for the next task, and
 *   {@code addEvent} wakes it up, for every peer; here, no thread waits, and no other thread takes the locks of the
 *   queues;</li>
 *   <li>the network, and the handoffs between the threads.</li>
 * </ul>
 *
 * <p>A handler broadcasts only while the connection to its peer runs the RPC protocol, and only after the first sync on
 * that connection. The benchmark creates the {@link RpcProtocol} and the handler and protocol of every peer as
 * production does, but opens no connection. Instead, it drives every handler through one sync of empty shadowgraphs,
 * calling the handler's methods as the dispatch thread does. {@code RpcProtocol} keeps the handlers, and each
 * {@code RpcPeerProtocol} its output queue, in private fields, which the benchmark reads with reflection; if one of
 * them is renamed, the setup fails.
 *
 * <p>After every call of {@code addEvent}, the benchmark checks that the output queue of every peer holds exactly one
 * task, and empties it. So every call finds empty queues, as in production, where the writer threads empty them
 * between two events of the node. The check and the emptying are paused ({@link LatencyRecorder#pause()}), so they
 * count neither in a latency nor in the allocation.
 *
 * <p>The work of {@code addEvent} grows with the number of peers, {@code numNodes - 1}. The work of
 * {@code receiveBroadcastEvent} grows with the number of parents and transactions of the event, one object each, but
 * not with the size of the network. Every received event has a self parent and {@value #OTHER_PARENTS} other parents,
 * the most that the default {@code maxOtherParents} of event creation allows, a signature of RSA length, and SHA-256
 * hashes, the event hash after the cutover. The number of transactions per event is a parameter: on mainnet, events
 * carry fewer than 0.1 transactions on average (metric {@code trans_per_event}), so most carry none. A transaction has
 * {@value #TRANSACTION_SIZE} bytes, the mainnet mean (metric {@code platform_bytes_per_trans}). The received events are
 * generated once per trial from a fixed seed, then serialized and parsed, as the reader thread receives them. The
 * handler does not change them, so every pass receives the same events.
 *
 * <p>The code under test gets the production metrics implementation, {@link DefaultPlatformMetrics}, not started, the
 * production {@link DefaultIntakeEventCounter}, and the system clock. In production, the received events go to the
 * wiring framework, which hands them to the intake; here, a consumer keeps them for the check.
 *
 * <p>After every invocation, the benchmark checks that every call of {@code addEvent} reached every peer, and that the
 * consumer received every event once, in order.
 *
 * <p>One invocation, a pass, makes {@value #BROADCASTS} or {@value #RECEPTIONS} calls. The first call of a pass, right
 * after the setup, is made but not timed: on an Apple M4 laptop, it took 3 to 9 µs (median), 12 to 44 times as long
 * as a later call, and as one call in 10,000 it set p99.99. No such setup precedes an event in production.
 *
 * <p>Each latency includes one clock read ({@link LatencyRecorder#lap()}). The calls take well under a microsecond, so
 * the clock read is a noticeable part of their latency. On an Apple M4 laptop, measured with and without laps in
 * chunks interleaved in the same run, {@code lap()} added at most 17 ns to a call of {@code addEvent} with 10 nodes (8%)
 * and 11 ns with 32 nodes (2%), and 6 to 8 ns to a call of {@code receiveBroadcastEvent} (4% to 6%); on the benchmark
 * machine it is unmeasured. A CPU profile overstates this share, at 2% to 18%: reading the clock waits for the
 * preceding instructions to finish, so the profiler charges their time to the clock read. The share is about constant,
 * so a regression still shows in full nanoseconds, but as a slightly smaller percentage.
 *
 * <p>{@link LatencyProfiler} reports the mean, the percentiles, and the allocation per measured call. JMH's primary
 * score divides the time of the whole pass, paused steps included, by the number of calls, so for
 * {@link #broadcast(Sender)} it is not the time per call; {@code latency.mean} is. Each invocation's setup ends with
 * {@code System.gc()}; a pass during which a garbage collection runs is discarded and replaced by the next invocation.
 *
 * <p>Run with {@code -prof org.hiero.consensus.benchmark.tools.histogram.LatencyProfiler}.
 */
@Fork(
        value = 1,
        jvmArgsAppend = {"-Xms1g", "-Xmx1g", "-XX:+AlwaysPreTouch"})
@Warmup(iterations = 1, time = 10)
@Measurement(iterations = 2, time = 10)
public class BroadcastBenchmark {

    /** The seed of the random number generator. The same seed produces the same roster and events. */
    private static final long SEED = 0;

    /** The number of calls of {@code addEvent} in one invocation. */
    private static final int BROADCASTS = 10_000;

    /** The number of calls of {@code receiveBroadcastEvent} in one invocation. */
    private static final int RECEPTIONS = 10_000;

    /** The number of nodes for {@link #receive(Receiver)}. It only has to exceed the number of parents of an event. */
    private static final int RECEIVER_NODES = 10;

    /** The number of other parents of every received event, the default {@code maxOtherParents} of event creation. */
    private static final int OTHER_PARENTS = 4;

    /** The size of a transaction in bytes, the mainnet mean. */
    private static final int TRANSACTION_SIZE = 445;

    /** The length of a SHA-256 hash in bytes. */
    private static final int SHA_256_LENGTH = 32;

    /** The default of {@code fallenBehindThreshold}; it only matters for syncs. */
    private static final double FALLEN_BEHIND_THRESHOLD = 0.5;

    /**
     * The node that broadcasts its events to its peers.
     */
    @State(Scope.Thread)
    public static class Sender {

        /** The number of nodes in the network, the node itself included. */
        @Param({"10", "32"})
        public int numNodes;

        /** The gossip of the node. */
        private Gossip gossip;

        /** The events the node broadcasts in one invocation, all created by the node. */
        private PlatformEvent[] selfEvents;

        /** The number of calls in the invocation after which every peer's queue held exactly one task. */
        private int completeBroadcasts;

        /** The latency recorder of the invocation. */
        private LatencyRecorder recorder;

        /**
         * Creates the gossip of the node and its events. This method is called once per trial.
         */
        @Setup(Level.Trial)
        public void createGossip() {
            EventHashFactory.initialize(ConsensusConstants.ROUND_FIRST);
            final Random random = new Random(SEED);
            final RosterWrapper roster = RosterWrapperFactory.randomRoster(random, numNodes);
            final NodeId selfId = roster.nodeIdAtIndex(0);
            gossip = Gossip.create(roster, selfId, event -> {
                throw new IllegalStateException("The sender received an event");
            });
            selfEvents = new PlatformEvent[BROADCASTS];
            for (int i = 0; i < BROADCASTS; i++) {
                selfEvents[i] = new TestingEventBuilder(random)
                        .setCreatorId(selfId)
                        .setAppTransactionCount(0)
                        .build();
            }
        }

        /**
         * Shuts down the executor of the metrics. This method is called once per trial.
         */
        @TearDown(Level.Trial)
        public void shutdownGossip() {
            gossip.metricsExecutor().shutdownNow();
        }

        /**
         * Prepares the invocation. This method is called once per invocation.
         */
        @Setup(Level.Invocation)
        public void prepareInvocation() {
            completeBroadcasts = 0;
            recorder = LatencyProfiler.recorder();

            // Collect the garbage of the setup and of the previous invocation now, so that it is not collected during
            // the measured calls.
            System.gc();
        }

        /**
         * Verifies that every call reached every peer. This method is called once per invocation.
         */
        @TearDown(Level.Invocation)
        public void verifyInvocation() {
            if (completeBroadcasts != BROADCASTS) {
                throw new IllegalStateException("Expected %d broadcasts to all %d peers, but got %d"
                        .formatted(BROADCASTS, numNodes - 1, completeBroadcasts));
            }
            recorder = null;
        }

        /**
         * Counts the call if every peer's output queue holds exactly one task, and empties the queues, as the writer
         * threads do in production.
         */
        private void takeQueuedTasks() {
            boolean complete = true;
            for (final BlockingQueue<?> queue : gossip.outputQueues()) {
                complete &= queue.size() == 1;
                queue.clear();
            }
            if (complete) {
                completeBroadcasts++;
            }
        }
    }

    /**
     * The node that receives the events a peer broadcasts.
     */
    @State(Scope.Thread)
    public static class Receiver {

        /** The number of transactions of every received event. */
        @Param({"0", "1"})
        public int transactionsPerEvent;

        /** The gossip of the node. */
        private Gossip gossip;

        /** The handler of the peer that broadcasts the events. */
        private RpcPeerHandler handler;

        /** The events the peer broadcasts in one invocation, as the reader thread parses them. */
        private GossipEvent[] events;

        /** The events passed on to the intake in the invocation. */
        private PlatformEvent[] receivedEvents;

        /** The number of events passed on to the intake in the invocation. */
        private int receivedCount;

        /** The latency recorder of the invocation. */
        private LatencyRecorder recorder;

        /**
         * Creates the gossip of the node and the events of its peer. This method is called once per trial.
         */
        @Setup(Level.Trial)
        public void createGossip() {
            EventHashFactory.initialize(ConsensusConstants.ROUND_FIRST);
            final Random random = new Random(SEED);
            final RosterWrapper roster = RosterWrapperFactory.randomRoster(random, RECEIVER_NODES);
            gossip = Gossip.create(roster, roster.nodeIdAtIndex(0), this::passToIntake);
            handler = gossip.handlers().getFirst();
            events =
                    generatePeerEvents(random, roster, gossip.peers().getFirst().nodeId());
            receivedEvents = new PlatformEvent[RECEPTIONS];
        }

        /**
         * Generates events of all nodes in turn, each with a self parent and
         * {@value BroadcastBenchmark#OTHER_PARENTS} other parents, and returns those created by the peer, serialized
         * and parsed.
         *
         * @param random the source of randomness
         * @param roster the roster of the network
         * @param peerId the peer that creates and broadcasts the events
         * @return the peer's events, as the reader thread parses them
         */
        private GossipEvent[] generatePeerEvents(final Random random, final RosterWrapper roster, final NodeId peerId) {
            final int nodes = roster.size();
            // The first event of every node has no parents. It is a parent only and is not received.
            final PlatformEvent[] latestEvents = new PlatformEvent[nodes];
            for (int node = 0; node < nodes; node++) {
                latestEvents[node] = new TestingEventBuilder(random)
                        .setCreatorId(roster.nodeIdAtIndex(node))
                        .setAppTransactionCount(0)
                        .build();
            }

            final List<Integer> otherNodes = new ArrayList<>(nodes - 1);
            final List<GossipEvent> peerEvents = new ArrayList<>(RECEPTIONS);
            for (int i = 0; peerEvents.size() < RECEPTIONS; i++) {
                final int creator = i % nodes;
                otherNodes.clear();
                for (int node = 0; node < nodes; node++) {
                    if (node != creator) {
                        otherNodes.add(node);
                    }
                }
                Collections.shuffle(otherNodes, random);
                final List<PlatformEvent> otherParents = new ArrayList<>(OTHER_PARENTS);
                for (int j = 0; j < OTHER_PARENTS; j++) {
                    otherParents.add(latestEvents[otherNodes.get(j)]);
                }
                final PlatformEvent event = new TestingEventBuilder(random)
                        .setSelfParent(latestEvents[creator])
                        .setOtherParents(otherParents)
                        .setAppTransactionCount(transactionsPerEvent)
                        .setTransactionSize(TRANSACTION_SIZE)
                        .build();
                latestEvents[creator] = event;
                if (event.getCreatorId().equals(peerId)) {
                    peerEvents.add(serializeAndParse(event.getGossipEvent()));
                }
            }
            verifyEvents(peerEvents);
            return peerEvents.toArray(new GossipEvent[0]);
        }

        /**
         * Verifies that every event has the parents and transactions that set the handler's work, so that a change to
         * the event builder cannot change the work unnoticed.
         *
         * @param peerEvents the events to verify
         */
        private void verifyEvents(final List<GossipEvent> peerEvents) {
            for (final GossipEvent event : peerEvents) {
                final boolean shapeMatches = event.parents().size() == 1 + OTHER_PARENTS
                        && event.parents().stream()
                                .allMatch(parent -> parent.hash().length() == SHA_256_LENGTH)
                        && event.transactions().size() == transactionsPerEvent
                        && event.transactions().stream()
                                .allMatch(transaction -> transaction.length() == TRANSACTION_SIZE);
                if (!shapeMatches) {
                    throw new IllegalStateException(("Expected events with %d parents with SHA-256 hashes and %d"
                                    + " transactions of %d bytes, but got %s")
                            .formatted(1 + OTHER_PARENTS, transactionsPerEvent, TRANSACTION_SIZE, event));
                }
            }
        }

        /**
         * Shuts down the executor of the metrics. This method is called once per trial.
         */
        @TearDown(Level.Trial)
        public void shutdownGossip() {
            gossip.metricsExecutor().shutdownNow();
        }

        /**
         * Prepares the invocation. This method is called once per invocation.
         */
        @Setup(Level.Invocation)
        public void prepareInvocation() {
            Arrays.fill(receivedEvents, null);
            receivedCount = 0;
            recorder = LatencyProfiler.recorder();

            // Collect the garbage of the setup and of the previous invocation now, so that it is not collected during
            // the measured calls.
            System.gc();
        }

        /**
         * Verifies that every event was passed on to the intake once, in order. This method is called once per
         * invocation.
         */
        @TearDown(Level.Invocation)
        public void verifyInvocation() {
            if (receivedCount != RECEPTIONS) {
                throw new IllegalStateException(
                        "Expected %d received events, but got %d".formatted(RECEPTIONS, receivedCount));
            }
            for (int i = 0; i < RECEPTIONS; i++) {
                if (receivedEvents[i].getGossipEvent() != events[i]) {
                    throw new IllegalStateException("Received event %d is not the event broadcast".formatted(i));
                }
            }
            recorder = null;
        }

        /** Keeps an event that the handler passes on to the intake. */
        private void passToIntake(final PlatformEvent event) {
            receivedEvents[receivedCount++] = event;
        }
    }

    /**
     * Broadcasts the node's events and records the latency of each call but the first.
     *
     * @param sender the node that broadcasts
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @OperationsPerInvocation(BROADCASTS)
    public void broadcast(final Sender sender) {
        final RpcProtocol rpcProtocol = sender.gossip.rpcProtocol();
        final PlatformEvent[] events = sender.selfEvents;
        final LatencyRecorder recorder = sender.recorder;
        recorder.startMeasurement();
        // The first call follows the setup, which makes it slow; it is made but not timed.
        recorder.pause();
        rpcProtocol.addEvent(events[0]);
        sender.takeQueuedTasks();
        recorder.resume();
        for (int i = 1; i < BROADCASTS; i++) {
            rpcProtocol.addEvent(events[i]);
            recorder.lap();
            recorder.pause();
            sender.takeQueuedTasks();
            recorder.resume();
        }
        recorder.stopMeasurement();
    }

    /**
     * Receives the peer's events and records the latency of each call but the first.
     *
     * @param receiver the node that receives
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @OperationsPerInvocation(RECEPTIONS)
    public void receive(final Receiver receiver) {
        final RpcPeerHandler handler = receiver.handler;
        final GossipEvent[] events = receiver.events;
        final LatencyRecorder recorder = receiver.recorder;
        recorder.startMeasurement();
        // The first call follows the setup, which makes it slow; it is made but not timed.
        recorder.pause();
        handler.receiveBroadcastEvent(events[0]);
        recorder.resume();
        for (int i = 1; i < RECEPTIONS; i++) {
            handler.receiveBroadcastEvent(events[i]);
            // One timestamp per call: each latency also contains the recording of the previous one.
            recorder.lap();
        }
        recorder.stopMeasurement();
    }

    /**
     * The gossip of one node, created as production creates it, with the handler of every peer past its first sync.
     *
     * @param metricsExecutor the executor the metrics require; it stays idle, because the metrics are not started
     * @param rpcProtocol     the node's RPC protocol
     * @param peers           the node's peers
     * @param handlers        the handler of each peer, in the order of {@code peers}
     * @param outputQueues    the output queue of each peer's protocol, in the order of {@code peers}
     */
    private record Gossip(
            ScheduledExecutorService metricsExecutor,
            RpcProtocol rpcProtocol,
            List<PeerInfo> peers,
            List<RpcPeerHandler> handlers,
            List<BlockingQueue<?>> outputQueues) {

        /**
         * Creates the gossip of a node as {@code SyncGossipModular} does, and drives the handler of every peer through
         * one sync, after which broadcast to the peer runs.
         *
         * @param roster               the roster of the network
         * @param selfId               the node
         * @param receivedEventHandler receives the events that peers broadcast to the node
         * @return the gossip of the node
         */
        static Gossip create(
                final RosterWrapper roster, final NodeId selfId, final Consumer<PlatformEvent> receivedEventHandler) {
            final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
            final MetricsConfig metricsConfig = configuration.getConfigData(MetricsConfig.class);
            final ScheduledExecutorService metricsExecutor = Executors.newSingleThreadScheduledExecutor();
            final DefaultPlatformMetrics metrics = new DefaultPlatformMetrics(
                    selfId,
                    new MetricKeyRegistry(),
                    metricsExecutor,
                    new PlatformMetricsFactoryImpl(metricsConfig),
                    metricsConfig);
            final Time time = Time.getCurrent();
            final List<PeerInfo> peers = Utilities.createPeerInfoList(roster, selfId);
            final SyncMetrics syncMetrics = new SyncMetrics(metrics, time, peers);
            final DefaultIntakeEventCounter intakeEventCounter = new DefaultIntakeEventCounter(roster);
            final ShadowgraphSynchronizer synchronizer = new ShadowgraphSynchronizer(
                    configuration, metrics, time, roster.size(), syncMetrics, intakeEventCounter, lag -> {});
            synchronizer.updateEventWindow(EventWindow.getGenesisEventWindow());

            final RpcProtocol rpcProtocol = new RpcProtocol(
                    configuration,
                    metrics,
                    time,
                    synchronizer,
                    // never started: the benchmark runs no connection
                    new CachedPoolParallelExecutor(getStaticThreadManager(), "broadcast-benchmark"),
                    intakeEventCounter,
                    roster.size(),
                    new NetworkMetrics(metrics, selfId, peers),
                    syncMetrics,
                    selfId,
                    new FallenBehindMonitor(roster, selfId, FALLEN_BEHIND_THRESHOLD),
                    receivedEventHandler,
                    new StackTraceDeduplicator());
            rpcProtocol.updatePlatformStatus(PlatformStatus.ACTIVE);

            final List<BlockingQueue<?>> outputQueues = new ArrayList<>(peers.size());
            for (final PeerInfo peer : peers) {
                final PeerProtocol peerProtocol = rpcProtocol.createPeerInstance(peer.nodeId());
                outputQueues.add((BlockingQueue<?>) readField(peerProtocol, "outputQueue"));
            }
            @SuppressWarnings("unchecked")
            final List<RpcPeerHandler> handlers =
                    List.copyOf((List<RpcPeerHandler>) readField(rpcProtocol, "allRpcPeers"));
            if (handlers.size() != peers.size()) {
                throw new IllegalStateException(
                        "Expected %d peer handlers, but got %d".formatted(peers.size(), handlers.size()));
            }

            for (final RpcPeerHandler handler : handlers) {
                completeSync(handler);
            }
            // the messages of the syncs
            outputQueues.forEach(BlockingQueue::clear);

            return new Gossip(metricsExecutor, rpcProtocol, List.copyOf(peers), handlers, List.copyOf(outputQueues));
        }

        /**
         * Drives a handler through a sync that the peer starts, with empty shadowgraphs on both sides, by calling its
         * methods as the dispatch thread of {@link RpcPeerProtocol} does when the peer's messages arrive.
         *
         * @param handler the handler
         */
        private static void completeSync(final RpcPeerHandler handler) {
            final SyncData emptySync = new SyncData(EventWindow.getGenesisEventWindow(), List.of(), false);
            // the peer starts a sync
            handler.receiveSyncData(emptySync);
            // the dispatch thread's periodic check answers: the node's sync data and which of the peer's tips it has
            handler.checkForPeriodicActions(false, false);
            // the peer says which of the node's tips it has; the node sends the events the peer lacks, none
            handler.receiveTips(List.of());
            // the peer has sent the events the node lacks, none
            handler.receiveEventsFinished();
        }

        /**
         * Reads a private field of the code under test.
         *
         * @param target the object to read from
         * @param name   the name of the field
         * @return the value of the field
         */
        private static Object readField(final Object target, final String name) {
            try {
                final Field field = target.getClass().getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (final ReflectiveOperationException | RuntimeException e) {
                throw new IllegalStateException(
                        "Cannot read the field %s of %s"
                                .formatted(name, target.getClass().getName()),
                        e);
            }
        }
    }

    /**
     * Serializes and parses an event, as gossip sends and receives it.
     *
     * @param event the event
     * @return the parsed event
     */
    private static GossipEvent serializeAndParse(final GossipEvent event) {
        final Bytes serialized = GossipEvent.PROTOBUF.toBytes(event);
        try {
            return GossipEvent.PROTOBUF.parse(serialized);
        } catch (final ParseException e) {
            throw new IllegalStateException("Failed to parse a generated event", e);
        }
    }
}

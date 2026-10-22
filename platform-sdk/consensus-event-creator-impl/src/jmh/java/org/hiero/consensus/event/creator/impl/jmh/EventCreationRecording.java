// SPDX-License-Identifier: Apache-2.0
package org.hiero.consensus.event.creator.impl.jmh;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.base.time.Time;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
import org.hiero.base.crypto.BytesSigner;
import org.hiero.base.crypto.Hash;
import org.hiero.consensus.event.NoOpIntakeEventCounter;
import org.hiero.consensus.event.creator.config.EventCreationConfig;
import org.hiero.consensus.event.creator.impl.DefaultEventCreationManager;
import org.hiero.consensus.event.creator.impl.EventCreator;
import org.hiero.consensus.event.creator.impl.tipset.TipsetEventCreator;
import org.hiero.consensus.fakes.noop.NoOpMetrics;
import org.hiero.consensus.hashgraph.impl.ConsensusEngineOutput;
import org.hiero.consensus.hashgraph.impl.DefaultConsensusEngine;
import org.hiero.consensus.model.event.PlatformEvent;
import org.hiero.consensus.model.hashgraph.ConsensusRound;
import org.hiero.consensus.model.hashgraph.EventWindow;
import org.hiero.consensus.model.quiescence.QuiescenceCommand;
import org.hiero.consensus.model.roster.RosterWrapper;
import org.hiero.consensus.model.status.PlatformStatus;
import org.hiero.consensus.model.test.fixtures.roster.RosterWrapperFactory;
import org.hiero.consensus.model.transaction.TimestampedTransaction;
import org.hiero.consensus.orphan.DefaultOrphanBuffer;
import org.hiero.consensus.test.fixtures.WeightGenerators;
import org.hiero.consensus.test.fixtures.io.RealisticPingSamples;

/**
 * Records what one node's event creator receives and does in a simulated network, so that {@link EventCreationBenchmark}
 * can replay it.
 *
 * <p>The simulation runs every node of the network on one thread, in steps of {@link #TICK} on a fake clock:
 * <ul>
 *   <li>Each node sends every event it creates directly to every other node, delayed by half the ping between them
 *   in {@link RealisticPingSamples#MAINNET}.</li>
 *   <li>Each node passes the events it receives, and its own events, through its own orphan buffer before registering
 *   them with its event creator, as the intake pipeline does in production. The orphan buffer is needed because the
 *   ping matrix does not obey the triangle inequality: an event can arrive before its parent.</li>
 *   <li>Each node calls {@link DefaultEventCreationManager#maybeCreateEvent()} once per
 *   {@link EventCreationConfig#period()}, as the heartbeat does in production, with the production defaults of
 *   {@link EventCreationConfig}.</li>
 *   <li>A consensus engine receives the events in the order in which node 0 registers them, so its event windows are
 *   the ones node 0's own consensus would produce, at the moment it would produce them. Every node gets these event
 *   windows.</li>
 * </ul>
 *
 * <p>Signing is replaced by a constant signature, and every event carries one empty transaction. All random numbers
 * come from seeded generators, so the same seed gives the same recording.
 */
final class EventCreationRecording {

    /** The start of the fake clock. */
    static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    /** The step of the simulation's fake clock. */
    private static final Duration TICK = Duration.ofMillis(1);

    /** A limit on the simulated time, so that a recording that never reaches its targets fails instead of hanging. */
    private static final Duration MAX_SIMULATED_TIME = Duration.ofMinutes(30);

    /** The signature of every event. Its content does not matter, because no one verifies it. */
    private static final Bytes SIGNATURE = Bytes.wrap(new byte[64]);

    /** Replaces signing, which this benchmark leaves out. */
    private static final BytesSigner NO_OP_SIGNER = data -> SIGNATURE;

    /** What node 0's event creation manager did in one step of the recording. */
    enum Kind {
        /** Registered a peer event, {@link Step#event()}. */
        REGISTER_PEER,
        /** Tried to create an event and passed the creation rules; {@link Step#expectedHash()} is the result. */
        CREATE,
        /** Registered its own event, the one created last, after the orphan buffer assigned its nGen and sequence. */
        REGISTER_SELF,
        /** Received a new event window, {@link Step#window()}. */
        SET_EVENT_WINDOW
    }

    /**
     * One step of node 0's event creation manager.
     *
     * @param kind           what the manager did
     * @param elapsedNanos   the time of the fake clock, in nanoseconds since {@link #START}
     * @param event          the registered peer event, for {@link Kind#REGISTER_PEER}
     * @param window         the event window, for {@link Kind#SET_EVENT_WINDOW}
     * @param expectedHash   the hash of the created event, for {@link Kind#CREATE}, or null if no event was created
     * @param nGen           the nGen of the registered self event, for {@link Kind#REGISTER_SELF}
     * @param sequenceNumber the sequence number of the registered self event, for {@link Kind#REGISTER_SELF}
     */
    record Step(
            @NonNull Kind kind,
            long elapsedNanos,
            @Nullable PlatformEvent event,
            @Nullable EventWindow window,
            @Nullable Hash expectedHash,
            long nGen,
            long sequenceNumber) {}

    /** An event on its way to a node. The order breaks ties in the arrival time, so that delivery is deterministic. */
    private record InTransit(
            long arrivalNanos, long order, @NonNull PlatformEvent event) {}

    private final int numNodes;
    private final Configuration configuration;
    private final RosterWrapper roster;
    private final FakeTime time;
    private final long[][] oneWayLatencyNanos;
    private final DefaultEventCreationManager[] managers;
    private final DefaultOrphanBuffer[] orphanBuffers;
    private final List<Queue<InTransit>> inTransit;
    private final RuleCheck nodeZeroCreator;
    private final DefaultConsensusEngine engine;
    private final Queue<EventWindow> pendingWindows = new ArrayDeque<>();
    private final List<Step> steps = new ArrayList<>();
    private long sendOrder;
    private int creationAttempts;
    private int peerRegistrations;

    private EventCreationRecording(final long seed, final int numNodes) {
        if (numNodes > RealisticPingSamples.MAINNET.length) {
            throw new IllegalArgumentException(
                    "The ping matrix has only " + RealisticPingSamples.MAINNET.length + " nodes, not " + numNodes);
        }
        this.numNodes = numNodes;
        configuration = configuration();
        roster = roster(seed, numNodes);
        time = new FakeTime(START, Duration.ZERO);
        final Metrics metrics = new NoOpMetrics();

        oneWayLatencyNanos = new long[numNodes][numNodes];
        for (int sender = 0; sender < numNodes; sender++) {
            for (int receiver = 0; receiver < numNodes; receiver++) {
                oneWayLatencyNanos[sender][receiver] = Duration.ofMillis(RealisticPingSamples.MAINNET[sender][receiver])
                                .toNanos()
                        / 2;
            }
        }

        managers = new DefaultEventCreationManager[numNodes];
        orphanBuffers = new DefaultOrphanBuffer[numNodes];
        inTransit = new ArrayList<>(numNodes);
        nodeZeroCreator = new RuleCheck(newCreator(configuration, metrics, time, roster, seed, 0));
        for (int node = 0; node < numNodes; node++) {
            final EventCreator creator =
                    node == 0 ? nodeZeroCreator : newCreator(configuration, metrics, time, roster, seed, node);
            managers[node] = newManager(configuration, metrics, time, roster, node, creator);
            orphanBuffers[node] = new DefaultOrphanBuffer(metrics, new NoOpIntakeEventCounter());
            inTransit.add(new PriorityQueue<>(
                    Comparator.comparingLong(InTransit::arrivalNanos).thenComparingLong(InTransit::order)));
        }
        engine = new DefaultConsensusEngine(
                configuration, metrics, time, roster, roster.nodeIdAtIndex(0), timestamp -> false, 0L);
    }

    /**
     * Runs the simulation until node 0 has made at least the given numbers of creation attempts and peer registrations,
     * and returns node 0's steps.
     *
     * @param seed              the seed of the roster and of every node's random numbers
     * @param numNodes          the number of nodes, at most the size of {@link RealisticPingSamples#MAINNET}
     * @param creationAttempts  the minimum number of {@link Kind#CREATE} steps
     * @param peerRegistrations the minimum number of {@link Kind#REGISTER_PEER} steps
     * @return node 0's steps, in order
     */
    @NonNull
    static List<Step> record(
            final long seed, final int numNodes, final int creationAttempts, final int peerRegistrations) {
        final EventCreationRecording recording = new EventCreationRecording(seed, numNodes);
        recording.run(creationAttempts, peerRegistrations);
        return recording.steps;
    }

    /** The configuration of every node: the defaults of all configuration types. */
    @NonNull
    static Configuration configuration() {
        return new TestConfigBuilder().getOrCreateConfig();
    }

    /** The roster of the network, with equal weights. Index 0 is the recorded node. */
    @NonNull
    static RosterWrapper roster(final long seed, final int numNodes) {
        return RosterWrapperFactory.randomRoster(new Random(seed), numNodes, WeightGenerators.BALANCED);
    }

    /**
     * Creates the tipset event creator of a node, with a seeded random generator and no signing.
     *
     * <p>{@code SHA1PRNG} seeded before its first use always produces the same numbers. {@code new SecureRandom()}
     * followed by {@code setSeed} does not: the seed only adds to the operating system's entropy. The seed is passed
     * as bytes, because {@link SecureRandom#setSeed(long)} ignores a seed of 0.
     */
    @NonNull
    static TipsetEventCreator newCreator(
            @NonNull final Configuration configuration,
            @NonNull final Metrics metrics,
            @NonNull final Time time,
            @NonNull final RosterWrapper roster,
            final long seed,
            final int nodeIndex) {
        final SecureRandom random;
        try {
            random = SecureRandom.getInstance("SHA1PRNG");
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        random.setSeed(ByteBuffer.allocate(2 * Long.BYTES)
                .putLong(seed)
                .putLong(nodeIndex)
                .array());
        return new TipsetEventCreator(
                configuration,
                metrics,
                time,
                random,
                NO_OP_SIGNER,
                roster,
                roster.nodeIdAtIndex(nodeIndex),
                () -> List.of(new TimestampedTransaction(Bytes.EMPTY, time.now())));
    }

    /** Creates the event creation manager of a node, in the status in which it creates events. */
    @NonNull
    static DefaultEventCreationManager newManager(
            @NonNull final Configuration configuration,
            @NonNull final Metrics metrics,
            @NonNull final Time time,
            @NonNull final RosterWrapper roster,
            final int nodeIndex,
            @NonNull final EventCreator creator) {
        final DefaultEventCreationManager manager = new DefaultEventCreationManager(
                configuration, metrics, time, () -> false, creator, roster, roster.nodeIdAtIndex(nodeIndex));
        manager.updatePlatformStatus(PlatformStatus.ACTIVE);
        return manager;
    }

    private void run(final int targetCreationAttempts, final int targetPeerRegistrations) {
        final long periodTicks =
                configuration.getConfigData(EventCreationConfig.class).period().dividedBy(TICK);
        for (long tick = 0;
                creationAttempts < targetCreationAttempts || peerRegistrations < targetPeerRegistrations;
                tick++) {
            if (time.elapsed().compareTo(MAX_SIMULATED_TIME) > 0) {
                throw new IllegalStateException("Node 0 made only %d creation attempts and %d peer registrations in %s"
                        .formatted(creationAttempts, peerRegistrations, MAX_SIMULATED_TIME));
            }
            for (int node = 0; node < numNodes; node++) {
                deliver(node);
            }
            if (tick % periodTicks == 0) {
                for (int node = 0; node < numNodes; node++) {
                    heartbeat(node);
                }
            }
            time.tick(TICK);
        }
    }

    /** Hands a node the events that have arrived by now. */
    private void deliver(final int node) {
        final long now = time.elapsed().toNanos();
        final Queue<InTransit> queue = inTransit.get(node);
        while (!queue.isEmpty() && queue.peek().arrivalNanos() <= now) {
            intake(node, orphanBuffers[node].handleEvent(queue.poll().event()));
        }
        applyPendingWindows();
    }

    /** Lets a node try to create an event, and sends the event to every other node and to its own intake. */
    private void heartbeat(final int node) {
        nodeZeroCreator.reached = false;
        final PlatformEvent event = managers[node].maybeCreateEvent();
        if (node == 0 && nodeZeroCreator.reached) {
            steps.add(new Step(Kind.CREATE, now(), null, null, event == null ? null : event.getHash(), 0, 0));
            creationAttempts++;
        }
        if (event == null) {
            return;
        }
        final long sentNanos = time.elapsed().toNanos();
        for (int receiver = 0; receiver < numNodes; receiver++) {
            if (receiver != node) {
                final long arrivalNanos = sentNanos + oneWayLatencyNanos[node][receiver];
                final PlatformEvent copy = event.copyGossipedData();
                copy.setSenderId(event.getCreatorId());
                copy.setTimeReceived(START.plusNanos(arrivalNanos));
                inTransit.get(receiver).add(new InTransit(arrivalNanos, sendOrder++, copy));
            }
        }
        intake(node, orphanBuffers[node].handleEvent(event));
        applyPendingWindows();
    }

    /** Registers the events that left a node's orphan buffer, and passes node 0's events to the consensus engine. */
    private void intake(final int node, @NonNull final List<PlatformEvent> events) {
        for (final PlatformEvent event : events) {
            managers[node].registerEvent(event);
            if (node != 0) {
                continue;
            }
            if (event.getCreatorId().equals(roster.nodeIdAtIndex(0))) {
                steps.add(new Step(
                        Kind.REGISTER_SELF, now(), null, null, null, event.getNGen(), event.getSequenceNumber()));
            } else {
                steps.add(new Step(Kind.REGISTER_PEER, now(), event, null, null, 0, 0));
                peerRegistrations++;
            }
            final ConsensusEngineOutput output = engine.addEvent(copy(event));
            for (final ConsensusRound round : output.consensusRounds()) {
                pendingWindows.add(round.getEventWindow());
            }
        }
    }

    /** Gives every node the event windows of the rounds that reached consensus, including those that this causes. */
    private void applyPendingWindows() {
        while (!pendingWindows.isEmpty()) {
            final EventWindow window = pendingWindows.poll();
            for (int node = 0; node < numNodes; node++) {
                if (node == 0) {
                    steps.add(new Step(Kind.SET_EVENT_WINDOW, now(), null, window, null, 0, 0));
                }
                managers[node].setEventWindow(window);
                intake(node, orphanBuffers[node].setEventWindow(window));
            }
        }
    }

    private long now() {
        return time.elapsed().toNanos();
    }

    /** Copies an event for the consensus engine, which changes the events it processes. */
    @NonNull
    private static PlatformEvent copy(@NonNull final PlatformEvent event) {
        final PlatformEvent copy = event.copyGossipedData();
        copy.setNGen(event.getNGen());
        copy.setSequenceNumber(event.getSequenceNumber());
        return copy;
    }

    /** Wraps node 0's event creator to tell whether a call of the manager passed the creation rules. */
    private static final class RuleCheck implements EventCreator {
        private final EventCreator delegate;
        private boolean reached;

        private RuleCheck(@NonNull final EventCreator delegate) {
            this.delegate = delegate;
        }

        @Override
        public void registerEvent(@NonNull final PlatformEvent event) {
            delegate.registerEvent(event);
        }

        @Override
        public void setEventWindow(@NonNull final EventWindow eventWindow) {
            delegate.setEventWindow(eventWindow);
        }

        @Nullable
        @Override
        public PlatformEvent maybeCreateEvent() {
            reached = true;
            return delegate.maybeCreateEvent();
        }

        @Override
        public void quiescenceCommand(@NonNull final QuiescenceCommand quiescenceCommand) {
            delegate.quiescenceCommand(quiescenceCommand);
        }

        @Override
        public void clear() {
            delegate.clear();
        }
    }
}

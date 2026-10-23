// SPDX-License-Identifier: Apache-2.0
package org.hiero.consensus.model.jmh;

import com.hedera.hapi.platform.event.GossipEvent;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.hiero.consensus.benchmark.tools.histogram.LatencyProfiler;
import org.hiero.consensus.benchmark.tools.histogram.LatencyRecorder;
import org.hiero.consensus.crypto.DefaultEventHasher;
import org.hiero.consensus.crypto.EventHasher;
import org.hiero.consensus.hashgraph.impl.test.fixtures.consensus.TestIntake;
import org.hiero.consensus.hashgraph.impl.test.fixtures.event.generator.GeneratorEventGraphSource;
import org.hiero.consensus.hashgraph.impl.test.fixtures.event.generator.GeneratorEventGraphSourceBuilder;
import org.hiero.consensus.model.event.EventDescriptorWrapper;
import org.hiero.consensus.model.event.EventOrigin;
import org.hiero.consensus.model.event.PlatformEvent;
import org.hiero.consensus.model.hashgraph.ConsensusRound;
import org.hiero.consensus.model.hashgraph.EventWindow;
import org.hiero.consensus.model.sequence.map.SequenceMap;
import org.hiero.consensus.model.sequence.map.StandardSequenceMap;
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
 * Measures the latency of {@link StandardSequenceMap} as the components on the event path use it: adding an event
 * after looking up its parents, and moving the window forward, which removes the events that became ancient.
 *
 * <p>A {@link SequenceMap} is a map whose keys each have a sequence number, and which keeps only the keys whose
 * sequence numbers fall into a window. In production, the linker, deduplication, the orphan buffer, and the tipset
 * tracker each keep one, keyed by the {@link EventDescriptorWrapper} of an event, whose sequence number is the event's
 * birth round. For every event, the orphan buffer and the tipset tracker look up each of the event's parents and then
 * add the event; the linker and deduplication only add it. After every consensus round, each of them moves the window
 * to the round's ancient threshold with {@link SequenceMap#shiftWindow(long)}, which removes the events of the birth
 * rounds below it. The benchmark measures one such map.
 *
 * <p>The calls are replayed from a recording made once per trial: the generated events of a network of
 * {@link #numNodes} nodes in the order generated, each followed by the ancient thresholds of the rounds it decided,
 * which a {@link TestIntake} computes by running the production consensus engine on copies of the events. How many
 * events share a birth round and how far the window moves thus come from consensus, not from chosen numbers. Each
 * recorded event is serialized and parsed, as gossip receives it, and hashed. A parent lookup therefore compares two
 * different descriptor objects in full, as in production, where the parent's descriptor in a received event is never
 * the object that the map holds. The hash code of every descriptor is computed once in the setup: in production, the
 * first component that uses a descriptor computes it, and the others find it cached.
 *
 * <p>For each birth round in its window, the map keeps the set of its keys, and it reuses these sets in turn as the
 * window moves. A set's table grows when keys are added and keeps its size when the set is cleared, so in a node that
 * has run for a while, adding an event does not grow a table. To measure that state, the map is created once per
 * trial, with the initial capacity of the maps of the linker, deduplication, and the orphan buffer, and the whole
 * recording is replayed into it once. Each invocation clears the map, which keeps the tables, and adds the events up
 * to the first window that removes events, outside the measured region. From then on, the map holds the non-ancient
 * events. The benchmark methods replay the rest:
 * <ul>
 *   <li>{@link #addEvent()} times every event: {@link SequenceMap#get(Object)} for each of its parents, then
 *   {@link SequenceMap#put(Object, Object)};</li>
 *   <li>{@link #shiftWindow()} times every call of {@link SequenceMap#shiftWindow(long)}, including those after a
 *   round that leaves the ancient threshold unchanged.</li>
 * </ul>
 * Each method runs the other steps of the replay paused ({@link LatencyRecorder#pause()}), so they count neither in
 * its latencies nor in its allocation. After every pass, the number of parents found, the size of the map, and the
 * start of its window must equal those of a plain {@link HashMap} that replays the same steps.
 *
 * <p>Each latency includes one clock read ({@link LatencyRecorder#lap()}). On an Apple M4 laptop, a clock read after
 * every event added 5 to 8 ns per event, 2% to 3% of the latency of {@link #addEvent()}. A call of
 * {@link #shiftWindow()} takes over half a microsecond on average, so the clock read is about 1% of it or less. The
 * read costs nearly the same for every call, so it does not hide a regression, but it makes one look slightly smaller.
 *
 * <p>{@link LatencyProfiler} reports the mean, the percentiles, and the allocation per measured call. JMH's primary
 * score divides the time of the whole pass, paused steps included, by the number of measured calls, so it is not the
 * time per call; {@code latency.mean} is. Each invocation's setup ends with {@code System.gc()}; a pass during which a
 * garbage collection runs is discarded and replaced by the next invocation.
 *
 * <p>Run with {@code -prof org.hiero.consensus.benchmark.tools.histogram.LatencyProfiler}.
 */
@State(Scope.Thread)
@Fork(
        value = 5,
        jvmArgsAppend = {"-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch"})
@Warmup(iterations = 1, time = 10)
@Measurement(iterations = 2, time = 10)
public class SequenceMapBenchmark {

    /** The seed of the event generator. The same seed produces the same recording. */
    private static final long SEED = 0;

    /** The maximum number of other parents of a generated event. */
    private static final int MAX_OTHER_PARENTS = 4;

    /** The initial capacity of the maps of the linker, deduplication, and the orphan buffer, in birth rounds. */
    private static final int MAP_CAPACITY = 1024;

    /** The number of events that {@link #addEvent()} measures per invocation. */
    private static final int ADDED_EVENTS = 10_000;

    /** The number of calls that {@link #shiftWindow()} measures per invocation. */
    private static final int WINDOW_SHIFTS = 1_000;

    /** The number of nodes in the generated network. */
    @Param({"10", "32"})
    public int numNodes;

    /** The kinds of steps in the recording. */
    private enum Kind {
        ADD_EVENT,
        SHIFT_WINDOW
    }

    /**
     * One step of the recording.
     *
     * @param kind             the kind of step
     * @param event            the event to add, for {@link Kind#ADD_EVENT}
     * @param ancientThreshold the new start of the window, for {@link Kind#SHIFT_WINDOW}
     */
    private record Step(Kind kind, PlatformEvent event, long ancientThreshold) {}

    /** The recorded steps. */
    private Step[] steps;

    /** The number of steps that each invocation's setup replays: those before the first window that removes events. */
    private int fillSteps;

    /** The number of steps after the fill that {@link #addEvent()} replays: up to its last measured event. */
    private int addEventSteps;

    /** The number of steps after the fill that {@link #shiftWindow()} replays: up to its last measured call. */
    private int shiftWindowSteps;

    /** The number of parents that the lookups of the first {@code i} steps find, at index {@code i}. */
    private long[] expectedFound;

    /** The size of the map after the first {@code i} steps, at index {@code i}. */
    private int[] expectedSize;

    /** The start of the map's window after the first {@code i} steps, at index {@code i}. */
    private long[] expectedFirst;

    /** The map, created once per trial and cleared by every invocation. */
    private SequenceMap<EventDescriptorWrapper, PlatformEvent> map;

    /** The number of parents found by the lookups in the invocation after the fill. */
    private long foundParents;

    /** The number of steps after the fill replayed in the invocation. */
    private int replayedSteps;

    /** The latency recorder of the invocation. */
    private LatencyRecorder recorder;

    /**
     * Records the steps, computes the expected results, checks that the recording is the expected one, and creates the
     * map with the tables of a map that has run for a while. This method is called once per trial.
     */
    @Setup(Level.Trial)
    public void recordSteps() {
        final GeneratorEventGraphSource generator = GeneratorEventGraphSourceBuilder.builder()
                .seed(SEED)
                .maxOtherParents(MAX_OTHER_PARENTS)
                .realSignatures(false)
                .numNodes(numNodes)
                .build();
        steps = record(generator).toArray(new Step[0]);
        fillSteps = firstMovingWindow();
        addEventSteps = stepsUpTo(Kind.ADD_EVENT, ADDED_EVENTS);
        shiftWindowSteps = stepsUpTo(Kind.SHIFT_WINDOW, WINDOW_SHIFTS);
        computeExpectedResults();

        final String summary = summarize();
        final String expected =
                switch (numNodes) {
                    case 10 ->
                        "25441 events (733 in the fill), 27.4 per birth round, 127164 parent lookups (127164 found),"
                                + " 1029 windows (moved by 0/1/more: 124/809/96), map size 642 to 809 after the fill,"
                                + " last threshold 1002, last d9a7c12d1cea0c45b6ab7781aec601db";
                    case 32 ->
                        "113179 events (3632 in the fill), 143.1 per birth round, 565730 parent lookups (565730 found),"
                                + " 1029 windows (moved by 0/1/more: 282/498/249), map size 2939 to 3632 after the fill,"
                                + " last threshold 1001, last cd9edc4957de32ce1cbcd15de3df95a0";
                    default -> throw new IllegalArgumentException("No expected recording for numNodes=" + numNodes);
                };
        if (!summary.equals(expected)) {
            throw new IllegalStateException("Expected the recording %s, but got %s".formatted(expected, summary));
        }

        map = new StandardSequenceMap<>(0, MAP_CAPACITY, true, EventDescriptorWrapper::birthRound);
        for (final Step step : steps) {
            execute(step);
        }
    }

    /**
     * Generates events until the part after the fill holds every measured call, and turns them into steps.
     *
     * @param generator the event generator
     * @return the steps
     */
    private List<Step> record(final GeneratorEventGraphSource generator) {
        final TestIntake intake = new TestIntake(new TestConfigBuilder().getOrCreateConfig(), generator.getRoster());
        final EventHasher hasher = new DefaultEventHasher();
        final long genesisThreshold = EventWindow.getGenesisEventWindow().ancientThreshold();
        final List<Step> recording = new ArrayList<>();
        boolean filled = false;
        int eventsAfterFill = 0;
        int windowsAfterFill = 0;
        while (eventsAfterFill < ADDED_EVENTS || windowsAfterFill < WINDOW_SHIFTS) {
            final PlatformEvent event = generator.next();
            recording.add(new Step(Kind.ADD_EVENT, receive(event, hasher), 0));
            if (filled) {
                eventsAfterFill++;
            }
            final int roundsBefore = intake.getConsensusRounds().size();
            intake.addEvent(event.copyGossipedData());
            final List<ConsensusRound> rounds = intake.getConsensusRounds();
            for (int i = roundsBefore; i < rounds.size(); i++) {
                final long threshold = rounds.get(i).getEventWindow().ancientThreshold();
                filled |= threshold > genesisThreshold;
                if (filled) {
                    windowsAfterFill++;
                }
                recording.add(new Step(Kind.SHIFT_WINDOW, null, threshold));
            }
        }
        return recording;
    }

    /**
     * Turns a generated event into one as gossip receives it: serialized, parsed, and hashed. Computes the hash codes
     * of its descriptor and of its parents' descriptors, which the descriptors cache.
     */
    private static PlatformEvent receive(final PlatformEvent event, final EventHasher hasher) {
        final Bytes serialized = GossipEvent.PROTOBUF.toBytes(event.getGossipEvent());
        final GossipEvent parsed;
        try {
            parsed = GossipEvent.PROTOBUF.parse(serialized);
        } catch (final ParseException e) {
            throw new IllegalStateException("Failed to parse a generated event", e);
        }
        final PlatformEvent received = hasher.hashEvent(new PlatformEvent(parsed, EventOrigin.GOSSIP));
        received.getDescriptor().hashCode();
        for (final EventDescriptorWrapper parent : received.getAllParents()) {
            parent.hashCode();
        }
        return received;
    }

    /**
     * Computes the results that the map must produce after each step, with a plain {@link HashMap} keyed by the hash
     * bytes of the events.
     */
    private void computeExpectedResults() {
        expectedFound = new long[steps.length + 1];
        expectedSize = new int[steps.length + 1];
        expectedFirst = new long[steps.length + 1];
        final Map<Bytes, Long> birthRounds = new HashMap<>();
        long first = 0;
        long found = 0;
        for (int i = 0; i < steps.length; i++) {
            final Step step = steps[i];
            if (step.kind() == Kind.ADD_EVENT) {
                final PlatformEvent event = step.event();
                for (final EventDescriptorWrapper parent : event.getAllParents()) {
                    if (birthRounds.containsKey(parent.toPbj().hash())) {
                        found++;
                    }
                }
                // the map rejects an event below its window
                if (event.getBirthRound() >= first) {
                    birthRounds.put(event.getDescriptor().toPbj().hash(), event.getBirthRound());
                }
            } else {
                final long threshold = step.ancientThreshold();
                first = threshold;
                birthRounds.values().removeIf(birthRound -> birthRound < threshold);
            }
            expectedFound[i + 1] = found;
            expectedSize[i + 1] = birthRounds.size();
            expectedFirst[i + 1] = first;
        }
    }

    /**
     * Clears the map and replays the steps up to the first window that removes events. This method is called once per
     * invocation.
     */
    @Setup(Level.Invocation)
    public void prepareInvocation() {
        map.clear();
        for (int i = 0; i < fillSteps; i++) {
            execute(steps[i]);
        }
        foundParents = 0;
        replayedSteps = 0;
        recorder = LatencyProfiler.recorder();

        // Collect the garbage of the setup and of the previous invocation now, so that it is not collected during the
        // measured calls.
        System.gc();
    }

    /**
     * Verifies the invocation: the lookups found the expected number of parents, and the map has the expected size and
     * window. This method is called once per invocation.
     */
    @TearDown(Level.Invocation)
    public void verifyInvocation() {
        final int end = fillSteps + replayedSteps;
        final long expected = expectedFound[end] - expectedFound[fillSteps];
        if (foundParents != expected) {
            throw new IllegalStateException(
                    "Expected the lookups to find %d parents, but they found %d".formatted(expected, foundParents));
        }
        if (map.getSize() != expectedSize[end]) {
            throw new IllegalStateException(
                    "Expected %d events in the map, but got %d".formatted(expectedSize[end], map.getSize()));
        }
        if (map.getFirstSequenceNumberInWindow() != expectedFirst[end]) {
            throw new IllegalStateException("Expected the window to start at %d, but it starts at %d"
                    .formatted(expectedFirst[end], map.getFirstSequenceNumberInWindow()));
        }
        recorder = null;
    }

    /**
     * Replays the recording after the fill up to the last measured event and records the latency of each event's
     * parent lookups and addition.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @OperationsPerInvocation(ADDED_EVENTS)
    public void addEvent() {
        replay(Kind.ADD_EVENT, addEventSteps);
    }

    /**
     * Replays the recording after the fill up to the last measured call of {@link SequenceMap#shiftWindow(long)} and
     * records the latency of each of these calls.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @OperationsPerInvocation(WINDOW_SHIFTS)
    public void shiftWindow() {
        replay(Kind.SHIFT_WINDOW, shiftWindowSteps);
    }

    /**
     * Replays the steps after the fill, times the steps of the measured kind, and pauses the recorder for the others.
     *
     * @param measured  the kind of step to time
     * @param stepCount the number of steps to replay
     */
    private void replay(final Kind measured, final int stepCount) {
        recorder.startMeasurement();
        recorder.pause();
        boolean paused = true;
        for (int i = fillSteps; i < fillSteps + stepCount; i++) {
            final Step step = steps[i];
            if (step.kind() == measured) {
                if (paused) {
                    recorder.resume();
                    paused = false;
                }
                execute(step);
                recorder.lap();
            } else {
                if (!paused) {
                    recorder.pause();
                    paused = true;
                }
                execute(step);
            }
        }
        if (paused) {
            recorder.resume();
        }
        recorder.stopMeasurement();
        replayedSteps = stepCount;
    }

    /** Calls the map as the recorded step does. */
    private void execute(final Step step) {
        if (step.kind() == Kind.ADD_EVENT) {
            final PlatformEvent event = step.event();
            for (final EventDescriptorWrapper parent : event.getAllParents()) {
                if (map.get(parent) != null) {
                    foundParents++;
                }
            }
            map.put(event.getDescriptor(), event);
        } else {
            map.shiftWindow(step.ancientThreshold());
        }
    }

    /** Returns the index of the first window that moves past the genesis window, and so removes events. */
    private int firstMovingWindow() {
        final long genesisThreshold = EventWindow.getGenesisEventWindow().ancientThreshold();
        for (int i = 0; i < steps.length; i++) {
            if (steps[i].kind() == Kind.SHIFT_WINDOW && steps[i].ancientThreshold() > genesisThreshold) {
                return i;
            }
        }
        throw new IllegalStateException("No window of the recording moves past the genesis window");
    }

    /** Returns the number of steps after the fill up to and including the given occurrence of a kind of step. */
    private int stepsUpTo(final Kind kind, final int occurrence) {
        int seen = 0;
        for (int i = fillSteps; i < steps.length; i++) {
            if (steps[i].kind() == kind && ++seen == occurrence) {
                return i + 1 - fillSteps;
            }
        }
        throw new IllegalStateException(
                "The recording has only %d steps of kind %s after the fill".formatted(seen, kind));
    }

    /**
     * Summarizes the recording in a line that changes with every change of the recorded steps. It also shows what sets
     * the map's work: how many events share a birth round, how far the window moves, and how large the map gets.
     */
    private String summarize() {
        int events = 0;
        int fillEvents = 0;
        long lookups = 0;
        final Set<Long> birthRounds = new HashSet<>();
        int windows = 0;
        final int[] moves = new int[3];
        long threshold = 0;
        PlatformEvent lastEvent = null;
        for (int i = 0; i < steps.length; i++) {
            final Step step = steps[i];
            if (step.kind() == Kind.ADD_EVENT) {
                events++;
                fillEvents += i < fillSteps ? 1 : 0;
                lookups += step.event().getAllParents().size();
                birthRounds.add(step.event().getBirthRound());
                lastEvent = step.event();
            } else {
                windows++;
                moves[(int) Math.min(step.ancientThreshold() - threshold, 2)]++;
                threshold = step.ancientThreshold();
            }
        }
        int minSize = Integer.MAX_VALUE;
        int maxSize = 0;
        for (int i = fillSteps; i <= steps.length; i++) {
            minSize = Math.min(minSize, expectedSize[i]);
            maxSize = Math.max(maxSize, expectedSize[i]);
        }
        return String.format(
                Locale.ROOT,
                "%d events (%d in the fill), %.1f per birth round, %d parent lookups (%d found),"
                        + " %d windows (moved by 0/1/more: %d/%d/%d), map size %d to %d after the fill,"
                        + " last threshold %d, last %s",
                events,
                fillEvents,
                (double) events / birthRounds.size(),
                lookups,
                expectedFound[steps.length],
                windows,
                moves[0],
                moves[1],
                moves[2],
                minSize,
                maxSize,
                threshold,
                lastEvent.getHash().toHex(16));
    }
}

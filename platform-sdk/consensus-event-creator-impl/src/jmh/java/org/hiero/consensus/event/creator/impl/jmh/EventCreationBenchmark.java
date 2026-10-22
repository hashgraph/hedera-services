// SPDX-License-Identifier: Apache-2.0
package org.hiero.consensus.event.creator.impl.jmh;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.config.api.Configuration;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.hiero.base.crypto.Hash;
import org.hiero.consensus.benchmark.tools.histogram.LatencyProfiler;
import org.hiero.consensus.benchmark.tools.histogram.LatencyRecorder;
import org.hiero.consensus.event.creator.impl.DefaultEventCreationManager;
import org.hiero.consensus.event.creator.impl.jmh.EventCreationRecording.Kind;
import org.hiero.consensus.event.creator.impl.jmh.EventCreationRecording.Step;
import org.hiero.consensus.event.creator.impl.tipset.TipsetEventCreator;
import org.hiero.consensus.event.creator.impl.tipset.TipsetTracker;
import org.hiero.consensus.metrics.config.MetricsConfig;
import org.hiero.consensus.metrics.platform.DefaultPlatformMetrics;
import org.hiero.consensus.metrics.platform.MetricKeyRegistry;
import org.hiero.consensus.metrics.platform.PlatformMetricsFactoryImpl;
import org.hiero.consensus.model.event.PlatformEvent;
import org.hiero.consensus.model.roster.RosterWrapper;
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
 * Measures the latency of event creation on one node, without signing: the calls of
 * {@link DefaultEventCreationManager}, with the {@link TipsetEventCreator} behind it, that create events and that
 * register peer events. Most of their time goes to the tipset computation in {@link TipsetEventCreator} and
 * {@link TipsetTracker}.
 *
 * <p>The two kinds of calls alternate in production: a node registers every event it receives and creates its own
 * events in between. The benchmark therefore replays what node 0 of a simulated network did, recorded once per trial by
 * {@link EventCreationRecording}: which peer events it registered, when it tried to create an event, when its own
 * events came back from its intake, and when it received a new event window. Every step runs at the time of the
 * recording on a fake clock, so the replay creates exactly the events of the recording, with the same hashes, which
 * the peer events in the recording refer to as parents. Each invocation replays into a new event creation manager,
 * created outside the measured region.
 *
 * <p>Each benchmark method times one kind of call and runs the other steps of the replay paused
 * ({@link LatencyRecorder#pause()}), so they count neither in its latencies nor in its allocation:
 * <ul>
 *   <li>{@link #createEvent()} times every call of {@link DefaultEventCreationManager#maybeCreateEvent()} that passes
 *   the creation rules and runs the tipset algorithm. Calls that the rules reject, mostly because of the maximum
 *   creation rate, are not replayed: they do nearly no work and change no state that later calls depend on. A call
 *   that finds no eligible parent is measured, because it runs the tipset algorithm too.</li>
 *   <li>{@link #registerEvent()} times every call of {@link DefaultEventCreationManager#registerEvent(PlatformEvent)}
 *   with a peer event. Registering a node's own events is nearly free and not measured.</li>
 * </ul>
 * Setting a new event window, about once per round, is part of neither.
 *
 * <p>Each latency includes one clock read ({@link LatencyRecorder#lap()}). A registration takes well under a
 * microsecond, so the clock read is a few percent of its latency: on an Apple M4 laptop, 3% to 5%. It is nearly the
 * same for every call, so it does not hide a regression.
 *
 * <p>Signing is replaced by a constant signature; a separate benchmark measures it. Hashing the new event is part of
 * {@link DefaultEventCreationManager#maybeCreateEvent()} and is measured.
 *
 * <p>The manager updates the production metrics implementation, {@link DefaultPlatformMetrics}, so the benchmark
 * measures the cost of the metric updates. The metrics are not started: in production, their periodic updaters and
 * snapshots run on other threads.
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
@Warmup(iterations = 1, time = 15)
@Measurement(iterations = 2, time = 15)
public class EventCreationBenchmark {

    /** The seed of the recording. The same seed produces the same recording. */
    private static final long SEED = 0;

    /** The number of creation attempts that {@link #createEvent()} measures per invocation. */
    private static final int CREATION_ATTEMPTS = 1000;

    /** The number of peer registrations that {@link #registerEvent()} measures per invocation. */
    private static final int PEER_REGISTRATIONS = 30000;

    /** The number of nodes in the simulated network, with the ping times of the first nodes of mainnet. */
    @Param({"10", "32"})
    public int numNodes;

    /** The configuration of the replayed node. */
    private Configuration configuration;

    /** The roster of the simulated network. */
    private RosterWrapper roster;

    /** The recorded steps of node 0. */
    private Step[] steps;

    /** The number of steps that {@link #createEvent()} replays: up to its last measured call. */
    private int createEventSteps;

    /** The number of steps that {@link #registerEvent()} replays: up to its last measured call. */
    private int registerEventSteps;

    /** The executor the metrics require. It stays idle, because the metrics are not started. */
    private ScheduledExecutorService metricsExecutor;

    /** The fake clock of the replay. */
    private FakeTime time;

    /**
     * The time of {@link #time}, in nanoseconds since {@link EventCreationRecording#START}. Kept here, because reading
     * it from the clock allocates a {@link Duration}.
     */
    private long elapsedNanos;

    /** The event creation manager of the replay. */
    private DefaultEventCreationManager manager;

    /** The results of the creation attempts of the invocation, null where no event was created. */
    private PlatformEvent[] created;

    /** The number of creation attempts in the invocation. */
    private int attempts;

    /** The number of steps replayed in the invocation. */
    private int replayedSteps;

    /** The latency recorder of the invocation. */
    private LatencyRecorder recorder;

    /**
     * Records node 0's steps and checks that the recording is the expected one. This method is called once per trial.
     */
    @Setup(Level.Trial)
    public void recordSteps() {
        configuration = EventCreationRecording.configuration();
        roster = EventCreationRecording.roster(SEED, numNodes);
        final List<Step> recording =
                EventCreationRecording.record(SEED, numNodes, CREATION_ATTEMPTS, PEER_REGISTRATIONS);
        steps = recording.toArray(new Step[0]);
        createEventSteps = stepsUpTo(Kind.CREATE, CREATION_ATTEMPTS);
        registerEventSteps = stepsUpTo(Kind.REGISTER_PEER, PEER_REGISTRATIONS);
        created = new PlatformEvent[count(Kind.CREATE, steps.length)];

        final String summary = summarize();
        final String expected =
                switch (numNodes) {
                    case 10 ->
                        "37255 steps, 30000 peer registrations, 3335 creation attempts (0 without event),"
                                + " 585 event windows, 166.7 s, last 9fb13b49d1ff41e5518cd7c6693afead";
                    case 32 ->
                        "33088 steps, 30932 peer registrations, 1000 creation attempts (0 without event),"
                                + " 156 event windows, 50.0 s, last 77e68494ead3f3974e46968d53b2c310";
                    default -> throw new IllegalArgumentException("No expected recording for numNodes=" + numNodes);
                };
        if (!summary.equals(expected)) {
            throw new IllegalStateException("Expected the recording %s, but got %s".formatted(expected, summary));
        }
        metricsExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Shuts down the executor of the metrics. This method is called once per trial.
     */
    @TearDown(Level.Trial)
    public void shutdownMetricsExecutor() {
        metricsExecutor.shutdownNow();
    }

    /**
     * Creates a new event creation manager for node 0, with the same seed as in the recording. This method is called
     * once per invocation.
     */
    @Setup(Level.Invocation)
    public void prepareInvocation() {
        time = new FakeTime(EventCreationRecording.START, Duration.ZERO);
        elapsedNanos = 0;
        final MetricsConfig metricsConfig = configuration.getConfigData(MetricsConfig.class);
        final DefaultPlatformMetrics metrics = new DefaultPlatformMetrics(
                roster.nodeIdAtIndex(0),
                new MetricKeyRegistry(),
                metricsExecutor,
                new PlatformMetricsFactoryImpl(metricsConfig),
                metricsConfig);
        final TipsetEventCreator creator =
                EventCreationRecording.newCreator(configuration, metrics, time, roster, SEED, 0);
        manager = EventCreationRecording.newManager(configuration, metrics, time, roster, 0, creator);
        Arrays.fill(created, null);
        attempts = 0;
        replayedSteps = 0;
        recorder = LatencyProfiler.recorder();

        // Collect the garbage of the setup and of the previous invocation now, so that it is not collected during the
        // measured calls.
        System.gc();
    }

    /**
     * Verifies that every creation attempt of the invocation had the result of the recording: the same event, by its
     * hash, or none. This method is called once per invocation.
     */
    @TearDown(Level.Invocation)
    public void verifyInvocation() {
        int attempt = 0;
        for (int i = 0; i < replayedSteps; i++) {
            if (steps[i].kind() == Kind.CREATE) {
                final Hash expected = steps[i].expectedHash();
                final Hash actual = created[attempt] == null ? null : created[attempt].getHash();
                if (!Objects.equals(expected, actual)) {
                    throw new IllegalStateException(
                            "Creation attempt %d: expected %s, but got %s".formatted(attempt, expected, actual));
                }
                attempt++;
            }
        }
        if (attempt != attempts) {
            throw new IllegalStateException("Expected %d creation attempts, but got %d".formatted(attempt, attempts));
        }
        manager = null;
        recorder = null;
    }

    /**
     * Replays the recording up to the last measured creation attempt and records the latency of each attempt.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @OperationsPerInvocation(CREATION_ATTEMPTS)
    public void createEvent() {
        replay(Kind.CREATE, createEventSteps);
    }

    /**
     * Replays the recording up to the last measured peer registration and records the latency of each registration.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @OperationsPerInvocation(PEER_REGISTRATIONS)
    public void registerEvent() {
        replay(Kind.REGISTER_PEER, registerEventSteps);
    }

    /**
     * Replays the first steps of the recording, times the steps of the measured kind, and pauses the recorder for the
     * others.
     *
     * @param measured  the kind of step to time
     * @param stepCount the number of steps to replay
     */
    private void replay(final Kind measured, final int stepCount) {
        recorder.startMeasurement();
        recorder.pause();
        boolean paused = true;
        for (int i = 0; i < stepCount; i++) {
            final Step step = steps[i];
            if (step.kind() == measured) {
                // While not paused, advancing the clock counts in the latency; it is a single addition.
                advanceTime(step);
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
                advanceTime(step);
                execute(step);
            }
        }
        if (paused) {
            recorder.resume();
        }
        recorder.stopMeasurement();
        replayedSteps = stepCount;
    }

    /** Sets the fake clock to the time of the step. */
    private void advanceTime(final Step step) {
        final long delta = step.elapsedNanos() - elapsedNanos;
        if (delta != 0) {
            time.tick(delta);
            elapsedNanos = step.elapsedNanos();
        }
    }

    /** Calls the manager as the recorded step did. */
    private void execute(final Step step) {
        switch (step.kind()) {
            case REGISTER_PEER -> manager.registerEvent(step.event());
            case CREATE -> created[attempts++] = manager.maybeCreateEvent();
            case REGISTER_SELF -> {
                final PlatformEvent selfEvent = created[attempts - 1];
                selfEvent.setNGen(step.nGen());
                selfEvent.setSequenceNumber(step.sequenceNumber());
                manager.registerEvent(selfEvent);
            }
            case SET_EVENT_WINDOW -> manager.setEventWindow(step.window());
        }
    }

    /** Returns the number of steps up to and including the given occurrence of a kind of step. */
    private int stepsUpTo(final Kind kind, final int occurrence) {
        int seen = 0;
        for (int i = 0; i < steps.length; i++) {
            if (steps[i].kind() == kind && ++seen == occurrence) {
                return i + 1;
            }
        }
        throw new IllegalStateException("The recording has only %d steps of kind %s".formatted(seen, kind));
    }

    /** Returns the number of steps of a kind among the first steps. */
    private int count(final Kind kind, final int stepCount) {
        int count = 0;
        for (int i = 0; i < stepCount; i++) {
            if (steps[i].kind() == kind) {
                count++;
            }
        }
        return count;
    }

    /** Summarizes the recording in a line that changes with every change of the recorded steps. */
    private String summarize() {
        Hash lastCreated = null;
        int withoutEvent = 0;
        for (final Step step : steps) {
            if (step.kind() == Kind.CREATE) {
                if (step.expectedHash() == null) {
                    withoutEvent++;
                } else {
                    lastCreated = step.expectedHash();
                }
            }
        }
        return "%d steps, %d peer registrations, %d creation attempts (%d without event), %d event windows, %.1f s, last %s"
                .formatted(
                        steps.length,
                        count(Kind.REGISTER_PEER, steps.length),
                        count(Kind.CREATE, steps.length),
                        withoutEvent,
                        count(Kind.SET_EVENT_WINDOW, steps.length),
                        steps[steps.length - 1].elapsedNanos() / 1e9,
                        lastCreated == null ? "none" : lastCreated.toHex(16));
    }
}

/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.platform.eventhandling;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_10_3;
import static com.swirlds.common.metrics.Metrics.INTERNAL_CATEGORY;
import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.platform.SwirldsPlatform.PLATFORM_THREAD_POOL_NAME;

import com.swirlds.base.function.CheckedConsumer;
import com.swirlds.base.state.Startable;
import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.config.EventConfig;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.stream.EventStreamManager;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.PlatformStatNames;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.status.StatusActionSubmitter;
import com.swirlds.common.system.status.actions.FreezePeriodEnteredAction;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.Stoppable;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.framework.config.QueueThreadMetricsConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.utility.Clearable;
import com.swirlds.platform.config.ThreadConfig;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.ConsensusHandlingMetrics;
import com.swirlds.platform.observers.ConsensusRoundObserver;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.stats.AverageAndMax;
import com.swirlds.platform.stats.AverageStat;
import com.swirlds.platform.stats.CycleTimingStat;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Created by a Platform to manage the flow of consensus events to SwirldState (1 instance or 3 depending on the
 * SwirldState implemented). It contains a thread queue that contains a queue of consensus events (q2) and a
 * SwirldStateManager which applies those events to the state. It also creates signed states at the appropriate times.
 */
public class ConsensusRoundHandler implements ConsensusRoundObserver, Clearable, Startable {

    /**
     * use this for all logging, as controlled by the optional data/log4j2.xml file
     */
    private static final Logger logger = LogManager.getLogger(ConsensusRoundHandler.class);

    /**
     * The name of the thread that handles consensus events
     */
    public static final String THREAD_CONS_NAME = "thread-cons";

    /**
     * The class responsible for all interactions with the swirld state
     */
    private final SwirldStateManager swirldStateManager;

    private final ConsensusHandlingMetrics consensusHandlingMetrics;

    /**
     * The queue thread that stores consensus rounds and feeds them to this class for handling.
     */
    private final QueueThread<ConsensusRound> queueThread;

    /**
     * Stores consensus events in the event stream.
     */
    private final EventStreamManager<EventImpl> eventStreamManager;

    /**
     * indicates whether a state was saved in the current freeze period. we are only saving the first state in the
     * freeze period. this variable is only used by threadCons so there is no synchronization needed
     */
    private boolean savedStateInFreeze = false;

    /**
     * a RunningHash object which calculates running hash of all consensus events so far with their transactions handled
     * by stateCons
     */
    private RunningHash eventsConsRunningHash =
            new RunningHash(new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]));

    /**
     * A queue that accepts signed states for hashing and signature collection.
     */
    private final BlockingQueue<ReservedSignedState> stateHashSignQueue;

    /**
     * Enables submitting platform status actions.
     */
    private final StatusActionSubmitter statusActionSubmitter;

    private boolean addedFirstRoundInFreeze = false;

    private final SoftwareVersion softwareVersion;

    private final Consumer<Long> roundAppliedToStateConsumer;

    /**
     * A method that blocks until an event becomes durable.
     */
    final CheckedConsumer<EventImpl, InterruptedException> waitForEventDurability;

    /**
     * The number of non-ancient rounds.
     */
    private final int roundsNonAncient;

    private final PlatformContext platformContext;

    private static final RunningAverageMetric.Config AVG_STATE_TO_HASH_SIGN_DEPTH_CONFIG =
            new RunningAverageMetric.Config(INTERNAL_CATEGORY, "stateToHashSignDepth")
                    .withDescription("average depth of the stateToHashSign queue (number of SignedStates)")
                    .withUnit("count");

    /**
     * Instantiate, but don't start any threads yet. The Platform should first instantiate the
     * {@link ConsensusRoundHandler}. Then the Platform should call start to start the queue thread.
     *
     * @param platformContext          contains various platform utilities
     * @param threadManager            responsible for creating and managing threads
     * @param selfId                   the id of this node
     * @param swirldStateManager       the swirld state manager to send events to
     * @param consensusHandlingMetrics statistics updated by {@link ConsensusRoundHandler}
     * @param eventStreamManager       the event stream manager to send consensus events to
     * @param stateHashSignQueue       the queue thread that handles hashing and collecting signatures of new
     *                                 self-signed states
     * @param waitForEventDurability   a method that blocks until an event becomes durable.
     * @param statusActionSubmitter    enables submitting of platform status actions
     * @param softwareVersion          the current version of the software
     */
    public ConsensusRoundHandler(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final NodeId selfId,
            @NonNull final SwirldStateManager swirldStateManager,
            @NonNull final ConsensusHandlingMetrics consensusHandlingMetrics,
            @NonNull final EventStreamManager<EventImpl> eventStreamManager,
            @NonNull final BlockingQueue<ReservedSignedState> stateHashSignQueue,
            @NonNull final CheckedConsumer<EventImpl, InterruptedException> waitForEventDurability,
            @NonNull final StatusActionSubmitter statusActionSubmitter,
            @NonNull final Consumer<Long> roundAppliedToStateConsumer,
            @NonNull final SoftwareVersion softwareVersion) {

        this.platformContext = Objects.requireNonNull(platformContext);
        this.roundAppliedToStateConsumer = roundAppliedToStateConsumer;
        Objects.requireNonNull(selfId, "selfId must not be null");
        this.swirldStateManager = swirldStateManager;
        this.consensusHandlingMetrics = consensusHandlingMetrics;
        this.eventStreamManager = eventStreamManager;
        this.stateHashSignQueue = stateHashSignQueue;
        this.statusActionSubmitter = Objects.requireNonNull(statusActionSubmitter);

        this.softwareVersion = softwareVersion;

        final EventConfig eventConfig = platformContext.getConfiguration().getConfigData(EventConfig.class);
        final ConsensusQueue queue = new ConsensusQueue(consensusHandlingMetrics, eventConfig.maxEventQueueForCons());
        final ThreadConfig threadConfig = platformContext.getConfiguration().getConfigData(ThreadConfig.class);

        queueThread = new QueueThreadConfiguration<ConsensusRound>(threadManager)
                .setNodeId(selfId)
                .setHandler(this::applyConsensusRoundToState)
                .setComponent(PLATFORM_THREAD_POOL_NAME)
                .setThreadName(THREAD_CONS_NAME)
                .setStopBehavior(Stoppable.StopBehavior.BLOCKING)
                .setLogAfterPauseDuration(threadConfig.logStackTracePauseDuration())
                .setMetricsConfiguration(
                        new QueueThreadMetricsConfiguration(platformContext.getMetrics()).enableBusyTimeMetric())
                .setQueue(queue)
                .build();

        roundsNonAncient = platformContext
                .getConfiguration()
                .getConfigData(ConsensusConfig.class)
                .roundsNonAncient();

        this.waitForEventDurability = waitForEventDurability;

        final AverageAndMax avgQ2ConsEvents = new AverageAndMax(
                platformContext.getMetrics(),
                INTERNAL_CATEGORY,
                PlatformStatNames.CONSENSUS_QUEUE_SIZE,
                "average number of events in the consensus queue (q2) waiting to be handled",
                FORMAT_10_3,
                AverageStat.WEIGHT_VOLATILE);
        final RunningAverageMetric avgStateToHashSignDepth =
                platformContext.getMetrics().getOrCreate(AVG_STATE_TO_HASH_SIGN_DEPTH_CONFIG);
        platformContext.getMetrics().addUpdater(() -> {
            avgQ2ConsEvents.update(queueThread.size());
            avgStateToHashSignDepth.update(getStateToHashSignSize());
        });
    }

    /**
     * Starts the queue thread.
     */
    @Override
    public void start() {
        queueThread.start();
    }

    /**
     * Stops the queue thread. For unit testing purposes only.
     */
    public void stop() {
        queueThread.stop();
    }

    /**
     * Blocks until the handling thread has handled all available work and is no longer busy. May block indefinitely if
     * more work is continually added to the queue.
     *
     * @throws InterruptedException if interrupted while waiting
     */
    public void waitUntilNotBusy() throws InterruptedException {
        queueThread.waitUntilNotBusy();
    }

    @Override
    public void clear() {
        logger.info(RECONNECT.getMarker(), "consensus handler: clearing queue thread");
        queueThread.clear();

        logger.info(RECONNECT.getMarker(), "consensus handler: clearing stateHashSignQueue queue");
        clearStateHashSignQueueThread();

        // clear running Hash info
        eventsConsRunningHash = new RunningHash(new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]));

        logger.info(RECONNECT.getMarker(), "consensus handler: ready for reconnect");
    }

    /**
     * Clears and releases any signed states in the {@code stateHashSignQueueThread} queue.
     */
    private void clearStateHashSignQueueThread() {
        ReservedSignedState signedState = stateHashSignQueue.poll();
        while (signedState != null) {
            signedState.close();
            signedState = stateHashSignQueue.poll();
        }
    }

    /**
     * Loads data from a SignedState, this is used on startup to load events and the running hash that have been
     * previously saved on disk
     *
     * @param signedState the state to load data from
     * @param isReconnect if it is true, the reservedSignedState is loaded at reconnect; if it is false, the
     *                    reservedSignedState is loaded at startup
     */
    public void loadDataFromSignedState(final SignedState signedState, final boolean isReconnect) {
        // set initialHash of the RunningHash to be the hash loaded from signed state
        eventsConsRunningHash = new RunningHash(signedState.getHashEventsCons());

        logger.info(
                STARTUP.getMarker(),
                "consensus event handler minGenFamous after startup: {}",
                () -> Arrays.toString(signedState.getMinGenInfo().toArray()));

        // get startRunningHash from reservedSignedState
        final Hash initialHash = new Hash(signedState.getHashEventsCons());
        eventStreamManager.setInitialHash(initialHash);

        logger.info(STARTUP.getMarker(), "initialHash after startup {}", () -> initialHash);
        eventStreamManager.setStartWriteAtCompleteWindow(isReconnect);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void consensusRound(final ConsensusRound consensusRound) {
        if (consensusRound == null || consensusRound.getConsensusEvents().isEmpty()) {
            // we ignore rounds with no events for now
            return;
        }

        if (!addedFirstRoundInFreeze && isRoundInFreezePeriod(consensusRound)) {
            addedFirstRoundInFreeze = true;
            statusActionSubmitter.submitStatusAction(new FreezePeriodEnteredAction(consensusRound.getRoundNum()));
        }

        addConsensusRound(consensusRound);
    }

    private boolean isRoundInFreezePeriod(final ConsensusRound round) {
        if (round.getLastEvent() == null) {
            // there are no events in this round
            return false;
        }
        return swirldStateManager.isInFreezePeriod(round.getLastEvent().getLastTransTime());
    }

    /**
     * Add a consensus event to the queue (q2) for handling.
     *
     * @param consensusRound the consensus round to add
     */
    private void addConsensusRound(final ConsensusRound consensusRound) {
        try {
            // adds this consensus event to eventStreamHelper,
            // which will put it into a queue for calculating runningHash, and a queue for event streaming when enabled
            eventStreamManager.addEvents(consensusRound.getConsensusEvents());
            // this may block until the queue isn't full
            queueThread.put(consensusRound);
        } catch (final InterruptedException e) {
            logger.error(RECONNECT.getMarker(), "addEvent interrupted");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Adds the consensus events in the round to the eventsAndGenerations queue and feeds their transactions to the
     * consensus state object (which is a SwirldState representing the effect of all consensus transactions so far). It
     * also creates the signed state if Settings.signedStateFreq > 0 and this is a round for which it should be done.
     *
     * @throws InterruptedException if this thread was interrupted while adding a signed state to the signed state
     *                              queue
     */
    private void applyConsensusRoundToState(final ConsensusRound round) throws InterruptedException {
        // If there has already been a saved state created in a freeze period, do not apply any more rounds to the
        // state until the node shuts down and comes back up (which resets this variable to false).
        if (savedStateInFreeze) {
            return;
        }

        final CycleTimingStat consensusTimingStat = consensusHandlingMetrics.getConsCycleStat();
        consensusTimingStat.startCycle();

        waitForEventDurability.accept(round.getKeystoneEvent());

        consensusTimingStat.setTimePoint(1);

        propagateConsensusData(round);

        if (round.getEventCount() > 0) {
            consensusHandlingMetrics.recordConsensusTime(round.getLastEvent().getLastTransTime());
        }
        swirldStateManager.handleConsensusRound(round);

        consensusTimingStat.setTimePoint(2);

        roundAppliedToStateConsumer.accept(round.getRoundNum());

        consensusTimingStat.setTimePoint(3);

        consensusTimingStat.setTimePoint(4);

        for (final EventImpl event : round.getConsensusEvents()) {
            if (event.getHash() == null) {
                CryptographyHolder.get().digestSync(event);
            }
        }

        // update the running hash object
        eventsConsRunningHash = round.getLastEvent().getRunningHash();

        // time point 3 to the end is misleading on its own because it is recorded even when no signed state is created
        // . For an accurate stat on how much time it takes to create a signed state, refer to
        // newSignedStateCycleTiming in Statistics
        consensusTimingStat.setTimePoint(5);

        updatePlatformState(round);

        consensusTimingStat.setTimePoint(6);

        // If the round should be signed (because the settings say so), create the signed state
        if (timeToSignState(round.getRoundNum())) {
            if (isRoundInFreezePeriod(round)) {
                // We are saving the first state in the freeze period.
                // This should never be set to false once it is true. It is reset by restarting the node
                savedStateInFreeze = true;

                // Let the swirld state manager know we are about to write the saved state for the freeze period
                swirldStateManager.savedStateInFreezePeriod();
            }
            createSignedState();
        }
        consensusTimingStat.stopCycle();
    }

    /**
     * Propagates consensus data from every event to every transaction.
     *
     * @param round the round of events to propagate data in
     */
    private void propagateConsensusData(final ConsensusRound round) {
        for (final EventImpl event : round.getConsensusEvents()) {
            event.consensusReached();
        }
    }

    private boolean timeToSignState(final long roundNum) {
        final StateConfig stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);
        return stateConfig.signedStateFreq() > 0 // and we are signing states

                // the first round should be signed and every Nth should be signed, where N is signedStateFreq
                && (roundNum == 1 || roundNum % stateConfig.signedStateFreq() == 0);
    }

    /**
     * Populate the {@link com.swirlds.platform.state.PlatformState PlatformState} with all of its needed data.
     */
    private void updatePlatformState(final ConsensusRound round) throws InterruptedException {
        final Hash runningHash = eventsConsRunningHash.getFutureHash().getAndRethrow();

        swirldStateManager
                .getConsensusState()
                .getPlatformState()
                .getPlatformData()
                .setRound(round.getRoundNum())
                .setHashEventsCons(runningHash)
                .setConsensusTimestamp(round.getLastEvent().getLastTransTime())
                .setCreationSoftwareVersion(softwareVersion)
                .setRoundsNonAncient(roundsNonAncient)
                .setSnapshot(round.getSnapshot());
    }

    private void createSignedState() throws InterruptedException {
        final CycleTimingStat ssTimingStat = consensusHandlingMetrics.getNewSignedStateCycleStat();
        ssTimingStat.startCycle();

        // create a new signed state, sign it, and send out a new transaction with the signature
        // the signed state keeps a copy that never changes.
        final State immutableStateCons = swirldStateManager.getStateForSigning();

        ssTimingStat.setTimePoint(1);

        final SignedState signedState = new SignedState(
                platformContext, immutableStateCons, "ConsensusHandler.createSignedState()", savedStateInFreeze);

        ssTimingStat.setTimePoint(2);

        stateHashSignQueue.put(signedState.reserve("ConsensusHandler.createSignedState()"));

        ssTimingStat.stopCycle();
    }

    public int getRoundsInQueue() {
        return queueThread.size();
    }

    /**
     * {@inheritDoc}
     */
    public int getStateToHashSignSize() {
        return stateHashSignQueue.size();
    }
}

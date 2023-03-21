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

import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.platform.SwirldsPlatform.PLATFORM_THREAD_POOL_NAME;

import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.stream.EventStreamManager;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.utility.Clearable;
import com.swirlds.common.utility.Startable;
import com.swirlds.platform.SettingsProvider;
import com.swirlds.platform.components.common.output.RoundAppliedToStateConsumer;
import com.swirlds.platform.config.ThreadConfig;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.ConsensusHandlingMetrics;
import com.swirlds.platform.observers.ConsensusRoundObserver;
import com.swirlds.platform.state.MinGenInfo;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.stats.CycleTimingStat;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Created by a Platform to manage the flow of consensus events to SwirldState (1 instance or 3 depending on the
 * SwirldState implemented). It contains a thread queue that contains a queue of consensus events (q2) and a
 * SwirldStateManager which applies those events to the state. It also creates signed states at the appropriate times.
 */
public class ConsensusRoundHandler implements ConsensusRoundObserver, Clearable, Startable {

    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger logger = LogManager.getLogger(ConsensusRoundHandler.class);

    /**
     * The class responsible for all interactions with the swirld state
     */
    private final SwirldStateManager swirldStateManager;

    /** Stores consensus events and round generations that need to be saved in state */
    private final SignedStateEventsAndGenerations eventsAndGenerations;

    private final SettingsProvider settings;
    private final ConsensusHandlingMetrics consensusHandlingMetrics;

    /** The queue thread that stores consensus rounds and feeds them to this class for handling. */
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

    /** number of events that have had their transactions handled by stateCons so far. */
    private final AtomicLong numEventsCons = new AtomicLong(0);

    /**
     * a RunningHash object which calculates running hash of all consensus events so far with their transactions handled
     * by stateCons
     */
    private RunningHash eventsConsRunningHash =
            new RunningHash(new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]));

    /** A queue that accepts signed states for hashing and signature collection. */
    private final BlockingQueue<SignedState> stateHashSignQueue;

    /** puts the system in a freeze state when executed */
    private final Runnable enterFreezePeriod;

    private boolean addedFirstRoundInFreeze = false;

    private final SoftwareVersion softwareVersion;

    private final RoundAppliedToStateConsumer roundAppliedToStateConsumer;

    /**
     * The number of non-ancient rounds.
     */
    private final int roundsNonAncient;

    /**
     * Instantiate, but don't start any threads yet. The Platform should first instantiate the
     * {@link ConsensusRoundHandler}. Then the Platform should call start to start the queue thread.
     *
     * @param platformContext          contains various platform utilities
     * @param threadManager            responsible for creating and managing threads
     * @param selfId                   the id of this node
     * @param settings                 a provider of static settings
     * @param swirldStateManager       the swirld state manager to send events to
     * @param consensusHandlingMetrics statistics updated by {@link ConsensusRoundHandler}
     * @param eventStreamManager       the event stream manager to send consensus events to
     * @param stateHashSignQueue       the queue thread that handles hashing and collecting signatures of new
     *                                 self-signed states
     * @param enterFreezePeriod        puts the system in a freeze state when executed
     * @param softwareVersion          the current version of the software
     */
    public ConsensusRoundHandler(
            final PlatformContext platformContext,
            final ThreadManager threadManager,
            final long selfId,
            final SettingsProvider settings,
            final SwirldStateManager swirldStateManager,
            final ConsensusHandlingMetrics consensusHandlingMetrics,
            final EventStreamManager<EventImpl> eventStreamManager,
            final BlockingQueue<SignedState> stateHashSignQueue,
            final Runnable enterFreezePeriod,
            final RoundAppliedToStateConsumer roundAppliedToStateConsumer,
            final SoftwareVersion softwareVersion) {

        this.roundAppliedToStateConsumer = roundAppliedToStateConsumer;

        this.settings = settings;
        this.swirldStateManager = swirldStateManager;
        this.consensusHandlingMetrics = consensusHandlingMetrics;
        this.eventStreamManager = eventStreamManager;
        this.stateHashSignQueue = stateHashSignQueue;
        this.softwareVersion = softwareVersion;
        this.enterFreezePeriod = enterFreezePeriod;

        final ConsensusConfig consensusConfig =
                platformContext.getConfiguration().getConfigData(ConsensusConfig.class);

        eventsAndGenerations = new SignedStateEventsAndGenerations(consensusConfig);
        final ConsensusQueue queue = new ConsensusQueue(consensusHandlingMetrics, settings.getMaxEventQueueForCons());
        queueThread = new QueueThreadConfiguration<ConsensusRound>(threadManager)
                .setNodeId(selfId)
                .setHandler(this::applyConsensusRoundToState)
                .setComponent(PLATFORM_THREAD_POOL_NAME)
                .setThreadName("thread-cons")
                .setStopBehavior(swirldStateManager.getStopBehavior())
                // DO NOT turn the line below into a lambda reference because it will execute the getter, not the
                // runnable returned by the getter.
                .setWaitForItemRunnable(swirldStateManager.getConsensusWaitForWorkRunnable())
                .setLogAfterPauseDuration(ConfigurationHolder.getInstance()
                        .get()
                        .getConfigData(ThreadConfig.class)
                        .logStackTracePauseDuration())
                .setQueue(queue)
                .build();

        roundsNonAncient = platformContext
                .getConfiguration()
                .getConfigData(ConsensusConfig.class)
                .roundsNonAncient();
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

    @Override
    public void clear() {
        logger.info(RECONNECT.getMarker(), "consensus handler: clearing queue thread");
        queueThread.clear();

        logger.info(RECONNECT.getMarker(), "consensus handler: clearing stateHashSignQueue queue");
        clearStateHashSignQueueThread();

        // clear running Hash info
        eventsConsRunningHash = new RunningHash(new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]));
        numEventsCons.set(0);

        eventsAndGenerations.clear();
        logger.info(RECONNECT.getMarker(), "consensus handler: ready for reconnect");
    }

    /**
     * Clears and releases any signed states in the {@code stateHashSignQueueThread} queue.
     */
    private void clearStateHashSignQueueThread() {
        SignedState signedState = stateHashSignQueue.poll();
        while (signedState != null) {
            signedState.release();
            signedState = stateHashSignQueue.poll();
        }
    }

    /**
     * Loads data from a SignedState, this is used on startup to load events and the running hash that have been
     * previously saved on disk
     *
     * @param signedState the state to load data from
     * @param isReconnect if it is true, the signedState is loaded at reconnect; if it is false, the signedState is
     *                    loaded at startup
     */
    public void loadDataFromSignedState(final SignedState signedState, final boolean isReconnect) {
        eventsAndGenerations.loadDataFromSignedState(signedState);
        // nodes not reconnecting expired eventsAndGenerations right after creating a signed state
        // we expire here right after receiving it to align ourselves with other nodes for the next round
        eventsAndGenerations.expire();

        // set initialHash of the RunningHash to be the hash loaded from signed state
        eventsConsRunningHash = new RunningHash(signedState.getHashEventsCons());

        numEventsCons.set(signedState.getNumEventsCons());

        logger.info(
                STARTUP.getMarker(),
                "consensus event handler minGenFamous after startup: {}",
                () -> Arrays.toString(signedState.getMinGenInfo().toArray()));

        // get startRunningHash from signedState
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
        if (consensusRound == null) {
            return;
        }

        if (!addedFirstRoundInFreeze && isRoundInFreezePeriod(consensusRound)) {
            addedFirstRoundInFreeze = true;
            enterFreezePeriod.run();
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
    public void addConsensusRound(final ConsensusRound consensusRound) {
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

        propagateConsensusData(round);

        consensusTimingStat.setTimePoint(1);

        swirldStateManager.handleConsensusRound(round);

        consensusTimingStat.setTimePoint(2);

        roundAppliedToStateConsumer.roundAppliedToState(round.getRoundNum());

        consensusTimingStat.setTimePoint(3);

        eventsAndGenerations.addEvents(round.getConsensusEvents());

        // count events that have had all their transactions handled by stateCons
        numEventsCons.updateAndGet(
                prevValue -> prevValue + round.getConsensusEvents().size());

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

        // remove events and generations that are not needed
        eventsAndGenerations.expire();
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
        return settings.getSignedStateFreq() > 0 // and we are signing states

                // the first round should be signed and every Nth should be signed, where N is signedStateFreq
                && (roundNum == 1 || roundNum % settings.getSignedStateFreq() == 0);
    }

    /**
     * Populate the {@link com.swirlds.platform.state.PlatformState PlatformState} with all of its needed data.
     */
    private void updatePlatformState(final ConsensusRound round) throws InterruptedException {
        final Hash runningHash = eventsConsRunningHash.getFutureHash().getAndRethrow();
        final EventImpl[] events = eventsAndGenerations.getEventsForSignedState();
        final List<MinGenInfo> minGen = eventsAndGenerations.getMinGenForSignedState();

        swirldStateManager
                .getConsensusState()
                .getPlatformState()
                .getPlatformData()
                .setRound(round.getRoundNum())
                .setNumEventsCons(numEventsCons.get())
                .setHashEventsCons(runningHash)
                .setEvents(events)
                .setConsensusTimestamp(round.getLastEvent().getLastTransTime())
                .setMinGenInfo(minGen)
                .setCreationSoftwareVersion(softwareVersion)
                .setRoundsNonAncient(roundsNonAncient);
    }

    private void createSignedState() throws InterruptedException {
        final CycleTimingStat ssTimingStat = consensusHandlingMetrics.getNewSignedStateCycleStat();
        ssTimingStat.startCycle();

        // create a new signed state, sign it, and send out a new transaction with the signature
        // the signed state keeps a copy that never changes.
        final State immutableStateCons = swirldStateManager.getStateForSigning();

        ssTimingStat.setTimePoint(1);

        final SignedState signedState = new SignedState(immutableStateCons, savedStateInFreeze);

        ssTimingStat.setTimePoint(2);

        stateHashSignQueue.put(signedState);

        ssTimingStat.stopCycle();
    }

    public void addMinGenInfo(final long round, final long minGeneration) {
        eventsAndGenerations.addRoundGeneration(round, minGeneration);
    }

    public int getRoundsInQueue() {
        return queueThread.size();
    }

    public int getSignedStateEventsSize() {
        return eventsAndGenerations.getNumberOfEvents();
    }

    /**
     * {@inheritDoc}
     */
    public int getStateToHashSignSize() {
        return stateHashSignQueue.size();
    }

    public long getNumEventsInQueue() {
        return queueThread.size();
    }
}

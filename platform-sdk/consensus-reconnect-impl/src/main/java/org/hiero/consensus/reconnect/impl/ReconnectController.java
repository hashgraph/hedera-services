// SPDX-License-Identifier: Apache-2.0
package org.hiero.consensus.reconnect.impl;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.RECONNECT;
import static com.swirlds.logging.legacy.LogMarker.STATE_HASH;
import static java.util.Objects.requireNonNull;
import static org.hiero.consensus.platformstate.PlatformStateUtils.creationSoftwareVersionOf;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.roster.Roster;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.config.api.Configuration;
import com.swirlds.logging.legacy.payload.ReconnectFailurePayload;
import com.swirlds.logging.legacy.payload.ReconnectFailurePayload.CauseOfFailure;
import com.swirlds.platform.listeners.ReconnectCompleteListener;
import com.swirlds.platform.listeners.ReconnectCompleteNotification;
import com.swirlds.platform.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.platform.listeners.StateWriteToDiskCompleteNotification;
import com.swirlds.platform.state.ConsensusStateEventHandler;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.state.State;
import com.swirlds.state.StateLifecycleManager;
import com.swirlds.state.merkle.VirtualMapState;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hiero.base.concurrent.BlockingResourceProvider;
import org.hiero.base.concurrent.ResettableLatch;
import org.hiero.base.concurrent.locks.locked.LockedResource;
import org.hiero.base.crypto.Hash;
import org.hiero.consensus.ConsensusLayerLifecycleManager;
import org.hiero.consensus.gossip.ReservedSignedStateResult;
import org.hiero.consensus.main.model.NodeId;
import org.hiero.consensus.reconnect.config.ReconnectConfig;
import org.hiero.consensus.roster.RosterRetriever;
import org.hiero.consensus.state.SavedStateController;
import org.hiero.consensus.state.SignedStateFileReader;
import org.hiero.consensus.state.signed.ReservedSignedState;
import org.hiero.consensus.state.signed.SignedState;
import org.hiero.consensus.system.SystemExitCode;
import org.hiero.consensus.system.SystemExitUtils;

/**
 * Orchestrates the reconnect process when a node falls behind.
 *
 * <p>Once started the controller runs in a continuous loop, waiting for the node to fall behind, then attempting
 * to reconnect until successful or until configured thresholds are exceeded. Each reconnect attempt involves preparing
 * the current state, obtaining a new state from a peer, validating it, and loading it into the platform components.
 *
 * <p>This class blocks the caller until the fall behind condition is detected.
 * Callers are responsible to call this in a separated thread.
 *
 * @see ResettableLatch
 * @see BlockingResourceProvider
 */
public class ReconnectController implements StateWriteToDiskCompleteListener, Runnable {

    private static final Logger logger = LogManager.getLogger(ReconnectController.class);

    private final Roster roster;
    private final SignedStateValidator signedStateValidator;
    private final Platform platform;
    private final StateLifecycleManager<VirtualMapState, VirtualMap> stateLifecycleManager;
    private final SavedStateController savedStateController;
    private final ConsensusStateEventHandler consensusStateEventHandler;
    private final BlockingResourceProvider<ReservedSignedStateResult> peerReservedSignedStateResultProvider;
    private final NodeId selfId;
    private final ReconnectConfig reconnectConfig;
    private final ConsensusLayerLifecycleManager consensusLayerLifecycleManager;
    private final ResettableLatch behindLatch;
    private final AtomicBoolean run = new AtomicBoolean(true);
    private final ReconnectCoordinator reconnectCoordinator;
    private final NotificationEngine notificationEngine;
    private volatile long reconnectRoundNumber;
    private final AtomicBoolean waitingForReconnectStateOnDisk = new AtomicBoolean(false);
    private volatile ReservedSignedState acquiredReservedState;

    public ReconnectController(
            @NonNull final Configuration configuration,
            @NonNull final Roster roster,
            @NonNull final Platform platform,
            @NonNull final ConsensusLayerLifecycleManager consensusLayerLifecycleManager,
            @NonNull final ReconnectCoordinator reconnectCoordinator,
            @NonNull final StateLifecycleManager<VirtualMapState, VirtualMap> stateLifecycleManager,
            @NonNull final SavedStateController savedStateController,
            @NonNull final ConsensusStateEventHandler consensusStateEventHandler,
            @NonNull final BlockingResourceProvider<ReservedSignedStateResult> peerReservedSignedStateResultProvider,
            @NonNull final NodeId selfId,
            @NonNull final ResettableLatch behindLatch,
            @NonNull final SignedStateValidator signedStateValidator,
            @NonNull final NotificationEngine notificationEngine) {
        this.roster = requireNonNull(roster);
        this.peerReservedSignedStateResultProvider = requireNonNull(peerReservedSignedStateResultProvider);
        this.behindLatch = requireNonNull(behindLatch);
        this.signedStateValidator = requireNonNull(signedStateValidator);
        this.reconnectConfig = configuration.getConfigData(ReconnectConfig.class);
        this.platform = requireNonNull(platform);
        this.consensusLayerLifecycleManager = requireNonNull(consensusLayerLifecycleManager);
        this.reconnectCoordinator = reconnectCoordinator;
        this.stateLifecycleManager = requireNonNull(stateLifecycleManager);
        this.savedStateController = requireNonNull(savedStateController);
        this.consensusStateEventHandler = requireNonNull(consensusStateEventHandler);
        this.notificationEngine = requireNonNull(notificationEngine);
        this.selfId = selfId;
    }

    /**
     * Initiates graceful shutdown of the controller. Does not guarantee stopping if the thread where the controller is
     * running is blocked
     */
    public void stopReconnectLoop() {
        run.set(false);
    }

    /**
     * Once started, it manages the lifecycle of reconnection:
     * <ul>
     *   <li>Waits for the consensus layer to report that the node is behind, via the behind latch</li>
     *   <li>Acquiring and validating signed states from peer nodes, while the consensus layer (and its gossip) keeps
     *       running so that another teacher can be found if an attempt fails</li>
     *   <li>Destroying the consensus layer once a valid state has been acquired</li>
     *   <li>Loading the validated state into the platform</li>
     *   <li>Managing retry logic with configurable limits</li>
     * </ul>
     */
    @Override
    public void run() {
        logger.info(RECONNECT.getMarker(), "Starting the ReconnectController");
        try {
            while (run.get()) {
                behindLatch.awaitArmed(); // Block until the consensus layer reports the node is behind
                exitIfReconnectDisabled();
                logger.info(RECONNECT.getMarker(), "Node has fallen behind, preparing for reconnect");

                final State currentState = stateLifecycleManager.getMutableState();
                currentState.getHash(); // hash the state
                int failedReconnectsInARow = 0;
                do {
                    final AttemptReconnectResult result = attemptReconnect(currentState);
                    if (result.success()) {
                        loadReconnectState(requireNonNull(result.acquiredState()));
                        // The old consensus layer has been destroyed, so it can no longer re-arm the latch
                        behindLatch.disarm();
                        logger.info(RECONNECT.getMarker(), "Reconnect state loaded");
                        break;
                    }
                    exitIfMaxRetriesOrWait(++failedReconnectsInARow, result.throwable());
                } while (run.get());
            }
        } catch (final RuntimeException e) {
            logger.error(
                    RECONNECT.getMarker(),
                    () -> new ReconnectFailurePayload(
                            "Unexpected error occurred while reconnecting", CauseOfFailure.ERROR),
                    e);
            // in the future we might want to consider a graceful shutdown hook instead of exitSystem
            SystemExitUtils.exitSystem(SystemExitCode.RECONNECT_FAILURE);
        } catch (final InterruptedException e) {
            if (run.get()) {
                logger.error(
                        RECONNECT.getMarker(),
                        () -> new ReconnectFailurePayload("Thread was interrupted unexpectedly", CauseOfFailure.ERROR),
                        e);
                Thread.currentThread().interrupt();
                // in the future we might want to consider a graceful shutdown hook instead of exitSystem
                SystemExitUtils.exitSystem(SystemExitCode.RECONNECT_FAILURE);
            }
        }
    }

    /**
     * One attempt to acquire and validate a state from a peer. Nothing in this method may modify the platform, so
     * that a failed attempt can be retried with another teacher.
     *
     * @return the acquired state, reserved for the caller, on success
     */
    private AttemptReconnectResult attemptReconnect(@NonNull final State currentState) throws InterruptedException {
        // This is a direct connection with the protocols at Gossip component.
        // reservedStateResource is a blocking data structure that will provide a signed state from one of the peers
        // At the same time this code is evaluated, the ReconnectStateProtocol is being executed
        // which will select a peer to receive a state (only one from all the ones that reported we are behind)
        // Once the transferred is complete this peerReservedSignedStatePromise will be notified and this code will be
        // unblocked
        logger.info(RECONNECT.getMarker(), "Waiting for a state to be obtained from a peer");
        try (final LockedResource<ReservedSignedStateResult> reservedStateResource =
                        requireNonNull(peerReservedSignedStateResultProvider.waitForResource());
                final ReservedSignedStateResult result = requireNonNull(reservedStateResource.getResource())) {
            if (result.isError()) {
                return AttemptReconnectResult.error(requireNonNull(result.throwable()));
            }
            logger.info(RECONNECT.getMarker(), "A state was obtained from a peer");

            // We validate the data in the peer state relative to our current state
            final SignedStateValidationData data = new SignedStateValidationData(currentState, roster);
            final SignedState peerState = result.reservedSignedState().get();

            SignedStateFileReader.registerServiceStates(peerState);
            signedStateValidator.validate(peerState, roster, data);
            validateRoster(peerState);
            logger.info(RECONNECT.getMarker(), "The state obtained from a peer was validated");

            // Reserve the state before the resource is closed, closing it releases the learner's reservation.
            // Closing the resource also unblocks the learner's gossip thread, which is waiting in provide(). That must
            // happen before the consensus layer is destroyed, otherwise destroying gossip would wait on that thread.
            return AttemptReconnectResult.ok(
                    result.reservedSignedState().getAndReserve("ReconnectController.acquiredState"));
        } catch (final RuntimeException e) {
            return AttemptReconnectResult.error(e);
        }
    }

    /**
     * Destroys the consensus layer and loads the acquired state into the platform. Failures here are not retryable,
     * since the consensus layer (and the gossip that finds teachers) has already been destroyed.
     *
     * @param acquiredState the validated state, reserved for this controller
     */
    private void loadReconnectState(@NonNull final ReservedSignedState acquiredState) {
        acquiredReservedState = acquiredState;
        final SignedState signedState = acquiredState.get();
        reconnectRoundNumber = signedState.getRound();
        // Must be set before the state is sent to be written to disk, or the write may complete before we listen
        waitingForReconnectStateOnDisk.set(true);

        logger.info(RECONNECT.getMarker(), "Destroying the consensus layer");
        consensusLayerLifecycleManager.get().destroy();

        initializeState(signedState);
        savedStateController.reconnectStateReceived(
                acquiredState.getAndReserve("savedStateController.reconnectStateReceived"));
        reconnectCoordinator.loadReconnectState(signedState);
    }

    @Override
    public void notify(@NonNull final StateWriteToDiskCompleteNotification notification) {
        if (notification.getRoundNumber() < reconnectRoundNumber) {
            return;
        }

        if (waitingForReconnectStateOnDisk.compareAndExchange(true, false)) {
            // Notify any listeners that the reconnect has been completed
            sendReconnectCompleteNotification();
            consensusLayerLifecycleManager.recreateConsensusLayer(acquiredReservedState);
            acquiredReservedState.close();
        }
    }

    private void sendReconnectCompleteNotification() {
        final SignedState acquiredState = acquiredReservedState.get();
        final ReconnectCompleteNotification notification = new ReconnectCompleteNotification(
                acquiredState.getRound(), acquiredState.getConsensusTimestamp(), acquiredState.getState());
        notificationEngine.dispatch(ReconnectCompleteListener.class, notification);
    }

    /**
     * Verifies that the platform roster matches the roster in the state received from a peer.
     */
    private void validateRoster(@NonNull final SignedState signedState) {
        final Roster stateRoster = RosterRetriever.retrieveActive(signedState.getState(), signedState.getRound());
        if (!roster.equals(stateRoster)) {
            throw new IllegalStateException("Current roster and state-based roster do not contain the same nodes "
                    + " (currentRoster=" + Roster.JSON.toJSON(roster) + ") (stateRoster="
                    + Roster.JSON.toJSON(stateRoster) + ")");
        }
    }

    /**
     * Initializes the received signed state.
     */
    private void initializeState(@NonNull final SignedState signedState) {
        // the state was received, so now we load its data into different objects
        logger.info(STATE_HASH.getMarker(), "RECONNECT: loadState: reloading state");
        logger.debug(RECONNECT.getMarker(), "`loadState` : reloading state");
        final Hash reconnectHash = signedState.getState().getHash();
        final VirtualMapState state = signedState.getState();
        final SemanticVersion creationSoftwareVersion = creationSoftwareVersionOf(state);
        consensusStateEventHandler.onStateInitialized(state, platform, InitTrigger.RECONNECT, creationSoftwareVersion);

        if (!Objects.equals(signedState.getState().getHash(), reconnectHash)) {
            throw new IllegalStateException(
                    "State hash is not permitted to change during a reconnect init() call. Previous hash was "
                            + reconnectHash + ", new hash is "
                            + signedState.getState().getHash());
        }

        stateLifecycleManager.initWithState(state);
    }

    /**
     * Kills the node if we reached the max reconnect retries, otherwise wait until minimumTimeBetweenReconnects
     */
    private void exitIfMaxRetriesOrWait(final int failedReconnectsInARow, final @Nullable Throwable throwable)
            throws InterruptedException {
        if (failedReconnectsInARow >= reconnectConfig.maximumReconnectFailuresBeforeShutdown()) {
            logger.error(
                    EXCEPTION.getMarker(),
                    () -> new ReconnectFailurePayload(
                            "Too many reconnect failures in a row (%s), killing node"
                                    .formatted(reconnectConfig.maximumReconnectFailuresBeforeShutdown()),
                            CauseOfFailure.ERROR),
                    throwable);
            SystemExitUtils.exitSystem(SystemExitCode.RECONNECT_FAILURE);
        } else {
            if (throwable != null) {
                logger.warn(
                        EXCEPTION.getMarker(),
                        () -> new ReconnectFailurePayload(
                                "Unexpected exception during reconnect", CauseOfFailure.ERROR),
                        throwable);
            }
            logger.info(
                    RECONNECT.getMarker(),
                    "Reconnect (try {} of {}) failed with error. Will try again after {}.",
                    failedReconnectsInARow,
                    reconnectConfig.maximumReconnectFailuresBeforeShutdown(),
                    reconnectConfig.minimumTimeBetweenReconnects());
            Thread.sleep(reconnectConfig.minimumTimeBetweenReconnects().toMillis());
        }
    }

    /**
     * Kills the node if the reconnect time window has elapsed.
     */
    private void exitIfReconnectDisabled() {
        if (!reconnectConfig.active()) {
            logger.error(
                    RECONNECT.getMarker(), "Node {} has fallen behind, reconnect is disabled, will die", selfId.id());
            SystemExitUtils.exitSystem(SystemExitCode.BEHIND_RECONNECT_DISABLED);
        }
    }

    private record AttemptReconnectResult(
            boolean success,
            @Nullable ReservedSignedState acquiredState,
            @Nullable Throwable throwable) {
        static AttemptReconnectResult ok(@NonNull final ReservedSignedState acquiredState) {
            return new AttemptReconnectResult(true, acquiredState, null);
        }

        static AttemptReconnectResult error(@NonNull final Throwable error) {
            return new AttemptReconnectResult(false, null, error);
        }
    }
}

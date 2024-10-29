/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.reconnect;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.RECONNECT;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.protocol.Protocol;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedStateValidator;
import com.swirlds.platform.system.status.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hiero.consensus.gossip.FallenBehindManager;

/**
 * Implements the reconnect protocol over a bidirectional network
 */
public class ReconnectProtocol implements Protocol {

    private static final Logger logger = LogManager.getLogger(ReconnectProtocol.class);

    private final NodeId peerId;
    private final ReconnectThrottle teacherThrottle;
    private final Supplier<ReservedSignedState> lastCompleteSignedState;
    private final Duration reconnectSocketTimeout;
    private final ReconnectMetrics reconnectMetrics;
    private final ReconnectController reconnectController;
    private final SignedStateValidator validator;
    private InitiatedBy initiatedBy = InitiatedBy.NO_ONE;
    private final ThreadManager threadManager;
    private final FallenBehindManager fallenBehindManager;

    /**
     * Provides the platform status.
     */
    private final Supplier<PlatformStatus> platformStatusSupplier;

    private final Configuration configuration;
    private ReservedSignedState teacherState;
    /**
     * A rate limited logger for when rejecting teacher role due to state being null.
     */
    private final RateLimitedLogger stateNullLogger;
    /**
     * A rate limited logger for when rejecting teacher role due to state being incomplete.
     */
    private final RateLimitedLogger stateIncompleteLogger;
    /**
     * A rate limited logger for when rejecting teacher role due to falling behind.
     */
    private final RateLimitedLogger fallenBehindLogger;
    /**
     * A rate limited logger for when rejecting teacher role due to not having a status of ACTIVE
     */
    private final RateLimitedLogger notActiveLogger;

    private final Time time;
    private final PlatformContext platformContext;

    /**
     * @param threadManager           responsible for creating and managing threads
     * @param peerId                  the ID of the peer we are communicating with
     * @param teacherThrottle         restricts reconnects as a teacher
     * @param lastCompleteSignedState provides the latest completely signed state
     * @param reconnectSocketTimeout  the socket timeout to use when executing a reconnect
     * @param reconnectMetrics        tracks reconnect metrics
     * @param reconnectController     controls reconnecting as a learner
     * @param fallenBehindManager     maintains this node's behind status
     * @param platformStatusSupplier  provides the platform status
     * @param configuration           platform configuration
     * @param time                    the time object to use
     */
    public ReconnectProtocol(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final NodeId peerId,
            @NonNull final ReconnectThrottle teacherThrottle,
            @NonNull final Supplier<ReservedSignedState> lastCompleteSignedState,
            @NonNull final Duration reconnectSocketTimeout,
            @NonNull final ReconnectMetrics reconnectMetrics,
            @NonNull final ReconnectController reconnectController,
            @NonNull final SignedStateValidator validator,
            @NonNull final FallenBehindManager fallenBehindManager,
            @NonNull final Supplier<PlatformStatus> platformStatusSupplier,
            @NonNull final Configuration configuration,
            @NonNull final Time time) {

        this.platformContext = Objects.requireNonNull(platformContext);
        this.threadManager = Objects.requireNonNull(threadManager);
        this.peerId = Objects.requireNonNull(peerId);
        this.teacherThrottle = Objects.requireNonNull(teacherThrottle);
        this.lastCompleteSignedState = Objects.requireNonNull(lastCompleteSignedState);
        this.reconnectSocketTimeout = Objects.requireNonNull(reconnectSocketTimeout);
        this.reconnectMetrics = Objects.requireNonNull(reconnectMetrics);
        this.reconnectController = Objects.requireNonNull(reconnectController);
        this.validator = Objects.requireNonNull(validator);
        this.fallenBehindManager = Objects.requireNonNull(fallenBehindManager);
        this.platformStatusSupplier = Objects.requireNonNull(platformStatusSupplier);
        this.configuration = Objects.requireNonNull(configuration);
        Objects.requireNonNull(time);

        final Duration minimumTimeBetweenReconnects =
                configuration.getConfigData(ReconnectConfig.class).minimumTimeBetweenReconnects();

        stateNullLogger = new RateLimitedLogger(logger, time, minimumTimeBetweenReconnects);
        stateIncompleteLogger = new RateLimitedLogger(logger, time, minimumTimeBetweenReconnects);
        fallenBehindLogger = new RateLimitedLogger(logger, time, minimumTimeBetweenReconnects);
        notActiveLogger = new RateLimitedLogger(logger, time, minimumTimeBetweenReconnects);
        this.time = Objects.requireNonNull(time);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldInitiate() {
        // if this neighbor has not told me I have fallen behind, I will not reconnect with him
        if (!fallenBehindManager.shouldReconnectFrom(peerId)) {
            return false;
        }

        // if a permit is acquired, it will be released by either initiateFailed or runProtocol
        final boolean acquiredPermit = reconnectController.acquireLearnerPermit();
        if (acquiredPermit) {
            initiatedBy = InitiatedBy.SELF;
        }
        return acquiredPermit;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initiateFailed() {
        reconnectController.cancelLearnerPermit();
        initiatedBy = InitiatedBy.NO_ONE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldAccept() {
        // we should not be the teacher if we have fallen behind
        if (fallenBehindManager.hasFallenBehind()) {
            fallenBehindLogger.info(
                    RECONNECT.getMarker(),
                    "Rejecting reconnect request from node {} because this node has fallen behind",
                    peerId);
            reconnectRejected();
            return false;
        }

        // only teach if the platform is active
        if (platformStatusSupplier.get() != PlatformStatus.ACTIVE) {
            notActiveLogger.info(
                    RECONNECT.getMarker(),
                    "Rejecting reconnect request from node {} because this node isn't ACTIVE",
                    peerId);
            reconnectRejected();
            return false;
        }

        // Check if we have a state that is legal to send to a learner.
        teacherState = lastCompleteSignedState.get();

        if (teacherState == null || teacherState.isNull()) {
            stateNullLogger.info(
                    RECONNECT.getMarker(),
                    "Rejecting reconnect request from node {} due to lack of a fully signed state",
                    peerId);
            reconnectRejected();
            return false;
        }

        if (!teacherState.get().isComplete()) {
            // this is only possible if signed state manager violates its contractual obligations
            stateIncompleteLogger.error(
                    EXCEPTION.getMarker(),
                    "Rejecting reconnect request from node {} due to lack of a fully signed state."
                            + " The signed state manager attempted to provide a state that was not"
                            + " fully signed, which should not be possible.",
                    peerId);
            reconnectRejected();
            return false;
        }

        // we should not become a learner while we are teaching
        // this can happen if we fall behind while we are teaching
        // in this case, we want to finish teaching before we start learning
        // so we acquire the learner permit and release it when we are done teaching
        if (!reconnectController.blockLearnerPermit()) {
            reconnectRejected();
            return false;
        }

        // Check if a reconnect with the learner is permitted by the throttle.
        final boolean reconnectPermittedByThrottle = teacherThrottle.initiateReconnect(peerId);
        if (!reconnectPermittedByThrottle) {
            reconnectRejected();
            reconnectController.cancelLearnerPermit();
            return false;
        }

        initiatedBy = InitiatedBy.PEER;
        return true;
    }

    /**
     * Called when we reject a reconnect as a teacher
     */
    private void reconnectRejected() {
        if (teacherState != null) {
            teacherState.close();
            teacherState = null;
        }
        reconnectMetrics.recordReconnectRejection(peerId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void acceptFailed() {
        teacherState.close();
        teacherState = null;
        teacherThrottle.reconnectAttemptFinished();
        // cancel the permit acquired in shouldAccept() so that we can start learning if we need to
        reconnectController.cancelLearnerPermit();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean acceptOnSimultaneousInitiate() {
        // if both nodes fall behind, it makes no sense to reconnect with each other
        // also, it would not be clear who the teacher and who the learner is
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void runProtocol(final Connection connection)
            throws NetworkProtocolException, IOException, InterruptedException {
        try {
            switch (initiatedBy) {
                case PEER -> teacher(connection);
                case SELF -> learner(connection);
                default -> throw new NetworkProtocolException(
                        "runProtocol() called but it is unclear who the teacher and who the learner is");
            }
        } finally {
            initiatedBy = InitiatedBy.NO_ONE;
        }
    }

    /**
     * Perform reconnect as the learner.
     *
     * @param connection the connection to use for the reconnect
     */
    private void learner(final Connection connection) throws InterruptedException {
        reconnectController.setStateValidator(validator);
        reconnectController.provideLearnerConnection(connection);
    }

    /**
     * Perform reconnect as the teacher.
     *
     * @param connection the connection to use for the reconnect
     */
    private void teacher(final Connection connection) {
        try (final ReservedSignedState state = teacherState) {
            new ReconnectTeacher(
                            platformContext,
                            time,
                            threadManager,
                            connection,
                            reconnectSocketTimeout,
                            connection.getSelfId(),
                            connection.getOtherId(),
                            state.get().getRound(),
                            reconnectMetrics,
                            configuration)
                    .execute(state.get());
        } finally {
            teacherThrottle.reconnectAttemptFinished();
            teacherState = null;
            // cancel the permit acquired in shouldAccept() so that we can start learning if we need to
            reconnectController.cancelLearnerPermit();
        }
    }

    private enum InitiatedBy {
        NO_ONE,
        SELF,
        PEER
    }
}

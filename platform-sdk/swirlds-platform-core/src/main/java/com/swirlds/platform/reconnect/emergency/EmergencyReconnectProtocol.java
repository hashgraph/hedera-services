/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.reconnect.emergency;

import static com.swirlds.logging.legacy.LogMarker.RECONNECT;

import com.swirlds.base.time.Time;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.notification.listeners.ReconnectCompleteListener;
import com.swirlds.common.notification.listeners.ReconnectCompleteNotification;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.status.StatusActionSubmitter;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.gossip.FallenBehindManager;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.protocol.Protocol;
import com.swirlds.platform.reconnect.ReconnectController;
import com.swirlds.platform.reconnect.ReconnectThrottle;
import com.swirlds.platform.recovery.EmergencyRecoveryManager;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedStateFinder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implements the emergency reconnect protocol over a bidirectional network
 */
public class EmergencyReconnectProtocol implements Protocol {
    private static final Logger logger = LogManager.getLogger(EmergencyReconnectProtocol.class);
    private final NodeId peerId;
    private final EmergencyRecoveryManager emergencyRecoveryManager;
    private final ReconnectThrottle teacherThrottle;
    private final Supplier<ReservedSignedState> emergencyStateSupplier;
    private final Duration reconnectSocketTimeout;
    private final ReconnectMetrics reconnectMetrics;
    private final ReconnectController reconnectController;
    private InitiatedBy initiatedBy = InitiatedBy.NO_ONE;
    private final ThreadManager threadManager;
    private final NotificationEngine notificationEngine;
    private final Configuration configuration;
    private final Time time;

    /**
     * Enables submitting platform status actions
     */
    private final StatusActionSubmitter statusActionSubmitter;

    /**
     * @param time                     provides wall clock time
     * @param threadManager            responsible for managing thread lifecycles
     * @param notificationEngine       the notification engine to use
     * @param peerId                   the ID of the peer we are communicating with
     * @param emergencyRecoveryManager the state of emergency recovery, if any
     * @param teacherThrottle          restricts reconnects as a teacher
     * @param emergencyStateSupplier   returns the emergency state if available
     * @param reconnectSocketTimeout   the socket timeout to use when executing a reconnect
     * @param reconnectMetrics         tracks reconnect metrics
     * @param reconnectController      controls reconnecting as a learner
     * @param statusActionSubmitter    enables submitting platform status actions
     * @param configuration            the platform configuration
     */
    public EmergencyReconnectProtocol(
            @NonNull final Time time,
            @NonNull final ThreadManager threadManager,
            @NonNull final NotificationEngine notificationEngine,
            @NonNull final NodeId peerId,
            @NonNull final EmergencyRecoveryManager emergencyRecoveryManager,
            @NonNull final ReconnectThrottle teacherThrottle,
            @NonNull final Supplier<ReservedSignedState> emergencyStateSupplier,
            @NonNull final Duration reconnectSocketTimeout,
            @NonNull final ReconnectMetrics reconnectMetrics,
            @NonNull final ReconnectController reconnectController,
            @NonNull final StatusActionSubmitter statusActionSubmitter,
            @NonNull final Configuration configuration) {

        this.time = Objects.requireNonNull(time);
        this.threadManager = Objects.requireNonNull(threadManager, "threadManager must not be null");
        this.notificationEngine = Objects.requireNonNull(notificationEngine, "notificationEngine must not be null");
        this.peerId = Objects.requireNonNull(peerId, "peerId must not be null");
        this.emergencyRecoveryManager =
                Objects.requireNonNull(emergencyRecoveryManager, "emergencyRecoveryManager must not be null");
        this.teacherThrottle = Objects.requireNonNull(teacherThrottle, "teacherThrottle must not be null");
        this.emergencyStateSupplier = Objects.requireNonNull(emergencyStateSupplier, "emergencyStateSupplier must not be null");
        this.reconnectSocketTimeout =
                Objects.requireNonNull(reconnectSocketTimeout, "reconnectSocketTimeout must not be null");
        this.reconnectMetrics = Objects.requireNonNull(reconnectMetrics, "reconnectMetrics must not be null");
        this.reconnectController = Objects.requireNonNull(reconnectController, "reconnectController must not be null");
        this.statusActionSubmitter = Objects.requireNonNull(statusActionSubmitter);
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
    }

    @Override
    public boolean shouldInitiate() {
        final boolean initiateEmergencyReconnect = emergencyRecoveryManager.isEmergencyStateRequired();
        if (initiateEmergencyReconnect) {
            // if a permit is acquired, it will be released by either initiateFailed or runProtocol
            final boolean shouldInitiate = reconnectController.acquireLearnerPermit();
            if (shouldInitiate) {
                initiatedBy = InitiatedBy.SELF;
            }
            return shouldInitiate;
        }
        return false;
    }

    @Override
    public void initiateFailed() {
        reconnectController.cancelLearnerPermit();
        initiatedBy = InitiatedBy.NO_ONE;
    }

    @Override
    public boolean shouldAccept() {
        // if the throttle is initiated, we should call markReconnectFinished in teacher()
        final boolean shouldAccept = teacherThrottle.initiateReconnect(peerId);
        if (shouldAccept) {
            initiatedBy = InitiatedBy.PEER;
        }
        return shouldAccept;
    }

    @Override
    public boolean acceptOnSimultaneousInitiate() {
        // if both nodes are missing the emergency state, it makes no sense to reconnect with each other
        return false;
    }

    @Override
    public void runProtocol(final Connection connection)
            throws NetworkProtocolException, IOException, InterruptedException {
        try {
            switch (initiatedBy) {
                case PEER -> teacher(connection);
                case SELF -> learner(connection);
                default -> throw new NetworkProtocolException(String.format(
                        "runProtocol() called for emergency reconnect with peer %d "
                                + "but it is unclear who the teacher and who the learner is",
                        peerId.id()));
            }
        } finally {
            initiatedBy = InitiatedBy.NO_ONE;
        }
    }

    private void teacher(final Connection connection) {
        try {
            new EmergencyReconnectTeacher(
                            time, threadManager, emergencyStateSupplier, reconnectSocketTimeout, reconnectMetrics, configuration)
                    .execute(connection);
        } finally {
            teacherThrottle.reconnectAttemptFinished();
        }
    }

    private void learner(final Connection connection) {
        registerReconnectCompleteListener();
        try {
            final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);
            final boolean peerHasState = new EmergencyReconnectLearner(
                            stateConfig,
                            emergencyRecoveryManager.getEmergencyRecoveryFile(),
                            reconnectController,
                            statusActionSubmitter)
                    .execute(connection);
            if (!peerHasState) {
                reconnectController.cancelLearnerPermit();
            }
        } catch (final Exception e) {
            reconnectController.cancelLearnerPermit();
            throw e;
        }
    }

    private void registerReconnectCompleteListener() {
        notificationEngine.unregister(ReconnectCompleteListener.class, this::emergencyReconnectComplete);
        notificationEngine.register(ReconnectCompleteListener.class, this::emergencyReconnectComplete);
    }

    private void emergencyReconnectComplete(final ReconnectCompleteNotification notification) {
        if (emergencyRecoveryManager.isEmergencyStateRequired()) {
            logger.info(
                    RECONNECT.getMarker(),
                    "Emergency Reconnect Complete, round {} received from peer {}",
                    notification.getRoundNumber(),
                    peerId.id());
            emergencyRecoveryManager.emergencyStateLoaded();
        }
    }

    private enum InitiatedBy {
        NO_ONE,
        SELF,
        PEER
    }
}

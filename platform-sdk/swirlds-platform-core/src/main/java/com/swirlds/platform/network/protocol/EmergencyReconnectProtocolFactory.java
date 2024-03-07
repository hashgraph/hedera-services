/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.network.protocol;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.reconnect.ReconnectController;
import com.swirlds.platform.reconnect.ReconnectThrottle;
import com.swirlds.platform.reconnect.emergency.EmergencyReconnectProtocol;
import com.swirlds.platform.recovery.EmergencyRecoveryManager;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.system.status.StatusActionSubmitter;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Implementation of a protocol factory for emergency reconnect
 */
public class EmergencyReconnectProtocolFactory implements ProtocolFactory {

    private final EmergencyRecoveryManager emergencyRecoveryManager;
    private final ReconnectThrottle teacherThrottle;
    private final Supplier<ReservedSignedState> emergencyStateSupplier;
    private final Duration reconnectSocketTimeout;
    private final ReconnectMetrics reconnectMetrics;
    private final ReconnectController reconnectController;
    private final ThreadManager threadManager;
    private final NotificationEngine notificationEngine;
    private final Configuration configuration;
    private final Time time;
    private final PlatformContext platformContext;
    private final StatusActionSubmitter statusActionSubmitter;

    /**
     * @param  platformContext         the platform context
     * @param threadManager            responsible for managing thread lifecycles
     * @param notificationEngine       the notification engine to use
     * @param emergencyRecoveryManager the state of emergency recovery, if any
     * @param teacherThrottle          restricts reconnects as a teacher
     * @param emergencyStateSupplier   returns the emergency state if available
     * @param reconnectSocketTimeout   the socket timeout to use when executing a reconnect
     * @param reconnectMetrics         tracks reconnect metrics
     * @param reconnectController      controls reconnecting as a learner
     * @param statusActionSubmitter    enables submitting platform status actions
     * @param configuration            the platform configuration
     */
    public EmergencyReconnectProtocolFactory(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final NotificationEngine notificationEngine,
            @NonNull final EmergencyRecoveryManager emergencyRecoveryManager,
            @NonNull final ReconnectThrottle teacherThrottle,
            @NonNull final Supplier<ReservedSignedState> emergencyStateSupplier,
            @NonNull final Duration reconnectSocketTimeout,
            @NonNull final ReconnectMetrics reconnectMetrics,
            @NonNull final ReconnectController reconnectController,
            @NonNull final StatusActionSubmitter statusActionSubmitter,
            @NonNull final Configuration configuration) {

        this.platformContext = Objects.requireNonNull(platformContext);
        this.threadManager = Objects.requireNonNull(threadManager);
        this.notificationEngine = Objects.requireNonNull(notificationEngine);
        this.emergencyRecoveryManager = Objects.requireNonNull(emergencyRecoveryManager);
        this.teacherThrottle = Objects.requireNonNull(teacherThrottle);
        this.emergencyStateSupplier = Objects.requireNonNull(emergencyStateSupplier);
        this.reconnectSocketTimeout = Objects.requireNonNull(reconnectSocketTimeout);
        this.reconnectMetrics = Objects.requireNonNull(reconnectMetrics);
        this.reconnectController = Objects.requireNonNull(reconnectController);
        this.statusActionSubmitter = Objects.requireNonNull(statusActionSubmitter);
        this.configuration = Objects.requireNonNull(configuration);
        this.time = Objects.requireNonNull(platformContext.getTime());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public EmergencyReconnectProtocol build(@NonNull final NodeId peerId) {
        return new EmergencyReconnectProtocol(
                platformContext,
                time,
                threadManager,
                notificationEngine,
                Objects.requireNonNull(peerId),
                emergencyRecoveryManager,
                teacherThrottle,
                emergencyStateSupplier,
                reconnectSocketTimeout,
                reconnectMetrics,
                reconnectController,
                statusActionSubmitter,
                configuration);
    }
}

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
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.gossip.FallenBehindManager;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.reconnect.ReconnectController;
import com.swirlds.platform.reconnect.ReconnectProtocol;
import com.swirlds.platform.reconnect.ReconnectThrottle;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedStateValidator;
import com.swirlds.platform.system.status.PlatformStatusGetter;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Implementation of a factory for reconnect protocol
 */
public class ReconnectProtocolFactory implements ProtocolFactory {

    private final ReconnectThrottle reconnectThrottle;
    private final Supplier<ReservedSignedState> lastCompleteSignedState;
    private final Duration reconnectSocketTimeout;
    private final ReconnectMetrics reconnectMetrics;
    private final ReconnectController reconnectController;
    private final SignedStateValidator validator;
    private final ThreadManager threadManager;
    private final FallenBehindManager fallenBehindManager;

    /**
     * Provides the platform status.
     */
    private final PlatformStatusGetter platformStatusGetter;

    private final Configuration configuration;

    private final Time time;
    private final PlatformContext platformContext;

    public ReconnectProtocolFactory(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final ReconnectThrottle reconnectThrottle,
            @NonNull final Supplier<ReservedSignedState> lastCompleteSignedState,
            @NonNull final Duration reconnectSocketTimeout,
            @NonNull final ReconnectMetrics reconnectMetrics,
            @NonNull final ReconnectController reconnectController,
            @NonNull final SignedStateValidator validator,
            @NonNull final FallenBehindManager fallenBehindManager,
            @NonNull final PlatformStatusGetter platformStatusGetter,
            @NonNull final Configuration configuration) {

        this.platformContext = Objects.requireNonNull(platformContext);
        this.threadManager = Objects.requireNonNull(threadManager);
        this.reconnectThrottle = Objects.requireNonNull(reconnectThrottle);
        this.lastCompleteSignedState = Objects.requireNonNull(lastCompleteSignedState);
        this.reconnectSocketTimeout = Objects.requireNonNull(reconnectSocketTimeout);
        this.reconnectMetrics = Objects.requireNonNull(reconnectMetrics);
        this.reconnectController = Objects.requireNonNull(reconnectController);
        this.validator = Objects.requireNonNull(validator);
        this.fallenBehindManager = Objects.requireNonNull(fallenBehindManager);
        this.platformStatusGetter = Objects.requireNonNull(platformStatusGetter);
        this.configuration = Objects.requireNonNull(configuration);
        this.time = Objects.requireNonNull(platformContext.getTime());
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ReconnectProtocol build(@NonNull final NodeId peerId) {
        return new ReconnectProtocol(
                platformContext,
                threadManager,
                Objects.requireNonNull(peerId),
                reconnectThrottle,
                lastCompleteSignedState,
                reconnectSocketTimeout,
                reconnectMetrics,
                reconnectController,
                validator,
                fallenBehindManager,
                platformStatusGetter,
                configuration,
                time);
    }
}

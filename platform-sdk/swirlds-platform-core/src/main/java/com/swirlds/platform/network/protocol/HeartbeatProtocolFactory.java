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
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.heartbeats.HeartbeatProtocol;
import com.swirlds.platform.network.NetworkMetrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.Objects;

/**
 * Implementation of a factory for heartbeat protocol
 */
public class HeartbeatProtocolFactory implements ProtocolFactory {

    /**
     * The period at which the heartbeat protocol should be executed
     */
    private final Duration heartbeatPeriod;

    /**
     * Network metrics, for recording roundtrip heartbeat time
     */
    private final NetworkMetrics networkMetrics;

    /**
     * Source of time
     */
    private final Time time;

    public HeartbeatProtocolFactory(
            @NonNull final Duration heartbeatPeriod,
            @NonNull final NetworkMetrics networkMetrics,
            @NonNull final Time time) {

        this.heartbeatPeriod = Objects.requireNonNull(heartbeatPeriod);
        this.networkMetrics = Objects.requireNonNull(networkMetrics);
        this.time = Objects.requireNonNull(time);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public HeartbeatProtocol build(@NonNull final NodeId peerId) {
        return new HeartbeatProtocol(Objects.requireNonNull(peerId), heartbeatPeriod, networkMetrics, time);
    }
}

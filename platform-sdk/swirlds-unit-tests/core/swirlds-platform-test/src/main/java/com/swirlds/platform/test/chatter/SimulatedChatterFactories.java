/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.chatter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import com.swirlds.common.metrics.platform.DefaultMetricsFactory;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.time.OSTime;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.platform.gossip.chatter.config.ChatterConfig;
import com.swirlds.platform.gossip.chatter.protocol.ChatterCore;
import com.swirlds.platform.gossip.chatter.protocol.messages.ChatterEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class SimulatedChatterFactories implements SimulatedChatterFactory {

    private static final SimulatedChatterFactory SINGLETON = new SimulatedChatterFactories();

    public static SimulatedChatterFactory getInstance() {
        return SINGLETON;
    }

    @Override
    public SimulatedChatter build(
            @NonNull final NodeId selfId,
            @NonNull final Iterable<NodeId> nodeIds,
            @NonNull final GossipEventObserver eventTracker,
            @Nullable final Supplier<Instant> now) {
        Objects.requireNonNull(selfId, "selfId must not be null");
        Objects.requireNonNull(nodeIds, "nodeIds must not be null");
        Objects.requireNonNull(eventTracker, "eventTracker must not be null");

        final Configuration configuration = ConfigurationBuilder.create()
                .withConfigDataType(MetricsConfig.class)
                .withConfigDataType(ChatterConfig.class)
                .build();
        final MetricsConfig metricsConfig = configuration.getConfigData(MetricsConfig.class);
        final ChatterConfig chatterConfig = configuration.getConfigData(ChatterConfig.class);

        final MetricKeyRegistry registry = mock(MetricKeyRegistry.class);
        when(registry.register(any(), any(), any())).thenReturn(true);

        final ChatterCore<ChatterEvent> core = new ChatterCore<>(
                OSTime.getInstance(),
                ChatterEvent.class,
                e -> {},
                chatterConfig,
                (nodeId, ping) -> {},
                new DefaultMetrics(
                        selfId,
                        registry,
                        Executors.newSingleThreadScheduledExecutor(),
                        new DefaultMetricsFactory(),
                        metricsConfig));
        final EventDedup dedup = new EventDedup(List.of(core::eventReceived, eventTracker::newEvent));
        for (final NodeId nodeId : nodeIds) {
            core.newPeerInstance(nodeId, dedup);
        }

        return new ChatterWrapper(core, List.of(core, dedup));
    }
}

/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.uptime;

import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Metrics that track node uptime.
 */
public class UptimeMetrics {

    private static final String CATEGORY = "platform";

    /**
     * A map from node to the time since the last consensus event was observed from that node.
     */
    private final Map<Long, RunningAverageMetric> timeSinceLastConsensusEvent = new HashMap<>();

    /**
     * A map from node to the time since the last consensus event was observed from that node.
     */
    private final Map<Long, RunningAverageMetric> timeSinceLastJudge = new HashMap<>();

    private static final RunningAverageMetric.Config FRACTION_OF_NETWORK_ALIVE_CONFIG = new RunningAverageMetric.Config(
                    CATEGORY, "fractionOfNetworkAlive")
            .withUnit("fraction")
            .withDescription("The fraction (out of 1.0) of the network that is alive, weighted by consensus weight.");
    private final RunningAverageMetric fractionOfNetworkAlive;

    /**
     * Construct a new uptime metrics object.
     *
     * @param addressBook the address book
     */
    public UptimeMetrics(
            @NonNull Metrics metrics, @NonNull final AddressBook addressBook, @NonNull Supplier<Boolean> isDegraded) {

        fractionOfNetworkAlive = metrics.getOrCreate(FRACTION_OF_NETWORK_ALIVE_CONFIG);

        final FunctionGauge.Config<Boolean> degradedConfig = new FunctionGauge.Config<>(
                        CATEGORY, "degraded", Boolean.class, isDegraded)
                .withUnit("boolean")
                .withDescription("False if this node is healthy, true if this node is degraded.");
        metrics.getOrCreate(degradedConfig);

        for (final Address address : addressBook) {
            final RunningAverageMetric.Config timeSinceLastConensusEventConfig = new RunningAverageMetric.Config(
                            CATEGORY, "timeSinceLastConsensusEvent-" + address.getId())
                    .withUnit("seconds")
                    .withDescription("The consensus time in seconds since the "
                            + "last consensus event created by this node was observed");
            timeSinceLastConsensusEvent.put(address.getId(), metrics.getOrCreate(timeSinceLastConensusEventConfig));

            final RunningAverageMetric.Config timeSinceLastJudgeConfig = new RunningAverageMetric.Config(
                            CATEGORY, "timeSinceLastJudge-" + address.getId())
                    .withUnit("seconds")
                    .withDescription("The consensus time in seconds since the "
                            + "last judge created by this node was observed");
            timeSinceLastJudge.put(address.getId(), metrics.getOrCreate(timeSinceLastJudgeConfig));
        }
    }

    /**
     * Get the metric that tracks the time since the last consensus event was observed from a node.
     *
     * @param id the id of the node
     * @return the metric
     */
    public @NonNull RunningAverageMetric getTimeSinceLastConsensusEventMetric(final long id) {
        return Objects.requireNonNull(timeSinceLastConsensusEvent.get(id));
    }

    /**
     * Get the metric that tracks the time since the last judge was observed from a node.
     *
     * @param id the id of the node
     * @return the metric
     */
    public @NonNull RunningAverageMetric getTimeSinceLastJudgeMetric(final long id) {
        return Objects.requireNonNull(timeSinceLastJudge.get(id));
    }

    /**
     * Get the metric that tracks the fraction of the network that is alive.
     *
     * @return the metric
     */
    public @NonNull RunningAverageMetric getFractionOfNetworkAliveMetric() {
        return fractionOfNetworkAlive;
    }
}

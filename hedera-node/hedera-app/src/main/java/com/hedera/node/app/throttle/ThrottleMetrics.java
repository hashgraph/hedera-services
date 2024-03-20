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

package com.hedera.node.app.throttle;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.hapi.utils.throttles.CongestibleThrottle;
import com.hedera.node.app.hapi.utils.throttles.DeterministicThrottle;
import com.hedera.node.app.hapi.utils.throttles.GasLimitDeterministicThrottle;
import com.hedera.node.app.throttle.ThrottleAccumulator.ThrottleType;
import com.hedera.node.config.data.StatsConfig;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class maintains all metrics related to a {@link ThrottleAccumulator}.
 */
public class ThrottleMetrics {

    private static final Logger log = LogManager.getLogger(ThrottleMetrics.class);

    private static final String GAS_THROTTLE_ID = "<GAS>";
    private static final String LOG_MESSAGE_TPL = "Registered {} gauge for '{}' under name '{}'";
    private static final Supplier<Double> ZERO_SUPPLIER = () -> 0.0;

    private final Metrics metrics;
    private final String nameTemplate;
    private final String descriptionTemplate;
    private final Function<StatsConfig, List<String>> throttlesToSampleSupplier;

    public ThrottleMetrics(@NonNull final Metrics metrics, @NonNull final ThrottleType throttleType) {
        this.metrics = requireNonNull(metrics);
        final var typePrefix = requireNonNull(throttleType) == ThrottleType.FRONTEND_THROTTLE ? "HAPI" : "cons";
        this.nameTemplate = typePrefix.toLowerCase() + "%sPercentUsed";
        this.descriptionTemplate = "instantaneous %% used in " + typePrefix + " %s throttle bucket";
        this.throttlesToSampleSupplier = throttleType == ThrottleType.FRONTEND_THROTTLE
                ? StatsConfig::hapiThrottlesToSample
                : StatsConfig::consThrottlesToSample;
    }

    public void setupThrottles(
            @NonNull final List<DeterministicThrottle> throttles, @NonNull final Configuration configuration) {
        final var statsConfig = configuration.getConfigData(StatsConfig.class);
        final var throttlesToSample = throttlesToSampleSupplier.apply(statsConfig);

        final Set<String> liveMetrics = new HashSet<>();
        throttles.stream()
                .filter(throttle -> throttlesToSample.contains(throttle.name()))
                .forEach(throttle -> {
                    setupLiveMetric(throttle);
                    liveMetrics.add(throttle.name());
                });
        throttlesToSample.stream()
                .filter(name -> !liveMetrics.contains(name) && !GAS_THROTTLE_ID.equals(name))
                .forEach(this::setupInertMetric);
    }

    public void setupGasThrottle(
            @NonNull final GasLimitDeterministicThrottle gasThrottle, @NonNull final Configuration configuration) {
        final var statsConfig = configuration.getConfigData(StatsConfig.class);
        final var throttlesToSample = throttlesToSampleSupplier.apply(statsConfig);

        if (throttlesToSample.contains(GAS_THROTTLE_ID)) {
            setupLiveMetric(gasThrottle);
        }
    }

    private void setupLiveMetric(@NonNull final CongestibleThrottle throttle) {
        setupMetric(throttle.name(), throttle::instantaneousPercentUsed, "LIVE");
    }

    private void setupInertMetric(@NonNull final String throttleName) {
        setupMetric(throttleName, ZERO_SUPPLIER, "INERT");
    }

    private void setupMetric(
            @NonNull final String throttleName,
            @NonNull final Supplier<Double> valueSupplier,
            @NonNull final String status) {
        final var name = String.format(nameTemplate, throttleName);
        final var description = String.format(descriptionTemplate, throttleName);
        final var config = new FunctionGauge.Config<>("app", name, Double.class, valueSupplier)
                .withDescription(description)
                .withFormat("%,13.2f");
        metrics.getOrCreate(config);
        log.info(LOG_MESSAGE_TPL, status, description, name);
    }
}

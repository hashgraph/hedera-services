/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.stats;

import static com.hedera.services.stats.ServicesStatsManager.GAUGE_FORMAT;
import static com.hedera.services.stats.ServicesStatsManager.STAT_CATEGORY;

import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.throttles.GasLimitDeterministicThrottle;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.throttling.annotations.HandleThrottle;
import com.hedera.services.throttling.annotations.HapiThrottle;
import com.swirlds.common.metrics.DoubleGauge;
import com.swirlds.common.system.Platform;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class ThrottleGauges {
    private static final Logger log = LogManager.getLogger(ThrottleGauges.class);

    private static final String GAS_THROTTLE_ID = "<GAS>";
    private static final String GAS_THROTTLE_NAME = "Gas";
    private static final String CONS_THROTTLE_PREFIX = "cons";
    private static final String HAPI_THROTTLE_PREFIX = "HAPI";

    private final NodeLocalProperties nodeProperties;
    private final FunctionalityThrottling hapiThrottling;
    private final FunctionalityThrottling handleThrottling;
    private final Map<String, DoubleGauge> consNamedMetrics = new HashMap<>();
    private final Map<String, DoubleGauge> hapiNamedMetrics = new HashMap<>();

    public ThrottleGauges(
            final @HandleThrottle FunctionalityThrottling handleThrottling,
            final @HapiThrottle FunctionalityThrottling hapiThrottling,
            final NodeLocalProperties nodeProperties) {
        this.hapiThrottling = hapiThrottling;
        this.handleThrottling = handleThrottling;
        this.nodeProperties = nodeProperties;
        // Need to wait to create utilizationMetrics until the throttles are initialized
    }

    public void registerWith(final Platform platform) {
        registerTypeWith(
                HAPI_THROTTLE_PREFIX,
                platform,
                nodeProperties.hapiThrottlesToSample(),
                hapiThrottling.allActiveThrottles(),
                hapiNamedMetrics);
        registerTypeWith(
                CONS_THROTTLE_PREFIX,
                platform,
                nodeProperties.consThrottlesToSample(),
                handleThrottling.allActiveThrottles(),
                consNamedMetrics);
    }

    public void updateAll() {
        final var now = Instant.now();
        update(
                now,
                handleThrottling.allActiveThrottles(),
                handleThrottling.gasLimitThrottle(),
                consNamedMetrics);
        update(
                now,
                hapiThrottling.allActiveThrottles(),
                hapiThrottling.gasLimitThrottle(),
                hapiNamedMetrics);
    }

    private void registerTypeWith(
            final String type,
            final Platform platform,
            final List<String> throttlesToSample,
            final List<DeterministicThrottle> throttles,
            final Map<String, DoubleGauge> namedGauges) {
        final Set<String> activeReqs = new HashSet<>();
        throttles.stream()
                .filter(throttle -> throttlesToSample.contains(throttle.name()))
                .forEach(
                        throttle -> {
                            addAndCorrelateEntryFor(type, throttle.name(), platform, namedGauges);
                            activeReqs.add(throttle.name());
                        });
        throttlesToSample.stream()
                .filter(req -> !activeReqs.contains(req) && !GAS_THROTTLE_ID.equals(req))
                .forEach(name -> justAddEntryFor(type, name, platform));
        if (throttlesToSample.contains(GAS_THROTTLE_ID)) {
            addAndCorrelateEntryFor(type, GAS_THROTTLE_NAME, platform, namedGauges);
        }
    }

    private void update(
            final Instant now,
            final List<DeterministicThrottle> activeThrottles,
            final GasLimitDeterministicThrottle gasThrottle,
            final Map<String, DoubleGauge> namedMetrics) {
        activeThrottles.forEach(
                throttle -> {
                    final var name = throttle.name();
                    if (namedMetrics.containsKey(name)) {
                        namedMetrics.get(name).set(throttle.percentUsed(now));
                    }
                });
        if (namedMetrics.containsKey(GAS_THROTTLE_NAME)) {
            namedMetrics.get(GAS_THROTTLE_NAME).set(gasThrottle.percentUsed(now));
        }
    }

    private void addAndCorrelateEntryFor(
            final String type,
            final String throttleName,
            final Platform platform,
            final Map<String, DoubleGauge> utilizationMetrics) {
        utilizationMetrics.put(
                throttleName, registeredGaugeFor(type, throttleName, platform, "LIVE"));
    }

    private void justAddEntryFor(
            final String type, final String throttleName, final Platform platform) {
        // Don't track this gauge since we can't update its value (the throttle bucket doesn't
        // exist)
        registeredGaugeFor(type, throttleName, platform, "INERT");
    }

    private DoubleGauge registeredGaugeFor(
            final String type,
            final String throttleName,
            final Platform platform,
            final String status) {
        final var name = type.toLowerCase() + String.format(THROTTLE_NAME_TPL, throttleName);
        final var desc = String.format(THROTTLE_DESCRIPTION_TPL, type, throttleName);
        final var config =
                new DoubleGauge.Config(STAT_CATEGORY, name)
                        .withDescription(desc)
                        .withFormat(GAUGE_FORMAT);
        final var gauge = platform.getMetrics().getOrCreate(config);
        log.info("Registered {} gauge for '{}' under name '{}'", status, desc, name);
        return gauge;
    }

    private static final String THROTTLE_NAME_TPL = "%sPercentUsed";
    private static final String THROTTLE_DESCRIPTION_TPL =
            "instantaneous %% used in %s %s throttle bucket";
}

/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.stats.ServicesStatsConfig.IGNORED_FUNCTIONS;
import static com.hedera.services.stats.ServicesStatsConfig.SPEEDOMETER_ANSWERED_DESC_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.SPEEDOMETER_ANSWERED_NAME_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.SPEEDOMETER_DEPRECATED_TXNS_DESC;
import static com.hedera.services.stats.ServicesStatsConfig.SPEEDOMETER_DEPRECATED_TXNS_NAME;
import static com.hedera.services.stats.ServicesStatsConfig.SPEEDOMETER_HANDLED_DESC_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.SPEEDOMETER_HANDLED_NAME_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.SPEEDOMETER_RECEIVED_DESC_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.SPEEDOMETER_RECEIVED_NAME_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.SPEEDOMETER_SUBMITTED_DESC_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.SPEEDOMETER_SUBMITTED_NAME_TPL;
import static com.hedera.services.stats.ServicesStatsManager.SPEEDOMETER_FORMAT;
import static com.hedera.services.stats.ServicesStatsManager.STAT_CATEGORY;
import static com.hedera.services.utils.MiscUtils.QUERY_FUNCTIONS;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.system.Platform;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

public class HapiOpSpeedometers {
    static Supplier<HederaFunctionality[]> allFunctions =
            HederaFunctionality.class::getEnumConstants;

    private final HapiOpCounters counters;
    private final Function<HederaFunctionality, String> statNameFn;

    private final Map<HederaFunctionality, Long> lastReceivedOpsCount =
            new EnumMap<>(HederaFunctionality.class);
    private final Map<HederaFunctionality, Long> lastHandledTxnsCount =
            new EnumMap<>(HederaFunctionality.class);
    private final Map<HederaFunctionality, Long> lastSubmittedTxnsCount =
            new EnumMap<>(HederaFunctionality.class);
    private final Map<HederaFunctionality, Long> lastAnsweredQueriesCount =
            new EnumMap<>(HederaFunctionality.class);
    private Long lastReceivedDeprecatedTxnCount;

    private final EnumMap<HederaFunctionality, SpeedometerMetric> receivedOps =
            new EnumMap<>(HederaFunctionality.class);
    private final EnumMap<HederaFunctionality, SpeedometerMetric> handledTxns =
            new EnumMap<>(HederaFunctionality.class);
    private final EnumMap<HederaFunctionality, SpeedometerMetric> submittedTxns =
            new EnumMap<>(HederaFunctionality.class);
    private final EnumMap<HederaFunctionality, SpeedometerMetric> answeredQueries =
            new EnumMap<>(HederaFunctionality.class);
    private SpeedometerMetric receivedDeprecatedTxns;

    private EnumMap<HederaFunctionality, SpeedometerMetric.Config> receivedOpsConfig =
            new EnumMap<>(HederaFunctionality.class);
    private EnumMap<HederaFunctionality, SpeedometerMetric.Config> handledTxnsConfig =
            new EnumMap<>(HederaFunctionality.class);
    private EnumMap<HederaFunctionality, SpeedometerMetric.Config> submittedTxnsConfig =
            new EnumMap<>(HederaFunctionality.class);
    private EnumMap<HederaFunctionality, SpeedometerMetric.Config> answeredQueriesConfig =
            new EnumMap<>(HederaFunctionality.class);
    private SpeedometerMetric.Config receivedDeprecatedTxnsConfig;

    public HapiOpSpeedometers(
            final HapiOpCounters counters,
            final NodeLocalProperties properties,
            final Function<HederaFunctionality, String> statNameFn) {
        this.counters = counters;
        this.statNameFn = statNameFn;

        double halfLife = properties.statsSpeedometerHalfLifeSecs();
        Arrays.stream(allFunctions.get())
                .filter(function -> !IGNORED_FUNCTIONS.contains(function))
                .forEach(
                        function -> {
                            receivedOpsConfig.put(
                                    function,
                                    speedometerConfigFor(
                                            function,
                                            SPEEDOMETER_RECEIVED_NAME_TPL,
                                            SPEEDOMETER_RECEIVED_DESC_TPL,
                                            halfLife));
                            lastReceivedOpsCount.put(function, 0L);
                            if (QUERY_FUNCTIONS.contains(function)) {
                                answeredQueriesConfig.put(
                                        function,
                                        speedometerConfigFor(
                                                function,
                                                SPEEDOMETER_ANSWERED_NAME_TPL,
                                                SPEEDOMETER_ANSWERED_DESC_TPL,
                                                halfLife));
                                lastAnsweredQueriesCount.put(function, 0L);
                            } else {
                                submittedTxnsConfig.put(
                                        function,
                                        speedometerConfigFor(
                                                function,
                                                SPEEDOMETER_SUBMITTED_NAME_TPL,
                                                SPEEDOMETER_SUBMITTED_DESC_TPL,
                                                halfLife));
                                lastSubmittedTxnsCount.put(function, 0L);
                                handledTxnsConfig.put(
                                        function,
                                        speedometerConfigFor(
                                                function,
                                                SPEEDOMETER_HANDLED_NAME_TPL,
                                                SPEEDOMETER_HANDLED_DESC_TPL,
                                                halfLife));
                                lastHandledTxnsCount.put(function, 0L);
                            }
                        });
        receivedDeprecatedTxnsConfig =
                new SpeedometerMetric.Config(STAT_CATEGORY, SPEEDOMETER_DEPRECATED_TXNS_NAME)
                        .withDescription(SPEEDOMETER_DEPRECATED_TXNS_DESC)
                        .withFormat(SPEEDOMETER_FORMAT)
                        .withHalfLife(halfLife);
        lastReceivedDeprecatedTxnCount = 0L;
    }

    public SpeedometerMetric.Config speedometerConfigFor(
            final HederaFunctionality function,
            final String nameTpl,
            final String descTpl,
            final double halfLife) {
        final var baseName = statNameFn.apply(function);
        var fullName = String.format(nameTpl, baseName);
        var description = String.format(descTpl, baseName);
        return new SpeedometerMetric.Config(STAT_CATEGORY, fullName)
                .withDescription(description)
                .withFormat(SPEEDOMETER_FORMAT)
                .withHalfLife(halfLife);
    }

    public void registerWith(Platform platform) {
        registerSpeedometers(platform, receivedOps, receivedOpsConfig);
        registerSpeedometers(platform, submittedTxns, submittedTxnsConfig);
        registerSpeedometers(platform, handledTxns, handledTxnsConfig);
        registerSpeedometers(platform, answeredQueries, answeredQueriesConfig);
        receivedDeprecatedTxns = platform.getOrCreateMetric(receivedDeprecatedTxnsConfig);

        receivedOpsConfig = null;
        submittedTxnsConfig = null;
        handledTxnsConfig = null;
        answeredQueriesConfig = null;
        receivedDeprecatedTxnsConfig = null;
    }

    private void registerSpeedometers(
            final Platform platform,
            final Map<HederaFunctionality, SpeedometerMetric> speedometers,
            final Map<HederaFunctionality, SpeedometerMetric.Config> configs) {

        configs.forEach(
                (function, config) ->
                        speedometers.put(function, platform.getOrCreateMetric(config)));
    }

    public void updateAll() {
        updateSpeedometers(receivedOps, lastReceivedOpsCount, counters::receivedSoFar);
        updateSpeedometers(submittedTxns, lastSubmittedTxnsCount, counters::submittedSoFar);
        updateSpeedometers(handledTxns, lastHandledTxnsCount, counters::handledSoFar);
        updateSpeedometers(answeredQueries, lastAnsweredQueriesCount, counters::answeredSoFar);

        receivedDeprecatedTxns.update(
                (double) counters.receivedDeprecatedTxnSoFar() - lastReceivedDeprecatedTxnCount);
        lastReceivedDeprecatedTxnCount = counters.receivedDeprecatedTxnSoFar();
    }

    private void updateSpeedometers(
            Map<HederaFunctionality, SpeedometerMetric> speedometers,
            Map<HederaFunctionality, Long> lastMeasurements,
            ToLongFunction<HederaFunctionality> currMeasurement) {
        for (Map.Entry<HederaFunctionality, SpeedometerMetric> entry : speedometers.entrySet()) {
            var function = entry.getKey();
            long last = lastMeasurements.get(function);
            long curr = currMeasurement.applyAsLong(function);
            entry.getValue().update((double) curr - last);
            lastMeasurements.put(function, curr);
        }
    }

    @VisibleForTesting
    EnumMap<HederaFunctionality, SpeedometerMetric> getReceivedOps() {
        return receivedOps;
    }

    @VisibleForTesting
    EnumMap<HederaFunctionality, SpeedometerMetric> getHandledTxns() {
        return handledTxns;
    }

    @VisibleForTesting
    EnumMap<HederaFunctionality, SpeedometerMetric> getSubmittedTxns() {
        return submittedTxns;
    }

    @VisibleForTesting
    EnumMap<HederaFunctionality, SpeedometerMetric> getAnsweredQueries() {
        return answeredQueries;
    }

    @VisibleForTesting
    Map<HederaFunctionality, Long> getLastReceivedOpsCount() {
        return lastReceivedOpsCount;
    }

    @VisibleForTesting
    Map<HederaFunctionality, Long> getLastHandledTxnsCount() {
        return lastHandledTxnsCount;
    }

    @VisibleForTesting
    Map<HederaFunctionality, Long> getLastSubmittedTxnsCount() {
        return lastSubmittedTxnsCount;
    }

    @VisibleForTesting
    Map<HederaFunctionality, Long> getLastAnsweredQueriesCount() {
        return lastAnsweredQueriesCount;
    }

    @VisibleForTesting
    Long getLastReceivedDeprecatedTxnCount() {
        return lastReceivedDeprecatedTxnCount;
    }

    @VisibleForTesting
    void setLastReceivedDeprecatedTxnCount(Long lastReceivedDeprecatedTxnCount) {
        this.lastReceivedDeprecatedTxnCount = lastReceivedDeprecatedTxnCount;
    }

    @VisibleForTesting
    void setReceivedDeprecatedTxns(final SpeedometerMetric receivedDeprecatedTxns) {
        this.receivedDeprecatedTxns = receivedDeprecatedTxns;
    }
}

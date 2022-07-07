package com.hedera.services.stats;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.context.properties.NodeLocalProperties;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.swirlds.common.statistics.StatsSpeedometer;
import com.swirlds.common.system.Platform;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import static com.hedera.services.stats.ServicesStatsConfig.IGNORED_FUNCTIONS;
import static com.hedera.services.stats.ServicesStatsConfig.SPEEDOMETER_ANSWERED_DESC_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.SPEEDOMETER_ANSWERED_NAME_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.SPEEDOMETER_HANDLED_DESC_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.SPEEDOMETER_HANDLED_NAME_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.SPEEDOMETER_RECEIVED_DEPRECATED_DESC_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.SPEEDOMETER_RECEIVED_DEPRECATED_NAME_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.SPEEDOMETER_RECEIVED_DESC_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.SPEEDOMETER_RECEIVED_NAME_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.SPEEDOMETER_SUBMITTED_DESC_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.SPEEDOMETER_SUBMITTED_NAME_TPL;
import static com.hedera.services.utils.MiscUtils.QUERY_FUNCTIONS;

public class HapiOpSpeedometers {
	static Supplier<HederaFunctionality[]> allFunctions = HederaFunctionality.class::getEnumConstants;

	private final HapiOpCounters counters;
	private final SpeedometerFactory speedometer;
	private final Function<HederaFunctionality, String> statNameFn;

	final Map<HederaFunctionality, Long> lastReceivedOpsCount = new EnumMap<>(HederaFunctionality.class);
	final Map<HederaFunctionality, Long> lastHandledTxnsCount = new EnumMap<>(HederaFunctionality.class);
	final Map<HederaFunctionality, Long> lastSubmittedTxnsCount = new EnumMap<>(HederaFunctionality.class);
	final Map<HederaFunctionality, Long> lastAnsweredQueriesCount = new EnumMap<>(HederaFunctionality.class);
	Long lastReceivedDeprecatedTxnCount;

	final EnumMap<HederaFunctionality, StatsSpeedometer> receivedOps = new EnumMap<>(HederaFunctionality.class);
	final EnumMap<HederaFunctionality, StatsSpeedometer> handledTxns = new EnumMap<>(HederaFunctionality.class);
	final EnumMap<HederaFunctionality, StatsSpeedometer> submittedTxns = new EnumMap<>(HederaFunctionality.class);
	final EnumMap<HederaFunctionality, StatsSpeedometer> answeredQueries = new EnumMap<>(HederaFunctionality.class);
	StatsSpeedometer receivedDeprecatedTxns;

	public HapiOpSpeedometers(
			HapiOpCounters counters,
			SpeedometerFactory speedometer,
			NodeLocalProperties properties,
			Function<HederaFunctionality, String> statNameFn
	) {
		this.counters = counters;
		this.statNameFn = statNameFn;
		this.speedometer = speedometer;

		double halfLife = properties.statsSpeedometerHalfLifeSecs();
		Arrays.stream(allFunctions.get())
				.filter(function -> !IGNORED_FUNCTIONS.contains(function))
				.forEach(function -> {
					receivedOps.put(function, new StatsSpeedometer(halfLife));
					lastReceivedOpsCount.put(function, 0L);
					if (QUERY_FUNCTIONS.contains(function)) {
						answeredQueries.put(function, new StatsSpeedometer(halfLife));
						lastAnsweredQueriesCount.put(function, 0L);
					} else {
						submittedTxns.put(function, new StatsSpeedometer(halfLife));
						lastSubmittedTxnsCount.put(function, 0L);
						handledTxns.put(function, new StatsSpeedometer(halfLife));
						lastHandledTxnsCount.put(function, 0L);
					}
				});
		receivedDeprecatedTxns = new StatsSpeedometer(halfLife);
		lastReceivedDeprecatedTxnCount = 0L;
	}

	public void registerWith(Platform platform) {
		registerSpeedometers(platform, receivedOps, SPEEDOMETER_RECEIVED_NAME_TPL, SPEEDOMETER_RECEIVED_DESC_TPL);
		registerSpeedometers(platform, submittedTxns, SPEEDOMETER_SUBMITTED_NAME_TPL, SPEEDOMETER_SUBMITTED_DESC_TPL);
		registerSpeedometers(platform, handledTxns, SPEEDOMETER_HANDLED_NAME_TPL, SPEEDOMETER_HANDLED_DESC_TPL);
		registerSpeedometers(platform, answeredQueries, SPEEDOMETER_ANSWERED_NAME_TPL, SPEEDOMETER_ANSWERED_DESC_TPL);
		registerSpeedometer(platform, receivedDeprecatedTxns, SPEEDOMETER_RECEIVED_DEPRECATED_NAME_TPL,
				SPEEDOMETER_RECEIVED_DEPRECATED_DESC_TPL);
	}

	private void registerSpeedometer(
			Platform platform,
			StatsSpeedometer speedometers,
			String nameTpl,
			String descTpl
	) {
		platform.addAppStatEntry(speedometer.from(nameTpl, descTpl, speedometers));
	}

	private void registerSpeedometers(
			Platform platform,
			Map<HederaFunctionality, StatsSpeedometer> speedometers,
			String nameTpl,
			String descTpl
	) {
		for (Map.Entry<HederaFunctionality, StatsSpeedometer> entry : speedometers.entrySet()) {
			var baseName = statNameFn.apply(entry.getKey());
			var fullName = String.format(nameTpl, baseName);
			var description = String.format(descTpl, baseName);
			platform.addAppStatEntry(speedometer.from(fullName, description, entry.getValue()));
		}
	}

	public void updateAll() {
		updateSpeedometers(receivedOps, lastReceivedOpsCount, counters::receivedSoFar);
		updateSpeedometers(submittedTxns, lastSubmittedTxnsCount, counters::submittedSoFar);
		updateSpeedometers(handledTxns, lastHandledTxnsCount, counters::handledSoFar);
		updateSpeedometers(answeredQueries, lastAnsweredQueriesCount, counters::answeredSoFar);

		receivedDeprecatedTxns.update((double) counters.receivedDeprecatedTxnSoFar() - lastReceivedDeprecatedTxnCount);
		lastReceivedDeprecatedTxnCount = counters.receivedDeprecatedTxnSoFar();
	}

	private void updateSpeedometers(
			Map<HederaFunctionality, StatsSpeedometer> speedometers,
			Map<HederaFunctionality, Long> lastMeasurements,
			ToLongFunction<HederaFunctionality> currMeasurement
	) {
		for (Map.Entry<HederaFunctionality, StatsSpeedometer> entry : speedometers.entrySet()) {
			var function = entry.getKey();
			long last = lastMeasurements.get(function);
			long curr = currMeasurement.applyAsLong(function);
			entry.getValue().update((double) curr - last);
			lastMeasurements.put(function, curr);
		}
	}
}

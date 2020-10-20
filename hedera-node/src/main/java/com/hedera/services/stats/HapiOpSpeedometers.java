package com.hedera.services.stats;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.swirlds.common.Platform;
import com.swirlds.platform.StatsSpeedometer;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.hedera.services.utils.MiscUtils.QUERY_FUNCTIONS;

public class HapiOpSpeedometers {
	static Supplier<HederaFunctionality[]> allFunctions = HederaFunctionality.class::getEnumConstants;

	private final HapiOpCounters counters;
	private final SpeedometerFactory speedometer;
	private final NodeLocalProperties properties;
	private final Function<HederaFunctionality, String> statNameFn;

	final Map<HederaFunctionality, Long> lastReceivedOpsCount = new HashMap<>();
	final Map<HederaFunctionality, Long> lastHandledTxnsCount = new HashMap<>();
	final Map<HederaFunctionality, Long> lastSubmittedTxnsCount = new HashMap<>();
	final Map<HederaFunctionality, Long> lastAnsweredQueriesCount = new HashMap<>();

	final EnumMap<HederaFunctionality, StatsSpeedometer> receivedOps = new EnumMap<>(HederaFunctionality.class);
	final EnumMap<HederaFunctionality, StatsSpeedometer> handledTxns = new EnumMap<>(HederaFunctionality.class);
	final EnumMap<HederaFunctionality, StatsSpeedometer> submittedTxns = new EnumMap<>(HederaFunctionality.class);
	final EnumMap<HederaFunctionality, StatsSpeedometer> answeredQueries = new EnumMap<>(HederaFunctionality.class);

	public HapiOpSpeedometers(
			HapiOpCounters counters,
			SpeedometerFactory speedometer,
			NodeLocalProperties properties,
			Function<HederaFunctionality, String> statNameFn
	) {
		this.counters = counters;
		this.statNameFn = statNameFn;
		this.properties = properties;
		this.speedometer = speedometer;

		double halfLife = properties.statsHapiSpeedometerHalfLifeSecs();
		Arrays.stream(allFunctions.get()).forEach(function -> {
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
	}

	public void registerWith(Platform platform) {
		throw new AssertionError("Not implemented!");
	}

	public void updateAll() {
		throw new AssertionError("Not implemented!");
	}
}

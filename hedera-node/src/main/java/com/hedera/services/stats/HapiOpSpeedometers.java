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

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.swirlds.platform.StatsSpeedometer;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

public class HapiOpSpeedometers {
	static Supplier<HederaFunctionality[]> allFunctions = HederaFunctionality.class::getEnumConstants;

	private final SpeedometerFactory speedometer;
	private final Function<HederaFunctionality, String> statNameFn;
	private final Function<HederaFunctionality, Long> handledSoFar;
	private final Function<HederaFunctionality, Long> receivedSoFar;
	private final Function<HederaFunctionality, Long> answeredSoFar;
	private final Function<HederaFunctionality, Long> submittedSoFar;

	final HashMap<HederaFunctionality, Long> lastReceivedSoFar = new HashMap<>();
	final HashMap<HederaFunctionality, Long> lastHandledSoFar = new HashMap<>();
	final HashMap<HederaFunctionality, Long> lastSubmittedSoFar = new HashMap<>();
	final HashMap<HederaFunctionality, Long> lastAnsweredSoFar = new HashMap<>();

	final EnumMap<HederaFunctionality, StatsSpeedometer> receivedOps = new EnumMap<>(HederaFunctionality.class);
	final EnumMap<HederaFunctionality, StatsSpeedometer> handledTxns = new EnumMap<>(HederaFunctionality.class);
	final EnumMap<HederaFunctionality, StatsSpeedometer> submittedTxns = new EnumMap<>(HederaFunctionality.class);
	final EnumMap<HederaFunctionality, StatsSpeedometer> answeredQueries = new EnumMap<>(HederaFunctionality.class);

	public HapiOpSpeedometers(
			SpeedometerFactory speedometer,
			Function<HederaFunctionality, Long> handledSoFar,
			Function<HederaFunctionality, Long> receivedSoFar,
			Function<HederaFunctionality, Long> answeredSoFar,
			Function<HederaFunctionality, Long> submittedSoFar,
			Function<HederaFunctionality, String> statNameFn
	) {
		this.statNameFn = statNameFn;
		this.speedometer = speedometer;
		this.handledSoFar = handledSoFar;
		this.answeredSoFar = answeredSoFar;
		this.receivedSoFar = receivedSoFar;
		this.submittedSoFar = submittedSoFar;
	}
}

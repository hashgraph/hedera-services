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
import com.swirlds.common.Platform;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.hedera.services.utils.MiscUtils.QUERY_FUNCTIONS;

public class HapiOpCounters {
	public static final String FREEZE_METRIC = "freeze";
	public static final String SYSTEM_DELETE_METRIC = "systemDelete";
	public static final String SYSTEM_UNDELETE_METRIC = "systemUndelete";

	static Supplier<HederaFunctionality[]> allFunctions = HederaFunctionality.class::getEnumConstants;

	private final CounterFactory counter;
	private final Function<HederaFunctionality, String> statNameFn;

	EnumMap<HederaFunctionality, AtomicLong> receivedOps = new EnumMap<>(HederaFunctionality.class);
	EnumMap<HederaFunctionality, AtomicLong> handledTxns = new EnumMap<>(HederaFunctionality.class);
	EnumMap<HederaFunctionality, AtomicLong> submittedTxns = new EnumMap<>(HederaFunctionality.class);
	EnumMap<HederaFunctionality, AtomicLong> answeredQueries = new EnumMap<>(HederaFunctionality.class);

	public HapiOpCounters( CounterFactory counter, Function<HederaFunctionality, String> statNameFn) {
		this.counter = counter;
		this.statNameFn = statNameFn;

		Arrays.stream(allFunctions.get()).forEach(function -> {
			receivedOps.put(function, new AtomicLong());
			if (QUERY_FUNCTIONS.contains(function)) {
				answeredQueries.put(function, new AtomicLong());
			} else {
				submittedTxns.put(function, new AtomicLong());
				handledTxns.put(function, new AtomicLong());
			}
		});
	}

	public void countReceived(HederaFunctionality op) {
		receivedOps.get(op).getAndIncrement();
	}

	public long receivedSoFar(HederaFunctionality op) {
		return receivedOps.get(op).get();
	}

	public void countSubmitted(HederaFunctionality txn) {
		submittedTxns.get(txn).getAndIncrement();
	}

	public long submittedSoFar(HederaFunctionality txn) {
		return submittedTxns.get(txn).get();
	}

	public void countHandled(HederaFunctionality txn) {
		handledTxns.get(txn).getAndIncrement();
	}

	public long handledSoFar(HederaFunctionality txn) {
		return handledTxns.get(txn).get();
	}

	public void countAnswered(HederaFunctionality query) {
		answeredQueries.get(query).getAndIncrement();
	}

	public long answeredSoFar(HederaFunctionality query) {
		return answeredQueries.get(query).get();
	}

	public void registerWith(Platform platform) {
		registerCounters(
				platform, receivedOps, "%sRcv", "number of %s received");
		registerCounters(
				platform, submittedTxns, "%sSub", "number of %s submitted");
		registerCounters(
				platform, handledTxns, "%sHdl", "number of %s handled");
		registerCounters(
				platform, answeredQueries, "%sAns", "number of %s answered");
	}

	private void registerCounters(
			Platform platform,
			EnumMap<HederaFunctionality, AtomicLong> counters,
			String nameTpl,
			String descTpl
	) {
		for (Map.Entry<HederaFunctionality, AtomicLong> entry : counters.entrySet())	{
			var baseName = statNameFn.apply(entry.getKey());
			var fullName = String.format(nameTpl, baseName);
			var description = String.format(descTpl, baseName);
			platform.addAppStatEntry(counter.from(fullName, description, entry.getValue()::get));
		}
	}
}

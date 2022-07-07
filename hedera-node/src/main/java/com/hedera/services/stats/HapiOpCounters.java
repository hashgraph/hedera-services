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

import com.hedera.services.context.TransactionContext;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.swirlds.common.system.Platform;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.hedera.services.stats.ServicesStatsConfig.COUNTER_ANSWERED_DESC_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.COUNTER_ANSWERED_NAME_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.COUNTER_HANDLED_DESC_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.COUNTER_HANDLED_NAME_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.COUNTER_RECEIVED_DEPRECATED_DESC_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.COUNTER_RECEIVED_DEPRECATED_NAME_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.COUNTER_RECEIVED_DESC_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.COUNTER_RECEIVED_NAME_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.COUNTER_SUBMITTED_DESC_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.COUNTER_SUBMITTED_NAME_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.IGNORED_FUNCTIONS;
import static com.hedera.services.utils.MiscUtils.QUERY_FUNCTIONS;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;

public class HapiOpCounters {
	static Supplier<HederaFunctionality[]> allFunctions = HederaFunctionality.class::getEnumConstants;

	private final CounterFactory counter;
	private final MiscRunningAvgs runningAvgs;
	private final TransactionContext txnCtx;
	private final Function<HederaFunctionality, String> statNameFn;

	EnumMap<HederaFunctionality, AtomicLong> receivedOps = new EnumMap<>(HederaFunctionality.class);
	EnumMap<HederaFunctionality, AtomicLong> handledTxns = new EnumMap<>(HederaFunctionality.class);
	EnumMap<HederaFunctionality, AtomicLong> submittedTxns = new EnumMap<>(HederaFunctionality.class);
	EnumMap<HederaFunctionality, AtomicLong> answeredQueries = new EnumMap<>(HederaFunctionality.class);
	AtomicLong receivedDeprecatedTxns;

	public HapiOpCounters(
			final CounterFactory counter,
			final MiscRunningAvgs runningAvgs,
			final TransactionContext txnCtx,
			final Function<HederaFunctionality, String> statNameFn
	) {
		this.txnCtx = txnCtx;
		this.counter = counter;
		this.statNameFn = statNameFn;
		this.runningAvgs = runningAvgs;

		Arrays.stream(allFunctions.get())
				.filter(function -> !IGNORED_FUNCTIONS.contains(function))
				.forEach(function -> {
					receivedOps.put(function, new AtomicLong());
					if (QUERY_FUNCTIONS.contains(function)) {
						answeredQueries.put(function, new AtomicLong());
					} else {
						submittedTxns.put(function, new AtomicLong());
						handledTxns.put(function, new AtomicLong());
					}
				});
		receivedDeprecatedTxns = new AtomicLong();
	}

	public void registerWith(final Platform platform) {
		registerCounters(platform, receivedOps, COUNTER_RECEIVED_NAME_TPL, COUNTER_RECEIVED_DESC_TPL);
		registerCounters(platform, submittedTxns, COUNTER_SUBMITTED_NAME_TPL, COUNTER_SUBMITTED_DESC_TPL);
		registerCounters(platform, handledTxns, COUNTER_HANDLED_NAME_TPL, COUNTER_HANDLED_DESC_TPL);
		registerCounters(platform, answeredQueries, COUNTER_ANSWERED_NAME_TPL, COUNTER_ANSWERED_DESC_TPL);
		registerCounter(platform, receivedDeprecatedTxns, COUNTER_RECEIVED_DEPRECATED_NAME_TPL,
				COUNTER_RECEIVED_DEPRECATED_DESC_TPL);
	}

	private void registerCounter(
			final Platform platform,
			final AtomicLong counters,
			final String nameTpl,
			final String descTpl
	) {
		platform.addAppStatEntry(counter.from(nameTpl, descTpl, counters::get));
	}

	private void registerCounters(
			final Platform platform,
			final Map<HederaFunctionality, AtomicLong> counters,
			final String nameTpl,
			final String descTpl
	) {
		for (final var entry : counters.entrySet()) {
			final var baseName = statNameFn.apply(entry.getKey());
			final var fullName = String.format(nameTpl, baseName);
			final var description = String.format(descTpl, baseName);
			platform.addAppStatEntry(counter.from(fullName, description, entry.getValue()::get));
		}
	}

	public void countReceived(final HederaFunctionality op) {
		safeIncrement(receivedOps, op);
	}

	public long receivedSoFar(final HederaFunctionality op) {
		return IGNORED_FUNCTIONS.contains(op) ? 0 : receivedOps.get(op).get();
	}

	public void countSubmitted(final HederaFunctionality txn) {
		safeIncrement(submittedTxns, txn);
	}

	public long submittedSoFar(final HederaFunctionality txn) {
		return IGNORED_FUNCTIONS.contains(txn) ? 0 : submittedTxns.get(txn).get();
	}

	public void countHandled(final HederaFunctionality txn) {
		safeIncrement(handledTxns, txn);
		if (txn == ConsensusSubmitMessage) {
			int txnBytes = txnCtx.accessor().getTxn().getSerializedSize();
			runningAvgs.recordHandledSubmitMessageSize(txnBytes);
		}
	}

	public long handledSoFar(final HederaFunctionality txn) {
		return IGNORED_FUNCTIONS.contains(txn) ? 0 : handledTxns.get(txn).get();
	}

	public void countAnswered(final HederaFunctionality query) {
		safeIncrement(answeredQueries, query);
	}

	public long answeredSoFar(final HederaFunctionality query) {
		return IGNORED_FUNCTIONS.contains(query) ? 0 : answeredQueries.get(query).get();
	}

	private void safeIncrement(
			final Map<HederaFunctionality, AtomicLong> counters,
			final HederaFunctionality function
	) {
		if (!IGNORED_FUNCTIONS.contains(function)) {
			counters.get(function).getAndIncrement();
		}
	}

	public void countDeprecatedTxnReceived() {
		receivedDeprecatedTxns.getAndIncrement();
	}

	public long receivedDeprecatedTxnSoFar() {
		return receivedDeprecatedTxns.get();
	}
}

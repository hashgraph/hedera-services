package com.hedera.services.bdd.suites.issues;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hedera.services.bdd.spec.HapiApiSpec;
import static com.hedera.services.bdd.spec.HapiApiSpec.*;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

public class Issue2144Spec extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(Issue2144Spec.class);

	public static void main(String... args) {
		new Issue2144Spec().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[]{
						superusersAreNeverThrottledOnTransfers(),
						superusersAreNeverThrottledOnMiscTxns(),
						superusersAreNeverThrottledOnHcsTxns(),
						superusersAreNeverThrottledOnMiscQueries(),
						superusersAreNeverThrottledOnHcsQueries(),
				}
		);
	}

	final int BURST_SIZE = 10;
	Function<String, HapiSpecOperation[]> transferBurstFn = payer -> IntStream
			.range(0, BURST_SIZE)
			.mapToObj(ignore ->
					cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
							.payingWith(payer)
							.deferStatusResolution())
			.toArray(n -> new HapiSpecOperation[n]);
	Function<String, HapiSpecOperation[]> miscTxnBurstFn = payer -> IntStream
			.range(0, BURST_SIZE)
			.mapToObj(i -> cryptoCreate(String.format("Account%d", i))
					.payingWith(payer)
					.deferStatusResolution())
			.toArray(n -> new HapiSpecOperation[n]);
	Function<String, HapiSpecOperation[]> hcsTxnBurstFn = payer -> IntStream
			.range(0, BURST_SIZE)
			.mapToObj(i -> createTopic(String.format("Topic%d", i))
					.payingWith(payer)
					.deferStatusResolution())
			.toArray(n -> new HapiSpecOperation[n]);
	Function<String, HapiSpecOperation[]> miscQueryBurstFn = payer -> IntStream
			.range(0, BURST_SIZE)
			.mapToObj(i -> getAccountInfo(GENESIS)
					.nodePayment(100L)
					.payingWith(payer))
			.toArray(n -> new HapiSpecOperation[n]);
	Function<String, HapiSpecOperation[]> hcsQueryBurstFn = payer -> IntStream
			.range(0, BURST_SIZE)
			.mapToObj(i -> getTopicInfo("misc")
					.nodePayment(100L)
					.payingWith(payer))
			.toArray(n -> new HapiSpecOperation[n]);

	private HapiApiSpec superusersAreNeverThrottledOnTransfers() {
		return defaultHapiSpec("MasterIsNeverThrottledOnTransfers")
				.given(
						cryptoTransfer(tinyBarsFromTo(GENESIS, MASTER, 1_000_000_000L))
				).when(
						fileUpdate(APP_PROPERTIES).overridingProps(
								Map.of("simpletransferTps", "1"))
				).then(flattened(
						transferBurstFn.apply(MASTER),
						transferBurstFn.apply(GENESIS),
						sleepFor(5_000L),
						fileUpdate(APP_PROPERTIES).overridingProps(
								Map.of("simpletransferTps", "0"))
				));
	}

	private HapiApiSpec superusersAreNeverThrottledOnMiscTxns() {
		return defaultHapiSpec("MasterIsNeverThrottledOnMiscTxns")
				.given(
						cryptoTransfer(tinyBarsFromTo(GENESIS, MASTER, 1_000_000_000_000_000L))
				).when(
						fileUpdate(APP_PROPERTIES).overridingProps(
								Map.of("throttlingTps", "1"))
				).then(flattened(
						miscTxnBurstFn.apply(MASTER),
						miscTxnBurstFn.apply(GENESIS),
						sleepFor(5_000L),
						fileUpdate(APP_PROPERTIES).overridingProps(
								Map.of("throttlingTps", "0"))
				));
	}

	private HapiApiSpec superusersAreNeverThrottledOnHcsTxns() {
		return defaultHapiSpec("MasterIsNeverThrottledOnHcsTxns")
				.given(
						cryptoTransfer(tinyBarsFromTo(GENESIS, MASTER, 1_000_000_000_000_000L))
				).when(
						fileUpdate(APP_PROPERTIES).overridingProps(
								Map.of("throttling.hcs.createTopic.tps", "0.5"))
				).then(flattened(
						hcsTxnBurstFn.apply(MASTER),
						hcsTxnBurstFn.apply(GENESIS),
						fileUpdate(APP_PROPERTIES).overridingProps(
								Map.of("throttling.hcs.createTopic.tps", "33.3"))
				));
	}

	private HapiApiSpec superusersAreNeverThrottledOnMiscQueries() {
		return defaultHapiSpec("MasterIsNeverThrottledOnMiscQueries")
				.given(
						cryptoTransfer(tinyBarsFromTo(GENESIS, MASTER, 1_000_000_000_000_000L))
				).when(
						fileUpdate(APP_PROPERTIES).overridingProps(
								Map.of("queriesTps", "1"))
				).then(flattened(
						inParallel(miscQueryBurstFn.apply(MASTER)),
						inParallel(miscQueryBurstFn.apply(GENESIS)),
						fileUpdate(APP_PROPERTIES).overridingProps(
								Map.of("queriesTps", "0"))
				));
	}

	private HapiApiSpec superusersAreNeverThrottledOnHcsQueries() {
		return defaultHapiSpec("MasterIsNeverThrottledOnHcsQueries")
				.given(
						cryptoTransfer(tinyBarsFromTo(GENESIS, MASTER, 1_000_000_000_000_000L)),
						createTopic("misc")
				).when(
						fileUpdate(APP_PROPERTIES).overridingProps(
								Map.of("throttling.hcs.getTopicInfo.tps", "0.5"))
				).then(flattened(
						inParallel(hcsQueryBurstFn.apply(MASTER)),
						inParallel(hcsQueryBurstFn.apply(GENESIS)),
						fileUpdate(APP_PROPERTIES).overridingProps(
								Map.of("throttling.hcs.getTopicInfo.tps", "33.3"))
				));
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

package com.hedera.services.bdd.suites.perf;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.LoadTest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runLoadTest;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static java.util.concurrent.TimeUnit.MINUTES;

public class MixedOpsMemoPerfSuite extends LoadTest {
	private static final Logger log = LogManager.getLogger(MixedOpsMemoPerfSuite.class);
	private final String TARGET_ACCOUNT = "accountForMemo";
	private final String TARGET_TOKEN = "tokenForMemo";
	private final String TARGET_FILE = "fileForMemo";

	public static void main(String... args) {
		parseArgs(args);

		MixedOpsMemoPerfSuite suite = new MixedOpsMemoPerfSuite();
		suite.setReportStats(true);
		suite.runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return	List.of(
				runMixedMemoOps()
		);
	}

	@Override
	public boolean hasInterestingStats() {
		return true;
	}

	// perform cryptoCreate, cryptoUpdate, TokenCreate, TokenUpdate, FileCreate, FileUpdate txs with entity memo set.
	protected HapiApiSpec runMixedMemoOps() {
		PerfTestLoadSettings settings = new PerfTestLoadSettings();
		final AtomicInteger createdSoFar = new AtomicInteger(0);
		Supplier<HapiSpecOperation[]> transferBurst = () -> new HapiSpecOperation[] {
				cryptoCreate("testAccount" + createdSoFar)
						.balance(100_000L)
						.entityMemo(
								TxnUtils.randomUtf8Bytes(memoLength.getAsInt()).toString()
						)
						.logged()
						.payingWith(GENESIS)
						.deferStatusResolution(),
				cryptoUpdate(TARGET_ACCOUNT)
						.entityMemo(
								TxnUtils.randomUtf8Bytes(memoLength.getAsInt()).toString()
						)
						.payingWith(GENESIS)
						.logged()
						.hasPrecheckFrom(
								OK, BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED)
						.deferStatusResolution(),
				tokenCreate("testToken" + createdSoFar)
						.entityMemo(
								TxnUtils.randomUtf8Bytes(memoLength.getAsInt()).toString()
						)
						.payingWith(GENESIS)
						.logged()
						.initialSupply(100_000_000_000L)
						.hasPrecheckFrom(
								OK, BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED)
						.deferStatusResolution(),
				tokenUpdate(TARGET_TOKEN)
						.entityMemo(
								TxnUtils.randomUtf8Bytes(memoLength.getAsInt()).toString()
						)
						.payingWith(GENESIS)
						.logged()
						.hasPrecheckFrom(
								OK, BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED)
						.deferStatusResolution(),
				fileCreate("testFile" + createdSoFar)
						.entityMemo(
								TxnUtils.randomUtf8Bytes(memoLength.getAsInt()).toString()
						)
						.payingWith(GENESIS)
						.logged()
						.hasPrecheckFrom(
								OK, BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED)
						.deferStatusResolution(),
				fileUpdate(TARGET_FILE)
						.entityMemo(
								TxnUtils.randomUtf8Bytes(memoLength.getAsInt()).toString()
						)
						.payingWith(GENESIS)
						.logged()
						.hasPrecheckFrom(
								OK, BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED)
						.deferStatusResolution()
		};
		return defaultHapiSpec("RunMixedMemoOps")
				.given(
						withOpContext((spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
						logIt(ignore -> settings.toString())
				)
				.when(
						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(Map.of("hapi.throttling.buckets.fastOpBucket.capacity", "1300000.0")),
						cryptoCreate(TARGET_ACCOUNT)
								.entityMemo("InitialMemo")
								.logged(),
						tokenCreate(TARGET_TOKEN)
								.entityMemo("InitialMemo")
								.logged(),
						fileCreate(TARGET_FILE)
								.entityMemo("InitialMemo")
								.logged()
				)
				.then(
						defaultLoadTest(transferBurst, settings)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

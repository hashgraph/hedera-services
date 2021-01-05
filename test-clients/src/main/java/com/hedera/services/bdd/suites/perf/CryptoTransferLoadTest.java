package com.hedera.services.bdd.suites.perf;

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
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.keyFromPem;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;

public class CryptoTransferLoadTest extends LoadTest {
	private static final Logger log = LogManager.getLogger(CryptoTransferLoadTest.class);
	private Random r = new Random();

	public static void main(String... args) {
		parseArgs(args);

		CryptoTransferLoadTest suite = new CryptoTransferLoadTest();
		suite.setReportStats(true);
		suite.runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return	List.of(
				runCryptoTransfers()
		);
	}

	@Override
	public boolean hasInterestingStats() {
		return true;
	}

	private HapiApiSpec runCryptoTransfersDeleted() {
		PerfTestLoadSettings settings = new PerfTestLoadSettings();

		Supplier<HapiSpecOperation[]> transferBurst = () -> new HapiSpecOperation[] {
				cryptoTransfer(tinyBarsFromTo("sender", "receiver", 1L))
						.noLogging()
						.payingWith("sender")
						.suppressStats(true)
						.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED)
						.deferStatusResolution()
		};

		return defaultHapiSpec("RunCryptoTransfers")
				.given(
						withOpContext((spec, log) -> settings.setFrom(spec.setup().ciPropertiesMap())),
						logIt(log -> settings.toString())
				).when(
						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(Map.of("hapi.throttling.buckets.fastOpBucket.capacity", "4000")),
						cryptoCreate("sender")
								.balance(initialBalance.getAsLong())
								.withRecharging()
								.rechargeWindow(3)
								.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED),
						cryptoCreate("receiver")
								.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED)
				).then(
						defaultLoadTest(transferBurst, settings)
				);
	}


	private HapiApiSpec runCryptoTransfers() {
		PerfTestLoadSettings settings = new PerfTestLoadSettings();

		Supplier<HapiSpecOperation[]> transferBurst = () -> {
			String sender = "sender";
			String receiver = "receiver";
			if(settings.getTotalAccounts() > 2) {
				int s =  r.nextInt(settings.getTotalAccounts());
				int re = 0;
				do {
					re =  r.nextInt(settings.getTotalAccounts());
				} while (re == s);
				sender = String.format("0.0.%d", 1001 + s);
				receiver = String.format("0.0.%d", 1001 + re);
			}

//			log.info("Total account {}, sender={}, receiver={}", settings.getTotalAccounts(), sender, receiver);

			return new HapiSpecOperation[] { cryptoTransfer(
					tinyBarsFromTo(sender, receiver , 1L))
					.noLogging()
					.payingWith(sender)
					.signedBy("simple")
					.suppressStats(true)
					.fee(100_000_000L)
					.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED,
							INVALID_SIGNATURE, PAYER_ACCOUNT_NOT_FOUND)
					.deferStatusResolution()
			};

		};

		return defaultHapiSpec("RunCryptoTransfers")
				.given(
						withOpContext((spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
						logIt(ignore -> settings.toString())
				).when(
						keyFromPem("src/main/resource/simple_test.pem").name("simple"),
						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(Map.of("hapi.throttling.buckets.fastOpBucket.capacity", "4000")),
						cryptoCreate("sender")
								.balance(initialBalance.getAsLong())
								.withRecharging()
								.key("simple")
								.rechargeWindow(3)
								.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED),
						cryptoCreate("receiver")
								.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED)
						        .key("simple")
//  The following operation is to generate some local accounts for testing purpose. If needed, uncomment this operation, run it
//  once. Then rerun the normal testing process.
//						withOpContext((spec, ignore) -> {
//							for (int i = 0; i < 10; i++) {
//								var op = cryptoCreate("acct" + i)
//										.balance(initialBalance.getAsLong())
//										.payingWith(GENESIS)
//										.key("simple")
//										.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED);
//								CustomSpecAssert.allRunFor(spec, op);
//							}
//						})
				).then(
						defaultLoadTest(transferBurst, settings)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}



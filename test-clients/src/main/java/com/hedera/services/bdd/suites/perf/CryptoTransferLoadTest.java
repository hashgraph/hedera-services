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

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_EXPIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;

public class CryptoTransferLoadTest extends LoadTest {
	private static final Logger log = LogManager.getLogger(CryptoTransferLoadTest.class);
	private Random r = new Random();
	private final static long TEST_ACCOUNT_STARTS_FROM = 1001L;
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

	protected HapiApiSpec runCryptoTransfers() {
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
				sender = String.format("0.0.%d", TEST_ACCOUNT_STARTS_FROM + s);
				receiver = String.format("0.0.%d", TEST_ACCOUNT_STARTS_FROM + re);
			}

			return new HapiSpecOperation[] { cryptoTransfer(
					tinyBarsFromTo(sender, receiver , 1L))
					.noLogging()
					.payingWith(sender)
					.signedBy(GENESIS)
					.suppressStats(true)
					.fee(100_000_000L)
					.hasKnownStatusFrom(SUCCESS, OK, INSUFFICIENT_PAYER_BALANCE
							,UNKNOWN,TRANSACTION_EXPIRED)
					.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED,
							INVALID_SIGNATURE,PAYER_ACCOUNT_NOT_FOUND)
					.deferStatusResolution()
			};
		};

		return defaultHapiSpec("RunCryptoTransfers")
				.given(
						withOpContext((spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
						logIt(ignore -> settings.toString())
				).when(
						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(Map.of("hapi.throttling.buckets.fastOpBucket.capacity", "1300000.0")),
						cryptoCreate("sender")
								.balance(initialBalance.getAsLong())
								.withRecharging()
								.key(GENESIS)
								.rechargeWindow(3)
								.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED),
						cryptoCreate("receiver")
								.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED)
						        .key(GENESIS)
				).then(
						defaultLoadTest(transferBurst, settings)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}



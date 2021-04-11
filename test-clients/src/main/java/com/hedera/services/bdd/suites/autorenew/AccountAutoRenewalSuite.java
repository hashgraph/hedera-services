package com.hedera.services.bdd.suites.autorenew;

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
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultFailingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;

public class AccountAutoRenewalSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(AccountAutoRenewalSuite.class);

	public static void main(String... args) {
		new AccountAutoRenewalSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				accountAutoRemoval(),
				accountAutoRenewal()
		);
	}

	private HapiApiSpec accountAutoRemoval() {
		String autoRemovedAccount = "autoRemovedAccount";
		return defaultFailingHapiSpec("AccountAutoRemoval")
				.given(
						fileUpdate(APP_PROPERTIES).payingWith(GENESIS)
								.overridingProps(Map.of("ledger.autoRenewPeriod.minDuration", "10",
										"autorenew.gracePeriod", "0"))
								.erasingProps(Set.of("minimumAutoRenewDuration")),
						cryptoCreate(autoRemovedAccount).autoRenewSecs(10).balance(0L),
						getAccountInfo(autoRemovedAccount).logged()
				)
				.when(
						sleepFor(15 * 1000),
						cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)).via("triggeringTransaction"),
						getTxnRecord("triggeringTransaction").logged()
				)
				.then(
						getAccountBalance(autoRemovedAccount).hasAnswerOnlyPrecheck(INVALID_ACCOUNT_ID)
				);
	}

	private HapiApiSpec accountAutoRenewal() {
		String autoRenewedAccount = "autoRenewedAccount";
		long autoRenewSecs = 10;
		long initialExpirationTime = Instant.now().getEpochSecond() + autoRenewSecs;
		long newExpirationTime = initialExpirationTime + autoRenewSecs;
		long initialBalance = ONE_HUNDRED_HBARS;
		return defaultFailingHapiSpec("AccountAutoRenewal")
				.given(
						fileUpdate(APP_PROPERTIES).payingWith(GENESIS)
								.overridingProps(Map.of(
										"ledger.autoRenewPeriod.minDuration", String.valueOf(autoRenewSecs),
										"autorenew.gracePeriod", "0"))
								.erasingProps(Set.of("minimumAutoRenewDuration")),
						cryptoCreate(autoRenewedAccount).autoRenewSecs(autoRenewSecs).balance(initialBalance),
						getAccountInfo(autoRenewedAccount).logged()
				)
				.when(
						sleepFor(15 * 1000),
						cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)).via("triggeringTransaction"),
						getTxnRecord("triggeringTransaction").logged()
				)
				.then(
						getAccountInfo(autoRenewedAccount)
								.has(accountWith()
										.expiry(newExpirationTime, 5L)
										.balanceLessThan(initialBalance)
								).logged()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

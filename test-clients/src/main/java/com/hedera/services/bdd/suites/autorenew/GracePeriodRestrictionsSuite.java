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
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;

public class GracePeriodRestrictionsSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(GracePeriodRestrictionsSuite.class);

	private static final String defaultMinAutoRenewPeriod =
			HapiSpecSetup.getDefaultNodeProps().get("ledger.autoRenewPeriod.minDuration");
	private static final String defaultGracePeriod =
			HapiSpecSetup.getDefaultNodeProps().get("autorenew.gracePeriod");

	static Map<String, String> enablingAutoRenewPropsWith(long minAutoRenewPeriod, long gracePeriod) {
		return Map.of(
				"ledger.autoRenewPeriod.minDuration", "" + minAutoRenewPeriod,
				"autorenew.isEnabled", "true",
				"autorenew.gracePeriod", "" + gracePeriod
		);
	}

	static Map<String, String> disablingAutoRenewWithDefaults() {
		return Map.of(
				"ledger.autoRenewPeriod.minDuration", defaultMinAutoRenewPeriod,
				"autorenew.isEnabled", "false",
				"autorenew.gracePeriod", defaultGracePeriod
		);
	}

	public static void main(String... args) {
		new GracePeriodRestrictionsSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						gracePeriodRestrictionsSuiteSetup(),

						restrictionsEnforced(),

//						gracePeriodRestrictionsSuiteCleanup(),
				}
		);
	}

	private HapiApiSpec restrictionsEnforced() {
		final var notToBe = "a";
		final var tokenNotYetAssociated = "b";
		final var tokenAlreadyAssociated = "c";
		final var detachedAccount = "gone";
		final var tokenAdminKey = "tak";
		final var civilian = "misc";

		return defaultHapiSpec("RestrictionsEnforced")
				.given(
						newKeyNamed(tokenAdminKey),
						cryptoCreate(civilian),
						tokenCreate(tokenNotYetAssociated)
								.adminKey(tokenAdminKey),
						tokenCreate(tokenAlreadyAssociated)
				).when(
						cryptoCreate(detachedAccount)
								.balance(0L)
								.autoRenewSecs(2),
						tokenAssociate(detachedAccount, tokenAlreadyAssociated),
						sleepFor(1_500L)
				).then(
						tokenCreate(notToBe)
								.treasury(detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						cryptoTransfer(tinyBarsFromTo(GENESIS, detachedAccount, ONE_MILLION_HBARS))
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						cryptoDelete(detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						cryptoDelete(civilian)
								.transfer(detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						tokenAssociate(detachedAccount, tokenNotYetAssociated)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
//						tokenUpdate(tokenNotYetAssociated)
//								.treasury(detachedAccount)
//								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL)
						tokenDissociate(detachedAccount, tokenAlreadyAssociated)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL)
				);
	}

	private HapiApiSpec gracePeriodRestrictionsSuiteSetup() {
		return defaultHapiSpec("GracePeriodRestrictionsSuiteSetup")
				.given().when().then(
						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(enablingAutoRenewPropsWith(1, 3600))
				);
	}

	private HapiApiSpec gracePeriodRestrictionsSuiteCleanup() {
		return defaultHapiSpec("GracePeriodRestrictionsSuiteCleanup")
				.given().when().then(
						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(disablingAutoRenewWithDefaults())
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

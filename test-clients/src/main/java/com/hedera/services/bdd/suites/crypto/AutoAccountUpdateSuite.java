package com.hedera.services.bdd.suites.crypto;

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
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAutoCreatedAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdateAliased;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromToWithAlias;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices.disablingAutoRenewWithDefaults;
import static com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices.propsForAccountAutoRenewOnWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;

public class AutoAccountUpdateSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(AutoAccountUpdateSuite.class);
	private static final long initialBalance = 1000L;

	public static void main(String... args) {
		new AutoAccountUpdateSuite().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						updateKeyOnAutoCreatedAccount(),
//						accountCreatedAfterAliasAccountExpires(),
//						modifySigRequiredAfterAutoAccountCreation(),
//						accountCreatedAfterAliasAccountExpiresAndDelete()
				}
		);
	}

	private HapiApiSpec modifySigRequiredAfterAutoAccountCreation() {
		return defaultHapiSpec("modifySigRequiredAfterAutoAccountCreation")
				.given(
						newKeyNamed("testAlias"),
						cryptoCreate("payer").balance(initialBalance * ONE_HBAR)
				).when(
						cryptoTransfer(tinyBarsFromToWithAlias("payer", "testAlias", ONE_HUNDRED_HBARS)).via(
								"transferTxn"),
						/* validate child record has alias set and has fields as expected */
						getTxnRecord("transferTxn").andAllChildRecords()
								.hasNonStakingChildRecordCount(1)
								.hasAliasInChildRecord("testAlias", 0).logged(),
						getAliasedAccountInfo("testAlias").has(accountWith()
								.autoRenew(THREE_MONTHS_IN_SECONDS)
								.receiverSigReq(false)
								.expectedBalanceWithChargedUsd((ONE_HUNDRED_HBARS), 0.05, 10))
				).then(
						/* change receiverSigRequired to false and validate */
						cryptoUpdateAliased("testAlias").receiverSigRequired(true).signedBy(
								"testAlias", "payer", DEFAULT_PAYER),
						getAliasedAccountInfo("testAlias").has(accountWith()
								.autoRenew(THREE_MONTHS_IN_SECONDS)
								.receiverSigReq(true)
								.expectedBalanceWithChargedUsd((ONE_HUNDRED_HBARS), 0.05, 10)),

						/* transfer without receiver sig fails */
						cryptoTransfer(tinyBarsFromToWithAlias("payer", "testAlias", ONE_HUNDRED_HBARS))
								.via("transferTxn2")
								.signedBy("payer", DEFAULT_PAYER)
								.hasKnownStatus(INVALID_SIGNATURE),

						/* transfer with receiver sig passes */
						cryptoTransfer(tinyBarsFromToWithAlias("payer", "testAlias", ONE_HUNDRED_HBARS))
								.via("transferTxn3")
								.signedBy("testAlias", "payer", DEFAULT_PAYER),
						getTxnRecord("transferTxn3").andAllChildRecords().hasNonStakingChildRecordCount(0),
						getAliasedAccountInfo("testAlias").has(
								accountWith()
										.expectedBalanceWithChargedUsd((2 * ONE_HUNDRED_HBARS), 0.05, 10))
				);
	}

	private HapiApiSpec accountCreatedAfterAliasAccountExpires() {
		final var briefAutoRenew = 3L;
		return defaultHapiSpec("AccountCreatedAfterAliasAccountExpires")
				.given(
						newKeyNamed("alias"),
						fileUpdate(APP_PROPERTIES).payingWith(GENESIS)
								.overridingProps(propsForAccountAutoRenewOnWith(briefAutoRenew, 20 * briefAutoRenew)),
						cryptoCreate("randomPayer").balance(initialBalance * ONE_HBAR)
				).when(
						/* auto account is created */
						cryptoTransfer(tinyBarsFromToWithAlias("randomPayer", "alias", ONE_HUNDRED_HBARS)).via(
								"transferTxn"),
						getTxnRecord("transferTxn").andAllChildRecords().logged(),
						getAliasedAccountInfo("alias").has(accountWith()
								.autoRenew(THREE_MONTHS_IN_SECONDS)
								.expectedBalanceWithChargedUsd((ONE_HUNDRED_HBARS), 0.05, 10))
				).then(
						/* update auto renew period */
						cryptoUpdateAliased("alias").autoRenewPeriod(briefAutoRenew).signedBy(
								"alias", "randomPayer", DEFAULT_PAYER),
						sleepFor(2 * briefAutoRenew * 1_000L + 500L),
						getAutoCreatedAccountBalance("alias"),

						/* account is expired but not deleted and validate the transfer succeeds*/
						cryptoTransfer(tinyBarsFromToWithAlias("randomPayer", "alias", ONE_HUNDRED_HBARS)).via(
								"transferTxn2"),
						getTxnRecord("transferTxn2").andAllChildRecords().hasNonStakingChildRecordCount(0),
						getAliasedAccountInfo("alias").has(
								accountWith()
										.expectedBalanceWithChargedUsd((2 * ONE_HUNDRED_HBARS), 0.05, 10)),

						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(disablingAutoRenewWithDefaults())
				);
	}

	private HapiApiSpec updateKeyOnAutoCreatedAccount() {
		SigControl ENOUGH_UNIQUE_SIGS = KeyShape.threshSigs(2,
				KeyShape.threshSigs(1, OFF, OFF, OFF, OFF, OFF, OFF, ON),
				KeyShape.threshSigs(3, ON, ON, ON, OFF, OFF, OFF, OFF));

		return defaultHapiSpec("updateKeyOnAutoCreatedAccount")
				.given(
						newKeyNamed("alias"),
						newKeyNamed("complexKey").shape(ENOUGH_UNIQUE_SIGS),
						cryptoCreate("payer").balance(initialBalance * ONE_HBAR)
				).when(
						/* auto account is created */
						cryptoTransfer(
								tinyBarsFromToWithAlias("payer", "alias", ONE_HUNDRED_HBARS)).via(
								"transferTxn"),

						getTxnRecord("transferTxn").andAllChildRecords().logged(),
						getAliasedAccountInfo("alias").has(accountWith()
								.expectedBalanceWithChargedUsd(ONE_HUNDRED_HBARS, 0.05, 10)
								.alias("alias"))
				).then(
						/* validate the key on account can be updated to complex key, and has no relation to alias*/
						cryptoUpdateAliased("alias")
								.key("complexKey")
								.payingWith("payer")
								.signedBy("alias", "complexKey", "payer", DEFAULT_PAYER),
						getAliasedAccountInfo("alias").has(
								accountWith()
										.expectedBalanceWithChargedUsd((ONE_HUNDRED_HBARS), 0.05, 10)
										.key("complexKey")));
	}

	// Can't be done without property change, since auto-renew period can't be reduced from 3 months after create.
	private HapiApiSpec accountCreatedAfterAliasAccountExpiresAndDelete() {
		final var briefAutoRenew = 3L;
		return defaultHapiSpec("AccountCreatedAfterAliasAccountExpiresAndDelete")
				.given(
						newKeyNamed("alias"),
						fileUpdate(APP_PROPERTIES).payingWith(GENESIS)
								.overridingProps(propsForAccountAutoRenewOnWith(briefAutoRenew, 2 * briefAutoRenew)),
						cryptoCreate("randomPayer").balance(initialBalance * ONE_HBAR)
				).when(
						cryptoTransfer(tinyBarsFromToWithAlias("randomPayer", "alias", ONE_HUNDRED_HBARS)).via(
								"transferTxn"),
						getTxnRecord("transferTxn").andAllChildRecords().logged(),
						getAliasedAccountInfo("alias").has(accountWith().autoRenew(THREE_MONTHS_IN_SECONDS))
				).then(
						/* update auto renew period */
						cryptoUpdateAliased("alias").autoRenewPeriod(briefAutoRenew).signedBy(
								"alias", "randomPayer"),
						sleepFor(2 * briefAutoRenew * 1_000L + 500L),
						getAutoCreatedAccountBalance("alias").hasAnswerOnlyPrecheck(INVALID_ACCOUNT_ID),

						// Need to know why its INVALID_ACCOUNT_ID, same reason as Delete

						/* validate account is expired and deleted , so new account is created */
						cryptoTransfer(tinyBarsFromToWithAlias("randomPayer", "alias", ONE_HUNDRED_HBARS)).via(
								"transferTxn2"),
						getTxnRecord("transferTxn2").andAllChildRecords().hasNonStakingChildRecordCount(1),

						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(disablingAutoRenewWithDefaults())
				);
	}

}

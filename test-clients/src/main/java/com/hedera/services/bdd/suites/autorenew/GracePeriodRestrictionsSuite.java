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
import com.hederahashgraph.api.proto.java.AccountID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createDefaultContract;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.explicitContractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.revokeTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.updateTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices.disablingAutoRenewWith;
import static com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices.propsForAccountAutoRenewOnWith;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;

public class GracePeriodRestrictionsSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(GracePeriodRestrictionsSuite.class);

	public static void main(String... args) {
		new GracePeriodRestrictionsSuite().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						gracePeriodRestrictionsSuiteSetup(),

						contractCallRestrictionsEnforced(),
						payerRestrictionsEnforced(),
						cryptoTransferRestrictionsEnforced(),
						tokenMgmtRestrictionsEnforced(),
						cryptoAndContractDeleteRestrictionsEnforced(),
						treasuryOpsRestrictionEnforced(),
						tokenAutoRenewOpsEnforced(),
						topicAutoRenewOpsEnforced(),
						cryptoUpdateRestrictionsEnforced(),

						gracePeriodRestrictionsSuiteCleanup(),
				}
		);
	}

	private HapiApiSpec contractCallRestrictionsEnforced() {
		final var civilian = "misc";
		final var detachedAccount = "gone";
		final var contract = "DoubleSend";
		final AtomicReference<AccountID> detachedAccountID = new AtomicReference();
		final AtomicReference<AccountID> civilianAccountID = new AtomicReference();

		return defaultHapiSpec("ContractCallRestrictionsEnforced")
				.given(
						uploadInitCode(contract),
						contractCreate(contract)
								.balance(ONE_HBAR),
						cryptoCreate(civilian)
								.balance(0L),
						cryptoCreate(detachedAccount)
								.balance(0L)
								.autoRenewSecs(1)
				).when(
						sleepFor(1_500L),
						cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L)),
						withOpContext((spec, opLog) -> {
							detachedAccountID.set(spec.registry().getAccountID(detachedAccount));
							civilianAccountID.set(spec.registry().getAccountID(civilian));
						}),
						sourcing(() -> explicitContractCall(contract, getABIFor(FUNCTION, "donate", contract), new Object[] {
								asAddress(civilianAccountID.get()), asAddress(detachedAccountID.get())
						})
								.hasKnownStatus(INVALID_SOLIDITY_ADDRESS)),
						getAccountBalance(civilian).hasTinyBars(0L),
						getAccountBalance(detachedAccount).hasTinyBars(0L)
				).then(
						cryptoUpdate(detachedAccount)
								.expiring(Instant.now().getEpochSecond() + THREE_MONTHS_IN_SECONDS),
						sourcing(() -> explicitContractCall(contract, getABIFor(FUNCTION, "donate", contract), new Object[] {
								asAddress(civilianAccountID.get()), asAddress(detachedAccountID.get())
						})),
						getAccountBalance(civilian).hasTinyBars(1L),
						getAccountBalance(detachedAccount).hasTinyBars(1L)
				);
	}

	private HapiApiSpec cryptoUpdateRestrictionsEnforced() {
		final var detachedAccount = "gone";
		final long certainlyPast = Instant.now().getEpochSecond() - THREE_MONTHS_IN_SECONDS;
		final long certainlyDistant = Instant.now().getEpochSecond() + THREE_MONTHS_IN_SECONDS;

		return defaultHapiSpec("CryptoUpdateRestrictionsEnforced")
				.given(
						newKeyNamed("ntb"),
						cryptoCreate(detachedAccount)
								.balance(0L)
								.autoRenewSecs(1),
						sleepFor(1_500L),
						cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L))
				).when(
						cryptoUpdate(detachedAccount)
								.memo("Can't update receiverSigRequired")
								.receiverSigRequired(true)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						cryptoUpdate(detachedAccount)
								.memo("Can't update key")
								.key("ntb")
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						cryptoUpdate(detachedAccount)
								.memo("Can't update auto-renew period")
								.autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						cryptoUpdate(detachedAccount)
								.memo("Can't update memo")
								.entityMemo("NOPE")
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						cryptoUpdate(detachedAccount)
								.memo("Can't update with past expiry")
								.expiring(certainlyPast)
								.hasKnownStatus(INVALID_EXPIRATION_TIME),
						cryptoUpdate(detachedAccount)
								.memo("CAN extend expiry")
								.expiring(certainlyDistant)
				).then(
						cryptoUpdate(detachedAccount)
								.memo("Should work now!")
								.receiverSigRequired(true),
						cryptoUpdate(detachedAccount)
								.key("ntb"),
						cryptoUpdate(detachedAccount)
								.autoRenewPeriod(THREE_MONTHS_IN_SECONDS),
						cryptoUpdate(detachedAccount)
								.entityMemo("NOPE"),
						cryptoUpdate(detachedAccount)
								.expiring(certainlyDistant - 1_234L)
								.hasKnownStatus(EXPIRATION_REDUCTION_NOT_ALLOWED)
				);
	}

	private HapiApiSpec payerRestrictionsEnforced() {
		final var detachedAccount = "gone";

		return defaultHapiSpec("PayerRestrictionsEnforced")
				.given(
						cryptoCreate(detachedAccount)
								.balance(0L)
								.autoRenewSecs(1)
				).when(
						sleepFor(1_500L),
						cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L))
				).then(
						cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
								.payingWith(detachedAccount)
								.hasPrecheck(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						getAccountInfo("0.0.2")
								.payingWith(detachedAccount)
								.hasAnswerOnlyPrecheck(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						getAccountInfo("0.0.2")
								.payingWith(detachedAccount)
								.nodePayment(666L)
								.hasAnswerOnlyPrecheck(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						scheduleCreate("notToBe",
								cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1))
						)
								.designatingPayer(detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL)

				);
	}

	private HapiApiSpec topicAutoRenewOpsEnforced() {
		final var topicWithDetachedAsAutoRenew = "c";
		final var topicSansDetachedAsAutoRenew = "d";
		final var detachedAccount = "gone";
		final var adminKey = "tak";
		final var civilian = "misc";
		final var notToBe = "nope";

		return defaultHapiSpec("TopicAutoRenewOpsEnforced")
				.given(
						newKeyNamed(adminKey),
						cryptoCreate(civilian)
				).when(
						cryptoCreate(detachedAccount)
								.balance(0L)
								.autoRenewSecs(4),
						createTopic(topicWithDetachedAsAutoRenew)
								.adminKeyName(adminKey)
								.autoRenewAccountId(detachedAccount),
						createTopic(topicSansDetachedAsAutoRenew)
								.adminKeyName(adminKey)
								.autoRenewAccountId(civilian),
						sleepFor(3_500L)
				).then(
						createTopic(notToBe)
								.adminKeyName(adminKey)
								.autoRenewAccountId(detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						updateTopic(topicWithDetachedAsAutoRenew)
								.autoRenewAccountId(civilian)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						updateTopic(topicSansDetachedAsAutoRenew)
								.autoRenewAccountId(detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						getTopicInfo(topicSansDetachedAsAutoRenew)
								.hasAutoRenewAccount(civilian),
						getTopicInfo(topicWithDetachedAsAutoRenew)
								.hasAutoRenewAccount(detachedAccount)
				);
	}

	private HapiApiSpec tokenAutoRenewOpsEnforced() {
		final var tokenWithDetachedAsAutoRenew = "c";
		final var tokenSansDetachedAsAutoRenew = "d";
		final var detachedAccount = "gone";
		final var adminKey = "tak";
		final var civilian = "misc";
		final var notToBe = "nope";

		return defaultHapiSpec("TokenAutoRenewOpsEnforced")
				.given(
						newKeyNamed(adminKey),
						cryptoCreate(civilian)
				).when(
						cryptoCreate(detachedAccount)
								.balance(0L)
								.autoRenewSecs(3),
						tokenCreate(tokenWithDetachedAsAutoRenew)
								.adminKey(adminKey)
								.autoRenewAccount(detachedAccount),
						tokenCreate(tokenSansDetachedAsAutoRenew)
								.autoRenewAccount(civilian)
								.adminKey(adminKey),
						sleepFor(2_500L)
				).then(
						tokenCreate(notToBe)
								.autoRenewAccount(detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						tokenUpdate(tokenWithDetachedAsAutoRenew)
								.autoRenewAccount(civilian)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						tokenUpdate(tokenSansDetachedAsAutoRenew)
								.autoRenewAccount(detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						getTokenInfo(tokenSansDetachedAsAutoRenew)
								.hasAutoRenewAccount(civilian),
						getTokenInfo(tokenWithDetachedAsAutoRenew)
								.hasAutoRenewAccount(detachedAccount)
				);
	}

	private HapiApiSpec treasuryOpsRestrictionEnforced() {
		final var aToken = "c";
		final var detachedAccount = "gone";
		final var tokenMultiKey = "tak";
		final var civilian = "misc";
		final long expectedSupply = 1_234L;

		return defaultHapiSpec("TreasuryOpsRestrictionEnforced")
				.given(
						newKeyNamed(tokenMultiKey),
						cryptoCreate(civilian)
				).when(
						cryptoCreate(detachedAccount)
								.balance(0L)
								.autoRenewSecs(5),
						tokenCreate(aToken)
								.adminKey(tokenMultiKey)
								.supplyKey(tokenMultiKey)
								.initialSupply(expectedSupply)
								.treasury(detachedAccount),
						tokenAssociate(civilian, aToken),
						sleepFor(4_500L)
				).then(
						tokenUpdate(aToken)
								.treasury(civilian)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						mintToken(aToken, 1L)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						burnToken(aToken, 1L)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						getTokenInfo(aToken)
								.hasTreasury(detachedAccount),
						getAccountBalance(detachedAccount)
								.hasTokenBalance(aToken, expectedSupply)
				);
	}

	private HapiApiSpec tokenMgmtRestrictionsEnforced() {
		final var notToBe = "a";
		final var tokenNotYetAssociated = "b";
		final var tokenAlreadyAssociated = "c";
		final var detachedAccount = "gone";
		final var tokenMultiKey = "tak";
		final var civilian = "misc";

		return defaultHapiSpec("TokenMgmtRestrictionsEnforced")
				.given(
						newKeyNamed(tokenMultiKey),
						cryptoCreate(civilian),
						tokenCreate(tokenNotYetAssociated)
								.adminKey(tokenMultiKey),
						tokenCreate(tokenAlreadyAssociated)
								.freezeKey(tokenMultiKey)
								.kycKey(tokenMultiKey)
				).when(
						cryptoCreate(detachedAccount)
								.balance(0L)
								.autoRenewSecs(4),
						tokenAssociate(detachedAccount, tokenAlreadyAssociated),
						sleepFor(3_500L)
				).then(
						tokenCreate(notToBe)
								.treasury(detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						tokenUnfreeze(tokenAlreadyAssociated, detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						tokenFreeze(tokenAlreadyAssociated, detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						grantTokenKyc(tokenAlreadyAssociated, detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						revokeTokenKyc(tokenAlreadyAssociated, detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						tokenAssociate(detachedAccount, tokenNotYetAssociated)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						tokenUpdate(tokenNotYetAssociated)
								.treasury(detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						tokenDissociate(detachedAccount, tokenAlreadyAssociated)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL)
				);
	}

	private HapiApiSpec cryptoAndContractDeleteRestrictionsEnforced() {
		final var detachedAccount = "gone";
		final var civilian = "misc";
		final var tbd = "contract";
		final var adminKey = "tac";

		return defaultHapiSpec("CryptoAndContractDeleteRestrictionsEnforced")
				.given(
						newKeyNamed(adminKey),
						createDefaultContract(tbd).adminKey(adminKey),
						cryptoCreate(civilian),
						cryptoCreate(detachedAccount)
								.balance(0L)
								.autoRenewSecs(2)
				).when(
						sleepFor(1_500L)
				).then(
						cryptoDelete(detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						cryptoDelete(civilian)
								.transfer(detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						contractDelete(tbd)
								.transferAccount(detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL)
				);
	}

	private HapiApiSpec cryptoTransferRestrictionsEnforced() {
		final var aToken = "c";
		final var detachedAccount = "gone";
		final var civilian = "misc";

		return defaultHapiSpec("CryptoTransferRestrictionsEnforced")
				.given(
						cryptoCreate(civilian),
						cryptoCreate(detachedAccount)
								.balance(0L)
								.autoRenewSecs(4),
						tokenCreate(aToken)
								.treasury(detachedAccount),
						tokenAssociate(civilian, aToken)
				).when(
						sleepFor(3_500L)
				).then(
						cryptoTransfer(tinyBarsFromTo(GENESIS, detachedAccount, ONE_MILLION_HBARS))
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						cryptoTransfer(moving(1, aToken).between(detachedAccount, civilian))
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL)
				);
	}

	private HapiApiSpec gracePeriodRestrictionsSuiteSetup() {
		return defaultHapiSpec("GracePeriodRestrictionsSuiteSetup")
				.given().when().then(
						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(propsForAccountAutoRenewOnWith(1, 3600))
				);
	}

	private HapiApiSpec gracePeriodRestrictionsSuiteCleanup() {
		return defaultHapiSpec("GracePeriodRestrictionsSuiteCleanup")
				.given().when().then(
						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(disablingAutoRenewWith(10L))
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

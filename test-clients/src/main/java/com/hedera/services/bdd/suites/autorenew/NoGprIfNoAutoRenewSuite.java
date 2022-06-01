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
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
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
import static com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices.disablingAutoRenewWithDefaults;
import static com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices.leavingAutoRenewDisabledWith;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;

public class NoGprIfNoAutoRenewSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(NoGprIfNoAutoRenewSuite.class);

	public static void main(String... args) {
		new NoGprIfNoAutoRenewSuite().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						noGracePeriodRestrictionsIfNoAutoRenewSuiteSetup(),

						payerRestrictionsNotEnforced(),
						cryptoTransferRestrictionsNotEnforced(),
						tokenMgmtRestrictionsNotEnforced(),
						cryptoDeleteRestrictionsNotEnforced(),
						treasuryOpsRestrictionNotEnforced(),
						tokenAutoRenewOpsNotEnforced(),
						topicAutoRenewOpsNotEnforced(),
						cryptoUpdateRestrictionsNotEnforced(),
						contractCallRestrictionsNotEnforced(),

						noGracePeriodRestrictionsIfNoAutoRenewSuiteCleanup(),
				}
		);
	}

	private HapiApiSpec contractCallRestrictionsNotEnforced() {
		final var civilian = "misc";
		final var notDetachedAccount = "gone";
		final var contract = "DoubleSend";
		final AtomicInteger detachedNum = new AtomicInteger();
		final AtomicInteger civilianNum = new AtomicInteger();

		return defaultHapiSpec("ContractCallRestrictionsNotEnforced")
				.given(
						uploadInitCode(contract),
						contractCreate(contract)
								.balance(ONE_HBAR),
						cryptoCreate(civilian)
								.balance(0L),
						cryptoCreate(notDetachedAccount)
								.balance(0L)
								.autoRenewSecs(1)
				).when(
						sleepFor(1_500L),
						cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L)),
						withOpContext((spec, opLog) -> {
							detachedNum.set((int) spec.registry().getAccountID(notDetachedAccount).getAccountNum());
							civilianNum.set((int) spec.registry().getAccountID(civilian).getAccountNum());
						}),
						sourcing(() -> contractCall(contract, getABIFor(FUNCTION, "donate", contract), new Object[] {
								civilianNum.get(), detachedNum.get()
						}))
				).then(
								getAccountBalance(civilian).hasTinyBars(1L),
								getAccountBalance(notDetachedAccount).hasTinyBars(1L)
				);
	}

	private HapiApiSpec cryptoUpdateRestrictionsNotEnforced() {
		final var notDetachedAccount = "gone";
		final long certainlyPast = Instant.now().getEpochSecond() - THREE_MONTHS_IN_SECONDS;
		final long certainlyDistant = Instant.now().getEpochSecond() + THREE_MONTHS_IN_SECONDS;

		return defaultHapiSpec("CryptoUpdateRestrictionsNotEnforced")
				.given(
						newKeyNamed("ntb"),
						cryptoCreate(notDetachedAccount)
								.balance(0L)
								.autoRenewSecs(1),
						sleepFor(1_500L),
						cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L))
				).when(
						cryptoUpdate(notDetachedAccount)
								.memo("Can update receiverSigRequired")
								.receiverSigRequired(true),
						cryptoUpdate(notDetachedAccount)
								.memo("Can update key")
								.key("ntb"),
						cryptoUpdate(notDetachedAccount)
								.memo("Can update auto-renew period")
								.autoRenewPeriod(THREE_MONTHS_IN_SECONDS),
						cryptoUpdate(notDetachedAccount)
								.memo("Can update memo")
								.entityMemo("NOPE"),
						cryptoUpdate(notDetachedAccount)
								.memo("Can't pass precheck with past expiry")
								.expiring(certainlyPast)
								.hasPrecheck(INVALID_EXPIRATION_TIME)
				).then(
						cryptoUpdate(notDetachedAccount)
								.memo("CAN extend expiry")
								.expiring(certainlyDistant),
						cryptoUpdate(notDetachedAccount)
								.expiring(certainlyDistant - 1_234L)
								.hasKnownStatus(EXPIRATION_REDUCTION_NOT_ALLOWED)
				);
	}

	private HapiApiSpec payerRestrictionsNotEnforced() {
		final var notDetachedAccount = "gone";

		return defaultHapiSpec("PayerRestrictionsEnforced")
				.given(
						cryptoCreate(notDetachedAccount)
								.balance(0L)
								.autoRenewSecs(1)
				).when(
						sleepFor(1_500L),
						cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L))
				).then(
						cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
								.payingWith(notDetachedAccount)
								.hasPrecheck(INSUFFICIENT_PAYER_BALANCE),
						getAccountInfo("0.0.2")
								.payingWith(notDetachedAccount)
								.hasCostAnswerPrecheck(INSUFFICIENT_PAYER_BALANCE),
						getAccountInfo("0.0.2")
								.payingWith(notDetachedAccount)
								.nodePayment(666L)
								.hasAnswerOnlyPrecheck(INSUFFICIENT_PAYER_BALANCE),
						scheduleCreate("notEnoughMoney",
								cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1))
										.memo(TxnUtils.randomUppercase(32))
						)
								.via("creation")
								.designatingPayer(notDetachedAccount)
								.alsoSigningWith(notDetachedAccount),
						getTxnRecord("creation").scheduled()
								.hasPriority(recordWith().status(INSUFFICIENT_PAYER_BALANCE))
				);
	}

	private HapiApiSpec topicAutoRenewOpsNotEnforced() {
		final var topicWithDetachedAsAutoRenew = "c";
		final var topicSansDetachedAsAutoRenew = "d";
		final var notDetachedAccount = "gone";
		final var adminKey = "tak";
		final var civilian = "misc";
		final var onTheFly = "nope";

		return defaultHapiSpec("TopicAutoRenewOpsNotEnforced")
				.given(
						newKeyNamed(adminKey),
						cryptoCreate(civilian)
				).when(
						cryptoCreate(notDetachedAccount)
								.balance(0L)
								.autoRenewSecs(2),
						createTopic(topicWithDetachedAsAutoRenew)
								.adminKeyName(adminKey)
								.autoRenewAccountId(notDetachedAccount),
						createTopic(topicSansDetachedAsAutoRenew)
								.adminKeyName(adminKey)
								.autoRenewAccountId(civilian),
						sleepFor(1_500L)
				).then(
						createTopic(onTheFly)
								.adminKeyName(adminKey)
								.autoRenewAccountId(notDetachedAccount),
						updateTopic(topicWithDetachedAsAutoRenew)
								.autoRenewAccountId(civilian),
						updateTopic(topicSansDetachedAsAutoRenew)
								.autoRenewAccountId(notDetachedAccount),
						getTopicInfo(topicSansDetachedAsAutoRenew)
								.hasAutoRenewAccount(notDetachedAccount),
						getTopicInfo(topicWithDetachedAsAutoRenew)
								.hasAutoRenewAccount(civilian)
				);
	}

	private HapiApiSpec tokenAutoRenewOpsNotEnforced() {
		final var tokenWithDetachedAsAutoRenew = "c";
		final var tokenSansDetachedAsAutoRenew = "d";
		final var notDetachedAccount = "gone";
		final var adminKey = "tak";
		final var civilian = "misc";
		final var notToBe = "nope";

		return defaultHapiSpec("TokenAutoRenewOpsNotEnforced")
				.given(
						newKeyNamed(adminKey),
						cryptoCreate(civilian)
				).when(
						cryptoCreate(notDetachedAccount)
								.balance(0L)
								.autoRenewSecs(2),
						tokenCreate(tokenWithDetachedAsAutoRenew)
								.adminKey(adminKey)
								.autoRenewAccount(notDetachedAccount),
						tokenCreate(tokenSansDetachedAsAutoRenew)
								.autoRenewAccount(civilian)
								.adminKey(adminKey),
						sleepFor(1_500L)
				).then(
						tokenCreate(notToBe)
								.autoRenewAccount(notDetachedAccount),
						tokenUpdate(tokenWithDetachedAsAutoRenew)
								.autoRenewAccount(civilian),
						tokenUpdate(tokenSansDetachedAsAutoRenew)
								.autoRenewAccount(notDetachedAccount),
						getTokenInfo(tokenSansDetachedAsAutoRenew)
								.hasAutoRenewAccount(notDetachedAccount),
						getTokenInfo(tokenWithDetachedAsAutoRenew)
								.hasAutoRenewAccount(civilian)
				);
	}

	private HapiApiSpec treasuryOpsRestrictionNotEnforced() {
		final var aToken = "c";
		final var notDetachedAccount = "gone";
		final var tokenMultiKey = "tak";
		final var civilian = "misc";
		final long expectedSupply = 1_234L;

		return defaultHapiSpec("TreasuryOpsRestrictionNotEnforced")
				.given(
						newKeyNamed(tokenMultiKey),
						cryptoCreate(civilian)
				).when(
						cryptoCreate(notDetachedAccount)
								.balance(0L)
								.autoRenewSecs(2),
						tokenCreate(aToken)
								.adminKey(tokenMultiKey)
								.supplyKey(tokenMultiKey)
								.initialSupply(expectedSupply)
								.treasury(notDetachedAccount),
						tokenAssociate(civilian, aToken),
						sleepFor(1_500L)
				).then(
						tokenUpdate(aToken)
								.treasury(civilian),
						mintToken(aToken, 1L),
						burnToken(aToken, 1L),
						getTokenInfo(aToken).hasTreasury(civilian),
						getAccountBalance(notDetachedAccount).hasTokenBalance(aToken, 0L)
				);
	}

	private HapiApiSpec tokenMgmtRestrictionsNotEnforced() {
		final var onTheFly = "a";
		final var tokenNotYetAssociated = "b";
		final var tokenAlreadyAssociated = "c";
		final var notDetachedAccount = "gone";
		final var tokenMultiKey = "tak";
		final var civilian = "misc";

		return defaultHapiSpec("TokenMgmtRestrictionsNotEnforced")
				.given(
						newKeyNamed(tokenMultiKey),
						cryptoCreate(civilian),
						tokenCreate(tokenNotYetAssociated)
								.adminKey(tokenMultiKey),
						tokenCreate(tokenAlreadyAssociated)
								.freezeKey(tokenMultiKey)
								.kycKey(tokenMultiKey)
				).when(
						cryptoCreate(notDetachedAccount)
								.balance(0L)
								.autoRenewSecs(2),
						tokenAssociate(notDetachedAccount, tokenAlreadyAssociated),
						sleepFor(1_500L)
				).then(
						tokenCreate(onTheFly)
								.treasury(notDetachedAccount),
						tokenUnfreeze(tokenAlreadyAssociated, notDetachedAccount),
						tokenFreeze(tokenAlreadyAssociated, notDetachedAccount),
						grantTokenKyc(tokenAlreadyAssociated, notDetachedAccount),
						revokeTokenKyc(tokenAlreadyAssociated, notDetachedAccount),
						tokenAssociate(notDetachedAccount, tokenNotYetAssociated),
						tokenUpdate(tokenNotYetAssociated)
								.treasury(notDetachedAccount),
						tokenDissociate(notDetachedAccount, tokenAlreadyAssociated)
								.hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN)
				);
	}

	private HapiApiSpec cryptoDeleteRestrictionsNotEnforced() {
		final var notDetachedAccount = "gone";
		final var civilian = "misc";

		return defaultHapiSpec("CryptoDeleteRestrictionsNotEnforced")
				.given(
						cryptoCreate(civilian),
						cryptoCreate(notDetachedAccount)
								.balance(0L)
								.autoRenewSecs(2)
				).when(
						sleepFor(1_500L)
				).then(
						cryptoDelete(notDetachedAccount),
						cryptoDelete(civilian)
								.transfer(notDetachedAccount)
								.hasKnownStatus(ACCOUNT_DELETED)
				);
	}

	private HapiApiSpec cryptoTransferRestrictionsNotEnforced() {
		final var aToken = "c";
		final var notDetachedAccount = "gone";
		final var civilian = "misc";

		return defaultHapiSpec("CryptoTransferRestrictionsNotEnforced")
				.given(
						cryptoCreate(civilian),
						cryptoCreate(notDetachedAccount)
								.balance(0L)
								.autoRenewSecs(2),
						tokenCreate(aToken)
								.treasury(notDetachedAccount),
						tokenAssociate(civilian, aToken)
				).when(
						sleepFor(1_500L)
				).then(
						cryptoTransfer(tinyBarsFromTo(GENESIS, notDetachedAccount, ONE_HBAR)),
						cryptoTransfer(moving(1, aToken).between(notDetachedAccount, civilian))
				);
	}

	private HapiApiSpec noGracePeriodRestrictionsIfNoAutoRenewSuiteSetup() {
		return defaultHapiSpec("NoGracePeriodRestrictionsIfNoAutoRenewSuiteSetup")
				.given().when().then(
						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(leavingAutoRenewDisabledWith(1))
				);
	}

	private HapiApiSpec noGracePeriodRestrictionsIfNoAutoRenewSuiteCleanup() {
		return defaultHapiSpec("NoGracePeriodRestrictionsIfNoAutoRenewSuiteCleanup")
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

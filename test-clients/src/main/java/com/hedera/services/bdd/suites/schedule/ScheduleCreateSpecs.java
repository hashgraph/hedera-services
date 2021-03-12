package com.hedera.services.bdd.suites.schedule;

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
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreateFunctionless;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreateNonsense;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.ensureDifferentScheduledTXCreated;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.ensureIdempotentlyCreated;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyListNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.saveExpirations;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SOME_SIGNATURES_WERE_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNPARSEABLE_SCHEDULED_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNSCHEDULABLE_TRANSACTION;

public class ScheduleCreateSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ScheduleCreateSpecs.class);
	private static final int SCHEDULE_EXPIRY_TIME_SECS = 10;

	private static final String defaultWhitelist =
			HapiSpecSetup.getDefaultNodeProps().get("scheduling.whitelist");
	private static final String defaultTxExpiry =
			HapiSpecSetup.getDefaultNodeProps().get("ledger.schedule.txExpiryTimeSecs");

	public static void main(String... args) {
		new ScheduleCreateSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
//				suiteSetup(),
//				bodyOnlyCreation(),
//				onlyBodyAndAdminCreation(),
//				onlyBodyAndMemoCreation(),
				bodyAndSignatoriesCreation(),
//				bodyAndPayerCreation(),
//				nestedScheduleCreateFails(),
//				nestedScheduleSignFails(),
//				scheduledTXWithDifferentAdminAndPayerAreNotIdempotentlyCreated(),
//				scheduledTXWithDifferentPayerAreNotIdempotentlyCreated(),
//				scheduledTXWithDifferentAdminAreNotIdempotentlyCreated(),
//				scheduledTXWithDifferentMemoAreNotIdempotentlyCreated(),
//				idempotentCreationWithBodyOnly(),
//				idempotentCreationWithBodyAndPayer(),
//				idempotentCreationWithBodyAndAdmin(),
//				idempotentCreationWithBodyAndMemo(),
//				idempotentCreationWhenAllPropsAreTheSame(),
//				rejectsUnparseableTxn(),
//				rejectsUnresolvableReqSigners(),
//				triggersImmediatelyWithBothReqSimpleSigs(),
//				onlySchedulesWithMissingReqSimpleSigs(),
//				failsWithNonExistingPayerAccountId(),
//				failsWithTooLongMemo(),
//				detectsKeysChangedBetweenExpandSigsAndHandleTxn(),
//				retestsActivationOnCreateWithEmptySigMap(),
//				doesntTriggerUntilPayerSigns(),
//				requiresExtantPayer(),
//				rejectsFunctionlessTxn(),
//				whitelistWorks(),
//				allowsDoublingScheduledCreates(),
//				scheduledTXCreatedAfterPreviousIdenticalIsExecuted(),
//				preservesRevocationServiceSemanticsForFileDelete(),
//				worksAsExpectedWithDefaultScheduleId(),
//				infoIncludesTxnIdFromCreationReceipt(),
//				suiteCleanup(),
		});
	}

	private HapiApiSpec suiteSetup() {
		return defaultHapiSpec("suiteSetup")
				.given().when().then(
						overriding("ledger.schedule.txExpiryTimeSecs", "" + SCHEDULE_EXPIRY_TIME_SECS)
				);
	}

	private HapiApiSpec suiteCleanup() {
		return defaultHapiSpec("suiteCleanup")
				.given().when().then(
						overriding("ledger.schedule.txExpiryTimeSecs", defaultTxExpiry)
				);
	}

	private HapiApiSpec worksAsExpectedWithDefaultScheduleId() {
		return defaultHapiSpec("WorksAsExpectedWithDefaultScheduleId")
				.given().when().then(
						getScheduleInfo("0.0.0").hasCostAnswerPrecheck(INVALID_SCHEDULE_ID)
				);
	}

	private HapiApiSpec bodyOnlyCreation() {
		return defaultHapiSpec("BodyOnlyCreation")
				.given().when(
						scheduleCreate("onlyBody",
								cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, GENESIS, 1))
						).logged()
				).then(
						getScheduleInfo("onlyBody")
								.hasScheduleId("onlyBody")
								.hasExpectedScheduledTxn()
				);
	}

	private HapiApiSpec onlyBodyAndAdminCreation() {
		return defaultHapiSpec("OnlyBodyAndAdminCreation")
				.given(
						newKeyNamed("admin")
				).when(
						scheduleCreate("onlyBodyAndAdminKey",
								cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, GENESIS, 1))
						).adminKey("admin")
				).then(
						getScheduleInfo("onlyBodyAndAdminKey")
								.hasScheduleId("onlyBodyAndAdminKey")
								.hasAdminKey("admin")
								.hasExpectedScheduledTxn()
				);
	}

	private HapiApiSpec onlyBodyAndMemoCreation() {
		return defaultHapiSpec("OnlyBodyAndMemoCreation")
				.given().when(
						scheduleCreate("onlyBodyAndMemo",
								cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, GENESIS, 1))
						).withEntityMemo("sample memo")
				).then(
						getScheduleInfo("onlyBodyAndMemo")
								.hasScheduleId("onlyBodyAndMemo")
								.hasEntityMemo("sample memo")
								.hasExpectedScheduledTxn()
				);
	}

	private HapiApiSpec bodyAndPayerCreation() {
		return defaultHapiSpec("BodyAndPayerCreation")
				.given(
						cryptoCreate("payer")
				).when(
						scheduleCreate("onlyBodyAndPayer",
								cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, GENESIS, 1))
						).designatingPayer("payer")
				).then(
						getScheduleInfo("onlyBodyAndPayer")
								.hasScheduleId("onlyBodyAndPayer")
								.hasPayerAccountID("payer")
								.hasExpectedScheduledTxn()
				);
	}

	private HapiApiSpec bodyAndSignatoriesCreation() {
		var txnBody = cryptoTransfer(tinyBarsFromTo("sender", "receiver", 1));

		return defaultHapiSpec("BodyAndSignatoriesCreation")
				.given(
						cryptoCreate("payingAccount"),
						newKeyNamed("adminKey"),
						cryptoCreate("sender"),
						cryptoCreate("receiver").receiverSigRequired(true)
				).when(
						scheduleCreate("onlyBodyAndSignatories", txnBody)
								.adminKey("adminKey")
								.designatingPayer("payingAccount")
								.alsoSigningWith("receiver")
				).then(
						getScheduleInfo("onlyBodyAndSignatories")
								.hasScheduleId("onlyBodyAndSignatories")
								.hasSignatories("receiver")
								.hasExpectedScheduledTxn()
				);
	}

	private HapiApiSpec failsWithNonExistingPayerAccountId() {
		return defaultHapiSpec("FailsWithNonExistingPayerAccountId")
				.given().when(
						scheduleCreate("invalidPayer", cryptoCreate("secondary"))
								.designatingPayer("0.0.9999")
								.hasKnownStatus(INVALID_ACCOUNT_ID)
				)
				.then();
	}

	private HapiApiSpec failsWithTooLongMemo() {
		return defaultHapiSpec("FailsWithTooLongMemo")
				.given().when(
						scheduleCreate("invalidMemo", cryptoCreate("secondary"))
								.withEntityMemo(nAscii(101))
								.hasPrecheck(MEMO_TOO_LONG)
				)
				.then();
	}

	private String nAscii(int n) {
		return IntStream.range(0, n).mapToObj(ignore -> "A").collect(Collectors.joining());
	}

	private HapiApiSpec infoIncludesTxnIdFromCreationReceipt() {
		return defaultHapiSpec("InfoIncludesTxnIdFromCreationReceipt")
				.given(
						scheduleCreate("creation",
								cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1))
						)
								.savingExpectedScheduledTxnId()
				).when().then(
						getScheduleInfo("creation")
								.hasScheduleId("creation")
								.hasScheduledIdSavedBy("creation")
								.logged()
				);
	}

	private HapiApiSpec allowsDoublingScheduledCreates() {
		var txnBody = cryptoTransfer(tinyBarsFromTo("sender", "receiver", 1));

		return defaultHapiSpec("AllowsDoublingScheduledCreates")
				.given(
						cryptoCreate("payingAccount"),
						cryptoCreate("sender"),
						cryptoCreate("receiver").receiverSigRequired(true),
						newKeyNamed("adminKey"),
						scheduleCreate("toBeCreated", txnBody)
								.adminKey("adminKey")
								.designatingPayer("payingAccount")
				)
				.when(
						scheduleCreate("toBeCreated2", txnBody)
								.adminKey("adminKey")
								.designatingPayer("payingAccount")
								.alsoSigningWith("receiver")
				)
				.then(
						getScheduleInfo("toBeCreated")
								.hasScheduleId("toBeCreated")
								.hasPayerAccountID("payingAccount")
								.hasAdminKey("adminKey")
								.hasSignatories("receiver"),
						getScheduleInfo("toBeCreated2")
								.hasScheduleId("toBeCreated")
								.hasPayerAccountID("payingAccount")
								.hasAdminKey("adminKey")
								.hasSignatories("receiver")
				);
	}

	private HapiApiSpec scheduledTXCreatedAfterPreviousIdenticalIsExecuted() {
		var txnBody = cryptoTransfer(tinyBarsFromTo("sender1", "receiver1", 1));

		return defaultHapiSpec("ScheduledTXCreatedAfterPreviousIdenticalIsExecuted")
				.given(
						cryptoCreate("payingAccount"),
						cryptoCreate("sender1"),
						cryptoCreate("receiver1"),
						scheduleCreate("executedTX", txnBody)
								.via("executedTX"),
						scheduleSign("executedTX").withSignatories("sender1", "receiver1")
				)
				.when(
						scheduleCreate("secondTX", txnBody)
								.via("secondTX")
				)
				.then(
						ensureDifferentScheduledTXCreated("executedTX", "secondTX")
				);
	}

	private HapiApiSpec scheduledTXWithDifferentAdminAndPayerAreNotIdempotentlyCreated() {
		return defaultHapiSpec("ScheduledTXWithDifferentAdminAndPayerAreNotIdempotentlyCreated")
				.given(
						cryptoCreate("payer"),
						cryptoCreate("payer2"),
						newKeyNamed("admin"),
						newKeyNamed("admin2"),
						scheduleCreate("first",
								cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, GENESIS, 1))
						).adminKey("admin")
								.designatingPayer("payer")
								.via("first")
				).when(
						scheduleCreate("second",
								cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, GENESIS, 1))
						).adminKey("admin2")
								.designatingPayer("payer2")
								.via("second")
				).then(
						saveExpirations("first", "second", SCHEDULE_EXPIRY_TIME_SECS),
						ensureDifferentScheduledTXCreated("first", "second"),
						getScheduleInfo("first")
								.hasAdminKey("admin")
								.hasPayerAccountID("payer")
								.hasExpectedScheduledTxn()
								.hasValidExpirationTime(),
						getScheduleInfo("second")
								.hasAdminKey("admin2")
								.hasPayerAccountID("payer2")
								.hasExpectedScheduledTxn()
								.hasValidExpirationTime()
				);
	}

	private HapiApiSpec scheduledTXWithDifferentPayerAreNotIdempotentlyCreated() {
		return defaultHapiSpec("ScheduledTXWithDifferentPayerAreNotIdempotentlyCreated")
				.given(
						cryptoCreate("payer"),
						cryptoCreate("payer2"),
						newKeyNamed("admin"),
						scheduleCreate("first",
								cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, GENESIS, 1))
						).adminKey("admin")
								.designatingPayer("payer")
								.via("first")
				).when(
						scheduleCreate("second",
								cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, GENESIS, 1))
						).adminKey("admin")
								.designatingPayer("payer2")
								.via("second")
				).then(
						ensureDifferentScheduledTXCreated("first", "second"),
						getScheduleInfo("first")
								.hasAdminKey("admin")
								.hasPayerAccountID("payer"),
						getScheduleInfo("second")
								.hasAdminKey("admin")
								.hasPayerAccountID("payer2")
				);
	}

	private HapiApiSpec scheduledTXWithDifferentAdminAreNotIdempotentlyCreated() {
		return defaultHapiSpec("ScheduledTXWithDifferentAdminAreNotIdempotentlyCreated")
				.given(
						cryptoCreate("payer"),
						newKeyNamed("admin"),
						newKeyNamed("admin2"),
						scheduleCreate("first",
								cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, GENESIS, 1))
						).adminKey("admin")
								.designatingPayer("payer")
								.via("first")
				).when(
						scheduleCreate("second",
								cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, GENESIS, 1))
						).adminKey("admin2")
								.designatingPayer("payer")
								.via("second")
				).then(
						ensureDifferentScheduledTXCreated("first", "second"),
						getScheduleInfo("first")
								.hasAdminKey("admin")
								.hasPayerAccountID("payer"),
						getScheduleInfo("second")
								.hasAdminKey("admin2")
								.hasPayerAccountID("payer")
				);
	}

	private HapiApiSpec scheduledTXWithDifferentMemoAreNotIdempotentlyCreated() {
		return defaultHapiSpec("ScheduledTXWithDifferentMemoAreNotIdempotentlyCreated")
				.given(
						cryptoCreate("payer"),
						newKeyNamed("admin"),
						scheduleCreate("first",
								cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, GENESIS, 1))
						).adminKey("admin")
								.designatingPayer("payer")
								.withEntityMemo("memo here")
								.via("first")
				).when(
						scheduleCreate("second",
								cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, GENESIS, 1))
						).adminKey("admin")
								.designatingPayer("payer")
								.withEntityMemo("different memo here")
								.via("second")
				).then(
						ensureDifferentScheduledTXCreated("first", "second"),
						getScheduleInfo("first")
								.hasAdminKey("admin")
								.hasPayerAccountID("payer")
								.hasEntityMemo("memo here"),
						getScheduleInfo("second")
								.hasAdminKey("admin")
								.hasPayerAccountID("payer")
								.hasEntityMemo("different memo here")
				);
	}

	private HapiApiSpec idempotentCreationWithBodyOnly() {
		return defaultHapiSpec("IdempotentCreationWithBodyOnly")
				.given(
						scheduleCreate("first",
								cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, GENESIS, 1))
						).via("first")
				).when(
						scheduleCreate("second",
								cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, GENESIS, 1))
						).via("second")
				).then(
						ensureIdempotentlyCreated("first", "second")
				);
	}

	private HapiApiSpec idempotentCreationWithBodyAndPayer() {
		return defaultHapiSpec("IdempotentCreationWithBodyAndPayer")
				.given(
						cryptoCreate("payer"),
						scheduleCreate("first",
								cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, GENESIS, 1))
						).designatingPayer("payer")
								.via("first")
				).when(
						scheduleCreate("second",
								cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, GENESIS, 1))
						).designatingPayer("payer")
								.via("second")
				).then(
						ensureIdempotentlyCreated("first", "second")
				);
	}

	private HapiApiSpec idempotentCreationWithBodyAndAdmin() {
		return defaultHapiSpec("IdempotentCreationWithBodyAndAdmin")
				.given(
						newKeyNamed("admin"),
						scheduleCreate("first",
								cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, GENESIS, 1))
						).adminKey("admin")
								.via("first")
				).when(
						scheduleCreate("second",
								cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, GENESIS, 1))
						).adminKey("admin")
								.via("second")
				).then(
						ensureIdempotentlyCreated("first", "second")
				);
	}

	private HapiApiSpec idempotentCreationWithBodyAndMemo() {
		return defaultHapiSpec("IdempotentCreationWithBodyAndMemo")
				.given(
						scheduleCreate("first",
								cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, GENESIS, 1))
						).memo("memo here")
								.via("first")
				).when(
						scheduleCreate("second",
								cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, GENESIS, 1))
						).memo("memo here")
								.via("second")
				).then(
						ensureIdempotentlyCreated("first", "second")
				);
	}

	private HapiApiSpec idempotentCreationWhenAllPropsAreTheSame() {
		return defaultHapiSpec("IdempotentCreationWhenAllPropsAreTheSame")
				.given(
						newKeyNamed("admin"),
						cryptoCreate("payer"),
						scheduleCreate("first",
								cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, GENESIS, 1))
						).designatingPayer("payer")
								.adminKey("admin")
								.withEntityMemo("memo here")
								.via("first")
				).when(
						scheduleCreate("second",
								cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, GENESIS, 1))
						).designatingPayer("payer")
								.adminKey("admin")
								.withEntityMemo("memo here")
								.via("second")
				).then(
						ensureIdempotentlyCreated("first", "second"),
						getScheduleInfo("first")
								.hasPayerAccountID("payer")
								.hasAdminKey("admin")
								.hasEntityMemo("memo here"),
						getScheduleInfo("second")
								.hasPayerAccountID("payer")
								.hasAdminKey("admin")
								.hasEntityMemo("memo here")
				);
	}

	private HapiApiSpec nestedScheduleCreateFails() {
		return defaultHapiSpec("NestedScheduleCreateFails")
				.given()
				.when(
						scheduleCreate("first",
								scheduleCreate("second", cryptoCreate("primaryCrypto")))
								.hasKnownStatus(UNSCHEDULABLE_TRANSACTION)
				)
				.then();
	}

	private HapiApiSpec nestedScheduleSignFails() {
		return defaultHapiSpec("NestedScheduleSignFails")
				.given(
						newKeyNamed("signer"),
						scheduleCreate("inner",
								cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, GENESIS, 1)))
				)
				.when(
						scheduleCreate("outer",
								scheduleSign("inner").withSignatories("signer"))
								.hasKnownStatus(UNSCHEDULABLE_TRANSACTION)
				)
				.then();
	}

	private HapiApiSpec preservesRevocationServiceSemanticsForFileDelete() {
		KeyShape waclShape = listOf(SIMPLE, threshOf(2, 3));
		SigControl adequateSigs = waclShape.signedWith(sigs(OFF, sigs(ON, ON, OFF)));
		SigControl inadequateSigs = waclShape.signedWith(sigs(OFF, sigs(ON, OFF, OFF)));
		SigControl compensatorySigs = waclShape.signedWith(sigs(OFF, sigs(OFF, OFF, ON)));

		String shouldBeInstaDeleted = "tbd";
		String shouldBeDeletedEventually = "tbdl";

		return defaultHapiSpec("PreservesRevocationServiceSemanticsForFileDelete")
				.given(
						overriding("scheduling.whitelist", "FileDelete"),
						fileCreate(shouldBeInstaDeleted).waclShape(waclShape),
						fileCreate(shouldBeDeletedEventually).waclShape(waclShape)
				).when(
						scheduleCreate(
								"validRevocation",
								fileDelete(shouldBeInstaDeleted)
						)
								.alsoSigningWith(shouldBeInstaDeleted)
								.sigControl(forKey(shouldBeInstaDeleted, adequateSigs)),
						sleepFor(1_000L),
						getFileInfo(shouldBeInstaDeleted).hasDeleted(true)
				).then(
						scheduleCreate(
								"notYetValidRevocation",
								fileDelete(shouldBeDeletedEventually)
						)
								.alsoSigningWith(shouldBeDeletedEventually)
								.sigControl(forKey(shouldBeDeletedEventually, inadequateSigs)),
						getFileInfo(shouldBeDeletedEventually).hasDeleted(false),
						scheduleCreate(
								"nowValidRevocation",
								fileDelete(shouldBeDeletedEventually)
						)
								.alsoSigningWith(shouldBeDeletedEventually)
								.sigControl(forKey(shouldBeDeletedEventually, compensatorySigs)),
						sleepFor(1_000L),
						getFileInfo(shouldBeDeletedEventually).hasDeleted(true),
						overriding("scheduling.whitelist", defaultWhitelist)
				);
	}

	public HapiApiSpec detectsKeysChangedBetweenExpandSigsAndHandleTxn() {
		KeyShape firstShape = listOf(3);
		KeyShape secondShape = threshOf(2, 4);

		return defaultHapiSpec("DetectsKeysChangedBetweenExpandSigsAndHandleTxn")
				.given(
						newKeyNamed("a").shape(firstShape),
						newKeyNamed("b").shape(secondShape)
				).when(
						cryptoCreate("sender"),
						cryptoCreate("receiver")
								.key("a")
								.receiverSigRequired(true)
				).then(
						cryptoUpdate("receiver")
								.key("b")
								.deferStatusResolution(),
						scheduleCreate(
								"outdatedXferSigs",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", 1)
								).fee(ONE_HBAR)
						)
								.alsoSigningWith("sender", "a")
								.hasKnownStatus(SOME_SIGNATURES_WERE_INVALID)
				);
	}

	public HapiApiSpec onlySchedulesWithMissingReqSimpleSigs() {
		return defaultHapiSpec("onlySchedulesWithMissingReqSimpleSigs")
				.given(
						cryptoCreate("sender").balance(1L),
						cryptoCreate("receiver").balance(0L).receiverSigRequired(true)
				).when(
						scheduleCreate(
								"basicXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", 1)
								)
						)
								.alsoSigningWith("sender")
				).then(
						getAccountBalance("sender").hasTinyBars(1L)
				);
	}

	public HapiApiSpec requiresExtantPayer() {
		return defaultHapiSpec("RequiresExtantPayer")
				.given().when().then(
						scheduleCreate(
								"neverToBe",
								cryptoCreate("nope")
										.key(GENESIS)
										.receiverSigRequired(true)
						)
								.designatingPayer("1.2.3")
								.hasKnownStatus(INVALID_ACCOUNT_ID)
				);
	}

	public HapiApiSpec doesntTriggerUntilPayerSigns() {
		return defaultHapiSpec("DoesntTriggerUntilPayerSigns")
				.given(
						cryptoCreate("payer").balance(ONE_HBAR * 2),
						cryptoCreate("sender").balance(1L),
						cryptoCreate("receiver").receiverSigRequired(true).balance(0L)
				).when(
						scheduleCreate(
								"basicXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", 1L)
								).fee(ONE_HBAR)
						)
								.designatingPayer("payer")
								.alsoSigningWith("sender", "receiver")
				).then(
						getAccountBalance("sender").hasTinyBars(1L),
						getAccountBalance("receiver").hasTinyBars(0L),
						scheduleCreate(
								"basicXferWithPayerNow",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", 1L)
								).fee(ONE_HBAR)
						)
								.designatingPayer("payer")
								.alsoSigningWith("payer"),
						getAccountBalance("sender").hasTinyBars(0L),
						getAccountBalance("receiver").hasTinyBars(1L)
				);
	}

	public HapiApiSpec triggersImmediatelyWithBothReqSimpleSigs() {
		long initialBalance = HapiSpecSetup.getDefaultInstance().defaultBalance();
		long transferAmount = 1;

		return defaultHapiSpec("TriggersImmediatelyWithBothReqSimpleSigs")
				.given(
						cryptoCreate("sender"),
						cryptoCreate("receiver").receiverSigRequired(true)
				).when(
						scheduleCreate(
								"basicXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", transferAmount)
								)
						).alsoSigningWith("sender", "receiver")
				).then(
						getAccountBalance("sender").hasTinyBars(initialBalance - transferAmount),
						getAccountBalance("receiver").hasTinyBars(initialBalance + transferAmount)
				);
	}

	public HapiApiSpec rejectsUnresolvableReqSigners() {
		return defaultHapiSpec("RejectsUnresolvableReqSigners")
				.given().when().then(
						scheduleCreate(
								"xferWithImaginaryAccount",
								cryptoTransfer(
										tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1),
										tinyBarsFromTo("1.2.3", FUNDING, 1)
								).signedBy(DEFAULT_PAYER)
						).hasKnownStatus(UNRESOLVABLE_REQUIRED_SIGNERS)
				);
	}

	public HapiApiSpec rejectsUnparseableTxn() {
		return defaultHapiSpec("RejectsUnparseableTxn")
				.given().when().then(
						scheduleCreateNonsense("absurd")
								.hasKnownStatus(UNPARSEABLE_SCHEDULED_TRANSACTION)
				);
	}

	public HapiApiSpec rejectsFunctionlessTxn() {
		return defaultHapiSpec("RejectsFunctionlessTxn")
				.given().when().then(
						scheduleCreateFunctionless("unknown")
								.hasKnownStatus(UNPARSEABLE_SCHEDULED_TRANSACTION)
				);
	}

	public HapiApiSpec whitelistWorks() {
		return defaultHapiSpec("whitelistWorks")
				.given(
						scheduleCreate(
								"nope",
								createTopic("neverToBe").signedBy()
						).hasKnownStatus(UNSCHEDULABLE_TRANSACTION)
				).when(
						overriding("scheduling.whitelist", "ConsensusCreateTopic"),
						scheduleCreate("ok", createTopic("neverToBe"))
				).then(
						overriding("scheduling.whitelist", defaultWhitelist)
				);
	}

	public HapiApiSpec retestsActivationOnCreateWithEmptySigMap() {
		return defaultHapiSpec("RetestsActivationOnCreateWithEmptySigMap")
				.given(
						newKeyNamed("a"),
						newKeyNamed("b"),
						newKeyListNamed("ab", List.of("a", "b"))
				).when(
						cryptoCreate("sender").key("ab").balance(667L),
						scheduleCreate(
								"deferredFall",
								cryptoTransfer(
										tinyBarsFromTo("sender", FUNDING, 1)
								).fee(ONE_HBAR)
						).alsoSigningWith("a"),
						getAccountBalance("sender").hasTinyBars(667L),
						cryptoUpdate("sender").key("a")
				).then(
						scheduleCreate(
								"triggeredFall",
								cryptoTransfer(
										tinyBarsFromTo("sender", FUNDING, 1)
								).fee(ONE_HBAR).signedBy()
						),
						getAccountBalance("sender").hasTinyBars(666L)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

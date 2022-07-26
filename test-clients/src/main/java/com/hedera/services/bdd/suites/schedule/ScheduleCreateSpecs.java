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
import com.hedera.services.bdd.spec.keys.OverlappingKeyGenerator;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.nAscii;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreateFunctionless;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.suites.schedule.ScheduleLongTermExecutionSpecs.withAndWithoutLongTermEnabled;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.IDENTICAL_SCHEDULE_ALREADY_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SOME_SIGNATURES_WERE_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;

public class ScheduleCreateSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ScheduleCreateSpecs.class);

	private static final String defaultWhitelist =
			HapiSpecSetup.getDefaultNodeProps().get("scheduling.whitelist");
	private static final String defaultTxExpiry =
			HapiSpecSetup.getDefaultNodeProps().get("ledger.schedule.txExpiryTimeSecs");

	public static void main(String... args) {
		new ScheduleCreateSpecs().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return withAndWithoutLongTermEnabled(() -> List.of(
			notIdenticalScheduleIfScheduledTxnChanges(),
			notIdenticalScheduleIfAdminKeyChanges(),
			notIdenticalScheduleIfMemoChanges(),
			recognizesIdenticalScheduleEvenWithDifferentDesignatedPayer(),
			rejectsSentinelKeyListAsAdminKey(),
			rejectsMalformedScheduledTxnMemo(),
			bodyOnlyCreation(),
			onlyBodyAndAdminCreation(),
			onlyBodyAndMemoCreation(),
			bodyAndSignatoriesCreation(),
			bodyAndPayerCreation(),
			rejectsUnresolvableReqSigners(),
			triggersImmediatelyWithBothReqSimpleSigs(),
			onlySchedulesWithMissingReqSimpleSigs(),
			failsWithNonExistingPayerAccountId(),
			failsWithTooLongMemo(),
			detectsKeysChangedBetweenExpandSigsAndHandleTxn(),
			doesntTriggerUntilPayerSigns(),
			requiresExtantPayer(),
			rejectsFunctionlessTxn(),
			functionlessTxnBusyWithNonExemptPayer(),
			whitelistWorks(),
			preservesRevocationServiceSemanticsForFileDelete(),
			worksAsExpectedWithDefaultScheduleId(),
			infoIncludesTxnIdFromCreationReceipt(),
			suiteCleanup(),
			validateSignersInInfo()
		));
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
		return customHapiSpec("bodyOnlyCreation").withProperties(Map.of(
						"default.keyAlgorithm", "SECP256K1"))
				.given(
						cryptoCreate("sender")
				).when(
						scheduleCreate("onlyBody",
								cryptoTransfer(tinyBarsFromTo("sender", GENESIS, 1))
						)
								.recordingScheduledTxn(),
						scheduleSign("onlyBody").alsoSigningWith("sender")
				).then(
						getScheduleInfo("onlyBody")
								.hasScheduleId("onlyBody")
								.hasRecordedScheduledTxn().logged()
				);
	}

	private HapiApiSpec validateSignersInInfo() {
		return customHapiSpec("validSchedule").withProperties(Map.of(
						"default.keyAlgorithm", "SECP256K1"))
				.given(
						cryptoCreate("sender")
				).when(
						scheduleCreate("validSchedule",
								cryptoTransfer(tinyBarsFromTo("sender", GENESIS, 1))
						)
								.recordingScheduledTxn(),
						scheduleSign("validSchedule").alsoSigningWith("sender")
				).then(
						getScheduleInfo("validSchedule")
								.hasScheduleId("validSchedule")
								.hasRecordedScheduledTxn()
								.hasSignatories("sender")
				);
	}

	private HapiApiSpec onlyBodyAndAdminCreation() {
		return defaultHapiSpec("OnlyBodyAndAdminCreation")
				.given(
						newKeyNamed("admin"),
						cryptoCreate("sender")
				).when(
						scheduleCreate("onlyBodyAndAdminKey",
								cryptoTransfer(tinyBarsFromTo("sender", GENESIS, 1))
						)
								.adminKey("admin")
								.recordingScheduledTxn()
				).then(
						getScheduleInfo("onlyBodyAndAdminKey")
								.hasScheduleId("onlyBodyAndAdminKey")
								.hasAdminKey("admin")
								.hasRecordedScheduledTxn()
				);
	}

	private HapiApiSpec onlyBodyAndMemoCreation() {
		return defaultHapiSpec("OnlyBodyAndMemoCreation")
				.given(
						cryptoCreate("sender")
				).when(
						scheduleCreate("onlyBodyAndMemo",
								cryptoTransfer(tinyBarsFromTo("sender", GENESIS, 1))
						)
								.recordingScheduledTxn()
								.withEntityMemo("sample memo")
				).then(
						getScheduleInfo("onlyBodyAndMemo")
								.hasScheduleId("onlyBodyAndMemo")
								.hasEntityMemo("sample memo")
								.hasRecordedScheduledTxn()
				);
	}

	private HapiApiSpec bodyAndPayerCreation() {
		return defaultHapiSpec("BodyAndPayerCreation")
				.given(
						cryptoCreate("payer")
				).when(
						scheduleCreate("onlyBodyAndPayer",
								cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, GENESIS, 1))
										.memo("SURPRISE!!!")
						)
								.recordingScheduledTxn()
								// prevent multiple runs of this test causing duplicates
								.withEntityMemo("" + new SecureRandom().nextLong())
								.designatingPayer("payer")
				).then(
						getScheduleInfo("onlyBodyAndPayer")
								.hasScheduleId("onlyBodyAndPayer")
								.hasPayerAccountID("payer")
								.hasRecordedScheduledTxn()
				);
	}

	private HapiApiSpec bodyAndSignatoriesCreation() {
		var scheduleName = "onlyBodyAndSignatories";
		var scheduledTxn = cryptoTransfer(tinyBarsFromTo("sender", "receiver", 1));

		return defaultHapiSpec("BodyAndSignatoriesCreation")
				.given(
						cryptoCreate("payingAccount"),
						newKeyNamed("adminKey"),
						cryptoCreate("sender"),
						cryptoCreate("receiver").receiverSigRequired(true)
				).when(
						scheduleCreate(scheduleName, scheduledTxn)
								.adminKey("adminKey")
								.recordingScheduledTxn()
								.designatingPayer("payingAccount")
								.alsoSigningWith("receiver")
				).then(
						getScheduleInfo(scheduleName)
								.hasScheduleId(scheduleName)
								.hasSignatories("receiver")
								.hasRecordedScheduledTxn()
				);
	}

	private HapiApiSpec failsWithNonExistingPayerAccountId() {
		return defaultHapiSpec("FailsWithNonExistingPayerAccountId")
				.given().when(
						scheduleCreate("invalidPayer", cryptoCreate("secondary"))
								.designatingPayer("1.2.3")
								.hasKnownStatus(ACCOUNT_ID_DOES_NOT_EXIST)
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

	private HapiApiSpec notIdenticalScheduleIfScheduledTxnChanges() {
		return defaultHapiSpec("NotIdenticalScheduleIfScheduledTxnChanges")
				.given(
						cryptoCreate("sender").balance(1L),
						cryptoCreate("firstPayer"),
						scheduleCreate("original",
								cryptoTransfer(tinyBarsFromTo("sender", FUNDING, 1))
										.memo("A")
										.fee(ONE_HBAR)
						)
								.payingWith("firstPayer")
				).when( ).then(
						scheduleCreate("continue",
								cryptoTransfer(tinyBarsFromTo("sender", FUNDING, 1))
										.memo("B")
										.fee(ONE_HBAR)
						)
								.payingWith("firstPayer")
				);
	}

	private HapiApiSpec notIdenticalScheduleIfMemoChanges() {
		return defaultHapiSpec("NotIdenticalScheduleIfMemoChanges")
				.given(
						cryptoCreate("sender").balance(1L),
						scheduleCreate("original",
								cryptoTransfer(tinyBarsFromTo("sender", FUNDING, 1))
										.fee(ONE_HBAR)
						)
								.withEntityMemo("This was Mr. Bleaney's room. He stayed")
				).when( ).then(
						scheduleCreate("continue",
								cryptoTransfer(tinyBarsFromTo("sender", FUNDING, 1))
										.fee(ONE_HBAR)
						)
								.withEntityMemo("The whole time he was at the Bodies, til")
				);
	}

	private HapiApiSpec notIdenticalScheduleIfAdminKeyChanges() {
		return defaultHapiSpec("notIdenticalScheduleIfAdminKeyChanges")
				.given(
						newKeyNamed("adminA"),
						newKeyNamed("adminB"),
						cryptoCreate("sender").balance(1L),
						cryptoCreate("firstPayer"),
						scheduleCreate("original",
								cryptoTransfer(tinyBarsFromTo("sender", FUNDING, 1))
										.fee(ONE_HBAR)
						)
								.adminKey("adminA")
								.withEntityMemo("This was Mr. Bleaney's room. He stayed")
								.designatingPayer("firstPayer")
								.payingWith("firstPayer")
				).when( ).then(
						scheduleCreate("continue",
								cryptoTransfer(tinyBarsFromTo("sender", FUNDING, 1))
										.fee(ONE_HBAR)
						)
								.adminKey("adminB")
								.withEntityMemo("This was Mr. Bleaney's room. He stayed")
								.designatingPayer("firstPayer")
								.payingWith("firstPayer")
				);
	}

	private HapiApiSpec recognizesIdenticalScheduleEvenWithDifferentDesignatedPayer() {
		return defaultHapiSpec("recognizesIdenticalScheduleEvenWithDifferentDesignatedPayer")
				.given(
						cryptoCreate("sender").balance(1L),
						cryptoCreate("firstPayer"),
						scheduleCreate("original",
								cryptoTransfer(tinyBarsFromTo("sender", FUNDING, 1))
										.fee(ONE_HBAR)
						)
								.designatingPayer("firstPayer")
								.payingWith("firstPayer")
								.savingExpectedScheduledTxnId()
				).when(
						cryptoCreate("secondPayer")
				).then(
						scheduleCreate("duplicate",
								cryptoTransfer(tinyBarsFromTo("sender", FUNDING, 1))
										.fee(ONE_HBAR)
						)
								.payingWith("secondPayer")
								.designatingPayer("secondPayer")
								.via("copycat")
								.hasKnownStatus(IDENTICAL_SCHEDULE_ALREADY_CREATED),
						getTxnRecord("copycat").logged(),
						getReceipt("copycat")
								.hasSchedule("original")
								.hasScheduledTxnId("original")
				);
	}

	private HapiApiSpec rejectsSentinelKeyListAsAdminKey() {
		return defaultHapiSpec("RejectsSentinelKeyListAsAdminKey")
				.given().when().then(
						scheduleCreate("creation",
								cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1))
						)
								.usingSentinelKeyListForAdminKey()
								.hasPrecheck(INVALID_ADMIN_KEY)
				);
	}

	private HapiApiSpec rejectsMalformedScheduledTxnMemo() {
		return defaultHapiSpec("RejectsMalformedScheduledTxnMemo")
				.given(
						cryptoCreate("ntb")
								.memo(ZERO_BYTE_MEMO)
								.hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
						cryptoCreate("sender")
				).when().then(
						scheduleCreate("creation",
								cryptoTransfer(tinyBarsFromTo("sender", FUNDING, 1))
										.memo(nAscii(101))
						).hasPrecheck(MEMO_TOO_LONG),
						scheduleCreate("creationPartDeux",
								cryptoTransfer(tinyBarsFromTo("sender", FUNDING, 1))
										.memo("Here's s\u0000 to chew on!")
						).hasPrecheck(INVALID_ZERO_BYTE_IN_STRING)
				);
	}

	private HapiApiSpec infoIncludesTxnIdFromCreationReceipt() {
		return defaultHapiSpec("InfoIncludesTxnIdFromCreationReceipt")
				.given(
						cryptoCreate("sender"),
						scheduleCreate("creation",
								cryptoTransfer(tinyBarsFromTo("sender", FUNDING, 1))
						)
								.savingExpectedScheduledTxnId()
				).when().then(
						getScheduleInfo("creation")
								.hasScheduleId("creation")
								.hasScheduledTxnIdSavedBy("creation")
								.logged()
				);
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
						scheduleSign("notYetValidRevocation")
								.alsoSigningWith(shouldBeDeletedEventually)
								.sigControl(forKey(shouldBeDeletedEventually, compensatorySigs)),
						sleepFor(1_000L),
						getFileInfo(shouldBeDeletedEventually).hasDeleted(true),
						overriding("scheduling.whitelist", defaultWhitelist)
				);
	}

	public HapiApiSpec detectsKeysChangedBetweenExpandSigsAndHandleTxn() {
		var keyGen = OverlappingKeyGenerator.withAtLeastOneOverlappingByte(2);
		String aKey = "a", bKey = "b";

		return defaultHapiSpec("DetectsKeysChangedBetweenExpandSigsAndHandleTxn")
				.given(
						newKeyNamed(aKey).generator(keyGen),
						newKeyNamed(bKey).generator(keyGen)
				).when(
						cryptoCreate("sender"),
						cryptoCreate("receiver")
								.key(aKey)
								.receiverSigRequired(true)
				).then(
						cryptoUpdate("receiver")
								.key(bKey)
								.deferStatusResolution(),
						scheduleCreate(
								"outdatedXferSigs",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", 1)
								).fee(ONE_HBAR)
						)
								.alsoSigningWith(aKey)
								/* In the rare, but possible, case that the overlapping byte shared by aKey
								 * and bKey is _also_ shared by the DEFAULT_PAYER, the bKey prefix in the sig
								 * map will probably not collide with aKey any more, and we will get
								 * SUCCESS instead of SOME_SIGNATURES_WERE_INVALID.
								 *
								 * So we need this to stabilize CI. But if just testing locally, you may
								 * only use .hasKnownStatus(SOME_SIGNATURES_WERE_INVALID) and it will pass
								 * >99.99% of the time. */
								.hasKnownStatusFrom(SOME_SIGNATURES_WERE_INVALID, SUCCESS)
				);
	}

	public HapiApiSpec onlySchedulesWithMissingReqSimpleSigs() {
		return defaultHapiSpec("OnlySchedulesWithMissingReqSimpleSigs")
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
								.hasKnownStatus(ACCOUNT_ID_DOES_NOT_EXIST)
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
						scheduleSign("basicXfer").alsoSigningWith("payer"),
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
										.memo("Shocked, I tell you!")
						)
								.alsoSigningWith("sender", "receiver")
								.via("basicXfer")
								.recordingScheduledTxn()
				).then(
						getAccountBalance("sender").hasTinyBars(initialBalance - transferAmount),
						getAccountBalance("receiver").hasTinyBars(initialBalance + transferAmount),
						getScheduleInfo("basicXfer")
								.isExecuted()
								.hasRecordedScheduledTxn(),
						getTxnRecord("basicXfer").scheduled()
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
								)
						).hasKnownStatus(UNRESOLVABLE_REQUIRED_SIGNERS)
				);
	}

	public HapiApiSpec rejectsFunctionlessTxn() {
		return defaultHapiSpec("RejectsFunctionlessTxn")
				.given().when().then(
						scheduleCreateFunctionless("unknown")
								.hasKnownStatus(SCHEDULED_TRANSACTION_NOT_IN_WHITELIST)
								.payingWith(GENESIS)
				);
	}

	public HapiApiSpec functionlessTxnBusyWithNonExemptPayer() {
		return defaultHapiSpec("FunctionlessTxnBusyWithNonExemptPayer")
				.given().when().then(
						cryptoCreate("sender"),
						scheduleCreateFunctionless("unknown")
								.hasPrecheck(BUSY)
								.payingWith("sender")
				);
	}

	public HapiApiSpec whitelistWorks() {
		return defaultHapiSpec("whitelistWorks")
				.given(
						scheduleCreate(
								"nope",
								createTopic("neverToBe")
						).hasKnownStatus(SCHEDULED_TRANSACTION_NOT_IN_WHITELIST)
				).when(
						overriding("scheduling.whitelist", "ConsensusCreateTopic"),
						scheduleCreate("ok", createTopic("neverToBe"))
								// prevent multiple runs of this test causing duplicates
								.withEntityMemo("" + new SecureRandom().nextLong())
				).then(
						overriding("scheduling.whitelist", defaultWhitelist)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

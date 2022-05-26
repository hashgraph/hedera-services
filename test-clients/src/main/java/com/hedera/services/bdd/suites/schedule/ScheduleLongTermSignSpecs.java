package com.hedera.services.bdd.suites.schedule;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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
import com.hedera.services.bdd.spec.keys.ControlForKey;
import com.hedera.services.bdd.spec.keys.OverlappingKeyGenerator;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.Key;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.keyFromMutation;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyListNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_NEW_VALID_SIGNATURES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RECORD_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SOME_SIGNATURES_WERE_INVALID;

public class ScheduleLongTermSignSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ScheduleLongTermSignSpecs.class);

	private static final String suiteWhitelist = "CryptoCreate,ConsensusSubmitMessage,CryptoTransfer";

	private static final String defaultWhitelist =
			HapiSpecSetup.getDefaultNodeProps().get("scheduling.whitelist");


	public static void main(String... args) {
		new ScheduleLongTermSignSpecs().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
			ScheduleLongTermExecutionSpecs.enableLongTermScheduledTransactions(),
			suiteSetup(),

			triggersUponAdditionalNeededSig(),
			sharedKeyWorksAsExpected(),
			overlappingKeysTreatedAsExpected(),
			retestsActivationOnSignWithEmptySigMap(),
			basicSignatureCollectionWorks(),
			signalsIrrelevantSig(),
			signalsIrrelevantSigEvenAfterLinkedEntityUpdate(),
			triggersUponFinishingPayerSig(),
			receiverSigRequiredUpdateIsRecognized(),
			receiverSigRequiredNotConfusedByMultiSigSender(),
			receiverSigRequiredNotConfusedByOrder(),
			extraSigsDontMatterAtExpiry(),
			nestedSigningReqsWorkAsExpected(),
			changeInNestedSigningReqsRespected(),
			reductionInSigningReqsAllowsTxnToGoThrough(),
			reductionInSigningReqsAllowsTxnToGoThroughAtExpiryWithNoWaitForExpiry(),

			suiteCleanup(),
			ScheduleLongTermExecutionSpecs.setLongTermScheduledTransactionsToDefault()
		);
	}

	private HapiApiSpec suiteCleanup() {
		return defaultHapiSpec("suiteCleanup")
				.given().when().then(
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"scheduling.whitelist", defaultWhitelist
								))
				);
	}

	private HapiApiSpec suiteSetup() {
		return defaultHapiSpec("suiteSetup")
				.given().when().then(
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"scheduling.whitelist", suiteWhitelist
								))
				);
	}

	private HapiApiSpec changeInNestedSigningReqsRespected() {
		var senderShape = threshOf(2, threshOf(1, 3), threshOf(1, 3), threshOf(1, 3));
		var sigOne = senderShape.signedWith(sigs(sigs(OFF, OFF, ON), sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF)));
		var firstSigThree = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF), sigs(ON, OFF, OFF)));
		var secondSigThree = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF), sigs(ON, ON, OFF)));
		String sender = "X", receiver = "Y", schedule = "Z", senderKey = "sKey", newSenderKey = "newSKey";

		return defaultHapiSpec("ChangeInNestedSigningReqsRespectedAtExpiry")
				.given(
						newKeyNamed(senderKey).shape(senderShape),
						keyFromMutation(newSenderKey, senderKey)
								.changing(this::bumpThirdNestedThresholdSigningReq),
						cryptoCreate(sender).key(senderKey).via("senderTxn"),
						cryptoCreate(receiver).balance(0L),
						scheduleCreate(schedule, cryptoTransfer(
								tinyBarsFromTo(sender, receiver, 1))
						)
								.payingWith(DEFAULT_PAYER)
								.waitForExpiry()
								.withRelativeExpiry("senderTxn", 8)
								.recordingScheduledTxn()
								.alsoSigningWith(sender)
								.sigControl(ControlForKey.forKey(senderKey, sigOne)),
						getAccountBalance(receiver).hasTinyBars(0L)
				)
				.when(
						cryptoUpdate(sender).key(newSenderKey),
						scheduleSign(schedule)
								.alsoSigningWith(newSenderKey)
								.sigControl(forKey(newSenderKey, firstSigThree)),
						getAccountBalance(receiver).hasTinyBars(0L),
						scheduleSign(schedule)
								.alsoSigningWith(newSenderKey)
								.sigControl(forKey(newSenderKey, secondSigThree)),
						getAccountBalance(receiver).hasTinyBars(0L)
				)
				.then(
						getScheduleInfo(schedule)
								.hasScheduleId(schedule)
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("senderTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo"),
						getScheduleInfo(schedule)
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
						getAccountBalance(receiver).hasTinyBars(1L)
				);
	}

	private Key bumpThirdNestedThresholdSigningReq(Key source) {
		var newKey = source.getThresholdKey().getKeys().getKeys(2).toBuilder();
		newKey.setThresholdKey(newKey.getThresholdKeyBuilder().setThreshold(2));
		var newKeyList = source.getThresholdKey().getKeys().toBuilder()
				.setKeys(2, newKey);
		var mutation = source.toBuilder()
				.setThresholdKey(source.getThresholdKey().toBuilder().setKeys(newKeyList))
				.build();
		return mutation;
	}

	private HapiApiSpec reductionInSigningReqsAllowsTxnToGoThrough() {
		var senderShape = threshOf(2, threshOf(1, 3), threshOf(1, 3), threshOf(2, 3));
		var sigOne = senderShape.signedWith(sigs(sigs(OFF, OFF, ON), sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF)));
		var sigTwo = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(ON, ON, ON), sigs(OFF, OFF, OFF)));
		var firstSigThree = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF), sigs(ON, OFF, OFF)));
		String sender = "X", receiver = "Y", schedule = "Z", senderKey = "sKey", newSenderKey = "newSKey";

		return defaultHapiSpec("ReductionInSigningReqsAllowsTxnToGoThroughAtExpiry")
				.given(
						newKeyNamed(senderKey).shape(senderShape),
						keyFromMutation(newSenderKey, senderKey)
								.changing(this::lowerThirdNestedThresholdSigningReq),
						cryptoCreate(sender).key(senderKey).via("senderTxn"),
						cryptoCreate(receiver).balance(0L),
						scheduleCreate(schedule, cryptoTransfer(
								tinyBarsFromTo(sender, receiver, 1))
						)
								.payingWith(DEFAULT_PAYER)
								.waitForExpiry()
								.withRelativeExpiry("senderTxn", 8)
								.recordingScheduledTxn()
								.alsoSigningWith(sender)
								.sigControl(ControlForKey.forKey(senderKey, sigOne)),
						getAccountBalance(receiver).hasTinyBars(0L)
				)
				.when(
						scheduleSign(schedule)
								.alsoSigningWith(newSenderKey)
								.sigControl(forKey(newSenderKey, firstSigThree)),
						getAccountBalance(receiver).hasTinyBars(0L),
						cryptoUpdate(sender).key(newSenderKey),
						scheduleSign(schedule)
								.hasKnownStatus(NO_NEW_VALID_SIGNATURES),
						getAccountBalance(receiver).hasTinyBars(0L)
				)
				.then(
						getScheduleInfo(schedule)
								.hasScheduleId(schedule)
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("senderTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo"),

						scheduleSign(schedule)
								.alsoSigningWith(newSenderKey)
								.sigControl(forKey(newSenderKey, sigTwo))
								.hasPrecheck(INVALID_SCHEDULE_ID),
						getAccountBalance(receiver).hasTinyBars(1L)
				);
	}
	private HapiApiSpec reductionInSigningReqsAllowsTxnToGoThroughAtExpiryWithNoWaitForExpiry() {
		var senderShape = threshOf(2, threshOf(1, 3), threshOf(1, 3), threshOf(2, 3));
		var sigOne = senderShape.signedWith(sigs(sigs(OFF, OFF, ON), sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF)));
		var sigTwo = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(ON, ON, ON), sigs(OFF, OFF, OFF)));
		var firstSigThree = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF), sigs(ON, OFF, OFF)));
		String sender = "X", receiver = "Y", schedule = "Z", senderKey = "sKey", newSenderKey = "newSKey";

		return defaultHapiSpec("ReductionInSigningReqsAllowsTxnToGoThroughAtExpiryWithNoWaitForExpiry")
				.given(
						newKeyNamed(senderKey).shape(senderShape),
						keyFromMutation(newSenderKey, senderKey)
								.changing(this::lowerThirdNestedThresholdSigningReq),
						cryptoCreate(sender).key(senderKey).via("senderTxn"),
						cryptoCreate(receiver).balance(0L),
						scheduleCreate(schedule, cryptoTransfer(
								tinyBarsFromTo(sender, receiver, 1))
						)
								.payingWith(DEFAULT_PAYER)
								.withRelativeExpiry("senderTxn", 8)
								.recordingScheduledTxn()
								.alsoSigningWith(sender)
								.sigControl(ControlForKey.forKey(senderKey, sigOne)),
						getAccountBalance(receiver).hasTinyBars(0L)
				)
				.when(
						scheduleSign(schedule)
								.alsoSigningWith(newSenderKey)
								.sigControl(forKey(newSenderKey, firstSigThree)),
						getAccountBalance(receiver).hasTinyBars(0L),
						cryptoUpdate(sender).key(newSenderKey),
						getAccountBalance(receiver).hasTinyBars(0L)
				)
				.then(
						getScheduleInfo(schedule)
								.hasScheduleId(schedule)
								.hasWaitForExpiry(false)
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("senderTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo"),

						scheduleSign(schedule)
								.alsoSigningWith(newSenderKey)
								.sigControl(forKey(newSenderKey, sigTwo))
								.hasPrecheck(INVALID_SCHEDULE_ID),
						getAccountBalance(receiver).hasTinyBars(1L)
				);
	}

	private Key lowerThirdNestedThresholdSigningReq(Key source) {
		var newKey = source.getThresholdKey().getKeys().getKeys(2).toBuilder();
		newKey.setThresholdKey(newKey.getThresholdKeyBuilder().setThreshold(1));
		var newKeyList = source.getThresholdKey().getKeys().toBuilder()
				.setKeys(2, newKey);
		var mutation = source.toBuilder()
				.setThresholdKey(source.getThresholdKey().toBuilder().setKeys(newKeyList))
				.build();
		return mutation;
	}

	private HapiApiSpec nestedSigningReqsWorkAsExpected() {
		var senderShape = threshOf(2, threshOf(1, 3), threshOf(1, 3), threshOf(1, 3));
		var sigOne = senderShape.signedWith(sigs(sigs(OFF, OFF, ON), sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF)));
		var sigTwo = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(OFF, ON, OFF), sigs(OFF, OFF, OFF)));
		String sender = "X", receiver = "Y", schedule = "Z", senderKey = "sKey";

		return defaultHapiSpec("NestedSigningReqsWorkAsExpectedAtExpiry")
				.given(
						newKeyNamed(senderKey).shape(senderShape),
						cryptoCreate(sender).key(senderKey).via("senderTxn"),
						cryptoCreate(receiver).balance(0L),
						scheduleCreate(schedule, cryptoTransfer(
								tinyBarsFromTo(sender, receiver, 1))
						)
								.payingWith(DEFAULT_PAYER)
								.waitForExpiry()
								.withRelativeExpiry("senderTxn", 8)
								.recordingScheduledTxn()
								.alsoSigningWith(sender)
								.sigControl(ControlForKey.forKey(senderKey, sigOne)),
						getAccountBalance(receiver).hasTinyBars(0L)
				)
				.when(
						scheduleSign(schedule)
								.alsoSigningWith(senderKey)
								.sigControl(forKey(senderKey, sigTwo)),
						getAccountBalance(receiver).hasTinyBars(0L)
				)
				.then(
						getScheduleInfo(schedule)
								.hasScheduleId(schedule)
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("senderTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo"),
						getScheduleInfo(schedule)
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
						getAccountBalance(receiver).hasTinyBars(1L)
				);
	}

	private HapiApiSpec receiverSigRequiredNotConfusedByOrder() {
		var senderShape = threshOf(1, 3);
		var sigOne = senderShape.signedWith(sigs(ON, OFF, OFF));
		String sender = "X", receiver = "Y", schedule = "Z", senderKey = "sKey";

		return defaultHapiSpec("ReceiverSigRequiredNotConfusedByOrderAtExpiry")
				.given(
						newKeyNamed(senderKey).shape(senderShape),
						cryptoCreate(sender).key(senderKey).via("senderTxn"),
						cryptoCreate(receiver).balance(0L).receiverSigRequired(true),
						scheduleCreate(schedule, cryptoTransfer(
								tinyBarsFromTo(sender, receiver, 1))
						)
								.waitForExpiry()
								.withRelativeExpiry("senderTxn", 8)
								.recordingScheduledTxn()
								.payingWith(DEFAULT_PAYER),
						scheduleSign(schedule)
								.alsoSigningWith(receiver),
						getAccountBalance(receiver).hasTinyBars(0L)
				)
				.when(
						scheduleSign(schedule)
								.alsoSigningWith(senderKey)
								.sigControl(forKey(senderKey, sigOne)),
						getAccountBalance(receiver).hasTinyBars(0L)
				)
				.then(
						getScheduleInfo(schedule)
								.hasScheduleId(schedule)
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("senderTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo"),
						getScheduleInfo(schedule)
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
						getAccountBalance(receiver).hasTinyBars(1L)
				);
	}

	private HapiApiSpec extraSigsDontMatterAtExpiry() {
		var senderShape = threshOf(1, 3);
		var sigOne = senderShape.signedWith(sigs(ON, OFF, OFF));
		var sigTwo = senderShape.signedWith(sigs(OFF, ON, OFF));
		var sigThree = senderShape.signedWith(sigs(OFF, OFF, ON));
		String sender = "X", receiver = "Y", schedule = "Z", senderKey = "sKey";

		return defaultHapiSpec("ExtraSigsDontMatterAtExpiry")
				.given(
						cryptoCreate("payer").balance(ONE_MILLION_HBARS),
						newKeyNamed(senderKey).shape(senderShape),
						newKeyNamed("extraKey"),
						cryptoCreate(sender).key(senderKey).via("senderTxn"),
						cryptoCreate(receiver).balance(0L).receiverSigRequired(true),
						scheduleCreate(schedule, cryptoTransfer(
								tinyBarsFromTo(sender, receiver, 1))
						)
								.waitForExpiry()
								.withRelativeExpiry("senderTxn", 20)
								.recordingScheduledTxn()
								.payingWith(DEFAULT_PAYER),
						scheduleSign(schedule)
								.payingWith("payer")
								.fee(THOUSAND_HBAR)
								.alsoSigningWith(receiver),
						getAccountBalance(receiver).hasTinyBars(0L)
				)
				.when(
						scheduleSign(schedule)
								.payingWith("payer")
								.fee(THOUSAND_HBAR)
								.alsoSigningWith(senderKey)
								.sigControl(forKey(senderKey, sigOne)),
						getAccountBalance(receiver).hasTinyBars(0L),
						scheduleSign(schedule)
								.payingWith("payer")
								.fee(THOUSAND_HBAR)
								.alsoSigningWith(senderKey)
								.sigControl(forKey(senderKey, sigTwo)),
						getAccountBalance(receiver).hasTinyBars(0L),
						scheduleSign(schedule)
								.payingWith("payer")
								.fee(THOUSAND_HBAR)
								.alsoSigningWith("extraKey")
								.hasKnownStatus(NO_NEW_VALID_SIGNATURES),
						getAccountBalance(receiver).hasTinyBars(0L),
						scheduleSign(schedule)
								.payingWith("payer")
								.fee(THOUSAND_HBAR)
								.alsoSigningWith(senderKey)
								.sigControl(forKey(senderKey, sigTwo))
								.hasKnownStatus(NO_NEW_VALID_SIGNATURES),
						getAccountBalance(receiver).hasTinyBars(0L),
						scheduleSign(schedule)
								.payingWith("payer")
								.fee(THOUSAND_HBAR)
								.alsoSigningWith(senderKey)
								.sigControl(forKey(senderKey, sigThree)),
						getAccountBalance(receiver).hasTinyBars(0L),
						scheduleSign(schedule)
								.payingWith("payer")
								.fee(THOUSAND_HBAR)
								.alsoSigningWith(senderKey)
								.sigControl(forKey(senderKey, sigTwo))
								.hasKnownStatus(NO_NEW_VALID_SIGNATURES),
						getAccountBalance(receiver).hasTinyBars(0L),
						scheduleSign(schedule)
								.payingWith("payer")
								.fee(THOUSAND_HBAR)
								.alsoSigningWith("extraKey")
								.hasKnownStatus(NO_NEW_VALID_SIGNATURES),
						getAccountBalance(receiver).hasTinyBars(0L),
						scheduleSign(schedule)
								.payingWith("payer")
								.fee(THOUSAND_HBAR)
								.alsoSigningWith(senderKey)
								.sigControl(forKey(senderKey, sigOne))
								.hasKnownStatus(NO_NEW_VALID_SIGNATURES),
						getAccountBalance(receiver).hasTinyBars(0L)
				)
				.then(
						getScheduleInfo(schedule)
								.hasScheduleId(schedule)
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("senderTxn", 20)
								.hasRecordedScheduledTxn(),
						sleepFor(21000),
						cryptoCreate("foo"),
						getScheduleInfo(schedule)
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
						getAccountBalance(receiver).hasTinyBars(1L)
				);
	}

	private HapiApiSpec receiverSigRequiredNotConfusedByMultiSigSender() {
		var senderShape = threshOf(1, 3);
		var sigOne = senderShape.signedWith(sigs(ON, OFF, OFF));
		var sigTwo = senderShape.signedWith(sigs(OFF, ON, OFF));
		String sender = "X", receiver = "Y", schedule = "Z", senderKey = "sKey";

		return defaultHapiSpec("ReceiverSigRequiredNotConfusedByMultiSigSenderAtExpiry")
				.given(
						newKeyNamed(senderKey).shape(senderShape),
						cryptoCreate(sender).key(senderKey).via("senderTxn"),
						cryptoCreate(receiver).balance(0L).receiverSigRequired(true),
						scheduleCreate(schedule, cryptoTransfer(
								tinyBarsFromTo(sender, receiver, 1))
						)
								.waitForExpiry()
								.withRelativeExpiry("senderTxn", 8)
								.recordingScheduledTxn()
								.payingWith(DEFAULT_PAYER),
						getAccountBalance(receiver).hasTinyBars(0L)
				)
				.when(
						scheduleSign(schedule)
								.alsoSigningWith(senderKey)
								.sigControl(forKey(senderKey, sigOne)),
						getAccountBalance(receiver).hasTinyBars(0L),
						scheduleSign(schedule)
								.alsoSigningWith(senderKey)
								.sigControl(forKey(senderKey, sigTwo)),
						getAccountBalance(receiver).hasTinyBars(0L),
						scheduleSign(schedule)
								.alsoSigningWith(receiver),
						getAccountBalance(receiver).hasTinyBars(0L)
				)
				.then(
						getScheduleInfo(schedule)
								.hasScheduleId(schedule)
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("senderTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo"),
						getScheduleInfo(schedule)
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
						getAccountBalance(receiver).hasTinyBars(1L)
				);
	}

	private HapiApiSpec receiverSigRequiredUpdateIsRecognized() {
		var senderShape = threshOf(2, 3);
		var sigOne = senderShape.signedWith(sigs(ON, OFF, OFF));
		var sigTwo = senderShape.signedWith(sigs(OFF, ON, OFF));
		String sender = "X", receiver = "Y", schedule = "Z", senderKey = "sKey";

		return defaultHapiSpec("ReceiverSigRequiredUpdateIsRecognizedAtExpiry")
				.given(
						newKeyNamed(senderKey).shape(senderShape),
						cryptoCreate(sender).key(senderKey).via("senderTxn"),
						cryptoCreate(receiver).balance(0L),
						scheduleCreate(schedule, cryptoTransfer(
								tinyBarsFromTo(sender, receiver, 1))
						)
								.payingWith(DEFAULT_PAYER)
								.waitForExpiry()
								.withRelativeExpiry("senderTxn", 8)
								.recordingScheduledTxn()
								.alsoSigningWith(sender)
								.sigControl(forKey(senderKey, sigOne)),
						getAccountBalance(receiver).hasTinyBars(0L)
				)
				.when(
						cryptoUpdate(receiver).receiverSigRequired(true),
						scheduleSign(schedule)
								.alsoSigningWith(senderKey)
								.sigControl(forKey(senderKey, sigTwo)),
						getAccountBalance(receiver).hasTinyBars(0L)
				)
				.then(
						scheduleSign(schedule)
								.alsoSigningWith(receiver),
						getAccountBalance(receiver).hasTinyBars(0),
						getScheduleInfo(schedule)
								.hasScheduleId(schedule)
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("senderTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo"),
						getAccountBalance(receiver).hasTinyBars(1),
						scheduleSign(schedule)
								.alsoSigningWith(senderKey)
								.sigControl(forKey(senderKey, sigTwo))
								.hasPrecheck(INVALID_SCHEDULE_ID),
						getAccountBalance(receiver).hasTinyBars(1)
				);
	}

	private HapiApiSpec basicSignatureCollectionWorks() {
		var txnBody = cryptoTransfer(tinyBarsFromTo("sender", "receiver", 1));

		return defaultHapiSpec("BasicSignatureCollectionWorksWithExpiryAndWait")
				.given(
						cryptoCreate("sender").via("senderTxn"),
						cryptoCreate("receiver").receiverSigRequired(true),
						scheduleCreate("basicXfer", txnBody)
								.waitForExpiry()
								.withRelativeExpiry("senderTxn", 8)
				)
				.when(
						scheduleSign("basicXfer").alsoSigningWith("receiver")
				)
				.then(
						getScheduleInfo("basicXfer")
								.hasSignatories("receiver")
				);
	}

	private HapiApiSpec signalsIrrelevantSig() {
		var txnBody = cryptoTransfer(tinyBarsFromTo("sender", "receiver", 1));

		return defaultHapiSpec("SignalsIrrelevantSigWithExpiryAndWait")
				.given(
						cryptoCreate("sender").via("senderTxn"),
						cryptoCreate("receiver"),
						newKeyNamed("somebodyelse"),
						scheduleCreate("basicXfer", txnBody)
								.waitForExpiry()
								.withRelativeExpiry("senderTxn", 8)
				)
				.when()
				.then(
						scheduleSign("basicXfer")
								.alsoSigningWith("somebodyelse")
								.hasKnownStatusFrom(NO_NEW_VALID_SIGNATURES, SOME_SIGNATURES_WERE_INVALID)
				);
	}

	private HapiApiSpec signalsIrrelevantSigEvenAfterLinkedEntityUpdate() {
		var txnBody = mintToken("tokenA", 50000000L);

		return defaultHapiSpec("SignalsIrrelevantSigEvenAfterLinkedEntityUpdateWithExpiryAndWait")
				.given(
						overriding("scheduling.whitelist", "TokenMint"),
						newKeyNamed("admin"),
						newKeyNamed("mint"),
						newKeyNamed("newMint"),
						tokenCreate("tokenA").adminKey("admin").supplyKey("mint").via("createTxn"),
						scheduleCreate("tokenMintScheduled", txnBody)
								.waitForExpiry()
								.withRelativeExpiry("createTxn", 8)
				).when(
						tokenUpdate("tokenA").supplyKey("newMint")
				).then(
						scheduleSign("tokenMintScheduled")
								.alsoSigningWith("mint")
								/* In the rare, but possible, case that the the mint and newMint keys overlap
								 * in their first byte (and that byte is not shared by the DEFAULT_PAYER),
								 * we will get SOME_SIGNATURES_WERE_INVALID instead of NO_NEW_VALID_SIGNATURES.
								 *
								 * So we need this to stabilize CI. But if just testing locally, you may
								 * only use .hasKnownStatus(NO_NEW_VALID_SIGNATURES) and it will pass
								 * >99.99% of the time. */
								.hasKnownStatusFrom(NO_NEW_VALID_SIGNATURES, SOME_SIGNATURES_WERE_INVALID),
						overriding("scheduling.whitelist", suiteWhitelist)
				);
	}

	public HapiApiSpec triggersUponFinishingPayerSig() {
		return defaultHapiSpec("TriggersUponFinishingPayerSigAtExpiry")
				.given(
						cryptoCreate("payer").balance(ONE_HBAR),
						cryptoCreate("sender").balance(1L).via("senderTxn"),
						cryptoCreate("receiver").balance(0L).receiverSigRequired(true)
				).when(
						scheduleCreate(
								"threeSigXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", 1)
								).fee(ONE_HBAR)
						).designatingPayer("payer")
								.waitForExpiry()
								.withRelativeExpiry("senderTxn", 8)
								.recordingScheduledTxn()
								.alsoSigningWith("sender", "receiver"),
						getAccountBalance("receiver").hasTinyBars(0L),
						scheduleSign("threeSigXfer").alsoSigningWith("payer")
				).then(
						getAccountBalance("receiver").hasTinyBars(0L),
						getScheduleInfo("threeSigXfer")
								.hasScheduleId("threeSigXfer")
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("senderTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo"),
						getScheduleInfo("threeSigXfer")
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
						getAccountBalance("receiver").hasTinyBars(1L)
				);
	}

	public HapiApiSpec triggersUponAdditionalNeededSig() {
		return defaultHapiSpec("TriggersUponAdditionalNeededSigAtExpiry")
				.given(
						cryptoCreate("sender").balance(1L).via("senderTxn"),
						cryptoCreate("receiver").balance(0L).receiverSigRequired(true)
				).when(
						scheduleCreate(
								"twoSigXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", 1)
								).fee(ONE_HBAR)
						)
								.waitForExpiry()
								.withRelativeExpiry("senderTxn", 8)
								.recordingScheduledTxn()
								.alsoSigningWith("sender"),
						getAccountBalance("receiver").hasTinyBars(0L),
						scheduleSign("twoSigXfer").alsoSigningWith("receiver")
				).then(
						getAccountBalance("receiver").hasTinyBars(0L),
						getScheduleInfo("twoSigXfer")
								.hasScheduleId("twoSigXfer")
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("senderTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo"),
						getScheduleInfo("twoSigXfer")
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
						getAccountBalance("receiver").hasTinyBars(1L)
				);
	}

	public HapiApiSpec sharedKeyWorksAsExpected() {
		return defaultHapiSpec("RequiresSharedKeyToSignBothSchedulingAndScheduledTxnsAtExpiry")
				.given(
						newKeyNamed("sharedKey"),
						cryptoCreate("payerWithSharedKey").key("sharedKey").via("createTxn")
				).when(
						scheduleCreate(
								"deferredCreation",
								cryptoCreate("yetToBe")
										.signedBy()
										.receiverSigRequired(true)
										.key("sharedKey")
										.balance(123L)
										.fee(ONE_HBAR)
						)
								.waitForExpiry()
								.withRelativeExpiry("createTxn", 8)
								.recordingScheduledTxn()
								.payingWith("payerWithSharedKey")
								.via("creation")
				).then(
						getTxnRecord("creation").scheduled().hasAnswerOnlyPrecheck(RECORD_NOT_FOUND),
						getScheduleInfo("deferredCreation")
								.hasScheduleId("deferredCreation")
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("createTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo"),
						getScheduleInfo("deferredCreation")
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
						getTxnRecord("creation").scheduled()
				);
	}

	public HapiApiSpec overlappingKeysTreatedAsExpected() {
		var keyGen = OverlappingKeyGenerator.withAtLeastOneOverlappingByte(2);

		return defaultHapiSpec("OverlappingKeysTreatedAsExpectedAtExpiry")
				.given(
						newKeyNamed("aKey").generator(keyGen),
						newKeyNamed("bKey").generator(keyGen),
						newKeyNamed("cKey"),
						cryptoCreate("aSender").key("aKey").balance(1L).via("aSenderTxn"),
						cryptoCreate("cSender").key("cKey").balance(1L),
						balanceSnapshot("before", ADDRESS_BOOK_CONTROL)
				).when(
						scheduleCreate("deferredXfer",
								cryptoTransfer(
										tinyBarsFromTo("aSender", ADDRESS_BOOK_CONTROL, 1),
										tinyBarsFromTo("cSender", ADDRESS_BOOK_CONTROL, 1)
								)
						)
								.waitForExpiry()
								.withRelativeExpiry("aSenderTxn", 8)
								.recordingScheduledTxn()
				).then(
						scheduleSign("deferredXfer")
								.alsoSigningWith("aKey"),
						scheduleSign("deferredXfer")
								.alsoSigningWith("aKey")
								.hasKnownStatus(NO_NEW_VALID_SIGNATURES),
						scheduleSign("deferredXfer")
								.alsoSigningWith("aKey", "bKey")
								.hasKnownStatus(NO_NEW_VALID_SIGNATURES),
						scheduleSign("deferredXfer")
								.alsoSigningWith("bKey")
								/* In the rare, but possible, case that the overlapping byte shared by aKey
								 * and bKey is _also_ shared by the DEFAULT_PAYER, the bKey prefix in the sig
								 * map will probably not collide with aKey any more, and we will get
								 * NO_NEW_VALID_SIGNATURES instead of SOME_SIGNATURES_WERE_INVALID.
								 *
								 * So we need this to stabilize CI. But if just testing locally, you may
								 * only use .hasKnownStatus(SOME_SIGNATURES_WERE_INVALID) and it will pass
								 * >99.99% of the time. */
								.hasKnownStatusFrom(SOME_SIGNATURES_WERE_INVALID, NO_NEW_VALID_SIGNATURES),
						scheduleSign("deferredXfer")
								.alsoSigningWith("aKey", "bKey", "cKey"),
						getAccountBalance(ADDRESS_BOOK_CONTROL)
								.hasTinyBars(changeFromSnapshot("before", 0)),
						getScheduleInfo("deferredXfer")
								.hasScheduleId("deferredXfer")
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("aSenderTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo"),
						getScheduleInfo("deferredXfer")
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
						getAccountBalance(ADDRESS_BOOK_CONTROL)
								.hasTinyBars(changeFromSnapshot("before", +2))
				);
	}

	public HapiApiSpec retestsActivationOnSignWithEmptySigMap() {
		return defaultHapiSpec("RetestsActivationOnCreateWithEmptySigMapAtExpiry")
				.given(
						newKeyNamed("a"),
						newKeyNamed("b"),
						newKeyListNamed("ab", List.of("a", "b")),
						newKeyNamed("admin")
				).when(
						cryptoCreate("sender").key("ab").balance(667L).via("senderTxn"),
						scheduleCreate(
								"deferredFall",
								cryptoTransfer(
										tinyBarsFromTo("sender", FUNDING, 1)
								).fee(ONE_HBAR)
						)
								.waitForExpiry()
								.withRelativeExpiry("senderTxn", 8)
								.recordingScheduledTxn()
								.alsoSigningWith("a"),
						getAccountBalance("sender").hasTinyBars(667L),
						cryptoUpdate("sender").key("a")
				).then(
						scheduleSign("deferredFall").alsoSigningWith()
								.hasKnownStatus(NO_NEW_VALID_SIGNATURES),
						getAccountBalance("sender").hasTinyBars(667L),
						getScheduleInfo("deferredFall")
								.hasScheduleId("deferredFall")
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("senderTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo"),
						getScheduleInfo("deferredFall")
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
						getAccountBalance("sender").hasTinyBars(666L)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

}

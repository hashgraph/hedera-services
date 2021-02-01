package com.hedera.services.bdd.suites.schedule;

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
import com.hedera.services.bdd.spec.keys.OverlappingKeyGenerator;
import com.hedera.services.bdd.spec.transactions.file.HapiFileUpdate;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyListNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_NEW_VALID_SIGNATURES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RECORD_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SOME_SIGNATURES_WERE_INVALID;

public class ScheduleSignSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ScheduleSignSpecs.class);
	private static final int SCHEDULE_EXPIRY_TIME_SECS = 10;
	private static final int SCHEDULE_EXPIRY_TIME_MS = SCHEDULE_EXPIRY_TIME_SECS * 1000;
	private static final HapiFileUpdate updateScheduleExpiryTimeSecs = fileUpdate(APP_PROPERTIES)
			.payingWith(ADDRESS_BOOK_CONTROL)
			.overridingProps(
					Map.of("ledger.schedule.txExpiryTimeSecs", "" + SCHEDULE_EXPIRY_TIME_SECS));


	public static void main(String... args) {
		new ScheduleSignSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						expiredBeforeSigning(),
						triggersUponAdditionalNeededSig(),
						requiresSharedKeyToSignBothSchedulingAndScheduledTxns(),
						scheduleSigIrrelevantToSchedulingTxn(),
						overlappingKeysTreatedAsExpected(),
						retestsActivationOnSignWithEmptySigMap(),
				}
		);
	}

	public HapiApiSpec triggersUponAdditionalNeededSig() {
		return defaultHapiSpec("TriggersUponAdditionalNeededSig")
				.given(
						updateScheduleExpiryTimeSecs,
						cryptoCreate("sender").balance(1L),
						cryptoCreate("receiver").balance(0L).receiverSigRequired(true)
				).when(
						scheduleCreate(
								"twoSigXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", 1)
								).fee(ONE_HBAR).signedBy("sender")
						).inheritingScheduledSigs(),
						getAccountBalance("receiver").hasTinyBars(0L),
						scheduleSign("twoSigXfer").withSignatories("receiver")
				).then(
						getAccountBalance("receiver").hasTinyBars(1L)
				);
	}

	public HapiApiSpec requiresSharedKeyToSignBothSchedulingAndScheduledTxns() {
		return defaultHapiSpec("RequiresSharedKeyToSignBothSchedulingAndScheduledTxns")
				.given(
						updateScheduleExpiryTimeSecs,
						newKeyNamed("sharedKey"),
						cryptoCreate("payerWithSharedKey").key("sharedKey")
				).when(
						scheduleCreate(
								"deferredCreation",
								cryptoCreate("yetToBe")
										.signedBy()
										.receiverSigRequired(true)
										.key("sharedKey")
										.balance(123L)
										.fee(ONE_HBAR)
						).inheritingScheduledSigs()
								.payingWith("payerWithSharedKey")
								.via("creation"),
						getTxnRecord("creation")
								.scheduled()
								.hasAnswerOnlyPrecheck(RECORD_NOT_FOUND)
				).then(
						scheduleSign("deferredCreation")
								.withSignatories("sharedKey")
								.via("deferredCreation"),
						getTxnRecord("creation").scheduled().logged()
				);
	}

	public HapiApiSpec scheduleSigIrrelevantToSchedulingTxn() {
		return defaultHapiSpec("ScheduleSigIrrelevantToSchedulingTxn")
				.given(
						updateScheduleExpiryTimeSecs,
						newKeyNamed("origKey"),
						newKeyNamed("sharedKey"),
						cryptoCreate("payerToHaveSharedKey").key("origKey")
				).when().then(
						cryptoUpdate("payerToHaveSharedKey")
								.key("sharedKey")
								.deferStatusResolution(),
						scheduleCreate(
								"deferredCreation",
								cryptoCreate("yetToBe")
										.signedBy("sharedKey")
										.receiverSigRequired(true)
										.key("sharedKey")
										.fee(ONE_HBAR)
						).inheritingScheduledSigs()
								.payingWith("payerToHaveSharedKey")
								.signedBy("origKey")
								.hasKnownStatus(INVALID_PAYER_SIGNATURE)
				);
	}

	public HapiApiSpec overlappingKeysTreatedAsExpected() {
		var keyGen = OverlappingKeyGenerator.withAtLeastOneOverlappingByte(2);

		return defaultHapiSpec("OverlappingKeysTreatedAsExpected")
				.given(
						updateScheduleExpiryTimeSecs,
						newKeyNamed("aKey").generator(keyGen),
						newKeyNamed("bKey").generator(keyGen),
						newKeyNamed("cKey"),
						cryptoCreate("aSender").key("aKey").balance(1L),
						cryptoCreate("cSender").key("cKey").balance(1L),
						balanceSnapshot("before", ADDRESS_BOOK_CONTROL)
				).when(
						scheduleCreate("deferredXfer",
								cryptoTransfer(
										tinyBarsFromTo("aSender", ADDRESS_BOOK_CONTROL, 1),
										tinyBarsFromTo("cSender", ADDRESS_BOOK_CONTROL, 1)
								).signedBy()
						).inheritingScheduledSigs()
				).then(
						scheduleSign("deferredXfer")
								.withSignatories("aKey"),
						scheduleSign("deferredXfer")
								.withSignatories("aKey")
								.hasKnownStatus(NO_NEW_VALID_SIGNATURES),
						scheduleSign("deferredXfer")
								.withSignatories("aKey", "bKey")
								.hasKnownStatus(SOME_SIGNATURES_WERE_INVALID),
						scheduleSign("deferredXfer")
								.withSignatories("aKey", "cKey"),
						getAccountBalance(ADDRESS_BOOK_CONTROL)
								.hasTinyBars(changeFromSnapshot("before", +2))
				);
	}

	public HapiApiSpec retestsActivationOnSignWithEmptySigMap() {
		return defaultHapiSpec("RetestsActivationOnCreateWithEmptySigMap")
				.given(
						updateScheduleExpiryTimeSecs,
						newKeyNamed("a"),
						newKeyNamed("b"),
						newKeyListNamed("ab", List.of("a", "b")),
						newKeyNamed("admin")
				).when(
						cryptoCreate("sender").key("ab").balance(667L),
						scheduleCreate(
								"deferredFall",
								cryptoTransfer(
										tinyBarsFromTo("sender", FUNDING, 1)
								).fee(ONE_HBAR).signedBy("a")
						).inheritingScheduledSigs(),
						getAccountBalance("sender").hasTinyBars(667L),
						cryptoUpdate("sender").key("a")
				).then(
						scheduleSign("deferredFall").withSignatories(),
						getAccountBalance("sender").hasTinyBars(666L)
				);
	}

	public HapiApiSpec expiredBeforeSigning() {
		final int FAST_EXPIRATION = 0;
		return defaultHapiSpec("SignFailsDueToDeletedExpiration")
				.given(
						sleepFor(SCHEDULE_EXPIRY_TIME_MS), // await any other scheduled expiring entity to expire
						cryptoCreate("sender").balance(1L),
						cryptoCreate("receiver").balance(0L).receiverSigRequired(true)
				).when(
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(
										Map.of("ledger.schedule.txExpiryTimeSecs", "" + FAST_EXPIRATION)),
						scheduleCreate(
								"twoSigXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", 1)
								)
										.signedBy("sender")
						).inheritingScheduledSigs(),
						updateScheduleExpiryTimeSecs,
						getAccountBalance("receiver").hasTinyBars(0L)
				).then(
						scheduleSign("twoSigXfer").withSignatories("receiver")
								.hasKnownStatus(INVALID_SCHEDULE_ID)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

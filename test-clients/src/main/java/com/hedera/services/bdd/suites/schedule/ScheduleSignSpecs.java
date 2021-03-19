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
import com.hedera.services.bdd.spec.keys.OverlappingKeyGenerator;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyListNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_NEW_VALID_SIGNATURES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SOME_SIGNATURES_WERE_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class ScheduleSignSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ScheduleSignSpecs.class);
	private static final int SCHEDULE_EXPIRY_TIME_SECS = 10;
	private static final int SCHEDULE_EXPIRY_TIME_MS = SCHEDULE_EXPIRY_TIME_SECS * 1000;

	private static final String defaultTxExpiry =
			HapiSpecSetup.getDefaultNodeProps().get("ledger.schedule.txExpiryTimeSecs");
	private static final String defaultWhitelist =
			HapiSpecSetup.getDefaultNodeProps().get("scheduling.whitelist");

	public static void main(String... args) {
		new ScheduleSignSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						suiteSetup(),
						signFailsDueToDeletedExpiration(),
						triggersUponAdditionalNeededSig(),
						sharedKeyWorksAsExpected(),
						overlappingKeysTreatedAsExpected(),
						retestsActivationOnSignWithEmptySigMap(),
						basicSignatureCollectionWorks(),
						addingSignaturesToNonExistingTxFails(),
						signalsIrrelevantSig(),
						signalsIrrelevantSigEvenAfterLinkedEntityUpdate(),
						triggersUponFinishingPayerSig(),
						addingSignaturesToExecutedTxFails(),
						suiteCleanup(),
				}
		);
	}

	private HapiApiSpec suiteCleanup() {
		return defaultHapiSpec("suiteCleanup")
				.given().when().then(
						overriding("ledger.schedule.txExpiryTimeSecs", defaultTxExpiry)
				);
	}

	private HapiApiSpec suiteSetup() {
		return defaultHapiSpec("suiteSetup")
				.given().when().then(
						overriding("ledger.schedule.txExpiryTimeSecs", "" + SCHEDULE_EXPIRY_TIME_SECS)
				);
	}

	private HapiApiSpec basicSignatureCollectionWorks() {
		var txnBody = cryptoTransfer(tinyBarsFromTo("sender", "receiver", 1));

		return defaultHapiSpec("BasicSignatureCollectionWorks")
				.given(
						cryptoCreate("sender"),
						cryptoCreate("receiver").receiverSigRequired(true),
						scheduleCreate("basicXfer", txnBody)
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

		return defaultHapiSpec("SignalsIrrelevantSig")
				.given(
						cryptoCreate("sender"),
						cryptoCreate("receiver"),
						newKeyNamed("somebodyelse"),
						scheduleCreate("basicXfer", txnBody)
				)
				.when()
				.then(
						scheduleSign("basicXfer")
								.alsoSigningWith("somebodyelse")
								.hasKnownStatus(NO_NEW_VALID_SIGNATURES)
				);
	}

	private HapiApiSpec signalsIrrelevantSigEvenAfterLinkedEntityUpdate() {
		var txnBody = mintToken("tokenA", 50000000L);

		return defaultHapiSpec("SignalsIrrelevantSigEvenAfterLinkedEntityUpdate")
				.given(
						overriding("scheduling.whitelist", "TokenMint"),
						newKeyNamed("admin"),
						newKeyNamed("mint"),
						newKeyNamed("newMint"),
						tokenCreate("tokenA").adminKey("admin").supplyKey("mint"),
						scheduleCreate("tokenMintScheduled", txnBody)
				).when(
						tokenUpdate("tokenA").supplyKey("newMint")
				).then(
						scheduleSign("tokenMintScheduled")
								.alsoSigningWith("mint")
								.hasKnownStatus(NO_NEW_VALID_SIGNATURES),
						overriding("scheduling.whitelist", defaultWhitelist)
				);
	}

	private HapiApiSpec addingSignaturesToNonExistingTxFails() {
		return defaultHapiSpec("AddingSignaturesToNonExistingTxFails")
				.given(
						cryptoCreate("sender"),
						newKeyNamed("somebody")
				).when().then(
						scheduleSign("0.0.123321")
								.alsoSigningWith("somebody", "sender")
								.hasKnownStatus(INVALID_SCHEDULE_ID)
				);
	}

	private HapiApiSpec addingSignaturesToExecutedTxFails() {
		var txnBody = cryptoCreate("somebody");
		var creation = "basicCryptoCreate";

		return defaultHapiSpec("AddingSignaturesToExecutedTxFails")
				.given(
						cryptoCreate("somesigner"),
						scheduleCreate(creation, txnBody)
				).when(
						getScheduleInfo(creation)
								.isExecuted()
								.logged()
				).then(
						scheduleSign(creation)
								.via("signing")
								.alsoSigningWith("somesigner")
								.hasKnownStatus(SCHEDULE_ALREADY_EXECUTED)
				);
	}

	public HapiApiSpec triggersUponFinishingPayerSig() {
		return defaultHapiSpec("TriggersUponFinishingPayerSig")
				.given(
						cryptoCreate("payer").balance(ONE_HBAR),
						cryptoCreate("sender").balance(1L),
						cryptoCreate("receiver").balance(0L).receiverSigRequired(true)
				).when(
						scheduleCreate(
								"threeSigXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", 1)
								).fee(ONE_HBAR)
						).designatingPayer("payer")
								.alsoSigningWith("sender", "receiver"),
						getAccountBalance("receiver").hasTinyBars(0L),
						scheduleSign("threeSigXfer").alsoSigningWith("payer")
				).then(
						getAccountBalance("receiver").hasTinyBars(1L)
				);
	}

	public HapiApiSpec triggersUponAdditionalNeededSig() {
		return defaultHapiSpec("TriggersUponAdditionalNeededSig")
				.given(
						cryptoCreate("sender").balance(1L),
						cryptoCreate("receiver").balance(0L).receiverSigRequired(true)
				).when(
						scheduleCreate(
								"twoSigXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", 1)
								).fee(ONE_HBAR)
						).alsoSigningWith("sender"),
						getAccountBalance("receiver").hasTinyBars(0L),
						scheduleSign("twoSigXfer").alsoSigningWith("receiver")
				).then(
						getAccountBalance("receiver").hasTinyBars(1L)
				);
	}

	public HapiApiSpec sharedKeyWorksAsExpected() {
		return defaultHapiSpec("RequiresSharedKeyToSignBothSchedulingAndScheduledTxns")
				.given(
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
						)
								.payingWith("payerWithSharedKey")
								.via("creation")
				).then(
						getTxnRecord("creation").scheduled()
				);
	}

	public HapiApiSpec overlappingKeysTreatedAsExpected() {
		var keyGen = OverlappingKeyGenerator.withAtLeastOneOverlappingByte(2);

		return defaultHapiSpec("OverlappingKeysTreatedAsExpected")
				.given(
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
								)
						)
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
								.hasTinyBars(changeFromSnapshot("before", +2))
				);
	}

	public HapiApiSpec retestsActivationOnSignWithEmptySigMap() {
		return defaultHapiSpec("RetestsActivationOnCreateWithEmptySigMap")
				.given(
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
								).fee(ONE_HBAR)
						).alsoSigningWith("a"),
						getAccountBalance("sender").hasTinyBars(667L),
						cryptoUpdate("sender").key("a")
				).then(
						scheduleSign("deferredFall").alsoSigningWith(),
						getAccountBalance("sender").hasTinyBars(666L)
				);
	}

	public HapiApiSpec signFailsDueToDeletedExpiration() {
		final int FAST_EXPIRATION = 0;
		return defaultHapiSpec("SignFailsDueToDeletedExpiration")
				.given(
						sleepFor(SCHEDULE_EXPIRY_TIME_MS), // await any other scheduled expiring entity to expire
						cryptoCreate("sender").balance(1L),
						cryptoCreate("receiver").balance(0L).receiverSigRequired(true)
				).when(
						overriding("ledger.schedule.txExpiryTimeSecs", "" + FAST_EXPIRATION),
						scheduleCreate(
								"twoSigXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", 1)
								)
						)
								.alsoSigningWith("sender"),
						getAccountBalance("receiver").hasTinyBars(0L)
				).then(
						scheduleSign("twoSigXfer").alsoSigningWith("receiver")
								.hasKnownStatus(INVALID_SCHEDULE_ID),
						overriding("ledger.schedule.txExpiryTimeSecs", "" + SCHEDULE_EXPIRY_TIME_SECS)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

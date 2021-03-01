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
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_ID_FIELD_NOT_ALLOWED;

public class ScheduleRecordSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ScheduleRecordSpecs.class);
	private static final int SCHEDULE_EXPIRY_TIME_SECS = 10;

	private static final String defaultTxExpiry =
			HapiSpecSetup.getDefaultNodeProps().get("ledger.schedule.txExpiryTimeSecs");

	public static void main(String... args) {
		new ScheduleRecordSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						suiteSetup(),
						allRecordsAreQueryable(),
						schedulingTxnIdFieldsNotAllowed(),
						suiteCleanup(),
						canonicalScheduleOpsHaveExpectedUsdFees(),
						canScheduleChunkedMessages(),
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

	HapiApiSpec canonicalScheduleOpsHaveExpectedUsdFees() {
		return defaultHapiSpec("canonicalScheduleOpsHaveExpectedUsdFees")
				.given(
						cryptoCreate("payingSender"),
						cryptoCreate("receiver")
								.balance(0L)
								.receiverSigRequired(true)
				).when(
						scheduleCreate("canonical",
								cryptoTransfer(tinyBarsFromTo("payingSender", "receiver", 1L))
										.memo("")
										.fee(ONE_HBAR)
										.signedBy("payingSender")
						)
								.via("canonicalCreation")
								.payingWith("payingSender")
								.adminKey("payingSender")
								.inheritingScheduledSigs(),
						scheduleSign("canonical")
								.via("canonicalSigning")
								.payingWith("payingSender")
								.withSignatories("receiver"),
						scheduleCreate("tbd",
								cryptoTransfer(tinyBarsFromTo("payingSender", "receiver", 1L))
										.memo("")
										.fee(ONE_HBAR)
										.signedBy("payingSender")
						)
								.payingWith("payingSender")
								.adminKey("payingSender")
								.inheritingScheduledSigs(),
						scheduleDelete("tbd")
								.via("canonicalDeletion")
								.payingWith("payingSender")
				).then(
						validateChargedUsdWithin("canonicalCreation", 0.01, 3.0),
						validateChargedUsdWithin("canonicalSigning", 0.001, 3.0),
						validateChargedUsdWithin("canonicalDeletion", 0.001, 3.0)
				);
	}

	public HapiApiSpec canScheduleChunkedMessages() {
		String ofGeneralInterest = "Scotch";
		AtomicReference<TransactionID> initialTxnId = new AtomicReference<>();

		return defaultHapiSpec("CanScheduleChunkedMessages")
				.given(
						cryptoCreate("payingSender").balance(A_HUNDRED_HBARS),
						createTopic(ofGeneralInterest)
				).when(
						withOpContext((spec, opLog) -> {
							var subOp = usableTxnIdNamed("begin").payerId("payingSender");
							allRunFor(spec, subOp);
							initialTxnId.set(spec.registry().getTxnId("begin"));
						}),
						sourcing(() -> scheduleCreate("firstChunk",
								submitMessageTo(ofGeneralInterest)
										.chunkInfo(
												3,
												1,
												scheduledVersionOf(initialTxnId.get()))
										.signedBy("payingSender")
								)
										.txnId("begin")
										.signedBy("payingSender")
										.inheritingScheduledSigs()
						),
						getTxnRecord("begin").scheduled().hasPriority(recordWith().status(SUCCESS))
				).then(
						scheduleCreate("secondChunk",
								submitMessageTo(ofGeneralInterest)
										.chunkInfo(3, 2, "payingSender")
										.signedBy("payingSender")
						)
								.payingWith("payingSender")
								.inheritingScheduledSigs(),
						getTopicInfo(ofGeneralInterest).logged().hasSeqNo(2L)
				);
	}

	private TransactionID scheduledVersionOf(TransactionID txnId) {
		return txnId.toBuilder().setScheduled(true).build();
	}

	public HapiApiSpec schedulingTxnIdFieldsNotAllowed() {
		return defaultHapiSpec("SchedulingTxnIdFieldsNotAllowed")
				.given(
						usableTxnIdNamed("withNonce").usingNonceInappropriately(),
						usableTxnIdNamed("withScheduled").settingScheduledInappropriately()
				).when().then(
						cryptoCreate("nope")
								.txnId("withNonce")
								.hasPrecheck(TRANSACTION_ID_FIELD_NOT_ALLOWED),
						cryptoCreate("nope")
								.txnId("withScheduled")
								.hasPrecheck(TRANSACTION_ID_FIELD_NOT_ALLOWED)
				);
	}

	public HapiApiSpec allRecordsAreQueryable() {
		return defaultHapiSpec("AllRecordsAreQueryable")
				.given(
						cryptoCreate("payer"),
						cryptoCreate("receiver").receiverSigRequired(true).balance(0L)
				).when(
						scheduleCreate(
								"twoSigXfer",
								cryptoTransfer(
										tinyBarsFromTo("payer", "receiver", 1)
								).fee(ONE_HBAR).signedBy("payer")
						).inheritingScheduledSigs()
								.payingWith("payer")
								.via("creation"),
						getAccountBalance("receiver").hasTinyBars(0L)
				).then(
						scheduleSign("twoSigXfer")
								.via("trigger")
								.withSignatories("receiver"),
						getAccountBalance("receiver").hasTinyBars(1L),
						getTxnRecord("trigger").logged(),
						getTxnRecord("creation").logged(),
						getTxnRecord("creation").scheduled().logged()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

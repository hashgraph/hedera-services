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
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.assertions.TransferListAsserts.exactParticipants;
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
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_ID_FIELD_NOT_ALLOWED;

public class ScheduleRecordSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ScheduleRecordSpecs.class);

	public static void main(String... args) {
		new ScheduleRecordSpecs().runSuiteAsync();
	}

	@Override
	public boolean canRunAsync() {
		return true;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						executionTimeIsAvailable(),
						deletionTimeIsAvailable(),
						allRecordsAreQueryable(),
						schedulingTxnIdFieldsNotAllowed(),
						canonicalScheduleOpsHaveExpectedUsdFees(),
						canScheduleChunkedMessages(),
						noFeesChargedIfTriggeredPayerIsInsolvent(),
						noFeesChargedIfTriggeredPayerIsUnwilling(),
				}
		);
	}

	HapiApiSpec canonicalScheduleOpsHaveExpectedUsdFees() {
		return defaultHapiSpec("CanonicalScheduleOpsHaveExpectedUsdFees")
				.given(
						cryptoCreate("otherPayer"),
						cryptoCreate("payingSender"),
						cryptoCreate("receiver")
								.receiverSigRequired(true)
				).when(
						scheduleCreate("canonical",
								cryptoTransfer(tinyBarsFromTo("payingSender", "receiver", 1L))
										.memo("")
										.fee(ONE_HBAR)
						)
								.payingWith("otherPayer")
								.via("canonicalCreation")
								.alsoSigningWith("payingSender")
								.adminKey("otherPayer"),
						scheduleSign("canonical")
								.via("canonicalSigning")
								.payingWith("payingSender")
								.alsoSigningWith("receiver"),
						scheduleCreate("tbd",
								cryptoTransfer(tinyBarsFromTo("payingSender", "receiver", 1L))
										.memo("")
										.fee(ONE_HBAR)
						)
								.payingWith("payingSender")
								.adminKey("payingSender"),
						scheduleDelete("tbd")
								.via("canonicalDeletion")
								.payingWith("payingSender")
				).then(
						validateChargedUsdWithin("canonicalCreation", 0.01, 3.0),
						validateChargedUsdWithin("canonicalSigning", 0.001, 3.0),
						validateChargedUsdWithin("canonicalDeletion", 0.001, 3.0)
				);
	}

	public HapiApiSpec noFeesChargedIfTriggeredPayerIsUnwilling() {
		return defaultHapiSpec("NoFeesChargedIfTriggeredPayerIsUnwilling")
				.given(
						cryptoCreate("unwillingPayer")
				).when(
						scheduleCreate("schedule",
								cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1))
										.fee(1L)
						)
								.alsoSigningWith(GENESIS, "unwillingPayer")
								.via("simpleXferSchedule")
								.designatingPayer("unwillingPayer")
								.savingExpectedScheduledTxnId()
				).then(
						getTxnRecord("simpleXferSchedule")
								.scheduledBy("schedule")
								.hasPriority(recordWith()
										.transfers(exactParticipants(ignore -> Collections.emptyList()))
										.status(INSUFFICIENT_TX_FEE))

				);
	}

	public HapiApiSpec noFeesChargedIfTriggeredPayerIsInsolvent() {
		return defaultHapiSpec("NoFeesChargedIfTriggeredPayerIsInsolvent")
				.given(
						cryptoCreate("insolventPayer").balance(0L)
				).when(
						scheduleCreate("schedule",
								cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1))
						)
								.alsoSigningWith(GENESIS, "insolventPayer")
								.via("simpleXferSchedule")
								.designatingPayer("insolventPayer")
								.savingExpectedScheduledTxnId()
				).then(
						getTxnRecord("simpleXferSchedule")
								.scheduledBy("schedule")
								.hasPriority(recordWith()
										.transfers(exactParticipants(ignore -> Collections.emptyList()))
										.status(INSUFFICIENT_PAYER_BALANCE))

				);
	}

	public HapiApiSpec canScheduleChunkedMessages() {
		String ofGeneralInterest = "Scotch";
		AtomicReference<TransactionID> initialTxnId = new AtomicReference<>();

		return defaultHapiSpec("CanScheduleChunkedMessages")
				.given(
						cryptoCreate("payingSender").balance(ONE_HUNDRED_HBARS),
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
								)
										.txnId("begin")
										.logged()
										.signedBy("payingSender")
						),
						getTxnRecord("begin").hasPriority(recordWith()
								.status(SUCCESS)
								.transfers(exactParticipants(spec -> List.of(
										spec.setup().defaultNode(),
										spec.setup().fundingAccount(),
										spec.setup().stakingRewardAccount(),
										spec.setup().nodeRewardAccount(),
										spec.registry().getAccountID("payingSender")
								)))).assertingOnlyPriority().logged(),
						getTxnRecord("begin").scheduled().hasPriority(recordWith()
								.status(SUCCESS)
								.transfers(exactParticipants(spec -> List.of(
										spec.setup().fundingAccount(),
										spec.setup().stakingRewardAccount(),
										spec.setup().nodeRewardAccount(),
										spec.registry().getAccountID("payingSender")
								)))).logged()
				).then(
						scheduleCreate("secondChunk",
								submitMessageTo(ofGeneralInterest)
										.chunkInfo(3, 2, "payingSender")
						)
								.via("end")
								.logged()
								.payingWith("payingSender"),
						getTxnRecord("end").scheduled().hasPriority(recordWith()
								.status(SUCCESS)
								.transfers(exactParticipants(spec -> List.of(
										spec.setup().fundingAccount(),
										spec.setup().stakingRewardAccount(),
										spec.setup().nodeRewardAccount(),
										spec.registry().getAccountID("payingSender")
								)))).logged(),
						getTopicInfo(ofGeneralInterest).logged().hasSeqNo(2L)
				);
	}

	static TransactionID scheduledVersionOf(TransactionID txnId) {
		return txnId.toBuilder().setScheduled(true).build();
	}

	public HapiApiSpec schedulingTxnIdFieldsNotAllowed() {
		return defaultHapiSpec("SchedulingTxnIdFieldsNotAllowed")
				.given(
						usableTxnIdNamed("withScheduled").settingScheduledInappropriately()
				).when().then(
						cryptoCreate("nope")
								.txnId("withScheduled")
								.hasPrecheck(TRANSACTION_ID_FIELD_NOT_ALLOWED)
				);
	}

	public HapiApiSpec executionTimeIsAvailable() {
		return defaultHapiSpec("ExecutionTimeIsAvailable")
				.given(
						cryptoCreate("payer"),
						cryptoCreate("receiver").receiverSigRequired(true).balance(0L)
				).when(
						scheduleCreate(
								"tb",
								cryptoTransfer(
										tinyBarsFromTo("payer", "receiver", 1)
								).fee(ONE_HBAR)
						)
								.savingExpectedScheduledTxnId()
								.payingWith("payer")
								.via("creation"),
						scheduleSign("tb")
								.via("trigger")
								.alsoSigningWith("receiver")
				).then(
						getScheduleInfo("tb")
								.logged()
								.wasExecutedBy("trigger")
				);
	}

	public HapiApiSpec deletionTimeIsAvailable() {
		return defaultHapiSpec("DeletionTimeIsAvailable")
				.given(
						newKeyNamed("admin"),
						cryptoCreate("payer"),
						cryptoCreate("receiver").receiverSigRequired(true).balance(0L)
				).when(
						scheduleCreate(
								"ntb",
								cryptoTransfer(
										tinyBarsFromTo("payer", "receiver", 1)
								).fee(ONE_HBAR)
						)
								.payingWith("payer")
								.adminKey("admin")
								.via("creation"),
						scheduleDelete("ntb")
								.via("deletion")
								.signedBy(DEFAULT_PAYER, "admin")
				).then(
						getScheduleInfo("ntb")
								.wasDeletedAtConsensusTimeOf("deletion")
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
								).fee(ONE_HBAR)
						)
								.logged()
								.savingExpectedScheduledTxnId()
								.payingWith("payer")
								.via("creation"),
						getAccountBalance("receiver").hasTinyBars(0L)
				).then(
						scheduleSign("twoSigXfer")
								.via("trigger")
								.alsoSigningWith("receiver"),
						getAccountBalance("receiver").hasTinyBars(1L),
						getTxnRecord("trigger"),
						getTxnRecord("creation"),
						getTxnRecord("creation").scheduled(),
						getTxnRecord("creation").scheduledBy("twoSigXfer")
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

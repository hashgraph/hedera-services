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
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromToWithInvalidAmounts;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.recordFeeAmount;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.schedule.ScheduleExecutionSpecs.transferListCheck;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RECORD_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class ScheduleLongTermExecutionSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ScheduleLongTermExecutionSpecs.class);

	private static final String defaultLongTermEnabled =
			HapiSpecSetup.getDefaultNodeProps().get("scheduling.longTermEnabled");

	private static final String defaultTxExpiry =
			HapiSpecSetup.getDefaultNodeProps().get("ledger.schedule.txExpiryTimeSecs");


	public static void main(String... args) {
		new ScheduleLongTermExecutionSpecs().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
			enableLongTermScheduledTransactions(),

			executionWithDefaultPayerWorks(),
			executionWithCustomPayerWorks(),
			executionWithCustomPayerThatNeverSignsFails(),
			executionWithCustomPayerAndAdminKeyWorks(),
			executionWithDefaultPayerButNoFundsFails(),
			executionWithCustomPayerButNoFundsFails(),
			executionWithDefaultPayerButAccountDeletedFails(),
			executionWithCustomPayerButAccountDeletedFails(),
			executionWithInvalidAccountAmountsFails(),
			executionWithCryptoInsufficientAccountBalanceFails(),
			executionWithCryptoSenderDeletedFails(),
			executionTriggersWithWeirdlyRepeatedKey(),

			executionNoSigTxnRequiredWorks(),

			disableLongTermScheduledTransactions(),

			waitForExpiryIgnoredWhenLongTermDisabled(),
			expiryIgnoredWhenLongTermDisabled(),
			waitForExpiryIgnoredWhenLongTermDisabledThenEnabled(),
			expiryIgnoredWhenLongTermDisabledThenEnabled(),

			setLongTermScheduledTransactionsToDefault()
		);
	}

	private HapiApiSpec executionWithCustomPayerWorks() {
		return defaultHapiSpec("ExecutionAtExpiryWithCustomPayerWorks")
				.given(
						cryptoCreate("payingAccount"),
						cryptoCreate("receiver"),
						cryptoCreate("sender").via("senderTxn")
				).when(
						scheduleCreate(
								"basicXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", 1)
								)
						)
								.designatingPayer("payingAccount")
								.waitForExpiry()
								.withRelativeExpiry("senderTxn", 8)
								.recordingScheduledTxn()
								.via("createTx"),
						scheduleSign("basicXfer")
								.alsoSigningWith("sender", "payingAccount")
								.via("signTx")
								.hasKnownStatus(SUCCESS)
				).then(
						getScheduleInfo("basicXfer")
								.hasScheduleId("basicXfer")
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("senderTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo").via("triggeringTxn"),
						getScheduleInfo("basicXfer")
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
						withOpContext((spec, opLog) -> {
							var createTx = getTxnRecord("createTx");
							var signTx = getTxnRecord("signTx");
							var triggeringTx = getTxnRecord("triggeringTxn");
							var triggeredTx = getTxnRecord("createTx").scheduled();
							allRunFor(spec, createTx, signTx, triggeredTx, triggeringTx);

							Assertions.assertEquals(SUCCESS,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction be successful!");

							Instant triggerTime = Instant.ofEpochSecond(
									triggeringTx.getResponseRecord().getConsensusTimestamp().getSeconds(),
									triggeringTx.getResponseRecord().getConsensusTimestamp().getNanos());

							Instant triggeredTime = Instant.ofEpochSecond(
									triggeredTx.getResponseRecord().getConsensusTimestamp().getSeconds(),
									triggeredTx.getResponseRecord().getConsensusTimestamp().getNanos());

							Assertions.assertTrue(
									triggerTime.isBefore(triggeredTime),
									"Wrong consensus timestamp!");

							Assertions.assertEquals(
									createTx.getResponseRecord().getTransactionID().getTransactionValidStart(),
									triggeredTx.getResponseRecord().getTransactionID().getTransactionValidStart(),
									"Wrong transaction valid start!");

							Assertions.assertEquals(
									createTx.getResponseRecord().getTransactionID().getAccountID(),
									triggeredTx.getResponseRecord().getTransactionID().getAccountID(),
									"Wrong record account ID!");

							Assertions.assertTrue(
									triggeredTx.getResponseRecord().getTransactionID().getScheduled(),
									"Transaction not scheduled!");

							Assertions.assertEquals(
									createTx.getResponseRecord().getReceipt().getScheduleID(),
									triggeredTx.getResponseRecord().getScheduleRef(),
									"Wrong schedule ID!");

							Assertions.assertTrue(
									transferListCheck(triggeredTx, asId("sender", spec), asId("receiver", spec),
											asId("payingAccount", spec), 1L),
									"Wrong transfer list!");
						})
				);
	}

	private HapiApiSpec executionWithCustomPayerAndAdminKeyWorks() {
		return defaultHapiSpec("ExecutionAtExpiryWithCustomPayerAndAdminKeyWorks")
				.given(
						newKeyNamed("adminKey"),
						cryptoCreate("payingAccount"),
						cryptoCreate("receiver"),
						cryptoCreate("sender").via("senderTxn")
				).when(
						scheduleCreate(
								"basicXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", 1)
								)
						)
								.designatingPayer("payingAccount")
								.adminKey("adminKey")
								.waitForExpiry()
								.withRelativeExpiry("senderTxn", 8)
								.recordingScheduledTxn()
								.via("createTx"),
						scheduleSign("basicXfer")
								.alsoSigningWith("sender", "payingAccount")
								.via("signTx")
								.hasKnownStatus(SUCCESS)
				).then(
						getScheduleInfo("basicXfer")
								.hasScheduleId("basicXfer")
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("senderTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo").via("triggeringTxn"),
						getScheduleInfo("basicXfer")
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
						withOpContext((spec, opLog) -> {
							var createTx = getTxnRecord("createTx");
							var signTx = getTxnRecord("signTx");
							var triggeringTx = getTxnRecord("triggeringTxn");
							var triggeredTx = getTxnRecord("createTx").scheduled();
							allRunFor(spec, createTx, signTx, triggeredTx, triggeringTx);

							Assertions.assertEquals(SUCCESS,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction be successful!");

							Instant triggerTime = Instant.ofEpochSecond(
									triggeringTx.getResponseRecord().getConsensusTimestamp().getSeconds(),
									triggeringTx.getResponseRecord().getConsensusTimestamp().getNanos());

							Instant triggeredTime = Instant.ofEpochSecond(
									triggeredTx.getResponseRecord().getConsensusTimestamp().getSeconds(),
									triggeredTx.getResponseRecord().getConsensusTimestamp().getNanos());

							Assertions.assertTrue(
									triggerTime.isBefore(triggeredTime),
									"Wrong consensus timestamp!");

							Assertions.assertEquals(
									createTx.getResponseRecord().getTransactionID().getTransactionValidStart(),
									triggeredTx.getResponseRecord().getTransactionID().getTransactionValidStart(),
									"Wrong transaction valid start!");

							Assertions.assertEquals(
									createTx.getResponseRecord().getTransactionID().getAccountID(),
									triggeredTx.getResponseRecord().getTransactionID().getAccountID(),
									"Wrong record account ID!");

							Assertions.assertTrue(
									triggeredTx.getResponseRecord().getTransactionID().getScheduled(),
									"Transaction not scheduled!");

							Assertions.assertEquals(
									createTx.getResponseRecord().getReceipt().getScheduleID(),
									triggeredTx.getResponseRecord().getScheduleRef(),
									"Wrong schedule ID!");

							Assertions.assertTrue(
									transferListCheck(triggeredTx, asId("sender", spec), asId("receiver", spec),
											asId("payingAccount", spec), 1L),
									"Wrong transfer list!");
						})
				);
	}

	public HapiApiSpec executionWithDefaultPayerWorks() {
		long transferAmount = 1;
		return defaultHapiSpec("ExecutionAtExpiryWithDefaultPayerWorks")
				.given(
						cryptoCreate("sender").via("senderTxn"),
						cryptoCreate("receiver"),
						cryptoCreate("payingAccount"),
						scheduleCreate(
								"basicXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", transferAmount)
								)
						)
								.waitForExpiry()
								.withRelativeExpiry("senderTxn", 8)
								.payingWith("payingAccount")
								.recordingScheduledTxn()
								.via("createTx")
				).when(
						scheduleSign("basicXfer")
								.alsoSigningWith("sender")
								.via("signTx")
				).then(
						getScheduleInfo("basicXfer")
								.hasScheduleId("basicXfer")
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("senderTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo").via("triggeringTxn"),
						getScheduleInfo("basicXfer")
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
						withOpContext((spec, opLog) -> {
							var createTx = getTxnRecord("createTx");
							var signTx = getTxnRecord("signTx");
							var triggeringTx = getTxnRecord("triggeringTxn");
							var triggeredTx = getTxnRecord("createTx").scheduled();
							allRunFor(spec, createTx, signTx, triggeredTx, triggeringTx);

							Assertions.assertEquals(SUCCESS,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction be successful!");

							Instant triggerTime = Instant.ofEpochSecond(
									triggeringTx.getResponseRecord().getConsensusTimestamp().getSeconds(),
									triggeringTx.getResponseRecord().getConsensusTimestamp().getNanos());

							Instant triggeredTime = Instant.ofEpochSecond(
									triggeredTx.getResponseRecord().getConsensusTimestamp().getSeconds(),
									triggeredTx.getResponseRecord().getConsensusTimestamp().getNanos());

							Assertions.assertTrue(
									triggerTime.isBefore(triggeredTime),
									"Wrong consensus timestamp!");

							Assertions.assertEquals(
									createTx.getResponseRecord().getTransactionID().getTransactionValidStart(),
									triggeredTx.getResponseRecord().getTransactionID().getTransactionValidStart(),
									"Wrong transaction valid start!");

							Assertions.assertEquals(
									createTx.getResponseRecord().getTransactionID().getAccountID(),
									triggeredTx.getResponseRecord().getTransactionID().getAccountID(),
									"Wrong record account ID!");

							Assertions.assertTrue(
									triggeredTx.getResponseRecord().getTransactionID().getScheduled(),
									"Transaction not scheduled!");

							Assertions.assertEquals(
									createTx.getResponseRecord().getReceipt().getScheduleID(),
									triggeredTx.getResponseRecord().getScheduleRef(),
									"Wrong schedule ID!");

							Assertions.assertTrue(
									transferListCheck(triggeredTx, asId("sender", spec), asId("receiver", spec),
											asId("payingAccount", spec), transferAmount),
									"Wrong transfer list!");
						})
				);
	}

	public HapiApiSpec executionWithDefaultPayerButNoFundsFails() {
		long balance = 10_000_000L;
		long noBalance = 0L;
		long transferAmount = 1L;
		return defaultHapiSpec("ExecutionAtExpiryWithDefaultPayerButNoFundsFails")
				.given(
						cryptoCreate("payingAccount").balance(balance),
						cryptoCreate("luckyReceiver"),
						cryptoCreate("sender").balance(transferAmount).via("senderTxn"),
						cryptoCreate("receiver").balance(noBalance),
						scheduleCreate(
								"basicXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", transferAmount)
								)
						)
								.waitForExpiry()
								.withRelativeExpiry("senderTxn", 8)
								.payingWith("payingAccount")
								.recordingScheduledTxn()
								.via("createTx"),
						recordFeeAmount("createTx", "scheduleCreateFee")
				).when(
						cryptoTransfer(
								tinyBarsFromTo(
										"payingAccount",
										"luckyReceiver",
										(spec -> {
											long scheduleCreateFee = spec.registry().getAmount("scheduleCreateFee");
											return balance - scheduleCreateFee;
										}))),
						getAccountBalance("payingAccount").hasTinyBars(noBalance),
						scheduleSign("basicXfer")
								.alsoSigningWith("sender")
								.hasKnownStatus(SUCCESS)
				).then(
						getScheduleInfo("basicXfer")
								.hasScheduleId("basicXfer")
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("senderTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo").via("triggeringTxn"),
						getScheduleInfo("basicXfer")
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
						getAccountBalance("sender").hasTinyBars(transferAmount),
						getAccountBalance("receiver").hasTinyBars(noBalance),
						withOpContext((spec, opLog) -> {
							var triggeredTx = getTxnRecord("createTx").scheduled();

							allRunFor(spec, triggeredTx);

							Assertions.assertEquals(
									INSUFFICIENT_PAYER_BALANCE,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction should not be successful!");
						})
				);
	}

	public HapiApiSpec executionWithCustomPayerThatNeverSignsFails() {
		long transferAmount = 1;
		return defaultHapiSpec("ExecutionWithCustomPayerThatNeverSignsFails")
				.given(
						cryptoCreate("payingAccount"),
						cryptoCreate("sender").via("senderTxn"),
						cryptoCreate("receiver"),
						scheduleCreate(
								"basicXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", transferAmount)
								)
						)
								.waitForExpiry()
								.withRelativeExpiry("senderTxn", 8)
								.recordingScheduledTxn()
								.designatingPayer("payingAccount")
								.via("createTx")
				).when(
						scheduleSign("basicXfer")
								.alsoSigningWith("sender")
								.via("signTx")
								.hasKnownStatus(SUCCESS)
				).then(
						getScheduleInfo("basicXfer")
								.hasScheduleId("basicXfer")
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("senderTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo").via("triggeringTxn"),
						getScheduleInfo("basicXfer")
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
						getTxnRecord("createTx").scheduled().hasAnswerOnlyPrecheck(RECORD_NOT_FOUND)
				);
	}

	public HapiApiSpec executionWithCustomPayerButNoFundsFails() {
		long balance = 0L;
		long transferAmount = 1;
		return defaultHapiSpec("ExecutionAtExpiryWithCustomPayerButNoFundsFails")
				.given(
						cryptoCreate("payingAccount").balance(balance),
						cryptoCreate("sender").via("senderTxn"),
						cryptoCreate("receiver"),
						scheduleCreate(
								"basicXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", transferAmount)
								)
						)
								.waitForExpiry()
								.withRelativeExpiry("senderTxn", 8)
								.recordingScheduledTxn()
								.designatingPayer("payingAccount")
								.via("createTx")
				).when(
						scheduleSign("basicXfer")
								.alsoSigningWith("sender", "payingAccount")
								.via("signTx")
								.hasKnownStatus(SUCCESS)
				).then(
						getScheduleInfo("basicXfer")
								.hasScheduleId("basicXfer")
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("senderTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo").via("triggeringTxn"),
						getScheduleInfo("basicXfer")
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
						withOpContext((spec, opLog) -> {
							var triggeredTx = getTxnRecord("createTx").scheduled();

							allRunFor(spec, triggeredTx);

							Assertions.assertEquals(
									INSUFFICIENT_PAYER_BALANCE,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction should not be successful!");
						})
				);
	}


	public HapiApiSpec executionWithDefaultPayerButAccountDeletedFails() {
		long balance = 10_000_000L;
		long noBalance = 0L;
		long transferAmount = 1L;
		return defaultHapiSpec("ExecutionAtExpiryWithDefaultPayerButAccountDeletedFails")
				.given(
						cryptoCreate("payingAccount").balance(balance),
						cryptoCreate("luckyReceiver"),
						cryptoCreate("sender").balance(transferAmount).via("senderTxn"),
						cryptoCreate("receiver").balance(noBalance),
						scheduleCreate(
								"basicXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", transferAmount)
								)
						)
								.waitForExpiry()
								.withRelativeExpiry("senderTxn", 8)
								.recordingScheduledTxn()
								.payingWith("payingAccount")
								.via("createTx"),
						recordFeeAmount("createTx", "scheduleCreateFee")
				).when(
						cryptoDelete("payingAccount"),
						scheduleSign("basicXfer")
								.alsoSigningWith("sender")
								.hasKnownStatus(SUCCESS)
				).then(
						getAccountBalance("sender").hasTinyBars(transferAmount),
						getAccountBalance("receiver").hasTinyBars(noBalance),

						getScheduleInfo("basicXfer")
								.hasScheduleId("basicXfer")
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("senderTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo").via("triggeringTxn"),
						getScheduleInfo("basicXfer")
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),

						getAccountBalance("sender").hasTinyBars(transferAmount),
						getAccountBalance("receiver").hasTinyBars(noBalance),
						getTxnRecord("createTx").scheduled()
									.hasCostAnswerPrecheck(ACCOUNT_DELETED)
				);
	}

	public HapiApiSpec executionWithCustomPayerButAccountDeletedFails() {
		long balance = 10_000_000L;
		long noBalance = 0L;
		long transferAmount = 1;
		return defaultHapiSpec("ExecutionAtExpiryWithCustomPayerButAccountDeletedFails")
				.given(
						cryptoCreate("payingAccount").balance(balance),
						cryptoCreate("sender").balance(transferAmount).via("senderTxn"),
						cryptoCreate("receiver").balance(noBalance),
						scheduleCreate(
								"basicXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", transferAmount)
								)
						)
								.waitForExpiry()
								.withRelativeExpiry("senderTxn", 8)
								.recordingScheduledTxn()
								.designatingPayer("payingAccount")
								.alsoSigningWith("payingAccount")
								.via("createTx")
				).when(
						cryptoDelete("payingAccount"),
						scheduleSign("basicXfer")
								.alsoSigningWith("sender")
								.via("signTx")
								.hasKnownStatus(SUCCESS)
				).then(
						getAccountBalance("sender").hasTinyBars(transferAmount),
						getAccountBalance("receiver").hasTinyBars(noBalance),

						getScheduleInfo("basicXfer")
								.hasScheduleId("basicXfer")
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("senderTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo").via("triggeringTxn"),
						getScheduleInfo("basicXfer")
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),

						getAccountBalance("sender").hasTinyBars(transferAmount),
						getAccountBalance("receiver").hasTinyBars(noBalance),

						withOpContext((spec, opLog) -> {
							var triggeredTx = getTxnRecord("createTx").scheduled();

							allRunFor(spec, triggeredTx);

							Assertions.assertEquals(
									INSUFFICIENT_PAYER_BALANCE,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction should not be successful!");
						})
				);
	}

	public HapiApiSpec executionWithInvalidAccountAmountsFails() {
		long transferAmount = 100;
		long senderBalance = 1000L;
		long payingAccountBalance = 1_000_000L;
		long noBalance = 0L;
		return defaultHapiSpec("ExecutionAtExpiryWithInvalidAccountAmountsFails")
				.given(
						cryptoCreate("payingAccount").balance(payingAccountBalance),
						cryptoCreate("sender").balance(senderBalance).via("senderTxn"),
						cryptoCreate("receiver").balance(noBalance),
						scheduleCreate(
								"failedXfer",
								cryptoTransfer(
										tinyBarsFromToWithInvalidAmounts("sender", "receiver", transferAmount)
								)
						)
								.waitForExpiry()
								.withRelativeExpiry("senderTxn", 8)
								.designatingPayer("payingAccount")
								.recordingScheduledTxn()
								.via("createTx")
				)
				.when(
						scheduleSign("failedXfer")
								.alsoSigningWith("sender", "payingAccount")
								.via("signTx")
								.hasKnownStatus(SUCCESS)
				)
				.then(
						getScheduleInfo("failedXfer")
								.hasScheduleId("failedXfer")
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("senderTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo").via("triggeringTxn"),
						getScheduleInfo("failedXfer")
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
						getAccountBalance("sender").hasTinyBars(senderBalance),
						getAccountBalance("receiver").hasTinyBars(noBalance),
						withOpContext((spec, opLog) -> {
							var triggeredTx = getTxnRecord("createTx").scheduled();

							allRunFor(spec, triggeredTx);

							Assertions.assertEquals(
									INVALID_ACCOUNT_AMOUNTS,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction should not be successful!");
						})
				);
	}

	public HapiApiSpec executionWithCryptoInsufficientAccountBalanceFails() {
		long noBalance = 0L;
		long senderBalance = 100L;
		long transferAmount = 101L;
		long payerBalance = 1_000_000L;
		return defaultHapiSpec("ExecutionAtExpiryWithCryptoInsufficientAccountBalanceFails")
				.given(
						cryptoCreate("payingAccount").balance(payerBalance),
						cryptoCreate("sender").balance(senderBalance).via("senderTxn"),
						cryptoCreate("receiver").balance(noBalance),
						scheduleCreate(
								"failedXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", transferAmount)
								)
						)
								.waitForExpiry()
								.withRelativeExpiry("senderTxn", 8)
								.designatingPayer("payingAccount")
								.recordingScheduledTxn()
								.via("createTx")
				).when(
						scheduleSign("failedXfer")
								.alsoSigningWith("sender", "payingAccount")
								.via("signTx")
								.hasKnownStatus(SUCCESS)
				).then(
						getScheduleInfo("failedXfer")
								.hasScheduleId("basicXfer")
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("senderTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo").via("triggeringTxn"),
						getScheduleInfo("failedXfer")
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
						getAccountBalance("sender").hasTinyBars(senderBalance),
						getAccountBalance("receiver").hasTinyBars(noBalance),
						withOpContext((spec, opLog) -> {
							var triggeredTx = getTxnRecord("createTx").scheduled();

							allRunFor(spec, triggeredTx);

							Assertions.assertEquals(
									INSUFFICIENT_ACCOUNT_BALANCE,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction should not be successful!");
						})
				);
	}

	public HapiApiSpec executionWithCryptoSenderDeletedFails() {
		long noBalance = 0L;
		long senderBalance = 100L;
		long transferAmount = 101L;
		long payerBalance = 1_000_000L;
		return defaultHapiSpec("ExecutionAtExpiryWithCryptoSenderDeletedFails")
				.given(
						cryptoCreate("payingAccount").balance(payerBalance),
						cryptoCreate("sender").balance(senderBalance).via("senderTxn"),
						cryptoCreate("receiver").balance(noBalance),
						scheduleCreate(
								"failedXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", transferAmount)
								)
						)
								.waitForExpiry()
								.withRelativeExpiry("senderTxn", 8)
								.recordingScheduledTxn()
								.designatingPayer("payingAccount")
								.via("createTx")
				).when(
						cryptoDelete("sender"),
						scheduleSign("failedXfer")
								.alsoSigningWith("sender", "payingAccount")
								.via("signTx")
								.hasKnownStatus(SUCCESS)
				).then(
						getAccountBalance("receiver").hasTinyBars(noBalance),

						getScheduleInfo("failedXfer")
								.hasScheduleId("failedXfer")
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("senderTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo").via("triggeringTxn"),
						getScheduleInfo("failedXfer")
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),

						getAccountBalance("receiver").hasTinyBars(noBalance),

						withOpContext((spec, opLog) -> {
							var triggeredTx = getTxnRecord("createTx").scheduled();

							allRunFor(spec, triggeredTx);

							Assertions.assertEquals(
									ACCOUNT_DELETED,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction should not be successful!");
						})
				);
	}

	public HapiApiSpec executionTriggersWithWeirdlyRepeatedKey() {
		String schedule = "dupKeyXfer";

		return defaultHapiSpec("ExecutionAtExpiryTriggersWithWeirdlyRepeatedKey")
				.given(
						cryptoCreate("weirdlyPopularKey").via("weirdlyPopularKeyTxn"),
						cryptoCreate("sender1").key("weirdlyPopularKey").balance(1L),
						cryptoCreate("sender2").key("weirdlyPopularKey").balance(1L),
						cryptoCreate("sender3").key("weirdlyPopularKey").balance(1L),
						cryptoCreate("receiver").balance(0L),
						scheduleCreate(
								schedule,
								cryptoTransfer(
										tinyBarsFromTo("sender1", "receiver", 1L),
										tinyBarsFromTo("sender2", "receiver", 1L),
										tinyBarsFromTo("sender3", "receiver", 1L)
								)
						)
								.waitForExpiry()
								.withRelativeExpiry("weirdlyPopularKeyTxn", 8)
								.payingWith(DEFAULT_PAYER)
								.recordingScheduledTxn()
								.via("creation")
				).when(
						scheduleSign(schedule)
								.alsoSigningWith("weirdlyPopularKey")
				).then(
						getScheduleInfo(schedule)
								.hasScheduleId(schedule)
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("weirdlyPopularKeyTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo").via("triggeringTxn"),
						getScheduleInfo(schedule)
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
						getAccountBalance("sender1").hasTinyBars(0L),
						getAccountBalance("sender2").hasTinyBars(0L),
						getAccountBalance("sender3").hasTinyBars(0L),
						getAccountBalance("receiver").hasTinyBars(3L),
						scheduleSign(schedule)
								.alsoSigningWith("weirdlyPopularKey")
								.hasPrecheck(INVALID_SCHEDULE_ID)
				);
	}

	private HapiApiSpec executionNoSigTxnRequiredWorks() {
		return defaultHapiSpec("ExecutionAtExpiryWithNoSigRequiredWorks")
				.given(
						cryptoCreate("payingAccount"),
						cryptoCreate("receiver"),
						cryptoCreate("sender").via("senderTxn")
				).when(
						scheduleCreate(
								"basicXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", 1)
								)
						)
								.payingWith("payingAccount")
								.alsoSigningWith("sender")
								.waitForExpiry()
								.withRelativeExpiry("senderTxn", 8)
								.recordingScheduledTxn()
								.via("createTx")
				).then(
						getScheduleInfo("basicXfer")
								.hasScheduleId("basicXfer")
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("senderTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo").via("triggeringTxn"),
						getScheduleInfo("basicXfer")
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
						withOpContext((spec, opLog) -> {
							var createTx = getTxnRecord("createTx");
							var triggeringTx = getTxnRecord("triggeringTxn");
							var triggeredTx = getTxnRecord("createTx").scheduled();
							allRunFor(spec, createTx, triggeredTx, triggeringTx);

							Assertions.assertEquals(SUCCESS,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction be successful!");

							Instant triggerTime = Instant.ofEpochSecond(
									triggeringTx.getResponseRecord().getConsensusTimestamp().getSeconds(),
									triggeringTx.getResponseRecord().getConsensusTimestamp().getNanos());

							Instant triggeredTime = Instant.ofEpochSecond(
									triggeredTx.getResponseRecord().getConsensusTimestamp().getSeconds(),
									triggeredTx.getResponseRecord().getConsensusTimestamp().getNanos());

							Assertions.assertTrue(
									triggerTime.isBefore(triggeredTime),
									"Wrong consensus timestamp!");

							Assertions.assertEquals(
									createTx.getResponseRecord().getTransactionID().getTransactionValidStart(),
									triggeredTx.getResponseRecord().getTransactionID().getTransactionValidStart(),
									"Wrong transaction valid start!");

							Assertions.assertEquals(
									createTx.getResponseRecord().getTransactionID().getAccountID(),
									triggeredTx.getResponseRecord().getTransactionID().getAccountID(),
									"Wrong record account ID!");

							Assertions.assertTrue(
									triggeredTx.getResponseRecord().getTransactionID().getScheduled(),
									"Transaction not scheduled!");

							Assertions.assertEquals(
									createTx.getResponseRecord().getReceipt().getScheduleID(),
									triggeredTx.getResponseRecord().getScheduleRef(),
									"Wrong schedule ID!");

							Assertions.assertTrue(
									transferListCheck(triggeredTx, asId("sender", spec), asId("receiver", spec),
											asId("payingAccount", spec), 1L),
									"Wrong transfer list!");
						})
				);
	}

	public HapiApiSpec waitForExpiryIgnoredWhenLongTermDisabled() {

		return defaultHapiSpec("WaitForExpiryIgnoredWhenLongTermDisabled")
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
						)
								.waitForExpiry()
								.designatingPayer("payer")
								.alsoSigningWith("sender", "receiver"),
						getAccountBalance("receiver").hasTinyBars(0L),
						scheduleSign("threeSigXfer").alsoSigningWith("payer")
				).then(
						getAccountBalance("receiver").hasTinyBars(1L),
						getScheduleInfo("threeSigXfer")
								.hasScheduleId("threeSigXfer")
								.hasWaitForExpiry(false)
								.isExecuted()
				);
	}

	public HapiApiSpec expiryIgnoredWhenLongTermDisabled() {

		return defaultHapiSpec("ExpiryIgnoredWhenLongTermDisabled")
				.given(
						cryptoCreate("payer").balance(ONE_HBAR),
						cryptoCreate("sender").balance(1L).via("senderTxn"),
						cryptoCreate("receiver").balance(0L).receiverSigRequired(true)
				).when(
						overriding("ledger.schedule.txExpiryTimeSecs", "" + 5),
						scheduleCreate(
								"threeSigXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", 1)
								).fee(ONE_HBAR)
						)
								.withRelativeExpiry("senderTxn", 500)
								.designatingPayer("payer")
								.alsoSigningWith("sender", "receiver")
				).then(
						overriding("ledger.schedule.txExpiryTimeSecs", defaultTxExpiry),

						getScheduleInfo("threeSigXfer")
								.hasScheduleId("threeSigXfer")
								.isNotExecuted()
								.isNotDeleted(),
						sleepFor(9000),
						cryptoCreate("foo"),
						getScheduleInfo("threeSigXfer")
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),

						getAccountBalance("receiver").hasTinyBars(0L)
				);
	}

	public HapiApiSpec waitForExpiryIgnoredWhenLongTermDisabledThenEnabled() {

		return defaultHapiSpec("WaitForExpiryIgnoredWhenLongTermDisabledThenEnabled")
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
						)
								.waitForExpiry()
								.designatingPayer("payer")
								.alsoSigningWith("sender", "receiver"),
						getAccountBalance("receiver").hasTinyBars(0L),
						overriding("scheduling.longTermEnabled", "true"),
						scheduleSign("threeSigXfer").alsoSigningWith("payer")
				).then(
						getAccountBalance("receiver").hasTinyBars(1L),
						getScheduleInfo("threeSigXfer")
								.hasScheduleId("threeSigXfer")
								.hasWaitForExpiry(false)
								.isExecuted(),
						overriding("scheduling.longTermEnabled", "false")
				);
	}

	public HapiApiSpec expiryIgnoredWhenLongTermDisabledThenEnabled() {

		return defaultHapiSpec("ExpiryIgnoredWhenLongTermDisabledThenEnabled")
				.given(
						cryptoCreate("payer").balance(ONE_HBAR),
						cryptoCreate("sender").balance(1L).via("senderTxn"),
						cryptoCreate("receiver").balance(0L).receiverSigRequired(true)
				).when(
						overriding("ledger.schedule.txExpiryTimeSecs", "" + 5),
						scheduleCreate(
								"threeSigXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", 1)
								).fee(ONE_HBAR)
						)
								.withRelativeExpiry("senderTxn", 500)
								.designatingPayer("payer")
								.alsoSigningWith("sender", "receiver")
				).then(
						overriding("ledger.schedule.txExpiryTimeSecs", defaultTxExpiry),
						overriding("scheduling.longTermEnabled", "true"),

						getScheduleInfo("threeSigXfer")
								.hasScheduleId("threeSigXfer")
								.isNotExecuted()
								.isNotDeleted(),
						sleepFor(9000),
						cryptoCreate("foo"),
						getScheduleInfo("threeSigXfer")
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),

						overriding("scheduling.longTermEnabled", "false"),

						getAccountBalance("receiver").hasTinyBars(0L)
				);
	}


	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	public static HapiApiSpec enableLongTermScheduledTransactions() {
		return defaultHapiSpec("EnableLongTermScheduledTransactions")
				.given().when().then(
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"scheduling.longTermEnabled", "true"
								))
				);
	}

	public static HapiApiSpec disableLongTermScheduledTransactions() {
		return defaultHapiSpec("DisableLongTermScheduledTransactions")
				.given().when().then(
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"scheduling.longTermEnabled", "false"
								))
				);
	}

	public static HapiApiSpec setLongTermScheduledTransactionsToDefault() {
		return defaultHapiSpec("SetLongTermScheduledTransactionsToDefault")
				.given().when().then(
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"scheduling.longTermEnabled", defaultLongTermEnabled
								))
				);
	}

	public static List<HapiApiSpec> withAndWithoutLongTermEnabled(Supplier<List<HapiApiSpec>> getSpecs) {
		List<HapiApiSpec> list = new ArrayList<>();
		list.add(disableLongTermScheduledTransactions());
		list.addAll(getSpecs.get());
		list.add(enableLongTermScheduledTransactions());
		var withEnabled = getSpecs.get();
		withEnabled.forEach(s -> s.appendToName("WithLongTermEnabled"));
		list.addAll(withEnabled);
		list.add(setLongTermScheduledTransactionsToDefault());
		return list;
	}
}

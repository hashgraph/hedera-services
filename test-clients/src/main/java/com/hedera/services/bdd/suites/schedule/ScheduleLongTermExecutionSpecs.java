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
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUppercase;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.systemFileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uncheckedSubmit;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromToWithInvalidAmounts;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeAbort;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.prepareUpgrade;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.recordFeeAmount;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.freeze.UpgradeSuite.poeticUpgradeLoc;
import static com.hedera.services.bdd.suites.freeze.UpgradeSuite.standardUpdateFile;
import static com.hedera.services.bdd.suites.schedule.ScheduleExecutionSpecs.ORIG_FILE;
import static com.hedera.services.bdd.suites.schedule.ScheduleExecutionSpecs.getPoeticUpgradeHash;
import static com.hedera.services.bdd.suites.schedule.ScheduleExecutionSpecs.transferListCheck;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.ThrottleDefsLoader.protoDefsFromResource;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RECORD_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_EXPIRATION_TIME_MUST_BE_HIGHER_THAN_CONSENSUS_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_EXPIRATION_TIME_TOO_FAR_IN_FUTURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_FUTURE_THROTTLE_EXCEEDED;
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
			executionWithCustomPayerWhoSignsAtCreationAsPayerWorks(),
			executionWithCustomPayerAndAdminKeyWorks(),
			executionWithDefaultPayerButNoFundsFails(),
			executionWithCustomPayerButNoFundsFails(),
			executionWithDefaultPayerButAccountDeletedFails(),
			executionWithCustomPayerButAccountDeletedFails(),
			executionWithInvalidAccountAmountsFails(),
			executionWithCryptoInsufficientAccountBalanceFails(),
			executionWithCryptoSenderDeletedFails(),
			executionTriggersWithWeirdlyRepeatedKey(),

			scheduledFreezeWorksAsExpected(),
			scheduledFreezeWithUnauthorizedPayerFails(),
			scheduledPermissionedFileUpdateWorksAsExpected(),
			scheduledPermissionedFileUpdateUnauthorizedPayerFails(),
			scheduledSystemDeleteWorksAsExpected(),
			scheduledSystemDeleteUnauthorizedPayerFails(),

			executionWithContractCallWorksAtExpiry(),
			executionWithContractCreateWorksAtExpiry(),

			futureThrottlesAreRespected(),

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
	private HapiApiSpec executionWithCustomPayerWhoSignsAtCreationAsPayerWorks() {
		return defaultHapiSpec("ExecutionAtExpiryWithCustomPayerWhoSignsAtCreationAsPayerWorks")
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
								.designatingPayer("payingAccount")
								.waitForExpiry()
								.withRelativeExpiry("senderTxn", 8)
								.recordingScheduledTxn()
								.via("createTx"),
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

	public HapiApiSpec executionWithContractCallWorksAtExpiry() {
		return defaultHapiSpec("ExecutionWithContractCallWorksAtExpiry")
				.given(
						overriding("scheduling.whitelist", "ContractCall"),
						uploadInitCode("SimpleUpdate"),
						contractCreate("SimpleUpdate").gas(500_000L),
						cryptoCreate("payingAccount").balance(1000000000000L).via("payingAccountTxn")
				).when(
						scheduleCreate(
								"basicXfer",
								contractCall("SimpleUpdate", "set", 5, 42)
										.gas(300000L)
						)
								.waitForExpiry()
								.withRelativeExpiry("payingAccountTxn", 8)
								.designatingPayer("payingAccount")
								.alsoSigningWith("payingAccount")
								.recordingScheduledTxn()
								.via("createTx")
				).then(
						getScheduleInfo("basicXfer")
								.hasScheduleId("basicXfer")
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("payingAccountTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo").via("triggeringTxn"),
						getScheduleInfo("basicXfer")
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
						overriding("scheduling.whitelist", HapiSpecSetup.getDefaultNodeProps().get("scheduling.whitelist")),
						getAccountBalance("payingAccount").hasTinyBars(spec -> bal ->
										bal < 1000000000000L ? Optional.empty() : Optional.of("didnt change")),
						withOpContext((spec, opLog) -> {
							var triggeredTx = getTxnRecord("createTx").scheduled();
							allRunFor(spec, triggeredTx);

							Assertions.assertEquals(SUCCESS,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction be successful!");

							Assertions.assertTrue(
									triggeredTx.getResponseRecord()
											.getContractCallResult().getContractCallResult().size() >= 0);
						})
				);
	}
	public HapiApiSpec executionWithContractCreateWorksAtExpiry() {
		return defaultHapiSpec("ExecutionWithContractCreateWorksAtExpiry")
				.given(
						overriding("scheduling.whitelist", "ContractCreate"),
						uploadInitCode("SimpleUpdate"),
						cryptoCreate("payingAccount").balance(1000000000000L).via("payingAccountTxn")
				).when(
						scheduleCreate(
								"basicXfer",
								contractCreate("SimpleUpdate").gas(500_000L)
										.adminKey("payingAccount")
						)
								.waitForExpiry()
								.withRelativeExpiry("payingAccountTxn", 8)
								.designatingPayer("payingAccount")
								.alsoSigningWith("payingAccount")
								.recordingScheduledTxn()
								.via("createTx")
				).then(
						getScheduleInfo("basicXfer")
								.hasScheduleId("basicXfer")
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("payingAccountTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo").via("triggeringTxn"),
						getScheduleInfo("basicXfer")
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
						overriding("scheduling.whitelist", HapiSpecSetup.getDefaultNodeProps().get("scheduling.whitelist")),
						getAccountBalance("payingAccount").hasTinyBars(spec -> bal ->
										bal < 1000000000000L ? Optional.empty() : Optional.of("didnt change")),
						withOpContext((spec, opLog) -> {
							var triggeredTx = getTxnRecord("createTx").scheduled();
							allRunFor(spec, triggeredTx);

							Assertions.assertEquals(SUCCESS,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction be successful!");

							Assertions.assertTrue(
									triggeredTx.getResponseRecord().getReceipt().hasContractID());

							Assertions.assertTrue(
									triggeredTx.getResponseRecord()
											.getContractCreateResult().getContractCallResult().size() >= 0);
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

	private HapiApiSpec scheduledFreezeWorksAsExpected() {

		final byte[] poeticUpgradeHash = getPoeticUpgradeHash();

		return defaultHapiSpec("ScheduledFreezeWorksAsExpectedAtExpiry")
				.given(
						cryptoCreate("payingAccount").via("payerTxn"),
						overriding("scheduling.whitelist", "Freeze"),
						fileUpdate(standardUpdateFile)
								.signedBy(FREEZE_ADMIN)
								.path(poeticUpgradeLoc)
								.payingWith(FREEZE_ADMIN),
						scheduleCreate("validSchedule",
							prepareUpgrade()
									.withUpdateFile(standardUpdateFile)
									.havingHash(poeticUpgradeHash)
						)
								.withEntityMemo(randomUppercase(100))
								.designatingPayer(GENESIS)
								.payingWith("payingAccount")
								.waitForExpiry()
								.recordingScheduledTxn()
								.withRelativeExpiry("payerTxn", 8)
								.via("successTxn")
				)
				.when(
						scheduleSign("validSchedule")
								.alsoSigningWith(GENESIS)
								.payingWith("payingAccount")
								.hasKnownStatus(SUCCESS)
				)
				.then(
						getScheduleInfo("validSchedule")
								.hasScheduleId("validSchedule")
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("payerTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo").via("triggeringTxn"),
						getScheduleInfo("validSchedule")
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),

						freezeAbort().payingWith(GENESIS),
						overriding("scheduling.whitelist", HapiSpecSetup.getDefaultNodeProps().get("scheduling.whitelist")),
						withOpContext((spec, opLog) -> {
							var triggeredTx = getTxnRecord("successTxn").scheduled();
							allRunFor(spec, triggeredTx);

							Assertions.assertEquals(SUCCESS,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction be successful!");
						})
				);
	}

	private HapiApiSpec scheduledFreezeWithUnauthorizedPayerFails() {

		final byte[] poeticUpgradeHash = getPoeticUpgradeHash();

		return defaultHapiSpec("ScheduledFreezeWithUnauthorizedPayerFailsAtExpiry")
				.given(
						cryptoCreate("payingAccount").via("payerTxn"),
						cryptoCreate("payingAccount2"),
						overriding("scheduling.whitelist", "Freeze"),
						fileUpdate(standardUpdateFile)
								.signedBy(FREEZE_ADMIN)
								.path(poeticUpgradeLoc)
								.payingWith(FREEZE_ADMIN)
				)
				.when(
				)
				.then(
						scheduleCreate("validSchedule",
							prepareUpgrade()
									.withUpdateFile(standardUpdateFile)
									.havingHash(poeticUpgradeHash)
						)
								.withEntityMemo(randomUppercase(100))
								.designatingPayer("payingAccount2")
								.waitForExpiry()
								.withRelativeExpiry("payerTxn", 8)
								.payingWith("payingAccount")
								// future throttles will be exceeded because there is no throttle for freeze
								// and the custom payer is not exempt from throttles like and admin
								// user would be
								.hasKnownStatus(SCHEDULE_FUTURE_THROTTLE_EXCEEDED),
						overriding("scheduling.whitelist", HapiSpecSetup.getDefaultNodeProps().get("scheduling.whitelist"))
				);
	}

	private HapiApiSpec scheduledPermissionedFileUpdateWorksAsExpected() {

		return defaultHapiSpec("ScheduledPermissionedFileUpdateWorksAsExpectedAtExpiry")
				.given(
						cryptoCreate("payingAccount").via("payerTxn"),
						overriding("scheduling.whitelist", "FileUpdate"),
						scheduleCreate("validSchedule",
								fileUpdate(standardUpdateFile).contents("fooo!")
						)
								.withEntityMemo(randomUppercase(100))
								.designatingPayer(FREEZE_ADMIN)
								.payingWith("payingAccount")
								.waitForExpiry()
								.withRelativeExpiry("payerTxn", 8)
								.recordingScheduledTxn()
								.via("successTxn")
				)
				.when(
						scheduleSign("validSchedule")
								.alsoSigningWith(FREEZE_ADMIN)
								.payingWith("payingAccount")
								.hasKnownStatus(SUCCESS)
				)
				.then(
						getScheduleInfo("validSchedule")
								.hasScheduleId("validSchedule")
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("payerTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo").via("triggeringTxn"),
						getScheduleInfo("validSchedule")
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),

						overriding("scheduling.whitelist", HapiSpecSetup.getDefaultNodeProps().get("scheduling.whitelist")),
						withOpContext((spec, opLog) -> {
							var triggeredTx = getTxnRecord("successTxn").scheduled();
							allRunFor(spec, triggeredTx);

							Assertions.assertEquals(SUCCESS,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction be successful!");
						})
				);
	}

	private HapiApiSpec scheduledPermissionedFileUpdateUnauthorizedPayerFails() {

		return defaultHapiSpec("ScheduledPermissionedFileUpdateUnauthorizedPayerFailsAtExpiry")
				.given(
						cryptoCreate("payingAccount").via("payerTxn"),
						cryptoCreate("payingAccount2"),
						overriding("scheduling.whitelist", "FileUpdate"),
						scheduleCreate("validSchedule",
							fileUpdate(standardUpdateFile).contents("fooo!")
						)
								.withEntityMemo(randomUppercase(100))
								.designatingPayer("payingAccount2")
								.payingWith("payingAccount")
								.waitForExpiry()
								.withRelativeExpiry("payerTxn", 8)
								.recordingScheduledTxn()
								.via("successTxn")
				)
				.when(
						scheduleSign("validSchedule")
								.alsoSigningWith("payingAccount2", FREEZE_ADMIN)
								.payingWith("payingAccount")
								.hasKnownStatus(SUCCESS)
				)
				.then(
						getScheduleInfo("validSchedule")
								.hasScheduleId("validSchedule")
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("payerTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo").via("triggeringTxn"),
						getScheduleInfo("validSchedule")
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),

						overriding("scheduling.whitelist", HapiSpecSetup.getDefaultNodeProps().get("scheduling.whitelist")),
						withOpContext((spec, opLog) -> {
							var triggeredTx = getTxnRecord("successTxn").scheduled();
							allRunFor(spec, triggeredTx);

							Assertions.assertEquals(AUTHORIZATION_FAILED,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction be AUTHORIZATION_FAILED!");
						})
				);
	}
	private HapiApiSpec scheduledSystemDeleteWorksAsExpected() {

		return defaultHapiSpec("ScheduledSystemDeleteWorksAsExpectedAtExpiry")
				.given(
						cryptoCreate("payingAccount").via("payerTxn"),
						fileCreate("misc")
								.lifetime(THREE_MONTHS_IN_SECONDS)
								.contents(ORIG_FILE),
						overriding("scheduling.whitelist", "SystemDelete"),
						scheduleCreate("validSchedule",
							systemFileDelete("misc")
									.updatingExpiry(1L)
						)
								.withEntityMemo(randomUppercase(100))
								.designatingPayer(SYSTEM_DELETE_ADMIN)
								.payingWith("payingAccount")
								.waitForExpiry()
								.withRelativeExpiry("payerTxn", 8)
								.recordingScheduledTxn()
								.via("successTxn")
				)
				.when(
						scheduleSign("validSchedule")
								.alsoSigningWith(SYSTEM_DELETE_ADMIN)
								.payingWith("payingAccount")
								.hasKnownStatus(SUCCESS)
				)
				.then(
						getScheduleInfo("validSchedule")
								.hasScheduleId("validSchedule")
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("payerTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo").via("triggeringTxn"),
						getScheduleInfo("validSchedule")
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),

						overriding("scheduling.whitelist", HapiSpecSetup.getDefaultNodeProps().get("scheduling.whitelist")),
						getFileInfo("misc")
								.nodePayment(1_234L)
								.hasAnswerOnlyPrecheck(INVALID_FILE_ID),
						withOpContext((spec, opLog) -> {
							var triggeredTx = getTxnRecord("successTxn").scheduled();
							allRunFor(spec, triggeredTx);

							Assertions.assertEquals(SUCCESS,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction be successful!");
						})
				);
	}

	private HapiApiSpec scheduledSystemDeleteUnauthorizedPayerFails() {

		return defaultHapiSpec("ScheduledSystemDeleteUnauthorizedPayerFailsAtExpiry")
				.given(
						cryptoCreate("payingAccount").via("payerTxn"),
						cryptoCreate("payingAccount2"),
						fileCreate("misc")
								.lifetime(THREE_MONTHS_IN_SECONDS)
								.contents(ORIG_FILE),
						overriding("scheduling.whitelist", "SystemDelete")
				)
				.when(
				)
				.then(
						scheduleCreate("validSchedule",
							systemFileDelete("misc")
									.updatingExpiry(1L)
						)
								.withEntityMemo(randomUppercase(100))
								.designatingPayer("payingAccount2")
								.payingWith("payingAccount")
								.waitForExpiry()
								.withRelativeExpiry("payerTxn", 8)
								// future throttles will be exceeded because there is no throttle for system delete
								// and the custom payer is not exempt from throttles like and admin
								// user would be
								.hasKnownStatus(SCHEDULE_FUTURE_THROTTLE_EXCEEDED),
						overriding("scheduling.whitelist", HapiSpecSetup.getDefaultNodeProps().get("scheduling.whitelist"))
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

	private HapiApiSpec failsWithExpiryInPast() {
		return defaultHapiSpec("FailsWithExpiryInPast")
				.given(
						cryptoCreate("sender").via("senderTxn")
				).when(
						scheduleCreate("validSchedule",
								cryptoTransfer(tinyBarsFromTo("sender", GENESIS, 1))
						)
								.withRelativeExpiry("senderTxn", -1)
								.hasKnownStatus(SCHEDULE_EXPIRATION_TIME_MUST_BE_HIGHER_THAN_CONSENSUS_TIME)
				)
				.then();
	}

	private HapiApiSpec failsWithExpiryInFarFuture() {
		return defaultHapiSpec("FailsWithExpiryInFarFuture")
				.given(
						cryptoCreate("sender").balance(ONE_MILLION_HBARS).via("senderTxn")
				).when(
						scheduleCreate("validSchedule",
								cryptoTransfer(tinyBarsFromTo("sender", GENESIS, 1))
						)
								.payingWith("sender")
								.fee(THOUSAND_HBAR)
								.withRelativeExpiry("senderTxn",
										Long.parseLong(HapiSpecSetup.getDefaultNodeProps().
												get("scheduling.maxExpirationFutureSeconds")) + 10)
								.hasKnownStatus(SCHEDULE_EXPIRATION_TIME_TOO_FAR_IN_FUTURE)
				)
				.then();
	}

	private HapiApiSpec congestionPricingDoesNotAffectScheduleExecutionAtExpiry() {
		var artificialLimits = protoDefsFromResource("testSystemFiles/artificial-limits-congestion.json");
		var defaultThrottles = protoDefsFromResource("testSystemFiles/throttles-dev.json");
		var contract = "Multipurpose";

		AtomicLong normalPriceScheduled = new AtomicLong();
		AtomicLong normalPriceRegular = new AtomicLong();

		return defaultHapiSpec("CongestionPricingAffectsImmediateScheduleExecution")
				.given(
						cryptoCreate("civilian")
								.payingWith(GENESIS)
								.balance(ONE_MILLION_HBARS).via("civilianTxn"),
							overriding("scheduling.whitelist", "ContractCall"),

						uploadInitCode(contract),
						contractCreate(contract),

						contractCall(contract)
								.payingWith("civilian")
								.fee(ONE_HUNDRED_HBARS)
								.sending(ONE_HBAR)
								.via("cheapCallRegular"),
						getTxnRecord("cheapCallRegular")
								.providingFeeTo(normalFee -> {
									log.info("Normal fee is {}", normalFee);
									normalPriceRegular.set(normalFee);
								}),

						scheduleCreate("cheapSchedule",
							contractCall(contract)
									.fee(ONE_HUNDRED_HBARS)
									.sending(ONE_HBAR)
						)
								.withEntityMemo(randomUppercase(100))
								.designatingPayer("civilian")
								.alsoSigningWith("civilian")
								.waitForExpiry()
								.withRelativeExpiry("civilianTxn", 8)
								.payingWith(GENESIS)
								.via("cheapCallScheduled"),

						sleepFor(9000),
						cryptoCreate("foo").via("fooTxn"),

						getTxnRecord("cheapCallScheduled")
								.scheduled()
								.providingFeeTo(normalFee -> {
									log.info("Normal fee is {}", normalFee);
									normalPriceScheduled.set(normalFee);
								}),

						scheduleCreate("validSchedule",
							contractCall(contract)
									.fee(ONE_HUNDRED_HBARS)
									.sending(ONE_HBAR)
						)
								.withEntityMemo(randomUppercase(100))
								.designatingPayer("civilian")
								.alsoSigningWith("civilian")
								.waitForExpiry()
								.withRelativeExpiry("fooTxn", 8)
								.payingWith(GENESIS)
								.via("pricyCallScheduled"),


						fileUpdate(APP_PROPERTIES)
								.fee(ONE_HUNDRED_HBARS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.overridingProps(Map.of(
										"fees.percentCongestionMultipliers", "1,7x",
										"fees.minCongestionPeriod", "1"
								)),
						fileUpdate(THROTTLE_DEFS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents(artificialLimits.toByteArray())
				)
				.when(

						blockingOrder(IntStream.range(0, 75).mapToObj(i -> new HapiSpecOperation[] {
								usableTxnIdNamed("uncheckedTxn" + i).payerId("civilian"),
								uncheckedSubmit(
										contractCall(contract)
												.signedBy("civilian")
												.fee(ONE_HUNDRED_HBARS)
												.sending(ONE_HBAR)
												.txnId("uncheckedTxn" + i)
								).payingWith(GENESIS),
								sleepFor(125)
						}).flatMap(Arrays::stream).toArray(HapiSpecOperation[]::new)),

						contractCall(contract)
								.payingWith("civilian")
								.fee(ONE_HUNDRED_HBARS)
								.sending(ONE_HBAR)
								.via("pricyCallRegular")
				)
				.then(

						fileUpdate(THROTTLE_DEFS)
								.fee(ONE_HUNDRED_HBARS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents(defaultThrottles.toByteArray()),
						fileUpdate(APP_PROPERTIES)
								.fee(ONE_HUNDRED_HBARS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.overridingProps(Map.of(
										"fees.percentCongestionMultipliers", HapiSpecSetup.getDefaultNodeProps().get("fees.percentCongestionMultipliers"),
										"fees.minCongestionPeriod", HapiSpecSetup.getDefaultNodeProps().get("fees.minCongestionPeriod"),
										"scheduling.whitelist", HapiSpecSetup.getDefaultNodeProps().get("scheduling.whitelist")
								)),
						cryptoTransfer(HapiCryptoTransfer.tinyBarsFromTo(GENESIS, FUNDING, 1))
								.payingWith(GENESIS),
						getScheduleInfo("validSchedule")
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),

						withOpContext((spec, opLog) -> {
							var regularTx = getTxnRecord("pricyCallRegular");
							var scheduledTx = getTxnRecord("pricyCallScheduled").scheduled();
							allRunFor(spec, regularTx, scheduledTx);

							Assertions.assertEquals(SUCCESS,
									scheduledTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction be successful!");
							Assertions.assertEquals(SUCCESS,
									regularTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction be successful!");

							var priceScheduled = scheduledTx.getResponseRecord().getTransactionFee();
							var priceRegular = regularTx.getResponseRecord().getTransactionFee();

							Assertions.assertEquals(
									7.0,
									(1.0 * priceRegular) / normalPriceRegular.get(),
									0.1,
									"~7x multiplier should be in affect for regular txns!");

							Assertions.assertEquals(
									1.0,
									(1.0 * priceScheduled) / normalPriceScheduled.get(),
									0.1,
									"no multiplier should be in affect for execution at expiry!");
						})
				);
	}


	private HapiApiSpec futureThrottlesAreRespected() {
		var artificialLimits = protoDefsFromResource("testSystemFiles/artificial-limits-schedule.json");
		var defaultThrottles = protoDefsFromResource("testSystemFiles/throttles-dev.json");

		return defaultHapiSpec("FutureThrottlesAreRespected")
				.given(
						cryptoCreate("sender").balance(ONE_MILLION_HBARS).via("senderTxn"),
						cryptoCreate("receiver"),

						overriding("scheduling.maxTxnPerSecond", "100"),
						fileUpdate(THROTTLE_DEFS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents(artificialLimits.toByteArray()),

						sleepFor(500)
				)
				.when(

						blockingOrder(IntStream.range(0, 17).mapToObj(i -> new HapiSpecOperation[] {
								scheduleCreate(
										"twoSigXfer" + i,
										cryptoTransfer(
												tinyBarsFromTo("sender", "receiver", 1)
										).fee(ONE_HBAR)
								)
										.withEntityMemo(randomUppercase(100))
										.payingWith("sender")
										.waitForExpiry()
										.withRelativeExpiry("senderTxn", 120),
						}).flatMap(Arrays::stream).toArray(HapiSpecOperation[]::new)),

						scheduleCreate(
								"twoSigXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", 1)
								).fee(ONE_HBAR)
						)
								.withEntityMemo(randomUppercase(100))
								.payingWith("sender")
								.waitForExpiry()
								.withRelativeExpiry("senderTxn", 120)
								.hasKnownStatus(SCHEDULE_FUTURE_THROTTLE_EXCEEDED)

				)
				.then(

						overriding("scheduling.maxTxnPerSecond",
								HapiSpecSetup.getDefaultNodeProps().get("scheduling.maxTxnPerSecond")),
						fileUpdate(THROTTLE_DEFS)
								.fee(ONE_HUNDRED_HBARS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents(defaultThrottles.toByteArray()),

						cryptoTransfer(HapiCryptoTransfer.tinyBarsFromTo(GENESIS, FUNDING, 1))
								.payingWith(GENESIS)

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

	public static List<HapiApiSpec> withAndWithoutLongTermEnabled(Function<Boolean, List<HapiApiSpec>> getSpecs) {
		List<HapiApiSpec> list = new ArrayList<>();
		list.add(disableLongTermScheduledTransactions());
		list.addAll(getSpecs.apply(false));
		list.add(enableLongTermScheduledTransactions());
		var withEnabled = getSpecs.apply(true);
		withEnabled.forEach(s -> s.appendToName("WithLongTermEnabled"));
		list.addAll(withEnabled);
		list.add(setLongTermScheduledTransactionsToDefault());
		return list;
	}
}

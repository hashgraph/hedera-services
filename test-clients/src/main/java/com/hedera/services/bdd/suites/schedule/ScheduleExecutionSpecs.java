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
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.updateTopic;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.recordFeeAmount;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_NEW_VALID_SIGNATURES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class ScheduleExecutionSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ScheduleExecutionSpecs.class);
	private static final int SCHEDULE_EXPIRY_TIME_SECS = 10;

	private static final String defaultTxExpiry =
			HapiSpecSetup.getDefaultNodeProps().get("ledger.schedule.txExpiryTimeSecs");

	public static void main(String... args) {
		new ScheduleExecutionSpecs().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
//				suiteSetup(),
//				executionWithDefaultPayerWorks(),
//				executionWithCustomPayerWorks(),
//				executionWithDefaultPayerButNoFundsFails(),
//				executionWithCustomPayerButNoFundsFails(),
//				executionTriggersWithWeirdlyRepeatedKey(),
//				executionTriggersWithWeirdlyRepeatedKey(),
				executionTriggersOnceTopicHasSatisfiedSubmitKey(),
//				suiteCleanup(),
		});
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

	public HapiApiSpec executionTriggersOnceTopicHasSatisfiedSubmitKey() {
		String adminKey = "admin", submitKey = "submit";
		String mutableTopic = "XXX";
		String schedule = "deferredSubmitMsg";

		return defaultHapiSpec("ExecutionTriggersOnceTopicHasNoSubmitKey")
				.given(
						newKeyNamed(adminKey),
						newKeyNamed(submitKey),
						createTopic(mutableTopic)
								.adminKeyName(adminKey)
								.submitKeyName(submitKey),
						cryptoCreate("somebody"),
						scheduleCreate(schedule,
								submitMessageTo(mutableTopic)
										.message("Little did they care who danced between / " +
												"And little she by whom her dance was seen")
						)
								.designatingPayer("somebody")
								.payingWith(DEFAULT_PAYER)
								.alsoSigningWith("somebody")
								.via("creation"),
						getTopicInfo(mutableTopic).hasSeqNo(0L)
				).when(
						scheduleSign(schedule)
								.alsoSigningWith(adminKey)
								.hasKnownStatus(NO_NEW_VALID_SIGNATURES),
						updateTopic(mutableTopic).submitKey("somebody"),
						scheduleSign(schedule)
				).then(
						getScheduleInfo(schedule).isExecuted(),
						getTopicInfo(mutableTopic).hasSeqNo(1L)
				);
	}


	public HapiApiSpec executionTriggersWithWeirdlyRepeatedKey() {
		String schedule = "dupKeyXfer";

		return defaultHapiSpec("ExecutionTriggersWithWeirdlyRepeatedKey")
				.given(
						cryptoCreate("weirdlyPopularKey"),
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
								.payingWith(DEFAULT_PAYER)
								.via("creation")
				).when(
						scheduleSign(schedule)
								.alsoSigningWith("weirdlyPopularKey")
				).then(
						getScheduleInfo(schedule).isExecuted(),
						getAccountBalance("sender1").hasTinyBars(0L),
						getAccountBalance("sender2").hasTinyBars(0L),
						getAccountBalance("sender3").hasTinyBars(0L),
						getAccountBalance("receiver").hasTinyBars(3L),
						scheduleSign(schedule)
								.via("repeatedSigning")
								.alsoSigningWith("weirdlyPopularKey")
								.hasKnownStatus(SCHEDULE_ALREADY_EXECUTED),
						getTxnRecord("repeatedSigning").logged()
				);
	}

	public HapiApiSpec executionWithDefaultPayerWorks() {
		long transferAmount = 1;
		return defaultHapiSpec("ExecutionWithDefaultPayerWorks")
				.given(
						cryptoCreate("sender"),
						cryptoCreate("receiver"),
						cryptoCreate("payingAccount"),
						scheduleCreate(
								"basicXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", transferAmount)
								)
						)
								.payingWith("payingAccount")
								.via("createTx")
				).when(
						scheduleSign("basicXfer")
								.alsoSigningWith("sender")
								.via("signTx")
				).then(
						withOpContext((spec, opLog) -> {
							var createTx = getTxnRecord("createTx");
							var signTx = getTxnRecord("signTx");
							var triggeredTx = getTxnRecord("createTx").scheduled();
							allRunFor(spec, createTx, signTx, triggeredTx);

							Assert.assertEquals("Wrong consensus timestamp!",
									signTx.getResponseRecord().getConsensusTimestamp().getNanos() + 1,
									triggeredTx.getResponseRecord().getConsensusTimestamp().getNanos());

							Assert.assertEquals("Wrong transaction valid start!",
									createTx.getResponseRecord().getTransactionID().getTransactionValidStart(),
									triggeredTx.getResponseRecord().getTransactionID().getTransactionValidStart());

							Assert.assertEquals("Wrong record account ID!",
									createTx.getResponseRecord().getTransactionID().getAccountID(),
									triggeredTx.getResponseRecord().getTransactionID().getAccountID());

							Assert.assertTrue("Transaction not scheduled!",
									triggeredTx.getResponseRecord().getTransactionID().getScheduled());

							Assert.assertEquals("Wrong schedule ID!",
									createTx.getResponseRecord().getReceipt().getScheduleID(),
									triggeredTx.getResponseRecord().getScheduleRef());

							Assert.assertTrue("Wrong transfer list!",
									transferListCheck(triggeredTx, asId("sender", spec), asId("receiver", spec),
											asId("payingAccount", spec), transferAmount));
						})
				);
	}

	public HapiApiSpec executionWithDefaultPayerButNoFundsFails() {
		long balance = 10_000_000L;
		long noBalance = 0L;
		long transferAmount = 1L;
		return defaultHapiSpec("ExecutionWithDefaultPayerButNoFundsFails")
				.given(
						cryptoCreate("payingAccount").balance(balance),
						cryptoCreate("luckyReceiver"),
						cryptoCreate("sender").balance(transferAmount),
						cryptoCreate("receiver").balance(noBalance),
						scheduleCreate(
								"basicXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", transferAmount)
								)
						)
								.payingWith("payingAccount")
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
						getAccountBalance("sender").hasTinyBars(transferAmount),
						getAccountBalance("receiver").hasTinyBars(noBalance),
						withOpContext((spec, opLog) -> {
							var triggeredTx = getTxnRecord("createTx").scheduled();

							allRunFor(spec, triggeredTx);

							Assert.assertEquals("Scheduled transaction should not be successful!",
									INSUFFICIENT_PAYER_BALANCE,
									triggeredTx.getResponseRecord().getReceipt().getStatus());
						})
				);
	}

	public HapiApiSpec executionWithCustomPayerButNoFundsFails() {
		long balance = 0L;
		long transferAmount = 1;
		return defaultHapiSpec("ExecutionWithCustomPayerButNoFundsFails")
				.given(
						cryptoCreate("payingAccount").balance(balance),
						cryptoCreate("sender"),
						cryptoCreate("receiver"),
						scheduleCreate(
								"basicXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", transferAmount)
								)
						)
								.designatingPayer("payingAccount")
								.via("createTx")
				).when(
						scheduleSign("basicXfer")
								.alsoSigningWith("sender", "payingAccount")
								.via("signTx")
								.hasKnownStatus(SUCCESS)
				).then(
						withOpContext((spec, opLog) -> {
							var triggeredTx = getTxnRecord("createTx").scheduled();

							allRunFor(spec, triggeredTx);

							Assert.assertEquals("Scheduled transaction should not be successful!",
									INSUFFICIENT_PAYER_BALANCE,
									triggeredTx.getResponseRecord().getReceipt().getStatus());
						})
				);
	}

	public HapiApiSpec executionWithCustomPayerWorks() {
		long transferAmount = 1;
		return defaultHapiSpec("ExecutionWithCustomPayerWorks")
				.given(
						cryptoCreate("payingAccount"),
						cryptoCreate("sender"),
						cryptoCreate("receiver"),
						scheduleCreate(
								"basicXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", transferAmount)
								)
						)
								.designatingPayer("payingAccount")
								.via("createTx")
				).when(
						scheduleSign("basicXfer")
								.alsoSigningWith("sender", "payingAccount")
								.via("signTx")
								.hasKnownStatus(SUCCESS)
				).then(
						withOpContext((spec, opLog) -> {
							var createTx = getTxnRecord("createTx");
							var signTx = getTxnRecord("signTx");
							var triggeredTx = getTxnRecord("createTx").scheduled();
							allRunFor(spec, createTx, signTx, triggeredTx);

							Assert.assertEquals("Scheduled transaction be successful!", SUCCESS,
									triggeredTx.getResponseRecord().getReceipt().getStatus());

							Assert.assertEquals("Wrong consensus timestamp!",
									signTx.getResponseRecord().getConsensusTimestamp().getNanos() + 1,
									triggeredTx.getResponseRecord().getConsensusTimestamp().getNanos());

							Assert.assertEquals("Wrong transaction valid start!",
									createTx.getResponseRecord().getTransactionID().getTransactionValidStart(),
									triggeredTx.getResponseRecord().getTransactionID().getTransactionValidStart());

							Assert.assertEquals("Wrong record account ID!",
									createTx.getResponseRecord().getTransactionID().getAccountID(),
									triggeredTx.getResponseRecord().getTransactionID().getAccountID());

							Assert.assertTrue("Transaction not scheduled!",
									triggeredTx.getResponseRecord().getTransactionID().getScheduled());

							Assert.assertEquals("Wrong schedule ID!",
									createTx.getResponseRecord().getReceipt().getScheduleID(),
									triggeredTx.getResponseRecord().getScheduleRef());

							Assert.assertTrue("Wrong transfer list!",
									transferListCheck(triggeredTx, asId("sender", spec), asId("receiver", spec),
											asId("payingAccount", spec), transferAmount));
						})
				);
	}

	private boolean transferListCheck(HapiGetTxnRecord triggered, AccountID givingAccountID,
			AccountID receivingAccountID, AccountID payingAccountID, Long amount) {
		AccountAmount givingAmount = AccountAmount.newBuilder()
				.setAccountID(givingAccountID)
				.setAmount(-amount)
				.build();

		AccountAmount receivingAmount = AccountAmount.newBuilder()
				.setAccountID(receivingAccountID)
				.setAmount(amount)
				.build();

		var accountAmountList = triggered.getResponseRecord()
				.getTransferList()
				.getAccountAmountsList();

		boolean payerHasPaid = accountAmountList.stream().anyMatch(
				a -> a.getAccountID().equals(payingAccountID) && a.getAmount() < 0);
		boolean amountHasBeenTransferred = accountAmountList.contains(givingAmount) &&
				accountAmountList.contains(receivingAmount);

		return amountHasBeenTransferred && payerHasPaid;
	}
}

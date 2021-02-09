package com.hedera.services.bdd.suites.schedule;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.recordFeeAmount;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
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
				suiteSetup(),
				executionWithDefaultPayerWorks(),
                executionWithCustomPayerWorks(),
                executionWithDefaultPayerButNoFundsFails(),
                executionWithCustomPayerButNoFundsFails(),
				suiteCleanup(),
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
								).signedBy()
						).inheritingScheduledSigs().payingWith("payingAccount").via("createTx")
				).when(
						scheduleSign("basicXfer")
								.withSignatories("sender")
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

							Assert.assertEquals("Wrong triggered transaction nonce!",
									ByteString.EMPTY,
									triggeredTx.getResponseRecord().getTransactionID().getNonce());

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
								).signedBy()
						).inheritingScheduledSigs()
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
								.withSignatories("sender")
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
								).signedBy()
						).inheritingScheduledSigs().designatingPayer("payingAccount").via("createTx")
				).when(
						scheduleSign("basicXfer")
								.withSignatories("sender", "payingAccount")
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
								).signedBy()
						).inheritingScheduledSigs()
								.designatingPayer("payingAccount")
								.via("createTx")
				).when(
						scheduleSign("basicXfer")
								.withSignatories("sender", "payingAccount")
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

							Assert.assertEquals("Wrong triggered transaction nonce!",
									ByteString.EMPTY,
									triggeredTx.getResponseRecord().getTransactionID().getNonce());

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
		boolean amountHasBeenTransfered = accountAmountList.contains(givingAmount) &&
				accountAmountList.contains(receivingAmount);

		return amountHasBeenTransfered && payerHasPaid;
	}
}

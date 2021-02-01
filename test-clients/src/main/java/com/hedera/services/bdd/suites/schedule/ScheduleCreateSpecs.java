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
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.spec.transactions.file.HapiFileUpdate;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreateNonsense;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.ensureIdempotentlyCreated;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyListNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SOME_SIGNATURES_WERE_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNPARSEABLE_SCHEDULED_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;
import static org.junit.Assert.assertNotEquals;

public class ScheduleCreateSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ScheduleCreateSpecs.class);
	private static final int SCHEDULE_EXPIRY_TIME_SECS = 10;
	private static final HapiFileUpdate updateScheduleExpiryTimeSecs = fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(
										Map.of("ledger.schedule.txExpiryTimeSecs", "" + SCHEDULE_EXPIRY_TIME_SECS));

	private static final HapiCryptoTransfer cryptoTransferTx =
			cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, GENESIS, 1));


	public static void main(String... args) {
		new ScheduleCreateSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				bodyOnlyCreation(),
				onlyBodyAndAdminCreation(),
				onlyBodyAndMemoCreation(),
				bodyAndPayerCreation(),
				allowsScheduledTransactionsWithDuplicatingBody(),
				allowsScheduledTransactionsWithDuplicatingBodyAndAdmin(),
				allowsScheduledTransactionsWithDuplicatingBodyAndPayer(),
				allowsScheduledTransactionsWithDuplicatingBodyPayerAndAdmin(),
				idempotentCreationWithBodyOnly(),
				idempotentCreationWithBodyAndPayer(),
				idempotentCreationWithBodyAndAdmin(),
				idempotentCreationWithBodyAndMemo(),
				idempotentCreationWhenAllPropsAreTheSame(),
				rejectsUnparseableTxn(),
				rejectsUnresolvableReqSigners(),
				triggersImmediatelyWithBothReqSimpleSigs(),
				onlySchedulesWithMissingReqSimpleSigs(),
				preservesRevocationServiceSemanticsForFileDelete(),
				detectsKeysChangedBetweenExpandSigsAndHandleTxn(),
				retestsActivationOnCreateWithEmptySigMap(),
		});
	}

	private HapiApiSpec bodyOnlyCreation() {
		return defaultHapiSpec("BodyOnlyCreation")
				.given(
						updateScheduleExpiryTimeSecs
				)
				.when(
						scheduleCreate( "onlyBody",
								cryptoTransferTx
						).logged()
				)
				.then(
						getScheduleInfo("onlyBody")
								.hasScheduleId("onlyBody")
								.hasValidTxBytes()
				);
	}

	private HapiApiSpec onlyBodyAndAdminCreation() {
		return defaultHapiSpec("OnlyBodyAndAdminCreation")
				.given(
						updateScheduleExpiryTimeSecs,
						newKeyNamed("admin")
				).when(
						scheduleCreate("onlyBodyAndAdminKey",
								cryptoTransferTx
						).adminKey("admin")
				).then(
						getScheduleInfo("onlyBodyAndAdminKey")
								.hasScheduleId("onlyBodyAndAdminKey")
								.hasAdminKey("admin")
								.hasValidTxBytes()
				);
	}

	private HapiApiSpec onlyBodyAndMemoCreation() {
		return defaultHapiSpec("OnlyBodyAndMemoCreation")
				.given(
						updateScheduleExpiryTimeSecs
				).when(
						scheduleCreate("onlyBodyAndMemo",
								cryptoTransferTx
						).withEntityMemo("sample memo")
				).then(
						getScheduleInfo("onlyBodyAndMemo")
								.hasScheduleId("onlyBodyAndMemo")
								.hasEntityMemo("sample memo")
								.hasValidTxBytes()
				);
	}

	private HapiApiSpec bodyAndPayerCreation() {
		return defaultHapiSpec("BodyAndPayerCreation")
				.given(
						updateScheduleExpiryTimeSecs,
						cryptoCreate("payer")
				).when(
						scheduleCreate("onlyBodyAndPayer",
								cryptoTransferTx
						).payer("payer")
				).then(
						getScheduleInfo("onlyBodyAndPayer")
								.hasScheduleId("onlyBodyAndPayer")
								.hasPayerAccountID("payer")
								.hasValidTxBytes()
				);
	}

	private HapiApiSpec allowsScheduledTransactionsWithDuplicatingBody() {
		return defaultHapiSpec("AllowsScheduledTransactionsWithDuplicatingBody")
				.given(
						updateScheduleExpiryTimeSecs,
						cryptoCreate("payer"),
						cryptoCreate("payer2"),
						newKeyNamed("admin"),
						newKeyNamed("admin2"),
						scheduleCreate("first", cryptoTransferTx)
								.adminKey("admin")
								.payer("payer")
								.via("first")
				)
				.when(
						scheduleCreate("second", cryptoTransferTx)
								.adminKey("admin2")
								.payer("payer2")
								.via("second")
				)
				.then(
						withOpContext((spec, opLog) -> {
							var firstTx = getTxnRecord("first");
							var secondTx = getTxnRecord("second");
							allRunFor(spec, firstTx, secondTx);
							assertNotEquals(
									firstTx.getResponseRecord().getReceipt().getScheduleID(),
									secondTx.getResponseRecord().getReceipt().getScheduleID());
								var timestampFirst = firstTx.getResponseRecord().getConsensusTimestamp().getSeconds();
								var timestampSecond = secondTx.getResponseRecord().getConsensusTimestamp().getSeconds();
								spec.registry().saveExpiry("first", timestampFirst + SCHEDULE_EXPIRY_TIME_SECS);
								spec.registry().saveExpiry("second", timestampSecond + SCHEDULE_EXPIRY_TIME_SECS);
						}),
						getScheduleInfo("first")
								.hasAdminKey("admin")
								.hasPayerAccountID("payer")
								.hasValidTxBytes()
								.hasValidExpirationTime()
						,
						getScheduleInfo("second")
								.hasAdminKey("admin2")
								.hasPayerAccountID("payer2")
								.hasValidTxBytes()
								.hasValidExpirationTime()
				);
	}

	private HapiApiSpec allowsScheduledTransactionsWithDuplicatingBodyAndAdmin() {
		return defaultHapiSpec("AllowsScheduledTransactionsWithDuplicatingBodyAndAdmin")
				.given(
						updateScheduleExpiryTimeSecs,
						cryptoCreate("payer"),
						cryptoCreate("payer2"),
						newKeyNamed("admin"),
						scheduleCreate("first", cryptoTransferTx)
								.adminKey("admin")
								.payer("payer")
								.via("first")
				).when(
						scheduleCreate("second", cryptoTransferTx)
								.adminKey("admin")
								.payer("payer2")
								.via("second")
				).then(
						withOpContext((spec, opLog) -> {
							var firstTx = getTxnRecord("first");
							var secondTx = getTxnRecord("second");
							allRunFor(spec, firstTx, secondTx);
							assertNotEquals(
									firstTx.getResponseRecord().getReceipt().getScheduleID(),
									secondTx.getResponseRecord().getReceipt().getScheduleID());
						}),
						getScheduleInfo("first")
								.hasAdminKey("admin")
								.hasPayerAccountID("payer"),
						getScheduleInfo("second")
								.hasAdminKey("admin")
								.hasPayerAccountID("payer2")
				);
	}

	private HapiApiSpec allowsScheduledTransactionsWithDuplicatingBodyAndPayer() {
		return defaultHapiSpec("AllowsScheduledTransactionsWithDuplicatingBodyAndPayer")
				.given(
						updateScheduleExpiryTimeSecs,
						cryptoCreate("payer"),
						newKeyNamed("admin"),
						newKeyNamed("admin2"),
						scheduleCreate("first", cryptoTransferTx)
								.adminKey("admin")
								.payer("payer")
								.via("first")
				).when(
						scheduleCreate("second", cryptoTransferTx)
								.adminKey("admin2")
								.payer("payer")
								.via("second")
				).then(
						withOpContext((spec, opLog) -> {
							var firstTx = getTxnRecord("first");
							var secondTx = getTxnRecord("second");
							allRunFor(spec, firstTx, secondTx);
							assertNotEquals(
									firstTx.getResponseRecord().getReceipt().getScheduleID(),
									secondTx.getResponseRecord().getReceipt().getScheduleID());
						}),
						getScheduleInfo("first")
								.hasAdminKey("admin")
								.hasPayerAccountID("payer"),
						getScheduleInfo("second")
								.hasAdminKey("admin2")
								.hasPayerAccountID("payer")
				);
	}

	private HapiApiSpec allowsScheduledTransactionsWithDuplicatingBodyPayerAndAdmin() {
		return defaultHapiSpec("AllowsScheduledTransactionsWithDuplicatingBodyPayerAndAdmin")
				.given(
						updateScheduleExpiryTimeSecs,
						cryptoCreate("payer"),
						newKeyNamed("admin"),
						scheduleCreate("first", cryptoTransferTx)
								.adminKey("admin")
								.payer("payer")
								.withEntityMemo("memo here")
								.via("first")
				).when(
						scheduleCreate("second", cryptoTransferTx)
								.adminKey("admin")
								.payer("payer")
								.withEntityMemo("different memo here")
								.via("second")
				).then(
						withOpContext((spec, opLog) -> {
							var firstTx = getTxnRecord("first");
							var secondTx = getTxnRecord("second");
							allRunFor(spec, firstTx, secondTx);
							assertNotEquals(
									firstTx.getResponseRecord().getReceipt().getScheduleID(),
									secondTx.getResponseRecord().getReceipt().getScheduleID());
						}),
						getScheduleInfo("first")
								.hasAdminKey("admin")
								.hasPayerAccountID("payer")
								.hasEntityMemo("memo here"),
						getScheduleInfo("second")
								.hasAdminKey("admin")
								.hasPayerAccountID("payer")
								.hasEntityMemo("different memo here")
				);
	}

	private HapiApiSpec idempotentCreationWithBodyOnly() {
		return defaultHapiSpec("IdempotentCreationWithBodyOnly")
				.given(
						updateScheduleExpiryTimeSecs,
						scheduleCreate("first", cryptoTransferTx)
								.via("first")
				).when(
						scheduleCreate("second", cryptoTransferTx)
								.via("second")
				).then(
						ensureIdempotentlyCreated("first", "second")
				);
	}

	private HapiApiSpec idempotentCreationWithBodyAndPayer() {
		return defaultHapiSpec("IdempotentCreationWithBodyAndPayer")
				.given(
						updateScheduleExpiryTimeSecs,
						cryptoCreate("payer"),
						scheduleCreate("first", cryptoTransferTx)
								.payer("payer")
								.via("first")
				).when(
						scheduleCreate("second", cryptoTransferTx)
								.payer("payer")
								.via("second")
				).then(
						ensureIdempotentlyCreated("first", "second")
				);
	}

	private HapiApiSpec idempotentCreationWithBodyAndAdmin() {
		return defaultHapiSpec("IdempotentCreationWithBodyAndAdmin")
				.given(
						updateScheduleExpiryTimeSecs,
						newKeyNamed("admin"),
						scheduleCreate("first", cryptoTransferTx)
								.adminKey("admin")
								.via("first")
				).when(
						scheduleCreate("second", cryptoTransferTx)
								.adminKey("admin")
								.via("second")
				).then(
						ensureIdempotentlyCreated("first", "second")
				);
	}

	private HapiApiSpec idempotentCreationWithBodyAndMemo() {
		return defaultHapiSpec("IdempotentCreationWithBodyAndMemo")
				.given(
						updateScheduleExpiryTimeSecs,
						scheduleCreate("first", cryptoTransferTx)
								.memo("memo here")
								.via("first")
				).when(
						scheduleCreate("second", cryptoTransferTx)
								.memo("memo here")
								.via("second")
				).then(
						ensureIdempotentlyCreated("first", "second")
				);
	}

	private HapiApiSpec idempotentCreationWhenAllPropsAreTheSame() {
		return defaultHapiSpec("IdempotentCreationWhenAllPropsAreTheSame")
				.given(
						updateScheduleExpiryTimeSecs,
						newKeyNamed("admin"),
						cryptoCreate("payer"),
						scheduleCreate("first", cryptoTransferTx)
								.payer("payer")
								.adminKey("admin")
								.withEntityMemo("memo here")
								.via("first")
				).when(
						scheduleCreate("second", cryptoTransferTx)
								.payer("payer")
								.adminKey("admin")
								.withEntityMemo("memo here")
								.via("second")
				).then(
						ensureIdempotentlyCreated("first", "second"),
						getScheduleInfo("first")
								.hasPayerAccountID("payer")
								.hasAdminKey("admin")
								.hasEntityMemo("memo here"),
						getScheduleInfo("second")
								.hasPayerAccountID("payer")
								.hasAdminKey("admin")
								.hasEntityMemo("memo here")
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
						updateScheduleExpiryTimeSecs,
						fileCreate(shouldBeInstaDeleted).waclShape(waclShape),
						fileCreate(shouldBeDeletedEventually).waclShape(waclShape)
				).when(
						scheduleCreate(
								"validRevocation",
								fileDelete(shouldBeInstaDeleted)
										.signedBy(shouldBeInstaDeleted)
										.sigControl(forKey(shouldBeInstaDeleted, adequateSigs))
						).inheritingScheduledSigs(),
						getFileInfo(shouldBeInstaDeleted).hasDeleted(true)
				).then(
						scheduleCreate(
								"notYetValidRevocation",
								fileDelete(shouldBeDeletedEventually)
										.signedBy(shouldBeDeletedEventually)
										.sigControl(forKey(shouldBeDeletedEventually, inadequateSigs))
						).inheritingScheduledSigs(),
						getFileInfo(shouldBeDeletedEventually).hasDeleted(false),
						scheduleCreate(
								"nowValidRevocation",
								fileDelete(shouldBeDeletedEventually)
										.signedBy(shouldBeDeletedEventually)
										.sigControl(forKey(shouldBeDeletedEventually, compensatorySigs))
						).inheritingScheduledSigs(),
						getFileInfo(shouldBeDeletedEventually).hasDeleted(true)
				);
	}

	public HapiApiSpec detectsKeysChangedBetweenExpandSigsAndHandleTxn() {
		KeyShape firstShape = listOf(3);
		KeyShape secondShape = threshOf(2, 4);

		return defaultHapiSpec("DetectsKeysChangedBetweenExpandSigsAndHandleTxn")
				.given(
						updateScheduleExpiryTimeSecs,
						newKeyNamed("a").shape(firstShape),
						newKeyNamed("b").shape(secondShape)
				).when(
						cryptoCreate("sender"),
						cryptoCreate("receiver")
								.key("a")
								.receiverSigRequired(true)
				).then(
						cryptoUpdate("receiver")
								.key("b")
								.deferStatusResolution(),
						scheduleCreate(
								"outdatedXferSigs",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", 1)
								).fee(ONE_HBAR).signedBy("sender", "a")
						).inheritingScheduledSigs()
								.hasKnownStatus(SOME_SIGNATURES_WERE_INVALID)
				);
	}

	public HapiApiSpec onlySchedulesWithMissingReqSimpleSigs() {
		return defaultHapiSpec("onlySchedulesWithMissingReqSimpleSigs")
				.given(
						updateScheduleExpiryTimeSecs,
						cryptoCreate("sender").balance(1L),
						cryptoCreate("receiver").balance(0L).receiverSigRequired(true)
				).when(
						scheduleCreate(
								"basicXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", 1)
								).signedBy("sender")
						).inheritingScheduledSigs()
				).then(
						getAccountBalance("sender").hasTinyBars(1L)
				);
	}

	public HapiApiSpec triggersImmediatelyWithBothReqSimpleSigs() {
		long initialBalance = HapiSpecSetup.getDefaultInstance().defaultBalance();
		long transferAmount = 1;

		return defaultHapiSpec("TriggersImmediatelyWithBothReqSimpleSigs")
				.given(
						updateScheduleExpiryTimeSecs,
						cryptoCreate("sender"),
						cryptoCreate("receiver").receiverSigRequired(true)
				).when(
						scheduleCreate(
								"basicXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", transferAmount)
								).signedBy("sender", "receiver")
						).inheritingScheduledSigs()
				).then(
						getAccountBalance("sender").hasTinyBars(initialBalance - transferAmount),
						getAccountBalance("receiver").hasTinyBars(initialBalance + transferAmount)
				);
	}

	public HapiApiSpec rejectsUnresolvableReqSigners() {
		return defaultHapiSpec("RejectsUnresolvableReqSigners")
				.given(
						updateScheduleExpiryTimeSecs
				).when().then(
						scheduleCreate(
								"xferWithImaginaryAccount",
								cryptoTransfer(
										tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1),
										tinyBarsFromTo("1.2.3", FUNDING, 1)
								).signedBy(DEFAULT_PAYER)
						).hasKnownStatus(UNRESOLVABLE_REQUIRED_SIGNERS)
				);
	}

	public HapiApiSpec rejectsUnparseableTxn() {
		return defaultHapiSpec("RejectsUnparseableTxn")
				.given(
						updateScheduleExpiryTimeSecs
				).when().then(
						scheduleCreateNonsense("absurd")
								.hasKnownStatus(UNPARSEABLE_SCHEDULED_TRANSACTION)
				);
	}

	public HapiApiSpec retestsActivationOnCreateWithEmptySigMap() {
		return defaultHapiSpec("RetestsActivationOnCreateWithEmptySigMap")
				.given(
						updateScheduleExpiryTimeSecs,
						newKeyNamed("a"),
						newKeyNamed("b"),
						newKeyListNamed("ab", List.of("a", "b"))
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
						scheduleCreate(
								"triggeredFall",
								cryptoTransfer(
										tinyBarsFromTo("sender", FUNDING, 1)
								).fee(ONE_HBAR).signedBy()
						),
						getAccountBalance("sender").hasTinyBars(666L)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

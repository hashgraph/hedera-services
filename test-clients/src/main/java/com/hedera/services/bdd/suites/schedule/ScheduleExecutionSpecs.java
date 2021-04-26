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
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUppercase;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.deleteTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.revokeTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.updateTopic;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.recordFeeAmount;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.schedule.ScheduleRecordSpecs.scheduledVersionOf;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CHUNK_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CHUNK_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MESSAGE_SIZE_TOO_LARGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_NEW_VALID_SIGNATURES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;

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
				executionTriggersWithWeirdlyRepeatedKey(),
				executionTriggersWithWeirdlyRepeatedKey(),
				executionTriggersOnceTopicHasSatisfiedSubmitKey(),
				scheduledSubmitThatWouldFailWithTopicDeletedCannotBeSigned(),
				scheduledSubmitFailedWithInvalidChunkNumberStillPaysServiceFeeButHasNoImpact(),
				scheduledSubmitFailedWithInvalidChunkTxnIdStillPaysServiceFeeButHasNoImpact(),
				scheduledSubmitThatWouldFailWithInvalidTopicIdCannotBeScheduled(),
				scheduledSubmitFailedWithMsgSizeTooLargeStillPaysServiceFeeButHasNoImpact(),
				scheduledXferFailingWithEmptyTokenTransferAccountAmountsPaysServiceFeeButNoImpact(),
				scheduledXferFailingWithRepeatedTokenIdPaysServiceFeeButNoImpact(),
				scheduledXferFailingWithNonNetZeroTokenTransferPaysServiceFeeButNoImpact(),
				scheduledXferFailingWithUnassociatedAccountTransferPaysServiceFeeButNoImpact(),
				scheduledXferFailingWithNonKycedAccountTransferPaysServiceFeeButNoImpact(),
				scheduledXferFailingWithFrozenAccountTransferPaysServiceFeeButNoImpact(),
				scheduledXferFailingWithDeletedTokenPaysServiceFeeButNoImpact(),
				scheduledXferFailingWithDeletedAccountPaysServiceFeeButNoImpact(),
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

	private HapiApiSpec scheduledXferFailingWithDeletedAccountPaysServiceFeeButNoImpact() {
		String xToken = "XXX";
		String validSchedule = "withLiveAccount";
		String invalidSchedule = "withDeletedAccount";
		String schedulePayer = "somebody", xTreasury = "xt", xCivilian = "xc", deadXCivilian = "deadxc";
		String successTxn = "good", failedTxn = "bad";
		AtomicReference<Map<AccountID, Long>> successFeesObs = new AtomicReference<>();
		AtomicReference<Map<AccountID, Long>> failureFeesObs = new AtomicReference<>();

		return defaultHapiSpec("ScheduledXferFailingWithDeletedTokenPaysServiceFeeButNoImpact")
				.given(
						cryptoCreate(schedulePayer),
						cryptoCreate(xTreasury),
						cryptoCreate(xCivilian),
						cryptoCreate(deadXCivilian),
						tokenCreate(xToken)
								.treasury(xTreasury)
								.initialSupply(101),
						tokenAssociate(xCivilian, xToken),
						tokenAssociate(deadXCivilian, xToken)
				).when(
						scheduleCreate(validSchedule,
								cryptoTransfer(
										moving(1, xToken).between(xTreasury, xCivilian)
								)
						)
								.via(successTxn)
								.alsoSigningWith(xTreasury, schedulePayer)
								.designatingPayer(schedulePayer),
						getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
						getAccountBalance(xCivilian).hasTokenBalance(xToken, 1),
						getTxnRecord(successTxn).scheduled()
								.logged()
								.revealingDebitsTo(successFeesObs::set),
						cryptoDelete(deadXCivilian),
						scheduleCreate(invalidSchedule,
								cryptoTransfer(
										moving(1, xToken).between(xTreasury, deadXCivilian)
								)
						)
								.via(failedTxn)
								.alsoSigningWith(xTreasury, schedulePayer)
								.designatingPayer(schedulePayer)
				).then(
						getTxnRecord(failedTxn).scheduled()
								.hasPriority(recordWith().status(ACCOUNT_DELETED))
								.revealingDebitsTo(failureFeesObs::set),
						getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
						assertionsHold((spec, opLog) ->
								assertBasicallyIdentical(successFeesObs.get(), failureFeesObs.get(), 1.0))
				);
	}

	private HapiApiSpec scheduledXferFailingWithDeletedTokenPaysServiceFeeButNoImpact() {
		String xToken = "XXX";
		String validSchedule = "withLiveToken";
		String invalidSchedule = "withDeletedToken";
		String schedulePayer = "somebody", xTreasury = "xt", xCivilian = "xc";
		String successTxn = "good", failedTxn = "bad";
		AtomicReference<Map<AccountID, Long>> successFeesObs = new AtomicReference<>();
		AtomicReference<Map<AccountID, Long>> failureFeesObs = new AtomicReference<>();

		return defaultHapiSpec("ScheduledXferFailingWithDeletedTokenPaysServiceFeeButNoImpact")
				.given(
						newKeyNamed("admin"),
						cryptoCreate(schedulePayer),
						cryptoCreate(xTreasury),
						cryptoCreate(xCivilian),
						tokenCreate(xToken)
								.treasury(xTreasury)
								.initialSupply(101)
								.adminKey("admin"),
						tokenAssociate(xCivilian, xToken)
				).when(
						scheduleCreate(validSchedule,
								cryptoTransfer(
										moving(1, xToken).between(xTreasury, xCivilian)
								)
										.memo(randomUppercase(100))
						)
								.via(successTxn)
								.alsoSigningWith(xTreasury, schedulePayer)
								.designatingPayer(schedulePayer),
						getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
						getAccountBalance(xCivilian).hasTokenBalance(xToken, 1),
						getTxnRecord(successTxn).scheduled()
								.logged()
								.revealingDebitsTo(successFeesObs::set),
						tokenDelete(xToken),
						scheduleCreate(invalidSchedule,
								cryptoTransfer(
										moving(1, xToken).between(xTreasury, xCivilian)
								)
										.memo(randomUppercase(100))
						)
								.via(failedTxn)
								.alsoSigningWith(xTreasury, schedulePayer)
								.designatingPayer(schedulePayer)
				).then(
						getTxnRecord(failedTxn).scheduled()
								.hasPriority(recordWith().status(TOKEN_WAS_DELETED))
								.revealingDebitsTo(failureFeesObs::set),
						getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
						assertionsHold((spec, opLog) ->
								assertBasicallyIdentical(successFeesObs.get(), failureFeesObs.get(), 1.0))
				);
	}

	private HapiApiSpec scheduledXferFailingWithFrozenAccountTransferPaysServiceFeeButNoImpact() {
		String xToken = "XXX";
		String validSchedule = "withUnfrozenAccount";
		String invalidSchedule = "withFrozenAccount";
		String schedulePayer = "somebody", xTreasury = "xt", xCivilian = "xc";
		String successTxn = "good", failedTxn = "bad";
		AtomicReference<Map<AccountID, Long>> successFeesObs = new AtomicReference<>();
		AtomicReference<Map<AccountID, Long>> failureFeesObs = new AtomicReference<>();

		return defaultHapiSpec("ScheduledXferFailingWithFrozenAccountTransferPaysServiceFeeButNoImpact")
				.given(
						newKeyNamed("freeze"),
						cryptoCreate(schedulePayer),
						cryptoCreate(xTreasury),
						cryptoCreate(xCivilian),
						tokenCreate(xToken)
								.treasury(xTreasury)
								.initialSupply(101)
								.freezeKey("freeze")
								.freezeDefault(true),
						tokenAssociate(xCivilian, xToken),
						tokenUnfreeze(xToken, xCivilian)
				).when(
						scheduleCreate(validSchedule,
								cryptoTransfer(
										moving(1, xToken).between(xTreasury, xCivilian)
								)
										.memo(randomUppercase(100))
						)
								.via(successTxn)
								.alsoSigningWith(xTreasury, schedulePayer)
								.designatingPayer(schedulePayer),
						getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
						getAccountBalance(xCivilian).hasTokenBalance(xToken, 1),
						getTxnRecord(successTxn).scheduled()
								.logged()
								.revealingDebitsTo(successFeesObs::set),
						tokenFreeze(xToken, xCivilian),
						scheduleCreate(invalidSchedule,
								cryptoTransfer(
										moving(1, xToken).between(xTreasury, xCivilian)
								)
										.memo(randomUppercase(100))
						)
								.via(failedTxn)
								.alsoSigningWith(xTreasury, schedulePayer)
								.designatingPayer(schedulePayer)
				).then(
						getTxnRecord(failedTxn).scheduled()
								.hasPriority(recordWith().status(ACCOUNT_FROZEN_FOR_TOKEN))
								.revealingDebitsTo(failureFeesObs::set),
						getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
						assertionsHold((spec, opLog) ->
								assertBasicallyIdentical(successFeesObs.get(), failureFeesObs.get(), 1.0))
				);
	}

	private HapiApiSpec scheduledXferFailingWithNonKycedAccountTransferPaysServiceFeeButNoImpact() {
		String xToken = "XXX";
		String validSchedule = "withKycedToken";
		String invalidSchedule = "withNonKycedToken";
		String schedulePayer = "somebody", xTreasury = "xt", xCivilian = "xc";
		String successTxn = "good", failedTxn = "bad";
		AtomicReference<Map<AccountID, Long>> successFeesObs = new AtomicReference<>();
		AtomicReference<Map<AccountID, Long>> failureFeesObs = new AtomicReference<>();

		return defaultHapiSpec("ScheduledXferFailingWithNonKycedAccountTransferPaysServiceFeeButNoImpact")
				.given(
						newKeyNamed("kyc"),
						cryptoCreate(schedulePayer),
						cryptoCreate(xTreasury),
						cryptoCreate(xCivilian),
						tokenCreate(xToken).treasury(xTreasury).initialSupply(101).kycKey("kyc"),
						tokenAssociate(xCivilian, xToken),
						grantTokenKyc(xToken, xCivilian)
				).when(
						scheduleCreate(validSchedule,
								cryptoTransfer(
										moving(1, xToken).between(xTreasury, xCivilian)
								)
										.memo(randomUppercase(100))
						)
								.via(successTxn)
								.alsoSigningWith(xTreasury, schedulePayer)
								.designatingPayer(schedulePayer),
						getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
						getAccountBalance(xCivilian).hasTokenBalance(xToken, 1),
						getTxnRecord(successTxn).scheduled()
								.logged()
								.revealingDebitsTo(successFeesObs::set),
						revokeTokenKyc(xToken, xCivilian),
						scheduleCreate(invalidSchedule,
								cryptoTransfer(
										moving(1, xToken).between(xTreasury, xCivilian)
								)
										.memo(randomUppercase(100))
						)
								.via(failedTxn)
								.alsoSigningWith(xTreasury, schedulePayer)
								.designatingPayer(schedulePayer)
				).then(
						getTxnRecord(failedTxn).scheduled()
								.hasPriority(recordWith().status(ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN))
								.revealingDebitsTo(failureFeesObs::set),
						getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
						assertionsHold((spec, opLog) ->
								assertBasicallyIdentical(successFeesObs.get(), failureFeesObs.get(), 1.0))
				);
	}

	private HapiApiSpec scheduledXferFailingWithUnassociatedAccountTransferPaysServiceFeeButNoImpact() {
		String xToken = "XXX";
		String validSchedule = "withAssociatedToken";
		String invalidSchedule = "withUnassociatedToken";
		String schedulePayer = "somebody", xTreasury = "xt", xCivilian = "xc", nonXCivilian = "nxc";
		String successTxn = "good", failedTxn = "bad";
		AtomicReference<Map<AccountID, Long>> successFeesObs = new AtomicReference<>();
		AtomicReference<Map<AccountID, Long>> failureFeesObs = new AtomicReference<>();

		return defaultHapiSpec("ScheduledXferFailingWithUnassociatedAccountTransferPaysServiceFeeButNoImpact")
				.given(
						cryptoCreate(schedulePayer),
						cryptoCreate(xTreasury),
						cryptoCreate(xCivilian),
						cryptoCreate(nonXCivilian),
						tokenCreate(xToken).treasury(xTreasury).initialSupply(101),
						tokenAssociate(xCivilian, xToken)
				).when(
						scheduleCreate(validSchedule,
								cryptoTransfer(
										moving(1, xToken).between(xTreasury, xCivilian)
								)
						)
								.via(successTxn)
								.alsoSigningWith(xTreasury, schedulePayer)
								.designatingPayer(schedulePayer),
						getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
						getAccountBalance(xCivilian).hasTokenBalance(xToken, 1),
						getTxnRecord(successTxn).scheduled()
								.logged()
								.revealingDebitsTo(successFeesObs::set),
						scheduleCreate(invalidSchedule,
								cryptoTransfer(
										moving(1, xToken).between(xTreasury, nonXCivilian)
								)
						)
								.via(failedTxn)
								.alsoSigningWith(xTreasury, schedulePayer)
								.designatingPayer(schedulePayer)
				).then(
						getTxnRecord(failedTxn).scheduled()
								.hasPriority(recordWith().status(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT))
								.revealingDebitsTo(failureFeesObs::set),
						getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
						getAccountInfo(nonXCivilian).hasNoTokenRelationship(xToken),
						assertionsHold((spec, opLog) ->
								assertBasicallyIdentical(successFeesObs.get(), failureFeesObs.get(), 1.0))
				);
	}

	private HapiApiSpec scheduledXferFailingWithNonNetZeroTokenTransferPaysServiceFeeButNoImpact() {
		String xToken = "XXX", yToken = "YYY";
		String validSchedule = "withZeroNetTokenChange";
		String invalidSchedule = "withNonZeroNetTokenChange";
		String schedulePayer = "somebody", xTreasury = "xt", xCivilian = "xc";
		String successTxn = "good", failedTxn = "bad";
		AtomicReference<Map<AccountID, Long>> successFeesObs = new AtomicReference<>();
		AtomicReference<Map<AccountID, Long>> failureFeesObs = new AtomicReference<>();

		return defaultHapiSpec("ScheduledXferFailingWithRepeatedTokenIdPaysServiceFeeButNoImpact")
				.given(
						cryptoCreate(schedulePayer),
						cryptoCreate(xTreasury),
						cryptoCreate(xCivilian),
						tokenCreate(xToken).treasury(xTreasury).initialSupply(101),
						tokenAssociate(xCivilian, xToken)
				).when(
						scheduleCreate(validSchedule,
								cryptoTransfer(
										moving(1, xToken).between(xTreasury, xCivilian)
								)
						)
								.via(successTxn)
								.alsoSigningWith(xTreasury, schedulePayer)
								.designatingPayer(schedulePayer),
						getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
						getAccountBalance(xCivilian).hasTokenBalance(xToken, 1),
						getTxnRecord(successTxn).scheduled()
								.logged()
								.revealingDebitsTo(successFeesObs::set),
						scheduleCreate(invalidSchedule,
								cryptoTransfer(
										moving(1, xToken).between(xTreasury, xCivilian)
								).breakingNetZeroInvariant()
						)
								.via(failedTxn)
								.alsoSigningWith(xTreasury, schedulePayer)
								.designatingPayer(schedulePayer)
				).then(
						getTxnRecord(failedTxn).scheduled()
								.hasPriority(recordWith().status(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN))
								.revealingDebitsTo(failureFeesObs::set),
						getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
						getAccountBalance(xCivilian).hasTokenBalance(xToken, 1),
						assertionsHold((spec, opLog) ->
								assertBasicallyIdentical(successFeesObs.get(), failureFeesObs.get(), 1.0))
				);
	}

	private HapiApiSpec scheduledXferFailingWithRepeatedTokenIdPaysServiceFeeButNoImpact() {
		String xToken = "XXX", yToken = "YYY";
		String validSchedule = "withNoRepeats";
		String invalidSchedule = "withRepeats";
		String schedulePayer = "somebody", xTreasury = "xt", yTreasury = "yt";
		String successTxn = "good", failedTxn = "bad";
		AtomicReference<Map<AccountID, Long>> successFeesObs = new AtomicReference<>();
		AtomicReference<Map<AccountID, Long>> failureFeesObs = new AtomicReference<>();

		return defaultHapiSpec("ScheduledXferFailingWithRepeatedTokenIdPaysServiceFeeButNoImpact")
				.given(
						cryptoCreate(schedulePayer),
						cryptoCreate(xTreasury),
						cryptoCreate(yTreasury),
						tokenCreate(xToken).treasury(xTreasury).initialSupply(101),
						tokenCreate(yToken).treasury(yTreasury).initialSupply(101),
						tokenAssociate(xTreasury, yToken),
						tokenAssociate(yTreasury, xToken)
				).when(
						scheduleCreate(validSchedule,
								cryptoTransfer(
										moving(1, xToken).between(xTreasury, yTreasury),
										moving(1, yToken).between(yTreasury, xTreasury)
								)
						)
								.via(successTxn)
								.alsoSigningWith(xTreasury, yTreasury, schedulePayer)
								.designatingPayer(schedulePayer),
						getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
						getAccountBalance(xTreasury).hasTokenBalance(yToken, 1),
						getAccountBalance(yTreasury).hasTokenBalance(yToken, 100),
						getAccountBalance(yTreasury).hasTokenBalance(xToken, 1),
						getTxnRecord(successTxn).scheduled()
								.logged()
								.revealingDebitsTo(successFeesObs::set),
						scheduleCreate(invalidSchedule,
								cryptoTransfer(
										moving(1, xToken).between(xTreasury, yTreasury)
								).appendingTokenFromTo(xToken, xTreasury, yTreasury, 1)
						)
								.via(failedTxn)
								.alsoSigningWith(xTreasury, schedulePayer)
								.designatingPayer(schedulePayer)
				).then(
						getTxnRecord(failedTxn).scheduled()
								.hasPriority(recordWith().status(TOKEN_ID_REPEATED_IN_TOKEN_LIST))
								.revealingDebitsTo(failureFeesObs::set),
						getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
						getAccountBalance(xTreasury).hasTokenBalance(yToken, 1),
						getAccountBalance(yTreasury).hasTokenBalance(yToken, 100),
						getAccountBalance(yTreasury).hasTokenBalance(xToken, 1),
						assertionsHold((spec, opLog) ->
								assertBasicallyIdentical(successFeesObs.get(), failureFeesObs.get(), 1.0))
				);
	}

	private HapiApiSpec scheduledXferFailingWithEmptyTokenTransferAccountAmountsPaysServiceFeeButNoImpact() {
		String xToken = "XXX", yToken = "YYY";
		String validSchedule = "withNonEmptyTransfers";
		String invalidSchedule = "withEmptyTransfer";
		String schedulePayer = "somebody", xTreasury = "xt", yTreasury = "yt", xyCivilian = "xyt";
		String successTxn = "good", failedTxn = "bad";
		AtomicReference<Map<AccountID, Long>> successFeesObs = new AtomicReference<>();
		AtomicReference<Map<AccountID, Long>> failureFeesObs = new AtomicReference<>();

		return defaultHapiSpec("ScheduledXferFailingWithEmptyTokenTransferAccountAmountsPaysServiceFeeButNoImpact")
				.given(
						cryptoCreate(schedulePayer),
						cryptoCreate(xTreasury),
						cryptoCreate(yTreasury),
						cryptoCreate(xyCivilian),
						tokenCreate(xToken).treasury(xTreasury).initialSupply(101),
						tokenCreate(yToken).treasury(yTreasury).initialSupply(101),
						tokenAssociate(xTreasury, yToken),
						tokenAssociate(yTreasury, xToken)
				).when(
						scheduleCreate(validSchedule,
								cryptoTransfer(
										moving(1, xToken).between(xTreasury, yTreasury),
										moving(1, yToken).between(yTreasury, xTreasury)
								)
						)
								.via(successTxn)
								.alsoSigningWith(xTreasury, yTreasury, schedulePayer)
								.designatingPayer(schedulePayer),
						getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
						getAccountBalance(xTreasury).hasTokenBalance(yToken, 1),
						getAccountBalance(yTreasury).hasTokenBalance(yToken, 100),
						getAccountBalance(yTreasury).hasTokenBalance(xToken, 1),
						getTxnRecord(successTxn).scheduled()
								.logged()
								.revealingDebitsTo(successFeesObs::set),
						scheduleCreate(invalidSchedule,
								cryptoTransfer(
										moving(2, xToken).distributing(xTreasury, yTreasury, xyCivilian)
								).withEmptyTokenTransfers(yToken)
						)
								.via(failedTxn)
								.alsoSigningWith(xTreasury, yTreasury, schedulePayer)
								.designatingPayer(schedulePayer)
				).then(
						getTxnRecord(failedTxn).scheduled()
								.hasPriority(recordWith().status(EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS))
								.revealingDebitsTo(failureFeesObs::set),
						getAccountBalance(xTreasury).hasTokenBalance(xToken, 100),
						getAccountBalance(xTreasury).hasTokenBalance(yToken, 1),
						getAccountBalance(yTreasury).hasTokenBalance(yToken, 100),
						getAccountBalance(yTreasury).hasTokenBalance(xToken, 1),
						assertionsHold((spec, opLog) ->
								assertBasicallyIdentical(successFeesObs.get(), failureFeesObs.get(), 1.0))
				);
	}

	private HapiApiSpec scheduledSubmitFailedWithMsgSizeTooLargeStillPaysServiceFeeButHasNoImpact() {
		String immutableTopic = "XXX";
		String validSchedule = "withValidSize";
		String invalidSchedule = "withInvalidSize";
		String schedulePayer = "somebody";
		String successTxn = "good", failedTxn = "bad";
		AtomicReference<Map<AccountID, Long>> successFeesObs = new AtomicReference<>();
		AtomicReference<Map<AccountID, Long>> failureFeesObs = new AtomicReference<>();
		var maxValidLen = HapiSpecSetup.getDefaultNodeProps().getInteger("consensus.message.maxBytesAllowed");

		return defaultHapiSpec("ScheduledSubmitFailedWithMsgSizeTooLargeStillPaysServiceFeeButHasNoImpact")
				.given(
						createTopic(immutableTopic),
						cryptoCreate(schedulePayer)
				).when(
						scheduleCreate(validSchedule,
								submitMessageTo(immutableTopic)
										.message(randomUppercase(maxValidLen))
						)
								.designatingPayer(schedulePayer)
								.via(successTxn)
								.signedBy(DEFAULT_PAYER, schedulePayer),
						getTopicInfo(immutableTopic).hasSeqNo(1L),
						getTxnRecord(successTxn).scheduled()
								.logged()
								.revealingDebitsTo(successFeesObs::set),
						scheduleCreate(invalidSchedule,
								submitMessageTo(immutableTopic)
										.message(randomUppercase(maxValidLen + 1))
						)
								.designatingPayer(schedulePayer)
								.via(failedTxn)
								.signedBy(DEFAULT_PAYER, schedulePayer)
				).then(
						getTopicInfo(immutableTopic).hasSeqNo(1L),
						getTxnRecord(failedTxn).scheduled()
								.hasPriority(recordWith().status(MESSAGE_SIZE_TOO_LARGE))
								.revealingDebitsTo(failureFeesObs::set),
						assertionsHold((spec, opLog) ->
								assertBasicallyIdentical(successFeesObs.get(), failureFeesObs.get(), 1.0))
				);
	}

	private HapiApiSpec scheduledSubmitFailedWithInvalidChunkTxnIdStillPaysServiceFeeButHasNoImpact() {
		String immutableTopic = "XXX";
		String validSchedule = "withValidChunkTxnId";
		String invalidSchedule = "withInvalidChunkTxnId";
		String schedulePayer = "somebody";
		String successTxn = "good", failedTxn = "bad";
		AtomicReference<Map<AccountID, Long>> successFeesObs = new AtomicReference<>();
		AtomicReference<Map<AccountID, Long>> failureFeesObs = new AtomicReference<>();
		AtomicReference<TransactionID> initialTxnId = new AtomicReference<>();
		AtomicReference<TransactionID> irrelevantTxnId = new AtomicReference<>();

		return defaultHapiSpec("ScheduledSubmitFailedWithInvalidChunkTxnIdStillPaysServiceFeeButHasNoImpact")
				.given(
						createTopic(immutableTopic),
						cryptoCreate(schedulePayer)
				).when(
						withOpContext((spec, opLog) -> {
							var subOp = usableTxnIdNamed(successTxn).payerId(schedulePayer);
							var secondSubOp = usableTxnIdNamed("wontWork").payerId(schedulePayer);
							allRunFor(spec, subOp, secondSubOp);
							initialTxnId.set(spec.registry().getTxnId(successTxn));
							irrelevantTxnId.set(spec.registry().getTxnId("wontWork"));
						}),
						sourcing(() -> scheduleCreate(validSchedule,
								submitMessageTo(immutableTopic)
										.chunkInfo(
												3,
												1,
												scheduledVersionOf(initialTxnId.get()))
								)
										.txnId(successTxn)
										.logged()
										.signedBy(schedulePayer)
						),
						getTopicInfo(immutableTopic).hasSeqNo(1L),
						getTxnRecord(successTxn).scheduled()
								.logged()
								.revealingDebitsTo(successFeesObs::set),
						sourcing(() -> scheduleCreate(invalidSchedule,
								submitMessageTo(immutableTopic)
										.chunkInfo(
												3,
												1,
												scheduledVersionOf(irrelevantTxnId.get()))
								)
										.designatingPayer(schedulePayer)
										.via(failedTxn)
										.logged()
										.signedBy(DEFAULT_PAYER, schedulePayer)
						)
				).then(
						getTopicInfo(immutableTopic).hasSeqNo(1L),
						getTxnRecord(failedTxn).scheduled()
								.hasPriority(recordWith().status(INVALID_CHUNK_TRANSACTION_ID))
								.revealingDebitsTo(failureFeesObs::set),
						assertionsHold((spec, opLog) -> Assert.assertEquals(successFeesObs.get(), failureFeesObs.get()))
				);
	}

	private HapiApiSpec scheduledSubmitFailedWithInvalidChunkNumberStillPaysServiceFeeButHasNoImpact() {
		String immutableTopic = "XXX";
		String validSchedule = "withValidChunkNumber";
		String invalidSchedule = "withInvalidChunkNumber";
		String schedulePayer = "somebody";
		String successTxn = "good", failedTxn = "bad";
		AtomicReference<Map<AccountID, Long>> successFeesObs = new AtomicReference<>();
		AtomicReference<Map<AccountID, Long>> failureFeesObs = new AtomicReference<>();
		AtomicReference<TransactionID> initialTxnId = new AtomicReference<>();

		return defaultHapiSpec("ScheduledSubmitFailedWithInvalidChunkNumberStillPaysServiceFeeButHasNoImpact")
				.given(
						createTopic(immutableTopic),
						cryptoCreate(schedulePayer)
				).when(
						withOpContext((spec, opLog) -> {
							var subOp = usableTxnIdNamed(successTxn).payerId(schedulePayer);
							allRunFor(spec, subOp);
							initialTxnId.set(spec.registry().getTxnId(successTxn));
						}),
						sourcing(() -> scheduleCreate(validSchedule,
								submitMessageTo(immutableTopic)
										.chunkInfo(
												3,
												1,
												scheduledVersionOf(initialTxnId.get()))
								)
										.txnId(successTxn)
										.logged()
										.signedBy(schedulePayer)
						),
						getTopicInfo(immutableTopic).hasSeqNo(1L),
						getTxnRecord(successTxn).scheduled()
								.logged()
								.revealingDebitsTo(successFeesObs::set),
						scheduleCreate(invalidSchedule,
								submitMessageTo(immutableTopic)
										.chunkInfo(3, 111, schedulePayer)
						)
								.via(failedTxn)
								.logged()
								.payingWith(schedulePayer)
				).then(
						getTopicInfo(immutableTopic).hasSeqNo(1L),
						getTxnRecord(failedTxn).scheduled()
								.hasPriority(recordWith().status(INVALID_CHUNK_NUMBER))
								.revealingDebitsTo(failureFeesObs::set),
						assertionsHold((spec, opLog) -> Assert.assertEquals(successFeesObs.get(), failureFeesObs.get()))
				);
	}

	private HapiApiSpec scheduledSubmitThatWouldFailWithInvalidTopicIdCannotBeScheduled() {
		String civilianPayer = "somebody";
		AtomicReference<Map<AccountID, Long>> successFeesObs = new AtomicReference<>();
		AtomicReference<Map<AccountID, Long>> failureFeesObs = new AtomicReference<>();

		return defaultHapiSpec("ScheduledSubmitThatWouldFailWithInvalidTopicIdCannotBeScheduled")
				.given(
						cryptoCreate(civilianPayer),
						createTopic("fascinating")
				).when(
						scheduleCreate("yup",
								submitMessageTo("fascinating")
										.message("Little did they care who danced between / " +
												"And little she by whom her dance was seen")
						)
								.payingWith(civilianPayer)
								.via("creation"),
						scheduleCreate("nope",
								submitMessageTo("1.2.3")
										.message("Little did they care who danced between / " +
												"And little she by whom her dance was seen")
						)
								.payingWith(civilianPayer)
								.via("nothingShouldBeCreated")
								.hasKnownStatus(UNRESOLVABLE_REQUIRED_SIGNERS)
				).then(
						getTxnRecord("creation")
								.revealingDebitsTo(successFeesObs::set),
						getTxnRecord("nothingShouldBeCreated")
								.revealingDebitsTo(failureFeesObs::set)
								.logged(),
						assertionsHold((spec, opLog) ->
								assertBasicallyIdentical(successFeesObs.get(), failureFeesObs.get(), 1.0))
				);
	}

	private void assertBasicallyIdentical(
			Map<AccountID, Long> aFees,
			Map<AccountID, Long> bFees,
			double allowedPercentDeviation
	) {
		Assert.assertEquals(aFees.keySet(), bFees.keySet());
		for (var id : aFees.keySet()) {
			long a = aFees.get(id);
			long b = bFees.get(id);
			Assert.assertEquals(100.0, (1.0 * a) / b * 100.0, allowedPercentDeviation);
		}
	}

	private HapiApiSpec scheduledSubmitThatWouldFailWithTopicDeletedCannotBeSigned() {
		String adminKey = "admin";
		String mutableTopic = "XXX";
		String postDeleteSchedule = "deferredTooLongSubmitMsg";
		String schedulePayer = "somebody";
		String failedTxn = "deleted";

		return defaultHapiSpec("ScheduledSubmitThatWouldFailWithTopicDeletedCannotBeSigned")
				.given(
						newKeyNamed(adminKey),
						createTopic(mutableTopic)
								.adminKeyName(adminKey),
						cryptoCreate(schedulePayer),
						scheduleCreate(postDeleteSchedule,
								submitMessageTo(mutableTopic)
										.message("Little did they care who danced between / " +
												"And little she by whom her dance was seen")
						)
								.designatingPayer(schedulePayer)
								.payingWith(DEFAULT_PAYER)
								.via(failedTxn)
				).when(
						deleteTopic(mutableTopic)
				).then(
						scheduleSign(postDeleteSchedule)
								.alsoSigningWith(schedulePayer)
								.hasKnownStatus(UNRESOLVABLE_REQUIRED_SIGNERS)
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

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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUppercase;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.deleteTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.invalidBurnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.invalidMintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.revokeTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.systemFileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uncheckedSubmit;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.updateTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromToWithInvalidAmounts;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeAbort;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.prepareUpgrade;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.recordFeeAmount;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.freeze.UpgradeSuite.poeticUpgradeLoc;
import static com.hedera.services.bdd.suites.freeze.UpgradeSuite.standardUpdateFile;
import static com.hedera.services.bdd.suites.schedule.ScheduleLongTermExecutionSpecs.withAndWithoutLongTermEnabled;
import static com.hedera.services.bdd.suites.schedule.ScheduleRecordSpecs.scheduledVersionOf;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.ThrottleDefsLoader.protoDefsFromResource;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CHUNK_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CHUNK_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_METADATA;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MESSAGE_SIZE_TOO_LARGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.METADATA_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_NEW_VALID_SIGNATURES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SOME_SIGNATURES_WERE_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;

public class ScheduleExecutionSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ScheduleExecutionSpecs.class);
	private static final String A_TOKEN = "token";
	public static byte[] ORIG_FILE = "SOMETHING".getBytes();

	/**
	 * This is ConsensusTimeTracker.MAX_PRECEDING_RECORDS_REMAINING_TXN + 1.
	 * It is not guaranteed to be this. If there are any following records generated by the txn then it could be
	 * different.
	 */
	private static final long normalTriggeredTxnTimestampOffset = 4;

	private static final String defaultMaxBatchSizeMint =
			HapiSpecSetup.getDefaultNodeProps().get("tokens.nfts.maxBatchSizeMint");

	String failingTxn = "failingTxn", successTxn = "successTxn", signTxn = "signTxn";

	public static void main(String... args) {
		new ScheduleExecutionSpecs().runSuiteAsync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return withAndWithoutLongTermEnabled((isLongTermEnabled) -> List.of(
			executionWithDefaultPayerWorks(),
			executionWithCustomPayerWorks(),
			executionWithCustomPayerWorksWithLastSigBeingCustomPayer(),
			executionWithCustomPayerWhoSignsAtCreationAsPayerWorks(),
			executionWithCustomPayerAndAdminKeyWorks(),
			executionWithDefaultPayerButNoFundsFails(),
			executionWithCustomPayerButNoFundsFails(),
			executionWithDefaultPayerButAccountDeletedFails(),
			executionWithCustomPayerButAccountDeletedFails(),
			executionWithInvalidAccountAmountsFails(),
			executionWithCryptoInsufficientAccountBalanceFails(),
			executionWithCryptoSenderDeletedFails(),
			executionWithTokenInsufficientAccountBalanceFails(),
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

			scheduledMintExecutesProperly(),
			scheduledUniqueMintExecutesProperly(),
			scheduledMintFailsWithoutSupplyKey(),
			scheduledUniqueMintFailsWithInvalidBatchSize(),
			scheduledUniqueMintFailsWithInvalidMetadata(),
			scheduledMintFailsWithInvalidAmount(),
			scheduledMintWithInvalidTokenThrowsUnresolvableSigners(),
			scheduledMintFailsWithInvalidTxBody(),

			scheduledBurnExecutesProperly(),
			scheduledUniqueBurnExecutesProperly(),
			scheduledUniqueBurnFailsWithInvalidBatchSize(),
			scheduledUniqueBurnFailsWithInvalidNftId(),
			scheduledBurnForUniqueFailsWithInvalidAmount(),
			scheduledBurnForUniqueFailsWithExistingAmount(),
			scheduledBurnFailsWithInvalidTxBody(),

			scheduledFreezeWorksAsExpected(),
			scheduledFreezeWithUnauthorizedPayerFails(isLongTermEnabled),
			scheduledPermissionedFileUpdateWorksAsExpected(),
			scheduledPermissionedFileUpdateUnauthorizedPayerFails(),
			scheduledSystemDeleteWorksAsExpected(),
			scheduledSystemDeleteUnauthorizedPayerFails(isLongTermEnabled),

			congestionPricingAffectsImmediateScheduleExecution()
		));
	}

	private HapiApiSpec scheduledBurnFailsWithInvalidTxBody() {
		return defaultHapiSpec("ScheduledBurnFailsWithInvalidTxBody")
				.given(
						cryptoCreate("treasury"),
						cryptoCreate("schedulePayer"),
						newKeyNamed("supplyKey"),
						tokenCreate(A_TOKEN)
								.supplyKey("supplyKey")
								.treasury("treasury")
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0),
						scheduleCreate("validSchedule", invalidBurnToken(
								A_TOKEN, List.of(1L, 2L), 123
						))
								.designatingPayer("schedulePayer")
								.via(failingTxn)
				)
				.when(
						scheduleSign("validSchedule")
								.alsoSigningWith("supplyKey", "schedulePayer", "treasury")
								.hasKnownStatus(SUCCESS)
				).then(
						getTxnRecord(failingTxn).scheduled()
								.hasPriority(recordWith().status(INVALID_TRANSACTION_BODY))
				);
	}

	private HapiApiSpec scheduledMintFailsWithInvalidTxBody() {
		return defaultHapiSpec("ScheduledMintFailsWithInvalidTxBody")
				.given(
						cryptoCreate("treasury"),
						cryptoCreate("schedulePayer"),
						newKeyNamed("supplyKey"),
						tokenCreate(A_TOKEN)
								.supplyKey("supplyKey")
								.treasury("treasury")
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0),
						scheduleCreate("validSchedule", invalidMintToken(
								A_TOKEN, List.of(ByteString.copyFromUtf8("m1")), 123
						))
								.designatingPayer("schedulePayer")
								.via(failingTxn)
				)
				.when(
						scheduleSign("validSchedule")
								.alsoSigningWith("supplyKey", "schedulePayer", "treasury")
								.hasKnownStatus(SUCCESS)
				).then(
						getTxnRecord(failingTxn).scheduled()
								.hasPriority(recordWith().status(INVALID_TRANSACTION_BODY)),
						getTokenInfo(A_TOKEN)
								.hasTotalSupply(0)
				);
	}

	private HapiApiSpec scheduledMintWithInvalidTokenThrowsUnresolvableSigners() {
		return defaultHapiSpec("ScheduledMintWithInvalidTokenThrowsUnresolvableSigners")
				.given(
						cryptoCreate("schedulePayer")
				)
				.when(
						scheduleCreate("validSchedule", mintToken(
								"0.0.123231", List.of(
										ByteString.copyFromUtf8("m1")
								)
						).fee(ONE_HBAR))
								.designatingPayer("schedulePayer")
								.hasKnownStatus(UNRESOLVABLE_REQUIRED_SIGNERS)
				).then();
	}

	private HapiApiSpec scheduledUniqueBurnFailsWithInvalidBatchSize() {
		String failingTxn = "failingTxn";
		return defaultHapiSpec("ScheduledUniqueBurnFailsWithInvalidBatchSize")
				.given(
						cryptoCreate("treasury"),
						cryptoCreate("schedulePayer"),
						newKeyNamed("supplyKey"),
						tokenCreate(A_TOKEN)
								.supplyKey("supplyKey")
								.treasury("treasury")
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0),
						mintToken(
								A_TOKEN, List.of(
										ByteString.copyFromUtf8("m1")
								)
						),
						scheduleCreate("validSchedule",
								burnToken(A_TOKEN, List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L)))
								.designatingPayer("schedulePayer")
								.via(failingTxn)
				)
				.when(
						getTokenInfo(A_TOKEN)
								.hasTotalSupply(1),
						scheduleSign("validSchedule")
								.alsoSigningWith("supplyKey", "schedulePayer", "treasury")
								.hasKnownStatus(SUCCESS)
				)
				.then(
						getTxnRecord(failingTxn).scheduled()
								.hasPriority(recordWith().status(BATCH_SIZE_LIMIT_EXCEEDED)),
						getTokenInfo(A_TOKEN)
								.hasTotalSupply(1)
				);
	}

	private HapiApiSpec scheduledUniqueBurnExecutesProperly() {
		return defaultHapiSpec("ScheduledUniqueBurnExecutesProperly")
				.given(
						cryptoCreate("treasury"),
						cryptoCreate("schedulePayer"),
						newKeyNamed("supplyKey"),
						tokenCreate(A_TOKEN)
								.supplyKey("supplyKey")
								.treasury("treasury")
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0),
						mintToken(A_TOKEN, List.of(ByteString.copyFromUtf8("metadata"))),
						scheduleCreate("validSchedule",
								burnToken(
										A_TOKEN, List.of(1L)
								)
						)
								.designatingPayer("schedulePayer")
								.via(successTxn)
				)
				.when(
						getTokenInfo(A_TOKEN)
								.hasTotalSupply(1),
						scheduleSign("validSchedule")
								.alsoSigningWith("supplyKey", "schedulePayer", "treasury")
								.via(signTxn)
								.hasKnownStatus(SUCCESS)
				)
				.then(
						withOpContext((spec, opLog) -> {
							var createTx = getTxnRecord(successTxn);
							var signTx = getTxnRecord(signTxn);
							var triggeredTx = getTxnRecord(successTxn).scheduled();
							allRunFor(spec, createTx, signTx, triggeredTx);

							Assertions.assertEquals(
									signTx.getResponseRecord().getConsensusTimestamp().getNanos() + normalTriggeredTxnTimestampOffset,
									triggeredTx.getResponseRecord().getConsensusTimestamp().getNanos(),
									"Wrong consensus timestamp!");

							Assertions.assertEquals(
									createTx.getResponseRecord().getTransactionID().getTransactionValidStart(),
									triggeredTx.getResponseRecord().getTransactionID().getTransactionValidStart(),
									"Wrong transaction valid start!");

							Assertions.assertEquals(createTx.getResponseRecord().getTransactionID().getAccountID(),
									triggeredTx.getResponseRecord().getTransactionID().getAccountID(),
									"Wrong record account ID!");

							Assertions.assertTrue(triggeredTx.getResponseRecord().getTransactionID().getScheduled(),
									"Transaction not scheduled!");

							Assertions.assertEquals(createTx.getResponseRecord().getReceipt().getScheduleID(),
									triggeredTx.getResponseRecord().getScheduleRef(),
									"Wrong schedule ID!");
						}),
						getTokenInfo(A_TOKEN)
								.hasTotalSupply(0)
				);
	}

	private HapiApiSpec scheduledUniqueMintFailsWithInvalidMetadata() {
		String failingTxn = "failingTxn";
		return defaultHapiSpec("ScheduledUniqueMintFailsWithInvalidMetadata")
				.given(
						cryptoCreate("payer"),
						cryptoCreate("treasury"),
						cryptoCreate("schedulePayer"),
						newKeyNamed("supplyKey"),
						tokenCreate(A_TOKEN)
								.supplyKey("supplyKey")
								.treasury("treasury")
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0),
						scheduleCreate("validSchedule",
								mintToken(
										A_TOKEN, List.of(metadataOfLength(101))
								)
						)
								.designatingPayer("schedulePayer")
								.payingWith("payer")
								.via(failingTxn)
				)
				.when(
						scheduleSign("validSchedule")
								.alsoSigningWith("supplyKey", "schedulePayer", "treasury")
								.hasKnownStatus(SUCCESS)
				)
				.then(
						getTxnRecord(failingTxn).scheduled()
								.hasPriority(recordWith().status(METADATA_TOO_LONG)),
						getTokenInfo(A_TOKEN)
								.hasTotalSupply(0)
				);
	}

	private HapiApiSpec scheduledUniqueBurnFailsWithInvalidNftId() {
		String failingTxn = "failingTxn";
		return defaultHapiSpec("ScheduledUniqueBurnFailsWithInvalidNftId")
				.given(
						cryptoCreate("treasury"),
						cryptoCreate("schedulePayer"),
						newKeyNamed("supplyKey"),
						tokenCreate(A_TOKEN)
								.supplyKey("supplyKey")
								.treasury("treasury")
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0),
						scheduleCreate("validSchedule",
								burnToken(
										A_TOKEN, List.of(123L)
								)
						)
								.designatingPayer("schedulePayer")
								.via(failingTxn)
				)
				.when(
						scheduleSign("validSchedule")
								.alsoSigningWith("supplyKey", "schedulePayer", "treasury")
								.hasKnownStatus(SUCCESS)
				)
				.then(
						getTxnRecord(failingTxn).scheduled()
								.hasPriority(recordWith().status(INVALID_NFT_ID))
				);
	}

	private HapiApiSpec scheduledBurnForUniqueFailsWithExistingAmount() {
		String failingTxn = "failingTxn";
		return defaultHapiSpec("ScheduledBurnForUniqueFailsWithExistingAmount")
				.given(
						cryptoCreate("treasury"),
						cryptoCreate("schedulePayer"),
						newKeyNamed("supplyKey"),
						tokenCreate(A_TOKEN)
								.supplyKey("supplyKey")
								.treasury("treasury")
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0),
						scheduleCreate("validSchedule",
								burnToken(
										A_TOKEN, 123L
								)
						)
								.designatingPayer("schedulePayer")
								.via(failingTxn)
				)
				.when(
						scheduleSign("validSchedule")
								.alsoSigningWith("supplyKey", "schedulePayer", "treasury")
								.hasKnownStatus(SUCCESS)
				)
				.then(
						getTxnRecord(failingTxn).scheduled()
								.hasPriority(recordWith().status(INVALID_TOKEN_BURN_METADATA)),
						getTokenInfo(A_TOKEN).hasTotalSupply(0)
				);
	}

	private HapiApiSpec scheduledBurnForUniqueFailsWithInvalidAmount() {
		String failingTxn = "failingTxn";
		return defaultHapiSpec("ScheduledBurnForUniqueFailsWithInvalidAmount")
				.given(
						cryptoCreate("treasury"),
						cryptoCreate("schedulePayer"),
						newKeyNamed("supplyKey"),
						tokenCreate(A_TOKEN)
								.supplyKey("supplyKey")
								.treasury("treasury")
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0),
						scheduleCreate("validSchedule",
								burnToken(
										A_TOKEN, -123L
								)
						)
								.designatingPayer("schedulePayer")
								.via(failingTxn)
				)
				.when(
						scheduleSign("validSchedule")
								.alsoSigningWith("supplyKey", "schedulePayer", "treasury")
								.hasKnownStatus(SUCCESS)
				)
				.then(
						getTxnRecord(failingTxn).scheduled()
								.hasPriority(recordWith().status(INVALID_TOKEN_BURN_AMOUNT)),
						getTokenInfo(A_TOKEN).hasTotalSupply(0)
				);
	}

	private ByteString metadataOfLength(int length) {
		return ByteString.copyFrom(genRandomBytes(length));
	}

	private byte[] genRandomBytes(int numBytes) {
		byte[] contents = new byte[numBytes];
		(new Random()).nextBytes(contents);
		return contents;
	}

	private HapiApiSpec scheduledUniqueMintFailsWithInvalidBatchSize() {
		String failingTxn = "failingTxn";
		return defaultHapiSpec("ScheduledUniqueMintFailsWithInvalidBatchSize")
				.given(
						overriding("tokens.nfts.maxBatchSizeMint", "5"),
						cryptoCreate("treasury"),
						cryptoCreate("schedulePayer"),
						newKeyNamed("supplyKey"),
						tokenCreate(A_TOKEN)
								.supplyKey("supplyKey")
								.treasury("treasury")
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0),
						scheduleCreate("validSchedule",
								mintToken(
										A_TOKEN, List.of(
												ByteString.copyFromUtf8("m1"),
												ByteString.copyFromUtf8("m2"),
												ByteString.copyFromUtf8("m3"),
												ByteString.copyFromUtf8("m4"),
												ByteString.copyFromUtf8("m5"),
												ByteString.copyFromUtf8("m6")
										)
								)
						)
								.designatingPayer("schedulePayer")
								.via(failingTxn)
				)
				.when(
						scheduleSign("validSchedule")
								.alsoSigningWith("supplyKey", "schedulePayer", "treasury")
								.hasKnownStatus(SUCCESS)
				)
				.then(
						getTxnRecord(failingTxn).scheduled()
								.hasPriority(recordWith().status(BATCH_SIZE_LIMIT_EXCEEDED)),
						getTokenInfo(A_TOKEN)
								.hasTotalSupply(0),
						overriding("tokens.nfts.maxBatchSizeMint", defaultMaxBatchSizeMint)
				);
	}

	private HapiApiSpec scheduledMintFailsWithInvalidAmount() {
		String failingTxn = "failingTxn";
		return defaultHapiSpec("ScheduledMintFailsWithInvalidAmount")
				.given(
						cryptoCreate("treasury"),
						cryptoCreate("schedulePayer"),
						newKeyNamed("supplyKey"),
						tokenCreate(A_TOKEN)
								.supplyKey("supplyKey")
								.treasury("treasury")
								.initialSupply(101),
						scheduleCreate("validSchedule",
								mintToken(
										A_TOKEN, 0
								)
						)
								.designatingPayer("schedulePayer")
								.via(failingTxn)
				)
				.when(
						scheduleSign("validSchedule")
								.alsoSigningWith("supplyKey", "schedulePayer", "treasury")
								.hasKnownStatus(SUCCESS)
				)
				.then(
						getTxnRecord(failingTxn).scheduled()
								.hasPriority(recordWith().status(INVALID_TOKEN_MINT_AMOUNT)),
						getTokenInfo(A_TOKEN)
								.hasTotalSupply(101)
				);
	}


	private HapiApiSpec scheduledUniqueMintExecutesProperly() {
		String successTxn = "successTxn", signTxn = "signTxn";
		return defaultHapiSpec("ScheduledUniqueMintExecutesProperly")
				.given(
						cryptoCreate("treasury"),
						cryptoCreate("schedulePayer"),
						newKeyNamed("supplyKey"),
						tokenCreate(A_TOKEN)
								.supplyKey("supplyKey")
								.treasury("treasury")
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0),
						scheduleCreate("validSchedule",
								mintToken(
										A_TOKEN, List.of(ByteString.copyFromUtf8("somemetadata1"),
												ByteString.copyFromUtf8("somemetadata2"))
								)
						)
								.designatingPayer("schedulePayer")
								.via(successTxn)
				)
				.when(
						scheduleSign("validSchedule")
								.alsoSigningWith("supplyKey", "schedulePayer", "treasury")
								.via(signTxn)
								.hasKnownStatus(SUCCESS)
				)
				.then(
						withOpContext((spec, opLog) -> {
							var createTx = getTxnRecord(successTxn);
							var signTx = getTxnRecord(signTxn);
							var triggeredTx = getTxnRecord(successTxn).scheduled();
							allRunFor(spec, createTx, signTx, triggeredTx);

							Assertions.assertEquals(
									signTx.getResponseRecord().getConsensusTimestamp().getNanos() + normalTriggeredTxnTimestampOffset,
									triggeredTx.getResponseRecord().getConsensusTimestamp().getNanos(),
									"Wrong consensus timestamp!");

							Assertions.assertEquals(
									createTx.getResponseRecord().getTransactionID().getTransactionValidStart(),
									triggeredTx.getResponseRecord().getTransactionID().getTransactionValidStart(),
									"Wrong transaction valid start!");

							Assertions.assertEquals(createTx.getResponseRecord().getTransactionID().getAccountID(),
									triggeredTx.getResponseRecord().getTransactionID().getAccountID(),
									"Wrong record account ID!");

							Assertions.assertTrue(triggeredTx.getResponseRecord().getTransactionID().getScheduled(),
									"Transaction not scheduled!");

							Assertions.assertEquals(createTx.getResponseRecord().getReceipt().getScheduleID(),
									triggeredTx.getResponseRecord().getScheduleRef(),
									"Wrong schedule ID!");
						}),
						getTokenInfo(A_TOKEN)
								.hasTotalSupply(2)
				);
	}

	private HapiApiSpec scheduledMintExecutesProperly() {
		String successTxn = "successTxn", signTxn = "signTxn";
		return defaultHapiSpec("ScheduledMintExecutesProperly")
				.given(
						cryptoCreate("treasury"),
						cryptoCreate("schedulePayer"),
						newKeyNamed("supplyKey"),
						tokenCreate(A_TOKEN)
								.supplyKey("supplyKey")
								.treasury("treasury")
								.initialSupply(101),
						scheduleCreate("validSchedule",
								mintToken(
										A_TOKEN, 10
								)
						)
								.designatingPayer("schedulePayer")
								.via(successTxn)
				)
				.when(
						scheduleSign("validSchedule")
								.alsoSigningWith("supplyKey", "schedulePayer", "treasury")
								.via(signTxn)
								.hasKnownStatus(SUCCESS)
				)
				.then(
						withOpContext((spec, opLog) -> {
							var createTx = getTxnRecord(successTxn);
							var signTx = getTxnRecord(signTxn);
							var triggeredTx = getTxnRecord(successTxn).scheduled();
							allRunFor(spec, createTx, signTx, triggeredTx);

							Assertions.assertEquals(
									signTx.getResponseRecord().getConsensusTimestamp().getNanos() + normalTriggeredTxnTimestampOffset,
									triggeredTx.getResponseRecord().getConsensusTimestamp().getNanos(),
									"Wrong consensus timestamp!");

							Assertions.assertEquals(
									createTx.getResponseRecord().getTransactionID().getTransactionValidStart(),
									triggeredTx.getResponseRecord().getTransactionID().getTransactionValidStart(),
									"Wrong transaction valid start!");

							Assertions.assertEquals(createTx.getResponseRecord().getTransactionID().getAccountID(),
									triggeredTx.getResponseRecord().getTransactionID().getAccountID(),
									"Wrong record account ID!");

							Assertions.assertTrue(triggeredTx.getResponseRecord().getTransactionID().getScheduled(),
									"Transaction not scheduled!");

							Assertions.assertEquals(createTx.getResponseRecord().getReceipt().getScheduleID(),
									triggeredTx.getResponseRecord().getScheduleRef(),
									"Wrong schedule ID!");
						}),
						getTokenInfo(A_TOKEN)
								.hasTotalSupply(111)
				);
	}

	private HapiApiSpec scheduledBurnExecutesProperly() {
		String successTxn = "successTxn", signTxn = "signTxn";
		return defaultHapiSpec("ScheduledBurnExecutesProperly")
				.given(
						cryptoCreate("treasury"),
						cryptoCreate("schedulePayer"),
						newKeyNamed("supplyKey"),
						tokenCreate(A_TOKEN)
								.supplyKey("supplyKey")
								.treasury("treasury")
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(101),
						scheduleCreate("validSchedule",
								burnToken(
										A_TOKEN, 10
								)
						)
								.designatingPayer("schedulePayer")
								.via(successTxn)
				)
				.when(
						scheduleSign("validSchedule")
								.alsoSigningWith("supplyKey", "schedulePayer", "treasury")
								.via(signTxn)
								.hasKnownStatus(SUCCESS)
				)
				.then(
						withOpContext((spec, opLog) -> {
							var createTx = getTxnRecord(successTxn);
							var signTx = getTxnRecord(signTxn);
							var triggeredTx = getTxnRecord(successTxn).scheduled();
							allRunFor(spec, createTx, signTx, triggeredTx);

							Assertions.assertEquals(
									signTx.getResponseRecord().getConsensusTimestamp().getNanos() + normalTriggeredTxnTimestampOffset,
									triggeredTx.getResponseRecord().getConsensusTimestamp().getNanos(),
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
						}),
						getTokenInfo(A_TOKEN)
								.hasTotalSupply(91)
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
						assertionsHold(
								(spec, opLog) -> Assertions.assertEquals(successFeesObs.get(), failureFeesObs.get()))
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
						assertionsHold(
								(spec, opLog) -> Assertions.assertEquals(successFeesObs.get(), failureFeesObs.get()))
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
		Assertions.assertEquals(aFees.keySet(), bFees.keySet());
		for (var id : aFees.keySet()) {
			long a = aFees.get(id);
			long b = bFees.get(id);
			Assertions.assertEquals(100.0, (1.0 * a) / b * 100.0, allowedPercentDeviation);
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
								/* In the rare, but possible, case that the adminKey and submitKey keys overlap
								 * in their first byte (and that byte is not shared by the DEFAULT_PAYER),
								 * we will get SOME_SIGNATURES_WERE_INVALID instead of NO_NEW_VALID_SIGNATURES.
								 *
								 * So we need this to stabilize CI. But if just testing locally, you may
								 * only use .hasKnownStatus(NO_NEW_VALID_SIGNATURES) and it will pass
								 * >99.99% of the time. */
								.hasKnownStatusFrom(NO_NEW_VALID_SIGNATURES, SOME_SIGNATURES_WERE_INVALID),
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

							Assertions.assertEquals(
									signTx.getResponseRecord().getConsensusTimestamp().getNanos() + normalTriggeredTxnTimestampOffset,
									triggeredTx.getResponseRecord().getConsensusTimestamp().getNanos(),
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

							Assertions.assertEquals(
									INSUFFICIENT_PAYER_BALANCE,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction should not be successful!");
						})
				);
	}

	public HapiApiSpec executionWithCustomPayerWorksWithLastSigBeingCustomPayer() {
		long noBalance = 0L;
		long transferAmount = 1;
		return defaultHapiSpec("ExecutionWithCustomPayerWorksWithLastSigBeingCustomPayer")
				.given(
						cryptoCreate("payingAccount"),
						cryptoCreate("sender").balance(transferAmount),
						cryptoCreate("receiver").balance(noBalance),
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
								.alsoSigningWith("sender")
								.via("signTx")
								.hasKnownStatus(SUCCESS)
				).then(
						getAccountBalance("sender").hasTinyBars(transferAmount),
						getAccountBalance("receiver").hasTinyBars(noBalance),
						scheduleSign("basicXfer")
								.alsoSigningWith("payingAccount")
								.via("signTx")
								.hasKnownStatus(SUCCESS),
						withOpContext((spec, opLog) -> {
							var triggeredTx = getTxnRecord("createTx").scheduled();

							allRunFor(spec, triggeredTx);

							Assertions.assertEquals(
									SUCCESS,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction should not be successful!");
						}),
						getAccountBalance("sender").hasTinyBars(noBalance),
						getAccountBalance("receiver").hasTinyBars(transferAmount)
				);
	}

	public HapiApiSpec executionWithCustomPayerButNoFundsFails() {
		long balance = 0L;
		long noBalance = 0L;
		long transferAmount = 1;
		return defaultHapiSpec("ExecutionWithCustomPayerButNoFundsFails")
				.given(
						cryptoCreate("payingAccount").balance(balance),
						cryptoCreate("sender").balance(transferAmount),
						cryptoCreate("receiver").balance(noBalance),
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


	public HapiApiSpec executionWithDefaultPayerButAccountDeletedFails() {
		long balance = 10_000_000L;
		long noBalance = 0L;
		long transferAmount = 1L;
		return defaultHapiSpec("ExecutionWithDefaultPayerButAccountDeletedFails")
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
						cryptoDelete("payingAccount"),
						scheduleSign("basicXfer")
								.alsoSigningWith("sender")
								.hasKnownStatus(SUCCESS)
				).then(
						getAccountBalance("sender").hasTinyBars(transferAmount),
						getAccountBalance("receiver").hasTinyBars(noBalance),
						getScheduleInfo("basicXfer")
								.isExecuted(),
						getTxnRecord("createTx").scheduled()
									.hasCostAnswerPrecheck(ACCOUNT_DELETED)
				);
	}

	public HapiApiSpec executionWithCustomPayerButAccountDeletedFails() {
		long balance = 10_000_000L;
		long noBalance = 0L;
		long transferAmount = 1;
		return defaultHapiSpec("ExecutionWithCustomPayerButAccountDeletedFails")
				.given(
						cryptoCreate("payingAccount").balance(balance),
						cryptoCreate("sender").balance(transferAmount),
						cryptoCreate("receiver").balance(noBalance),
						scheduleCreate(
								"basicXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", transferAmount)
								)
						)
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
								.isExecuted(),
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

	public HapiApiSpec executionWithCryptoInsufficientAccountBalanceFails() {
		long noBalance = 0L;
		long senderBalance = 100L;
		long transferAmount = 101L;
		long payerBalance = 1_000_000L;
		return defaultHapiSpec("ExecutionWithCryptoInsufficientAccountBalanceFails")
				.given(
						cryptoCreate("payingAccount").balance(payerBalance),
						cryptoCreate("sender").balance(senderBalance),
						cryptoCreate("receiver").balance(noBalance),
						scheduleCreate(
								"failedXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", transferAmount)
								)
						)
								.designatingPayer("payingAccount")
								.via("createTx")
				).when(
						scheduleSign("failedXfer")
								.alsoSigningWith("sender", "payingAccount")
								.via("signTx")
								.hasKnownStatus(SUCCESS)
				).then(
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
		return defaultHapiSpec("ExecutionWithCryptoSenderDeletedFails")
				.given(
						cryptoCreate("payingAccount").balance(payerBalance),
						cryptoCreate("sender").balance(senderBalance),
						cryptoCreate("receiver").balance(noBalance),
						scheduleCreate(
								"failedXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", transferAmount)
								)
						)
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
								.isExecuted(),
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

	public HapiApiSpec executionWithTokenInsufficientAccountBalanceFails() {
		String xToken = "XXX";
		String invalidSchedule = "withInsufficientTokenTransfer";
		String schedulePayer = "somebody", xTreasury = "xt", civilian = "xa";
		String failedTxn = "bad";
		return defaultHapiSpec("ExecutionWithTokenInsufficientAccountBalanceFails")
				.given(
						newKeyNamed("admin"),
						cryptoCreate(schedulePayer),
						cryptoCreate(xTreasury),
						cryptoCreate(civilian),
						tokenCreate(xToken)
								.treasury(xTreasury)
								.initialSupply(100)
								.adminKey("admin"),
						tokenAssociate(civilian, xToken)
				).when(
						scheduleCreate(invalidSchedule,
								cryptoTransfer(
										moving(101, xToken).between(xTreasury, civilian)
								)
										.memo(randomUppercase(100))
						)
								.via(failedTxn)
								.alsoSigningWith(xTreasury, schedulePayer)
								.designatingPayer(schedulePayer)
				).then(
						getTxnRecord(failedTxn).scheduled()
								.hasPriority(recordWith().status(INSUFFICIENT_TOKEN_BALANCE)),
						getAccountBalance(xTreasury).hasTokenBalance(xToken, 100)
				);
	}

	public HapiApiSpec executionWithInvalidAccountAmountsFails() {
		long transferAmount = 100;
		long senderBalance = 1000L;
		long payingAccountBalance = 1_000_000L;
		long noBalance = 0L;
		return defaultHapiSpec("ExecutionWithInvalidAccountAmountsFails")
				.given(
						cryptoCreate("payingAccount").balance(payingAccountBalance),
						cryptoCreate("sender").balance(senderBalance),
						cryptoCreate("receiver").balance(noBalance),
						scheduleCreate(
								"failedXfer",
								cryptoTransfer(
										tinyBarsFromToWithInvalidAmounts("sender", "receiver", transferAmount)
								)
						)
								.designatingPayer("payingAccount")
								.via("createTx")
				)
				.when(
						scheduleSign("failedXfer")
								.alsoSigningWith("sender", "payingAccount")
								.via("signTx")
								.hasKnownStatus(SUCCESS)
				)
				.then(
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


	private HapiApiSpec scheduledMintFailsWithoutSupplyKey() {
		return defaultHapiSpec("ScheduledMintFailsWithoutSupplyKey")
				.given(
						cryptoCreate("treasury"),
						cryptoCreate("schedulePayer"),
						tokenCreate(A_TOKEN)
								.treasury("treasury")
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0),
						scheduleCreate("validSchedule", mintToken(A_TOKEN,
								List.of(ByteString.copyFromUtf8("metadata"))))
								.designatingPayer("schedulePayer")
								.via(failingTxn)
				)
				.when(
						scheduleSign("validSchedule")
								.alsoSigningWith("schedulePayer", "treasury")
								.hasKnownStatus(SUCCESS)
				).then(
						getTxnRecord(failingTxn).scheduled()
								.hasPriority(recordWith().status(TOKEN_HAS_NO_SUPPLY_KEY)),
						getTokenInfo(A_TOKEN)
								.hasTotalSupply(0)
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

							Assertions.assertEquals(SUCCESS,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction be successful!");

							Assertions.assertEquals(
									signTx.getResponseRecord().getConsensusTimestamp().getNanos() + normalTriggeredTxnTimestampOffset,
									triggeredTx.getResponseRecord().getConsensusTimestamp().getNanos(),
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
	public HapiApiSpec executionWithCustomPayerAndAdminKeyWorks() {
		long transferAmount = 1;
		return defaultHapiSpec("ExecutionWithCustomPayerAndAdminKeyWorks")
				.given(
						newKeyNamed("adminKey"),
						cryptoCreate("payingAccount"),
						cryptoCreate("sender"),
						cryptoCreate("receiver"),
						scheduleCreate(
								"basicXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", transferAmount)
								)
						)
								.adminKey("adminKey")
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

							Assertions.assertEquals(SUCCESS,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction be successful!");

							Assertions.assertEquals(
									signTx.getResponseRecord().getConsensusTimestamp().getNanos() + normalTriggeredTxnTimestampOffset,
									triggeredTx.getResponseRecord().getConsensusTimestamp().getNanos(),
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

	public HapiApiSpec executionWithCustomPayerWhoSignsAtCreationAsPayerWorks() {
		long transferAmount = 1;
		return defaultHapiSpec("ExecutionWithCustomPayerWhoSignsAtCreationAsPayerWorks")
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
								.payingWith("payingAccount")
								.designatingPayer("payingAccount")
								.via("createTx")
				).when(
						scheduleSign("basicXfer")
								.alsoSigningWith("sender")
								.via("signTx")
								.hasKnownStatus(SUCCESS)
				).then(
						withOpContext((spec, opLog) -> {
							var createTx = getTxnRecord("createTx");
							var signTx = getTxnRecord("signTx");
							var triggeredTx = getTxnRecord("createTx").scheduled();
							allRunFor(spec, createTx, signTx, triggeredTx);

							Assertions.assertEquals(SUCCESS,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction be successful!");

							Assertions.assertEquals(
									signTx.getResponseRecord().getConsensusTimestamp().getNanos() + normalTriggeredTxnTimestampOffset,
									triggeredTx.getResponseRecord().getConsensusTimestamp().getNanos(),
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

	public static boolean transferListCheck(HapiGetTxnRecord triggered, AccountID givingAccountID,
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

	private HapiApiSpec scheduledFreezeWorksAsExpected() {

		final byte[] poeticUpgradeHash = getPoeticUpgradeHash();

		return defaultHapiSpec("ScheduledFreezeWorksAsExpected")
				.given(
						cryptoCreate("payingAccount"),
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
								.via(successTxn)
				)
				.when(
						scheduleSign("validSchedule")
								.alsoSigningWith(GENESIS)
								.payingWith("payingAccount")
								.via(signTxn)
								.hasKnownStatus(SUCCESS)
				)
				.then(
						freezeAbort().payingWith(GENESIS),
						overriding("scheduling.whitelist", HapiSpecSetup.getDefaultNodeProps().get("scheduling.whitelist")),
						getScheduleInfo("validSchedule").isExecuted(),
						withOpContext((spec, opLog) -> {
							var triggeredTx = getTxnRecord(successTxn).scheduled();
							allRunFor(spec, triggeredTx);

							Assertions.assertEquals(SUCCESS,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction be successful!");
						})
				);
	}

	private HapiApiSpec scheduledFreezeWithUnauthorizedPayerFails(boolean isLongTermEnabled) {

		final byte[] poeticUpgradeHash = getPoeticUpgradeHash();

		if (isLongTermEnabled) {

			return defaultHapiSpec("ScheduledFreezeWithUnauthorizedPayerFails")
					.given(
							cryptoCreate("payingAccount"),
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
									.payingWith("payingAccount")
									// we are always busy with long term enabled in this case because
									// there are no throttles for freeze and we deeply check with
									// long term enabled
									.hasPrecheck(BUSY),
							overriding("scheduling.whitelist", HapiSpecSetup.getDefaultNodeProps().get("scheduling.whitelist"))
					);
		}

		return defaultHapiSpec("ScheduledFreezeWithUnauthorizedPayerFails")
				.given(
						cryptoCreate("payingAccount"),
						cryptoCreate("payingAccount2"),
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
								.designatingPayer("payingAccount2")
								.payingWith("payingAccount")
								.via(successTxn)
				)
				.when(
						scheduleSign("validSchedule")
								.payingWith("payingAccount")
								.alsoSigningWith("payingAccount2")
								.via(signTxn)
								.hasKnownStatus(SUCCESS)
				)
				.then(
						freezeAbort().payingWith(GENESIS),
						overriding("scheduling.whitelist", HapiSpecSetup.getDefaultNodeProps().get("scheduling.whitelist")),
						getScheduleInfo("validSchedule").isExecuted(),
						withOpContext((spec, opLog) -> {
							var triggeredTx = getTxnRecord(successTxn).scheduled();
							allRunFor(spec, triggeredTx);

							Assertions.assertEquals(NOT_SUPPORTED,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction be NOT_SUPPORTED!");
						})
				);
	}

	private HapiApiSpec scheduledPermissionedFileUpdateWorksAsExpected() {
		return defaultHapiSpec("ScheduledPermissionedFileUpdateWorksAsExpected")
				.given(
						cryptoCreate("payingAccount"),
						overriding("scheduling.whitelist", "FileUpdate"),
						scheduleCreate("validSchedule",
								fileUpdate(standardUpdateFile).contents("fooo!")
						)
								.withEntityMemo(randomUppercase(100))
								.designatingPayer(FREEZE_ADMIN)
								.payingWith("payingAccount")
								.via(successTxn)
				)
				.when(
						scheduleSign("validSchedule")
								.alsoSigningWith(FREEZE_ADMIN)
								.payingWith("payingAccount")
								.via(signTxn)
								.hasKnownStatus(SUCCESS)
				)
				.then(
						overriding("scheduling.whitelist", HapiSpecSetup.getDefaultNodeProps().get("scheduling.whitelist")),
						getScheduleInfo("validSchedule").isExecuted(),
						withOpContext((spec, opLog) -> {
							var triggeredTx = getTxnRecord(successTxn).scheduled();
							allRunFor(spec, triggeredTx);

							Assertions.assertEquals(SUCCESS,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction be successful!");
						})
				);
	}

	private HapiApiSpec scheduledPermissionedFileUpdateUnauthorizedPayerFails() {

		return defaultHapiSpec("ScheduledPermissionedFileUpdateUnauthorizedPayerFails")
				.given(
						cryptoCreate("payingAccount"),
						cryptoCreate("payingAccount2"),
						overriding("scheduling.whitelist", "FileUpdate"),
						scheduleCreate("validSchedule",
							fileUpdate(standardUpdateFile).contents("fooo!")
						)
								.withEntityMemo(randomUppercase(100))
								.designatingPayer("payingAccount2")
								.payingWith("payingAccount")
								.via(successTxn)
				)
				.when(
						scheduleSign("validSchedule")
								.alsoSigningWith("payingAccount2", FREEZE_ADMIN)
								.payingWith("payingAccount")
								.via(signTxn)
								.hasKnownStatus(SUCCESS)
				)
				.then(
						overriding("scheduling.whitelist", HapiSpecSetup.getDefaultNodeProps().get("scheduling.whitelist")),
						getScheduleInfo("validSchedule").isExecuted(),
						withOpContext((spec, opLog) -> {
							var triggeredTx = getTxnRecord(successTxn).scheduled();
							allRunFor(spec, triggeredTx);

							Assertions.assertEquals(AUTHORIZATION_FAILED,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction be AUTHORIZATION_FAILED!");
						})
				);
	}
	private HapiApiSpec scheduledSystemDeleteWorksAsExpected() {

		return defaultHapiSpec("ScheduledSystemDeleteWorksAsExpected")
				.given(
						cryptoCreate("payingAccount"),
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
								.via(successTxn)
				)
				.when(
						scheduleSign("validSchedule")
								.alsoSigningWith(SYSTEM_DELETE_ADMIN)
								.payingWith("payingAccount")
								.via(signTxn)
								.hasKnownStatus(SUCCESS)
				)
				.then(
						overriding("scheduling.whitelist", HapiSpecSetup.getDefaultNodeProps().get("scheduling.whitelist")),
						getScheduleInfo("validSchedule").isExecuted(),
						getFileInfo("misc")
								.nodePayment(1_234L)
								.hasAnswerOnlyPrecheck(INVALID_FILE_ID),
						withOpContext((spec, opLog) -> {
							var triggeredTx = getTxnRecord(successTxn).scheduled();
							allRunFor(spec, triggeredTx);

							Assertions.assertEquals(SUCCESS,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction be successful!");
						})
				);
	}

	private HapiApiSpec scheduledSystemDeleteUnauthorizedPayerFails(boolean isLongTermEnabled) {

		if (isLongTermEnabled) {

			return defaultHapiSpec("ScheduledSystemDeleteUnauthorizedPayerFails")
					.given(
							cryptoCreate("payingAccount"),
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
									// we are always busy with long term enabled in this case because
									// there are no throttles for SystemDelete and we deeply check with
									// long term enabled
									.hasPrecheck(BUSY),
							overriding("scheduling.whitelist", HapiSpecSetup.getDefaultNodeProps().get("scheduling.whitelist"))
					);
		}

		return defaultHapiSpec("ScheduledSystemDeleteUnauthorizedPayerFails")
				.given(
						cryptoCreate("payingAccount"),
						cryptoCreate("payingAccount2"),
						fileCreate("misc")
								.lifetime(THREE_MONTHS_IN_SECONDS)
								.contents(ORIG_FILE),
						overriding("scheduling.whitelist", "SystemDelete"),
						scheduleCreate("validSchedule",
							systemFileDelete("misc")
									.updatingExpiry(1L)
						)
								.withEntityMemo(randomUppercase(100))
								.designatingPayer("payingAccount2")
								.payingWith("payingAccount")
								.via(successTxn)
				)
				.when(
						scheduleSign("validSchedule")
								.alsoSigningWith("payingAccount2")
								.payingWith("payingAccount")
								.via(signTxn)
								.hasKnownStatus(SUCCESS)
				)
				.then(
						overriding("scheduling.whitelist", HapiSpecSetup.getDefaultNodeProps().get("scheduling.whitelist")),
						getScheduleInfo("validSchedule").isExecuted(),
						getFileInfo("misc")
								.nodePayment(1_234L),
						withOpContext((spec, opLog) -> {
							var triggeredTx = getTxnRecord(successTxn).scheduled();
							allRunFor(spec, triggeredTx);

							Assertions.assertEquals(NOT_SUPPORTED,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction be NOT_SUPPORTED!");
						})
				);
	}

	private HapiApiSpec congestionPricingAffectsImmediateScheduleExecution() {
		var artificialLimits = protoDefsFromResource("testSystemFiles/artificial-limits-congestion.json");
		var defaultThrottles = protoDefsFromResource("testSystemFiles/throttles-dev.json");
		var contract = "Multipurpose";

		AtomicLong normalPrice = new AtomicLong();

		return defaultHapiSpec("CongestionPricingAffectsImmediateScheduleExecution")
				.given(
						cryptoCreate("civilian")
								.payingWith(GENESIS)
								.balance(ONE_MILLION_HBARS),
							overriding("scheduling.whitelist", "ContractCall"),

						uploadInitCode(contract),
						contractCreate(contract),


						scheduleCreate("cheapSchedule",
							contractCall(contract)
									.fee(ONE_HUNDRED_HBARS)
									.sending(ONE_HBAR)
						)
								.withEntityMemo(randomUppercase(100))
								.designatingPayer("civilian")
								.payingWith(GENESIS)
								.via("cheapCall"),
						scheduleSign("cheapSchedule")
								.alsoSigningWith("civilian")
								.fee(ONE_HUNDRED_HBARS)
								.payingWith(GENESIS)
								.hasKnownStatus(SUCCESS),

						getTxnRecord("cheapCall")
								.scheduled()
								.providingFeeTo(normalFee -> {
									log.info("Normal fee is {}", normalFee);
									normalPrice.set(normalFee);
								}),

						scheduleCreate("validSchedule",
							contractCall(contract)
									.fee(ONE_HUNDRED_HBARS)
									.sending(ONE_HBAR)
						)
								.withEntityMemo(randomUppercase(100))
								.designatingPayer("civilian")
								.payingWith(GENESIS)
								.via("pricyCall"),


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

						blockingOrder(IntStream.range(0, 10).mapToObj(i -> new HapiSpecOperation[] {
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

						scheduleSign("validSchedule")
								.alsoSigningWith("civilian")
								.fee(ONE_HUNDRED_HBARS)
								.payingWith(GENESIS)
								.hasKnownStatus(SUCCESS)
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

						getScheduleInfo("validSchedule").isExecuted(),
						withOpContext((spec, opLog) -> {
							var triggeredTx = getTxnRecord("pricyCall").scheduled();
							allRunFor(spec, triggeredTx);

							Assertions.assertEquals(SUCCESS,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction be successful!");

							var sevenXPrice = triggeredTx.getResponseRecord().getTransactionFee();

							Assertions.assertEquals(
									7.0,
									(1.0 * sevenXPrice) / normalPrice.get(),
									0.1,
									"~7x multiplier should be in affect!");
						})
				);
	}

	public static byte[] getPoeticUpgradeHash() {
		final byte[] poeticUpgradeHash;
		try {
			final var sha384 = MessageDigest.getInstance("SHA-384");
			final var poeticUpgrade = Files.readAllBytes(Paths.get(poeticUpgradeLoc));
			poeticUpgradeHash = sha384.digest(poeticUpgrade);
		} catch (NoSuchAlgorithmException | IOException e) {
			throw new IllegalStateException("scheduledFreezeWorksAsExpected environment is unsuitable", e);
		}
		return poeticUpgradeHash;
	}
}

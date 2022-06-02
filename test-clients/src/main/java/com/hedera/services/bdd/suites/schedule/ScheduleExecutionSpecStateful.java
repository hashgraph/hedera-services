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
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUppercase;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.schedule.ScheduleLongTermExecutionSpecs.withAndWithoutLongTermEnabled;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;

public class ScheduleExecutionSpecStateful extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ScheduleExecutionSpecStateful.class);

	private static final int TMP_MAX_TRANSFER_LENGTH = 2;
	private static final int TMP_MAX_TOKEN_TRANSFER_LENGTH = 2;

	private static final String defaultTxExpiry =
			HapiSpecSetup.getDefaultNodeProps().get("ledger.schedule.txExpiryTimeSecs");
	private static final String defaultMaxTransferLen =
			HapiSpecSetup.getDefaultNodeProps().get("ledger.transfers.maxLen");
	private static final String defaultMaxTokenTransferLen =
			HapiSpecSetup.getDefaultNodeProps().get("ledger.tokenTransfers.maxLen");
	private static final String defaultWhitelist =
			HapiSpecSetup.getDefaultNodeProps().get("scheduling.whitelist");

	private static final String A_TOKEN = "token";
	String failingTxn = "failingTxn";

	public static void main(String... args) {
		new ScheduleExecutionSpecStateful().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return withAndWithoutLongTermEnabled(() -> List.of(
			/* Stateful specs from ScheduleExecutionSpecs */
			scheduledUniqueMintFailsWithNftsDisabled(),
			scheduledUniqueBurnFailsWithNftsDisabled(),
			scheduledBurnWithInvalidTokenThrowsUnresolvableSigners(),
			executionWithTransferListWrongSizedFails(),
			executionWithTokenTransferListSizeExceedFails(),
			suiteCleanup()
		));
	}

	private HapiApiSpec scheduledBurnWithInvalidTokenThrowsUnresolvableSigners() {
		return defaultHapiSpec("ScheduledBurnWithInvalidTokenThrowsUnresolvableSigners")
				.given(
						cryptoCreate("schedulePayer")
				)
				.when(
						scheduleCreate("validSchedule", burnToken(
								"0.0.123231", List.of(1L, 2L)
						))
								.designatingPayer("schedulePayer")
								.hasKnownStatus(UNRESOLVABLE_REQUIRED_SIGNERS)
				).then();
	}

	private HapiApiSpec scheduledUniqueMintFailsWithNftsDisabled() {
		return defaultHapiSpec("ScheduledUniqueMintFailsWithNftsDisabled")
				.given(
						cryptoCreate("treasury"),
						cryptoCreate("schedulePayer"),
						newKeyNamed("supplyKey"),
						tokenCreate(A_TOKEN)
								.supplyKey("supplyKey")
								.treasury("treasury")
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0),
						scheduleCreate("validSchedule", mintToken(
								A_TOKEN, List.of(
										ByteString.copyFromUtf8("m1")
								)
						))
								.designatingPayer("schedulePayer")
								.via(failingTxn),
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"tokens.nfts.areEnabled", "false"
								))
				)
				.when(
						scheduleSign("validSchedule")
								.alsoSigningWith("supplyKey", "schedulePayer", "treasury")
								.hasKnownStatus(SUCCESS)
				).then(
						getTxnRecord(failingTxn).scheduled()
								.hasPriority(recordWith().status(NOT_SUPPORTED)),
						getTokenInfo(A_TOKEN)
								.hasTotalSupply(0),
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"tokens.nfts.areEnabled", "true"
								))
				);
	}

	private HapiApiSpec scheduledUniqueBurnFailsWithNftsDisabled() {
		return defaultHapiSpec("ScheduledUniqueBurnFailsWithNftsDisabled")
				.given(
						cryptoCreate("treasury"),
						cryptoCreate("schedulePayer"),
						newKeyNamed("supplyKey"),
						tokenCreate(A_TOKEN)
								.supplyKey("supplyKey")
								.treasury("treasury")
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0),
						scheduleCreate("validSchedule", burnToken(A_TOKEN, List.of(1L, 2L)))
								.designatingPayer("schedulePayer")
								.via(failingTxn),
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"tokens.nfts.areEnabled", "false"
								))
				)
				.when(
						scheduleSign("validSchedule")
								.alsoSigningWith("supplyKey", "schedulePayer", "treasury")
								.hasKnownStatus(SUCCESS)
				).then(
						getTxnRecord(failingTxn).scheduled()
								.hasPriority(recordWith().status(NOT_SUPPORTED)),
						getTokenInfo(A_TOKEN)
								.hasTotalSupply(0),
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"tokens.nfts.areEnabled", "true"
								))
				);
	}

	public HapiApiSpec executionWithTransferListWrongSizedFails() {
		long transferAmount = 1L;
		long senderBalance = 1000L;
		long payingAccountBalance = 1_000_000L;
		long noBalance = 0L;
		final var rejectedTxn = "rejectedTxn";

		return defaultHapiSpec("ExecutionWithTransferListWrongSizedFails")
				.given(
						overriding("ledger.transfers.maxLen", "" + TMP_MAX_TRANSFER_LENGTH),
						cryptoCreate("payingAccount").balance(payingAccountBalance),
						cryptoCreate("sender").balance(senderBalance),
						cryptoCreate("receiverA").balance(noBalance),
						cryptoCreate("receiverB").balance(noBalance),
						cryptoCreate("receiverC").balance(noBalance),
						scheduleCreate(
								rejectedTxn,
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiverA", transferAmount),
										tinyBarsFromTo("sender", "receiverB", transferAmount),
										tinyBarsFromTo("sender", "receiverC", transferAmount)
								)
										.memo(randomUppercase(100))
						)
								.designatingPayer("payingAccount")
								.via("createTx")
				).when(
						scheduleSign(rejectedTxn)
								.alsoSigningWith("sender", "payingAccount")
								.via("signTx")
								.hasKnownStatus(SUCCESS)
				).then(
						overriding("ledger.transfers.maxLen", defaultMaxTransferLen),
						getAccountBalance("sender").hasTinyBars(senderBalance),
						getAccountBalance("receiverA").hasTinyBars(noBalance),
						getAccountBalance("receiverB").hasTinyBars(noBalance),
						getAccountBalance("receiverC").hasTinyBars(noBalance),
						withOpContext((spec, opLog) -> {
							var triggeredTx = getTxnRecord("createTx").scheduled();

							allRunFor(spec, triggeredTx);

							Assertions.assertEquals(
									TRANSFER_LIST_SIZE_LIMIT_EXCEEDED,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction should not be successful!");
						})
				);
	}

	private HapiApiSpec executionWithTokenTransferListSizeExceedFails() {
		String xToken = "XXX";
		String invalidSchedule = "withMaxTokenTransfer";
		String schedulePayer = "somebody", xTreasury = "xt", civilianA = "xa", civilianB = "xb";
		String failedTxn = "bad";

		return defaultHapiSpec("ExecutionWithTokenTransferListSizeExceedFails")
				.given(
						overriding("ledger.tokenTransfers.maxLen", "" + TMP_MAX_TOKEN_TRANSFER_LENGTH),
						newKeyNamed("admin"),
						cryptoCreate(schedulePayer),
						cryptoCreate(xTreasury),
						cryptoCreate(civilianA),
						cryptoCreate(civilianB),
						tokenCreate(xToken)
								.treasury(xTreasury)
								.initialSupply(100)
								.adminKey("admin"),
						tokenAssociate(civilianA, xToken),
						tokenAssociate(civilianB, xToken)
				).when(
						scheduleCreate(invalidSchedule,
								cryptoTransfer(
										moving(2, xToken).distributing(xTreasury, civilianA, civilianB)
								)
										.memo(randomUppercase(100))
						)
								.via(failedTxn)
								.alsoSigningWith(xTreasury, schedulePayer)
								.designatingPayer(schedulePayer)
				).then(
						overriding("ledger.tokenTransfers.maxLen", defaultMaxTokenTransferLen),
						getTxnRecord(failedTxn).scheduled()
								.hasPriority(recordWith().status(TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED)),
						getAccountBalance(xTreasury).hasTokenBalance(xToken, 100)
				);
	}


	private HapiApiSpec suiteCleanup() {
		return defaultHapiSpec("suiteCleanup")
				.given().when().then(
						overriding("ledger.schedule.txExpiryTimeSecs", defaultTxExpiry),
						overriding("scheduling.whitelist", defaultWhitelist),
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"tokens.nfts.areEnabled", "true"
								))
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

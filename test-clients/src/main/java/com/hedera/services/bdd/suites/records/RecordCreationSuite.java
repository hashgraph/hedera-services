package com.hedera.services.bdd.suites.records;

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
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.utils.sysfiles.serdes.StandardSerdes;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.hederahashgraph.api.proto.java.Setting;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.fee.FeeObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.assertions.TransferListAsserts.includingDeduction;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountRecords;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uncheckedSubmit;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.wipeTokenAccount;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.uploadDefaultFeeSchedules;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RecordCreationSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(RecordCreationSuite.class);

	private static final long SLEEP_MS = 1_000L;
	private static final String defaultRecordsTtl = HapiSpecSetup.getDefaultNodeProps().get("cache.records.ttl");

	public static void main(String... args) {
		new RecordCreationSuite().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						ensureSystemStateAsExpected(),
						confirmNftToggleIsWorksThenReenable(),
						payerRecordCreationSanityChecks(),
						accountsGetPayerRecordsIfSoConfigured(),
						calledContractNoLongerGetsRecord(),
						thresholdRecordsDontExistAnymore(),
						submittingNodeChargedNetworkFeeForLackOfDueDiligence(),
						submittingNodeChargedNetworkFeeForIgnoringPayerUnwillingness(),
						submittingNodeStillPaidIfServiceFeesOmitted(),

						/* This last spec requires sleeping for the default TTL (180s) so that the
						expiration queue will be purged of all entries for existing records.

						Especially since we are _very_ unlikely to make a dynamic change to
						cache.records.ttl in practice, this test is not worth running in CircleCI.

						However, it is a good sanity check to have available locally when making
						changes to record expiration.  */
//						recordsTtlChangesAsExpected(),
				}
		);
	}

	private HapiApiSpec confirmNftToggleIsWorksThenReenable() {
		final var acceptedTokenAttempt = "someSuch";
		final var blockedTokenAttempt = "neverToBe";
		final var supplyKey = "supplyKey";
		final var wipeKey = "wipeKey";
		final var miscAccount = "civilian";

		return defaultHapiSpec("ConfirmNftToggleIsWorksThenReenable")
				.given(
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"tokens.nfts.areEnabled", "false"
								)),
						tokenCreate(blockedTokenAttempt)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0L)
								.hasPrecheck(NOT_SUPPORTED),
						mintToken("1.2.3", List.of(ByteString.copyFromUtf8("NOPE")))
								.signedBy(DEFAULT_PAYER)
								.fee(ONE_HBAR)
								.hasPrecheck(NOT_SUPPORTED),
						burnToken("1.2.3", List.of(1L, 2L, 3L))
								.signedBy(DEFAULT_PAYER)
								.fee(ONE_HBAR)
								.hasPrecheck(NOT_SUPPORTED),
						wipeTokenAccount("1.2.3", "2.3.4", List.of(1L, 2L, 3L))
								.signedBy(DEFAULT_PAYER)
								.fee(ONE_HBAR)
								.hasPrecheck(NOT_SUPPORTED),
						cryptoTransfer(movingUnique("1.2.3", 1L)
								.between("2.3.4", "3.4.5")
						)
								.signedBy(DEFAULT_PAYER)
								.fee(ONE_HBAR)
								.hasPrecheck(NOT_SUPPORTED)
				).when(
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"tokens.nfts.areEnabled", "true"
								))
				).then(
						newKeyNamed(supplyKey),
						newKeyNamed(wipeKey),
						cryptoCreate(miscAccount),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(acceptedTokenAttempt)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0L)
								.wipeKey(wipeKey)
								.supplyKey(supplyKey)
								.treasury(TOKEN_TREASURY),
						mintToken(
								acceptedTokenAttempt,
								List.of(
										ByteString.copyFromUtf8("A"),
										ByteString.copyFromUtf8("B"),
										ByteString.copyFromUtf8("C"))),
						burnToken(acceptedTokenAttempt, List.of(2L)),
						tokenAssociate(miscAccount, acceptedTokenAttempt),
						cryptoTransfer(movingUnique(acceptedTokenAttempt, 1L)
								.between(TOKEN_TREASURY, miscAccount)
						),
						wipeTokenAccount(acceptedTokenAttempt, miscAccount, List.of(1L)),
						getAccountBalance(miscAccount).hasTokenBalance(acceptedTokenAttempt, 0L),
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(acceptedTokenAttempt, 1L)
				);
	}

	private HapiApiSpec ensureSystemStateAsExpected() {
		final var EMPTY_KEY = Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build();
		try {
			final var defaultPermissionsLoc = "src/main/resource/api-permission.properties";
			final var stylized121 = Files.readString(Paths.get(defaultPermissionsLoc));
			final var serde = StandardSerdes.SYS_FILE_SERDES.get(122L);

			return defaultHapiSpec("EnsureDefaultSystemFiles")
					.given(
							uploadDefaultFeeSchedules(GENESIS),
							fileUpdate(API_PERMISSIONS)
									.payingWith(GENESIS)
									.contents(serde.toValidatedRawFile(stylized121))
					).when().then(
							getAccountDetails("0.0.800")
									.payingWith(GENESIS)
									.has(accountWith()
											.expiry(33197904000L, 0)
											.key(EMPTY_KEY)
											.memo("")
											.noAlias()
											.noAllowances()),
							getAccountDetails("0.0.801")
									.payingWith(GENESIS)
									.has(accountWith()
											.expiry(33197904000L, 0)
											.key(EMPTY_KEY)
											.memo("")
											.noAlias()
											.noAllowances())
					);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private HapiApiSpec submittingNodeStillPaidIfServiceFeesOmitted() {
		final String comfortingMemo = "This is ok, it's fine, it's whatever.";
		final AtomicReference<FeeObject> feeObs = new AtomicReference<>();

		return defaultHapiSpec("submittingNodeStillPaidIfServiceFeesOmitted")
				.given(
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"staking.fees.stakingRewardPercentage", "10",
										"staking.fees.nodeRewardPercentage", "10"
								)),
						cryptoTransfer(tinyBarsFromTo(GENESIS, "0.0.3", ONE_HBAR))
								.payingWith(GENESIS),
						cryptoCreate("payer"),
						cryptoTransfer(
								tinyBarsFromTo(GENESIS, FUNDING, 1L)
						)
								.memo(comfortingMemo)
								.exposingFeesTo(feeObs)
								.payingWith("payer")
				).when(
						balanceSnapshot("before", "0.0.3"),
						balanceSnapshot("fundingBefore", "0.0.98"),
						balanceSnapshot("stakingReward", "0.0.800"),
						balanceSnapshot("nodeReward", "0.0.801"),
						sourcing(() ->
								cryptoTransfer(
										tinyBarsFromTo(GENESIS, FUNDING, 1L)
								)
										.memo(comfortingMemo)
										.fee(feeObs.get().getNetworkFee() + feeObs.get().getNodeFee())
										.payingWith("payer")
										.via("txnId")
										.hasKnownStatus(INSUFFICIENT_TX_FEE)
										.logged()
						)
				).then(
						sourcing(() ->
								getAccountBalance("0.0.3")
										.hasTinyBars(
												changeFromSnapshot("before", +feeObs.get().getNodeFee()))
										.logged()),
						sourcing(() ->
								getAccountBalance("0.0.98")
										.hasTinyBars(
												changeFromSnapshot("fundingBefore",
														(long) (+feeObs.get().getNetworkFee() * 0.8 + 1)))
										.logged()
						),
						sourcing(() ->
								getAccountBalance("0.0.800")
										.hasTinyBars(
												changeFromSnapshot("stakingReward",
														(long) (+feeObs.get().getNetworkFee() * 0.1)))
										.logged()),
						sourcing(() ->
								getAccountBalance("0.0.801")
										.hasTinyBars(
												changeFromSnapshot("nodeReward",
														(long) (+feeObs.get().getNetworkFee() * 0.1)))
										.logged()),
						sourcing(() ->
								getTxnRecord("txnId")
										.assertingNothingAboutHashes()
										.hasPriority(recordWith()
												.transfers(includingDeduction(
														"payer",
														feeObs.get().getNetworkFee() + feeObs.get().getNodeFee()))
												.status(INSUFFICIENT_TX_FEE))
										.logged())
				);
	}

	private HapiApiSpec submittingNodeChargedNetworkFeeForLackOfDueDiligence() {
		final String comfortingMemo = "This is ok, it's fine, it's whatever.";
		final String disquietingMemo = "\u0000his is ok, it's fine, it's whatever.";
		final AtomicReference<FeeObject> feeObs = new AtomicReference<>();

		return defaultHapiSpec("SubmittingNodeChargedNetworkFeeForLackOfDueDiligence")
				.given(
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"staking.fees.stakingRewardPercentage", "10",
										"staking.fees.nodeRewardPercentage", "20"
								)),
						cryptoTransfer(tinyBarsFromTo(GENESIS, "0.0.3", ONE_HBAR))
								.payingWith(GENESIS),
						cryptoCreate("payer"),
						cryptoTransfer(
								tinyBarsFromTo(GENESIS, FUNDING, 1L)
						)
								.memo(comfortingMemo)
								.exposingFeesTo(feeObs)
								.payingWith("payer"),
						usableTxnIdNamed("txnId")
								.payerId("payer")
				).when(
						balanceSnapshot("before", "0.0.3"),
						balanceSnapshot("fundingBefore", "0.0.98"),
						balanceSnapshot("stakingReward", "0.0.800"),
						balanceSnapshot("nodeReward", "0.0.801"),
						uncheckedSubmit(
								cryptoTransfer(
										tinyBarsFromTo(GENESIS, FUNDING, 1L)
								)
										.memo(disquietingMemo)
										.payingWith("payer")
										.txnId("txnId")
						)
								.payingWith(GENESIS),
						sleepFor(SLEEP_MS)
				).then(
						sourcing(() ->
								getAccountBalance("0.0.3")
										.hasTinyBars(
												changeFromSnapshot("before", -feeObs.get().getNetworkFee()))),
						sourcing(() ->
								getAccountBalance("0.0.98")
										.hasTinyBars(
												changeFromSnapshot("fundingBefore",
														(long) (+feeObs.get().getNetworkFee() * 0.7 + 1)))
										.logged()
						),
						sourcing(() ->
								getAccountBalance("0.0.800")
										.hasTinyBars(
												changeFromSnapshot("stakingReward",
														(long) (+feeObs.get().getNetworkFee() * 0.1)))
										.logged()),
						sourcing(() ->
								getAccountBalance("0.0.801")
										.hasTinyBars(
												changeFromSnapshot("nodeReward",
														(long) (+feeObs.get().getNetworkFee() * 0.2)))
										.logged()),
						sourcing(() ->
								getTxnRecord("txnId")
										.assertingNothingAboutHashes()
										.hasPriority(recordWith()
												.transfers(includingDeduction(() -> 3L, feeObs.get().getNetworkFee()))
												.status(INVALID_ZERO_BYTE_IN_STRING))
										.logged())
				);
	}

	private HapiApiSpec submittingNodeChargedNetworkFeeForIgnoringPayerUnwillingness() {
		final String comfortingMemo = "This is ok, it's fine, it's whatever.";
		final AtomicReference<FeeObject> feeObs = new AtomicReference<>();

		return defaultHapiSpec("SubmittingNodeChargedNetworkFeeForIgnoringPayerUnwillingness")
				.given(
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"staking.fees.stakingRewardPercentage", "10",
										"staking.fees.nodeRewardPercentage", "20"
								)),
						cryptoTransfer(tinyBarsFromTo(GENESIS, "0.0.3", ONE_HBAR))
								.payingWith(GENESIS),
						cryptoCreate("payer"),
						cryptoTransfer(
								tinyBarsFromTo(GENESIS, FUNDING, 1L)
						)
								.memo(comfortingMemo)
								.exposingFeesTo(feeObs)
								.payingWith("payer"),
						usableTxnIdNamed("txnId")
								.payerId("payer")
				).when(
						balanceSnapshot("before", "0.0.3"),
						balanceSnapshot("fundingBefore", "0.0.98"),
						balanceSnapshot("stakingReward", "0.0.800"),
						balanceSnapshot("nodeReward", "0.0.801"),
						sourcing(() ->
								uncheckedSubmit(
										cryptoTransfer(
												tinyBarsFromTo(GENESIS, FUNDING, 1L)
										)
												.memo(comfortingMemo)
												.fee(feeObs.get().getNetworkFee() - 1L)
												.payingWith("payer")
												.txnId("txnId")
								)
										.payingWith(GENESIS)
						),
						sleepFor(SLEEP_MS)
				).then(
						sourcing(() ->
								getAccountBalance("0.0.3")
										.hasTinyBars(
												changeFromSnapshot("before", -feeObs.get().getNetworkFee()))),
						sourcing(() ->
								getAccountBalance("0.0.98")
										.hasTinyBars(
												changeFromSnapshot("fundingBefore",
														(long) (+feeObs.get().getNetworkFee() * 0.7 + 1)))
										.logged()
						),
						sourcing(() ->
								getAccountBalance("0.0.800")
										.hasTinyBars(
												changeFromSnapshot("stakingReward",
														(long) (+feeObs.get().getNetworkFee() * 0.1)))
										.logged()),
						sourcing(() ->
								getAccountBalance("0.0.801")
										.hasTinyBars(
												changeFromSnapshot("nodeReward",
														(long) (+feeObs.get().getNetworkFee() * 0.2)))
										.logged()),
						sourcing(() ->
								getTxnRecord("txnId")
										.assertingNothingAboutHashes()
										.hasPriority(recordWith()
												.transfers(includingDeduction(() -> 3L, feeObs.get().getNetworkFee()))
												.status(INSUFFICIENT_TX_FEE))
										.logged())
				);
	}


	private HapiApiSpec payerRecordCreationSanityChecks() {
		return defaultHapiSpec("PayerRecordCreationSanityChecks")
				.given(
						cryptoCreate("payer")
				).when(
						createTopic("ofGeneralInterest").payingWith("payer"),
						cryptoTransfer(
								tinyBarsFromTo(GENESIS, FUNDING, 1_000L)
						).payingWith("payer"),
						submitMessageTo("ofGeneralInterest")
								.message("I say!")
								.payingWith("payer")
				).then(
						assertionsHold((spec, opLog) -> {
							final var payerId = spec.registry().getAccountID("payer");
							final var subOp = getAccountRecords("payer").logged();
							allRunFor(spec, subOp);
							final var records = subOp.getResponse().getCryptoGetAccountRecords().getRecordsList();
							assertEquals(3, records.size());
							for (var record : records) {
								assertEquals(record.getTransactionFee(), -netChangeIn(record, payerId));
							}
						})
				);
	}

	private long netChangeIn(TransactionRecord record, AccountID id) {
		return record.getTransferList().getAccountAmountsList().stream()
				.filter(aa -> id.equals(aa.getAccountID()))
				.mapToLong(AccountAmount::getAmount)
				.sum();
	}

	private HapiApiSpec accountsGetPayerRecordsIfSoConfigured() {
		final var txn = "ofRecord";

		return defaultHapiSpec("AccountsGetPayerRecordsIfSoConfigured")
				.given(
						cryptoCreate("payer")
				).when(
						cryptoTransfer(
								tinyBarsFromTo(GENESIS, FUNDING, 1_000L)
						).payingWith("payer").via(txn)
				).then(
						getAccountRecords("payer").has(inOrder(recordWith().txnId(txn)))
				);
	}

	private HapiApiSpec calledContractNoLongerGetsRecord() {
		return defaultHapiSpec("CalledContractNoLongerGetsRecord")
				.given(
						uploadInitCode("PayReceivable")
				).when(
						contractCreate("PayReceivable").via("createTxn")
				).then(
						contractCall("PayReceivable", "deposit", 1_000L)
								.via("callTxn")
								.sending(1_000L)
				);
	}

	private HapiApiSpec thresholdRecordsDontExistAnymore() {
		return defaultHapiSpec("OnlyNetAdjustmentIsComparedToThresholdWhenCreating")
				.given(
						cryptoCreate("payer"),
						cryptoCreate("lowSendThreshold").sendThreshold(1L),
						cryptoCreate("lowReceiveThreshold").receiveThreshold(1L)
				).when(
						cryptoTransfer(
								tinyBarsFromTo(
										"lowSendThreshold",
										"lowReceiveThreshold",
										2L)
						).payingWith("payer").via("testTxn")
				).then(
						getAccountRecords("payer").has(inOrder(recordWith().txnId("testTxn"))),
						getAccountRecords("lowSendThreshold").has(inOrder()),
						getAccountRecords("lowReceiveThreshold").has(inOrder())
				);
	}

	private HapiApiSpec recordsTtlChangesAsExpected() {
		final int abbrevCacheTtl = 3;
		final String brieflyAvailMemo = "I can't stay for long...";
		final AtomicReference<byte[]> origPropContents = new AtomicReference<>();

		return defaultHapiSpec("RecordsTtlChangesAsExpected")
				.given(
						getFileContents(APP_PROPERTIES)
								.consumedBy(origPropContents::set),
						sleepFor((Long.parseLong(defaultRecordsTtl) + 1) * 1_000L),
						sourcing(() ->
								fileUpdate(APP_PROPERTIES)
										.fee(ONE_HUNDRED_HBARS)
										.contents(rawConfigPlus(
												origPropContents.get(),
												"cache.records.ttl",
												"" + abbrevCacheTtl))
										.payingWith(GENESIS)
						),
						cryptoCreate("payer")
				).when(
						cryptoTransfer(tinyBarsFromTo("payer", ADDRESS_BOOK_CONTROL, 1L))
								.memo(brieflyAvailMemo)
								.payingWith("payer"),
						getAccountRecords("payer").has(inOrder(recordWith().memo(brieflyAvailMemo))),
						sleepFor(abbrevCacheTtl * 1_000L),
						cryptoTransfer(tinyBarsFromTo(GENESIS, ADDRESS_BOOK_CONTROL, 1L))
								.payingWith(GENESIS),
						getAccountRecords("payer").has(inOrder())
				).then(
						sourcing(() ->
								fileUpdate(APP_PROPERTIES)
										.contents(origPropContents.get()))
				);
	}

	private byte[] rawConfigPlus(byte[] rawBase, String extraName, String extraValue) {
		try {
			final var rawConfig = ServicesConfigurationList.parseFrom(rawBase);
			return rawConfig.toBuilder().addNameValue(Setting.newBuilder()
					.setName(extraName)
					.setValue(extraValue)
			).build().toByteArray();
		} catch (InvalidProtocolBufferException e) {
			throw new IllegalStateException("Existing 0.0.121 wasn't valid protobuf!", e);
		}
	}


	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

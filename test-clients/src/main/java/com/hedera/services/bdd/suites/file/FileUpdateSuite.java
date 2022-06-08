package com.hedera.services.bdd.suites.file;

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
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.token.TokenAssociationSpecs;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.BYTES_4K;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUtf8Bytes;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uncheckedSubmit;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFee;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateSpecialFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONSENSUS_GAS_EXHAUSTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_CONTRACT_STORAGE_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.FreezeNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Frozen;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Unfrozen;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.KycNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.Revoked;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NOTE: 1. This test suite covers the test08UpdateFile() test scenarios from the legacy FileServiceIT test class after
 * the FileServiceIT class is removed since all other test scenarios in this class are already covered by test suites
 * under com.hedera.services.legacy.regression.suites.file and com.hedera.services.legacy.regression.suites.crpto.
 *
 * 2. While this class now provides minimal coverage for proto's FileUpdate transaction, we shall add more positive and
 * negative test scenarios to cover FileUpdate, such as missing (partial) keys for update, for update of expirationTime,
 * for modifying keys field, etc.
 *
 * We'll come back to add all missing test scenarios for this and other test suites once we are done with cleaning up
 * old test cases.
 */
public class FileUpdateSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(FileUpdateSuite.class);
	private static final String CONTRACT = "CreateTrivial";

	private static final String INDIVIDUAL_KV_LIMIT_PROP = "contracts.maxKvPairs.individual";
	private static final String AGGREGATE_KV_LIMIT_PROP = "contracts.maxKvPairs.aggregate";
	private static final String USE_GAS_THROTTLE_PROP = "contracts.throttle.throttleByGas";
	private static final String CONSENSUS_GAS_THROTTLE_PROP = "contracts.consensusThrottleMaxGasLimit";

	private static final long defaultMaxLifetime =
			Long.parseLong(HapiSpecSetup.getDefaultNodeProps().get("entities.maxLifetime"));
	private static final String defaultMaxCustomFees =
			HapiSpecSetup.getDefaultNodeProps().get("tokens.maxCustomFeesAllowed");
	private static final String defaultMaxTokenPerAccount =
			HapiSpecSetup.getDefaultNodeProps().get("tokens.maxRelsPerInfoQuery");
	private static final String defaultMaxIndividualKvPairs =
			HapiSpecSetup.getDefaultNodeProps().get(INDIVIDUAL_KV_LIMIT_PROP);
	private static final String defaultMaxAggregateKvPairs =
			HapiSpecSetup.getDefaultNodeProps().get(AGGREGATE_KV_LIMIT_PROP);
	private static final String defaultMaxConsGasLimit = HapiSpecSetup.getDefaultNodeProps()
			.get("contracts.consensusThrottleMaxGasLimit");

	public static void main(String... args) {
		new FileUpdateSuite().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				vanillaUpdateSucceeds(),
				updateFeesCompatibleWithCreates(),
				apiPermissionsChangeDynamically(),
				cannotUpdateExpirationPastMaxLifetime(),
				optimisticSpecialFileUpdate(),
				associateHasExpectedSemantics(),
				notTooManyFeeScheduleCanBeCreated(),
				allUnusedGasIsRefundedIfSoConfigured(),
				maxRefundIsEnforced(),
				gasLimitOverMaxGasLimitFailsPrecheck(),
				autoCreationIsDynamic(),
				kvLimitsEnforced(),
				serviceFeeRefundedIfConsGasExhausted(),
		});
	}

	private HapiApiSpec associateHasExpectedSemantics() {
		return defaultHapiSpec("AssociateHasExpectedSemantics")
				.given(flattened(
						TokenAssociationSpecs.basicKeysAndTokens()
				)).when(
						cryptoCreate("misc").balance(0L),
						TxnVerbs.tokenAssociate("misc", TokenAssociationSpecs.FREEZABLE_TOKEN_ON_BY_DEFAULT),
						TxnVerbs.tokenAssociate("misc", TokenAssociationSpecs.FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.hasKnownStatus(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT),
						tokenAssociate("misc", "1.2.3")
								.hasKnownStatus(INVALID_TOKEN_ID),
						tokenAssociate("misc", "1.2.3", "1.2.3")
								.hasPrecheck(TOKEN_ID_REPEATED_IN_TOKEN_LIST),
						tokenDissociate("misc", "1.2.3", "1.2.3")
								.hasPrecheck(TOKEN_ID_REPEATED_IN_TOKEN_LIST),
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of("tokens.maxRelsPerInfoQuery", "" + 1)),
						fileUpdate(APP_PROPERTIES).overridingProps(Map.of(
								"tokens.maxRelsPerInfoQuery", "" + 1000
						)).payingWith(ADDRESS_BOOK_CONTROL),
						TxnVerbs.tokenAssociate("misc", TokenAssociationSpecs.FREEZABLE_TOKEN_OFF_BY_DEFAULT),
						tokenAssociate("misc", TokenAssociationSpecs.KNOWABLE_TOKEN,
								TokenAssociationSpecs.VANILLA_TOKEN)
				).then(
						getAccountInfo("misc")
								.hasToken(
										relationshipWith(TokenAssociationSpecs.FREEZABLE_TOKEN_ON_BY_DEFAULT)
												.kyc(KycNotApplicable)
												.freeze(Frozen))
								.hasToken(
										relationshipWith(TokenAssociationSpecs.FREEZABLE_TOKEN_OFF_BY_DEFAULT)
												.kyc(KycNotApplicable)
												.freeze(Unfrozen))
								.hasToken(
										relationshipWith(TokenAssociationSpecs.KNOWABLE_TOKEN)
												.kyc(Revoked)
												.freeze(FreezeNotApplicable))
								.hasToken(
										relationshipWith(TokenAssociationSpecs.VANILLA_TOKEN)
												.kyc(KycNotApplicable)
												.freeze(FreezeNotApplicable))
								.logged()
				);
	}

	public HapiApiSpec autoCreationIsDynamic() {
		final var aliasKey = "autoCreationKey";

		return defaultHapiSpec("AutoCreationIsDynamic")
				.given(
						newKeyNamed(aliasKey),
						overriding("autoCreation.enabled", "false")
				).when(
						cryptoTransfer(
								tinyBarsFromAccountToAlias(GENESIS, aliasKey, ONE_HBAR)
						).hasKnownStatus(NOT_SUPPORTED)
				).then(
						overriding("autoCreation.enabled", "true"),
						cryptoTransfer(
								tinyBarsFromAccountToAlias(GENESIS, aliasKey, ONE_HBAR)
						),
						getAliasedAccountInfo(aliasKey)
				);
	}

	public HapiApiSpec notTooManyFeeScheduleCanBeCreated() {
		final var denom = "fungible";
		final var token = "token";
		return defaultHapiSpec("OnlyValidCustomFeeScheduleCanBeCreated")
				.given(
						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(Map.of("tokens.maxCustomFeesAllowed", "1"))
				).when(
						tokenCreate(denom),
						tokenCreate(token)
								.treasury(DEFAULT_PAYER)
								.withCustom(fixedHbarFee(1, DEFAULT_PAYER))
								.withCustom(fixedHtsFee(1, denom, DEFAULT_PAYER))
								.hasKnownStatus(CUSTOM_FEES_LIST_TOO_LONG)
				).then(
						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(Map.of("tokens.maxCustomFeesAllowed", defaultMaxCustomFees))
				);
	}

	private HapiApiSpec optimisticSpecialFileUpdate() {
		final var appendsPerBurst = 128;
		final var specialFile = "0.0.159";
		final var specialFileContents = ByteString.copyFrom(randomUtf8Bytes(64 * BYTES_4K));
		return defaultHapiSpec("OptimisticSpecialFileUpdate")
				.given().when(
						updateSpecialFile(
								GENESIS,
								specialFile,
								specialFileContents,
								BYTES_4K,
								appendsPerBurst)
				).then(
						getFileContents(specialFile).hasContents(ignore -> specialFileContents.toByteArray())
				);
	}

	private HapiApiSpec apiPermissionsChangeDynamically() {
		return defaultHapiSpec("ApiPermissionsChangeDynamically")
				.given(
						cryptoCreate("civilian").balance(ONE_HUNDRED_HBARS),
						getFileContents(API_PERMISSIONS).logged(),
						tokenCreate("poc").payingWith("civilian")
				).when(
						fileUpdate(API_PERMISSIONS)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.erasingProps(Set.of("tokenCreate")),
						getFileContents(API_PERMISSIONS).logged()
				).then(
						tokenCreate("poc").payingWith("civilian").hasPrecheck(NOT_SUPPORTED),
						fileUpdate(API_PERMISSIONS)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of("tokenCreate", "0-*")),
						tokenCreate("secondPoc").payingWith("civilian")
				);
	}

	private HapiApiSpec updateFeesCompatibleWithCreates() {
		final long origLifetime = 7_200_000L;
		final long extension = 700_000L;
		final byte[] old2k = randomUtf8Bytes(BYTES_4K / 2);
		final byte[] new4k = randomUtf8Bytes(BYTES_4K);
		final byte[] new2k = randomUtf8Bytes(BYTES_4K / 2);

		return defaultHapiSpec("UpdateFeesCompatibleWithCreates")
				.given(
						fileCreate("test")
								.contents(old2k)
								.lifetime(origLifetime)
								.via("create")
				).when(
						fileUpdate("test")
								.contents(new4k)
								.extendingExpiryBy(0)
								.via("updateTo4"),
						fileUpdate("test")
								.contents(new2k)
								.extendingExpiryBy(0)
								.via("updateTo2"),
						fileUpdate("test")
								.extendingExpiryBy(extension)
								.via("extend"),
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of("maxFileSize", "1025"))
								.via("special"),
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of("maxFileSize", "1024"))
				).then(
						UtilVerbs.withOpContext((spec, opLog) -> {
							final var createOp = getTxnRecord("create");
							final var to4kOp = getTxnRecord("updateTo4");
							final var to2kOp = getTxnRecord("updateTo2");
							final var extensionOp = getTxnRecord("extend");
							final var specialOp = getTxnRecord("special");
							allRunFor(spec, createOp, to4kOp, to2kOp, extensionOp, specialOp);
							final var createFee = createOp.getResponseRecord().getTransactionFee();
							opLog.info("Creation : " + createFee);
							opLog.info("New 4k   : " + to4kOp.getResponseRecord().getTransactionFee()
									+ " (" + (to4kOp.getResponseRecord().getTransactionFee() - createFee) + ")");
							opLog.info("New 2k   : " + to2kOp.getResponseRecord().getTransactionFee()
									+ " (" + (to2kOp.getResponseRecord().getTransactionFee() - createFee) + ")");
							opLog.info("Extension: " + extensionOp.getResponseRecord().getTransactionFee()
									+ " (" + (extensionOp.getResponseRecord().getTransactionFee() - createFee) + ")");
							opLog.info("Special: " + specialOp.getResponseRecord().getTransactionFee());
						})
				);
	}

	private HapiApiSpec vanillaUpdateSucceeds() {
		final byte[] old4K = randomUtf8Bytes(BYTES_4K);
		final byte[] new4k = randomUtf8Bytes(BYTES_4K);
		final String firstMemo = "Originally";
		final String secondMemo = "Subsequently";

		return defaultHapiSpec("VanillaUpdateSucceeds")
				.given(
						fileCreate("test")
								.entityMemo(firstMemo)
								.contents(old4K)
				).when(
						fileUpdate("test")
								.entityMemo(ZERO_BYTE_MEMO)
								.contents(new4k)
								.hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
						fileUpdate("test")
								.entityMemo(secondMemo)
								.contents(new4k)
				).then(
						getFileContents("test").hasContents(ignore -> new4k),
						getFileInfo("test").hasMemo(secondMemo)
				);
	}

	private HapiApiSpec cannotUpdateExpirationPastMaxLifetime() {
		return defaultHapiSpec("CannotUpdateExpirationPastMaxLifetime")
				.given(
						fileCreate("test")
				).when().then(
						fileUpdate("test")
								.lifetime(defaultMaxLifetime + 12_345L)
								.hasPrecheck(AUTORENEW_DURATION_NOT_IN_RANGE)
				);
	}

	private HapiApiSpec maxRefundIsEnforced() {
		return defaultHapiSpec("MaxRefundIsEnforced")
				.given(
						overriding("contracts.maxRefundPercentOfGasLimit", "5"),
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
				).when(
						contractCall(CONTRACT, "create")
								.gas(1_000_000L)
				).then(
						contractCallLocal(CONTRACT, "getIndirect")
								.gas(300_000L)
								.has(resultWith().gasUsed(285_000L)),
						UtilVerbs.resetToDefault("contracts.maxRefundPercentOfGasLimit")
				);
	}

	private HapiApiSpec allUnusedGasIsRefundedIfSoConfigured() {
		return defaultHapiSpec("AllUnusedGasIsRefundedIfSoConfigured")
				.given(
						overriding("contracts.maxRefundPercentOfGasLimit", "100"),
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
				).when(
						contractCall(CONTRACT, "create")
								.gas(1_000_000L)
				).then(
						contractCallLocal(CONTRACT, "getIndirect")
								.gas(300_000L)
								.has(resultWith().gasUsed(26_451)),
						UtilVerbs.resetToDefault("contracts.maxRefundPercentOfGasLimit")
				);
	}

	private HapiApiSpec gasLimitOverMaxGasLimitFailsPrecheck() {
		return defaultHapiSpec("GasLimitOverMaxGasLimitFailsPrecheck")
				.given(
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT),
						overriding("contracts.maxGas", "100")
				).when().then(
						contractCallLocal(CONTRACT, "getIndirect").gas(101L)
								.hasCostAnswerPrecheck(MAX_GAS_LIMIT_EXCEEDED),
						UtilVerbs.resetToDefault("contracts.maxGas")
				);
	}

	private HapiApiSpec kvLimitsEnforced() {
		final var contract = "User";
		final var gasToOffer = 4_000_000;

		return defaultHapiSpec("KvLimitsEnforced")
				.given(
						uploadInitCode(contract),
						/* This contract has 0 key/value mappings at creation */
						contractCreate(contract),
						/* Now we update the per-contract limit to 10 mappings */
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										INDIVIDUAL_KV_LIMIT_PROP, "10",
										CONSENSUS_GAS_THROTTLE_PROP, "100_000_000"))
				).when(
						/* The first call to insert adds 5 mappings */
						contractCall(contract, "insert", 1, 1)
								.payingWith(GENESIS)
								.gas(gasToOffer),
						/* Each subsequent call to adds 3 mappings; so 8 total after this */
						contractCall(contract, "insert", 2, 4)
								.payingWith(GENESIS)
								.gas(gasToOffer),
						/* And this one fails because 8 + 3 = 11 > 10 */
						contractCall(contract, "insert", 3, 9)
								.payingWith(GENESIS)
								.hasKnownStatus(MAX_CONTRACT_STORAGE_EXCEEDED)
								.gas(gasToOffer),
						/* Confirm the storage size didn't change */
						getContractInfo(contract).has(contractWith().numKvPairs(8)),
						/* Now we update the per-contract limit to 1B mappings, but the aggregate limit to just 1 🤪 */
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										INDIVIDUAL_KV_LIMIT_PROP, "1_000_000_000",
										AGGREGATE_KV_LIMIT_PROP, "1")),
						contractCall(contract, "insert", 3, 9)
								.payingWith(GENESIS)
								.hasKnownStatus(MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED)
								.gas(gasToOffer),
						getContractInfo(contract).has(contractWith().numKvPairs(8))
				).then(
						/* Now restore the defaults and confirm we can use more storage */
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										INDIVIDUAL_KV_LIMIT_PROP, defaultMaxIndividualKvPairs,
										AGGREGATE_KV_LIMIT_PROP, defaultMaxAggregateKvPairs,
										CONSENSUS_GAS_THROTTLE_PROP, defaultMaxConsGasLimit)),
						contractCall(contract, "insert", 3, 9)
								.payingWith(GENESIS)
								.gas(gasToOffer),
						contractCall(contract, "insert", 4, 16)
								.payingWith(GENESIS)
								.gas(gasToOffer),
						getContractInfo(contract).has(contractWith().numKvPairs(14))
				);
	}

	private HapiApiSpec serviceFeeRefundedIfConsGasExhausted() {
		final var contract = "User";
		final var gasToOffer = 4_000_000;
		final var civilian = "payer";
		final var unrefundedTxn = "unrefundedTxn";
		final var refundedTxn = "refundedTxn";

		return defaultHapiSpec("ServiceFeeRefundedIfConsGasExhausted")
				.given(
						cryptoCreate(civilian),
						uploadInitCode(contract),
						contractCreate(contract),
						contractCall(contract, "insert", 1, 4)
								.payingWith(civilian)
								.gas(gasToOffer)
								.via(unrefundedTxn),
						overridingTwo(
								CONSENSUS_GAS_THROTTLE_PROP, "1",
								USE_GAS_THROTTLE_PROP, "true")
				).when(
						usableTxnIdNamed(refundedTxn).payerId(civilian),
						contractCall(contract, "insert", 2, 4)
								.payingWith(civilian)
								.gas(gasToOffer)
								.hasAnyStatusAtAll()
								.deferStatusResolution(),
						uncheckedSubmit(
								contractCall(contract, "insert", 3, 4)
										.signedBy(civilian)
										.gas(gasToOffer)
										.txnId(refundedTxn)
						)
								.payingWith(GENESIS)
				).then(
						sleepFor(2_000L),
						withOpContext((spec, opLog) -> {
							final var unrefundedOp = getTxnRecord(unrefundedTxn);
							final var refundedOp = getTxnRecord(refundedTxn)
									.assertingNothingAboutHashes();
							allRunFor(spec, refundedOp, unrefundedOp);
							assertEquals(
									CONSENSUS_GAS_EXHAUSTED,
									refundedOp.getResponseRecord().getReceipt().getStatus());
							final var origFee = unrefundedOp.getResponseRecord().getTransactionFee();
							final var feeSansRefund = refundedOp.getResponseRecord().getTransactionFee();
							assertTrue(
									feeSansRefund < origFee,
									"Expected service fee to be refunded, but sans fee "
											+ feeSansRefund + " was not less than " + origFee);
						}),
						overridingTwo(
								CONSENSUS_GAS_THROTTLE_PROP, defaultMaxConsGasLimit,
								USE_GAS_THROTTLE_PROP, "false")
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.BYTES_4K;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUtf8Bytes;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFee;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateSpecialFile;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.FreezeNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Frozen;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Unfrozen;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.KycNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.Revoked;

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

	private static final long defaultMaxLifetime =
			Long.parseLong(HapiSpecSetup.getDefaultNodeProps().get("entities.maxLifetime"));
	private static final String defaultMaxCustomFees =
			HapiSpecSetup.getDefaultNodeProps().get("tokens.maxCustomFeesAllowed");
	private static final String defaultMaxTokenPerAccount =
			HapiSpecSetup.getDefaultNodeProps().get("tokens.maxPerAccount");


	public static void main(String... args) {
		new FileUpdateSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				vanillaUpdateSucceeds(),
				updateFeesCompatibleWithCreates(),
				apiPermissionsChangeDynamically(),
				cannotUpdateExpirationPastMaxLifetime(),
				optimisticSpecialFileUpdate(),
				notTooManyFeeScheduleCanBeCreated(),
				numAccountsAllowedIsDynamic(),
				associateHasExpectedSemantics(),
				autoCreationIsDynamic(),
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
								.overridingProps(Map.of("tokens.maxPerAccount", "" + 1)),
						TxnVerbs.tokenAssociate("misc", TokenAssociationSpecs.FREEZABLE_TOKEN_OFF_BY_DEFAULT)
								.hasKnownStatus(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED),
						fileUpdate(APP_PROPERTIES).overridingProps(Map.of(
								"tokens.maxPerAccount", "" + 1000
						)).payingWith(ADDRESS_BOOK_CONTROL),
						TxnVerbs.tokenAssociate("misc", TokenAssociationSpecs.FREEZABLE_TOKEN_OFF_BY_DEFAULT),
						tokenAssociate("misc", TokenAssociationSpecs.KNOWABLE_TOKEN, TokenAssociationSpecs.VANILLA_TOKEN)
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

	public HapiApiSpec numAccountsAllowedIsDynamic() {
		final int MONOGAMOUS_NETWORK = 1;

		return defaultHapiSpec("NumAccountsAllowedIsDynamic")
				.given(
						newKeyNamed("admin"),
						cryptoCreate(TOKEN_TREASURY).balance(0L)
				).when(
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of("tokens.maxPerAccount", "" + MONOGAMOUS_NETWORK)),
						tokenCreate("primary")
								.adminKey("admin")
								.treasury(TOKEN_TREASURY),
						tokenCreate("secondaryFails")
								.treasury(TOKEN_TREASURY)
								.hasKnownStatus(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED),
						tokenDelete("primary"),
						/* Deleted tokens still count against your max allowed associations. */
						tokenCreate("secondaryFailsAgain")
								.treasury(TOKEN_TREASURY)
								.hasKnownStatus(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED)
				).then(
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"tokens.maxPerAccount", defaultMaxTokenPerAccount
								)),
						tokenCreate("secondary").treasury(TOKEN_TREASURY)
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

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

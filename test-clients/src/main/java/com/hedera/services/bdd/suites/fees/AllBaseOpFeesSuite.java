package com.hedera.services.bdd.suites.fees;

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
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.revokeTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.wipeTokenAccount;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.suites.utils.MiscEETUtils.metadata;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class AllBaseOpFeesSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(AllBaseOpFeesSuite.class);

	private static final double ALLOWED_DIFFERENCE_PERCENTAGE = 0.01;

	private static final String TREASURE_KEY = "treasureKey";
	private static final String FUNGIBLE_COMMON_TOKEN = "fungibleCommonToken";

	private static final String ADMIN_KEY = "adminKey";
	private static final String SUPPLY_KEY = "supplyKey";
	private static final String FREEZE_KEY = "freezeKey";
	private static final String WIPE_KEY = "wipeKey";
	private static final String KYC_KEY = "kycKey";

	private static final String CIVILIAN_ACCT = "civilian";

	private static final String UNIQUE_TOKEN = "nftType";

	private static final String BASE_TXN = "baseTxn";

	private static final String COMMON_TOKEN = "FCType";

	private static final double EXPECTED_TOKEN_GRANTKYC_PRICE_USD = 0.001;
	private static final double EXPECTED_TOKEN_REVOKEKYC_PRICE_USD = 0.001;
	private static final double EXPECTED_TOKEN_ASSOCIATE_PRICE_USD = 0.05;
	private static final double EXPECTED_TOKEN_DISSOCIATE_PRICE_USD = 0.05;

	private static final double EXPECTED_UNFREEZE_PRICE_USD = 0.001;
	private static final double EXPECTED_FREEZE_PRICE_USD = 0.001;
	private static final double EXPECTED_NFT_MINT_PRICE_USD = 0.001;
	private static final double EXPECTED_NFT_BURN_PRICE_USD = 0.001;
	private static final double EXPECTED_NFT_WIPE_PRICE_USD = 0.001;


	public static void main(String... args) {
		new AllBaseOpFeesSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(List.of(new HapiApiSpec[] {
				baseNftFreezeUnfreezeChargedAsExpected(),
				baseCommonFreezeUnfreezeChargedAsExpected(),
				baseNftMintOperationIsChargedExpectedFee(),
				baseNftWipeOperationIsChargedExpectedFee(),
				baseNftBurnOperationIsChargedExpectedFee(),
				baseTokenGrantRevokeKycChargedAsExpected(),
				baseTokenAssociateDissociateCharedAsExpected(),
				baseTokenDeleteIsChargedExpectedFee(),
				baseTokeUpdateIsChargedExpectedFee()
				}
			)
		);
	}

	private HapiApiSpec baseNftMintOperationIsChargedExpectedFee() {
		final var standard100ByteMetadata = ByteString.copyFromUtf8(
				"0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789");

		return defaultHapiSpec("BaseUniqueMintOperationIsChargedExpectedFee")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(CIVILIAN_ACCT).key(SUPPLY_KEY),
						tokenCreate(UNIQUE_TOKEN)
								.initialSupply(0L)
								.expiry(Instant.now().getEpochSecond() + THREE_MONTHS_IN_SECONDS)
								.supplyKey(SUPPLY_KEY)
								.tokenType(NON_FUNGIBLE_UNIQUE)
				)
				.when(
						mintToken(UNIQUE_TOKEN, List.of(standard100ByteMetadata))
								.payingWith(CIVILIAN_ACCT)
								.signedBy(SUPPLY_KEY)
								.blankMemo()
								.via(BASE_TXN)
				).then(
						validateChargedUsdWithin(BASE_TXN, EXPECTED_NFT_MINT_PRICE_USD, ALLOWED_DIFFERENCE_PERCENTAGE)
				);
	}

	private HapiApiSpec baseNftWipeOperationIsChargedExpectedFee() {
		return defaultHapiSpec("BaseUniqueWipeOperationIsChargedExpectedFee")
				.given(
						newKeyNamed(SUPPLY_KEY),
						newKeyNamed(WIPE_KEY),
						cryptoCreate(CIVILIAN_ACCT).key(WIPE_KEY),
						cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS).key(WIPE_KEY),
						tokenCreate(UNIQUE_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0L)
								.supplyKey(SUPPLY_KEY)
								.wipeKey(WIPE_KEY)
								.treasury(TOKEN_TREASURY),
						tokenAssociate(CIVILIAN_ACCT, UNIQUE_TOKEN),
						mintToken(UNIQUE_TOKEN,
								List.of(ByteString.copyFromUtf8("token_to_wipe"))),
						cryptoTransfer(movingUnique(UNIQUE_TOKEN, 1L)
								.between(TOKEN_TREASURY, CIVILIAN_ACCT))
				)
				.when(
						wipeTokenAccount(UNIQUE_TOKEN, CIVILIAN_ACCT, List.of(1L))
								.payingWith(TOKEN_TREASURY)
								.fee(ONE_HBAR)
								.blankMemo()
								.via(BASE_TXN)
				).then(
						validateChargedUsdWithin(BASE_TXN, EXPECTED_NFT_WIPE_PRICE_USD, 0.01)
				);
	}


	private HapiApiSpec baseNftBurnOperationIsChargedExpectedFee() {
		return defaultHapiSpec("BaseUniqueBurnOperationIsChargedExpectedFee")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(CIVILIAN_ACCT).key(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(UNIQUE_TOKEN)
								.initialSupply(0)
								.supplyKey(SUPPLY_KEY)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY),
						mintToken(UNIQUE_TOKEN, List.of(metadata("memo")))
				)
				.when(
						burnToken(UNIQUE_TOKEN, List.of(1L))
								.fee(ONE_HBAR)
								.payingWith(CIVILIAN_ACCT)
								.blankMemo()
								.via(BASE_TXN)
				).then(
						validateChargedUsdWithin(BASE_TXN, EXPECTED_NFT_BURN_PRICE_USD, 0.01)
				);
	}

	private HapiApiSpec baseNftFreezeUnfreezeChargedAsExpected() {
		return defaultHapiSpec("baseNftFreezeUnfreezeChargedAsExpected")
				.given(
						newKeyNamed(TREASURE_KEY),
						newKeyNamed(ADMIN_KEY),
						newKeyNamed(KYC_KEY),
						cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS).key(TREASURE_KEY),
						cryptoCreate(CIVILIAN_ACCT),
						tokenCreate(UNIQUE_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0L)
								.adminKey(ADMIN_KEY)
								.freezeKey(TOKEN_TREASURY)
								.kycKey(KYC_KEY)
								.freezeDefault(false)
								.treasury(TOKEN_TREASURY)
								.payingWith(TOKEN_TREASURY)
								.via(BASE_TXN),
						tokenAssociate(CIVILIAN_ACCT, UNIQUE_TOKEN)
				).when(
						tokenFreeze(UNIQUE_TOKEN, CIVILIAN_ACCT)
								.blankMemo()
								.signedBy(TOKEN_TREASURY)
								.payingWith(TOKEN_TREASURY)
								.via("freeze"),
						tokenUnfreeze(UNIQUE_TOKEN, CIVILIAN_ACCT)
								.blankMemo()
								.payingWith(TOKEN_TREASURY)
								.signedBy(TOKEN_TREASURY)
								.via("unfreeze")
				).then(
						validateChargedUsdWithin("freeze", EXPECTED_FREEZE_PRICE_USD, ALLOWED_DIFFERENCE_PERCENTAGE),
						validateChargedUsdWithin("unfreeze", EXPECTED_UNFREEZE_PRICE_USD, ALLOWED_DIFFERENCE_PERCENTAGE)
				);
	}
	private HapiApiSpec baseCommonFreezeUnfreezeChargedAsExpected() {
		return defaultHapiSpec("baseCommonFreezeUnfreezeChargedAsExpected")
				.given(
						newKeyNamed(TREASURE_KEY),
						newKeyNamed(ADMIN_KEY),
						newKeyNamed(FREEZE_KEY),
						newKeyNamed(SUPPLY_KEY),
						newKeyNamed(WIPE_KEY),
						cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS).key(TREASURE_KEY),
						cryptoCreate(CIVILIAN_ACCT),
						tokenCreate(FUNGIBLE_COMMON_TOKEN)
								.adminKey(ADMIN_KEY)
								.freezeKey(TOKEN_TREASURY)
								.wipeKey(WIPE_KEY)
								.supplyKey(SUPPLY_KEY)
								.freezeDefault(false)
								.treasury(TOKEN_TREASURY)
								.payingWith(TOKEN_TREASURY),
						tokenAssociate(CIVILIAN_ACCT, FUNGIBLE_COMMON_TOKEN)
				).when(
						tokenFreeze(FUNGIBLE_COMMON_TOKEN, CIVILIAN_ACCT)
								.blankMemo()
								.signedBy(TOKEN_TREASURY)
								.payingWith(TOKEN_TREASURY)
								.via("freeze"),
						tokenUnfreeze(FUNGIBLE_COMMON_TOKEN, CIVILIAN_ACCT)
								.blankMemo()
								.payingWith(TOKEN_TREASURY)
								.signedBy(TOKEN_TREASURY)
								.via("unfreeze")
				).then(
						validateChargedUsdWithin("freeze", EXPECTED_FREEZE_PRICE_USD, ALLOWED_DIFFERENCE_PERCENTAGE),
						validateChargedUsdWithin("unfreeze", EXPECTED_UNFREEZE_PRICE_USD, ALLOWED_DIFFERENCE_PERCENTAGE)
				);
	}


	private HapiApiSpec baseTokenGrantRevokeKycChargedAsExpected() {
		return defaultHapiSpec("baseGrantKycChargedAsExpected")
				.given(
						newKeyNamed(TREASURE_KEY),
						newKeyNamed(ADMIN_KEY),
						newKeyNamed(KYC_KEY),
						cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS).key(TREASURE_KEY),
						cryptoCreate(CIVILIAN_ACCT),
						tokenCreate(FUNGIBLE_COMMON_TOKEN)
								.kycKey(TREASURE_KEY)
								.treasury(TOKEN_TREASURY)
				).when(
						grantTokenKyc(FUNGIBLE_COMMON_TOKEN, TOKEN_TREASURY)
								.payingWith(TOKEN_TREASURY)
								.blankMemo()
								.via("grantKycTxn"),
						revokeTokenKyc(FUNGIBLE_COMMON_TOKEN, TOKEN_TREASURY)
								.payingWith(TOKEN_TREASURY)
								.blankMemo()
								.via("revokeKycTxn")
				).then(
						validateChargedUsdWithin("grantKycTxn", EXPECTED_TOKEN_GRANTKYC_PRICE_USD, ALLOWED_DIFFERENCE_PERCENTAGE),
						validateChargedUsdWithin("revokeKycTxn", EXPECTED_TOKEN_REVOKEKYC_PRICE_USD, ALLOWED_DIFFERENCE_PERCENTAGE)
				);
	}

	public HapiApiSpec baseTokenAssociateDissociateCharedAsExpected() {
		return defaultHapiSpec("baseTokenAssociateDissociateCharedAsExpected")
				.given(
						newKeyNamed(TREASURE_KEY),
						cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS).key(TREASURE_KEY),
						cryptoCreate(CIVILIAN_ACCT)
								.key(TREASURE_KEY)
								.autoRenewSecs(THREE_MONTHS_IN_SECONDS),

						tokenCreate(COMMON_TOKEN)
								.treasury(TOKEN_TREASURY)
								.payingWith(TOKEN_TREASURY)
				).when(
						tokenAssociate(CIVILIAN_ACCT, COMMON_TOKEN)
								.payingWith(TOKEN_TREASURY)
								.blankMemo()
								.via("tokenAssociateTxn"),
						tokenDissociate(CIVILIAN_ACCT, COMMON_TOKEN)
								.payingWith(TOKEN_TREASURY)
								.blankMemo()
								.via("tokenDissociateTxn")
				).then(
						validateChargedUsdWithin("tokenAssociateTxn", EXPECTED_TOKEN_ASSOCIATE_PRICE_USD, ALLOWED_DIFFERENCE_PERCENTAGE),
						validateChargedUsdWithin("tokenDissociateTxn", EXPECTED_TOKEN_DISSOCIATE_PRICE_USD, ALLOWED_DIFFERENCE_PERCENTAGE)
				);
	}


	private HapiApiSpec baseTokenDeleteIsChargedExpectedFee() {
		final var uniqueToken = "nftToken";
		final var supplyKey = "supplyKey";
		final var adminKey = "adminKey";
		final var civilianPayer = "civilian";
		final var baseTxn = "baseTxn";
		final var expectedNftDeletePriceUsd = 0.001;

		return defaultHapiSpec("baseTokenDeleteIsChargedExpectedFee")
				.given(
						newKeyNamed(supplyKey),
						newKeyNamed(adminKey),
						cryptoCreate(civilianPayer).key(adminKey).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(uniqueToken)
								.initialSupply(0)
								.supplyKey(supplyKey)
								.adminKey(adminKey)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(civilianPayer)
				)
				.when(
						tokenDelete(uniqueToken)
								.payingWith(civilianPayer)
								.signedBy(civilianPayer)
								.blankMemo()
								.via(baseTxn)
				).then(
						validateChargedUsdWithin(baseTxn, expectedNftDeletePriceUsd, 0.01)
				);
	}

	private HapiApiSpec baseTokeUpdateIsChargedExpectedFee() {
		final var updateTxn = "baseUpdateTxn";
		final var tokenToUpdate = "tokenToUpdate";
		final String A_TOKEN_NAME = "012345678912";
		final String A_TOKEN_SYMBOL = "ABCD";
		final var adminKey = "adminKey";
		final var targetExpiry = Instant.now().getEpochSecond() +  THREE_MONTHS_IN_SECONDS;
		final var baseExpiry = Instant.now().getEpochSecond() +  86400;
		final var civilianPayer = "civilian";
		final var expectedTokenUpdatePriceUsd = 0.001;
		return defaultHapiSpec("baseTokeUpdateIsChargedExpectedFee")
				.given(
						newKeyNamed(adminKey),
						cryptoCreate(civilianPayer),
						cryptoCreate(TOKEN_TREASURY).key(adminKey),
						tokenCreate(tokenToUpdate)
								.initialSupply(1000)
								.payingWith(TOKEN_TREASURY)
								.name(A_TOKEN_NAME)
								.symbol(A_TOKEN_SYMBOL)
								.blankMemo()
								.expiry(baseExpiry)
								.treasury(TOKEN_TREASURY)
				).when(
						tokenUpdate(tokenToUpdate)
								.expiry(targetExpiry)
								.blankMemo()
								.payingWith(TOKEN_TREASURY)
								.via(updateTxn)
				).then(
						validateChargedUsdWithin(updateTxn, expectedTokenUpdatePriceUsd, 0.01)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

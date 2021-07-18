package com.hedera.services.bdd.suites.token;

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
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountNftInfos;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.queries.token.HapiTokenNftInfo.newTokenNftInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fractionalFee;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingHbar;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;

public class TokenTransactSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TokenTransactSpecs.class);

	private static final long TOTAL_SUPPLY = 1_000;
	private static final String A_TOKEN = "TokenA";
	private static final String B_TOKEN = "TokenB";
	private static final String FIRST_USER = "Client1";
	private static final String SECOND_USER = "Client2";
	private static final String TOKEN_TREASURY = "treasury";

	public static void main(String... args) {
		new TokenTransactSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						balancesChangeOnTokenTransfer(),
						accountsMustBeExplicitlyUnfrozenOnlyIfDefaultFreezeIsTrue(),
						senderSigsAreValid(),
						balancesAreChecked(),
						duplicateAccountsInTokenTransferRejected(),
						tokenOnlyTxnsAreAtomic(),
						tokenPlusHbarTxnsAreAtomic(),
						nonZeroTransfersRejected(),
						prechecksWork(),
						missingEntitiesRejected(),
						allRequiredSigsAreChecked(),
						uniqueTokenTxnAccountBalance(),
						uniqueTokenTxnWithNoAssociation(),
						uniqueTokenTxnWithFrozenAccount(),
						uniqueTokenTxnWithSenderNotSigned(),
						uniqueTokenTxnWithReceiverNotSigned(),
						uniqueTokenTxnsAreAtomic(),
						uniqueTokenDeletedTxn(),
						cannotSendFungibleToDissociatedContractsOrAccounts(),
						cannotGiveNftsToDissociatedContractsOrAccounts(),
						recordsIncludeBothFungibleTokenChangesAndOwnershipChange(),
						transferListsEnforceTokenTypeRestrictions(),
						/* HIP-18 charging case studies */
						fixedHbarCaseStudy(),
						fractionalCaseStudy(),
						simpleHtsFeeCaseStudy(),
						nestedHbarCaseStudy(),
						nestedFractionalCaseStudy(),
						nestedHtsCaseStudy(),
				}
		);
	}

	public HapiApiSpec transferListsEnforceTokenTypeRestrictions() {
		final var theAccount = "anybody";
		final var B_TOKEN = "non-fungible";
		final var theKey = "multipurpose";
		return defaultHapiSpec("TransferListsEnforceTokenTypeRestrictions")
				.given(
						newKeyNamed(theKey),
						cryptoCreate(theAccount),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(A_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(1000L)
								.treasury(TOKEN_TREASURY),
						tokenCreate(B_TOKEN)
								.supplyKey(theKey)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0L)
								.treasury(TOKEN_TREASURY)
				).when(
						mintToken(B_TOKEN, List.of(ByteString.copyFromUtf8("dark"))),
						tokenAssociate(theAccount, List.of(A_TOKEN, B_TOKEN))
				).then(
						cryptoTransfer(
								movingUnique(1, A_TOKEN).between(TOKEN_TREASURY, theAccount)
						).hasKnownStatus(INVALID_NFT_ID),
						cryptoTransfer(
								moving(1, B_TOKEN).between(TOKEN_TREASURY, theAccount)
						).hasKnownStatus(ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON)
				);
	}

	public HapiApiSpec recordsIncludeBothFungibleTokenChangesAndOwnershipChange() {
		final var theUniqueToken = "special";
		final var theCommonToken = "quotidian";
		final var theAccount = "lucky";
		final var theKey = "multipurpose";
		final var theTxn = "diverseXfer";

		return defaultHapiSpec("RecordsIncludeBothFungibleTokenChangesAndOwnershipChange")
				.given(
						newKeyNamed(theKey),
						cryptoCreate(theAccount),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(theCommonToken)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(1_234_567L)
								.treasury(TOKEN_TREASURY),
						tokenCreate(theUniqueToken)
								.supplyKey(theKey)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0L)
								.treasury(TOKEN_TREASURY),
						mintToken(theUniqueToken, List.of(ByteString.copyFromUtf8("Doesn't matter"))),
						tokenAssociate(theAccount, theUniqueToken),
						tokenAssociate(theAccount, theCommonToken)
				).when(
						cryptoTransfer(
								moving(1, theCommonToken).between(TOKEN_TREASURY, theAccount),
								movingUnique(1, theUniqueToken).between(TOKEN_TREASURY, theAccount)
						).via(theTxn)
				).then(
						getTxnRecord(theTxn).logged()
				);
	}

	public HapiApiSpec cannotGiveNftsToDissociatedContractsOrAccounts() {
		final var theContract = "tbd";
		final var theAccount = "alsoTbd";
		final var theKey = "multipurpose";
		return defaultHapiSpec("CannotGiveNftsToDissociatedContractsOrAccounts")
				.given(
						newKeyNamed(theKey),
						contractCreate(theContract),
						cryptoCreate(theAccount),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(A_TOKEN)
								.supplyKey(theKey)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0L)
								.treasury(TOKEN_TREASURY),
						tokenAssociate(theContract, A_TOKEN),
						tokenAssociate(theAccount, A_TOKEN),
						mintToken(A_TOKEN, List.of(ByteString.copyFromUtf8("dark"), ByteString.copyFromUtf8("matter")))
				).when(
						getContractInfo(theContract).hasToken(relationshipWith(A_TOKEN)),
						getAccountInfo(theAccount).hasToken(relationshipWith(A_TOKEN)),
						tokenDissociate(theContract, A_TOKEN),
						tokenDissociate(theAccount, A_TOKEN),
						getContractInfo(theContract).hasNoTokenRelationship(A_TOKEN),
						getAccountInfo(theAccount).hasNoTokenRelationship(A_TOKEN)
				).then(
						cryptoTransfer(
								movingUnique(1, A_TOKEN).between(TOKEN_TREASURY, theContract)
						).hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
						cryptoTransfer(
								movingUnique(1, A_TOKEN).between(TOKEN_TREASURY, theAccount)
						).hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
						tokenAssociate(theContract, A_TOKEN),
						tokenAssociate(theAccount, A_TOKEN),
						cryptoTransfer(movingUnique(1, A_TOKEN).between(TOKEN_TREASURY, theContract)),
						cryptoTransfer(movingUnique(2, A_TOKEN).between(TOKEN_TREASURY, theAccount)),
						getAccountBalance(theAccount).hasTokenBalance(A_TOKEN, 1),
						getAccountBalance(theContract).hasTokenBalance(A_TOKEN, 1),
						getAccountNftInfos(theAccount, 0, 1)
								.hasNfts(
										newTokenNftInfo(A_TOKEN,
												2, theAccount, ByteString.copyFromUtf8("matter"))),
						getAccountNftInfos(theContract, 0, 1)
								.hasNfts(
										newTokenNftInfo(A_TOKEN,
												1, theContract, ByteString.copyFromUtf8("dark")))
				);
	}

	public HapiApiSpec cannotSendFungibleToDissociatedContractsOrAccounts() {
		final var theContract = "tbd";
		final var theAccount = "alsoTbd";
		return defaultHapiSpec("CannotSendFungibleToDissociatedContract")
				.given(
						contractCreate(theContract),
						cryptoCreate(theAccount),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(A_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(1_234_567L)
								.treasury(TOKEN_TREASURY),
						tokenAssociate(theContract, A_TOKEN),
						tokenAssociate(theAccount, A_TOKEN)
				).when(
						getContractInfo(theContract).hasToken(relationshipWith(A_TOKEN)),
						getAccountInfo(theAccount).hasToken(relationshipWith(A_TOKEN)),
						tokenDissociate(theContract, A_TOKEN),
						tokenDissociate(theAccount, A_TOKEN),
						getContractInfo(theContract).hasNoTokenRelationship(A_TOKEN),
						getAccountInfo(theAccount).hasNoTokenRelationship(A_TOKEN)
				).then(
						cryptoTransfer(
								moving(1, A_TOKEN).between(TOKEN_TREASURY, theContract)
						).hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
						cryptoTransfer(
								moving(1, A_TOKEN).between(TOKEN_TREASURY, theAccount)
						).hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
						tokenAssociate(theContract, A_TOKEN),
						tokenAssociate(theAccount, A_TOKEN),
						cryptoTransfer(moving(1, A_TOKEN).between(TOKEN_TREASURY, theContract)),
						cryptoTransfer(moving(1, A_TOKEN).between(TOKEN_TREASURY, theAccount)),
						getAccountBalance(theAccount).hasTokenBalance(A_TOKEN, 1L),
						getAccountBalance(theContract).hasTokenBalance(A_TOKEN, 1L)
				);
	}

	private HapiApiSpec prechecksWork() {
		return defaultHapiSpec("PrechecksWork")
				.given(
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						cryptoCreate(FIRST_USER).balance(0L)
				).when(
						tokenCreate(A_TOKEN)
								.initialSupply(100)
								.treasury(TOKEN_TREASURY),
						tokenCreate(B_TOKEN)
								.initialSupply(100)
								.treasury(TOKEN_TREASURY)
				).then(
						cryptoTransfer(
								moving(1, A_TOKEN)
										.between(TOKEN_TREASURY, FIRST_USER),
								moving(1, A_TOKEN)
										.between(TOKEN_TREASURY, FIRST_USER)
						).hasPrecheck(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
						fileUpdate(APP_PROPERTIES).overridingProps(Map.of(
								"ledger.tokenTransfers.maxLen", "" + 2
						)).payingWith(ADDRESS_BOOK_CONTROL),
						cryptoTransfer(
								moving(1, A_TOKEN)
										.between(TOKEN_TREASURY, FIRST_USER),
								moving(1, B_TOKEN)
										.between(TOKEN_TREASURY, FIRST_USER)
						).hasPrecheck(TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED),
						fileUpdate(APP_PROPERTIES).overridingProps(Map.of(
								"ledger.tokenTransfers.maxLen", "" + 10
						)).payingWith(ADDRESS_BOOK_CONTROL),
						cryptoTransfer(
								movingHbar(1)
										.between(TOKEN_TREASURY, FIRST_USER),
								movingHbar(1)
										.between(TOKEN_TREASURY, FIRST_USER)
						).hasPrecheck(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
						cryptoTransfer(
								moving(1, A_TOKEN)
										.between(TOKEN_TREASURY, FIRST_USER),
								moving(1, A_TOKEN)
										.between(TOKEN_TREASURY, FIRST_USER)
						).hasPrecheck(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
						cryptoTransfer(
								moving(0, A_TOKEN)
										.between(TOKEN_TREASURY, FIRST_USER)
						).hasPrecheck(INVALID_ACCOUNT_AMOUNTS),
						cryptoTransfer(
								moving(10, A_TOKEN)
										.from(TOKEN_TREASURY)
						).hasPrecheck(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN),
						cryptoTransfer(
								moving(10, A_TOKEN)
										.empty()
						).hasPrecheck(EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS)
				);
	}

	public HapiApiSpec missingEntitiesRejected() {
		return defaultHapiSpec("MissingTokensRejected")
				.given(
						tokenCreate("some").treasury(DEFAULT_PAYER)
				).when().then(
						cryptoTransfer(
								moving(1L, "some")
										.between(DEFAULT_PAYER, "0.0.0")
						).signedBy(DEFAULT_PAYER).hasKnownStatus(INVALID_ACCOUNT_ID),
						cryptoTransfer(
								moving(100_000_000_000_000L, "0.0.0")
										.between(DEFAULT_PAYER, FUNDING)
						).signedBy(DEFAULT_PAYER).hasKnownStatus(INVALID_TOKEN_ID)
				);
	}

	public HapiApiSpec balancesAreChecked() {
		return defaultHapiSpec("BalancesAreChecked")
				.given(
						cryptoCreate("payer"),
						cryptoCreate("firstTreasury"),
						cryptoCreate("secondTreasury"),
						cryptoCreate("beneficiary")
				).when(
						tokenCreate(A_TOKEN)
								.initialSupply(100)
								.treasury("firstTreasury"),
						tokenAssociate("beneficiary", A_TOKEN)
				).then(
						cryptoTransfer(
								moving(100_000_000_000_000L, A_TOKEN)
										.between("firstTreasury", "beneficiary")
						).payingWith("payer")
								.signedBy("payer", "firstTreasury")
								.fee(ONE_HUNDRED_HBARS)
								.hasKnownStatus(INSUFFICIENT_TOKEN_BALANCE),
						cryptoTransfer(
								moving(1, A_TOKEN).between("firstTreasury", "beneficiary"),
								movingHbar(ONE_HUNDRED_HBARS).between("firstTreasury", "beneficiary")
						).payingWith("payer")
								.signedBy("payer", "firstTreasury")
								.fee(ONE_HUNDRED_HBARS)
								.hasKnownStatus(INSUFFICIENT_ACCOUNT_BALANCE)
				);
	}

	public HapiApiSpec accountsMustBeExplicitlyUnfrozenOnlyIfDefaultFreezeIsTrue() {
		return defaultHapiSpec("AccountsMustBeExplicitlyUnfrozenOnlyIfDefaultFreezeIsTrue")
				.given(
						cryptoCreate("randomBeneficiary").balance(0L),
						cryptoCreate("treasury").balance(0L),
						cryptoCreate("payer"),
						newKeyNamed("freezeKey")
				).when(
						tokenCreate(A_TOKEN)
								.treasury("treasury")
								.freezeKey("freezeKey")
								.freezeDefault(true),
						tokenAssociate("randomBeneficiary", A_TOKEN),
						cryptoTransfer(
								moving(100, A_TOKEN)
										.between("treasury", "randomBeneficiary")
						).hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN),
						/* and */
						tokenCreate(B_TOKEN)
								.treasury("treasury")
								.freezeDefault(false),
						tokenAssociate("randomBeneficiary", B_TOKEN),
						cryptoTransfer(
								moving(100, B_TOKEN)
										.between("treasury", "randomBeneficiary")
						).payingWith("payer").via("successfulTransfer")
				).then(
						getAccountBalance("randomBeneficiary")
								.logged()
								.hasTokenBalance(B_TOKEN, 100),
						getTxnRecord("successfulTransfer").logged()
				);
	}

	public HapiApiSpec allRequiredSigsAreChecked() {
		return defaultHapiSpec("AllRequiredSigsAreChecked")
				.given(
						cryptoCreate("payer"),
						cryptoCreate("firstTreasury").balance(0L),
						cryptoCreate("secondTreasury").balance(0L),
						cryptoCreate("sponsor"),
						cryptoCreate("beneficiary").receiverSigRequired(true)
				).when(
						tokenCreate(A_TOKEN)
								.initialSupply(123)
								.treasury("firstTreasury"),
						tokenCreate(B_TOKEN)
								.initialSupply(234)
								.treasury("secondTreasury"),
						tokenAssociate("beneficiary", A_TOKEN, B_TOKEN)
				).then(
						cryptoTransfer(
								moving(100, A_TOKEN).between("firstTreasury", "beneficiary"),
								moving(100, B_TOKEN).between("secondTreasury", "beneficiary"),
								movingHbar(1_000).between("sponsor", "firstTreasury")
						).payingWith("payer")
								.signedBy("payer", "firstTreasury", "beneficiary", "sponsor")
								.fee(ONE_HUNDRED_HBARS)
								.hasKnownStatus(INVALID_SIGNATURE),
						cryptoTransfer(
								moving(100, A_TOKEN).between("firstTreasury", "beneficiary"),
								moving(100, B_TOKEN).between("secondTreasury", "beneficiary"),
								movingHbar(1_000).between("sponsor", "firstTreasury")
						).payingWith("payer")
								.signedBy("payer", "firstTreasury", "secondTreasury", "sponsor")
								.fee(ONE_HUNDRED_HBARS)
								.hasKnownStatus(INVALID_SIGNATURE),
						cryptoTransfer(
								moving(100, A_TOKEN).between("firstTreasury", "beneficiary"),
								moving(100, B_TOKEN).between("secondTreasury", "beneficiary"),
								movingHbar(1_000).between("sponsor", "firstTreasury")
						).payingWith("payer")
								.fee(ONE_HUNDRED_HBARS)
								.signedBy("payer", "firstTreasury", "secondTreasury", "beneficiary")
								.hasKnownStatus(INVALID_SIGNATURE),
						cryptoTransfer(
								moving(100, A_TOKEN).between("firstTreasury", "beneficiary"),
								moving(100, B_TOKEN).between("secondTreasury", "beneficiary"),
								movingHbar(1_000).between("sponsor", "firstTreasury"))

								.fee(ONE_HUNDRED_HBARS)
								.payingWith("payer")

				);
	}

	public HapiApiSpec senderSigsAreValid() {
		return defaultHapiSpec("SenderSigsAreValid")
				.given(
						cryptoCreate("payer"),
						cryptoCreate("firstTreasury").balance(0L),
						cryptoCreate("secondTreasury").balance(0L),
						cryptoCreate("beneficiary")
				).when(
						tokenCreate(A_TOKEN)
								.initialSupply(123)
								.treasury("firstTreasury"),
						tokenCreate(B_TOKEN)
								.initialSupply(234)
								.treasury("secondTreasury"),
						tokenAssociate("beneficiary", A_TOKEN, B_TOKEN),
						balanceSnapshot("treasuryBefore", "firstTreasury"),
						balanceSnapshot("beneBefore", "beneficiary")
				).then(
						cryptoTransfer(
								moving(100, A_TOKEN).between("firstTreasury", "beneficiary"),
								movingHbar(ONE_HBAR).between("beneficiary", "firstTreasury")
						).payingWith("payer")
								.signedBy("firstTreasury", "payer", "beneficiary")
								.fee(ONE_HUNDRED_HBARS)
								.via("transactTxn"),
						getAccountBalance("firstTreasury")
								.hasTinyBars(changeFromSnapshot("treasuryBefore", +1 * ONE_HBAR))
								.hasTokenBalance(A_TOKEN, 23),
						getAccountBalance("beneficiary")
								.hasTinyBars(changeFromSnapshot("beneBefore", -1 * ONE_HBAR))
								.hasTokenBalance(A_TOKEN, 100),
						getTxnRecord("transactTxn").logged()
				);
	}

	public HapiApiSpec tokenPlusHbarTxnsAreAtomic() {
		return defaultHapiSpec("TokenPlusHbarTxnsAreAtomic")
				.given(
						cryptoCreate("payer"),
						cryptoCreate("firstTreasury").balance(0L),
						cryptoCreate("secondTreasury").balance(0L),
						cryptoCreate("beneficiary"),
						cryptoCreate("tbd").balance(0L)
				).when(
						cryptoDelete("tbd"),
						tokenCreate(A_TOKEN)
								.initialSupply(123)
								.treasury("firstTreasury"),
						tokenCreate(B_TOKEN)
								.initialSupply(50)
								.treasury("secondTreasury"),
						tokenAssociate("beneficiary", A_TOKEN, B_TOKEN),
						balanceSnapshot("before", "beneficiary"),
						cryptoTransfer(
								moving(100, A_TOKEN).between("firstTreasury", "beneficiary"),
								moving(10, B_TOKEN).between("secondTreasury", "beneficiary"),
								movingHbar(1).between("beneficiary", "tbd"))
								.fee(ONE_HUNDRED_HBARS)
								.hasKnownStatus(ACCOUNT_DELETED)

				).then(
						getAccountBalance("firstTreasury")
								.logged()
								.hasTokenBalance(A_TOKEN, 123),
						getAccountBalance("secondTreasury")
								.logged()
								.hasTokenBalance(B_TOKEN, 50),
						getAccountBalance("beneficiary")
								.logged()
								.hasTinyBars(changeFromSnapshot("before", 0L))
				);
	}

	public HapiApiSpec tokenOnlyTxnsAreAtomic() {
		return defaultHapiSpec("TokenOnlyTxnsAreAtomic")
				.given(
						cryptoCreate("payer"),
						cryptoCreate("firstTreasury").balance(0L),
						cryptoCreate("secondTreasury").balance(0L),
						cryptoCreate("beneficiary")
				).when(
						tokenCreate(A_TOKEN)
								.initialSupply(123)
								.treasury("firstTreasury"),
						tokenCreate(B_TOKEN)
								.initialSupply(50)
								.treasury("secondTreasury"),
						tokenAssociate("beneficiary", A_TOKEN, B_TOKEN),
						cryptoTransfer(
								moving(100, A_TOKEN).between("firstTreasury", "beneficiary"),
								moving(100, B_TOKEN).between("secondTreasury", "beneficiary")
						).hasKnownStatus(INSUFFICIENT_TOKEN_BALANCE)
				).then(
						getAccountBalance("firstTreasury")
								.logged()
								.hasTokenBalance(A_TOKEN, 123),
						getAccountBalance("secondTreasury")
								.logged()
								.hasTokenBalance(B_TOKEN, 50),
						getAccountBalance("beneficiary").logged()
				);
	}

	public HapiApiSpec duplicateAccountsInTokenTransferRejected() {
		return defaultHapiSpec("DuplicateAccountsInTokenTransferRejected")
				.given(
						cryptoCreate("firstTreasury").balance(0L),
						cryptoCreate("beneficiary").balance(0L)
				).when(
						tokenCreate(A_TOKEN)
				).then(
						cryptoTransfer(
								moving(1, A_TOKEN).between("firstTreasury", "beneficiary"),
								moving(1, A_TOKEN).from("firstTreasury")
						).hasPrecheck(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS)
				);
	}

	public HapiApiSpec nonZeroTransfersRejected() {
		return defaultHapiSpec("NonZeroTransfersRejected")
				.given(
						cryptoCreate("firstTreasury").balance(0L)
				).when(
						tokenCreate(A_TOKEN)
				).then(
						cryptoTransfer(
								moving(1, A_TOKEN).from("firstTreasury")
						).hasPrecheck(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN),
						cryptoTransfer(
								movingHbar(1).from("firstTreasury")
						).hasPrecheck(INVALID_ACCOUNT_AMOUNTS)
				);
	}

	public HapiApiSpec balancesChangeOnTokenTransfer() {
		return defaultHapiSpec("BalancesChangeOnTokenTransfer")
				.given(
						cryptoCreate(FIRST_USER).balance(0L),
						cryptoCreate(SECOND_USER).balance(0L),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate(A_TOKEN)
								.initialSupply(TOTAL_SUPPLY)
								.treasury(TOKEN_TREASURY),
						tokenCreate(B_TOKEN)
								.initialSupply(TOTAL_SUPPLY)
								.treasury(TOKEN_TREASURY),
						tokenAssociate(FIRST_USER, A_TOKEN),
						tokenAssociate(SECOND_USER, B_TOKEN)
				).when(
						cryptoTransfer(
								moving(100, A_TOKEN).between(TOKEN_TREASURY, FIRST_USER),
								moving(100, B_TOKEN).between(TOKEN_TREASURY, SECOND_USER)
						)
				).then(
						getAccountBalance(TOKEN_TREASURY)
								.hasTokenBalance(A_TOKEN, TOTAL_SUPPLY - 100)
								.hasTokenBalance(B_TOKEN, TOTAL_SUPPLY - 100),
						getAccountBalance(FIRST_USER)
								.hasTokenBalance(A_TOKEN, 100),
						getAccountBalance(SECOND_USER)
								.hasTokenBalance(B_TOKEN, 100)
				);
	}

	public HapiApiSpec uniqueTokenTxnAccountBalance() {
		return defaultHapiSpec("UniqueTokenTxnAccountBalance")
				.given(
						newKeyNamed("supplyKey"),
						newKeyNamed("signingKeyTreasury"),
						newKeyNamed("signingKeyFirstUser"),
						cryptoCreate(FIRST_USER).key("signingKeyFirstUser"),
						cryptoCreate(TOKEN_TREASURY).key("signingKeyTreasury"),
						tokenCreate(A_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.supplyKey("supplyKey")
								.treasury(TOKEN_TREASURY),
						mintToken(A_TOKEN, List.of(ByteString.copyFromUtf8("memo"))),
						tokenAssociate(FIRST_USER, A_TOKEN)
				).when(
						cryptoTransfer(
								movingUnique(1, A_TOKEN).between(TOKEN_TREASURY, FIRST_USER)
						).signedBy("signingKeyTreasury", "signingKeyFirstUser", DEFAULT_PAYER).via("cryptoTransferTxn")
				).then(
						getAccountBalance(TOKEN_TREASURY)
								.hasTokenBalance(A_TOKEN, 0),
						getAccountBalance(FIRST_USER)
								.hasTokenBalance(A_TOKEN, 1),
						getTokenNftInfo(A_TOKEN, 1)
								.hasSerialNum(1)
								.hasMetadata(ByteString.copyFromUtf8("memo"))
								.hasTokenID(A_TOKEN)
								.hasAccountID(FIRST_USER),
						getAccountNftInfos(FIRST_USER, 0, 1)
								.hasNfts(
										newTokenNftInfo(A_TOKEN, 1, FIRST_USER, ByteString.copyFromUtf8("memo"))),
						getTxnRecord("cryptoTransferTxn").logged()
				);
	}

	public HapiApiSpec uniqueTokenTxnWithNoAssociation() {
		return defaultHapiSpec("UniqueTokenTxnWithNoAssociation")
				.given(
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate(FIRST_USER),
						newKeyNamed("supplyKey"),
						tokenCreate(A_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.supplyKey("supplyKey")
								.treasury(TOKEN_TREASURY)
				)
				.when(
						mintToken(A_TOKEN, List.of(ByteString.copyFromUtf8("memo")))
				)
				.then(
						cryptoTransfer(
								movingUnique(1, A_TOKEN).between(TOKEN_TREASURY, FIRST_USER)

						).hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
						getAccountNftInfos(TOKEN_TREASURY, 0, 1)
								.hasNfts(
										newTokenNftInfo(A_TOKEN, 1, TOKEN_TREASURY,
												ByteString.copyFromUtf8("memo"))
								)
				);
	}

	public HapiApiSpec uniqueTokenTxnWithFrozenAccount() {
		return defaultHapiSpec("UniqueTokenTxnWithFrozenAccount")
				.given(
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						cryptoCreate(FIRST_USER).balance(0L),
						newKeyNamed("freezeKey"),
						newKeyNamed("supplyKey"),
						tokenCreate(A_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.freezeKey("freezeKey")
								.freezeDefault(true)
								.supplyKey("supplyKey")
								.treasury(TOKEN_TREASURY),
						tokenAssociate(FIRST_USER, A_TOKEN)
				)
				.when(
						mintToken(A_TOKEN, List.of(ByteString.copyFromUtf8("memo")))
				)
				.then(
						cryptoTransfer(
								movingUnique(1, A_TOKEN).between(TOKEN_TREASURY, FIRST_USER)
						)
								.hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN)
				);
	}

	public HapiApiSpec uniqueTokenTxnWithSenderNotSigned() {
		return defaultHapiSpec("UniqueTokenTxnWithOwnerNotSigned")
				.given(
						newKeyNamed("supplyKey"),
						newKeyNamed("signingKeyTreasury"),
						cryptoCreate(TOKEN_TREASURY).key("signingKeyTreasury"),
						cryptoCreate(FIRST_USER),
						tokenCreate(A_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.supplyKey("supplyKey")
								.treasury(TOKEN_TREASURY),
						tokenAssociate(FIRST_USER, A_TOKEN)
				)
				.when(
						mintToken(A_TOKEN, List.of(ByteString.copyFromUtf8("memo")))
				)
				.then(
						cryptoTransfer(
								movingUnique(1, A_TOKEN).between(TOKEN_TREASURY, FIRST_USER)
						)
								.signedBy(DEFAULT_PAYER)
								.hasKnownStatus(INVALID_SIGNATURE)
				);
	}

	public HapiApiSpec uniqueTokenTxnWithReceiverNotSigned() {
		return defaultHapiSpec("UniqueTokenTxnWithOwnerNotSigned")
				.given(
						newKeyNamed("supplyKey"),
						newKeyNamed("signingKeyTreasury"),
						newKeyNamed("signingKeyFirstUser"),
						cryptoCreate(TOKEN_TREASURY).key("signingKeyTreasury"),
						cryptoCreate(FIRST_USER).key("signingKeyFirstUser").receiverSigRequired(true),
						tokenCreate(A_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.supplyKey("supplyKey")
								.treasury(TOKEN_TREASURY),
						tokenAssociate(FIRST_USER, A_TOKEN)
				)
				.when(
						mintToken(A_TOKEN, List.of(ByteString.copyFromUtf8("memo")))
				)
				.then(
						cryptoTransfer(
								movingUnique(1, A_TOKEN).between(TOKEN_TREASURY, FIRST_USER)
						)
								.signedBy("signingKeyTreasury", DEFAULT_PAYER)
								.hasKnownStatus(INVALID_SIGNATURE)
				);
	}

	public HapiApiSpec uniqueTokenTxnsAreAtomic() {
		return defaultHapiSpec("UniqueTokenTxnsAreAtomic")
				.given(
						newKeyNamed("supplyKey"),
						newKeyNamed("signingKeyTreasury"),
						newKeyNamed("signingKeyFirstUser"),
						cryptoCreate(FIRST_USER).key("signingKeyFirstUser"),
						cryptoCreate(SECOND_USER),
						cryptoCreate(TOKEN_TREASURY).key("signingKeyTreasury"),
						tokenCreate(A_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.supplyKey("supplyKey")
								.treasury(TOKEN_TREASURY),
						tokenCreate(B_TOKEN)
								.initialSupply(100)
								.treasury(TOKEN_TREASURY),
						mintToken(A_TOKEN, List.of(ByteString.copyFromUtf8("memo"))),
						tokenAssociate(FIRST_USER, A_TOKEN),
						tokenAssociate(FIRST_USER, B_TOKEN),
						tokenAssociate(SECOND_USER, A_TOKEN)
				)
				.when(
						cryptoTransfer(
								movingUnique(1, A_TOKEN).between(TOKEN_TREASURY, SECOND_USER),
								moving(101, B_TOKEN).between(TOKEN_TREASURY, FIRST_USER)
						)
								.hasKnownStatus(INSUFFICIENT_TOKEN_BALANCE)
				)
				.then(
						getAccountBalance(TOKEN_TREASURY)
								.hasTokenBalance(A_TOKEN, 1),
						getAccountBalance(TOKEN_TREASURY)
								.hasTokenBalance(B_TOKEN, 100),
						getAccountBalance(FIRST_USER)
								.hasTokenBalance(A_TOKEN, 0),
						getAccountBalance(SECOND_USER)
								.hasTokenBalance(A_TOKEN, 0)
				);
	}

	public HapiApiSpec uniqueTokenDeletedTxn() {
		return defaultHapiSpec("UniqueTokenDeletedTxn")
				.given(
						newKeyNamed("supplyKey"),
						newKeyNamed("nftAdmin"),
						newKeyNamed("signingKeyTreasury"),
						newKeyNamed("signingKeyFirstUser"),
						cryptoCreate(FIRST_USER).key("signingKeyFirstUser"),
						cryptoCreate(TOKEN_TREASURY).key("signingKeyTreasury"),
						tokenCreate(A_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.supplyKey("supplyKey")
								.adminKey("nftAdmin")
								.treasury(TOKEN_TREASURY),
						mintToken(A_TOKEN, List.of(ByteString.copyFromUtf8("memo"))),
						tokenAssociate(FIRST_USER, A_TOKEN)
				).when(
						tokenDelete(A_TOKEN)
				).then(
						cryptoTransfer(
								movingUnique(1, A_TOKEN).between(TOKEN_TREASURY, FIRST_USER)
						)
								.signedBy("signingKeyTreasury", "signingKeyFirstUser", DEFAULT_PAYER)
								.hasKnownStatus(TOKEN_WAS_DELETED)
				);
	}

	public HapiApiSpec fixedHbarCaseStudy() {
		final var alice = "Alice";
		final var bob = "Bob";
		final var tokenWithHbarFee = "TokenWithHbarFee";
		final var treasuryForToken = "TokenTreasury";
		final var supplyKey = "antique";

		final var txnFromTreasury = "txnFromTreasury";
		final var txnFromAlice = "txnFromAlice";

		return defaultHapiSpec("FixedHbarCaseStudy")
				.given(
						newKeyNamed(supplyKey),
						cryptoCreate(alice),
						cryptoCreate(bob),
						cryptoCreate(treasuryForToken),
						tokenCreate(tokenWithHbarFee)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyKey(supplyKey)
								.initialSupply(0L)
								.treasury(treasuryForToken)
								.withCustom(fixedHbarFee(ONE_HBAR, treasuryForToken)),
						mintToken(tokenWithHbarFee, List.of(ByteString.copyFromUtf8("First!"))),
						tokenAssociate(alice, tokenWithHbarFee),
						tokenAssociate(bob, tokenWithHbarFee),
						cryptoTransfer(movingUnique(1L, tokenWithHbarFee).between(treasuryForToken, alice))
								.payingWith(treasuryForToken)
								.fee(ONE_HBAR)
								.via(txnFromTreasury)
				).when(
						cryptoTransfer(
								movingUnique(1L, tokenWithHbarFee).between(alice, bob)
						)
								.payingWith(alice)
								.fee(ONE_HBAR)
								.via(txnFromAlice)
				).then(
						getTxnRecord(txnFromTreasury).logged(),
						getTxnRecord(txnFromAlice).logged()
						/* TODO - validate balances */
				);
	}

	public HapiApiSpec fractionalCaseStudy() {
		final var alice = "Alice";
		final var bob = "Bob";
		final var tokenWithFractionalFee = "TokenWithFractionalFee";
		final var treasuryForToken = "TokenTreasury";

		final var txnFromTreasury = "txnFromTreasury";
		final var txnFromBob = "txnFromBob";

		return defaultHapiSpec("FractionalCaseStudy")
				.given(
						cryptoCreate(alice),
						cryptoCreate(bob),
						cryptoCreate(treasuryForToken),
						tokenCreate(tokenWithFractionalFee)
								.initialSupply(Long.MAX_VALUE)
								.treasury(treasuryForToken)
								.withCustom(fractionalFee(1, 100, 1L, OptionalLong.of(5L), treasuryForToken)),
						tokenAssociate(alice, tokenWithFractionalFee),
						tokenAssociate(bob, tokenWithFractionalFee),
						cryptoTransfer(moving(1_000_000L, tokenWithFractionalFee).between(treasuryForToken, bob))
								.payingWith(treasuryForToken)
								.fee(ONE_HBAR)
								.via(txnFromTreasury)
				).when(
						cryptoTransfer(
								moving(1_000L, tokenWithFractionalFee).between(bob, alice)
						)
								.payingWith(bob)
								.fee(ONE_HBAR)
								.via(txnFromBob)
				).then(
						getTxnRecord(txnFromTreasury).logged(),
						getTxnRecord(txnFromBob).logged()
						/* TODO - validate balances */
				);
	}

	public HapiApiSpec simpleHtsFeeCaseStudy() {
		final var claire = "Claire";
		final var debbie = "Debbie";
		final var simpleHtsFeeToken = "SimpleHtsFeeToken";
		final var commissionPaymentToken = "commissionPaymentToken";
		final var treasuryForToken = "TokenTreasury";

		final var txnFromTreasury = "txnFromTreasury";
		final var txnFromClaire = "txnFromClaire";

		return defaultHapiSpec("FractionalCaseStudy")
				.given(
						cryptoCreate(claire),
						cryptoCreate(debbie),
						cryptoCreate(treasuryForToken),
						tokenCreate(commissionPaymentToken)
								.initialSupply(Long.MAX_VALUE)
								.treasury(treasuryForToken),
						tokenCreate(simpleHtsFeeToken)
								.initialSupply(Long.MAX_VALUE)
								.treasury(treasuryForToken)
								.withCustom(fixedHtsFee(2, commissionPaymentToken, treasuryForToken)),
						tokenAssociate(claire, List.of(simpleHtsFeeToken, commissionPaymentToken)),
						tokenAssociate(debbie, simpleHtsFeeToken),
						cryptoTransfer(
								moving(1_000L, commissionPaymentToken).between(treasuryForToken, claire),
								moving(1_000L, simpleHtsFeeToken).between(treasuryForToken, claire)
						)
								.payingWith(treasuryForToken)
								.fee(ONE_HBAR)
								.via(txnFromTreasury)
				).when(
						cryptoTransfer(
								moving(100L, simpleHtsFeeToken).between(claire, debbie)
						)
								.payingWith(claire)
								.fee(ONE_HBAR)
								.via(txnFromClaire)
				).then(
						getTxnRecord(txnFromTreasury).logged(),
						getTxnRecord(txnFromClaire).logged()
						/* TODO - validate balances */
				);
	}

	public HapiApiSpec nestedHbarCaseStudy() {
		final var debbie = "Debbie";
		final var edgar = "Edgar";
		final var tokenWithHbarFee = "TokenWithHbarFee";
		final var tokenWithNestedFee = "TokenWithNestedFee";
		final var treasuryForTopLevelCollection = "TokenTreasury";
		final var treasuryForNestedCollection = "NestedTokenTreasury";

		final var txnFromTreasury = "txnFromTreasury";
		final var txnFromDebbie = "txnFromDebbie";

		return defaultHapiSpec("NestedHbarCaseStudy")
				.given(
						cryptoCreate(debbie),
						cryptoCreate(edgar),
						cryptoCreate(treasuryForTopLevelCollection),
						cryptoCreate(treasuryForNestedCollection),
						tokenCreate(tokenWithHbarFee)
								.initialSupply(Long.MAX_VALUE)
								.treasury(treasuryForNestedCollection)
								.withCustom(fixedHbarFee(ONE_HBAR, treasuryForNestedCollection)),
						tokenAssociate(treasuryForTopLevelCollection, tokenWithHbarFee),
						tokenCreate(tokenWithNestedFee)
								.initialSupply(Long.MAX_VALUE)
								.treasury(treasuryForTopLevelCollection)
								.withCustom(fixedHtsFee(1, tokenWithHbarFee, treasuryForTopLevelCollection)),
						tokenAssociate(debbie, List.of(tokenWithHbarFee, tokenWithNestedFee)),
						tokenAssociate(edgar, tokenWithNestedFee),
						cryptoTransfer(
								moving(1_000L, tokenWithHbarFee)
										.between(treasuryForNestedCollection, debbie),
								moving(1_000L, tokenWithNestedFee)
										.between(treasuryForTopLevelCollection, debbie)
						)
								.payingWith(treasuryForNestedCollection)
								.fee(ONE_HBAR)
								.via(txnFromTreasury)
				).when(
						cryptoTransfer(
								moving(1L, tokenWithNestedFee).between(debbie, edgar)
						)
								.payingWith(debbie)
								.fee(ONE_HBAR)
								.via(txnFromDebbie)
				).then(
						getTxnRecord(txnFromTreasury).logged(),
						getTxnRecord(txnFromDebbie).logged()
						/* TODO - validate balances */
				);
	}

	public HapiApiSpec nestedFractionalCaseStudy() {
		final var edgar = "Edgar";
		final var fern = "Fern";
		final var tokenWithFractionalFee = "TokenWithFractionalFee";
		final var tokenWithNestedFee = "TokenWithNestedFee";
		final var treasuryForTopLevelCollection = "TokenTreasury";
		final var treasuryForNestedCollection = "NestedTokenTreasury";

		final var txnFromTreasury = "txnFromTreasury";
		final var txnFromEdgar = "txnFromEdgar";

		return defaultHapiSpec("NestedFractionalCaseStudy")
				.given(
						cryptoCreate(edgar),
						cryptoCreate(fern),
						cryptoCreate(treasuryForTopLevelCollection),
						cryptoCreate(treasuryForNestedCollection),
						tokenCreate(tokenWithFractionalFee)
								.initialSupply(Long.MAX_VALUE)
								.treasury(treasuryForNestedCollection)
								.withCustom(fractionalFee(1, 100, 1L, OptionalLong.of(5L),
										treasuryForNestedCollection)),
						tokenAssociate(treasuryForTopLevelCollection, tokenWithFractionalFee),
						tokenCreate(tokenWithNestedFee)
								.initialSupply(Long.MAX_VALUE)
								.treasury(treasuryForTopLevelCollection)
								.withCustom(fixedHtsFee(50, tokenWithFractionalFee, treasuryForTopLevelCollection)),
						tokenAssociate(edgar, List.of(tokenWithFractionalFee, tokenWithNestedFee)),
						tokenAssociate(fern, tokenWithNestedFee),
						cryptoTransfer(
								moving(1_000L, tokenWithFractionalFee)
										.between(treasuryForNestedCollection, edgar),
								moving(1_000L, tokenWithNestedFee)
										.between(treasuryForTopLevelCollection, edgar)
						)
								.payingWith(treasuryForNestedCollection)
								.fee(ONE_HBAR)
								.via(txnFromTreasury)
				).when(
						cryptoTransfer(
								moving(10L, tokenWithNestedFee).between(edgar, fern)
						)
								.payingWith(edgar)
								.fee(ONE_HBAR)
								.via(txnFromEdgar)
				).then(
						getTxnRecord(txnFromTreasury).logged(),
						getTxnRecord(txnFromEdgar).logged()
						/* TODO - validate balances */
				);
	}

	public HapiApiSpec nestedHtsCaseStudy() {
		final var debbie = "Debbie";
		final var edgar = "Edgar";
		final var feeToken = "FeeToken";
		final var tokenWithHtsFee = "TokenWithHtsFee";
		final var tokenWithNestedFee = "TokenWithNestedFee";
		final var treasuryForTopLevelCollection = "TokenTreasury";
		final var treasuryForNestedCollection = "NestedTokenTreasury";

		final var txnFromTreasury = "txnFromTreasury";
		final var txnFromDebbie = "txnFromDebbie";

		return defaultHapiSpec("NestedHtsCaseStudy")
				.given(
						cryptoCreate(debbie),
						cryptoCreate(edgar),
						cryptoCreate(treasuryForTopLevelCollection),
						cryptoCreate(treasuryForNestedCollection),
						tokenCreate(feeToken)
								.treasury(DEFAULT_PAYER)
								.initialSupply(Long.MAX_VALUE),
						tokenAssociate(treasuryForNestedCollection, feeToken),
						tokenCreate(tokenWithHtsFee)
								.initialSupply(Long.MAX_VALUE)
								.treasury(treasuryForNestedCollection)
								.withCustom(fixedHtsFee(1, feeToken, treasuryForNestedCollection)),
						tokenAssociate(treasuryForTopLevelCollection, tokenWithHtsFee),
						tokenCreate(tokenWithNestedFee)
								.initialSupply(Long.MAX_VALUE)
								.treasury(treasuryForTopLevelCollection)
								.withCustom(fixedHtsFee(1, tokenWithHtsFee, treasuryForTopLevelCollection)),
						tokenAssociate(debbie, List.of(feeToken, tokenWithHtsFee, tokenWithNestedFee)),
						tokenAssociate(edgar, tokenWithNestedFee),
						cryptoTransfer(
								moving(1_000L, feeToken)
										.between(DEFAULT_PAYER, debbie),
								moving(1_000L, tokenWithHtsFee)
										.between(treasuryForNestedCollection, debbie),
								moving(1_000L, tokenWithNestedFee)
										.between(treasuryForTopLevelCollection, debbie)
						)
								.payingWith(treasuryForNestedCollection)
								.fee(ONE_HBAR)
								.via(txnFromTreasury)
				).when(
						cryptoTransfer(
								moving(1L, tokenWithNestedFee).between(debbie, edgar)
						)
								.payingWith(debbie)
								.fee(ONE_HBAR)
								.via(txnFromDebbie)
				).then(
						getTxnRecord(txnFromTreasury).logged(),
						getTxnRecord(txnFromDebbie).logged()
						/* TODO - validate balances */
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

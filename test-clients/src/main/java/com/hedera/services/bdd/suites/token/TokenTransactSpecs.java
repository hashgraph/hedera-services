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
import com.hedera.services.bdd.spec.queries.token.HapiTokenNftInfo;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountNftInfos;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.*;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
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
						balancesChangeOnTokenTransferWithFixedHbarCustomFees(),
						transferFailsWithInsufficientBalanceForFixedCustomFees(),
				}
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
										HapiTokenNftInfo.newTokenNftInfo(A_TOKEN, 1, FIRST_USER,
												ByteString.copyFromUtf8("memo"))
								),
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
										HapiTokenNftInfo.newTokenNftInfo(A_TOKEN, 1, TOKEN_TREASURY,
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

	public HapiApiSpec balancesChangeOnTokenTransferWithFixedHbarCustomFees() {
		return defaultHapiSpec("BalancesChangeOnTokenTransferWithFixedHbarCustomFees")
				.given(
						cryptoCreate(FIRST_USER).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(SECOND_USER).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate(A_TOKEN)
								.initialSupply(TOTAL_SUPPLY)
								.treasury(TOKEN_TREASURY)
								.withCustom(fixedHbarFee(ONE_HBAR, TOKEN_TREASURY)),
						tokenAssociate(FIRST_USER, A_TOKEN),
						tokenAssociate(SECOND_USER, A_TOKEN)
				).when(
						cryptoTransfer(
								moving(100, A_TOKEN).between(TOKEN_TREASURY, FIRST_USER)
						),
						cryptoTransfer(
								moving(100, A_TOKEN).between(TOKEN_TREASURY, SECOND_USER)
						),
						cryptoTransfer(
								moving(10, A_TOKEN).between(FIRST_USER, SECOND_USER)
						).payingWith(FIRST_USER)
								.signedBy(FIRST_USER)
								.fee(ONE_HBAR)
				).then(
						getAccountBalance(TOKEN_TREASURY)
								.logged()
								.hasTokenBalance(A_TOKEN, TOTAL_SUPPLY - 200)
								.hasTinyBars(3 * ONE_HBAR),
						getAccountBalance(FIRST_USER)
								.logged()
								.hasTokenBalance(A_TOKEN, 90)
								.hasTinyBars(9899205334L),
						getAccountBalance(SECOND_USER)
								.logged()
								.hasTokenBalance(A_TOKEN, 110)
								.hasTinyBars(ONE_HUNDRED_HBARS)
				);
	}

	public HapiApiSpec transferFailsWithInsufficientBalanceForFixedCustomFees() {
		return defaultHapiSpec("TransferFailsWithInsufficientBalanceForFixedCustomFees")
				.given(
						cryptoCreate(FIRST_USER).balance(ONE_HBAR),
						cryptoCreate(SECOND_USER).balance(0L),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate(A_TOKEN)
								.initialSupply(TOTAL_SUPPLY)
								.treasury(TOKEN_TREASURY)
								.withCustom(fixedHbarFee(5 * ONE_HBAR, TOKEN_TREASURY)),
						tokenAssociate(FIRST_USER, A_TOKEN),
						tokenAssociate(SECOND_USER, A_TOKEN)
				).when(
						cryptoTransfer(
								moving(10, A_TOKEN).between(TOKEN_TREASURY, FIRST_USER)
						),
						cryptoTransfer(
								moving(10, A_TOKEN).between(TOKEN_TREASURY, SECOND_USER)
						),
						cryptoTransfer(
								moving(5, A_TOKEN).between(FIRST_USER, SECOND_USER)
						).payingWith(FIRST_USER)
								.signedBy(FIRST_USER)
								.fee(ONE_HBAR)
						.hasKnownStatus(INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE)
				).then(
						getAccountBalance(TOKEN_TREASURY)
								.logged()
								.hasTokenBalance(A_TOKEN, TOTAL_SUPPLY - 20)
								.hasTinyBars(10 * ONE_HBAR),
						getAccountBalance(FIRST_USER)
								.logged()
								.hasTokenBalance(A_TOKEN, 10)
								.hasTinyBars(99205334),
						getAccountBalance(SECOND_USER)
								.logged()
								.hasTokenBalance(A_TOKEN, 10)
								.hasTinyBars(0L)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

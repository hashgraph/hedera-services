package com.hedera.services.bdd.suites.contract.precompile;

/*-
 * ‌
 * Hedera Services Node
 *
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 *
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
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asDotDelimitedLongArray;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.KNOWABLE_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.MiscEETUtils.metadata;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Frozen;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Unfrozen;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.KycNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class SafeOperationsSuite extends HapiApiSuite {

	private static final Logger log = LogManager.getLogger(SafeOperationsSuite.class);
	private static final String THE_CONTRACT = "SafeOperations";
	private static final String ACCOUNT = "anybody";
	private static final String SECOND_ACCOUNT = "anybody2";
	private static final String FROZEN_TOKEN = "Frozen token";
	private static final String UNFROZEN_TOKEN = "Unfrozen token";
	private static final String MULTI_KEY = "Multi key";
	private static final String FREEZE_KEY = "Freeze key";
	private static final TokenID DUMMY_ID = TokenID.newBuilder().build();

	public static void main(String... args) {
		new SafeOperationsSuite().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	public boolean canRunAsync() {
		return true;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
//				safeMintPrecompile(),
//				safeBurnPrecompile(),
//				safeAssociatePrecompile(),
//				safeDissociatePrecompile(),
//				safeMultipleAssociatePrecompile(),
//				safeMultipleDissociationPrecompile(),
//				safeNFTTransferPrecompile(),
//				safeTokenTransferPrecompile(),
//				safeTokensTransferPrecompile(),
//				safeNftsTransferPrecompile()
				fungibleTokenSafeCreateHappyPath()
		);
	}

	private static TokenID asToken(String v) {
		long[] nativeParts = asDotDelimitedLongArray(v);
		return TokenID.newBuilder()
				.setShardNum(nativeParts[0])
				.setRealmNum(nativeParts[1])
				.setTokenNum(nativeParts[2])
				.build();
	}

	private HapiApiSpec safeMintPrecompile() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
		final var amount = 10;

		return defaultHapiSpec("SafeMintPrecompile")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						uploadInitCode(THE_CONTRACT),
						contractCreate(THE_CONTRACT),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(THE_CONTRACT, "safeTokenMint",
														asAddress(DUMMY_ID), amount, Collections.emptyList())
														.payingWith(ACCOUNT)
														.via("safeMintDummyTxn")
														.gas(4_000_000)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												contractCall(THE_CONTRACT, "safeTokenMint",
														asAddress(vanillaTokenID.get()), amount, Collections.emptyList())
														.payingWith(ACCOUNT)
														.via("safeMintTxn")
														.gas(4_000_000)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						childRecordsCheck("safeMintDummyTxn", CONTRACT_REVERT_EXECUTED, recordWith().status(INVALID_TOKEN_ID)),
						childRecordsCheck("safeMintTxn", SUCCESS, recordWith().status(SUCCESS)),
						getTokenInfo(VANILLA_TOKEN).hasTotalSupply(amount),
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(VANILLA_TOKEN, amount)
				);
	}

	private HapiApiSpec safeBurnPrecompile() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
		final var amount = 10;

		return defaultHapiSpec("SafeBurnPrecompile")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						uploadInitCode(THE_CONTRACT),
						contractCreate(THE_CONTRACT),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
						mintToken(VANILLA_TOKEN, 10L)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(THE_CONTRACT, "safeTokenBurn",
														asAddress(DUMMY_ID), amount, Collections.emptyList())
														.payingWith(ACCOUNT)
														.via("safeBurnDummyTxn")
														.gas(4_000_000)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												contractCall(THE_CONTRACT, "safeTokenBurn",
														asAddress(vanillaTokenID.get()), amount, Collections.emptyList())
														.payingWith(ACCOUNT)
														.via("safeBurnTxn")
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						childRecordsCheck("safeBurnDummyTxn", CONTRACT_REVERT_EXECUTED, recordWith().status(INVALID_TOKEN_ID)),
						childRecordsCheck("safeBurnTxn", SUCCESS, recordWith().status(SUCCESS)),
						getTokenInfo(VANILLA_TOKEN).hasTotalSupply(0),
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(VANILLA_TOKEN, 0)
				);
	}

	private HapiApiSpec safeAssociatePrecompile() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("SafeAssociatePrecompile")
				.given(
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						uploadInitCode(THE_CONTRACT),
						contractCreate(THE_CONTRACT),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(THE_CONTRACT, "safeTokenAssociate",
														asAddress(accountID.get()), asAddress(DUMMY_ID))
														.payingWith(ACCOUNT)
														.via("safeDummyTokenAssociateTxn")
														.gas(4_000_000)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												contractCall(THE_CONTRACT, "safeTokenAssociate",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(ACCOUNT)
														.via("safeTokenAssociateTxn")
														.gas(4_000_000)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						childRecordsCheck("safeDummyTokenAssociateTxn", CONTRACT_REVERT_EXECUTED, recordWith()
								.status(INVALID_TOKEN_ID)),
						childRecordsCheck("safeTokenAssociateTxn", SUCCESS, recordWith()
								.status(SUCCESS)),
						getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN))
				);
	}

	public HapiApiSpec safeDissociatePrecompile() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("SafeDissociatePrecompile")
				.given(
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						uploadInitCode(THE_CONTRACT),
						contractCreate(THE_CONTRACT),
						cryptoCreate(TOKEN_TREASURY).exposingCreatedIdTo(treasuryID::set),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(10L)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(THE_CONTRACT, "safeTokenDissociate",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(ACCOUNT)
														.via("safeTokenDissociateFailsTxn")
														.gas(4_000_000)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												tokenAssociate(ACCOUNT, VANILLA_TOKEN),
												contractCall(THE_CONTRACT, "safeTokenDissociate",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(ACCOUNT)
														.via("safeTokenDissociateTxn")
														.gas(4_000_000)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						childRecordsCheck("safeTokenDissociateFailsTxn", CONTRACT_REVERT_EXECUTED,
								recordWith().status(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)),
						childRecordsCheck("safeTokenDissociateTxn", SUCCESS, recordWith().status(SUCCESS)),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(VANILLA_TOKEN)
				);
	}

	private HapiApiSpec safeMultipleAssociatePrecompile() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> frozenTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> unfrozenTokenID = new AtomicReference<>();

		return defaultHapiSpec("SafeMultipleAssociatePrecompile")
				.given(
						newKeyNamed(FREEZE_KEY),
						cryptoCreate(ACCOUNT)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						uploadInitCode(THE_CONTRACT),
						contractCreate(THE_CONTRACT),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate(FROZEN_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(10)
								.freezeKey(FREEZE_KEY)
								.freezeDefault(true)
								.exposingCreatedIdTo(id -> frozenTokenID.set(asToken(id))),
						tokenCreate(UNFROZEN_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.freezeKey(FREEZE_KEY)
								.freezeDefault(false)
								.exposingCreatedIdTo(id -> unfrozenTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(THE_CONTRACT, "safeTokensAssociate",
														asAddress(accountID.get()),
														List.of(
																asAddress(DUMMY_ID),
																asAddress(DUMMY_ID)))
														.payingWith(ACCOUNT)
														.via("safeMultipleTokensAssociationsFailsTxn")
														.gas(4_000_000)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												contractCall(THE_CONTRACT, "safeTokensAssociate",
														asAddress(accountID.get()),
														List.of(
																asAddress(frozenTokenID.get()),
																asAddress(unfrozenTokenID.get())))
														.payingWith(ACCOUNT)
														.via("safeMultipleTokensAssociationsTxn")
														.gas(4_000_000)
														.hasKnownStatus(ResponseCodeEnum.SUCCESS)
										)
						)
				).then(
						childRecordsCheck("safeMultipleTokensAssociationsFailsTxn", CONTRACT_REVERT_EXECUTED,
								recordWith().status(INVALID_TOKEN_ID)),
						childRecordsCheck("safeMultipleTokensAssociationsTxn", SUCCESS, recordWith().status(SUCCESS)),
						getAccountInfo(ACCOUNT)
								.hasToken(relationshipWith(FROZEN_TOKEN).kyc(KycNotApplicable).freeze(Frozen))
								.hasToken(relationshipWith(UNFROZEN_TOKEN).kyc(KycNotApplicable).freeze(Unfrozen))
				);
	}

	public HapiApiSpec safeMultipleDissociationPrecompile() {
		final AtomicReference<TokenID> knowableTokenTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();

		return defaultHapiSpec("SafeMultipleDissociationPrecompile")
				.given(
						cryptoCreate(ACCOUNT)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						uploadInitCode(THE_CONTRACT),
						contractCreate(THE_CONTRACT),
						cryptoCreate(TOKEN_TREASURY)
								.balance(0L)
								.exposingCreatedIdTo(treasuryID::set),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(10)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
						tokenCreate(KNOWABLE_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(10)
								.exposingCreatedIdTo(id -> knowableTokenTokenID.set(asToken(id)))
				)
				.when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(THE_CONTRACT, "safeTokensDissociate",
														asAddress(accountID.get()), List.of(
																asAddress(vanillaTokenID.get()),
																asAddress(knowableTokenTokenID.get())))
														.payingWith(ACCOUNT)
														.via("safeMultipleDissociationFailsTxn")
														.gas(4_000_000)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												tokenAssociate(ACCOUNT, List.of(VANILLA_TOKEN, KNOWABLE_TOKEN)),
												getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN)),
												getAccountInfo(ACCOUNT).hasToken(relationshipWith(KNOWABLE_TOKEN)),
												contractCall(THE_CONTRACT, "safeTokensDissociate",
														asAddress(accountID.get()), List.of(
																asAddress(vanillaTokenID.get()),
																asAddress(knowableTokenTokenID.get())))
														.payingWith(ACCOUNT)
														.gas(4_000_000)
														.via("safeMultipleDissociationTxn")
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						childRecordsCheck("safeMultipleDissociationFailsTxn", CONTRACT_REVERT_EXECUTED,
								recordWith().status(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)),
						childRecordsCheck("safeMultipleDissociationTxn", SUCCESS, recordWith().status(SUCCESS)),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(VANILLA_TOKEN),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(KNOWABLE_TOKEN)
				);
	}

	public HapiApiSpec safeNFTTransferPrecompile() {
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();

		return defaultHapiSpec("SafeNFTTransferPrecompile")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						uploadInitCode(THE_CONTRACT),
						contractCreate(THE_CONTRACT),
						cryptoCreate(TOKEN_TREASURY)
								.balance(0L)
								.exposingCreatedIdTo(treasuryID::set),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.initialSupply(0)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
						mintToken(VANILLA_TOKEN, List.of(metadata("firstMemo"), metadata("secondMemo"))),
						tokenAssociate(ACCOUNT, VANILLA_TOKEN),
						cryptoUpdate(TOKEN_TREASURY).key(MULTI_KEY)
				)
				.when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(THE_CONTRACT, "safeNFTTransfer",
														asAddress(vanillaTokenID.get()),
														asAddress(treasuryID.get()),
														asAddress(accountID.get()),
														1L)
														.payingWith(ACCOUNT)
														.via("safeNFTTransferFailsTxn")
														.gas(4_000_000)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												contractCall(THE_CONTRACT, "safeNFTTransfer",
														asAddress(vanillaTokenID.get()),
														asAddress(treasuryID.get()),
														asAddress(accountID.get()),
														1L)
														.payingWith(ACCOUNT)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via("safeNFTTransferTxn")
														.gas(4_000_000)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						childRecordsCheck("safeNFTTransferFailsTxn", CONTRACT_REVERT_EXECUTED,
								recordWith().status(INVALID_SIGNATURE)),
						childRecordsCheck("safeNFTTransferTxn", SUCCESS, recordWith().status(SUCCESS)),
						getAccountBalance(ACCOUNT).hasTokenBalance(VANILLA_TOKEN, 1),
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(VANILLA_TOKEN, 1)
				);
	}

	public HapiApiSpec safeTokenTransferPrecompile() {
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();

		return defaultHapiSpec("SafeTokenTransferPrecompile")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						uploadInitCode(THE_CONTRACT),
						contractCreate(THE_CONTRACT),
						cryptoCreate(TOKEN_TREASURY)
								.balance(0L)
								.exposingCreatedIdTo(treasuryID::set),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.initialSupply(0)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
						mintToken(VANILLA_TOKEN, 10),
						tokenAssociate(ACCOUNT, VANILLA_TOKEN),
						cryptoUpdate(TOKEN_TREASURY).key(MULTI_KEY)
				)
				.when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(THE_CONTRACT, "safeTokenTransfer",
														asAddress(vanillaTokenID.get()),
														asAddress(treasuryID.get()),
														asAddress(accountID.get()),
														5L)
														.payingWith(ACCOUNT)
														.via("safeTokenTransferFailsTxn")
														.gas(4_000_000)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												contractCall(THE_CONTRACT, "safeTokenTransfer",
														asAddress(vanillaTokenID.get()),
														asAddress(treasuryID.get()),
														asAddress(accountID.get()),
														5L)
														.payingWith(ACCOUNT)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via("safeTokenTransferTxn")
														.gas(4_000_000)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						childRecordsCheck("safeTokenTransferFailsTxn", CONTRACT_REVERT_EXECUTED,
								recordWith().status(INVALID_SIGNATURE)),
						childRecordsCheck("safeTokenTransferTxn", SUCCESS, recordWith().status(SUCCESS)),
						getAccountBalance(ACCOUNT).hasTokenBalance(VANILLA_TOKEN, 5),
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(VANILLA_TOKEN, 5)
				);
	}

	public HapiApiSpec safeTokensTransferPrecompile() {
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
		final AtomicReference<AccountID> firstAccountID = new AtomicReference<>();
		final AtomicReference<AccountID> secondAccountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();

		return defaultHapiSpec("SafeTokensTransferPrecompile")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(firstAccountID::set),
						cryptoCreate(SECOND_ACCOUNT)
								.exposingCreatedIdTo(secondAccountID::set),
						uploadInitCode(THE_CONTRACT),
						contractCreate(THE_CONTRACT),
						cryptoCreate(TOKEN_TREASURY)
								.balance(0L)
								.exposingCreatedIdTo(treasuryID::set),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.initialSupply(0)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
						mintToken(VANILLA_TOKEN, 10),
						tokenAssociate(ACCOUNT, VANILLA_TOKEN),
						tokenAssociate(SECOND_ACCOUNT, VANILLA_TOKEN),
						cryptoUpdate(TOKEN_TREASURY).key(MULTI_KEY),
						cryptoUpdate(SECOND_ACCOUNT).key(MULTI_KEY),
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(VANILLA_TOKEN, 10)
				)
				.when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(THE_CONTRACT, "safeTokensTransfer",
														asAddress(vanillaTokenID.get()),
														List.of(asAddress(firstAccountID.get()),
																asAddress(secondAccountID.get())),
														List.of(4, 5)
												)
														.payingWith(ACCOUNT)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via("safeTokensTransferFailingTxn")
														.gas(4_000_000)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												contractCall(THE_CONTRACT, "safeTokensTransfer",
														asAddress(vanillaTokenID.get()),
														List.of(asAddress(treasuryID.get()),
																asAddress(firstAccountID.get()),
																asAddress(secondAccountID.get())),
														List.of(-9, 4, 5)
												)
														.payingWith(ACCOUNT)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via("safeTokensTransferTxn")
														.gas(4_000_000)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						childRecordsCheck("safeTokensTransferFailingTxn", CONTRACT_REVERT_EXECUTED,
								recordWith().status(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN)),
						childRecordsCheck("safeTokensTransferTxn", SUCCESS,
								recordWith().status(SUCCESS)),
						getAccountBalance(ACCOUNT).hasTokenBalance(VANILLA_TOKEN, 4),
						getAccountBalance(SECOND_ACCOUNT).hasTokenBalance(VANILLA_TOKEN, 5),
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(VANILLA_TOKEN, 1)
				);
	}

	public HapiApiSpec safeNftsTransferPrecompile() {
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> secondAccountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();

		return defaultHapiSpec("SafeNftsTransferPrecompile")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(SECOND_ACCOUNT)
								.exposingCreatedIdTo(secondAccountID::set),
						uploadInitCode(THE_CONTRACT),
						contractCreate(THE_CONTRACT),
						cryptoCreate(TOKEN_TREASURY)
								.balance(0L)
								.exposingCreatedIdTo(treasuryID::set),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.initialSupply(0)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
						mintToken(VANILLA_TOKEN,
								List.of(metadata("firstMemo"),
										metadata("secondMemo")
								)),
						tokenAssociate(ACCOUNT, VANILLA_TOKEN),
						tokenAssociate(SECOND_ACCOUNT, VANILLA_TOKEN),
						cryptoUpdate(TOKEN_TREASURY).key(MULTI_KEY)
				)
				.when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(THE_CONTRACT, "safeNFTsTransfer",
														asAddress(vanillaTokenID.get()),
														Collections.emptyList(),
														List.of(asAddress(accountID.get()), asAddress(secondAccountID.get())),
														List.of(1L, 2L))
														.payingWith(ACCOUNT)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via("safeNftsTransferFailsTxn")
														.gas(4_000_000)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												contractCall(THE_CONTRACT, "safeNFTsTransfer",
														asAddress(vanillaTokenID.get()),
														List.of(asAddress(treasuryID.get()), asAddress(treasuryID.get())),
														List.of(asAddress(accountID.get()), asAddress(secondAccountID.get())),
														List.of(1L, 2L))
														.payingWith(ACCOUNT)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via("safeNftsTransferTxn")
														.gas(4_000_000)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						childRecordsCheck("safeNftsTransferFailsTxn", CONTRACT_REVERT_EXECUTED,
								recordWith().status(EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS)),
						childRecordsCheck("safeNftsTransferTxn", SUCCESS,
								recordWith().status(SUCCESS)),
						getAccountBalance(ACCOUNT).hasTokenBalance(VANILLA_TOKEN, 1),
						getAccountBalance(SECOND_ACCOUNT).hasTokenBalance(VANILLA_TOKEN, 1),
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(VANILLA_TOKEN, 0)
				);
	}

	private HapiApiSpec fungibleTokenSafeCreateHappyPath() {
		return defaultHapiSpec("fungibleTokenSafeCreateHappyPath")
				.given(
						uploadInitCode(THE_CONTRACT),
						contractCreate(THE_CONTRACT)

				).when(
						sourcing(() -> contractCall(
								THE_CONTRACT, "safeCreateOfFungibleToken"
						)
								.via("SAFE_CREATE_FUNGIBLE_TOKEN_TXN").gas(4_000_000).hasKnownStatus(CONTRACT_REVERT_EXECUTED))
				).then(
						getTxnRecord("SAFE_CREATE_FUNGIBLE_TOKEN_TXN").andAllChildRecords().logged()
				);
	}
}

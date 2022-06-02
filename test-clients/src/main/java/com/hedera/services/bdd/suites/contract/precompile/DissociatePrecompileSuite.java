package com.hedera.services.bdd.suites.contract.precompile;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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
import com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asDotDelimitedLongArray;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.FREEZABLE_TOKEN_ON_BY_DEFAULT;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.KNOWABLE_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.TBD_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;

public class DissociatePrecompileSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(DissociatePrecompileSuite.class);

	private static final long GAS_TO_OFFER = 2_000_000L;

	private static final long TOTAL_SUPPLY = 1_000;
	private static final String TOKEN_TREASURY = "treasury";
	private static final String OUTER_CONTRACT = "NestedAssociateDissociate";
	private static final String NESTED_CONTRACT = "AssociateDissociate";
	private static final String CONTRACT = "AssociateDissociate";
	private static final String ACCOUNT = "anybody";
	private static final String MULTI_KEY = "Multi key";

	public static void main(String... args) {
		new DissociatePrecompileSuite().runSuiteAsync();
	}

	@Override
	public boolean canRunConcurrent() {
		return true;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveSpecs(),
				negativeSpecs()
		);
	}

	List<HapiApiSpec> negativeSpecs() {
		return List.of(
		);
	}

	List<HapiApiSpec> positiveSpecs() {
		return List.of(new HapiApiSpec[] {
				dissociatePrecompileHasExpectedSemanticsForDeletedTokens(),
				nestedDissociateWorksAsExpected(),
				multiplePrecompileDissociationWithSigsForFungibleWorks()
		});
	}

	/* -- Not specifically required in the HTS Precompile Test Plan -- */
	public HapiApiSpec dissociatePrecompileHasExpectedSemanticsForDeletedTokens() {
		final var tbdUniqToken = "UniqToBeDeleted";
		final var zeroBalanceFrozen = "0bFrozen";
		final var zeroBalanceUnfrozen = "0bUnfrozen";
		final var nonZeroBalanceFrozen = "1bFrozen";
		final var nonZeroBalanceUnfrozen = "1bUnfrozen";
		final var initialSupply = 100L;
		final var nonZeroXfer = 10L;
		final var firstMeta = ByteString.copyFrom("FIRST".getBytes(StandardCharsets.UTF_8));
		final var secondMeta = ByteString.copyFrom("SECOND".getBytes(StandardCharsets.UTF_8));
		final var thirdMeta = ByteString.copyFrom("THIRD".getBytes(StandardCharsets.UTF_8));

		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();
		final AtomicReference<AccountID> zeroBalanceFrozenID = new AtomicReference<>();
		final AtomicReference<AccountID> zeroBalanceUnfrozenID = new AtomicReference<>();
		final AtomicReference<AccountID> nonZeroBalanceFrozenID = new AtomicReference<>();
		final AtomicReference<AccountID> nonZeroBalanceUnfrozenID = new AtomicReference<>();
		final AtomicReference<TokenID> tbdTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> tbdUniqueTokenID = new AtomicReference<>();

		return defaultHapiSpec("DissociatePrecompileHasExpectedSemanticsForDeletedTokens")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(treasuryID::set),
						tokenCreate(TBD_TOKEN)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.initialSupply(initialSupply)
								.treasury(TOKEN_TREASURY)
								.freezeKey(MULTI_KEY)
								.freezeDefault(true)
								.exposingCreatedIdTo(id -> tbdTokenID.set(asToken(id))),
						tokenCreate(tbdUniqToken)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.initialSupply(0)
								.exposingCreatedIdTo(id -> tbdUniqueTokenID.set(asToken(id))),
						cryptoCreate(zeroBalanceFrozen)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(zeroBalanceFrozenID::set),
						cryptoCreate(zeroBalanceUnfrozen)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(zeroBalanceUnfrozenID::set),
						cryptoCreate(nonZeroBalanceFrozen)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(nonZeroBalanceFrozenID::set),
						cryptoCreate(nonZeroBalanceUnfrozen)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(nonZeroBalanceUnfrozenID::set),
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												tokenAssociate(zeroBalanceFrozen, TBD_TOKEN),
												tokenAssociate(zeroBalanceUnfrozen, TBD_TOKEN),
												tokenAssociate(nonZeroBalanceFrozen, TBD_TOKEN),
												tokenAssociate(nonZeroBalanceUnfrozen, TBD_TOKEN),
												mintToken(tbdUniqToken, List.of(firstMeta, secondMeta, thirdMeta)),
												getAccountInfo(TOKEN_TREASURY).hasOwnedNfts(3),
												tokenUnfreeze(TBD_TOKEN, zeroBalanceUnfrozen),
												tokenUnfreeze(TBD_TOKEN, nonZeroBalanceUnfrozen),
												tokenUnfreeze(TBD_TOKEN, nonZeroBalanceFrozen),

												cryptoTransfer(moving(nonZeroXfer, TBD_TOKEN).between(TOKEN_TREASURY,
														nonZeroBalanceFrozen)),
												cryptoTransfer(moving(nonZeroXfer, TBD_TOKEN).between(TOKEN_TREASURY,
														nonZeroBalanceUnfrozen)),

												tokenFreeze(TBD_TOKEN, nonZeroBalanceFrozen),
												getAccountBalance(TOKEN_TREASURY)
														.hasTokenBalance(TBD_TOKEN, initialSupply - 2 * nonZeroXfer),
												tokenDelete(TBD_TOKEN),
												tokenDelete(tbdUniqToken),
												contractCall(CONTRACT, "tokenDissociate",
														asAddress(zeroBalanceFrozenID.get()),
														asAddress(tbdTokenID.get())
												)
														.alsoSigningWithFullPrefix(zeroBalanceFrozen)
														.gas(GAS_TO_OFFER)
														.via("dissociateZeroBalanceFrozenTxn"),
												getTxnRecord(
														"dissociateZeroBalanceFrozenTxn").andAllChildRecords().logged(),
												contractCall(CONTRACT, "tokenDissociate",
														asAddress(zeroBalanceUnfrozenID.get()),
														asAddress(tbdTokenID.get())
												)
														.alsoSigningWithFullPrefix(zeroBalanceUnfrozen)
														.gas(GAS_TO_OFFER)
														.via("dissociateZeroBalanceUnfrozenTxn"),
												getTxnRecord(
														"dissociateZeroBalanceUnfrozenTxn").andAllChildRecords().logged(),
												contractCall(CONTRACT, "tokenDissociate",
														asAddress(nonZeroBalanceFrozenID.get()),
														asAddress(tbdTokenID.get())
												)
														.alsoSigningWithFullPrefix(nonZeroBalanceFrozen)
														.gas(GAS_TO_OFFER)
														.via("dissociateNonZeroBalanceFrozenTxn"),
												getTxnRecord(
														"dissociateNonZeroBalanceFrozenTxn").andAllChildRecords().logged(),
												contractCall(CONTRACT, "tokenDissociate",
														asAddress(nonZeroBalanceUnfrozenID.get()),
														asAddress(tbdTokenID.get())
												)
														.alsoSigningWithFullPrefix(nonZeroBalanceUnfrozen)
														.gas(GAS_TO_OFFER)
														.via("dissociateNonZeroBalanceUnfrozenTxn"),
												getTxnRecord(
														"dissociateNonZeroBalanceUnfrozenTxn").andAllChildRecords().logged(),
												contractCall(CONTRACT, "tokenDissociate",
														asAddress(treasuryID.get()), asAddress(tbdUniqueTokenID.get())
												)
														.alsoSigningWithFullPrefix(TOKEN_TREASURY)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						childRecordsCheck("dissociateZeroBalanceFrozenTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS)))),
						childRecordsCheck("dissociateZeroBalanceUnfrozenTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS)))),
						childRecordsCheck("dissociateNonZeroBalanceFrozenTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS)))),
						childRecordsCheck("dissociateNonZeroBalanceUnfrozenTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS)))),
						getAccountInfo(zeroBalanceFrozen).hasNoTokenRelationship(TBD_TOKEN),
						getAccountInfo(zeroBalanceUnfrozen).hasNoTokenRelationship(TBD_TOKEN),
						getAccountInfo(nonZeroBalanceFrozen).hasNoTokenRelationship(TBD_TOKEN),
						getAccountInfo(nonZeroBalanceUnfrozen).hasNoTokenRelationship(TBD_TOKEN),
						getAccountInfo(TOKEN_TREASURY)
								.hasToken(relationshipWith(TBD_TOKEN))
								.hasNoTokenRelationship(tbdUniqToken)
								.hasOwnedNfts(0),
						getAccountBalance(TOKEN_TREASURY)
								.hasTokenBalance(TBD_TOKEN, initialSupply - 2 * nonZeroXfer)
				);
	}

	/* -- Not specifically required in the HTS Precompile Test Plan -- */
	private HapiApiSpec nestedDissociateWorksAsExpected() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("nestedDissociateWorksAsExpected")
				.given(
						cryptoCreate(ACCOUNT)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
						uploadInitCode(OUTER_CONTRACT, NESTED_CONTRACT),
						contractCreate(NESTED_CONTRACT)
				)
				.when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(OUTER_CONTRACT, getNestedContractAddress(NESTED_CONTRACT
														, spec)),
												tokenAssociate(ACCOUNT, VANILLA_TOKEN),
												contractCall(OUTER_CONTRACT, "dissociateAssociateContractCall",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get())
												)
														.alsoSigningWithFullPrefix(ACCOUNT)
														.via("nestedDissociateTxn")
														.gas(3_000_000L)
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("nestedDissociateTxn").andAllChildRecords().logged()
										)
						)
				).then(
						childRecordsCheck("nestedDissociateTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS))),
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS)))),
						getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN)));
	}

	/* -- HSCS-PREC-007 from HTS Precompile Test Plan -- */
	public HapiApiSpec multiplePrecompileDissociationWithSigsForFungibleWorks() {
		final AtomicReference<TokenID> knowableTokenTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();

		return defaultHapiSpec("multiplePrecompileDissociationWithSigsForFungibleWorks")
				.given(
						cryptoCreate(ACCOUNT)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY)
								.balance(0L)
								.exposingCreatedIdTo(treasuryID::set),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(TOTAL_SUPPLY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
						tokenCreate(KNOWABLE_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(TOTAL_SUPPLY)
								.exposingCreatedIdTo(id -> knowableTokenTokenID.set(asToken(id))),
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
				)
				.when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												tokenAssociate(ACCOUNT, List.of(VANILLA_TOKEN, KNOWABLE_TOKEN)),
												getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN)),
												getAccountInfo(ACCOUNT).hasToken(relationshipWith(KNOWABLE_TOKEN)),
												contractCall(CONTRACT, "tokensDissociate",
														asAddress(accountID.get()), List.of(
																asAddress(vanillaTokenID.get()),
																asAddress(knowableTokenTokenID.get()))
												)
														.alsoSigningWithFullPrefix(ACCOUNT)
														.via("multipleDissociationTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(SUCCESS),
												getTxnRecord("multipleDissociationTxn").andAllChildRecords().logged()
										)
						)
				).then(
						childRecordsCheck("multipleDissociationTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS)))),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(FREEZABLE_TOKEN_ON_BY_DEFAULT),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(KNOWABLE_TOKEN));
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	/* --- Helpers --- */

	private static TokenID asToken(String v) {
		long[] nativeParts = asDotDelimitedLongArray(v);
		return TokenID.newBuilder()
				.setShardNum(nativeParts[0])
				.setRealmNum(nativeParts[1])
				.setTokenNum(nativeParts[2])
				.build();
	}

	@NotNull
	private String getNestedContractAddress(final String outerContract, final HapiApiSpec spec) {
		return AssociatePrecompileSuite.getNestedContractAddress(outerContract, spec);
	}
}

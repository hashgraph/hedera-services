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
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.legacy.core.CommonUtils;
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
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.MULTIPLE_TOKENS_DISSOCIATE;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.NESTED_TOKEN_DISSOCIATE;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.SINGLE_TOKEN_DISSOCIATE;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.FREEZABLE_TOKEN_ON_BY_DEFAULT;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.KNOWABLE_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.TBD_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;

public class DissociatePrecompileSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(DissociatePrecompileSuite.class);
	private static final long TOTAL_SUPPLY = 1_000;
	private static final String TOKEN_TREASURY = "treasury";
	private static final String OUTER_CONTRACT = "Nested Associate/Dissociate Contract";
	private static final String INNER_CONTRACT = "Associate/Dissociate Contract";
	private static final String THE_CONTRACT = "Associate/Dissociate Contract";
	private static final String ACCOUNT = "anybody";
	private static final String MULTI_KEY = "Multi key";

	public static void main(String... args) {
		new DissociatePrecompileSuite().runSuiteAsync();
	}

	@Override
	public boolean canRunAsync() {
		return true;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
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
		return List.of(
				dissociatePrecompileHasExpectedSemanticsForDeletedTokens(),
				nestedDissociateWorksAsExpected(),
				multiplePrecompileDissociationWithSigsForFungibleWorks()
		);
	}

	/* -- Not specifically required in the HTS Precompile Test Plan -- */
	public HapiApiSpec dissociatePrecompileHasExpectedSemanticsForDeletedTokens() {
		final String tbdUniqToken = "UniqToBeDeleted";
		final String zeroBalanceFrozen = "0bFrozen";
		final String zeroBalanceUnfrozen = "0bUnfrozen";
		final String nonZeroBalanceFrozen = "1bFrozen";
		final String nonZeroBalanceUnfrozen = "1bUnfrozen";
		final long initialSupply = 100L;
		final long nonZeroXfer = 10L;
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
						fileCreate(THE_CONTRACT),
						updateLargeFile(ACCOUNT, THE_CONTRACT,
								extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
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
								.exposingCreatedIdTo(nonZeroBalanceUnfrozenID::set)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_CONTRACT)
														.bytecode(THE_CONTRACT)
														.gas(100_000),
												tokenAssociate(zeroBalanceFrozen, TBD_TOKEN),
												tokenAssociate(zeroBalanceUnfrozen, TBD_TOKEN),
												tokenAssociate(nonZeroBalanceFrozen, TBD_TOKEN),
												tokenAssociate(nonZeroBalanceUnfrozen, TBD_TOKEN),
												mintToken(tbdUniqToken, List.of(firstMeta, secondMeta, thirdMeta)),
												getAccountInfo(TOKEN_TREASURY).hasOwnedNfts(3),
												tokenUnfreeze(TBD_TOKEN, zeroBalanceUnfrozen),
												tokenUnfreeze(TBD_TOKEN, nonZeroBalanceUnfrozen),
												tokenUnfreeze(TBD_TOKEN, nonZeroBalanceFrozen),

												cryptoTransfer(moving(nonZeroXfer, TBD_TOKEN).between(TOKEN_TREASURY, nonZeroBalanceFrozen)),
												cryptoTransfer(moving(nonZeroXfer, TBD_TOKEN).between(TOKEN_TREASURY, nonZeroBalanceUnfrozen)),

												tokenFreeze(TBD_TOKEN, nonZeroBalanceFrozen),
												getAccountBalance(TOKEN_TREASURY)
														.hasTokenBalance(TBD_TOKEN, initialSupply - 2 * nonZeroXfer),
												tokenDelete(TBD_TOKEN),
												tokenDelete(tbdUniqToken),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
														asAddress(zeroBalanceFrozenID.get()), asAddress(tbdTokenID.get()))
														.payingWith(zeroBalanceFrozen)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via("dissociateZeroBalanceFrozenTxn"),
												getTxnRecord("dissociateZeroBalanceFrozenTxn").andAllChildRecords().logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
														asAddress(zeroBalanceUnfrozenID.get()), asAddress(tbdTokenID.get()))
														.payingWith(zeroBalanceUnfrozen)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via("dissociateZeroBalanceUnfrozenTxn"),
												getTxnRecord("dissociateZeroBalanceUnfrozenTxn").andAllChildRecords().logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
														asAddress(nonZeroBalanceFrozenID.get()), asAddress(tbdTokenID.get()))
														.payingWith(nonZeroBalanceFrozen)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via("dissociateNonZeroBalanceFrozenTxn"),
												getTxnRecord("dissociateNonZeroBalanceFrozenTxn").andAllChildRecords().logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
														asAddress(nonZeroBalanceUnfrozenID.get()), asAddress(tbdTokenID.get()))
														.payingWith(nonZeroBalanceUnfrozen)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via("dissociateNonZeroBalanceUnfrozenTxn"),
												getTxnRecord("dissociateNonZeroBalanceUnfrozenTxn").andAllChildRecords().logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
														asAddress(treasuryID.get()), asAddress(tbdUniqueTokenID.get()))
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.payingWith(TOKEN_TREASURY)
										)
						)
				).then(
						childRecordsCheck("dissociateZeroBalanceFrozenTxn", SUCCESS, recordWith().status(SUCCESS)),
						childRecordsCheck("dissociateZeroBalanceUnfrozenTxn", SUCCESS, recordWith().status(SUCCESS)),
						childRecordsCheck("dissociateNonZeroBalanceFrozenTxn", SUCCESS, recordWith().status(SUCCESS)),
						childRecordsCheck("dissociateNonZeroBalanceUnfrozenTxn", SUCCESS, recordWith().status(SUCCESS)),
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
						fileCreate(INNER_CONTRACT),
						updateLargeFile(ACCOUNT, INNER_CONTRACT,
								extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
						fileCreate(OUTER_CONTRACT),
						updateLargeFile(ACCOUNT, OUTER_CONTRACT,
								extractByteCode(ContractResources.NESTED_ASSOCIATE_DISSOCIATE_CONTRACT)),
						contractCreate(INNER_CONTRACT)
								.bytecode(INNER_CONTRACT)
								.gas(100_000),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id)))
				)
				.when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(OUTER_CONTRACT, ContractResources.NESTED_ASSOCIATE_DISSOCIATE_CONTRACT_CONSTRUCTOR,
														getNestedContractAddress(INNER_CONTRACT, spec))
														.bytecode(OUTER_CONTRACT)
														.gas(100_000),
												tokenAssociate(ACCOUNT, VANILLA_TOKEN),
												contractCall(OUTER_CONTRACT, NESTED_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(ACCOUNT)
														.via("nestedDissociateTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("nestedDissociateTxn").andAllChildRecords().logged()
										)
						)
				).then(
						childRecordsCheck("nestedDissociateTxn", SUCCESS,
								recordWith().status(SUCCESS),
								recordWith().status(SUCCESS)),
						getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN))
				);
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
						fileCreate(THE_CONTRACT),
						updateLargeFile(ACCOUNT, THE_CONTRACT,
								extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
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
								.exposingCreatedIdTo(id -> knowableTokenTokenID.set(asToken(id)))
				)
				.when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_CONTRACT)
														.bytecode(THE_CONTRACT)
														.gas(100_000),
												tokenAssociate(ACCOUNT, List.of(VANILLA_TOKEN, KNOWABLE_TOKEN)),
												getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN)),
												getAccountInfo(ACCOUNT).hasToken(relationshipWith(KNOWABLE_TOKEN)),
												contractCall(THE_CONTRACT, MULTIPLE_TOKENS_DISSOCIATE,
														asAddress(accountID.get()), List.of(
																asAddress(vanillaTokenID.get()),
																asAddress(knowableTokenTokenID.get())))
														.payingWith(ACCOUNT)
														.via("multipleDissociationTxn")
														.hasKnownStatus(SUCCESS),
												getTxnRecord("multipleDissociationTxn").andAllChildRecords().logged()
										)
						)
				).then(
						childRecordsCheck("multipleDissociationTxn", SUCCESS, recordWith().status(SUCCESS)),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(FREEZABLE_TOKEN_ON_BY_DEFAULT),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(KNOWABLE_TOKEN)
				);
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
	private String getNestedContractAddress(String outerContract, HapiApiSpec spec) {
		return CommonUtils.calculateSolidityAddress(
				(int) spec.registry().getContractId(outerContract).getShardNum(),
				spec.registry().getContractId(outerContract).getRealmNum(),
				spec.registry().getContractId(outerContract).getContractNum());
	}
}

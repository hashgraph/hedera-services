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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.keys.KeyShape;
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
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.DELEGATE_DISSOCIATE_CALL_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.MULTIPLE_TOKENS_DISSOCIATE;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.NESTED_TOKEN_DISSOCIATE;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.SINGLE_TOKEN_DISSOCIATE;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.STATIC_DISSOCIATE_CALL_ABI;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.FREEZABLE_TOKEN_ON_BY_DEFAULT;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.KNOWABLE_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.MULTI_KEY;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.TBD_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.MiscEETUtils.metadata;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class DissociatePrecompileSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(DissociatePrecompileSuite.class);
	private static final long TOTAL_SUPPLY = 1_000;
	private static final String TOKEN_TREASURY = "treasury";
	private static final String OUTER_CONTRACT = "AssociateDissociateContract";
	private static final String INNER_CONTRACT = "NestedAssociateDissociateContract";
	private static final String THE_ACCOUNT = "anybody";


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
				staticCallForDissociatePrecompileFails(),
				delegateCallForDissociatePrecompileSignedWithContractKeyFails()
		);
	}

	List<HapiApiSpec> positiveSpecs() {
		return List.of(
				dissociatePrecompileWithSigsForFungibleWorks(),
				dissociatePrecompileWitContractIdSigForFungibleWorks(),
				dissociatePrecompileWithSigsForNFTWorks(),
				dissociatePrecompileWitContractIdSigForNFTWorks(),
				dissociatePrecompileHasExpectedSemanticsForDeletedTokens(),
				nestedDissociateWorksAsExpected(),
				multiplePrecompileDissociationsWithSigsForFungibleWorks(),
				delegateCallForDissociatePrecompileSignedWithDelegateContractKeyWorks()
		);
	}

	private HapiApiSpec delegateCallForDissociatePrecompileSignedWithContractKeyFails() {
		final var contractKey = "Contract key";
		final var contractKeyShape = KeyShape.threshOf(1, SIMPLE, CONTRACT);

		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("DelegateCallForDissociatePrecompileSignedWithContractKeyFails")
				.given(
						fileCreate(INNER_CONTRACT).path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						fileCreate(OUTER_CONTRACT).path(ContractResources.NESTED_ASSOCIATE_DISSOCIATE_CONTRACT),
						contractCreate(INNER_CONTRACT)
								.bytecode(INNER_CONTRACT)
								.gas(100_000),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
						cryptoCreate(THE_ACCOUNT)
								.exposingCreatedIdTo(accountID::set)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(OUTER_CONTRACT, ContractResources.NESTED_ASSOCIATE_DISSOCIATE_CONTRACT_CONSTRUCTOR,
														getNestedContractAddress(INNER_CONTRACT, spec))
														.bytecode(OUTER_CONTRACT)
														.gas(100_000),
												newKeyNamed(contractKey).shape(contractKeyShape.signedWith(sigs(ON, OUTER_CONTRACT))),
												cryptoUpdate(THE_ACCOUNT).key(contractKey),
												tokenAssociate(THE_ACCOUNT, VANILLA_TOKEN),
												contractCall(OUTER_CONTRACT, DELEGATE_DISSOCIATE_CALL_ABI,
														asAddress(accountID.get()), asAddress(vanillaTokenTokenID.get()))
														.payingWith(GENESIS)
														.via("delegateDissociateCallWithContractKeyTxn")
														.hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
														.gas(5_000_000),
												getTxnRecord("delegateDissociateCallWithContractKeyTxn").andAllChildRecords().logged()
										)
						)
				).then(
						getAccountInfo(THE_ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN))
				);
	}

	private HapiApiSpec delegateCallForDissociatePrecompileSignedWithDelegateContractKeyWorks() {
		final var delegateKey = "Delegate contract key";
		final var delegateContractKeyShape = KeyShape.threshOf(1, SIMPLE, DELEGATE_CONTRACT);

		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("delegateCallForDissociatePrecompileSignedWithDelegateContractKeyWorks")
				.given(
						fileCreate(INNER_CONTRACT).path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						fileCreate(OUTER_CONTRACT).path(ContractResources.NESTED_ASSOCIATE_DISSOCIATE_CONTRACT),
						contractCreate(INNER_CONTRACT)
								.bytecode(INNER_CONTRACT)
								.gas(100_000),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
						cryptoCreate(THE_ACCOUNT)
								.exposingCreatedIdTo(accountID::set)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(OUTER_CONTRACT, ContractResources.NESTED_ASSOCIATE_DISSOCIATE_CONTRACT_CONSTRUCTOR,
														getNestedContractAddress(INNER_CONTRACT, spec))
														.bytecode(OUTER_CONTRACT)
														.gas(100_000),
												tokenAssociate(THE_ACCOUNT, VANILLA_TOKEN),
												newKeyNamed(delegateKey).shape(delegateContractKeyShape.signedWith(sigs(ON, OUTER_CONTRACT))),
												cryptoUpdate(THE_ACCOUNT).key(delegateKey),
												contractCall(OUTER_CONTRACT, DELEGATE_DISSOCIATE_CALL_ABI,
														asAddress(accountID.get()), asAddress(vanillaTokenTokenID.get()))
														.payingWith(GENESIS)
														.via("delegateDissociateCallWithDelegateContractKeyTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS)
														.gas(5_000_000),
												getTxnRecord("delegateDissociateCallWithDelegateContractKeyTxn").andAllChildRecords().logged()
										)
						)
				).then(
						getAccountInfo(THE_ACCOUNT).hasNoTokenRelationship(VANILLA_TOKEN)
				);
	}

	private HapiApiSpec staticCallForDissociatePrecompileFails() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("StaticCallForDissociatePrecompileFails")
				.given(
						fileCreate(INNER_CONTRACT).path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						fileCreate(OUTER_CONTRACT).path(ContractResources.NESTED_ASSOCIATE_DISSOCIATE_CONTRACT),
						contractCreate(INNER_CONTRACT)
								.bytecode(INNER_CONTRACT)
								.gas(100_000),
						cryptoCreate(THE_ACCOUNT)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												tokenAssociate(THE_ACCOUNT, VANILLA_TOKEN),
												contractCreate(OUTER_CONTRACT, ContractResources.NESTED_ASSOCIATE_DISSOCIATE_CONTRACT_CONSTRUCTOR,
														getNestedContractAddress(INNER_CONTRACT, spec))
														.bytecode(OUTER_CONTRACT)
														.gas(100_000),
												contractCall(OUTER_CONTRACT, STATIC_DISSOCIATE_CALL_ABI,
														asAddress(accountID.get()), asAddress(vanillaTokenTokenID.get()))
														.payingWith(GENESIS)
														.via("staticDissociateCallTxn")
														.hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
														.gas(5_000_000),
												getTxnRecord("staticDissociateCallTxn").andAllChildRecords().logged()
										)
						)
				).then(
						getAccountInfo(THE_ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN))
				);
	}

	public HapiApiSpec multiplePrecompileDissociationsWithSigsForFungibleWorks() {
		final var theAccount = "anybody";
		final var theContract = "associateDissociateContract";
		final var multiKey = "purpose";

		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();
		final AtomicReference<TokenID> freezeKeyOnTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> knowableTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("multiplePrecompileDissociationsWithSigsForFungibleWorks")
				.given(
						newKeyNamed(multiKey),
						fileCreate(theContract)
								.path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						cryptoCreate(theAccount)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY)
								.balance(0L)
								.exposingCreatedIdTo(treasuryID::set),
						tokenCreate(FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(TOTAL_SUPPLY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.exposingCreatedIdTo(id -> freezeKeyOnTokenID.set(asToken(id))),
						tokenCreate(KNOWABLE_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(TOTAL_SUPPLY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.exposingCreatedIdTo(id -> knowableTokenTokenID.set(asToken(id)))
				)
				.when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(theContract)
														.bytecode(theContract)
														.gas(100_000),
												tokenAssociate(theAccount,
														List.of(FREEZABLE_TOKEN_ON_BY_DEFAULT,
																KNOWABLE_TOKEN)),
												getAccountInfo(theAccount)
														.hasToken(relationshipWith(FREEZABLE_TOKEN_ON_BY_DEFAULT)),
												getAccountInfo(theAccount)
														.hasToken(relationshipWith(KNOWABLE_TOKEN)),
												contractCall(theContract, MULTIPLE_TOKENS_DISSOCIATE,
														asAddress(accountID.get()), List.of(
																asAddress(freezeKeyOnTokenID.get()),
																asAddress(knowableTokenTokenID.get())))
														.payingWith(theAccount)
														.via("multipleDissociationsTxn")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(SUCCESS),
												getTxnRecord("multipleDissociationsTxn").andAllChildRecords().logged()
										)
						)
				).then(
						getAccountInfo(theAccount)
								.hasNoTokenRelationship(FREEZABLE_TOKEN_ON_BY_DEFAULT),
						getAccountInfo(theAccount)
								.hasNoTokenRelationship(KNOWABLE_TOKEN)
				);
	}

	//	TODO: Test is failing due to potential bug. Investigate further.
	private HapiApiSpec nestedDissociateWorksAsExpected() {
		final var theAccount = "anybody";
		final var outerContract = "AssociateDissociateContract";
		final var nestedContract = "NestedAssociateDissociateContract";
		final var multiKey = "purpose";

		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("nestedDissociateWorksAsExpected")
				.given(
						newKeyNamed(multiKey),
						fileCreate(outerContract).path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						fileCreate(nestedContract).path(ContractResources.NESTED_ASSOCIATE_DISSOCIATE_CONTRACT),
						contractCreate(outerContract)
								.bytecode(outerContract)
								.gas(100_000),
						cryptoCreate(theAccount)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id)))
				)
				.when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(nestedContract, ContractResources.NESTED_ASSOCIATE_DISSOCIATE_CONTRACT_CONSTRUCTOR,
														getNestedContractAddress(outerContract, spec))
														.bytecode(nestedContract)
														.gas(100_000),
												tokenAssociate(theAccount, VANILLA_TOKEN),
												contractCall(nestedContract, NESTED_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(vanillaTokenTokenID.get()))
														.payingWith(theAccount)
														.via("nestedDissociateAfterAssociateTxn")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("nestedDissociateAfterAssociateTxn").andAllChildRecords().logged()
										)
						)
				).then(
						getAccountInfo(theAccount).hasToken(relationshipWith(VANILLA_TOKEN))
				);
	}

	public HapiApiSpec dissociatePrecompileWithSigsForFungibleWorks() {
		final var theAccount = "anybody";
		final var theContract = "associateDissociateContract";
		final var multiKey = "purpose";

		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();
		final AtomicReference<TokenID> freezeKeyOnTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> tk1TokenID = new AtomicReference<>();

		return defaultHapiSpec("DissociatePrecompileWithSigsForFungibleWorks")
				.given(
						newKeyNamed(multiKey),
						newKeyNamed("kycKey"),
						newKeyNamed("freezeKey"),
						fileCreate(theContract)
								.path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						cryptoCreate(theAccount)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY)
								.balance(0L)
								.exposingCreatedIdTo(treasuryID::set),
						tokenCreate(FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(TOTAL_SUPPLY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.freezeKey("freezeKey")
								.freezeDefault(true)
								.exposingCreatedIdTo(id -> freezeKeyOnTokenID.set(asToken(id))),
						tokenCreate(KNOWABLE_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.kycKey("kycKey"),
						tokenCreate("tkn1")
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.exposingCreatedIdTo(id -> tk1TokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate("AssociateDissociate")
														.bytecode(theContract)
														.gas(100_000),
												contractCall("AssociateDissociate", SINGLE_TOKEN_DISSOCIATE,
														asAddress(treasuryID.get()), asAddress(tk1TokenID.get()))
														.payingWith(theAccount)
														.via("tk1Txn")
														.alsoSigningWithFullPrefix(multiKey, TOKEN_TREASURY)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("tk1Txn").andAllChildRecords().logged(),
												contractCall("AssociateDissociate", SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeOnTxn")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("freezeOnTxn").andAllChildRecords().logged(),
												tokenAssociate(theAccount, FREEZABLE_TOKEN_ON_BY_DEFAULT, KNOWABLE_TOKEN),
												contractCall("AssociateDissociate", SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOnTxn")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("freezeKeyOnTxn").andAllChildRecords().logged(),
												tokenUnfreeze(FREEZABLE_TOKEN_ON_BY_DEFAULT, theAccount),
												cryptoTransfer(
														moving(1, FREEZABLE_TOKEN_ON_BY_DEFAULT)
																.between(TOKEN_TREASURY, theAccount)),
												contractCall("AssociateDissociate", SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOnTxn2")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("freezeKeyOnTxn2").andAllChildRecords().logged(),
												cryptoTransfer(
														moving(1, FREEZABLE_TOKEN_ON_BY_DEFAULT)
																.between(theAccount, TOKEN_TREASURY)),
												contractCall("AssociateDissociate", SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOnTxn3")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(SUCCESS),
												getTxnRecord("freezeKeyOnTxn3").andAllChildRecords().logged()
										)
						)
				).then(
						getAccountInfo(theAccount)
								.hasToken(relationshipWith(KNOWABLE_TOKEN))
								.hasNoTokenRelationship(FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.logged()
				);
	}

	public HapiApiSpec dissociatePrecompileWitContractIdSigForFungibleWorks() {
		final var theAccount = "anybody";
		final var theContract = "associateDissociateContract";
		final var contractKeyShape = DELEGATE_CONTRACT;
		final var contractKey = "meaning";
		final var multiKey = "purpose";

		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();
		final AtomicReference<TokenID> freezeKeyOnTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> tk1TokenID = new AtomicReference<>();

		return defaultHapiSpec("DissociatePrecompileWitContractIdSigForFungibleWorks")
				.given(
						newKeyNamed("kycKey"),
						newKeyNamed("freezeKey"),
						newKeyNamed(multiKey),
						fileCreate(theContract)
								.path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						cryptoCreate(theAccount)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY)
								.balance(0L)
								.exposingCreatedIdTo(treasuryID::set),
						tokenCreate(FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.initialSupply(TOTAL_SUPPLY)
								.freezeKey("freezeKey")
								.freezeDefault(true)
								.exposingCreatedIdTo(id -> freezeKeyOnTokenID.set(asToken(id))),
						tokenCreate(KNOWABLE_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.kycKey("kycKey"),
						tokenCreate("tkn1")
								.tokenType(FUNGIBLE_COMMON)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> tk1TokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate("AssociateDissociate")
														.bytecode(theContract)
														.gas(100_000),
												newKeyNamed(contractKey)
														.shape(contractKeyShape.signedWith("AssociateDissociate")),
												tokenUpdate("tkn1")
														.supplyKey(contractKey),
												getTokenInfo("tkn1").logged(),
												contractCall("AssociateDissociate", SINGLE_TOKEN_DISSOCIATE,
														asAddress(treasuryID.get()), asAddress(tk1TokenID.get()))
														.payingWith(theAccount)
														.via("tk1Txn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("tk1Txn").andAllChildRecords().logged(),
												tokenUpdate(FREEZABLE_TOKEN_ON_BY_DEFAULT)
														.supplyKey(contractKey),
												getTokenInfo(FREEZABLE_TOKEN_ON_BY_DEFAULT).logged(),
												contractCall("AssociateDissociate", SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeOnTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("freezeOnTxn").andAllChildRecords().logged(),
												tokenAssociate(theAccount, FREEZABLE_TOKEN_ON_BY_DEFAULT, KNOWABLE_TOKEN),
												contractCall("AssociateDissociate", SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOnTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("freezeKeyOnTxn").andAllChildRecords().logged(),
												tokenUnfreeze(FREEZABLE_TOKEN_ON_BY_DEFAULT, theAccount),
												cryptoTransfer(
														moving(1, FREEZABLE_TOKEN_ON_BY_DEFAULT)
																.between(TOKEN_TREASURY, theAccount)),
												contractCall("AssociateDissociate", SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOnTxn2")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("freezeKeyOnTxn2").andAllChildRecords().logged(),
												cryptoTransfer(
														moving(1, FREEZABLE_TOKEN_ON_BY_DEFAULT)
																.between(theAccount, TOKEN_TREASURY)),
												contractCall("AssociateDissociate", SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOnTxn3")
														.hasKnownStatus(SUCCESS),
												getTxnRecord("freezeKeyOnTxn3").andAllChildRecords().logged()
										)
						)
				).then(
						getAccountInfo(theAccount)
								.hasToken(relationshipWith(KNOWABLE_TOKEN))
								.hasNoTokenRelationship(FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.logged()
				);
	}

	public HapiApiSpec dissociatePrecompileWithSigsForNFTWorks() {
		final var theAccount = "anybody";
		final var theContract = "associateDissociateContract";
		final var multiKey = "purpose";

		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();
		final AtomicReference<TokenID> freezeKeyOnTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> tk1TokenID = new AtomicReference<>();

		return defaultHapiSpec("DissociatePrecompileWithSigsForNFTWorks")
				.given(
						newKeyNamed(multiKey),
						newKeyNamed("kycKey"),
						newKeyNamed("freezeKey"),
						fileCreate(theContract)
								.path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						cryptoCreate(theAccount)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY)
								.balance(0L)
								.exposingCreatedIdTo(treasuryID::set),
						tokenCreate(FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.freezeKey("freezeKey")
								.freezeDefault(true)
								.exposingCreatedIdTo(id -> freezeKeyOnTokenID.set(asToken(id))),
						mintToken(FREEZABLE_TOKEN_ON_BY_DEFAULT, List.of(metadata("memo"))),
						tokenCreate(KNOWABLE_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.kycKey("kycKey"),
						tokenCreate("tkn1")
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.exposingCreatedIdTo(id -> tk1TokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate("AssociateDissociate")
														.bytecode(theContract)
														.gas(100_000),
												contractCall("AssociateDissociate", SINGLE_TOKEN_DISSOCIATE,
														asAddress(treasuryID.get()), asAddress(tk1TokenID.get()))
														.payingWith(theAccount)
														.via("tk1Txn")
														.alsoSigningWithFullPrefix(multiKey, TOKEN_TREASURY)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("tk1Txn").andAllChildRecords().logged(),
												contractCall("AssociateDissociate", SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeOnTxn")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("freezeOnTxn").andAllChildRecords().logged(),
												tokenAssociate(theAccount, FREEZABLE_TOKEN_ON_BY_DEFAULT, KNOWABLE_TOKEN),
												contractCall("AssociateDissociate", SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOnTxn")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("freezeKeyOnTxn").andAllChildRecords().logged(),
												tokenUnfreeze(FREEZABLE_TOKEN_ON_BY_DEFAULT, theAccount),
												cryptoTransfer(movingUnique(FREEZABLE_TOKEN_ON_BY_DEFAULT, 1)
														.between(TOKEN_TREASURY, theAccount)),
												contractCall("AssociateDissociate", SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOnTxn2")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("freezeKeyOnTxn2").andAllChildRecords().logged(),
												cryptoTransfer(movingUnique(FREEZABLE_TOKEN_ON_BY_DEFAULT, 1)
														.between(theAccount, TOKEN_TREASURY)),
												contractCall("AssociateDissociate", SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOnTxn3")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(SUCCESS),
												getTxnRecord("freezeKeyOnTxn3").andAllChildRecords().logged()
										)
						)
				).then(
						getAccountInfo(theAccount)
								.hasToken(relationshipWith(KNOWABLE_TOKEN))
								.hasNoTokenRelationship(FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.logged()
				);
	}

	public HapiApiSpec dissociatePrecompileWitContractIdSigForNFTWorks() {
		final var theAccount = "anybody";
		final var theContract = "associateDissociateContract";
		final var contractKeyShape = DELEGATE_CONTRACT;
		final var contractKey = "meaning";
		final var multiKey = "purpose";

		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();
		final AtomicReference<TokenID> freezeKeyOnTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> tk1TokenID = new AtomicReference<>();

		return defaultHapiSpec("DissociatePrecompileWitContractIdSigForNFTWorks")
				.given(
						newKeyNamed("kycKey"),
						newKeyNamed("freezeKey"),
						newKeyNamed(multiKey),
						fileCreate(theContract)
								.path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						cryptoCreate(theAccount)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY)
								.balance(0L)
								.exposingCreatedIdTo(treasuryID::set),
						tokenCreate(FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.initialSupply(0)
								.freezeKey("freezeKey")
								.freezeDefault(true)
								.exposingCreatedIdTo(id -> freezeKeyOnTokenID.set(asToken(id))),
						mintToken(FREEZABLE_TOKEN_ON_BY_DEFAULT, List.of(metadata("memo"))),
						tokenCreate(KNOWABLE_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.kycKey("kycKey"),
						tokenCreate("tkn1")
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> tk1TokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate("AssociateDissociate")
														.bytecode(theContract)
														.gas(100_000),
												newKeyNamed(contractKey)
														.shape(contractKeyShape.signedWith("AssociateDissociate")),
												tokenUpdate("tkn1")
														.supplyKey(contractKey),
												getTokenInfo("tkn1").logged(),
												contractCall("AssociateDissociate", SINGLE_TOKEN_DISSOCIATE,
														asAddress(treasuryID.get()), asAddress(tk1TokenID.get()))
														.payingWith(theAccount)
														.via("tk1Txn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("tk1Txn").andAllChildRecords().logged(),
												tokenUpdate(FREEZABLE_TOKEN_ON_BY_DEFAULT)
														.supplyKey(contractKey),
												getTokenInfo(FREEZABLE_TOKEN_ON_BY_DEFAULT).logged(),
												contractCall("AssociateDissociate", SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeOnTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("freezeOnTxn").andAllChildRecords().logged(),
												tokenAssociate(theAccount, FREEZABLE_TOKEN_ON_BY_DEFAULT, KNOWABLE_TOKEN),
												contractCall("AssociateDissociate", SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOnTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("freezeKeyOnTxn").andAllChildRecords().logged(),
												tokenUnfreeze(FREEZABLE_TOKEN_ON_BY_DEFAULT, theAccount),
												cryptoTransfer(movingUnique(FREEZABLE_TOKEN_ON_BY_DEFAULT, 1)
														.between(TOKEN_TREASURY, theAccount)),
												contractCall("AssociateDissociate", SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOnTxn2")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("freezeKeyOnTxn2").andAllChildRecords().logged(),
												cryptoTransfer(movingUnique(FREEZABLE_TOKEN_ON_BY_DEFAULT, 1)
														.between(theAccount, TOKEN_TREASURY)),
												contractCall("AssociateDissociate", SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOnTxn3")
														.hasKnownStatus(SUCCESS),
												getTxnRecord("freezeKeyOnTxn3").andAllChildRecords().logged()
										)
						)
				).then(
						getAccountInfo(theAccount)
								.hasToken(relationshipWith(KNOWABLE_TOKEN))
								.hasNoTokenRelationship(FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.logged()
				);
	}

	public HapiApiSpec dissociatePrecompileHasExpectedSemanticsForDeletedTokens() {
		final String tbdUniqToken = "UniqToBeDeleted";
		final String zeroBalanceFrozen = "0bFrozen";
		final String zeroBalanceUnfrozen = "0bUnfrozen";
		final String nonZeroBalanceFrozen = "1bFrozen";
		final String nonZeroBalanceUnfrozen = "1bUnfrozen";
		final var theContract = "associateDissociateContract";
		final long initialSupply = 100L;
		final long nonZeroXfer = 10L;
		final var firstMeta = ByteString.copyFrom("FIRST".getBytes(StandardCharsets.UTF_8));
		final var secondMeta = ByteString.copyFrom("SECOND".getBytes(StandardCharsets.UTF_8));
		final var thirdMeta = ByteString.copyFrom("THIRD".getBytes(StandardCharsets.UTF_8));

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
						fileCreate(theContract)
								.path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
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
												contractCreate("AssociateDissociate")
														.bytecode(theContract)
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
												contractCall("AssociateDissociate", SINGLE_TOKEN_DISSOCIATE,
														asAddress(zeroBalanceFrozenID.get()), asAddress(tbdTokenID.get()))
														.payingWith(zeroBalanceFrozen)
														.alsoSigningWithFullPrefix(MULTI_KEY),
												contractCall("AssociateDissociate", SINGLE_TOKEN_DISSOCIATE,
														asAddress(zeroBalanceUnfrozenID.get()), asAddress(tbdTokenID.get()))
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.payingWith(zeroBalanceUnfrozen),
												contractCall("AssociateDissociate", SINGLE_TOKEN_DISSOCIATE,
														asAddress(nonZeroBalanceFrozenID.get()), asAddress(tbdTokenID.get()))
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.payingWith(nonZeroBalanceFrozen),
												contractCall("AssociateDissociate", SINGLE_TOKEN_DISSOCIATE,
														asAddress(nonZeroBalanceUnfrozenID.get()), asAddress(tbdTokenID.get()))
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.payingWith(nonZeroBalanceUnfrozen),
												contractCall("AssociateDissociate", SINGLE_TOKEN_DISSOCIATE,
														asAddress(treasuryID.get()), asAddress(tbdUniqueTokenID.get()))
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.payingWith(TOKEN_TREASURY)
										)
						)
				).then(
						getAccountInfo(zeroBalanceFrozen)
								.hasNoTokenRelationship(TBD_TOKEN),
						getAccountInfo(zeroBalanceUnfrozen)
								.hasNoTokenRelationship(TBD_TOKEN),
						getAccountInfo(nonZeroBalanceFrozen)
								.hasNoTokenRelationship(TBD_TOKEN),
						getAccountInfo(nonZeroBalanceUnfrozen)
								.hasNoTokenRelationship(TBD_TOKEN),
						getAccountInfo(TOKEN_TREASURY)
								.hasToken(relationshipWith(TBD_TOKEN))
								.hasNoTokenRelationship(tbdUniqToken)
								.hasOwnedNfts(0),
						getAccountBalance(TOKEN_TREASURY)
								.hasTokenBalance(TBD_TOKEN, initialSupply - 2 * nonZeroXfer)
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

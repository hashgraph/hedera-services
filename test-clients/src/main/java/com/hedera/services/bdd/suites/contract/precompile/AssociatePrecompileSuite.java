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
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.token.TokenAssociationSpecs;
import com.hedera.services.legacy.core.CommonUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asDotDelimitedLongArray;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.DELEGATE_ASSOCIATE_CALL_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.MULTIPLE_TOKENS_ASSOCIATE;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.NESTED_TOKEN_ASSOCIATE;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.SINGLE_TOKEN_ASSOCIATE;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.STATIC_ASSOCIATE_CALL_ABI;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.FREEZABLE_TOKEN_OFF_BY_DEFAULT;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.FREEZABLE_TOKEN_ON_BY_DEFAULT;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.KNOWABLE_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.FreezeNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Frozen;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Unfrozen;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.KycNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.Revoked;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class AssociatePrecompileSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(AssociatePrecompileSuite.class);
	private static final long TOTAL_SUPPLY = 1_000;
	private static final String TOKEN_TREASURY = "treasury";
	private static final String OUTER_CONTRACT = "AssociateDissociateContract";
	private static final String INNER_CONTRACT = "NestedAssociateDissociateContract";
	private static final String THE_ACCOUNT = "anybody";

	public static void main(String... args) {
		new AssociatePrecompileSuite().runSuiteAsync();
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
				delegateCallForAssociatePrecompileSignedWithContractKeyFails(),
				staticCallForAssociatePrecompileFails()
		);
	}

	List<HapiApiSpec> positiveSpecs() {
		return List.of(
				delegateCallForAssociatePrecompileSignedWithDelegateContractKeyWorks(),
				associatePrecompileWithSignatureWorksForFungible(),
				associatePrecompileWithContractIdSignatureWorksForFungible(),
				associatePrecompileWithSignatureWorksForNFT(),
				associatePrecompileWithContractIdSignatureWorksForNFT(),
				nestedAssociateWorksAsExpected(),
				multipleAssociatePrecompileWithSignatureWorksForFungible()
		);
	}

	private HapiApiSpec delegateCallForAssociatePrecompileSignedWithContractKeyFails() {
		final var contractKey = "Contract key";
		final var contractKeyShape = KeyShape.threshOf(1, SIMPLE, CONTRACT);

		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("DelegateCallForAssociatePrecompileSignedWithContractKeyFails")
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
												contractCall(OUTER_CONTRACT, DELEGATE_ASSOCIATE_CALL_ABI,
														asAddress(accountID.get()), asAddress(vanillaTokenTokenID.get()))
														.payingWith(GENESIS)
														.via("delegateAssociateCallWithContractKeyTxn")
														.hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
														.gas(5_000_000),
												getTxnRecord("delegateAssociateCallWithContractKeyTxn").andAllChildRecords().logged()
										)
						)
				).then(
						getAccountInfo(THE_ACCOUNT).hasNoTokenRelationship(VANILLA_TOKEN)
				);
	}

	private HapiApiSpec delegateCallForAssociatePrecompileSignedWithDelegateContractKeyWorks() {
		final var delegateKey = "Delegate contract key";
		final var delegateContractKeyShape = KeyShape.threshOf(1, SIMPLE, DELEGATE_CONTRACT);

		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("DelegateCallForAssociatePrecompileSignedWithDelegateContractKeyWorks")
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
												newKeyNamed(delegateKey).shape(delegateContractKeyShape.signedWith(sigs(ON, OUTER_CONTRACT))),
												cryptoUpdate(THE_ACCOUNT).key(delegateKey),
												contractCall(OUTER_CONTRACT, DELEGATE_ASSOCIATE_CALL_ABI,
														asAddress(accountID.get()), asAddress(vanillaTokenTokenID.get()))
														.payingWith(GENESIS)
														.via("delegateAssociateCallWithDelegateContractKeyTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS)
														.gas(5_000_000),
												getTxnRecord("delegateAssociateCallWithDelegateContractKeyTxn").andAllChildRecords().logged()
										)
						)
				).then(
						getAccountInfo(THE_ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN))
				);
	}

	private HapiApiSpec staticCallForAssociatePrecompileFails() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("StaticCallForAssociatePrecompileFails")
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
												contractCreate(OUTER_CONTRACT, ContractResources.NESTED_ASSOCIATE_DISSOCIATE_CONTRACT_CONSTRUCTOR,
														getNestedContractAddress(INNER_CONTRACT, spec))
														.bytecode(OUTER_CONTRACT)
														.gas(100_000),
												contractCall(OUTER_CONTRACT, STATIC_ASSOCIATE_CALL_ABI,
														asAddress(accountID.get()), asAddress(vanillaTokenTokenID.get()))
														.payingWith(THE_ACCOUNT)
														.via("staticAssociateCallTxn")
														.hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
														.gas(5_000_000),
												getTxnRecord("staticAssociateCallTxn").andAllChildRecords().logged()
										)
						)
				).then(
						getAccountInfo(THE_ACCOUNT).hasNoTokenRelationship(VANILLA_TOKEN)
				);
	}

	private HapiApiSpec multipleAssociatePrecompileWithSignatureWorksForFungible() {
		final var theAccount = "anybody";
		final var theContract = "associateDissociateContract";
		final var multiKey = "purpose";

		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> freezeKeyOnTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> freezeKeyOffTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> knowableTokenTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("multipleAssociatePrecompileWithSignatureWorksForFungible")
				.given(
						newKeyNamed(multiKey),
						newKeyNamed("kycKey"),
						newKeyNamed("freezeKey"),
						fileCreate(theContract)
								.path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						cryptoCreate(theAccount)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate(FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(TOTAL_SUPPLY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.freezeKey("freezeKey")
								.freezeDefault(true)
								.exposingCreatedIdTo(id -> freezeKeyOnTokenID.set(asToken(id))),
						tokenCreate(FREEZABLE_TOKEN_OFF_BY_DEFAULT)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.freezeKey("freezeKey")
								.freezeDefault(false)
								.exposingCreatedIdTo(id -> freezeKeyOffTokenID.set(asToken(id))),
						tokenCreate(KNOWABLE_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.kycKey("kycKey")
								.exposingCreatedIdTo(id -> knowableTokenTokenID.set(asToken(id))),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(theContract)
														.bytecode(theContract)
														.gas(100_000),
												contractCall(theContract, MULTIPLE_TOKENS_ASSOCIATE,
														asAddress(accountID.get()),
														List.of(
																asAddress(freezeKeyOnTokenID.get()),
																asAddress(freezeKeyOffTokenID.get()),
																asAddress(knowableTokenTokenID.get()),
																asAddress(vanillaTokenTokenID.get()))
												)
														.payingWith(theAccount)
														.via("MultipleTokensAssociationsTxn")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("MultipleTokensAssociationsTxn").andAllChildRecords().logged()
										)
						)
				).then(
						getAccountInfo(theAccount)
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

	private HapiApiSpec nestedAssociateWorksAsExpected() {
		final var theAccount = "anybody";
		final var outerContract = "AssociateDissociateContract";
		final var nestedContract = "NestedAssociateDissociateContract";
		final var multiKey = "purpose";

		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();


		return defaultHapiSpec("nestedAssociateWorksAsExpected")
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
												contractCall(nestedContract, NESTED_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(vanillaTokenTokenID.get()))
														.payingWith(theAccount)
														.via("nestedDissociateAfterAssociateTxn")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("nestedDissociateAfterAssociateTxn").andAllChildRecords().logged()
										)
						)

				).then(
						getAccountInfo(theAccount)
								.hasNoTokenRelationship(VANILLA_TOKEN)
				);
	}

	private HapiApiSpec associatePrecompileWithSignatureWorksForFungible() {
		final var theAccount = "anybody";
		final var theContract = "associateDissociateContract";
		final var multiKey = "purpose";

		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> freezeKeyOnTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> freezeKeyOffTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> knowableTokenTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("associatePrecompileWithSignatureWorksForFungible")
				.given(
						newKeyNamed(multiKey),
						newKeyNamed("kycKey"),
						newKeyNamed("freezeKey"),
						fileCreate(theContract)
								.path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						cryptoCreate(theAccount)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate(FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(TOTAL_SUPPLY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.freezeKey("freezeKey")
								.freezeDefault(true)
								.exposingCreatedIdTo(id -> freezeKeyOnTokenID.set(asToken(id))),
						tokenCreate(FREEZABLE_TOKEN_OFF_BY_DEFAULT)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.freezeKey("freezeKey")
								.freezeDefault(false)
								.exposingCreatedIdTo(id -> freezeKeyOffTokenID.set(asToken(id))),
						tokenCreate(KNOWABLE_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.kycKey("kycKey")
								.exposingCreatedIdTo(id -> knowableTokenTokenID.set(asToken(id))),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate("AssociateDissociate")
														.bytecode(theContract)
														.gas(100_000),
												contractCall("AssociateDissociate", SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOnTxn")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("freezeKeyOnTxn").andAllChildRecords().logged(),
												contractCall("AssociateDissociate", SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(freezeKeyOffTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOffTxn")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("freezeKeyOffTxn").andAllChildRecords().logged(),
												contractCall("AssociateDissociate", SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(knowableTokenTokenID.get()))
														.payingWith(theAccount)
														.via("knowableTokenTxn")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("knowableTokenTxn").andAllChildRecords().logged(),
												contractCall("AssociateDissociate", SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(vanillaTokenTokenID.get()))
														.payingWith(theAccount)
														.via("vanillaTokenTxn")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("vanillaTokenTxn").andAllChildRecords().logged()
										)
						)
				).then(
						getAccountInfo(theAccount)
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

	private HapiApiSpec associatePrecompileWithContractIdSignatureWorksForFungible() {
		final var theAccount = "anybody";
		final var theContract = "associateDissociateContract";
		final var multiKey = "purpose";
		final var contractKey = "meaning";

		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> freezeKeyOnTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> freezeKeyOffTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> knowableTokenTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("associatePrecompileWithContractIdSignatureWorksForFungible")
				.given(
						newKeyNamed("kycKey"),
						newKeyNamed("freezeKey"),
						newKeyNamed(multiKey),
						fileCreate(theContract)
								.path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						cryptoCreate(theAccount)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate(FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(TOTAL_SUPPLY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.freezeKey("freezeKey")
								.freezeDefault(true)
								.exposingCreatedIdTo(id -> freezeKeyOnTokenID.set(asToken(id))),
						tokenCreate(FREEZABLE_TOKEN_OFF_BY_DEFAULT)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.freezeKey("freezeKey")
								.freezeDefault(false)
								.exposingCreatedIdTo(id -> freezeKeyOffTokenID.set(asToken(id))),
						tokenCreate(KNOWABLE_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.kycKey("kycKey")
								.exposingCreatedIdTo(id -> knowableTokenTokenID.set(asToken(id))),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate("AssociateDissociate")
														.bytecode(theContract)
														.gas(100_000),
												newKeyNamed(contractKey)
														.shape(DELEGATE_CONTRACT.signedWith("AssociateDissociate")),
												tokenUpdate(FREEZABLE_TOKEN_ON_BY_DEFAULT)
														.supplyKey(contractKey),
												getTokenInfo(FREEZABLE_TOKEN_ON_BY_DEFAULT).logged(),
												contractCall("AssociateDissociate", SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOnTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("freezeKeyOnTxn").andAllChildRecords().logged(),
												tokenUpdate(FREEZABLE_TOKEN_OFF_BY_DEFAULT)
														.supplyKey(contractKey),
												getTokenInfo(FREEZABLE_TOKEN_OFF_BY_DEFAULT).logged(),
												contractCall("AssociateDissociate", SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(freezeKeyOffTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOffTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("freezeKeyOffTxn").andAllChildRecords().logged(),
												tokenUpdate(KNOWABLE_TOKEN)
														.supplyKey(contractKey),
												getTokenInfo(KNOWABLE_TOKEN).logged(),
												contractCall("AssociateDissociate", SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(knowableTokenTokenID.get()))
														.payingWith(theAccount)
														.via("knowableTokenTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("knowableTokenTxn").andAllChildRecords().logged(),
												tokenUpdate(VANILLA_TOKEN)
														.supplyKey(contractKey),
												getTokenInfo(VANILLA_TOKEN).logged(),
												contractCall("AssociateDissociate", SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(vanillaTokenTokenID.get()))
														.payingWith(theAccount)
														.via("vanillaTokenTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("vanillaTokenTxn").andAllChildRecords().logged()
										)
						)
				).then(
						getAccountInfo(theAccount)
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

	private HapiApiSpec associatePrecompileWithSignatureWorksForNFT() {
		final var theAccount = "anybody";
		final var theContract = "associateDissociateContract";
		final var multiKey = "purpose";

		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> freezeKeyOnTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> freezeKeyOffTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> knowableTokenTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("associatePrecompileWithSignatureWorksForNFT")
				.given(
						newKeyNamed(multiKey),
						newKeyNamed("kycKey"),
						newKeyNamed("freezeKey"),
						fileCreate(theContract)
								.path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						cryptoCreate(theAccount)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate(FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.freezeKey("freezeKey")
								.freezeDefault(true)
								.exposingCreatedIdTo(id -> freezeKeyOnTokenID.set(asToken(id))),
						tokenCreate(FREEZABLE_TOKEN_OFF_BY_DEFAULT)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.freezeKey("freezeKey")
								.freezeDefault(false)
								.exposingCreatedIdTo(id -> freezeKeyOffTokenID.set(asToken(id))),
						tokenCreate(KNOWABLE_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.kycKey("kycKey")
								.exposingCreatedIdTo(id -> knowableTokenTokenID.set(asToken(id))),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate("AssociateDissociate")
														.bytecode(theContract)
														.gas(100_000),
												contractCall("AssociateDissociate", SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOnTxn")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("freezeKeyOnTxn").andAllChildRecords().logged(),
												contractCall("AssociateDissociate", SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(freezeKeyOffTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOffTxn")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("freezeKeyOffTxn").andAllChildRecords().logged(),
												contractCall("AssociateDissociate", SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(knowableTokenTokenID.get()))
														.payingWith(theAccount)
														.via("knowableTokenTxn")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("knowableTokenTxn").andAllChildRecords().logged(),
												contractCall("AssociateDissociate", SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(vanillaTokenTokenID.get()))
														.payingWith(theAccount)
														.via("vanillaTokenTxn")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("vanillaTokenTxn").andAllChildRecords().logged()
										)
						)
				).then(
						getAccountInfo(theAccount)
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

	private HapiApiSpec associatePrecompileWithContractIdSignatureWorksForNFT() {
		final var theAccount = "anybody";
		final var theContract = "associateDissociateContract";
		final var multiKey = "purpose";
		final var contractKey = "meaning";

		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> freezeKeyOnTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> freezeKeyOffTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> knowableTokenTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("associatePrecompileWithContractIdSignatureWorksForNFT")
				.given(
						newKeyNamed("kycKey"),
						newKeyNamed("freezeKey"),
						newKeyNamed(multiKey),
						fileCreate(theContract)
								.path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						cryptoCreate(theAccount)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate(FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.freezeKey("freezeKey")
								.freezeDefault(true)
								.exposingCreatedIdTo(id -> freezeKeyOnTokenID.set(asToken(id))),
						tokenCreate(FREEZABLE_TOKEN_OFF_BY_DEFAULT)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.freezeKey("freezeKey")
								.freezeDefault(false)
								.exposingCreatedIdTo(id -> freezeKeyOffTokenID.set(asToken(id))),
						tokenCreate(KNOWABLE_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.kycKey("kycKey")
								.exposingCreatedIdTo(id -> knowableTokenTokenID.set(asToken(id))),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate("AssociateDissociate")
														.bytecode(theContract)
														.gas(100_000),
												newKeyNamed(contractKey)
														.shape(DELEGATE_CONTRACT.signedWith("AssociateDissociate")),
												tokenUpdate(FREEZABLE_TOKEN_ON_BY_DEFAULT)
														.supplyKey(contractKey),
												getTokenInfo(FREEZABLE_TOKEN_ON_BY_DEFAULT).logged(),
												contractCall("AssociateDissociate", SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOnTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("freezeKeyOnTxn").andAllChildRecords().logged(),
												tokenUpdate(FREEZABLE_TOKEN_OFF_BY_DEFAULT)
														.supplyKey(contractKey),
												getTokenInfo(FREEZABLE_TOKEN_OFF_BY_DEFAULT).logged(),
												contractCall("AssociateDissociate", SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(freezeKeyOffTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOffTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("freezeKeyOffTxn").andAllChildRecords().logged(),
												tokenUpdate(KNOWABLE_TOKEN)
														.supplyKey(contractKey),
												getTokenInfo(KNOWABLE_TOKEN).logged(),
												contractCall("AssociateDissociate", SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(knowableTokenTokenID.get()))
														.payingWith(theAccount)
														.via("knowableTokenTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("knowableTokenTxn").andAllChildRecords().logged(),
												tokenUpdate(VANILLA_TOKEN)
														.supplyKey(contractKey),
												getTokenInfo(VANILLA_TOKEN).logged(),
												contractCall("AssociateDissociate", SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(vanillaTokenTokenID.get()))
														.payingWith(theAccount)
														.via("vanillaTokenTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("vanillaTokenTxn").andAllChildRecords().logged()
										)
						)
				).then(
						getAccountInfo(theAccount)
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

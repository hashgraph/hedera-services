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
	private static final KeyShape CONTRACT_KEY_SHAPE = KeyShape.threshOf(1, SIMPLE, CONTRACT);
	private static final KeyShape DELEGATE_CONTRACT_KEY_SHAPE = KeyShape.threshOf(1, SIMPLE, DELEGATE_CONTRACT);
	private static final String TOKEN_TREASURY = "treasury";
	private static final String OUTER_CONTRACT = "Nested Associate/Dissociate Contract";
	private static final String INNER_CONTRACT = "Associate/Dissociate Contract";
	private static final String THE_CONTRACT = "Associate/Dissociate Contract";
	private static final String ACCOUNT = "anybody";
	private static final String FROZEN_TOKEN = "Frozen token";
	private static final String UNFROZEN_TOKEN = "Unfrozen token";
	private static final String KYC_TOKEN = "KYC token";
	private static final String CONTRACT_KEY = "Contract key";
	private static final String DELEGATE_KEY = "Delegate key";
	private static final String FREEZE_KEY = "Freeze key";
	private static final String KYC_KEY = "KYC key";
	private static final String MULTI_KEY = "Multi key";

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
						cryptoCreate(ACCOUNT)
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
												newKeyNamed(CONTRACT_KEY).shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON, OUTER_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(CONTRACT_KEY),
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
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(VANILLA_TOKEN)
				);
	}

	private HapiApiSpec delegateCallForAssociatePrecompileSignedWithDelegateContractKeyWorks() {
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
						cryptoCreate(ACCOUNT)
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
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, OUTER_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
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
						getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN))
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
						cryptoCreate(ACCOUNT)
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
														.payingWith(ACCOUNT)
														.via("staticAssociateCallTxn")
														.hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
														.gas(5_000_000),
												getTxnRecord("staticAssociateCallTxn").andAllChildRecords().logged()
										)
						)
				).then(
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(VANILLA_TOKEN)
				);
	}

	private HapiApiSpec multipleAssociatePrecompileWithSignatureWorksForFungible() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> frozenTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> unfrozenTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> kycTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("multipleAssociatePrecompileWithSignatureWorksForFungible")
				.given(
						fileCreate(THE_CONTRACT)
								.path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						cryptoCreate(ACCOUNT)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate(FROZEN_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(TOTAL_SUPPLY)
								.freezeKey(FREEZE_KEY)
								.freezeDefault(true)
								.exposingCreatedIdTo(id -> frozenTokenID.set(asToken(id))),
						tokenCreate(UNFROZEN_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.freezeKey(FREEZE_KEY)
								.freezeDefault(false)
								.exposingCreatedIdTo(id -> unfrozenTokenID.set(asToken(id))),
						tokenCreate(KYC_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.kycKey(KYC_KEY)
								.exposingCreatedIdTo(id -> kycTokenID.set(asToken(id))),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_CONTRACT)
														.bytecode(THE_CONTRACT)
														.gas(100_000),
												contractCall(THE_CONTRACT, MULTIPLE_TOKENS_ASSOCIATE,
														asAddress(accountID.get()),
														List.of(
																asAddress(frozenTokenID.get()),
																asAddress(unfrozenTokenID.get()),
																asAddress(kycTokenID.get()),
																asAddress(vanillaTokenID.get()))
												)
														.payingWith(ACCOUNT)
														.via("MultipleTokensAssociationsTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("MultipleTokensAssociationsTxn").andAllChildRecords().logged()
										)
						)
				).then(
						getAccountInfo(ACCOUNT)
								.hasToken(relationshipWith(FROZEN_TOKEN).kyc(KycNotApplicable).freeze(Frozen))
								.hasToken(relationshipWith(UNFROZEN_TOKEN).kyc(KycNotApplicable).freeze(Unfrozen))
								.hasToken(relationshipWith(KYC_TOKEN).kyc(Revoked).freeze(FreezeNotApplicable))
								.hasToken(relationshipWith(TokenAssociationSpecs.VANILLA_TOKEN).kyc(KycNotApplicable).freeze(FreezeNotApplicable))
				);
	}

	private HapiApiSpec nestedAssociateWorksAsExpected() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("nestedAssociateWorksAsExpected")
				.given(
						fileCreate(INNER_CONTRACT).path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						fileCreate(OUTER_CONTRACT).path(ContractResources.NESTED_ASSOCIATE_DISSOCIATE_CONTRACT),
						contractCreate(INNER_CONTRACT)
								.bytecode(INNER_CONTRACT)
								.gas(100_000),
						cryptoCreate(ACCOUNT)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
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
												contractCall(OUTER_CONTRACT, NESTED_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(ACCOUNT)
														.via("nestedDissociateAfterAssociateTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("nestedDissociateAfterAssociateTxn").andAllChildRecords().logged()
										)
						)
				).then(
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(VANILLA_TOKEN)
				);
	}

	private HapiApiSpec associatePrecompileWithSignatureWorksForFungible() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> frozenTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> unfrozenTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> kycTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("associatePrecompileWithSignatureWorksForFungible")
				.given(
						newKeyNamed(KYC_KEY),
						newKeyNamed(FREEZE_KEY),
						newKeyNamed(MULTI_KEY),
						fileCreate(THE_CONTRACT)
								.path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						cryptoCreate(ACCOUNT)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate(FROZEN_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(TOTAL_SUPPLY)
								.freezeKey(FREEZE_KEY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.freezeDefault(true)
								.exposingCreatedIdTo(id -> frozenTokenID.set(asToken(id))),
						tokenCreate(UNFROZEN_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.freezeKey(FREEZE_KEY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.freezeDefault(false)
								.exposingCreatedIdTo(id -> unfrozenTokenID.set(asToken(id))),
						tokenCreate(KYC_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.kycKey(KYC_KEY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.exposingCreatedIdTo(id -> kycTokenID.set(asToken(id))),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_CONTRACT)
														.bytecode(THE_CONTRACT)
														.gas(100_000),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(frozenTokenID.get()))
														.payingWith(ACCOUNT)
														.via("frozenTokenAssociateTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("frozenTokenAssociateTxn").andAllChildRecords().logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(unfrozenTokenID.get()))
														.payingWith(ACCOUNT)
														.via("unfrozenTokenAssociateTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("unfrozenTokenAssociateTxn").andAllChildRecords().logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(kycTokenID.get()))
														.payingWith(ACCOUNT)
														.via("kycTokenAssociateTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("kycTokenAssociateTxn").andAllChildRecords().logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(ACCOUNT)
														.via("vanillaTokenAssociateTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("vanillaTokenAssociateTxn").andAllChildRecords().logged()
										)
						)
				).then(
						getAccountInfo(ACCOUNT)
								.hasToken(relationshipWith(FROZEN_TOKEN).kyc(KycNotApplicable).freeze(Frozen))
								.hasToken(relationshipWith(UNFROZEN_TOKEN).kyc(KycNotApplicable).freeze(Unfrozen))
								.hasToken(relationshipWith(KYC_TOKEN).kyc(Revoked).freeze(FreezeNotApplicable))
								.hasToken(relationshipWith(VANILLA_TOKEN).kyc(KycNotApplicable).freeze(FreezeNotApplicable))
				);
	}

	private HapiApiSpec associatePrecompileWithContractIdSignatureWorksForFungible() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> frozenTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> unfrozenTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> kycTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("associatePrecompileWithContractIdSignatureWorksForFungible")
				.given(
						newKeyNamed(KYC_KEY),
						newKeyNamed(FREEZE_KEY),
						newKeyNamed(MULTI_KEY),
						fileCreate(THE_CONTRACT)
								.path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						cryptoCreate(ACCOUNT)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate(FROZEN_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(TOTAL_SUPPLY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.freezeKey(FREEZE_KEY)
								.freezeDefault(true)
								.exposingCreatedIdTo(id -> frozenTokenID.set(asToken(id))),
						tokenCreate(UNFROZEN_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.freezeKey(FREEZE_KEY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.freezeDefault(false)
								.exposingCreatedIdTo(id -> unfrozenTokenID.set(asToken(id))),
						tokenCreate(KYC_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.kycKey(KYC_KEY)
								.exposingCreatedIdTo(id -> kycTokenID.set(asToken(id))),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_CONTRACT)
														.bytecode(THE_CONTRACT)
														.gas(100_000),
												newKeyNamed(CONTRACT_KEY).shape(DELEGATE_CONTRACT.signedWith(THE_CONTRACT)),
												tokenUpdate(FROZEN_TOKEN).supplyKey(CONTRACT_KEY),
												getTokenInfo(FROZEN_TOKEN).logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(frozenTokenID.get()))
														.payingWith(ACCOUNT)
														.via("freezeKeyOnTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("freezeKeyOnTxn").andAllChildRecords().logged(),
												tokenUpdate(UNFROZEN_TOKEN).supplyKey(CONTRACT_KEY),
												getTokenInfo(UNFROZEN_TOKEN).logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(unfrozenTokenID.get()))
														.payingWith(ACCOUNT)
														.via("freezeKeyOffTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("freezeKeyOffTxn").andAllChildRecords().logged(),
												tokenUpdate(KYC_TOKEN).supplyKey(CONTRACT_KEY),
												getTokenInfo(KYC_TOKEN).logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(kycTokenID.get()))
														.payingWith(ACCOUNT)
														.via("knowableTokenTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("knowableTokenTxn").andAllChildRecords().logged(),
												tokenUpdate(VANILLA_TOKEN).supplyKey(CONTRACT_KEY),
												getTokenInfo(VANILLA_TOKEN).logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(ACCOUNT)
														.via("vanillaTokenTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("vanillaTokenTxn").andAllChildRecords().logged()
										)
						)
				).then(
						getAccountInfo(ACCOUNT)
								.hasToken(relationshipWith(FROZEN_TOKEN).kyc(KycNotApplicable).freeze(Frozen))
								.hasToken(relationshipWith(UNFROZEN_TOKEN).kyc(KycNotApplicable).freeze(Unfrozen))
								.hasToken(relationshipWith(KYC_TOKEN).kyc(Revoked).freeze(FreezeNotApplicable))
								.hasToken(relationshipWith(VANILLA_TOKEN).kyc(KycNotApplicable).freeze(FreezeNotApplicable))
				);
	}

	private HapiApiSpec associatePrecompileWithSignatureWorksForNFT() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> frozenTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> unfrozenTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> kycTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("associatePrecompileWithSignatureWorksForNFT")
				.given(
						newKeyNamed(KYC_KEY),
						newKeyNamed(FREEZE_KEY),
						newKeyNamed(MULTI_KEY),
						fileCreate(THE_CONTRACT).path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						cryptoCreate(ACCOUNT)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate(FROZEN_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.freezeKey(FREEZE_KEY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.freezeDefault(true)
								.exposingCreatedIdTo(id -> frozenTokenID.set(asToken(id))),
						tokenCreate(UNFROZEN_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.freezeKey(FREEZE_KEY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.freezeDefault(false)
								.exposingCreatedIdTo(id -> unfrozenTokenID.set(asToken(id))),
						tokenCreate(KYC_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.kycKey(KYC_KEY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.exposingCreatedIdTo(id -> kycTokenID.set(asToken(id))),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.initialSupply(0)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_CONTRACT)
														.bytecode(THE_CONTRACT)
														.gas(100_000),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(frozenTokenID.get()))
														.payingWith(ACCOUNT)
														.via("freezeKeyOnTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("freezeKeyOnTxn").andAllChildRecords().logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(unfrozenTokenID.get()))
														.payingWith(ACCOUNT)
														.via("freezeKeyOffTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("freezeKeyOffTxn").andAllChildRecords().logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(kycTokenID.get()))
														.payingWith(ACCOUNT)
														.via("knowableTokenTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("knowableTokenTxn").andAllChildRecords().logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(ACCOUNT)
														.via("vanillaTokenTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("vanillaTokenTxn").andAllChildRecords().logged()
										)
						)
				).then(
						getAccountInfo(ACCOUNT)
								.hasToken(relationshipWith(FROZEN_TOKEN).kyc(KycNotApplicable).freeze(Frozen))
								.hasToken(relationshipWith(UNFROZEN_TOKEN).kyc(KycNotApplicable).freeze(Unfrozen))
								.hasToken(relationshipWith(KYC_TOKEN).kyc(Revoked).freeze(FreezeNotApplicable))
								.hasToken(relationshipWith(VANILLA_TOKEN).kyc(KycNotApplicable).freeze(FreezeNotApplicable))
				);
	}

	private HapiApiSpec associatePrecompileWithContractIdSignatureWorksForNFT() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> frozenTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> unfrozenTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> kycTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("associatePrecompileWithContractIdSignatureWorksForNFT")
				.given(
						newKeyNamed(KYC_KEY),
						newKeyNamed(FREEZE_KEY),
						newKeyNamed(MULTI_KEY),
						fileCreate(THE_CONTRACT).path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						cryptoCreate(ACCOUNT)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate(FROZEN_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.freezeKey(FREEZE_KEY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.freezeDefault(true)
								.exposingCreatedIdTo(id -> frozenTokenID.set(asToken(id))),
						tokenCreate(UNFROZEN_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.freezeKey(FREEZE_KEY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.freezeDefault(false)
								.exposingCreatedIdTo(id -> unfrozenTokenID.set(asToken(id))),
						tokenCreate(KYC_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.kycKey(KYC_KEY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.exposingCreatedIdTo(id -> kycTokenID.set(asToken(id))),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_CONTRACT)
														.bytecode(THE_CONTRACT)
														.gas(100_000),
												newKeyNamed(CONTRACT_KEY).shape(DELEGATE_CONTRACT.signedWith(THE_CONTRACT)),
												tokenUpdate(FROZEN_TOKEN).supplyKey(CONTRACT_KEY),
												getTokenInfo(FROZEN_TOKEN).logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(frozenTokenID.get()))
														.payingWith(ACCOUNT)
														.via("freezeKeyOnTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("freezeKeyOnTxn").andAllChildRecords().logged(),
												tokenUpdate(UNFROZEN_TOKEN).supplyKey(CONTRACT_KEY),
												getTokenInfo(UNFROZEN_TOKEN).logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(frozenTokenID.get()))
														.payingWith(ACCOUNT)
														.via("freezeKeyOffTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("freezeKeyOffTxn").andAllChildRecords().logged(),
												tokenUpdate(KYC_TOKEN).supplyKey(CONTRACT_KEY),
												getTokenInfo(KYC_TOKEN).logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(kycTokenID.get()))
														.payingWith(ACCOUNT)
														.via("knowableTokenTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("knowableTokenTxn").andAllChildRecords().logged(),
												tokenUpdate(VANILLA_TOKEN).supplyKey(CONTRACT_KEY),
												getTokenInfo(VANILLA_TOKEN).logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(ACCOUNT)
														.via("vanillaTokenTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("vanillaTokenTxn").andAllChildRecords().logged()
										)
						)
				).then(
						getAccountInfo(ACCOUNT)
								.hasToken(relationshipWith(FROZEN_TOKEN).kyc(KycNotApplicable).freeze(Frozen))
								.hasToken(relationshipWith(UNFROZEN_TOKEN).kyc(KycNotApplicable).freeze(Unfrozen))
								.hasToken(relationshipWith(KYC_TOKEN).kyc(Revoked).freeze(FreezeNotApplicable))
								.hasToken(relationshipWith(VANILLA_TOKEN).kyc(KycNotApplicable).freeze(FreezeNotApplicable))
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

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
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.emptyChildRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
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
				multipleAssociatePrecompileWithSignatureWorksForFungible(),
				nestedAssociateWorksAsExpected(),
				associatePrecompileWithDelegateContractKeyForFungibleVanilla(),
				associatePrecompileWithDelegateContractKeyForFungibleFrozen(),
				associatePrecompileWithDelegateContractKeyForFungibleWithKYC(),
				associatePrecompileWithDelegateContractKeyForNonFungibleVanilla(),
				associatePrecompileWithDelegateContractKeyForNonFungibleFrozen(),
				associatePrecompileWithDelegateContractKeyForNonFungibleWithKYC()
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
						childRecordsCheck("delegateAssociateCallWithContractKeyTxn", CONTRACT_REVERT_EXECUTED, recordWith()
								.status(INVALID_SIGNATURE)),
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
						childRecordsCheck("delegateAssociateCallWithDelegateContractKeyTxn", SUCCESS, recordWith()
								.status(SUCCESS)),
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
						emptyChildRecordsCheck("staticAssociateCallTxn", CONTRACT_REVERT_EXECUTED),
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
						newKeyNamed(FREEZE_KEY),
						newKeyNamed(KYC_KEY),
						fileCreate(THE_CONTRACT).path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
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
						childRecordsCheck("MultipleTokensAssociationsTxn", SUCCESS, recordWith().status(SUCCESS)),
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
														.via("nestedAssociateTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("nestedAssociateTxn").andAllChildRecords().logged()
										)
						)
				).then(
						childRecordsCheck("nestedAssociateTxn", SUCCESS,
								recordWith().status(SUCCESS),
								recordWith().status(SUCCESS)),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(VANILLA_TOKEN)
				);
	}

	private HapiApiSpec associatePrecompileWithDelegateContractKeyForFungibleVanilla() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("AssociatePrecompileWithDelegateContractKeyForFungibleVanilla")
				.given(
						fileCreate(THE_CONTRACT).path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
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
												contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(GENESIS)
														.via("vanillaTokenAssociateFailsTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("vanillaTokenAssociateFailsTxn").andAllChildRecords().logged(),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(GENESIS)
														.via("vanillaTokenAssociateTxn")
														.hasKnownStatus(SUCCESS),
												getTxnRecord("vanillaTokenAssociateTxn").andAllChildRecords().logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(GENESIS)
														.via("vanillaTokenSecondAssociateFailsTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("vanillaTokenSecondAssociateFailsTxn").andAllChildRecords().logged()
										)
						)
				).then(
						childRecordsCheck("vanillaTokenAssociateFailsTxn", CONTRACT_REVERT_EXECUTED, recordWith()
								.status(INVALID_SIGNATURE)),
						childRecordsCheck("vanillaTokenAssociateTxn", SUCCESS, recordWith().status(SUCCESS)),
						childRecordsCheck("vanillaTokenSecondAssociateFailsTxn", CONTRACT_REVERT_EXECUTED, recordWith()
								.status(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT)),
						getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN))
				);
	}

	private HapiApiSpec associatePrecompileWithDelegateContractKeyForFungibleFrozen() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> frozenTokenID = new AtomicReference<>();

		return defaultHapiSpec("AssociatePrecompileWithDelegateContractKeyForFungibleFrozen")
				.given(
						newKeyNamed(FREEZE_KEY),
						fileCreate(THE_CONTRACT).path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FROZEN_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(TOTAL_SUPPLY)
								.freezeKey(FREEZE_KEY)
								.freezeDefault(true)
								.exposingCreatedIdTo(id -> frozenTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(frozenTokenID.get()))
														.payingWith(GENESIS)
														.via("frozenTokenAssociateFailsTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("frozenTokenAssociateFailsTxn").andAllChildRecords().logged(),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(frozenTokenID.get()))
														.payingWith(GENESIS)
														.via("frozenTokenAssociateTxn")
														.hasKnownStatus(SUCCESS),
												getTxnRecord("frozenTokenAssociateTxn").andAllChildRecords().logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(frozenTokenID.get()))
														.payingWith(GENESIS)
														.via("frozenTokenSecondAssociateFailsTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("frozenTokenSecondAssociateFailsTxn").andAllChildRecords().logged()
										)
						)
				).then(
						childRecordsCheck("frozenTokenAssociateFailsTxn", CONTRACT_REVERT_EXECUTED, recordWith()
								.status(INVALID_SIGNATURE)),
						childRecordsCheck("frozenTokenAssociateTxn", SUCCESS, recordWith().status(SUCCESS)),
						childRecordsCheck("frozenTokenSecondAssociateFailsTxn", CONTRACT_REVERT_EXECUTED, recordWith()
								.status(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT)),
						getAccountInfo(ACCOUNT).hasToken(relationshipWith(FROZEN_TOKEN).freeze(Frozen))
				);
	}

	private HapiApiSpec associatePrecompileWithDelegateContractKeyForFungibleWithKYC() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> kycTokenID = new AtomicReference<>();

		return defaultHapiSpec("AssociatePrecompileWithDelegateContractKeyForFungibleWithKYC")
				.given(
						newKeyNamed(KYC_KEY),
						fileCreate(THE_CONTRACT).path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(KYC_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.kycKey(KYC_KEY)
								.exposingCreatedIdTo(id -> kycTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(kycTokenID.get()))
														.payingWith(GENESIS)
														.via("kycTokenAssociateFailsTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("kycTokenAssociateFailsTxn").andAllChildRecords().logged(),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(kycTokenID.get()))
														.payingWith(GENESIS)
														.via("kycTokenAssociateTxn")
														.hasKnownStatus(SUCCESS),
												getTxnRecord("kycTokenAssociateTxn").andAllChildRecords().logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(kycTokenID.get()))
														.payingWith(GENESIS)
														.via("kycTokenSecondAssociateFailsTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("kycTokenSecondAssociateFailsTxn").andAllChildRecords().logged()
										)
						)
				).then(
						childRecordsCheck("kycTokenAssociateFailsTxn", CONTRACT_REVERT_EXECUTED, recordWith()
								.status(INVALID_SIGNATURE)),
						childRecordsCheck("kycTokenAssociateTxn", SUCCESS, recordWith().status(SUCCESS)),
						childRecordsCheck("kycTokenSecondAssociateFailsTxn", CONTRACT_REVERT_EXECUTED, recordWith()
								.status(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT)),
						getAccountInfo(ACCOUNT).hasToken(relationshipWith(KYC_TOKEN).kyc(Revoked))
				);
	}

	private HapiApiSpec associatePrecompileWithDelegateContractKeyForNonFungibleVanilla() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("AssociatePrecompileWithDelegateContractKeyForNonFungibleVanilla")
				.given(
						fileCreate(THE_CONTRACT).path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(GENESIS)
														.via("vanillaNFTAssociateFailsTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("vanillaNFTAssociateFailsTxn").andAllChildRecords().logged(),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(GENESIS)
														.via("vanillaNFTAssociateTxn")
														.hasKnownStatus(SUCCESS),
												getTxnRecord("vanillaNFTAssociateTxn").andAllChildRecords().logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(GENESIS)
														.via("vanillaNFTSecondAssociateFailsTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("vanillaNFTSecondAssociateFailsTxn").andAllChildRecords().logged()
										)
						)
				).then(
						childRecordsCheck("vanillaNFTAssociateFailsTxn", CONTRACT_REVERT_EXECUTED, recordWith()
								.status(INVALID_SIGNATURE)),
						childRecordsCheck("vanillaNFTAssociateTxn", SUCCESS, recordWith().status(SUCCESS)),
						childRecordsCheck("vanillaNFTSecondAssociateFailsTxn", CONTRACT_REVERT_EXECUTED, recordWith()
								.status(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT)),
						getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN))
				);
	}

	private HapiApiSpec associatePrecompileWithDelegateContractKeyForNonFungibleFrozen() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> frozenTokenID = new AtomicReference<>();

		return defaultHapiSpec("AssociatePrecompileWithDelegateContractKeyForNonFungibleFrozen")
				.given(
						newKeyNamed(FREEZE_KEY),
						fileCreate(THE_CONTRACT).path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FROZEN_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.freezeKey(FREEZE_KEY)
								.freezeDefault(true)
								.exposingCreatedIdTo(id -> frozenTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(frozenTokenID.get()))
														.payingWith(GENESIS)
														.via("frozenNFTAssociateFailsTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("frozenNFTAssociateFailsTxn").andAllChildRecords().logged(),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(frozenTokenID.get()))
														.payingWith(GENESIS)
														.via("frozenNFTAssociateTxn")
														.hasKnownStatus(SUCCESS),
												getTxnRecord("frozenNFTAssociateTxn").andAllChildRecords().logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(frozenTokenID.get()))
														.payingWith(GENESIS)
														.via("frozenNFTSecondAssociateFailsTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("frozenNFTSecondAssociateFailsTxn").andAllChildRecords().logged()
										)
						)
				).then(
						childRecordsCheck("frozenNFTAssociateFailsTxn", CONTRACT_REVERT_EXECUTED, recordWith()
								.status(INVALID_SIGNATURE)),
						childRecordsCheck("frozenNFTAssociateTxn", SUCCESS, recordWith().status(SUCCESS)),
						childRecordsCheck("frozenNFTSecondAssociateFailsTxn", CONTRACT_REVERT_EXECUTED, recordWith()
								.status(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT)),
						getAccountInfo(ACCOUNT).hasToken(relationshipWith(FROZEN_TOKEN).freeze(Frozen))
				);
	}

	private HapiApiSpec associatePrecompileWithDelegateContractKeyForNonFungibleWithKYC() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> kycTokenID = new AtomicReference<>();

		return defaultHapiSpec("AssociatePrecompileWithDelegateContractKeyForNonFungibleWithKYC")
				.given(
						newKeyNamed(KYC_KEY),
						fileCreate(THE_CONTRACT).path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(KYC_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.kycKey(KYC_KEY)
								.exposingCreatedIdTo(id -> kycTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(kycTokenID.get()))
														.payingWith(GENESIS)
														.via("kycNFTAssociateFailsTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("kycNFTAssociateFailsTxn").andAllChildRecords().logged(),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(kycTokenID.get()))
														.payingWith(GENESIS)
														.via("kycNFTAssociateTxn")
														.hasKnownStatus(SUCCESS),
												getTxnRecord("kycNFTAssociateTxn").andAllChildRecords().logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(kycTokenID.get()))
														.payingWith(GENESIS)
														.via("kycNFTSecondAssociateFailsTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("kycNFTSecondAssociateFailsTxn").andAllChildRecords().logged()
										)
						)
				).then(
						childRecordsCheck("kycNFTAssociateFailsTxn", CONTRACT_REVERT_EXECUTED, recordWith()
								.status(INVALID_SIGNATURE)),
						childRecordsCheck("kycNFTAssociateTxn", SUCCESS, recordWith().status(SUCCESS)),
						childRecordsCheck("kycNFTSecondAssociateFailsTxn", CONTRACT_REVERT_EXECUTED, recordWith()
								.status(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT)),
						getAccountInfo(ACCOUNT).hasToken(relationshipWith(KYC_TOKEN).kyc(Revoked))
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

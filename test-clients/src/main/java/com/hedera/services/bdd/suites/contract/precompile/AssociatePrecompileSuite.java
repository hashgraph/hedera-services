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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
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
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.MULTIPLE_TOKENS_ASSOCIATE;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.NESTED_TOKEN_ASSOCIATE;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.NON_SUPPORTED_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.PERFORM_INVALIDLY_FORMATTED_FUNCTION_CALL_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.PERFORM_INVALIDLY_FORMATTED_SINGLE_FUNCTION_CALL_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.PERFORM_NON_EXISTING_FUNCTION_CALL_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.PERFORM__FUNCTION_CALL_WITH_LESS_THAN_FOUR_BYTES_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.SINGLE_TOKEN_ASSOCIATE;
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
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.FreezeNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Frozen;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Unfrozen;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.KycNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.Revoked;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AssociatePrecompileSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(AssociatePrecompileSuite.class);
	private static final long TOTAL_SUPPLY = 1_000;
	private static final KeyShape DELEGATE_CONTRACT_KEY_SHAPE = KeyShape.threshOf(1, SIMPLE, DELEGATE_CONTRACT);
	private static final String TOKEN_TREASURY = "treasury";
	private static final String OUTER_CONTRACT = "Nested Associate/Dissociate Contract";
	private static final String INNER_CONTRACT = "Associate/Dissociate Contract";
	private static final String THE_CONTRACT = "Associate/Dissociate Contract";
	private static final String THE_GRACEFULLY_FAILING_CONTRACT = "Epically and gracefully failing contract";
	private static final String ACCOUNT = "anybody";
	private static final String FROZEN_TOKEN = "Frozen token";
	private static final String UNFROZEN_TOKEN = "Unfrozen token";
	private static final String COCONUT_TOKEN = "Coconut token";
	private static final String KYC_TOKEN = "KYC token";
	private static final String TOKEN = "Token";
	private static final String DELEGATE_KEY = "Delegate key";
	private static final String FREEZE_KEY = "Freeze key";
	private static final String KYC_KEY = "KYC key";

	public static void main(String... args) {
		new AssociatePrecompileSuite().runSuiteSync();
	}

	@Override
	public boolean canRunAsync() {
		return false;
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
				nonSupportedAbiCallGracefullyFailsWithMultipleContractCalls(),
				invalidlyFormattedAbiCallGracefullyFailsWithMultipleContractCalls(),
				nonSupportedAbiCallGracefullyFailsWithinSingleContractCall(),
				invalidAbiCallGracefullyFailsWithinSingleContractCall(),
				functionCallWithLessThanFourBytesFailsWithinSingleContractCall(),
				invalidSingleAbiCallConsumesAllProvidedGas()
		);
	}

	List<HapiApiSpec> positiveSpecs() {
		return List.of(
				multipleAssociatePrecompileWithSignatureWorksForFungible(),
				nestedAssociateWorksAsExpected(),
				associatePrecompileTokensPerAccountLimitExceeded()
		);
	}

	/* -- HSCS-PREC-27 from HTS Precompile Test Plan -- */
	private HapiApiSpec functionCallWithLessThanFourBytesFailsWithinSingleContractCall() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("Function call with less than four bytes fails within single contract call")
				.given(
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						fileCreate(THE_GRACEFULLY_FAILING_CONTRACT),
						updateLargeFile(ACCOUNT, THE_GRACEFULLY_FAILING_CONTRACT,
								extractByteCode(ContractResources.GRACEFULLY_FAILING_CONTRACT_BIN)),
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
												contractCreate(THE_GRACEFULLY_FAILING_CONTRACT)
														.bytecode(THE_GRACEFULLY_FAILING_CONTRACT)
														.gas(100_000),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_GRACEFULLY_FAILING_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(THE_GRACEFULLY_FAILING_CONTRACT, PERFORM__FUNCTION_CALL_WITH_LESS_THAN_FOUR_BYTES_ABI,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(GENESIS)
														.via("Function call with less than 4 bytes txn")
														.gas(2_000_000)
														.hasKnownStatus(SUCCESS),
												getTxnRecord("Function call with less than 4 bytes txn").andAllChildRecords().logged()
										)
						)
				).then(
						childRecordsCheck("Function call with less than 4 bytes txn", SUCCESS,
								recordWith().status(SUCCESS),
								recordWith().status(SUCCESS)),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(VANILLA_TOKEN)
				);
	}

	/* -- HSCS-PREC-27 from HTS Precompile Test Plan -- */
	private HapiApiSpec invalidAbiCallGracefullyFailsWithinSingleContractCall() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> coconutTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("Invalid Abi Call Gracefully Fails Within Single Contract Call")
				.given(
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						fileCreate(THE_GRACEFULLY_FAILING_CONTRACT),
						updateLargeFile(ACCOUNT, THE_GRACEFULLY_FAILING_CONTRACT,
								extractByteCode(ContractResources.GRACEFULLY_FAILING_CONTRACT_BIN)),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
						tokenCreate(COCONUT_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> coconutTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_GRACEFULLY_FAILING_CONTRACT)
														.bytecode(THE_GRACEFULLY_FAILING_CONTRACT)
														.gas(100_000),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_GRACEFULLY_FAILING_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(THE_GRACEFULLY_FAILING_CONTRACT, PERFORM_INVALIDLY_FORMATTED_FUNCTION_CALL_ABI,
														asAddress(accountID.get()),
														List.of(
																asAddress(coconutTokenID.get()),
																asAddress(vanillaTokenID.get()))
												)
														.payingWith(GENESIS)
														.gas(4_000_000)
														.via("Invalid Abi Function call txn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("Invalid Abi Function call txn").andAllChildRecords().logged()
										)
						)
				).then(
						childRecordsCheck("Invalid Abi Function call txn", SUCCESS,
								recordWith().status(SUCCESS),
								recordWith().status(SUCCESS)),
						getAccountInfo(ACCOUNT)
								.hasNoTokenRelationship(COCONUT_TOKEN)
								.hasNoTokenRelationship(VANILLA_TOKEN)
				);
	}

	/* -- HSCS-PREC-26 from HTS Precompile Test Plan -- */
	private HapiApiSpec nonSupportedAbiCallGracefullyFailsWithinSingleContractCall() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("NonSupportedAbiCallGracefullyFailsWithinSingleContractCall")
				.given(
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						fileCreate(THE_GRACEFULLY_FAILING_CONTRACT),
						updateLargeFile(ACCOUNT, THE_GRACEFULLY_FAILING_CONTRACT,
								extractByteCode(ContractResources.GRACEFULLY_FAILING_CONTRACT_BIN)),
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
												contractCreate(THE_GRACEFULLY_FAILING_CONTRACT)
														.bytecode(THE_GRACEFULLY_FAILING_CONTRACT)
														.gas(100_000),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_GRACEFULLY_FAILING_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(THE_GRACEFULLY_FAILING_CONTRACT, PERFORM_NON_EXISTING_FUNCTION_CALL_ABI,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(GENESIS)
														.via("nonExistingFunctionCallTxn")
														.gas(2_000_000)
														.hasKnownStatus(SUCCESS),
												getTxnRecord("nonExistingFunctionCallTxn").andAllChildRecords().logged()
										)
						)
				).then(
						childRecordsCheck("nonExistingFunctionCallTxn", SUCCESS,
								recordWith().status(SUCCESS),
								recordWith().status(SUCCESS)),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(VANILLA_TOKEN)
				);
	}

	/* -- HSCS-PREC-26 from HTS Precompile Test Plan -- */
	private HapiApiSpec nonSupportedAbiCallGracefullyFailsWithMultipleContractCalls() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("NonSupportedAbiCallGracefullyFails")
				.given(
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						fileCreate(THE_CONTRACT),
						updateLargeFile(ACCOUNT, THE_CONTRACT,
								extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
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
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(THE_CONTRACT, NON_SUPPORTED_ABI,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(GENESIS)
														.via("notSupportedFunctionCallTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("notSupportedFunctionCallTxn").andAllChildRecords().logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(GENESIS)
														.via("vanillaTokenAssociateTxn")
														.hasKnownStatus(SUCCESS),
												getTxnRecord("vanillaTokenAssociateTxn").andAllChildRecords().logged()
										)
						)
				).then(
						emptyChildRecordsCheck("notSupportedFunctionCallTxn", CONTRACT_REVERT_EXECUTED),
						childRecordsCheck("vanillaTokenAssociateTxn", SUCCESS, recordWith().status(SUCCESS)),
						getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN))
				);
	}

	/* -- HSCS-PREC-27 from HTS Precompile Test Plan -- */
	private HapiApiSpec invalidlyFormattedAbiCallGracefullyFailsWithMultipleContractCalls() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
		final var invalidAbiArgument = 123;

		return defaultHapiSpec("InvalidlyFormattedAbiCallGracefullyFails")
				.given(
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						fileCreate(THE_CONTRACT),
						updateLargeFile(ACCOUNT, THE_CONTRACT,
								extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
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
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), invalidAbiArgument)
														.payingWith(GENESIS)
														.via("functionCallWithInvalidArgumentTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("functionCallWithInvalidArgumentTxn").andAllChildRecords().logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(GENESIS)
														.via("vanillaTokenAssociateTxn")
														.hasKnownStatus(SUCCESS),
												getTxnRecord("vanillaTokenAssociateTxn").andAllChildRecords().logged()
										)
						)
				).then(
						childRecordsCheck("functionCallWithInvalidArgumentTxn", CONTRACT_REVERT_EXECUTED, recordWith().status(INVALID_TOKEN_ID)),
						childRecordsCheck("vanillaTokenAssociateTxn", SUCCESS, recordWith().status(SUCCESS)),
						getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN))
				);
	}

	/* -- HSCS-PREC-006 from HTS Precompile Test Plan -- */
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
						cryptoCreate(ACCOUNT)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						fileCreate(THE_CONTRACT),
						updateLargeFile(ACCOUNT, THE_CONTRACT,
								extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
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

	/* -- HSCS-PREC-010 from HTS Precompile Test Plan -- */
	private HapiApiSpec nestedAssociateWorksAsExpected() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("nestedAssociateWorksAsExpected")
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

	/* -- Not specifically required in the HTS Precompile Test Plan -- */
	private HapiApiSpec associatePrecompileTokensPerAccountLimitExceeded() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> secondVanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("AssociatePrecompileTokensPerAccountLimitExceeded")
				.given(
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						fileCreate(THE_CONTRACT),
						updateLargeFile(ACCOUNT, THE_CONTRACT,
								extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
						tokenCreate(TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> secondVanillaTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												UtilVerbs.overriding("tokens.maxPerAccount", "1"),
												contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(GENESIS)
														.via("vanillaTokenAssociateTxn")
														.hasKnownStatus(SUCCESS),
												getTxnRecord("vanillaTokenAssociateTxn").andAllChildRecords().logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
														asAddress(accountID.get()), asAddress(secondVanillaTokenID.get()))
														.payingWith(GENESIS)
														.via("secondVanillaTokenAssociateFailsTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("secondVanillaTokenAssociateFailsTxn").andAllChildRecords().logged()
										)
						)
				).then(
						childRecordsCheck("vanillaTokenAssociateTxn", SUCCESS, recordWith().status(SUCCESS)),
						childRecordsCheck("secondVanillaTokenAssociateFailsTxn", CONTRACT_REVERT_EXECUTED, recordWith()
								.status(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED)),
						getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN)),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(TOKEN),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	/* -- HSCS-PREC-27 from HTS Precompile Test Plan -- */
	private HapiApiSpec invalidSingleAbiCallConsumesAllProvidedGas() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();

		return defaultHapiSpec("Invalid Single Abi Call Consumes All Provided Gas")
				.given(
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						fileCreate(THE_GRACEFULLY_FAILING_CONTRACT),
						updateLargeFile(ACCOUNT, THE_GRACEFULLY_FAILING_CONTRACT,
								extractByteCode(ContractResources.GRACEFULLY_FAILING_CONTRACT_BIN))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_GRACEFULLY_FAILING_CONTRACT)
														.bytecode(THE_GRACEFULLY_FAILING_CONTRACT)
														.gas(100_000),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_GRACEFULLY_FAILING_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(THE_GRACEFULLY_FAILING_CONTRACT, PERFORM_INVALIDLY_FORMATTED_SINGLE_FUNCTION_CALL_ABI, asAddress(accountID.get()))
														.payingWith(GENESIS)
														.gas(2_000_000)
														.via("Invalid Single Abi Call txn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("Invalid Single Abi Call txn").saveTxnRecordToRegistry("Invalid Single Abi Call txn").logged()
										)
						)
				).then(
						withOpContext((spec, ignore) -> {
							final var gasUsed = spec.registry().getTransactionRecord("Invalid Single Abi Call txn")
									.getContractCallResult().getGasUsed();
							assertEquals(1969323, gasUsed);
						})
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

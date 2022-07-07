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
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.token.TokenAssociationSpecs;
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
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.emptyChildRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
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

	private static final long GAS_TO_OFFER = 4_000_000L;
	private static final long TOTAL_SUPPLY = 1_000;
	private static final KeyShape DELEGATE_CONTRACT_KEY_SHAPE = KeyShape.threshOf(1, SIMPLE, DELEGATE_CONTRACT);
	private static final String TOKEN_TREASURY = "treasury";
	private static final String OUTER_CONTRACT = "NestedAssociateDissociate";
	private static final String INNER_CONTRACT = "AssociateDissociate";
	private static final String THE_CONTRACT = "AssociateDissociate";
	private static final String THE_GRACEFULLY_FAILING_CONTRACT = "GracefullyFailing";
	private static final String ACCOUNT = "anybody";
	private static final String FROZEN_TOKEN = "Frozen token";
	private static final String UNFROZEN_TOKEN = "Unfrozen token";
	private static final String KYC_TOKEN = "KYC token";
	private static final String TOKEN = "Token";
	private static final String DELEGATE_KEY = "Delegate key";
	private static final String FREEZE_KEY = "Freeze key";
	private static final String KYC_KEY = "KYC key";
	private static final byte[] ACCOUNT_ADDRESS = asAddress(AccountID.newBuilder().build());
	private static final byte[] TOKEN_ADDRESS = asAddress(TokenID.newBuilder().build());

	public static void main(String... args) {
		new AssociatePrecompileSuite().runSuiteSync();
	}

	@Override
	public boolean canRunConcurrent() {
		return false;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveSpecs(),
				negativeSpecs()
		);
	}

	List<HapiApiSpec> negativeSpecs() {
		return List.of(new HapiApiSpec[]{
						functionCallWithLessThanFourBytesFailsWithinSingleContractCall(),
						nonSupportedAbiCallGracefullyFailsWithMultipleContractCalls(),
						invalidlyFormattedAbiCallGracefullyFailsWithMultipleContractCalls(),
						nonSupportedAbiCallGracefullyFailsWithinSingleContractCall(),
						invalidAbiCallGracefullyFailsWithinSingleContractCall(),
						invalidSingleAbiCallConsumesAllProvidedGas()
				}
		);
	}

	List<HapiApiSpec> positiveSpecs() {
		return List.of(
				nestedAssociateWorksAsExpected(),
				multipleAssociatePrecompileWithSignatureWorksForFungible(),
				associatePrecompileTokensPerAccountLimitExceeded()
		);
	}

	/* -- HSCS-PREC-27 from HTS Precompile Test Plan -- */
	private HapiApiSpec functionCallWithLessThanFourBytesFailsWithinSingleContractCall() {
		return defaultHapiSpec("FunctionCallWithLessThanFourBytesFailsWithinSingleContractCall")
				.given(
						uploadInitCode(THE_GRACEFULLY_FAILING_CONTRACT),
						contractCreate(THE_GRACEFULLY_FAILING_CONTRACT)
				).when(
						contractCall(THE_GRACEFULLY_FAILING_CONTRACT,
								"performLessThanFourBytesFunctionCall", ACCOUNT_ADDRESS, TOKEN_ADDRESS
						)
								.notTryingAsHexedliteral()
								.via("Function call with less than 4 bytes txn")
								.gas(100_000)
				).then(
						childRecordsCheck("Function call with less than 4 bytes txn", SUCCESS)
				);
	}

	/* -- HSCS-PREC-27 from HTS Precompile Test Plan -- */
	private HapiApiSpec invalidAbiCallGracefullyFailsWithinSingleContractCall() {
		return defaultHapiSpec("InvalidAbiCallGracefullyFailsWithinSingleContractCall")
				.given(
						uploadInitCode(THE_GRACEFULLY_FAILING_CONTRACT),
						contractCreate(THE_GRACEFULLY_FAILING_CONTRACT)
				).when(
						contractCall(THE_GRACEFULLY_FAILING_CONTRACT,
								"performInvalidlyFormattedFunctionCall", ACCOUNT_ADDRESS,
								List.of(TOKEN_ADDRESS, TOKEN_ADDRESS)
						)
								.notTryingAsHexedliteral()
								.via("Invalid Abi Function call txn")
				).then(
						childRecordsCheck("Invalid Abi Function call txn", SUCCESS)
				);
	}

	/* -- HSCS-PREC-26 from HTS Precompile Test Plan -- */
	private HapiApiSpec nonSupportedAbiCallGracefullyFailsWithinSingleContractCall() {
		return defaultHapiSpec("NonSupportedAbiCallGracefullyFailsWithinSingleContractCall")
				.given(
						uploadInitCode(THE_GRACEFULLY_FAILING_CONTRACT),
						contractCreate(THE_GRACEFULLY_FAILING_CONTRACT)
				).when(
						contractCall(
								THE_GRACEFULLY_FAILING_CONTRACT, "performNonExistingServiceFunctionCall",
								ACCOUNT_ADDRESS, TOKEN_ADDRESS
						)
								.notTryingAsHexedliteral()
								.via("nonExistingFunctionCallTxn")
				).then(
						childRecordsCheck("nonExistingFunctionCallTxn", SUCCESS)
				);
	}

	/* -- HSCS-PREC-26 from HTS Precompile Test Plan -- */
	private HapiApiSpec nonSupportedAbiCallGracefullyFailsWithMultipleContractCalls() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("NonSupportedAbiCallGracefullyFails")
				.given(
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
						uploadInitCode(THE_CONTRACT),
						contractCreate(THE_CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												newKeyNamed(DELEGATE_KEY).shape(
														DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(THE_CONTRACT, "nonSupportedFunction",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get())
												)
														.payingWith(GENESIS)
														.via("notSupportedFunctionCallTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												contractCall(THE_CONTRACT, "tokenAssociate",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get())
												)
														.payingWith(GENESIS)
														.via("vanillaTokenAssociateTxn")
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						emptyChildRecordsCheck("notSupportedFunctionCallTxn", CONTRACT_REVERT_EXECUTED),
						childRecordsCheck("vanillaTokenAssociateTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS)
														)
										)
						),

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
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
						uploadInitCode(THE_CONTRACT),
						contractCreate(THE_CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												newKeyNamed(DELEGATE_KEY)
														.shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(THE_CONTRACT, "tokenAssociate",
														asAddress(accountID.get()), invalidAbiArgument
												)
														.payingWith(GENESIS)
														.via("functionCallWithInvalidArgumentTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												contractCall(THE_CONTRACT, "tokenAssociate",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get())
												)
														.payingWith(GENESIS)
														.via("vanillaTokenAssociateTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						childRecordsCheck("functionCallWithInvalidArgumentTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(INVALID_TOKEN_ID)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(INVALID_TOKEN_ID))
										)),

						childRecordsCheck("vanillaTokenAssociateTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS))
										)
						),


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
						UtilVerbs.resetToDefault("tokens.maxPerAccount", "entities.limitTokenAssociations",
								"contracts.throttle.throttleByGas"),
						newKeyNamed(FREEZE_KEY),
						newKeyNamed(KYC_KEY),
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
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
						uploadInitCode(THE_CONTRACT),
						contractCreate(THE_CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(THE_CONTRACT, "tokensAssociate",
														asAddress(accountID.get()),
														List.of(
																asAddress(frozenTokenID.get()),
																asAddress(unfrozenTokenID.get()),
																asAddress(kycTokenID.get()),
																asAddress(vanillaTokenID.get()))
												)
														.alsoSigningWithFullPrefix(ACCOUNT)
														.via("MultipleTokensAssociationsTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(ResponseCodeEnum.SUCCESS)
										)
						)
				).then(
						childRecordsCheck("MultipleTokensAssociationsTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS))
										)
						),

						getAccountInfo(ACCOUNT)
								.hasToken(relationshipWith(FROZEN_TOKEN).kyc(KycNotApplicable).freeze(Frozen))
								.hasToken(relationshipWith(UNFROZEN_TOKEN).kyc(KycNotApplicable).freeze(Unfrozen))
								.hasToken(relationshipWith(KYC_TOKEN).kyc(Revoked).freeze(FreezeNotApplicable))
								.hasToken(relationshipWith(TokenAssociationSpecs.VANILLA_TOKEN).kyc(KycNotApplicable)
										.freeze(FreezeNotApplicable))
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
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
						uploadInitCode(INNER_CONTRACT, OUTER_CONTRACT),
						contractCreate(INNER_CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(OUTER_CONTRACT, getNestedContractAddress(INNER_CONTRACT, spec)),
												contractCall(OUTER_CONTRACT, "associateDissociateContractCall",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get())
												)
														.alsoSigningWithFullPrefix(ACCOUNT)
														.via("nestedAssociateTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(ResponseCodeEnum.SUCCESS)
										)
						)
				).then(
						childRecordsCheck("nestedAssociateTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS))
										),
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS))
										)
						),
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
						UtilVerbs.resetToDefault("tokens.maxPerAccount", "entities.limitTokenAssociations",
								"contracts.throttle.throttleByGas"),
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
						tokenCreate(TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> secondVanillaTokenID.set(asToken(id))),
						uploadInitCode(THE_CONTRACT),
						contractCreate(THE_CONTRACT),
						UtilVerbs.overriding("tokens.maxPerAccount", "1"),
						UtilVerbs.overriding("entities.limitTokenAssociations", "true"),
						UtilVerbs.overriding("contracts.throttle.throttleByGas", "true")
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT),
												newKeyNamed(DELEGATE_KEY).shape(
														DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(THE_CONTRACT, "tokenAssociate",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(GENESIS)
														.via("vanillaTokenAssociateTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(SUCCESS),
												contractCall(THE_CONTRACT, "tokenAssociate",
														asAddress(accountID.get()),
														asAddress(secondVanillaTokenID.get()))
														.payingWith(GENESIS)
														.via("secondVanillaTokenAssociateFailsTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
										)
						)
				).then(
						childRecordsCheck("vanillaTokenAssociateTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS))
										)
						),

						childRecordsCheck("secondVanillaTokenAssociateFailsTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED))
										)
						),

						getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN)),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(TOKEN),
						UtilVerbs.resetToDefault("tokens.maxPerAccount", "entities.limitTokenAssociations",
								"contracts.throttle.throttleByGas")
				);
	}

	/* -- HSCS-PREC-27 from HTS Precompile Test Plan -- */
	private HapiApiSpec invalidSingleAbiCallConsumesAllProvidedGas() {
		return defaultHapiSpec("InvalidSingleAbiCallConsumesAllProvidedGas")
				.given(
						uploadInitCode(THE_GRACEFULLY_FAILING_CONTRACT),
						contractCreate(THE_GRACEFULLY_FAILING_CONTRACT)
				).when(
						contractCall(THE_GRACEFULLY_FAILING_CONTRACT,
								"performInvalidlyFormattedSingleFunctionCall", ACCOUNT_ADDRESS
						)
								.notTryingAsHexedliteral()
								.via("Invalid Single Abi Call txn")
								.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
						getTxnRecord("Invalid Single Abi Call txn").saveTxnRecordToRegistry("Invalid Single Abi Call txn")
				).then(
						withOpContext((spec, ignore) -> {
							final var gasUsed = spec.registry().getTransactionRecord("Invalid Single Abi Call txn")
									.getContractCallResult().getGasUsed();
							assertEquals(99011, gasUsed);
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
	public static String getNestedContractAddress(final String outerContract, final HapiApiSpec spec) {
		return HapiPropertySource.asHexedSolidityAddress(spec.registry().getContractId(outerContract));
	}
}
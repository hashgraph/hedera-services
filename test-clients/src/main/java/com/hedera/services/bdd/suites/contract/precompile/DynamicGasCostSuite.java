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
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
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
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.SAFE_ASSOCIATE_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.SAFE_BURN_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.SAFE_DISSOCIATE_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.SAFE_MINT_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.SAFE_MULTIPLE_ASSOCIATE_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.SAFE_MULTIPLE_DISSOCIATE_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.SAFE_NFTS_TRANSFER_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.SAFE_NFT_TRANSFER_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.SAFE_TOKENS_TRANSFER_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.SAFE_TOKEN_TRANSFER_ABI;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.KNOWABLE_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.MiscEETUtils.metadata;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DynamicGasCostSuite extends HapiApiSuite {

	private static final Logger log = LogManager.getLogger(DynamicGasCostSuite.class);
	private static final String THE_CONTRACT = "Safe Operations Contract";
	private static final String ACCOUNT = "anybody";
	private static final String SECOND_ACCOUNT = "anybody2";
	private static final String MULTI_KEY = "Multi key";
	private static final String FREEZE_KEY = "Freeze key";
	public static final String DEFAULT_GAS_COST = "10000";
	public static final String FULL_GAS_REFUND = "100";
	public static final String ZERO_GAS_COST = "0";
	public static final String HTS_DEFAULT_GAS_COST = "contracts.precompile.htsDefaultGasCost";
	public static final String MAX_REFUND_PERCENT_OF_GAS_LIMIT = "contracts.maxRefundPercentOfGasLimit";
	public static final String BOOTSTRAP_PROPERTIES = "src/main/resource/bootstrap.properties";

	public static void main(String... args) {
		new DynamicGasCostSuite().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
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

		);
	}

	List<HapiApiSpec> positiveSpecs() {
		return List.of(
				mintDynamicGasCostPrecompile(),
				burnDynamicGasCostPrecompile(),
				associateDynamicGasCostPrecompile(),
				dissociateDynamicGasCostPrecompile(),
				multipleAssociateDynamicGasCostPrecompile(),
				multipleDissociateDynamicGasCostPrecompile(),
				nftTransferDynamicGasCostPrecompile(),
				tokenTransferDynamicGasCostPrecompile(),
				tokensTransferDynamicGasCostPrecompile(),
				nftsTransferDynamicGasCostPrecompile()
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

	private HapiApiSpec mintDynamicGasCostPrecompile() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
		final var amount = 10;

		return defaultHapiSpec("MintDynamicGasCostPrecompile")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						fileCreate(THE_CONTRACT),
						updateLargeFile(ACCOUNT, THE_CONTRACT, extractByteCode(ContractResources.SAFE_OPERATIONS_CONTRACT)),
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
												contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, ZERO_GAS_COST),
												UtilVerbs.overriding(MAX_REFUND_PERCENT_OF_GAS_LIMIT, FULL_GAS_REFUND),
												contractCall(THE_CONTRACT, SAFE_MINT_ABI,
														asAddress(vanillaTokenID.get()), amount, Collections.emptyList())
														.payingWith(ACCOUNT)
														.via("mintDynamicGasZeroCostTxn")
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.hasKnownStatus(SUCCESS),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, DEFAULT_GAS_COST),
												contractCall(THE_CONTRACT, SAFE_MINT_ABI,
														asAddress(vanillaTokenID.get()), amount, Collections.emptyList())
														.payingWith(ACCOUNT)
														.via("mintDynamicGasDefaultCostTxn")
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.hasKnownStatus(SUCCESS),
												UtilVerbs.resetAppPropertiesTo(BOOTSTRAP_PROPERTIES)
										)
						)
				).then(
						withOpContext((spec, ignore) -> {
							final var zeroCostTxnRecord =
									getTxnRecord("mintDynamicGasZeroCostTxn").saveTxnRecordToRegistry("mintZeroCostTxnRec");
							final var defaultCostTxnRecord =
									getTxnRecord("mintDynamicGasDefaultCostTxn").saveTxnRecordToRegistry("mintDefaultCostTxnRec");
							CustomSpecAssert.allRunFor(spec, zeroCostTxnRecord, defaultCostTxnRecord);

							final var gasUsedForZeroCostTxn = spec.registry().getTransactionRecord("mintZeroCostTxnRec")
									.getContractCallResult().getGasUsed();
							final var gasUsedForDefaultCostTxn = spec.registry().getTransactionRecord("mintDefaultCostTxnRec")
									.getContractCallResult().getGasUsed();
							assertEquals(10_000L, gasUsedForDefaultCostTxn - gasUsedForZeroCostTxn);
						})
				);
	}

	private HapiApiSpec burnDynamicGasCostPrecompile() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
		final var amount = 10;

		return defaultHapiSpec("BurnDynamicGasCostPrecompile")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						fileCreate(THE_CONTRACT),
						updateLargeFile(ACCOUNT, THE_CONTRACT, extractByteCode(ContractResources.SAFE_OPERATIONS_CONTRACT)),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
						mintToken(VANILLA_TOKEN, amount)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, ZERO_GAS_COST),
												UtilVerbs.overriding(MAX_REFUND_PERCENT_OF_GAS_LIMIT, FULL_GAS_REFUND),
												contractCall(THE_CONTRACT, SAFE_BURN_ABI,
														asAddress(vanillaTokenID.get()), amount, Collections.emptyList())
														.payingWith(ACCOUNT)
														.via("burnDynamicGasZeroCostTxn")
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.hasKnownStatus(SUCCESS),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, DEFAULT_GAS_COST),
												mintToken(VANILLA_TOKEN, amount),
												contractCall(THE_CONTRACT, SAFE_BURN_ABI,
														asAddress(vanillaTokenID.get()), amount, Collections.emptyList())
														.payingWith(ACCOUNT)
														.via("burnDynamicGasDefaultCostTxn")
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.hasKnownStatus(SUCCESS),
												UtilVerbs.resetAppPropertiesTo(BOOTSTRAP_PROPERTIES)
										)
						)
				).then(
						withOpContext((spec, ignore) -> {
							final var zeroCostTxnRecord =
									getTxnRecord("burnDynamicGasZeroCostTxn").saveTxnRecordToRegistry("burnZeroCostTxnRec");
							final var defaultCostTxnRecord =
									getTxnRecord("burnDynamicGasDefaultCostTxn").saveTxnRecordToRegistry("burnDefaultCostTxnRec");
							CustomSpecAssert.allRunFor(spec, zeroCostTxnRecord, defaultCostTxnRecord);

							final var gasUsedForZeroCostTxn = spec.registry().getTransactionRecord("burnZeroCostTxnRec")
									.getContractCallResult().getGasUsed();
							final var gasUsedForDefaultCostTxn = spec.registry().getTransactionRecord("burnDefaultCostTxnRec")
									.getContractCallResult().getGasUsed();
							assertEquals(10_000L, gasUsedForDefaultCostTxn - gasUsedForZeroCostTxn);
						})
				);
	}

	private HapiApiSpec associateDynamicGasCostPrecompile() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("AssociateDynamicGasCostPrecompile")
				.given(
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						fileCreate(THE_CONTRACT),
						updateLargeFile(ACCOUNT, THE_CONTRACT, extractByteCode(ContractResources.SAFE_OPERATIONS_CONTRACT)),
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
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, ZERO_GAS_COST),
												UtilVerbs.overriding(MAX_REFUND_PERCENT_OF_GAS_LIMIT, FULL_GAS_REFUND),
												contractCall(THE_CONTRACT, SAFE_ASSOCIATE_ABI,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(ACCOUNT)
														.via("associateDynamicGasZeroCostTxn")
														.hasKnownStatus(SUCCESS),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, DEFAULT_GAS_COST),
												tokenDissociate(ACCOUNT, VANILLA_TOKEN),
												contractCall(THE_CONTRACT, SAFE_ASSOCIATE_ABI,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(ACCOUNT)
														.via("associateDynamicGasDefaultCostTxn")
														.hasKnownStatus(SUCCESS),
												UtilVerbs.resetAppPropertiesTo(BOOTSTRAP_PROPERTIES)
										)
						)
				).then(
						withOpContext((spec, ignore) -> {
							final var zeroCostTxnRecord =
									getTxnRecord("associateDynamicGasZeroCostTxn").saveTxnRecordToRegistry("associateZeroCostTxnRec");
							final var defaultCostTxnRecord =
									getTxnRecord("associateDynamicGasDefaultCostTxn").saveTxnRecordToRegistry("associateDefaultCostTxnRec");
							CustomSpecAssert.allRunFor(spec, zeroCostTxnRecord, defaultCostTxnRecord);

							final var gasUsedForZeroCostTxn = spec.registry().getTransactionRecord("associateZeroCostTxnRec")
									.getContractCallResult().getGasUsed();
							final var gasUsedForDefaultCostTxn = spec.registry().getTransactionRecord("associateDefaultCostTxnRec")
									.getContractCallResult().getGasUsed();
							assertEquals(10_000L, gasUsedForDefaultCostTxn - gasUsedForZeroCostTxn);
						})
				);
	}

	public HapiApiSpec dissociateDynamicGasCostPrecompile() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("DissociateDynamicGasCostPrecompile")
				.given(
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						fileCreate(THE_CONTRACT),
						updateLargeFile(ACCOUNT, THE_CONTRACT, extractByteCode(ContractResources.SAFE_OPERATIONS_CONTRACT)),
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
												tokenAssociate(ACCOUNT, VANILLA_TOKEN),
												contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, ZERO_GAS_COST),
												UtilVerbs.overriding(MAX_REFUND_PERCENT_OF_GAS_LIMIT, FULL_GAS_REFUND),
												contractCall(THE_CONTRACT, SAFE_DISSOCIATE_ABI,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(ACCOUNT)
														.via("dissociateZeroCostTxn")
														.hasKnownStatus(SUCCESS),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, DEFAULT_GAS_COST),
												tokenAssociate(ACCOUNT, VANILLA_TOKEN),
												contractCall(THE_CONTRACT, SAFE_DISSOCIATE_ABI,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(ACCOUNT)
														.via("dissociateDefaultCostTxn")
														.hasKnownStatus(SUCCESS),
												UtilVerbs.resetAppPropertiesTo(BOOTSTRAP_PROPERTIES)
										)
						)
				).then(
						withOpContext((spec, ignore) -> {
							final var zeroCostTxnRecord =
									getTxnRecord("dissociateZeroCostTxn").saveTxnRecordToRegistry("dissociateZeroCostTxnRec");
							final var defaultCostTxnRecord =
									getTxnRecord("dissociateDefaultCostTxn").saveTxnRecordToRegistry("dissociateDefaultCostTxnRec");
							CustomSpecAssert.allRunFor(spec, zeroCostTxnRecord, defaultCostTxnRecord);

							final var gasUsedForZeroCostTxn = spec.registry().getTransactionRecord("dissociateZeroCostTxnRec")
									.getContractCallResult().getGasUsed();
							final var gasUsedForDefaultCostTxn = spec.registry().getTransactionRecord("dissociateDefaultCostTxnRec")
									.getContractCallResult().getGasUsed();
							assertEquals(10_000L, gasUsedForDefaultCostTxn - gasUsedForZeroCostTxn);
						})
				);
	}

	private HapiApiSpec multipleAssociateDynamicGasCostPrecompile() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> knowableTokenID = new AtomicReference<>();

		return defaultHapiSpec("MultipleAssociateDynamicGasCostPrecompile")
				.given(
						newKeyNamed(FREEZE_KEY),
						cryptoCreate(ACCOUNT)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						fileCreate(THE_CONTRACT),
						updateLargeFile(ACCOUNT, THE_CONTRACT, extractByteCode(ContractResources.SAFE_OPERATIONS_CONTRACT)),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(10L)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
						tokenCreate(KNOWABLE_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(10L)
								.exposingCreatedIdTo(id -> knowableTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, ZERO_GAS_COST),
												UtilVerbs.overriding(MAX_REFUND_PERCENT_OF_GAS_LIMIT, FULL_GAS_REFUND),
												contractCall(THE_CONTRACT, SAFE_MULTIPLE_ASSOCIATE_ABI,
														asAddress(accountID.get()),
														List.of(
																asAddress(vanillaTokenID.get()),
																asAddress(knowableTokenID.get())))
														.payingWith(ACCOUNT)
														.via("multipleAssociateZeroCostTxn")
														.hasKnownStatus(SUCCESS),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, DEFAULT_GAS_COST),
												tokenDissociate(ACCOUNT, VANILLA_TOKEN, KNOWABLE_TOKEN),
												contractCall(THE_CONTRACT, SAFE_MULTIPLE_ASSOCIATE_ABI,
														asAddress(accountID.get()),
														List.of(
																asAddress(vanillaTokenID.get()),
																asAddress(knowableTokenID.get())))
														.payingWith(ACCOUNT)
														.via("multipleAssociateDefaultCostTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												UtilVerbs.resetAppPropertiesTo(BOOTSTRAP_PROPERTIES)
										)
						)
				).then(
						withOpContext((spec, ignore) -> {
							final var zeroCostTxnRecord =
									getTxnRecord("multipleAssociateZeroCostTxn").saveTxnRecordToRegistry("multipleAssociateZeroCostTxnRec");
							final var defaultCostTxnRecord =
									getTxnRecord("multipleAssociateDefaultCostTxn").saveTxnRecordToRegistry("multipleAssociateDefaultCostTxnRec");
							CustomSpecAssert.allRunFor(spec, zeroCostTxnRecord, defaultCostTxnRecord);

							final var gasUsedForZeroCostTxn = spec.registry().getTransactionRecord("multipleAssociateZeroCostTxnRec")
									.getContractCallResult().getGasUsed();
							final var gasUsedForDefaultCostTxn = spec.registry().getTransactionRecord("multipleAssociateDefaultCostTxnRec")
									.getContractCallResult().getGasUsed();
							assertEquals(20_000L, gasUsedForDefaultCostTxn - gasUsedForZeroCostTxn);
						})
				);
	}

	public HapiApiSpec multipleDissociateDynamicGasCostPrecompile() {
		final AtomicReference<TokenID> knowableTokenTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();

		return defaultHapiSpec("MultipleDissociateDynamicGasCostPrecompile")
				.given(
						cryptoCreate(ACCOUNT)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						fileCreate(THE_CONTRACT),
						updateLargeFile(ACCOUNT, THE_CONTRACT, extractByteCode(ContractResources.SAFE_OPERATIONS_CONTRACT)),
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
												tokenAssociate(ACCOUNT, List.of(VANILLA_TOKEN, KNOWABLE_TOKEN)),
												contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, ZERO_GAS_COST),
												UtilVerbs.overriding(MAX_REFUND_PERCENT_OF_GAS_LIMIT, FULL_GAS_REFUND),
												contractCall(THE_CONTRACT, SAFE_MULTIPLE_DISSOCIATE_ABI,
														asAddress(accountID.get()), List.of(
																asAddress(vanillaTokenID.get()),
																asAddress(knowableTokenTokenID.get())))
														.payingWith(ACCOUNT)
														.via("multipleDissociateZeroCostTxn")
														.hasKnownStatus(SUCCESS),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, DEFAULT_GAS_COST),
												tokenAssociate(ACCOUNT, List.of(VANILLA_TOKEN, KNOWABLE_TOKEN)),
												contractCall(THE_CONTRACT, SAFE_MULTIPLE_DISSOCIATE_ABI,
														asAddress(accountID.get()), List.of(
																asAddress(vanillaTokenID.get()),
																asAddress(knowableTokenTokenID.get())))
														.payingWith(ACCOUNT)
														.via("multipleDissociateDefaultCostTxn")
														.hasKnownStatus(SUCCESS),
												UtilVerbs.resetAppPropertiesTo(BOOTSTRAP_PROPERTIES)
										)
						)
				).then(
						withOpContext((spec, ignore) -> {
							final var zeroCostTxnRecord =
									getTxnRecord("multipleDissociateZeroCostTxn").saveTxnRecordToRegistry("multipleDissociateZeroCostTxnRec");
							final var defaultCostTxnRecord =
									getTxnRecord("multipleDissociateDefaultCostTxn").saveTxnRecordToRegistry("multipleDissociateDefaultCostTxnRec");
							CustomSpecAssert.allRunFor(spec, zeroCostTxnRecord, defaultCostTxnRecord);

							final var gasUsedForZeroCostTxn = spec.registry().getTransactionRecord("multipleDissociateZeroCostTxnRec")
									.getContractCallResult().getGasUsed();
							final var gasUsedForDefaultCostTxn = spec.registry().getTransactionRecord("multipleDissociateDefaultCostTxnRec")
									.getContractCallResult().getGasUsed();
							assertEquals(20_000L, gasUsedForDefaultCostTxn - gasUsedForZeroCostTxn);
						})
				);
	}

	public HapiApiSpec nftTransferDynamicGasCostPrecompile() {
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();

		return defaultHapiSpec("NftTransferDynamicGasCostPrecompile")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						fileCreate(THE_CONTRACT),
						updateLargeFile(ACCOUNT, THE_CONTRACT, extractByteCode(ContractResources.SAFE_OPERATIONS_CONTRACT)),
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
												contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, ZERO_GAS_COST),
												UtilVerbs.overriding(MAX_REFUND_PERCENT_OF_GAS_LIMIT, FULL_GAS_REFUND),
												contractCall(THE_CONTRACT, SAFE_NFT_TRANSFER_ABI,
														asAddress(vanillaTokenID.get()),
														asAddress(treasuryID.get()),
														asAddress(accountID.get()),
														1L)
														.payingWith(ACCOUNT)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via("nftTransferZeroCostTxn")
														.hasKnownStatus(SUCCESS),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, DEFAULT_GAS_COST),
												contractCall(THE_CONTRACT, SAFE_NFT_TRANSFER_ABI,
														asAddress(vanillaTokenID.get()),
														asAddress(treasuryID.get()),
														asAddress(accountID.get()),
														2L)
														.payingWith(ACCOUNT)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via("nftTransferDefaultCostTxn")
														.hasKnownStatus(SUCCESS),
												UtilVerbs.resetAppPropertiesTo(BOOTSTRAP_PROPERTIES)
										)
						)
				).then(
						withOpContext((spec, ignore) -> {
							final var zeroCostTxnRecord =
									getTxnRecord("nftTransferZeroCostTxn").saveTxnRecordToRegistry("nftTransferZeroCostTxnRec");
							final var defaultCostTxnRecord =
									getTxnRecord("nftTransferDefaultCostTxn").saveTxnRecordToRegistry("nftTransferDefaultCostTxnRec");
							CustomSpecAssert.allRunFor(spec, zeroCostTxnRecord, defaultCostTxnRecord);

							final var gasUsedForZeroCostTxn = spec.registry().getTransactionRecord("nftTransferZeroCostTxnRec")
									.getContractCallResult().getGasUsed();
							final var gasUsedForDefaultCostTxn = spec.registry().getTransactionRecord("nftTransferDefaultCostTxnRec")
									.getContractCallResult().getGasUsed();
							assertEquals(10_000L, gasUsedForDefaultCostTxn - gasUsedForZeroCostTxn);
						})
				);
	}

	public HapiApiSpec tokenTransferDynamicGasCostPrecompile() {
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();

		return defaultHapiSpec("TokenTransferDynamicGasCostPrecompile")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						fileCreate(THE_CONTRACT),
						updateLargeFile(ACCOUNT, THE_CONTRACT, extractByteCode(ContractResources.SAFE_OPERATIONS_CONTRACT)),
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
												contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, ZERO_GAS_COST),
												UtilVerbs.overriding(MAX_REFUND_PERCENT_OF_GAS_LIMIT, FULL_GAS_REFUND),
												contractCall(THE_CONTRACT, SAFE_TOKEN_TRANSFER_ABI,
														asAddress(vanillaTokenID.get()),
														asAddress(treasuryID.get()),
														asAddress(accountID.get()),
														5L)
														.payingWith(ACCOUNT)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via("tokenTransferZeroCostTxn")
														.hasKnownStatus(SUCCESS),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, DEFAULT_GAS_COST),
												contractCall(THE_CONTRACT, SAFE_TOKEN_TRANSFER_ABI,
														asAddress(vanillaTokenID.get()),
														asAddress(treasuryID.get()),
														asAddress(accountID.get()),
														5L)
														.payingWith(ACCOUNT)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via("tokenTransferDefaultCostTxn")
														.hasKnownStatus(SUCCESS),
												UtilVerbs.resetAppPropertiesTo(BOOTSTRAP_PROPERTIES)
										)
						)
				).then(
						withOpContext((spec, ignore) -> {
							final var zeroCostTxnRecord =
									getTxnRecord("tokenTransferZeroCostTxn").saveTxnRecordToRegistry("tokenTransferZeroCostTxnRec");
							final var defaultCostTxnRecord =
									getTxnRecord("tokenTransferDefaultCostTxn").saveTxnRecordToRegistry("tokenTransferDefaultCostTxnRec");
							CustomSpecAssert.allRunFor(spec, zeroCostTxnRecord, defaultCostTxnRecord);

							final var gasUsedForZeroCostTxn = spec.registry().getTransactionRecord("tokenTransferZeroCostTxnRec")
									.getContractCallResult().getGasUsed();
							final var gasUsedForDefaultCostTxn = spec.registry().getTransactionRecord("tokenTransferDefaultCostTxnRec")
									.getContractCallResult().getGasUsed();
							assertEquals(10_000L, gasUsedForDefaultCostTxn - gasUsedForZeroCostTxn);
						})
				);
	}

	public HapiApiSpec tokensTransferDynamicGasCostPrecompile() {
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
		final AtomicReference<AccountID> firstAccountID = new AtomicReference<>();
		final AtomicReference<AccountID> secondAccountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();

		return defaultHapiSpec("TokensTransferDynamicGasCostPrecompile")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(firstAccountID::set),
						cryptoCreate(SECOND_ACCOUNT)
								.exposingCreatedIdTo(secondAccountID::set),
						fileCreate(THE_CONTRACT),
						updateLargeFile(ACCOUNT, THE_CONTRACT, extractByteCode(ContractResources.SAFE_OPERATIONS_CONTRACT)),
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
						mintToken(VANILLA_TOKEN, 20),
						tokenAssociate(ACCOUNT, VANILLA_TOKEN),
						tokenAssociate(SECOND_ACCOUNT, VANILLA_TOKEN),
						cryptoUpdate(TOKEN_TREASURY).key(MULTI_KEY),
						cryptoUpdate(SECOND_ACCOUNT).key(MULTI_KEY)
				)
				.when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, ZERO_GAS_COST),
												UtilVerbs.overriding(MAX_REFUND_PERCENT_OF_GAS_LIMIT, FULL_GAS_REFUND),
												contractCall(THE_CONTRACT, SAFE_TOKENS_TRANSFER_ABI,
														asAddress(vanillaTokenID.get()),
														List.of(asAddress(treasuryID.get()),
																asAddress(firstAccountID.get()),
																asAddress(secondAccountID.get())),
														List.of(-8, 4, 4)
												)
														.payingWith(ACCOUNT)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via("tokensTransferZeroCostTxn")
														.hasKnownStatus(SUCCESS),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, DEFAULT_GAS_COST),
												contractCall(THE_CONTRACT, SAFE_TOKENS_TRANSFER_ABI,
														asAddress(vanillaTokenID.get()),
														List.of(asAddress(treasuryID.get()),
																asAddress(firstAccountID.get()),
																asAddress(secondAccountID.get())),
														List.of(-10, 5, 5)
												)
														.payingWith(ACCOUNT)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via("tokensTransferDefaultCostTxn")
														.hasKnownStatus(SUCCESS),
												UtilVerbs.resetAppPropertiesTo(BOOTSTRAP_PROPERTIES)
										)
						)
				).then(
						withOpContext((spec, ignore) -> {
							final var zeroCostTxnRecord =
									getTxnRecord("tokensTransferZeroCostTxn").saveTxnRecordToRegistry("tokensTransferZeroCostTxnRec");
							final var defaultCostTxnRecord =
									getTxnRecord("tokensTransferDefaultCostTxn").saveTxnRecordToRegistry("tokensTransferDefaultCostTxnRec");
							CustomSpecAssert.allRunFor(spec, zeroCostTxnRecord, defaultCostTxnRecord);

							final var gasUsedForZeroCostTxn = spec.registry().getTransactionRecord("tokensTransferZeroCostTxnRec")
									.getContractCallResult().getGasUsed();
							final var gasUsedForDefaultCostTxn = spec.registry().getTransactionRecord("tokensTransferDefaultCostTxnRec")
									.getContractCallResult().getGasUsed();
							assertEquals(10_000L, gasUsedForDefaultCostTxn - gasUsedForZeroCostTxn);
						})
				);
	}

	public HapiApiSpec nftsTransferDynamicGasCostPrecompile() {
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> secondAccountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();

		return defaultHapiSpec("NftsTransferDynamicGasCostPrecompile")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(SECOND_ACCOUNT)
								.exposingCreatedIdTo(secondAccountID::set),
						fileCreate(THE_CONTRACT),
						updateLargeFile(ACCOUNT, THE_CONTRACT, extractByteCode(ContractResources.SAFE_OPERATIONS_CONTRACT)),
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
										metadata("secondMemo"),
										metadata("thirdMemo"),
										metadata("fourthMemo")
								)),
						tokenAssociate(ACCOUNT, VANILLA_TOKEN),
						cryptoUpdate(TOKEN_TREASURY).key(MULTI_KEY)
				)
				.when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, ZERO_GAS_COST),
												UtilVerbs.overriding(MAX_REFUND_PERCENT_OF_GAS_LIMIT, FULL_GAS_REFUND),
												contractCall(THE_CONTRACT, SAFE_NFTS_TRANSFER_ABI,
														asAddress(vanillaTokenID.get()),
														List.of(asAddress(treasuryID.get())),
														List.of(asAddress(accountID.get()), asAddress(secondAccountID.get())),
														List.of(1L, 2L))
														.payingWith(ACCOUNT)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via("nftsTransferZeroCostTxn")
														.hasKnownStatus(SUCCESS),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, DEFAULT_GAS_COST),
												contractCall(THE_CONTRACT, SAFE_NFTS_TRANSFER_ABI,
														asAddress(vanillaTokenID.get()),
														List.of(asAddress(treasuryID.get())),
														List.of(asAddress(accountID.get()), asAddress(secondAccountID.get())),
														List.of(3L, 4L))
														.payingWith(ACCOUNT)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via("nftsTransferDefaultCostTxn")
														.hasKnownStatus(SUCCESS),
												UtilVerbs.resetAppPropertiesTo(BOOTSTRAP_PROPERTIES)
										)
						)
				).then(
						withOpContext((spec, ignore) -> {
							final var zeroCostTxnRecord =
									getTxnRecord("nftsTransferZeroCostTxn").saveTxnRecordToRegistry("nftsTransferZeroCostTxnRec");
							final var defaultCostTxnRecord =
									getTxnRecord("nftsTransferDefaultCostTxn").saveTxnRecordToRegistry("nftsTransferDefaultCostTxnRec");
							CustomSpecAssert.allRunFor(spec, zeroCostTxnRecord, defaultCostTxnRecord);

							final var gasUsedForZeroCostTxn = spec.registry().getTransactionRecord("nftsTransferZeroCostTxnRec")
									.getContractCallResult().getGasUsed();
							final var gasUsedForDefaultCostTxn = spec.registry().getTransactionRecord("nftsTransferDefaultCostTxnRec")
									.getContractCallResult().getGasUsed();
							assertEquals(10_000L, gasUsedForDefaultCostTxn - gasUsedForZeroCostTxn);
						})
				);
	}
}

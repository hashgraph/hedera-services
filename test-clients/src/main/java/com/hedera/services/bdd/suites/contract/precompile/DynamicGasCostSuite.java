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
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.CommonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asDotDelimitedLongArray;
import static com.hedera.services.bdd.spec.HapiPropertySource.asSolidityAddress;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.ADDRESS_VAL_CALL_RETURNER_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.ADDRESS_VAL_CREATE_RETURNER_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.ADDRESS_VAL_RETURNER_PATH;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE2_FACTORY_DEPLOY_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE2_FACTORY_GET_ADDRESS_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE2_FACTORY_GET_BYTECODE_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE2_FACTORY_PATH;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_PLACEHOLDER_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.RETURN_THIS_ABI;
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
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.SALTING_CREATOR_CALL_CREATOR_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.SALTING_CREATOR_CREATE_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.SALTING_CREATOR_FACTORY_BUILD_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.SALTING_CREATOR_FACTORY_PATH;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.TEST_CONTRACT_GET_BALANCE_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.TEST_CONTRACT_VACATE_ADDRESS_ABI;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
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
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.KNOWABLE_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.MiscEETUtils.metadata;
import static com.hedera.services.legacy.core.CommonUtils.calculateSolidityAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.swirlds.common.CommonUtils.hex;
import static com.swirlds.common.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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
//				positiveSpecs(),
				create2Specs()
		);
	}

	List<HapiApiSpec> create2Specs() {
		return List.of(new HapiApiSpec[] {
//						create2FactoryWorksAsExpected(),
						priorityAddressIsCreate2ForStaticHapiCalls(),
//						priorityAddressIsCreate2ForInternalMessages(),
//						create2InputAddressIsStableWithTopLevelCallWhetherMirrorOrAliasIsUsed(),
				}
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

	private HapiApiSpec create2FactoryWorksAsExpected() {
		final var tcValue = 1_234L;
		final var creation2 = "create2Txn";
		final var initcode = "initcode";
		final var create2Factory = "create2Factory";

		final int salt = 42;
		final var adminKey = "adminKey";
		final var entityMemo = "JUST DO IT";
		final var customAutoRenew = 7776001L;
		final AtomicReference<String> factoryEvmAddress = new AtomicReference<>();
		final AtomicReference<String> expectedCreate2Address = new AtomicReference<>();
		final AtomicReference<String> expectedMirrorAddress = new AtomicReference<>();
		final AtomicReference<byte[]> testContractInitcode = new AtomicReference<>();

		return defaultHapiSpec("Create2FactoryWorksAsExpected")
				.given(
						newKeyNamed(adminKey),
						overriding("contracts.throttle.throttleByGas", "false"),
						fileCreate(initcode).path(CREATE2_FACTORY_PATH),
						contractCreate(create2Factory)
								.payingWith(GENESIS)
								.proxy("0.0.3")
								.bytecode(initcode)
								.adminKey(adminKey)
								.entityMemo(entityMemo)
								.autoRenewSecs(customAutoRenew)
								.via(creation2)
								.exposingNumTo(num -> factoryEvmAddress.set(calculateSolidityAddress(0, 0, num)))
				).when(
						sourcing(() -> contractCallLocal(
								create2Factory,
								CREATE2_FACTORY_GET_BYTECODE_ABI, factoryEvmAddress.get(), salt
						)
								.exposingTypedResultsTo(results -> {
									final var tcInitcode = (byte[]) results[0];
									testContractInitcode.set(tcInitcode);
									log.info("Contract reported TestContract initcode is {} bytes", tcInitcode.length);
								})
								.payingWith(GENESIS)
								.nodePayment(ONE_HBAR)),
						sourcing(() -> contractCallLocal(
								create2Factory,
								CREATE2_FACTORY_GET_ADDRESS_ABI, testContractInitcode.get(), salt
						)
								.exposingTypedResultsTo(results -> {
									log.info("Contract reported address results {}", results);
									final var expectedAddrBytes = (byte[]) results[0];
									final var hexedAddress = hex(expectedAddrBytes);
									log.info("  --> Expected CREATE2 address is {}", hexedAddress);
									expectedCreate2Address.set(hexedAddress);
								})
								.payingWith(GENESIS)),
						sourcing(() -> contractCall(
								create2Factory,
								CREATE2_FACTORY_DEPLOY_ABI, testContractInitcode.get(), salt
						)
								.payingWith(GENESIS)
								.gas(4_000_000L)
								.sending(tcValue)
								.via(creation2)),
						sourcing(() -> childRecordsCheck(creation2, SUCCESS,
								recordWith()
										.contractCreateResult(resultWith()
												.hexedEvmAddress(expectedCreate2Address.get()))
										.status(SUCCESS))),
						withOpContext((spec, opLog) -> {
							final var parentId = spec.registry().getContractId(create2Factory);
							final var childId = ContractID.newBuilder()
									.setContractNum(parentId.getContractNum() + 1L)
									.build();
							expectedMirrorAddress.set(hex(asSolidityAddress(childId)));
						})
				).then(
						sourcing(() -> contractCall(
								create2Factory,
								CREATE2_FACTORY_DEPLOY_ABI, testContractInitcode.get(), salt
						)
								.payingWith(GENESIS)
								.gas(4_000_000L)
								/* Cannot repeat CREATE2 with same args without destroying the existing contract */
								.hasKnownStatus(INVALID_SOLIDITY_ADDRESS)),
						sourcing(() -> getContractInfo(expectedCreate2Address.get())
								.has(contractWith().addressOrAlias(expectedCreate2Address.get()))),
						sourcing(() -> contractCallLocal(
								expectedCreate2Address.get(),
								TEST_CONTRACT_GET_BALANCE_ABI
						)
								.payingWith(GENESIS)
								.has(resultWith().resultThruAbi(
										TEST_CONTRACT_GET_BALANCE_ABI,
										isLiteralResult(new Object[] { BigInteger.valueOf(tcValue) })))),
						sourcing(() -> getContractInfo(expectedMirrorAddress.get())
								.has(contractWith().addressOrAlias(expectedCreate2Address.get()))),
						sourcing(() -> contractCall(expectedCreate2Address.get(), TEST_CONTRACT_VACATE_ADDRESS_ABI)
								.payingWith(GENESIS)),
						sourcing(() -> getContractInfo(expectedCreate2Address.get())
								.hasCostAnswerPrecheck(INVALID_CONTRACT_ID))
				);
	}

	private HapiApiSpec create2InputAddressIsStableWithTopLevelCallWhetherMirrorOrAliasIsUsed() {
		final var creation2 = "create2Txn";
		final var innerCreation2 = "innerCreate2Txn";
		final var delegateCreation2 = "delegateCreate2Txn";
		final var initcode = "initcode";
		final var saltingCreatorFactory = "saltingCreatorFactory";

		final AtomicReference<String> saltingCreatorAliasAddr = new AtomicReference<>();
		final AtomicReference<String> saltingCreatorMirrorAddr = new AtomicReference<>();
		final AtomicReference<String> tcAliasAddr1 = new AtomicReference<>();
		final AtomicReference<String> tcMirrorAddr1 = new AtomicReference<>();
		final AtomicReference<String> tcAliasAddr2 = new AtomicReference<>();
		final AtomicReference<String> tcMirrorAddr2 = new AtomicReference<>();

		final byte[] salt = unhex("aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011");

		return defaultHapiSpec("Create2InputAddressIsStableWithTopLevelCallWhetherMirrorOrAliasIsUsed")
				.given(
						overriding("contracts.throttle.throttleByGas", "false"),
						fileCreate(initcode),
						updateLargeFile(GENESIS, initcode, extractByteCode(SALTING_CREATOR_FACTORY_PATH)),
						contractCreate(saltingCreatorFactory)
								.payingWith(GENESIS)
								.proxy("0.0.3")
								.bytecode(initcode),
						contractCall(saltingCreatorFactory, SALTING_CREATOR_FACTORY_BUILD_ABI, salt)
								.payingWith(GENESIS)
								.gas(4_000_000L)
								.via(creation2),
						captureOneChildCreate2MetaFor(
								"Salting creator", creation2, saltingCreatorMirrorAddr, saltingCreatorAliasAddr)
				).when(
						sourcing(() -> contractCall(saltingCreatorAliasAddr.get(), SALTING_CREATOR_CREATE_ABI, salt)
								.payingWith(GENESIS)
								.gas(4_000_000L)
								.via(innerCreation2)),
						captureOneChildCreate2MetaFor(
								"Test contract create2'd via mirror address",
								innerCreation2, tcMirrorAddr1, tcAliasAddr1),
						sourcing(() -> contractCall(tcAliasAddr1.get(), TEST_CONTRACT_VACATE_ADDRESS_ABI)
								.payingWith(GENESIS)),
						sourcing(() -> getContractInfo(tcMirrorAddr1.get())
								.has(contractWith().isDeleted()))
				).then(
						sourcing(() -> contractCall(
								saltingCreatorFactory,
								SALTING_CREATOR_CALL_CREATOR_ABI, saltingCreatorAliasAddr.get(), salt
						)
								.payingWith(GENESIS)
								.gas(4_000_000L)
								.via(delegateCreation2)),
						captureOneChildCreate2MetaFor(
								"Test contract create2'd via alias address",
								delegateCreation2, tcMirrorAddr2, tcAliasAddr2),
						withOpContext((spec, opLog) -> {
							assertNotEquals(
									tcMirrorAddr1.get(), tcMirrorAddr2.get(),
									"Mirror addresses must be different");
							assertEquals(
									tcAliasAddr1.get(), tcAliasAddr2.get(),
									"Alias addresses must be stable");
						}),
						overriding("contracts.throttle.throttleByGas", "true")
				);
	}

	private HapiApiSpec priorityAddressIsCreate2ForStaticHapiCalls() {
		final var creation2 = "create2Txn";
		final var initcode = "initcode";
		final var placeholderCreation = "creation";
		final var returnerFactory = "returnerFactory";

		final AtomicReference<String> aliasAddr = new AtomicReference<>();
		final AtomicReference<String> mirrorAddr = new AtomicReference<>();
		final AtomicReference<BigInteger> staticCallAliasAns = new AtomicReference<>();
		final AtomicReference<BigInteger> staticCallMirrorAns = new AtomicReference<>();

		final byte[] salt = unhex("aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011");

		return defaultHapiSpec("PriorityAddressIsCreate2ForStaticHapiCalls")
				.given(
						fileCreate(initcode),
						updateLargeFile(GENESIS, initcode, extractByteCode(ADDRESS_VAL_RETURNER_PATH)),
						contractCreate(returnerFactory)
								.payingWith(GENESIS)
								.proxy("0.0.3")
								.bytecode(initcode),
						contractCall(returnerFactory, ADDRESS_VAL_CREATE_RETURNER_ABI, salt)
								.payingWith(GENESIS)
								.gas(4_000_000L)
								.via(creation2),
						captureOneChildCreate2MetaFor(
								"Returner", creation2, mirrorAddr, aliasAddr)
				).when(
						sourcing(() -> contractCallLocal(
								mirrorAddr.get(), RETURN_THIS_ABI
						)
								.payingWith(GENESIS)
								.exposingTypedResultsTo(results -> {
									log.info("Returner reported {} when called with mirror address", results);
									staticCallMirrorAns.set((BigInteger) results[0]);
								})),
						sourcing(() -> contractCallLocal(
								aliasAddr.get(), RETURN_THIS_ABI
						)
								.payingWith(GENESIS)
								.exposingTypedResultsTo(results -> {
									log.info("Returner reported {} when called with alias address", results);
									staticCallAliasAns.set((BigInteger) results[0]);
								}))
				).then(
						withOpContext((spec, opLog) -> {
							assertEquals(
									staticCallAliasAns.get(),
									staticCallMirrorAns.get(),
									"Static call with mirror address should be same as call with alias");
							assertEquals(
									staticCallAliasAns.get().toString(16),
									aliasAddr.get(),
									"Alias should get priority over mirror address");
						}),
						sourcing(() -> contractCall(
								aliasAddr.get(), CREATE_PLACEHOLDER_ABI
						)
								.gas(4_000_000L)
								.payingWith(GENESIS)
								.via(placeholderCreation)),
						getTxnRecord(placeholderCreation).andAllChildRecords().logged()
				);
	}

	private HapiApiSpec priorityAddressIsCreate2ForInternalMessages() {
		final var creation2 = "create2Txn";
		final var initcode = "initcode";
		final var returnerFactory = "returnerFactory";
		final var aliasCall = "aliasCall";
		final var mirrorCall = "mirrorCall";

		final AtomicReference<String> aliasAddr = new AtomicReference<>();
		final AtomicReference<String> mirrorAddr = new AtomicReference<>();
		final AtomicReference<BigInteger> staticCallAliasAns = new AtomicReference<>();
		final AtomicReference<BigInteger> staticCallMirrorAns = new AtomicReference<>();

		final byte[] salt = unhex("aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011");

		return defaultHapiSpec("PriorityAddressIsCreate2ForInternalMessages")
				.given(
						fileCreate(initcode),
						updateLargeFile(GENESIS, initcode, extractByteCode(ADDRESS_VAL_RETURNER_PATH)),
						contractCreate(returnerFactory)
								.payingWith(GENESIS)
								.proxy("0.0.3")
								.bytecode(initcode),
						contractCall(returnerFactory, ADDRESS_VAL_CREATE_RETURNER_ABI, salt)
								.payingWith(GENESIS)
								.gas(4_000_000L)
								.via(creation2),
						captureOneChildCreate2MetaFor(
								"Returner", creation2, mirrorAddr, aliasAddr)
				).when(
						sourcing(() -> contractCallLocal(
								returnerFactory, ADDRESS_VAL_CALL_RETURNER_ABI, mirrorAddr.get()
						)
								.payingWith(GENESIS)
								.exposingTypedResultsTo(results -> {
									log.info("Returner reported {} when called with mirror address", results);
									staticCallMirrorAns.set((BigInteger) results[0]);
								})),
						sourcing(() -> contractCallLocal(
								returnerFactory, ADDRESS_VAL_CALL_RETURNER_ABI, aliasAddr.get()
						)
								.payingWith(GENESIS)
								.exposingTypedResultsTo(results -> {
									log.info("Returner reported {} when called with alias address", results);
									staticCallAliasAns.set((BigInteger) results[0]);
								})),
						sourcing(() -> contractCall(
								returnerFactory, ADDRESS_VAL_CALL_RETURNER_ABI, aliasAddr.get()
						)
								.payingWith(GENESIS)
								.via(aliasCall)),
						sourcing(() -> contractCall(
								returnerFactory, ADDRESS_VAL_CALL_RETURNER_ABI, mirrorAddr.get()
						)
								.payingWith(GENESIS)
								.via(mirrorCall))
				).then(
						withOpContext((spec, opLog) -> {
							final var aliasLookup = getTxnRecord(aliasCall);
							final var mirrorLookup = getTxnRecord(aliasCall);
							allRunFor(spec, aliasLookup, mirrorLookup);
							final var aliasResult =
									aliasLookup.getResponseRecord().getContractCallResult().getContractCallResult();
							final var mirrorResult =
									mirrorLookup.getResponseRecord().getContractCallResult().getContractCallResult();
							assertEquals(
									aliasResult, mirrorResult,
									"Call with mirror address should be same as call with alias");
							assertEquals(
									staticCallAliasAns.get(),
									staticCallMirrorAns.get(),
									"Static call with mirror address should be same as call with alias");
						})
				);
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
						updateLargeFile(ACCOUNT, THE_CONTRACT,
								extractByteCode(ContractResources.SAFE_OPERATIONS_CONTRACT)),
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
														asAddress(vanillaTokenID.get()), amount,
														Collections.emptyList())
														.payingWith(ACCOUNT)
														.via("mintDynamicGasZeroCostTxn")
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.hasKnownStatus(SUCCESS),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, DEFAULT_GAS_COST),
												contractCall(THE_CONTRACT, SAFE_MINT_ABI,
														asAddress(vanillaTokenID.get()), amount,
														Collections.emptyList())
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
									getTxnRecord("mintDynamicGasZeroCostTxn").saveTxnRecordToRegistry(
											"mintZeroCostTxnRec");
							final var defaultCostTxnRecord =
									getTxnRecord("mintDynamicGasDefaultCostTxn").saveTxnRecordToRegistry(
											"mintDefaultCostTxnRec");
							allRunFor(spec, zeroCostTxnRecord, defaultCostTxnRecord);

							final var gasUsedForZeroCostTxn = spec.registry().getTransactionRecord("mintZeroCostTxnRec")
									.getContractCallResult().getGasUsed();
							final var gasUsedForDefaultCostTxn = spec.registry().getTransactionRecord(
									"mintDefaultCostTxnRec")
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
						updateLargeFile(ACCOUNT, THE_CONTRACT,
								extractByteCode(ContractResources.SAFE_OPERATIONS_CONTRACT)),
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
														asAddress(vanillaTokenID.get()), amount,
														Collections.emptyList())
														.payingWith(ACCOUNT)
														.via("burnDynamicGasZeroCostTxn")
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.hasKnownStatus(SUCCESS),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, DEFAULT_GAS_COST),
												mintToken(VANILLA_TOKEN, amount),
												contractCall(THE_CONTRACT, SAFE_BURN_ABI,
														asAddress(vanillaTokenID.get()), amount,
														Collections.emptyList())
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
									getTxnRecord("burnDynamicGasZeroCostTxn").saveTxnRecordToRegistry(
											"burnZeroCostTxnRec");
							final var defaultCostTxnRecord =
									getTxnRecord("burnDynamicGasDefaultCostTxn").saveTxnRecordToRegistry(
											"burnDefaultCostTxnRec");
							allRunFor(spec, zeroCostTxnRecord, defaultCostTxnRecord);

							final var gasUsedForZeroCostTxn = spec.registry().getTransactionRecord("burnZeroCostTxnRec")
									.getContractCallResult().getGasUsed();
							final var gasUsedForDefaultCostTxn = spec.registry().getTransactionRecord(
									"burnDefaultCostTxnRec")
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
						updateLargeFile(ACCOUNT, THE_CONTRACT,
								extractByteCode(ContractResources.SAFE_OPERATIONS_CONTRACT)),
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
									getTxnRecord("associateDynamicGasZeroCostTxn").saveTxnRecordToRegistry(
											"associateZeroCostTxnRec");
							final var defaultCostTxnRecord =
									getTxnRecord("associateDynamicGasDefaultCostTxn").saveTxnRecordToRegistry(
											"associateDefaultCostTxnRec");
							allRunFor(spec, zeroCostTxnRecord, defaultCostTxnRecord);

							final var gasUsedForZeroCostTxn = spec.registry().getTransactionRecord(
									"associateZeroCostTxnRec")
									.getContractCallResult().getGasUsed();
							final var gasUsedForDefaultCostTxn = spec.registry().getTransactionRecord(
									"associateDefaultCostTxnRec")
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
						updateLargeFile(ACCOUNT, THE_CONTRACT,
								extractByteCode(ContractResources.SAFE_OPERATIONS_CONTRACT)),
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
									getTxnRecord("dissociateZeroCostTxn").saveTxnRecordToRegistry(
											"dissociateZeroCostTxnRec");
							final var defaultCostTxnRecord =
									getTxnRecord("dissociateDefaultCostTxn").saveTxnRecordToRegistry(
											"dissociateDefaultCostTxnRec");
							allRunFor(spec, zeroCostTxnRecord, defaultCostTxnRecord);

							final var gasUsedForZeroCostTxn = spec.registry().getTransactionRecord(
									"dissociateZeroCostTxnRec")
									.getContractCallResult().getGasUsed();
							final var gasUsedForDefaultCostTxn = spec.registry().getTransactionRecord(
									"dissociateDefaultCostTxnRec")
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
						updateLargeFile(ACCOUNT, THE_CONTRACT,
								extractByteCode(ContractResources.SAFE_OPERATIONS_CONTRACT)),
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
									getTxnRecord("multipleAssociateZeroCostTxn").saveTxnRecordToRegistry(
											"multipleAssociateZeroCostTxnRec");
							final var defaultCostTxnRecord =
									getTxnRecord("multipleAssociateDefaultCostTxn").saveTxnRecordToRegistry(
											"multipleAssociateDefaultCostTxnRec");
							allRunFor(spec, zeroCostTxnRecord, defaultCostTxnRecord);

							final var gasUsedForZeroCostTxn = spec.registry().getTransactionRecord(
									"multipleAssociateZeroCostTxnRec")
									.getContractCallResult().getGasUsed();
							final var gasUsedForDefaultCostTxn = spec.registry().getTransactionRecord(
									"multipleAssociateDefaultCostTxnRec")
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
						updateLargeFile(ACCOUNT, THE_CONTRACT,
								extractByteCode(ContractResources.SAFE_OPERATIONS_CONTRACT)),
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
									getTxnRecord("multipleDissociateZeroCostTxn").saveTxnRecordToRegistry(
											"multipleDissociateZeroCostTxnRec");
							final var defaultCostTxnRecord =
									getTxnRecord("multipleDissociateDefaultCostTxn").saveTxnRecordToRegistry(
											"multipleDissociateDefaultCostTxnRec");
							allRunFor(spec, zeroCostTxnRecord, defaultCostTxnRecord);

							final var gasUsedForZeroCostTxn = spec.registry().getTransactionRecord(
									"multipleDissociateZeroCostTxnRec")
									.getContractCallResult().getGasUsed();
							final var gasUsedForDefaultCostTxn = spec.registry().getTransactionRecord(
									"multipleDissociateDefaultCostTxnRec")
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
						updateLargeFile(ACCOUNT, THE_CONTRACT,
								extractByteCode(ContractResources.SAFE_OPERATIONS_CONTRACT)),
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
									getTxnRecord("nftTransferZeroCostTxn").saveTxnRecordToRegistry(
											"nftTransferZeroCostTxnRec");
							final var defaultCostTxnRecord =
									getTxnRecord("nftTransferDefaultCostTxn").saveTxnRecordToRegistry(
											"nftTransferDefaultCostTxnRec");
							allRunFor(spec, zeroCostTxnRecord, defaultCostTxnRecord);

							final var gasUsedForZeroCostTxn = spec.registry().getTransactionRecord(
									"nftTransferZeroCostTxnRec")
									.getContractCallResult().getGasUsed();
							final var gasUsedForDefaultCostTxn = spec.registry().getTransactionRecord(
									"nftTransferDefaultCostTxnRec")
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
						updateLargeFile(ACCOUNT, THE_CONTRACT,
								extractByteCode(ContractResources.SAFE_OPERATIONS_CONTRACT)),
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
									getTxnRecord("tokenTransferZeroCostTxn").saveTxnRecordToRegistry(
											"tokenTransferZeroCostTxnRec");
							final var defaultCostTxnRecord =
									getTxnRecord("tokenTransferDefaultCostTxn").saveTxnRecordToRegistry(
											"tokenTransferDefaultCostTxnRec");
							allRunFor(spec, zeroCostTxnRecord, defaultCostTxnRecord);

							final var gasUsedForZeroCostTxn = spec.registry().getTransactionRecord(
									"tokenTransferZeroCostTxnRec")
									.getContractCallResult().getGasUsed();
							final var gasUsedForDefaultCostTxn = spec.registry().getTransactionRecord(
									"tokenTransferDefaultCostTxnRec")
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
						updateLargeFile(ACCOUNT, THE_CONTRACT,
								extractByteCode(ContractResources.SAFE_OPERATIONS_CONTRACT)),
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
									getTxnRecord("tokensTransferZeroCostTxn").saveTxnRecordToRegistry(
											"tokensTransferZeroCostTxnRec");
							final var defaultCostTxnRecord =
									getTxnRecord("tokensTransferDefaultCostTxn").saveTxnRecordToRegistry(
											"tokensTransferDefaultCostTxnRec");
							allRunFor(spec, zeroCostTxnRecord, defaultCostTxnRecord);

							final var gasUsedForZeroCostTxn = spec.registry().getTransactionRecord(
									"tokensTransferZeroCostTxnRec")
									.getContractCallResult().getGasUsed();
							final var gasUsedForDefaultCostTxn = spec.registry().getTransactionRecord(
									"tokensTransferDefaultCostTxnRec")
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
						updateLargeFile(ACCOUNT, THE_CONTRACT,
								extractByteCode(ContractResources.SAFE_OPERATIONS_CONTRACT)),
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
														List.of(asAddress(accountID.get()),
																asAddress(secondAccountID.get())),
														List.of(1L, 2L))
														.payingWith(ACCOUNT)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via("nftsTransferZeroCostTxn")
														.hasKnownStatus(SUCCESS),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, DEFAULT_GAS_COST),
												contractCall(THE_CONTRACT, SAFE_NFTS_TRANSFER_ABI,
														asAddress(vanillaTokenID.get()),
														List.of(asAddress(treasuryID.get())),
														List.of(asAddress(accountID.get()),
																asAddress(secondAccountID.get())),
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
									getTxnRecord("nftsTransferZeroCostTxn").saveTxnRecordToRegistry(
											"nftsTransferZeroCostTxnRec");
							final var defaultCostTxnRecord =
									getTxnRecord("nftsTransferDefaultCostTxn").saveTxnRecordToRegistry(
											"nftsTransferDefaultCostTxnRec");
							allRunFor(spec, zeroCostTxnRecord, defaultCostTxnRecord);

							final var gasUsedForZeroCostTxn = spec.registry().getTransactionRecord(
									"nftsTransferZeroCostTxnRec")
									.getContractCallResult().getGasUsed();
							final var gasUsedForDefaultCostTxn = spec.registry().getTransactionRecord(
									"nftsTransferDefaultCostTxnRec")
									.getContractCallResult().getGasUsed();
							assertEquals(10_000L, gasUsedForDefaultCostTxn - gasUsedForZeroCostTxn);
						})
				);
	}

	/* --- Internal helpers --- */
	private HapiSpecOperation captureOneChildCreate2MetaFor(
			final String desc,
			final String creation2,
			final AtomicReference<String> mirrorAddr,
			final AtomicReference<String> create2Addr
	) {
		return withOpContext((spec, opLog) -> {
			final var lookup = getTxnRecord(creation2).andAllChildRecords();
			allRunFor(spec, lookup);
			final var response = lookup.getResponse().getTransactionGetRecord();
			assertEquals(1, response.getChildTransactionRecordsCount());
			final var create2Record = response.getChildTransactionRecords(0);
			final var create2Address =
					create2Record.getContractCreateResult().getEvmAddress().getValue();
			create2Addr.set(CommonUtils.hex(create2Address.toByteArray()));
			final var createdId = create2Record.getReceipt().getContractID();
			mirrorAddr.set(CommonUtils.hex(asSolidityAddress(createdId)));
			opLog.info("{} is @ {} (mirror {})",
					desc,
					create2Addr.get(),
					mirrorAddr.get());
		});
	}

	private static TokenID asToken(String v) {
		long[] nativeParts = asDotDelimitedLongArray(v);
		return TokenID.newBuilder()
				.setShardNum(nativeParts[0])
				.setRealmNum(nativeParts[1])
				.setTokenNum(nativeParts[2])
				.build();
	}
}

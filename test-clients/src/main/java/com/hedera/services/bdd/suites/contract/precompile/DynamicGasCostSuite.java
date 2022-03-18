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
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.contract.Utils;
import com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransferList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asContractString;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.asSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.contractIdFromHexedMirrorAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.literalIdFromHexedMirrorAddress;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.ADDRESS_VAL_CALL_RETURNER_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.ADDRESS_VAL_CREATE_RETURNER_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.ADDRESS_VAL_RETURNER_PATH;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.BUILD_THEN_REVERT_THEN_BUILD_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE2_FACTORY_DEPLOY_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE2_FACTORY_GET_ADDRESS_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE2_FACTORY_GET_BYTECODE_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE2_FACTORY_PATH;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_AND_RECREATE_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_DONOR_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_DONOR_PATH;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_FACTORY_GET_BYTECODE_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_PLACEHOLDER_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.ERC_721_CONTRACT;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.ERC_721_OWNER_OF_CALL;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.NORMAL_DEPLOY_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.OUTER_CREATOR_PATH;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.PC2_ASSOCIATE_BOTH_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.PC2_CREATE_USER_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.PC2_DISSOCIATE_BOTH_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.PC2_FT_SEND_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.PC2_NFT_SEND_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.PC2_USER_HELPER_MINT_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.PC2_USER_MINT_NFT_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.PRECOMPILE_CREATE2_USER_PATH;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.RELINQUISH_FUNDS_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.RETURN_THIS_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.REVERTING_CREATE2_FACTORY_PATH;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.REVERTING_CREATE_FACTORY_PATH;
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
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.START_CHAIN_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.TEST_CONTRACT_GET_BALANCE_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.TEST_CONTRACT_VACATE_ADDRESS_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.WHAT_IS_FOO_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.WRONG_REPEATED_CREATE2_ABI;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.createLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
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
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_STILL_OWNS_NFTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OBTAINER_SAME_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REVERTED_SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.swirlds.common.CommonUtils.hex;
import static com.swirlds.common.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
	public List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
//				positiveSpecs(),
				create2Specs()
		);
	}

	List<HapiApiSpec> create2Specs() {
		return List.of(new HapiApiSpec[] {
//						create2FactoryWorksAsExpected(),
//						canDeleteViaAlias(),
//						cannotSelfDestructToMirrorAddress(),
//						priorityAddressIsCreate2ForStaticHapiCalls(),
//						priorityAddressIsCreate2ForInternalMessages(),
//						create2InputAddressIsStableWithTopLevelCallWhetherMirrorOrAliasIsUsed(),
//						canUseAliasesInPrecompilesAndContractKeys(),
//						inlineCreateCanFailSafely(),
//						inlineCreate2CanFailSafely(),
//						allLogOpcodesResolveExpectedContractId(),
						eip1014AliasIsPriorityInErcOwnerPrecompile(),
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

	private HapiApiSpec allLogOpcodesResolveExpectedContractId() {
		final var creation = "creation";
		final var initcode = "initcode";
		final var outerCreator = "outerCreator";

		final AtomicLong outerCreatorNum = new AtomicLong();
		final byte[] msg = new byte[] { (byte) 0xAB };
		final var noisyTxn = "noisyTxn";

		return defaultHapiSpec("AllLogOpcodesResolveExpectedContractId")
				.given(
						fileCreate(initcode).path(OUTER_CREATOR_PATH),
						contractCreate(outerCreator)
								.payingWith(GENESIS)
								.bytecode(initcode)
								.via(creation)
								.exposingNumTo(outerCreatorNum::set)
				).when(
						contractCall(outerCreator, START_CHAIN_ABI, msg)
								.gas(4_000_000)
								.via(noisyTxn)
				).then(
						sourcing(() -> {
							final var idOfFirstThreeLogs = "0.0." + (outerCreatorNum.get() + 1);
							final var idOfLastTwoLogs = "0.0." + (outerCreatorNum.get() + 2);
							return getTxnRecord(noisyTxn)
									.andAllChildRecords()
									.hasPriority(recordWith().contractCallResult(resultWith()
											.logs(inOrder(
													logWith().contract(idOfFirstThreeLogs),
													logWith().contract(idOfFirstThreeLogs),
													logWith().contract(idOfFirstThreeLogs),
													logWith().contract(idOfLastTwoLogs),
													logWith().contract(idOfLastTwoLogs)
											)))).logged();
						})
				);
	}

	// https://github.com/hashgraph/hedera-services/issues/2868
	private HapiApiSpec inlineCreate2CanFailSafely() {
		final var tcValue = 1_234L;
		final var creation = "creation";
		final var initcode = "initcode";
		final var inlineCreate2Factory = "inlineCreate2Factory";

		final int foo = 22;
		final int salt = 23;
		final int timesToFail = 7;
		final AtomicLong factoryEntityNum = new AtomicLong();
		final AtomicReference<String> factoryEvmAddress = new AtomicReference<>();
		final AtomicReference<byte[]> testContractInitcode = new AtomicReference<>();

		return defaultHapiSpec("InlineCreate2CanFailSafely")
				.given(
						overriding("contracts.throttle.throttleByGas", "false"),
						fileCreate(initcode).path(REVERTING_CREATE2_FACTORY_PATH),
						contractCreate(inlineCreate2Factory)
								.payingWith(GENESIS)
								.bytecode(initcode)
								.via(creation)
								.exposingNumTo(num -> {
									factoryEntityNum.set(num);
									factoryEvmAddress.set(asHexedSolidityAddress(0, 0, num));
								})
				).when(
						sourcing(() -> contractCallLocal(
								inlineCreate2Factory,
								CREATE2_FACTORY_GET_BYTECODE_ABI, factoryEvmAddress.get(), foo
						)
								.exposingTypedResultsTo(results -> {
									final var tcInitcode = (byte[]) results[0];
									testContractInitcode.set(tcInitcode);
									log.info("Contract reported TestContract initcode is {} bytes", tcInitcode.length);
								})
								.payingWith(GENESIS)
								.nodePayment(ONE_HBAR))
				).then(
						inParallel(IntStream.range(0, timesToFail).mapToObj(i ->
										sourcing(() -> contractCall(
												inlineCreate2Factory,
												CREATE2_FACTORY_DEPLOY_ABI, testContractInitcode.get(), salt
										)
												.payingWith(GENESIS)
												.gas(4_000_000L)
												.sending(tcValue)
												.via(creation))
								).toArray(HapiSpecOperation[]::new)
						),
						sourcing(() -> cryptoCreate("nextUp").exposingCreatedIdTo(id ->
								log.info("Next entity num was {} instead of expected {}",
										id.getAccountNum(),
										factoryEntityNum.get() + 1)))
				);
	}

	private HapiApiSpec inlineCreateCanFailSafely() {
		final var tcValue = 1_234L;
		final var creation = "creation";
		final var initcode = "initcode";
		final var inlineCreateFactory = "inlineCreateFactory";

		final int foo = 22;
		final int timesToFail = 7;
		final AtomicLong factoryEntityNum = new AtomicLong();
		final AtomicReference<String> factoryEvmAddress = new AtomicReference<>();
		final AtomicReference<byte[]> testContractInitcode = new AtomicReference<>();

		return defaultHapiSpec("InlineCreateCanFailSafely")
				.given(
						overriding("contracts.throttle.throttleByGas", "false"),
						fileCreate(initcode).path(REVERTING_CREATE_FACTORY_PATH),
						contractCreate(inlineCreateFactory)
								.payingWith(GENESIS)
								.bytecode(initcode)
								.via(creation)
								.exposingNumTo(num -> {
									factoryEntityNum.set(num);
									factoryEvmAddress.set(asHexedSolidityAddress(0, 0, num));
								})
				).when(
						sourcing(() -> contractCallLocal(
								inlineCreateFactory,
								CREATE_FACTORY_GET_BYTECODE_ABI, factoryEvmAddress.get(), foo
						)
								.exposingTypedResultsTo(results -> {
									final var tcInitcode = (byte[]) results[0];
									testContractInitcode.set(tcInitcode);
									log.info("Contract reported TestContract initcode is {} bytes", tcInitcode.length);
								})
								.payingWith(GENESIS)
								.nodePayment(ONE_HBAR))
				).then(
						inParallel(IntStream.range(0, timesToFail).mapToObj(i ->
										sourcing(() -> contractCall(
												inlineCreateFactory,
												NORMAL_DEPLOY_ABI, testContractInitcode.get()
										)
												.payingWith(GENESIS)
												.gas(4_000_000L)
												.sending(tcValue)
												.via(creation))
								).toArray(HapiSpecOperation[]::new)
						),
						sourcing(() -> cryptoCreate("nextUp").exposingCreatedIdTo(id ->
								log.info("Next entity num was {} instead of expected {}",
										id.getAccountNum(),
										factoryEntityNum.get() + 1)))
				);
	}

	// https://github.com/hashgraph/hedera-services/issues/2867
	// https://github.com/hashgraph/hedera-services/issues/2868
	private HapiApiSpec create2FactoryWorksAsExpected() {
		final var tcValue = 1_234L;
		final var creation2 = "create2Txn";
		final var initcode = "initcode";
		final var create2Factory = "create2Factory";

		final int salt = 42;
		final var adminKey = "adminKey";
		final var replAdminKey = "replAdminKey";
		final var entityMemo = "JUST DO IT";
		final var customAutoRenew = 7776001L;
		final AtomicReference<String> factoryEvmAddress = new AtomicReference<>();
		final AtomicReference<String> expectedCreate2Address = new AtomicReference<>();
		final AtomicReference<String> expectedMirrorAddress = new AtomicReference<>();
		final AtomicReference<byte[]> testContractInitcode = new AtomicReference<>();
		final AtomicReference<byte[]> bytecodeFromMirror = new AtomicReference<>();
		final AtomicReference<byte[]> bytecodeFromAlias = new AtomicReference<>();
		final AtomicReference<String> mirrorLiteralId = new AtomicReference<>();

		return defaultHapiSpec("Create2FactoryWorksAsExpected")
				.given(
						newKeyNamed(adminKey),
						newKeyNamed(replAdminKey),
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
								.exposingNumTo(num -> factoryEvmAddress.set(asHexedSolidityAddress(0, 0, num)))
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
						// First check the feature toggle
						overriding("contracts.allowCreate2", "false"),
						sourcing(() -> contractCall(
								create2Factory,
								CREATE2_FACTORY_DEPLOY_ABI, testContractInitcode.get(), salt
						)
								.payingWith(GENESIS)
								.gas(4_000_000L)
								.sending(tcValue)
								.via(creation2)
								.hasKnownStatus(CONTRACT_EXECUTION_EXCEPTION)),
						// Now re-enable CREATE2 and proceed
						overriding("contracts.allowCreate2", "true"),
						// https://github.com/hashgraph/hedera-services/issues/2867 - cannot re-create same address
						sourcing(() -> contractCall(
								create2Factory,
								WRONG_REPEATED_CREATE2_ABI, testContractInitcode.get(), salt
						)
								.payingWith(GENESIS)
								.gas(4_000_000L)
								.sending(tcValue)
								.hasKnownStatus(INVALID_SOLIDITY_ADDRESS)),
						sourcing(() -> getContractInfo(expectedCreate2Address.get())
								.hasCostAnswerPrecheck(INVALID_CONTRACT_ID)),
						sourcing(() -> contractCall(
								create2Factory,
								CREATE2_FACTORY_DEPLOY_ABI, testContractInitcode.get(), salt
						)
								.payingWith(GENESIS)
								.gas(4_000_000L)
								.sending(tcValue)),
						sourcing(() -> contractDelete(expectedCreate2Address.get())
								.signedBy(DEFAULT_PAYER, adminKey)),
						logIt("Deleted the deployed CREATE2 contract using HAPI"),
						sourcing(() -> contractCall(
								create2Factory,
								CREATE2_FACTORY_DEPLOY_ABI, testContractInitcode.get(), salt
						)
								.payingWith(GENESIS)
								.gas(4_000_000L)
								.sending(tcValue)
								.via(creation2)),
						logIt("Re-deployed the CREATE2 contract"),
						sourcing(() -> childRecordsCheck(creation2, SUCCESS,
								recordWith()
										.contractCreateResult(resultWith()
												.hexedEvmAddress(expectedCreate2Address.get()))
										.status(SUCCESS))),
						withOpContext((spec, opLog) -> {
							final var parentId = spec.registry().getContractId(create2Factory);
							final var childId = ContractID.newBuilder()
									.setContractNum(parentId.getContractNum() + 2L)
									.build();
							mirrorLiteralId.set("0.0." + childId.getContractNum());
							expectedMirrorAddress.set(hex(asSolidityAddress(childId)));
						}),
						sourcing(() -> getContractBytecode(mirrorLiteralId.get())
								.exposingBytecodeTo(bytecodeFromMirror::set)),
						// https://github.com/hashgraph/hedera-services/issues/2874
						sourcing(() -> getContractBytecode(expectedCreate2Address.get())
								.exposingBytecodeTo(bytecodeFromAlias::set)),
						withOpContext((spec, opLog) -> assertArrayEquals(
								bytecodeFromAlias.get(), bytecodeFromMirror.get(),
								"Bytecode should be get-able using alias")),
						sourcing(() -> contractUpdate(expectedCreate2Address.get())
								.signedBy(DEFAULT_PAYER, adminKey, replAdminKey)
								.newKey(replAdminKey))
				).then(
						sourcing(() -> contractCall(
								create2Factory,
								CREATE2_FACTORY_DEPLOY_ABI, testContractInitcode.get(), salt
						)
								.payingWith(GENESIS)
								.gas(4_000_000L)
								/* Cannot repeat CREATE2 with same args without destroying the existing contract */
								.hasKnownStatus(INVALID_SOLIDITY_ADDRESS)),
						// https://github.com/hashgraph/hedera-services/issues/2874
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
								.has(contractWith()
										.adminKey(replAdminKey)
										.addressOrAlias(expectedCreate2Address.get()))),
						sourcing(() -> contractCall(expectedCreate2Address.get(), TEST_CONTRACT_VACATE_ADDRESS_ABI)
								.payingWith(GENESIS)),
						sourcing(() -> getContractInfo(expectedCreate2Address.get())
								.hasCostAnswerPrecheck(INVALID_CONTRACT_ID))
				);
	}

	private HapiApiSpec eip1014AliasIsPriorityInErcOwnerPrecompile() {
		final var creation2 = "create2Txn";
		final var initcode = "initcode";
		final var ercInitcode = "ercInitcode";
		final var ercContract = "ercContract";
		final var pc2User = "pc2User";
		final var nft = "nonFungibleToken";
		final var multiKey = "swiss";
		final var lookup = "ownerOfPrecompile";

		final AtomicReference<String> userAliasAddr = new AtomicReference<>();
		final AtomicReference<String> userMirrorAddr = new AtomicReference<>();
		final AtomicReference<String> userLiteralId = new AtomicReference<>();
		final AtomicReference<byte[]> nftAddress = new AtomicReference<>();

		final byte[] salt = unhex("aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011");

		return defaultHapiSpec("Eip1014AliasIsPriorityInErcOwnerPrecompile")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(TOKEN_TREASURY),
						fileCreate(initcode),
						updateLargeFile(GENESIS, initcode, extractByteCode(PRECOMPILE_CREATE2_USER_PATH)),
						createLargeFile(GENESIS, ercInitcode, extractByteCode(ERC_721_CONTRACT)),
						contractCreate(ercContract).omitAdminKey().bytecode(ercInitcode),
						contractCreate(pc2User)
								.adminKey(multiKey)
								.payingWith(GENESIS)
								.proxy("0.0.3")
								.bytecode(initcode),
						contractCall(pc2User, PC2_CREATE_USER_ABI, salt)
								.payingWith(GENESIS)
								.gas(4_000_000L)
								.via(creation2),
						captureOneChildCreate2MetaFor(
								"Precompile user", creation2, userMirrorAddr, userAliasAddr),
						withOpContext((spec, opLog) ->
								userLiteralId.set(
										asContractString(
												contractIdFromHexedMirrorAddress(userMirrorAddr.get())))),
						sourcing(() -> tokenCreate(nft)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(userLiteralId.get())
								.initialSupply(0L)
								.supplyKey(multiKey)
								.fee(ONE_HUNDRED_HBARS)
								.signedBy(DEFAULT_PAYER, multiKey)),
						mintToken(nft, List.of(
								ByteString.copyFromUtf8("PRICELESS")
						))
				).when(
						withOpContext((spec, opLog) -> {
							final var nftType = spec.registry().getTokenID(nft);
							nftAddress.set(asSolidityAddress(nftType));
						}),
						sourcing(() -> getContractInfo(userLiteralId.get()).logged()),
						sourcing(() -> contractCall(ercContract,
								ERC_721_OWNER_OF_CALL, nftAddress.get(), 1)
								.via(lookup)
								.gas(4_000_000))
				).then(
						sourcing(() -> childRecordsCheck(lookup, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.OWNER)
																.withOwner(unhex(userAliasAddr.get()))
														))))
				);
	}

	private HapiApiSpec canUseAliasesInPrecompilesAndContractKeys() {
		final var creation2 = "create2Txn";
		final var initcode = "initcode";
		final var pc2User = "pc2User";
		final var ft = "fungibleToken";
		final var nft = "nonFungibleToken";
		final var otherNft = "otherNonFungibleToken";
		final var multiKey = "swiss";
		final var ftFail = "ofInterest";
		final var nftFail = "alsoOfInterest";
		final var helperMintFail = "alsoOfExtremeInterest";
		final var helperMintSuccess = "quotidian";

		final AtomicReference<String> userAliasAddr = new AtomicReference<>();
		final AtomicReference<String> userMirrorAddr = new AtomicReference<>();
		final AtomicReference<String> userLiteralId = new AtomicReference<>();
		final AtomicReference<String> hexedNftType = new AtomicReference<>();

		final byte[] salt = unhex("aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011");

		return defaultHapiSpec("CanUseAliasesInPrecompiles")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(TOKEN_TREASURY),
						fileCreate(initcode),
						updateLargeFile(GENESIS, initcode, extractByteCode(PRECOMPILE_CREATE2_USER_PATH)),
						contractCreate(pc2User)
								.omitAdminKey()
								.payingWith(GENESIS)
								.proxy("0.0.3")
								.bytecode(initcode),
						contractCall(pc2User, PC2_CREATE_USER_ABI, salt)
								.payingWith(GENESIS)
								.gas(4_000_000L)
								.via(creation2),
						captureOneChildCreate2MetaFor(
								"Precompile user", creation2, userMirrorAddr, userAliasAddr),
						withOpContext((spec, opLog) ->
								userLiteralId.set(
										asContractString(
												contractIdFromHexedMirrorAddress(userMirrorAddr.get())))),
						tokenCreate(ft)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(1_000),
						tokenCreate(nft)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.initialSupply(0L)
								.supplyKey(multiKey),
						mintToken(nft, List.of(
								ByteString.copyFromUtf8("PRICELESS")
						)),
						tokenUpdate(nft).supplyKey(() -> Utils.aliasContractIdKey(userAliasAddr.get()))
				).when(
						withOpContext((spec, opLog) -> {
							final var registry = spec.registry();
							final var ftType = registry.getTokenID(ft);
							final var nftType = registry.getTokenID(nft);

							final var ftAssoc = contractCall(
									pc2User, PC2_ASSOCIATE_BOTH_ABI, hex(asSolidityAddress(ftType))
							)
									.gas(4_000_000L);
							final var nftAssoc = contractCall(
									pc2User, PC2_ASSOCIATE_BOTH_ABI, hex(asSolidityAddress(nftType))
							)
									.gas(4_000_000L);

							final var fundingXfer = cryptoTransfer(
									moving(100, ft).between(TOKEN_TREASURY, pc2User),
									movingUnique(nft, 1L).between(TOKEN_TREASURY, pc2User)
							);

							// https://github.com/hashgraph/hedera-services/issues/2874 (alias in transfer precompile)
							final var sendFt = contractCall(
									pc2User, PC2_FT_SEND_ABI, hex(asSolidityAddress(ftType)), 100
							)
									.gas(4_000_000L);
							final var sendNft = contractCall(
									pc2User, PC2_NFT_SEND_ABI, hex(asSolidityAddress(nftType)), 1
							)
									.via(ftFail)
									.gas(4_000_000L);
							final var failFtDissoc = contractCall(
									pc2User, PC2_DISSOCIATE_BOTH_ABI, hex(asSolidityAddress(ftType))
							)
									.via(ftFail)
									.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
									.gas(4_000_000L);
							final var failNftDissoc = contractCall(
									pc2User, PC2_DISSOCIATE_BOTH_ABI, hex(asSolidityAddress(nftType))
							)
									.via(nftFail)
									.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
									.gas(4_000_000L);
							// https://github.com/hashgraph/hedera-services/issues/2876 (mint via ContractID key)
							final var mint = contractCall(
									userAliasAddr.get(),
									PC2_USER_MINT_NFT_ABI,
									hex(asSolidityAddress(nftType)),
									List.of("WoRtHlEsS")
							)
									.gas(4_000_000L);
							/* Can't succeed yet because supply key isn't delegatable */
							hexedNftType.set(hex(asSolidityAddress(nftType)));
							final var helperMint = contractCall(
									userAliasAddr.get(),
									PC2_USER_HELPER_MINT_ABI,
									hexedNftType.get(),
									List.of("WoRtHlEsS")
							)
									.via(helperMintFail)
									.gas(4_000_000L);

							allRunFor(spec,
									ftAssoc, nftAssoc,
									fundingXfer, sendFt, sendNft, failFtDissoc, failNftDissoc,
									mint, helperMint);
						})
				).then(
						childRecordsCheck(helperMintFail, SUCCESS,
								/* First record is of helper creation */
								recordWith().status(SUCCESS),
								recordWith().status(INVALID_SIGNATURE)),
						childRecordsCheck(ftFail, CONTRACT_REVERT_EXECUTED,
								recordWith().status(REVERTED_SUCCESS),
								recordWith().status(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES)),
						childRecordsCheck(nftFail, CONTRACT_REVERT_EXECUTED,
								recordWith().status(REVERTED_SUCCESS),
								recordWith().status(ACCOUNT_STILL_OWNS_NFTS)),
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(nft, 1),

						// https://github.com/hashgraph/hedera-services/issues/2876 (mint via delegatable_contract_id)
						tokenUpdate(nft).supplyKey(() -> Utils.aliasDelegateContractKey(userAliasAddr.get())),
						sourcing(() -> contractCall(
								userAliasAddr.get(),
								PC2_USER_HELPER_MINT_ABI,
								hexedNftType.get(),
								List.of("WoRtHlEsS...NOT")
						)
								.via(helperMintSuccess)
								.gas(4_000_000L)),
						getTxnRecord(helperMintSuccess).andAllChildRecords().logged(),
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(nft, 2),
						cryptoTransfer((spec, b) -> {
							final var registry = spec.registry();
							final var tt = registry.getAccountID(TOKEN_TREASURY);
							final var ftId = registry.getTokenID(ft);
							final var nftId = registry.getTokenID(nft);
							b.setTransfers(TransferList.newBuilder()
									.addAccountAmounts(Utils.aaWith(tt, -666))
									.addAccountAmounts(Utils.aaWith(userMirrorAddr.get(), +666)));
							b.addTokenTransfers(TokenTransferList.newBuilder()
											.setToken(ftId)
											.addTransfers(Utils.aaWith(tt, -6))
											.addTransfers(Utils.aaWith(userMirrorAddr.get(), +6)))
									.addTokenTransfers(TokenTransferList.newBuilder()
											.setToken(nftId)
											.addNftTransfers(NftTransfer.newBuilder()
													.setSerialNumber(2L)
													.setSenderAccountID(tt)
													.setReceiverAccountID(Utils.accountId(userMirrorAddr.get()))));
						}).signedBy(DEFAULT_PAYER, TOKEN_TREASURY),
						sourcing(() -> getContractInfo(userLiteralId.get()).logged())
				);
	}

	// https://github.com/hashgraph/hedera-services/issues/2874
	// https://github.com/hashgraph/hedera-services/issues/2925
	private HapiApiSpec cannotSelfDestructToMirrorAddress() {
		final var creation2 = "create2Txn";
		final var messyCreation2 = "messyCreate2Txn";
		final var initcode = "initcode";
		final var createDonor = "createDonor";

		final AtomicReference<String> donorAliasAddr = new AtomicReference<>();
		final AtomicReference<String> donorMirrorAddr = new AtomicReference<>();
		final AtomicReference<String> mDonorAliasAddr = new AtomicReference<>();
		final AtomicReference<String> mDonorMirrorAddr = new AtomicReference<>();

		final byte[] salt = unhex("aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011");
		final byte[] otherSalt = unhex("aabbccddee880011aabbccddee880011aabbccddee880011aabbccddee880011");

		return defaultHapiSpec("CannotSelfDestructToMirrorAddress")
				.given(
						fileCreate(initcode),
						updateLargeFile(GENESIS, initcode, extractByteCode(CREATE_DONOR_PATH)),
						contractCreate(createDonor)
								.payingWith(GENESIS)
								.proxy("0.0.3")
								.bytecode(initcode),
						contractCall(createDonor, CREATE_DONOR_ABI, salt)
								.sending(1_000)
								.payingWith(GENESIS)
								.gas(4_000_000L)
								.via(creation2),
						captureOneChildCreate2MetaFor(
								"donor", creation2, donorMirrorAddr, donorAliasAddr)
				).when(
						sourcing(() -> contractCall(
								donorAliasAddr.get(),
								RELINQUISH_FUNDS_ABI,
								donorAliasAddr.get()).hasKnownStatus(OBTAINER_SAME_CONTRACT_ID)),
						sourcing(() -> contractCall(
								donorAliasAddr.get(),
								RELINQUISH_FUNDS_ABI,
								donorMirrorAddr.get()).hasKnownStatus(OBTAINER_SAME_CONTRACT_ID))
				).then(
						contractCall(createDonor, BUILD_THEN_REVERT_THEN_BUILD_ABI, otherSalt)
								.sending(1_000)
								.payingWith(GENESIS)
								.gas(4_000_000L)
								.via(messyCreation2),
						captureOneChildCreate2MetaFor(
								"questionableDonor", messyCreation2, mDonorMirrorAddr, mDonorAliasAddr),
						sourcing(() -> getContractInfo(mDonorAliasAddr.get())
								.has(contractWith().balance(100))
								.logged())
				);
	}

	// https://github.com/hashgraph/hedera-services/issues/2874
	private HapiApiSpec canDeleteViaAlias() {
		final var adminKey = "adminKey";
		final var creation2 = "create2Txn";
		final var initcode = "initcode";
		final var deletion = "deletion";
		final var saltingCreatorFactory = "saltingCreatorFactory";

		final AtomicReference<String> saltingCreatorAliasAddr = new AtomicReference<>();
		final AtomicReference<String> saltingCreatorMirrorAddr = new AtomicReference<>();
		final AtomicReference<String> saltingCreatorLiteralId = new AtomicReference<>();

		final byte[] salt = unhex("aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011");
		final byte[] otherSalt = unhex("aabbccddee330011aabbccddee330011aabbccddee330011aabbccddee330011");

		return defaultHapiSpec("CanDeleteViaAlias")
				.given(
						newKeyNamed(adminKey),
						fileCreate(initcode),
						updateLargeFile(GENESIS, initcode, extractByteCode(SALTING_CREATOR_FACTORY_PATH)),
						contractCreate(saltingCreatorFactory)
								.adminKey(adminKey)
								.payingWith(GENESIS)
								.proxy("0.0.3")
								.bytecode(initcode),
						contractCall(saltingCreatorFactory, SALTING_CREATOR_FACTORY_BUILD_ABI, salt)
								.payingWith(GENESIS)
								.gas(4_000_000L)
								.via(creation2),
						captureOneChildCreate2MetaFor(
								"Salting creator", creation2, saltingCreatorMirrorAddr, saltingCreatorAliasAddr),
						withOpContext((spec, opLog) ->
								saltingCreatorLiteralId.set(
										asContractString(
												contractIdFromHexedMirrorAddress(saltingCreatorMirrorAddr.get())))),
						// https://github.com/hashgraph/hedera-services/issues/2867 (can't re-create2 after
						// selfdestruct)
						sourcing(() -> contractCall(saltingCreatorAliasAddr.get(), CREATE_AND_RECREATE_ABI, otherSalt)
								.payingWith(GENESIS)
								.gas(2_000_000L)
								.hasKnownStatus(CONTRACT_REVERT_EXECUTED))
				).when(
						sourcing(() -> contractUpdate(saltingCreatorAliasAddr.get())
								.signedBy(DEFAULT_PAYER, adminKey)
								.memo("That's why you always leave a note")),
						sourcing(() -> contractCallLocal(
								saltingCreatorAliasAddr.get(), WHAT_IS_FOO_ABI
						).has(resultWith().resultThruAbi(
								WHAT_IS_FOO_ABI, isLiteralResult(new Object[] { BigInteger.valueOf(42) })
						))),
						sourcing(() -> contractDelete(saltingCreatorAliasAddr.get())
								.signedBy(DEFAULT_PAYER, adminKey)
								.transferContract(saltingCreatorMirrorAddr.get())
								.hasKnownStatus(OBTAINER_SAME_CONTRACT_ID)),
						sourcing(() -> contractDelete(saltingCreatorMirrorAddr.get())
								.signedBy(DEFAULT_PAYER, adminKey)
								.transferContract(saltingCreatorAliasAddr.get())
								.hasKnownStatus(OBTAINER_SAME_CONTRACT_ID))
				).then(
						sourcing(() -> getContractInfo(saltingCreatorMirrorAddr.get())
								.has(contractWith().addressOrAlias(saltingCreatorAliasAddr.get()))),
						sourcing(() -> contractDelete(saltingCreatorAliasAddr.get())
								.signedBy(DEFAULT_PAYER, adminKey)
								.transferAccount(FUNDING)
								.via(deletion)),
						sourcing(() -> getTxnRecord(deletion)
								.hasPriority(recordWith().targetedContractId(saltingCreatorLiteralId.get()))),
						sourcing(() -> contractDelete(saltingCreatorMirrorAddr.get())
								.signedBy(DEFAULT_PAYER, adminKey)
								.transferAccount(FUNDING)
								.hasPrecheck(CONTRACT_DELETED)),
						sourcing(() -> getContractInfo(saltingCreatorMirrorAddr.get())
								.has(contractWith().addressOrAlias(saltingCreatorMirrorAddr.get())))
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
						sourcing(() -> {
							final var emitterId = literalIdFromHexedMirrorAddress(saltingCreatorMirrorAddr.get());
							return getTxnRecord(innerCreation2)
									.hasPriority(recordWith()
											.contractCallResult(
													resultWith()
															.contract(emitterId)
															.logs(inOrder(logWith().contract(emitterId)))))
									.andAllChildRecords().logged();
						}),
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
						})
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
							assertTrue(
									aliasAddr.get().endsWith(staticCallAliasAns.get().toString(16)),
									"Alias should get priority over mirror address");
						}),
						sourcing(() -> contractCall(
								aliasAddr.get(), CREATE_PLACEHOLDER_ABI
						)
								.gas(4_000_000L)
								.payingWith(GENESIS)
								.via(placeholderCreation))
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
								.exposingCreatedIdTo(id -> vanillaTokenID.set(Utils.asToken(id)))
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
								.exposingCreatedIdTo(id -> vanillaTokenID.set(Utils.asToken(id))),
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
								.exposingCreatedIdTo(id -> vanillaTokenID.set(Utils.asToken(id)))
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
								.exposingCreatedIdTo(id -> vanillaTokenID.set(Utils.asToken(id)))
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
								.exposingCreatedIdTo(id -> vanillaTokenID.set(Utils.asToken(id))),
						tokenCreate(KNOWABLE_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(10L)
								.exposingCreatedIdTo(id -> knowableTokenID.set(Utils.asToken(id)))
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
								.exposingCreatedIdTo(id -> vanillaTokenID.set(Utils.asToken(id))),
						tokenCreate(KNOWABLE_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(10)
								.exposingCreatedIdTo(id -> knowableTokenTokenID.set(Utils.asToken(id)))
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
								.exposingCreatedIdTo(id -> vanillaTokenID.set(Utils.asToken(id))),
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
								.exposingCreatedIdTo(id -> vanillaTokenID.set(Utils.asToken(id))),
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
								.exposingCreatedIdTo(id -> vanillaTokenID.set(Utils.asToken(id))),
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
								.exposingCreatedIdTo(id -> vanillaTokenID.set(Utils.asToken(id))),
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
	public static HapiSpecOperation captureOneChildCreate2MetaFor(
			final String desc,
			final String creation2,
			final AtomicReference<String> mirrorAddr,
			final AtomicReference<String> create2Addr
	) {
		return captureChildCreate2MetaFor(1, 0, desc, creation2, mirrorAddr, create2Addr);
	}

	public static HapiSpecOperation captureChildCreate2MetaFor(
			final int numExpectedChildren,
			final int childOfInterest,
			final String desc,
			final String creation2,
			final AtomicReference<String> mirrorAddr,
			final AtomicReference<String> create2Addr
	) {
		return withOpContext((spec, opLog) -> {
			final var lookup = getTxnRecord(creation2).andAllChildRecords();
			allRunFor(spec, lookup);
			final var response = lookup.getResponse().getTransactionGetRecord();
			assertEquals(numExpectedChildren, response.getChildTransactionRecordsCount());
			final var create2Record = response.getChildTransactionRecords(childOfInterest);
			final var create2Address =
					create2Record.getContractCreateResult().getEvmAddress().getValue();
			create2Addr.set(hex(create2Address.toByteArray()));
			final var createdId = create2Record.getReceipt().getContractID();
			mirrorAddr.set(hex(asSolidityAddress(createdId)));
			opLog.info("{} is @ {} (mirror {})",
					desc,
					create2Addr.get(),
					mirrorAddr.get());
		});
	}
}

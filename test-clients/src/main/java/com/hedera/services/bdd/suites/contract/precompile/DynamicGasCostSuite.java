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
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
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
import static com.hedera.services.bdd.spec.HapiPropertySource.accountIdFromHexedMirrorAddress;
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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocalWithFunctionAbi;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedContractBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.aaWith;
import static com.hedera.services.bdd.suites.contract.Utils.accountId;
import static com.hedera.services.bdd.suites.contract.Utils.aliasContractIdKey;
import static com.hedera.services.bdd.suites.contract.Utils.aliasDelegateContractKey;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
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
import static com.swirlds.common.utility.CommonUtils.hex;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DynamicGasCostSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(DynamicGasCostSuite.class);

	private static final String ACCOUNT = "anybody";
	private static final String SECOND_ACCOUNT = "anybody2";
	private static final String MULTI_KEY = "Multi key";
	private static final String FREEZE_KEY = "Freeze key";
	public static final String DEFAULT_GAS_COST = "10000";
	public static final String FULL_GAS_REFUND = "100";
	public static final String ZERO_GAS_COST = "0";
	public static final String HTS_DEFAULT_GAS_COST = "contracts.precompile.htsDefaultGasCost";
	public static final String MAX_REFUND_PERCENT_OF_GAS_LIMIT = "contracts.maxRefundPercentOfGasLimit";

	private static final String SAFE_OPERATIONS_CONTRACT = "SafeOperations";

	public static void main(String... args) {
		new DynamicGasCostSuite().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	public boolean canRunConcurrent() {
		return false;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
//				positiveSpecs()
				create2Specs()
		);
	}

	List<HapiApiSpec> create2Specs() {
		return List.of(new HapiApiSpec[] {
						create2FactoryWorksAsExpected(),
						canDeleteViaAlias(),
						cannotSelfDestructToMirrorAddress(),
						priorityAddressIsCreate2ForStaticHapiCalls(),
						canInternallyCallAliasedAddressesOnlyViaCreate2Address(),
						create2InputAddressIsStableWithTopLevelCallWhetherMirrorOrAliasIsUsed(),
						canUseAliasesInPrecompilesAndContractKeys(),
						inlineCreateCanFailSafely(),
						inlineCreate2CanFailSafely(),
						allLogOpcodesResolveExpectedContractId(),
						eip1014AliasIsPriorityInErcOwnerPrecompile(),
						canAssociateInConstructor(),
						childInheritanceOfAdminKeyAuthorizesParentAssociationInConstructor()
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
		final var contract = "OuterCreator";

		final AtomicLong outerCreatorNum = new AtomicLong();
		final var msg = new byte[]{(byte) 0xAB};
		final var noisyTxn = "noisyTxn";

		return defaultHapiSpec("AllLogOpcodesResolveExpectedContractId")
				.given(
						uploadInitCode(contract),
						contractCreate(contract)
								.payingWith(GENESIS)
								.via(creation)
								.exposingNumTo(outerCreatorNum::set)
				).when(
						contractCall(contract, "startChain", msg)
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
		final var contract = "RevertingCreateFactory";
		final var foo = 22;
		final var salt = 23;
		final var timesToFail = 7;
		final AtomicLong factoryEntityNum = new AtomicLong();
		final AtomicReference<String> factoryEvmAddress = new AtomicReference<>();
		final AtomicReference<byte[]> testContractInitcode = new AtomicReference<>();

		return defaultHapiSpec("InlineCreate2CanFailSafely")
				.given(
						overriding("contracts.throttle.throttleByGas", "false"),
						uploadInitCode(contract),
						contractCreate(contract)
								.payingWith(GENESIS)
								.via(creation)
								.exposingNumTo(num -> {
									factoryEntityNum.set(num);
									factoryEvmAddress.set(asHexedSolidityAddress(0, 0, num));
								})
				).when(
						sourcing(() -> contractCallLocal(
								contract,
								"getBytecode", factoryEvmAddress.get(), foo
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
												contract,
												"deploy", testContractInitcode.get(), salt
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
		final var contract = "RevertingCreateFactory";

		final var foo = 22;
		final var timesToFail = 7;
		final AtomicLong factoryEntityNum = new AtomicLong();
		final AtomicReference<String> factoryEvmAddress = new AtomicReference<>();
		final AtomicReference<byte[]> testContractInitcode = new AtomicReference<>();

		return defaultHapiSpec("InlineCreateCanFailSafely")
				.given(
						overriding("contracts.throttle.throttleByGas", "false"),
						uploadInitCode(contract),
						contractCreate(contract)
								.payingWith(GENESIS)
								.via(creation)
								.exposingNumTo(num -> {
									factoryEntityNum.set(num);
									factoryEvmAddress.set(asHexedSolidityAddress(0, 0, num));
								})
				).when(
						sourcing(() -> contractCallLocal(
								contract,
								"getBytecode", factoryEvmAddress.get(), foo
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
												contract,
												"deploy", testContractInitcode.get()
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

	private HapiApiSpec canAssociateInConstructor() {
		final var token = "token";
		final var contract = "SelfAssociating";
		final var creation = "creation";
		final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();

		return defaultHapiSpec("CanAssociateInConstructor")
				.given(
						uploadInitCode(contract),
						tokenCreate(token)
								.exposingCreatedIdTo(id -> tokenMirrorAddr.set(hex(asAddress(HapiPropertySource.asToken(id)))))
				).when(
						sourcing(() -> contractCreate(
								contract, tokenMirrorAddr.get()
						)
								.payingWith(GENESIS)
								.omitAdminKey()
								.gas(4_000_000)
								.via(creation))
				).then(
//						tokenDissociate(contract, token)
						getContractInfo(contract).logged()
				);
	}

	// https://github.com/hashgraph/hedera-services/issues/2867
	// https://github.com/hashgraph/hedera-services/issues/2868
	private HapiApiSpec create2FactoryWorksAsExpected() {
		final var tcValue = 1_234L;
		final var creation2 = "create2Txn";
		final var contract = "Create2Factory";
		final var testContract = "TestContract";
		final var salt = 42;
		final var adminKey = "adminKey";
		final var replAdminKey = "replAdminKey";
		final var entityMemo = "JUST DO IT";
		final var customAutoRenew = 7776001L;
		final var autoRenewAccountID = "autoRenewAccount";
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
						uploadInitCode(contract),
						cryptoCreate(autoRenewAccountID).balance(ONE_HUNDRED_HBARS),
						contractCreate(contract)
								.payingWith(GENESIS)
								.adminKey(adminKey)
								.entityMemo(entityMemo)
								.autoRenewSecs(customAutoRenew)
								.autoRenewAccountId(autoRenewAccountID)
								.via(creation2)
								.exposingNumTo(num -> factoryEvmAddress.set(asHexedSolidityAddress(0, 0, num))),
						getContractInfo(contract).has(contractWith().autoRenewAccountId(autoRenewAccountID)).logged()
				).when(
						sourcing(() -> contractCallLocal(
								contract,
								"getBytecode", factoryEvmAddress.get(), salt
						)
								.exposingTypedResultsTo(results -> {
									final var tcInitcode = (byte[]) results[0];
									testContractInitcode.set(tcInitcode);
									log.info("Contract reported TestContract initcode is {} bytes", tcInitcode.length);
								})
								.payingWith(GENESIS)
								.nodePayment(ONE_HBAR)),
						sourcing(() -> contractCallLocal(
								contract,
								"getAddress", testContractInitcode.get(), salt
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
								contract,
								"deploy", testContractInitcode.get(), salt
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
								contract,
								"wronglyDeployTwice", testContractInitcode.get(), salt
						)
								.payingWith(GENESIS)
								.gas(4_000_000L)
								.sending(tcValue)
								.hasKnownStatus(INVALID_SOLIDITY_ADDRESS)),
						sourcing(() -> getContractInfo(expectedCreate2Address.get())
								.hasCostAnswerPrecheck(INVALID_CONTRACT_ID)),
						sourcing(() -> contractCall(
								contract,
								"deploy", testContractInitcode.get(), salt
						)
								.payingWith(GENESIS)
								.gas(4_000_000L)
								.sending(tcValue)),
						sourcing(() -> contractDelete(expectedCreate2Address.get())
								.signedBy(DEFAULT_PAYER, adminKey)),
						logIt("Deleted the deployed CREATE2 contract using HAPI"),
						sourcing(() -> contractCall(
								contract,
								"deploy", testContractInitcode.get(), salt
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
							final var parentId = spec.registry().getContractId(contract);
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
								contract,
								"deploy", testContractInitcode.get(), salt
						)
								.payingWith(GENESIS)
								.gas(4_000_000L)
								/* Cannot repeat CREATE2 with same args without destroying the existing contract */
								.hasKnownStatus(INVALID_SOLIDITY_ADDRESS)),
						// https://github.com/hashgraph/hedera-services/issues/2874
						// autoRenewAccountID is inherited from the sender
						sourcing(() -> getContractInfo(expectedCreate2Address.get())
								.has(contractWith()
										.addressOrAlias(expectedCreate2Address.get())
										.autoRenewAccountId(autoRenewAccountID))
								.logged()),
						sourcing(() -> contractCallLocalWithFunctionAbi(
								expectedCreate2Address.get(),
								getABIFor(FUNCTION, "getBalance", testContract)
						)
								.payingWith(GENESIS)
								.has(resultWith().resultThruAbi(
										getABIFor(FUNCTION, "getBalance", testContract),
										isLiteralResult(new Object[]{BigInteger.valueOf(tcValue)})))),
						// autoRenewAccountID is inherited from the sender
						sourcing(() -> getContractInfo(expectedMirrorAddress.get())
								.has(contractWith()
										.adminKey(replAdminKey)
										.addressOrAlias(expectedCreate2Address.get())
										.autoRenewAccountId(autoRenewAccountID)
								)
								.logged()),
						sourcing(() -> contractCallWithFunctionAbi(expectedCreate2Address.get(),
								getABIFor(FUNCTION, "vacateAddress", testContract))
								.payingWith(GENESIS)),
						sourcing(() -> getContractInfo(expectedCreate2Address.get())
								.hasCostAnswerPrecheck(INVALID_CONTRACT_ID))
				);
	}

	private HapiApiSpec eip1014AliasIsPriorityInErcOwnerPrecompile() {
		final var creation2 = "create2Txn";
		final var ercContract = "ERC721Contract";
		final var pc2User = "Create2PrecompileUser";
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
						uploadInitCode(ercContract, pc2User),
						contractCreate(ercContract).omitAdminKey(),
						contractCreate(pc2User)
								.adminKey(multiKey)
								.payingWith(GENESIS),
						contractCall(pc2User, "createUser", salt)
								.payingWith(GENESIS)
								.gas(4_000_000L)
								.via(creation2),
						captureOneChildCreate2MetaFor(
								"Precompile user", creation2, userMirrorAddr, userAliasAddr),
						sourcing(() -> getAliasedContractBalance(userAliasAddr.get())
								.hasId(accountIdFromHexedMirrorAddress(userMirrorAddr.get()))),
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
								"ownerOf", nftAddress.get(), 1)
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

	private HapiApiSpec childInheritanceOfAdminKeyAuthorizesParentAssociationInConstructor() {
		final var initcode = "initcode";
		final var ft = "fungibleToken";
		final var multiKey = "swiss";
		final var creationAndAssociation = "creationAndAssociation";
		final var immediateChildAssoc = "ImmediateChildAssociation";

		final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
		final AtomicReference<String> childMirrorAddr = new AtomicReference<>();

		return defaultHapiSpec("childInheritanceOfAdminKeyAuthorizesParentAssociationInConstructor")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(ft).exposingCreatedIdTo(id ->
								tokenMirrorAddr.set(hex(asSolidityAddress(HapiPropertySource.asToken(id)))))
				).when(
						uploadInitCode(immediateChildAssoc),
						sourcing(() -> contractCreate(immediateChildAssoc, tokenMirrorAddr.get())
								.gas(2_000_000)
								.adminKey(multiKey)
								.payingWith(GENESIS)
								.exposingNumTo(n -> childMirrorAddr.set("0.0." + (n + 1)))
								.via(creationAndAssociation))
				).then(
						sourcing(() ->
								getContractInfo(childMirrorAddr.get())
										.logged())
				);
	}

	private HapiApiSpec canUseAliasesInPrecompilesAndContractKeys() {
		final var creation2 = "create2Txn";
		final var contract = "Create2PrecompileUser";
		final var userContract = "Create2User";
		final var ft = "fungibleToken";
		final var nft = "nonFungibleToken";
		final var multiKey = "swiss";
		final var ftFail = "ofInterest";
		final var nftFail = "alsoOfInterest";
		final var helperMintFail = "alsoOfExtremeInterest";
		final var helperMintSuccess = "quotidian";

		final AtomicReference<String> userAliasAddr = new AtomicReference<>();
		final AtomicReference<String> userMirrorAddr = new AtomicReference<>();
		final AtomicReference<String> userLiteralId = new AtomicReference<>();
		final AtomicReference<String> hexedNftType = new AtomicReference<>();

		final var salt = unhex("aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011");

		return defaultHapiSpec("CanUseAliasesInPrecompiles")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(TOKEN_TREASURY),
						uploadInitCode(contract),
						contractCreate(contract)
								.omitAdminKey()
								.payingWith(GENESIS),
						contractCall(contract, "createUser", salt)
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
						tokenUpdate(nft).supplyKey(() -> aliasContractIdKey(userAliasAddr.get()))
				).when(
						withOpContext((spec, opLog) -> {
							final var registry = spec.registry();
							final var ftType = registry.getTokenID(ft);
							final var nftType = registry.getTokenID(nft);

							final var ftAssoc = contractCall(
									contract, "associateBothTo", hex(asSolidityAddress(ftType))
							)
									.gas(4_000_000L);
							final var nftAssoc = contractCall(
									contract, "associateBothTo", hex(asSolidityAddress(nftType))
							)
									.gas(4_000_000L);

							final var fundingXfer = cryptoTransfer(
									moving(100, ft).between(TOKEN_TREASURY, contract),
									movingUnique(nft, 1L).between(TOKEN_TREASURY, contract)
							);

							// https://github.com/hashgraph/hedera-services/issues/2874 (alias in transfer precompile)
							final var sendFt = contractCall(
									contract, "sendFtToUser", hex(asSolidityAddress(ftType)), 100
							)
									.gas(4_000_000L);
							final var sendNft = contractCall(
									contract, "sendNftToUser", hex(asSolidityAddress(nftType)), 1
							)
									.via(ftFail)
									.gas(4_000_000L);
							final var failFtDissoc = contractCall(
									contract, "dissociateBothFrom", hex(asSolidityAddress(ftType))
							)
									.via(ftFail)
									.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
									.gas(4_000_000L);
							final var failNftDissoc = contractCall(
									contract, "dissociateBothFrom", hex(asSolidityAddress(nftType))
							)
									.via(nftFail)
									.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
									.gas(4_000_000L);
							// https://github.com/hashgraph/hedera-services/issues/2876 (mint via ContractID key)
							final var mint = contractCallWithFunctionAbi(
									userAliasAddr.get(),
									getABIFor(FUNCTION, "mintNft", userContract),
									hex(asSolidityAddress(nftType)),
									List.of("WoRtHlEsS")
							)
									.gas(4_000_000L);
							/* Can't succeed yet because supply key isn't delegatable */
							hexedNftType.set(hex(asSolidityAddress(nftType)));
							final var helperMint = contractCallWithFunctionAbi(
									userAliasAddr.get(),
									getABIFor(FUNCTION, "mintNftViaDelegate", userContract),
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
								recordWith()
										.status(SUCCESS),
								recordWith()
										.status(INVALID_SIGNATURE)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.MINT)
																.withTotalSupply(0)
																.withSerialNumbers()
																.withStatus(INVALID_SIGNATURE)))),
						childRecordsCheck(ftFail, CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(REVERTED_SUCCESS),
								recordWith()
										.status(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES)))),
						childRecordsCheck(nftFail, CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(REVERTED_SUCCESS),
								recordWith()
										.status(ACCOUNT_STILL_OWNS_NFTS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(ACCOUNT_STILL_OWNS_NFTS)))),
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(nft, 1),

						// https://github.com/hashgraph/hedera-services/issues/2876 (mint via delegatable_contract_id)
						tokenUpdate(nft).supplyKey(() -> aliasDelegateContractKey(userAliasAddr.get())),
						sourcing(() -> contractCallWithFunctionAbi(
								userAliasAddr.get(),
								getABIFor(FUNCTION, "mintNftViaDelegate", userContract),
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
									.addAccountAmounts(aaWith(tt, -666))
									.addAccountAmounts(aaWith(userMirrorAddr.get(), +666)));
							b.addTokenTransfers(TokenTransferList.newBuilder()
											.setToken(ftId)
											.addTransfers(aaWith(tt, -6))
											.addTransfers(aaWith(userMirrorAddr.get(), +6)))
									.addTokenTransfers(TokenTransferList.newBuilder()
											.setToken(nftId)
											.addNftTransfers(NftTransfer.newBuilder()
													.setSerialNumber(2L)
													.setSenderAccountID(tt)
													.setReceiverAccountID(accountId(userMirrorAddr.get()))));
						}).signedBy(DEFAULT_PAYER, TOKEN_TREASURY),
						sourcing(() -> getContractInfo(userLiteralId.get()).logged())
				);
	}

	// https://github.com/hashgraph/hedera-services/issues/2874
	// https://github.com/hashgraph/hedera-services/issues/2925
	private HapiApiSpec cannotSelfDestructToMirrorAddress() {
		final var creation2 = "create2Txn";
		final var messyCreation2 = "messyCreate2Txn";
		final var contract = "CreateDonor";
		final var donorContract = "Donor";

		final AtomicReference<String> donorAliasAddr = new AtomicReference<>();
		final AtomicReference<String> donorMirrorAddr = new AtomicReference<>();
		final AtomicReference<String> mDonorAliasAddr = new AtomicReference<>();
		final AtomicReference<String> mDonorMirrorAddr = new AtomicReference<>();

		final var salt = unhex("aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011");
		final var otherSalt = unhex("aabbccddee880011aabbccddee880011aabbccddee880011aabbccddee880011");

		return defaultHapiSpec("CannotSelfDestructToMirrorAddress")
				.given(
						uploadInitCode(contract),
						contractCreate(contract)
								.payingWith(GENESIS),
						contractCall(contract, "buildDonor", salt)
								.sending(1_000)
								.payingWith(GENESIS)
								.gas(4_000_000L)
								.via(creation2),
						captureOneChildCreate2MetaFor(
								"donor", creation2, donorMirrorAddr, donorAliasAddr)
				).when(
						sourcing(() -> contractCallWithFunctionAbi(
								donorAliasAddr.get(),
								getABIFor(FUNCTION, "relinquishFundsTo", donorContract),
								donorAliasAddr.get()).hasKnownStatus(OBTAINER_SAME_CONTRACT_ID)),
						sourcing(() -> contractCallWithFunctionAbi(
								donorAliasAddr.get(),
								getABIFor(FUNCTION, "relinquishFundsTo", donorContract),
								donorMirrorAddr.get()).hasKnownStatus(INVALID_SOLIDITY_ADDRESS))
				).then(
						contractCall(contract, "buildThenRevertThenBuild", otherSalt)
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
		final var deletion = "deletion";
		final var contract = "SaltingCreatorFactory";
		final var saltingCreator = "SaltingCreator";

		final AtomicReference<String> saltingCreatorAliasAddr = new AtomicReference<>();
		final AtomicReference<String> saltingCreatorMirrorAddr = new AtomicReference<>();
		final AtomicReference<String> saltingCreatorLiteralId = new AtomicReference<>();

		final var salt = unhex("aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011");
		final var otherSalt = unhex("aabbccddee330011aabbccddee330011aabbccddee330011aabbccddee330011");

		return defaultHapiSpec("CanDeleteViaAlias")
				.given(
						newKeyNamed(adminKey),
						uploadInitCode(contract),
						contractCreate(contract)
								.adminKey(adminKey)
								.payingWith(GENESIS),
						contractCall(contract, "buildCreator", salt)
								.payingWith(GENESIS)
								.gas(4_000_000L)
								.via(creation2),
						captureOneChildCreate2MetaFor(
								"Salting creator", creation2, saltingCreatorMirrorAddr, saltingCreatorAliasAddr),
						withOpContext((spec, opLog) ->
								saltingCreatorLiteralId.set(
										asContractString(
												contractIdFromHexedMirrorAddress(saltingCreatorMirrorAddr.get())))),
						// https://github.com/hashgraph/hedera-services/issues/2867 (can't re-create2 after selfdestruct)
						sourcing(() -> contractCallWithFunctionAbi(saltingCreatorAliasAddr.get(),
								getABIFor(FUNCTION, "createAndRecreateTest", saltingCreator), otherSalt)
								.payingWith(GENESIS)
								.gas(2_000_000L)
								.hasKnownStatus(CONTRACT_REVERT_EXECUTED))
				).when(
						sourcing(() -> contractUpdate(saltingCreatorAliasAddr.get())
								.signedBy(DEFAULT_PAYER, adminKey)
								.memo("That's why you always leave a note")),
						sourcing(() -> contractCallLocalWithFunctionAbi(
								saltingCreatorAliasAddr.get(), getABIFor(FUNCTION, "whatTheFoo", saltingCreator)
						).has(resultWith().resultThruAbi(
								getABIFor(FUNCTION, "whatTheFoo", saltingCreator), isLiteralResult(new Object[]{BigInteger.valueOf(42)})
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
		final var contract = "SaltingCreatorFactory";
		final var saltingCreator = "SaltingCreator";

		final AtomicReference<String> saltingCreatorAliasAddr = new AtomicReference<>();
		final AtomicReference<String> saltingCreatorMirrorAddr = new AtomicReference<>();
		final AtomicReference<String> tcAliasAddr1 = new AtomicReference<>();
		final AtomicReference<String> tcMirrorAddr1 = new AtomicReference<>();
		final AtomicReference<String> tcAliasAddr2 = new AtomicReference<>();
		final AtomicReference<String> tcMirrorAddr2 = new AtomicReference<>();

		final var salt = unhex("aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011");

		return defaultHapiSpec("Create2InputAddressIsStableWithTopLevelCallWhetherMirrorOrAliasIsUsed")
				.given(
						uploadInitCode(contract),
						contractCreate(contract)
								.payingWith(GENESIS),
						contractCall(contract, "buildCreator", salt)
								.payingWith(GENESIS)
								.gas(4_000_000L)
								.via(creation2),
						captureOneChildCreate2MetaFor(
								"Salting creator", creation2, saltingCreatorMirrorAddr, saltingCreatorAliasAddr)
				).when(
						sourcing(() -> contractCallWithFunctionAbi(saltingCreatorAliasAddr.get(),
								getABIFor(FUNCTION, "createSaltedTestContract", saltingCreator), salt)
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
						sourcing(() -> contractCallWithFunctionAbi(tcAliasAddr1.get(),
								getABIFor(FUNCTION, "vacateAddress", "TestContract"))
								.payingWith(GENESIS)),
						sourcing(() -> getContractInfo(tcMirrorAddr1.get())
								.has(contractWith().isDeleted()))
				).then(
						sourcing(() -> contractCall(
								contract, "callCreator", saltingCreatorAliasAddr.get(), salt
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
		final var contract = "AddressValueRet";
		final var returner = "Returner";
		final var creation2 = "create2Txn";
		final var placeholderCreation = "creation";

		final AtomicReference<String> aliasAddr = new AtomicReference<>();
		final AtomicReference<String> mirrorAddr = new AtomicReference<>();
		final AtomicReference<BigInteger> staticCallAliasAns = new AtomicReference<>();
		final AtomicReference<BigInteger> staticCallMirrorAns = new AtomicReference<>();

		final var salt = unhex("aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011");

		return defaultHapiSpec("PriorityAddressIsCreate2ForStaticHapiCalls")
				.given(
						uploadInitCode(contract),
						contractCreate(contract)
								.payingWith(GENESIS),
						contractCall(contract, "createReturner", salt)
								.payingWith(GENESIS)
								.gas(4_000_000L)
								.via(creation2),
						captureOneChildCreate2MetaFor(
								"Returner", creation2, mirrorAddr, aliasAddr)
				).when(
						sourcing(() -> contractCallLocalWithFunctionAbi(
								mirrorAddr.get(), getABIFor(FUNCTION, "returnThis", returner)
						)
								.payingWith(GENESIS)
								.exposingTypedResultsTo(results -> {
									log.info("Returner reported {} when called with mirror address", results);
									staticCallMirrorAns.set((BigInteger) results[0]);
								})),
						sourcing(() -> contractCallLocalWithFunctionAbi(
								aliasAddr.get(), getABIFor(FUNCTION, "returnThis", returner)
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
						sourcing(() -> contractCallWithFunctionAbi(
								aliasAddr.get(), getABIFor(FUNCTION, "createPlaceholder", returner)
						)
								.gas(4_000_000L)
								.payingWith(GENESIS)
								.via(placeholderCreation))
				);
	}

	private HapiApiSpec canInternallyCallAliasedAddressesOnlyViaCreate2Address() {
		final var creation2 = "create2Txn";
		final var contract = "AddressValueRet";
		final var aliasCall = "aliasCall";
		final var mirrorCall = "mirrorCall";

		final AtomicReference<String> aliasAddr = new AtomicReference<>();
		final AtomicReference<String> mirrorAddr = new AtomicReference<>();
		final AtomicReference<BigInteger> staticCallAliasAns = new AtomicReference<>();
		final AtomicReference<BigInteger> staticCallMirrorAns = new AtomicReference<>();

		final var salt = unhex("aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011");

		return defaultHapiSpec("CanInternallyCallAliasedAddressesOnlyViaCreate2Address")
				.given(
						uploadInitCode(contract),
						contractCreate(contract)
								.payingWith(GENESIS),
						contractCall(contract, "createReturner", salt)
								.payingWith(GENESIS)
								.gas(4_000_000L)
								.via(creation2),
						captureOneChildCreate2MetaFor(
								"Returner", creation2, mirrorAddr, aliasAddr)
				).when(
						sourcing(() -> contractCallLocal(
								contract, "callReturner", mirrorAddr.get()
						)
								.hasAnswerOnlyPrecheck(INVALID_SOLIDITY_ADDRESS)
								.payingWith(GENESIS)
								.exposingTypedResultsTo(results -> {
									log.info("Returner reported {} when called with mirror address", results);
									staticCallMirrorAns.set((BigInteger) results[0]);
								})),
						sourcing(() -> contractCallLocal(
								contract, "callReturner", aliasAddr.get()
						)
								.payingWith(GENESIS)
								.exposingTypedResultsTo(results -> {
									log.info("Returner reported {} when called with alias address", results);
									staticCallAliasAns.set((BigInteger) results[0]);
								})),
						sourcing(() -> contractCall(
								contract, "callReturner", aliasAddr.get()
						)
								.payingWith(GENESIS)
								.via(aliasCall)),
						sourcing(() -> contractCall(
								contract, "callReturner", mirrorAddr.get()
						)
								.hasKnownStatus(INVALID_SOLIDITY_ADDRESS)
								.payingWith(GENESIS)
								.via(mirrorCall))
				).then(
						withOpContext((spec, opLog) -> {
							final var mirrorLookup = getTxnRecord(mirrorCall);
							allRunFor(spec, mirrorLookup);
							final var mirrorResult =
									mirrorLookup.getResponseRecord().getContractCallResult().getContractCallResult();
							assertEquals(ByteString.EMPTY, mirrorResult,
									"Internal calls with mirror address should not be possible for aliased contracts");
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
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(HapiPropertySource.asToken(id))),
						uploadInitCode(SAFE_OPERATIONS_CONTRACT),
						contractCreate(SAFE_OPERATIONS_CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, ZERO_GAS_COST),
												UtilVerbs.overriding(MAX_REFUND_PERCENT_OF_GAS_LIMIT, FULL_GAS_REFUND),
												contractCall(SAFE_OPERATIONS_CONTRACT, "safeTokenMint",
														asAddress(vanillaTokenID.get()), amount,
														Collections.emptyList())
														.payingWith(ACCOUNT)
														.via("mintDynamicGasZeroCostTxn")
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.hasKnownStatus(SUCCESS),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, DEFAULT_GAS_COST),
												contractCall(SAFE_OPERATIONS_CONTRACT, "safeTokenMint",
														asAddress(vanillaTokenID.get()), amount,
														Collections.emptyList())
														.payingWith(ACCOUNT)
														.via("mintDynamicGasDefaultCostTxn")
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.hasKnownStatus(SUCCESS),
												UtilVerbs.resetToDefault(HTS_DEFAULT_GAS_COST,
														MAX_REFUND_PERCENT_OF_GAS_LIMIT)
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
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(HapiPropertySource.asToken(id))),
						mintToken(VANILLA_TOKEN, amount),
						uploadInitCode(SAFE_OPERATIONS_CONTRACT),
						contractCreate(SAFE_OPERATIONS_CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, ZERO_GAS_COST),
												UtilVerbs.overriding(MAX_REFUND_PERCENT_OF_GAS_LIMIT, FULL_GAS_REFUND),
												contractCall(SAFE_OPERATIONS_CONTRACT, "safeTokenBurn",
														asAddress(vanillaTokenID.get()), amount,
														Collections.emptyList())
														.payingWith(ACCOUNT)
														.via("burnDynamicGasZeroCostTxn")
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.hasKnownStatus(SUCCESS),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, DEFAULT_GAS_COST),
												mintToken(VANILLA_TOKEN, amount),
												contractCall(SAFE_OPERATIONS_CONTRACT, "safeTokenBurn",
														asAddress(vanillaTokenID.get()), amount,
														Collections.emptyList())
														.payingWith(ACCOUNT)
														.via("burnDynamicGasDefaultCostTxn")
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.hasKnownStatus(SUCCESS),
												UtilVerbs.resetToDefault(HTS_DEFAULT_GAS_COST,
														MAX_REFUND_PERCENT_OF_GAS_LIMIT)
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
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(HapiPropertySource.asToken(id))),
						uploadInitCode(SAFE_OPERATIONS_CONTRACT),
						contractCreate(SAFE_OPERATIONS_CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, ZERO_GAS_COST),
												UtilVerbs.overriding(MAX_REFUND_PERCENT_OF_GAS_LIMIT, FULL_GAS_REFUND),
												contractCall(SAFE_OPERATIONS_CONTRACT, "safeTokenAssociate",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(ACCOUNT)
														.via("associateDynamicGasZeroCostTxn")
														.hasKnownStatus(SUCCESS),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, DEFAULT_GAS_COST),
												tokenDissociate(ACCOUNT, VANILLA_TOKEN),
												contractCall(SAFE_OPERATIONS_CONTRACT, "safeTokenAssociate",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(ACCOUNT)
														.via("associateDynamicGasDefaultCostTxn")
														.hasKnownStatus(SUCCESS),
												UtilVerbs.resetToDefault(HTS_DEFAULT_GAS_COST,
														MAX_REFUND_PERCENT_OF_GAS_LIMIT)
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
						cryptoCreate(TOKEN_TREASURY).exposingCreatedIdTo(treasuryID::set),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(10L)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(HapiPropertySource.asToken(id))),
						uploadInitCode(SAFE_OPERATIONS_CONTRACT),
						contractCreate(SAFE_OPERATIONS_CONTRACT).gas(100_000)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												tokenAssociate(ACCOUNT, VANILLA_TOKEN),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, ZERO_GAS_COST),
												UtilVerbs.overriding(MAX_REFUND_PERCENT_OF_GAS_LIMIT, FULL_GAS_REFUND),
												contractCall(SAFE_OPERATIONS_CONTRACT, "safeTokenDissociate",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(ACCOUNT)
														.via("dissociateZeroCostTxn")
														.hasKnownStatus(SUCCESS),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, DEFAULT_GAS_COST),
												tokenAssociate(ACCOUNT, VANILLA_TOKEN),
												contractCall(SAFE_OPERATIONS_CONTRACT, "safeTokenDissociate",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(ACCOUNT)
														.via("dissociateDefaultCostTxn")
														.hasKnownStatus(SUCCESS),
												UtilVerbs.resetToDefault(HTS_DEFAULT_GAS_COST,
														MAX_REFUND_PERCENT_OF_GAS_LIMIT)
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
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(10L)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(HapiPropertySource.asToken(id))),
						tokenCreate(KNOWABLE_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(10L)
								.exposingCreatedIdTo(id -> knowableTokenID.set(HapiPropertySource.asToken(id))),
						uploadInitCode(SAFE_OPERATIONS_CONTRACT),
						contractCreate(SAFE_OPERATIONS_CONTRACT).gas(100_000)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, ZERO_GAS_COST),
												UtilVerbs.overriding(MAX_REFUND_PERCENT_OF_GAS_LIMIT, FULL_GAS_REFUND),
												contractCall(SAFE_OPERATIONS_CONTRACT, "safeTokensAssociate",
														asAddress(accountID.get()),
														List.of(
																asAddress(vanillaTokenID.get()),
																asAddress(knowableTokenID.get())))
														.payingWith(ACCOUNT)
														.via("multipleAssociateZeroCostTxn")
														.hasKnownStatus(SUCCESS),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, DEFAULT_GAS_COST),
												tokenDissociate(ACCOUNT, VANILLA_TOKEN, KNOWABLE_TOKEN),
												contractCall(SAFE_OPERATIONS_CONTRACT, "safeTokensAssociate",
														asAddress(accountID.get()),
														List.of(
																asAddress(vanillaTokenID.get()),
																asAddress(knowableTokenID.get())))
														.payingWith(ACCOUNT)
														.via("multipleAssociateDefaultCostTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												UtilVerbs.resetToDefault(HTS_DEFAULT_GAS_COST,
														MAX_REFUND_PERCENT_OF_GAS_LIMIT)
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
						cryptoCreate(TOKEN_TREASURY)
								.balance(0L)
								.exposingCreatedIdTo(treasuryID::set),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(10)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(HapiPropertySource.asToken(id))),
						tokenCreate(KNOWABLE_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(10)
								.exposingCreatedIdTo(id -> knowableTokenTokenID.set(HapiPropertySource.asToken(id))),
						uploadInitCode(SAFE_OPERATIONS_CONTRACT),
						contractCreate(SAFE_OPERATIONS_CONTRACT).gas(100_000)
				)
				.when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												tokenAssociate(ACCOUNT, List.of(VANILLA_TOKEN, KNOWABLE_TOKEN)),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, ZERO_GAS_COST),
												UtilVerbs.overriding(MAX_REFUND_PERCENT_OF_GAS_LIMIT, FULL_GAS_REFUND),
												contractCall(SAFE_OPERATIONS_CONTRACT, "safeTokensDissociate",
														asAddress(accountID.get()), List.of(
																asAddress(vanillaTokenID.get()),
																asAddress(knowableTokenTokenID.get())))
														.payingWith(ACCOUNT)
														.via("multipleDissociateZeroCostTxn")
														.hasKnownStatus(SUCCESS),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, DEFAULT_GAS_COST),
												tokenAssociate(ACCOUNT, List.of(VANILLA_TOKEN, KNOWABLE_TOKEN)),
												contractCall(SAFE_OPERATIONS_CONTRACT, "safeTokensDissociate",
														asAddress(accountID.get()), List.of(
																asAddress(vanillaTokenID.get()),
																asAddress(knowableTokenTokenID.get())))
														.payingWith(ACCOUNT)
														.via("multipleDissociateDefaultCostTxn")
														.hasKnownStatus(SUCCESS),
												UtilVerbs.resetToDefault(HTS_DEFAULT_GAS_COST,
														MAX_REFUND_PERCENT_OF_GAS_LIMIT)
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
						cryptoCreate(TOKEN_TREASURY)
								.balance(0L)
								.exposingCreatedIdTo(treasuryID::set),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.initialSupply(0)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(HapiPropertySource.asToken(id))),
						mintToken(VANILLA_TOKEN, List.of(metadata("firstMemo"), metadata("secondMemo"))),
						tokenAssociate(ACCOUNT, VANILLA_TOKEN),
						cryptoUpdate(TOKEN_TREASURY).key(MULTI_KEY),
						uploadInitCode(SAFE_OPERATIONS_CONTRACT),
						contractCreate(SAFE_OPERATIONS_CONTRACT).gas(100_000)
				)
				.when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, ZERO_GAS_COST),
												UtilVerbs.overriding(MAX_REFUND_PERCENT_OF_GAS_LIMIT, FULL_GAS_REFUND),
												contractCall(SAFE_OPERATIONS_CONTRACT, "safeNFTTransfer",
														asAddress(vanillaTokenID.get()),
														asAddress(treasuryID.get()),
														asAddress(accountID.get()),
														1L)
														.payingWith(ACCOUNT)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via("nftTransferZeroCostTxn")
														.hasKnownStatus(SUCCESS),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, DEFAULT_GAS_COST),
												contractCall(SAFE_OPERATIONS_CONTRACT, "safeNFTTransfer",
														asAddress(vanillaTokenID.get()),
														asAddress(treasuryID.get()),
														asAddress(accountID.get()),
														2L)
														.payingWith(ACCOUNT)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via("nftTransferDefaultCostTxn")
														.hasKnownStatus(SUCCESS),
												UtilVerbs.resetToDefault(HTS_DEFAULT_GAS_COST,
														MAX_REFUND_PERCENT_OF_GAS_LIMIT)
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
						cryptoCreate(TOKEN_TREASURY)
								.balance(0L)
								.exposingCreatedIdTo(treasuryID::set),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.initialSupply(0)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(HapiPropertySource.asToken(id))),
						mintToken(VANILLA_TOKEN, 10),
						tokenAssociate(ACCOUNT, VANILLA_TOKEN),
						cryptoUpdate(TOKEN_TREASURY).key(MULTI_KEY),
						uploadInitCode(SAFE_OPERATIONS_CONTRACT),
						contractCreate(SAFE_OPERATIONS_CONTRACT).gas(100_000)
				)
				.when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, ZERO_GAS_COST),
												UtilVerbs.overriding(MAX_REFUND_PERCENT_OF_GAS_LIMIT, FULL_GAS_REFUND),
												contractCall(SAFE_OPERATIONS_CONTRACT, "safeTokenTransfer",
														asAddress(vanillaTokenID.get()),
														asAddress(treasuryID.get()),
														asAddress(accountID.get()),
														5L)
														.payingWith(ACCOUNT)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via("tokenTransferZeroCostTxn")
														.hasKnownStatus(SUCCESS),
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, DEFAULT_GAS_COST),
												contractCall(SAFE_OPERATIONS_CONTRACT, "safeTokenTransfer",
														asAddress(vanillaTokenID.get()),
														asAddress(treasuryID.get()),
														asAddress(accountID.get()),
														5L)
														.payingWith(ACCOUNT)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via("tokenTransferDefaultCostTxn")
														.hasKnownStatus(SUCCESS),
												UtilVerbs.resetToDefault(HTS_DEFAULT_GAS_COST,
														MAX_REFUND_PERCENT_OF_GAS_LIMIT)
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
						cryptoCreate(TOKEN_TREASURY)
								.balance(0L)
								.exposingCreatedIdTo(treasuryID::set),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.initialSupply(0)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(HapiPropertySource.asToken(id))),
						mintToken(VANILLA_TOKEN, 20),
						tokenAssociate(ACCOUNT, VANILLA_TOKEN),
						tokenAssociate(SECOND_ACCOUNT, VANILLA_TOKEN),
						cryptoUpdate(TOKEN_TREASURY).key(MULTI_KEY),
						cryptoUpdate(SECOND_ACCOUNT).key(MULTI_KEY),
						uploadInitCode(SAFE_OPERATIONS_CONTRACT),
						contractCreate(SAFE_OPERATIONS_CONTRACT).gas(100_000)
				)
				.when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, ZERO_GAS_COST),
												UtilVerbs.overriding(MAX_REFUND_PERCENT_OF_GAS_LIMIT, FULL_GAS_REFUND),
												contractCall(SAFE_OPERATIONS_CONTRACT, "safeTokensTransfer",
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
												contractCall(SAFE_OPERATIONS_CONTRACT, "safeTokensTransfer",
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
												UtilVerbs.resetToDefault(HTS_DEFAULT_GAS_COST,
														MAX_REFUND_PERCENT_OF_GAS_LIMIT)
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
						cryptoCreate(TOKEN_TREASURY)
								.balance(0L)
								.exposingCreatedIdTo(treasuryID::set),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.initialSupply(0)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(HapiPropertySource.asToken(id))),
						mintToken(VANILLA_TOKEN,
								List.of(metadata("firstMemo"),
										metadata("secondMemo"),
										metadata("thirdMemo"),
										metadata("fourthMemo")
								)),
						tokenAssociate(ACCOUNT, VANILLA_TOKEN),
						cryptoUpdate(TOKEN_TREASURY).key(MULTI_KEY),
						uploadInitCode(SAFE_OPERATIONS_CONTRACT),
						contractCreate(SAFE_OPERATIONS_CONTRACT).gas(100_000)
				)
				.when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												UtilVerbs.overriding(HTS_DEFAULT_GAS_COST, ZERO_GAS_COST),
												UtilVerbs.overriding(MAX_REFUND_PERCENT_OF_GAS_LIMIT, FULL_GAS_REFUND),
												contractCall(SAFE_OPERATIONS_CONTRACT, "safeNFTsTransfer",
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
												contractCall(SAFE_OPERATIONS_CONTRACT, "safeNFTsTransfer",
														asAddress(vanillaTokenID.get()),
														List.of(asAddress(treasuryID.get())),
														List.of(asAddress(accountID.get()),
																asAddress(secondAccountID.get())),
														List.of(3L, 4L))
														.payingWith(ACCOUNT)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via("nftsTransferDefaultCostTxn")
														.hasKnownStatus(SUCCESS),
												UtilVerbs.resetToDefault(HTS_DEFAULT_GAS_COST,
														MAX_REFUND_PERCENT_OF_GAS_LIMIT)
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

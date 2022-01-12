package com.hedera.services.bdd.suites.contract.opcodes;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.google.common.primitives.Longs;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.junit.jupiter.api.Assertions;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.contractListWithPropertiesInheritedFrom;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.eventSignatureOf;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;

public class CreateOperationSuite extends HapiApiSuite {

	private static final Logger log = LogManager.getLogger(CreateOperationSuite.class);

	public static void main(String... args) {
		new CreateOperationSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				simpleFactoryWorks(),
				stackedFactoryWorks(),
				resetOnFactoryFailureWorks(),
				resetOnFactoryFailureAfterDeploymentWorks(),
				resetOnStackedFactoryFailureWorks(),
				inheritanceOfNestedCreatedContracts(),
//				factoryAndSelfDestructInConstructorContract(),
//				factoryQuickSelfDestructContract(),
				contractCreateWithNewOpInConstructor(),
				childContractStorageWorks()
		);
	}

	private HapiApiSpec factoryAndSelfDestructInConstructorContract() {
		final var CONTRACT = "contract";

		return defaultHapiSpec("FactoryAndSelfDestructInConstructorContract")
				.given(
						fileCreate("bytecode")
								.path(ContractResources.FACTORY_SELF_DESTRUCT_CONSTRUCTOR_CONTRACT),
						contractCreate(CONTRACT)
								.bytecode("bytecode")
								.gas(4_000_000)
								.balance(10)
				)
				.when(
						contractCall(CONTRACT)
								.hasKnownStatus(CONTRACT_DELETED)
				)
				.then(
						getContractBytecode(CONTRACT)
								.hasCostAnswerPrecheck(CONTRACT_DELETED)
				);
	}

	private HapiApiSpec factoryQuickSelfDestructContract() {
		final var CONTRACT = "contract";

		return defaultHapiSpec("FactoryQuickSelfDestructContract")
				.given(
						fileCreate("bytecode")
								.path(ContractResources.FACTORY_QUICK_SELF_DESTRUCT_CONTRACT),
						contractCreate(CONTRACT)
								.bytecode("bytecode"))
				.when(
						contractCall(CONTRACT, ContractResources.FACTORY_QUICK_SELF_DESTRUCT_CREATE_AND_DELETE_ABI)
								.gas(4_000_000)
								.via("callRecord"))
				.then(
						getTxnRecord("callRecord").hasPriority(
								recordWith().contractCallResult(
										resultWith().logs(
												inOrder(logWith().withTopicsInOrder(
																List.of(eventSignatureOf("ChildCreated(address)"))),
														logWith().withTopicsInOrder(List.of(
																eventSignatureOf("ChildDeleted()"))))))));
	}

	private HapiApiSpec inheritanceOfNestedCreatedContracts() {
		final var CONTRACT = "inheritanceOfNestedCreatedContracts";
		return defaultHapiSpec("InheritanceOfNestedCreatedContracts")
				.given(
						fileCreate("bytecode").path(ContractResources.NESTED_CHILDREN_CONTRACT),
						contractCreate(CONTRACT).bytecode("bytecode")
								.logged()
								.via("createRecord"),
						getContractInfo(CONTRACT).logged().saveToRegistry("parentInfo")
				)
				.when(
						contractCall(CONTRACT, ContractResources.NESTED_CHILDREN_CALL_CREATE_ABI)
								.gas(780_000)
								.via("callRecord")
				)
				.then(
						getTxnRecord("createRecord")
								.saveCreatedContractListToRegistry("ctorChild"),
						getTxnRecord("callRecord")
								.saveCreatedContractListToRegistry("callChild"),
						contractListWithPropertiesInheritedFrom("callChildCallResult", 2, "parentInfo"),
						contractListWithPropertiesInheritedFrom("ctorChildCreateResult", 3, "parentInfo")
				);
	}

	HapiApiSpec simpleFactoryWorks() {
		return defaultHapiSpec("ContractFactoryWorksHappyPath")
				.given(
						fileCreate("factory").path(ContractResources.FACTORY_CONTRACT),
						contractCreate("factoryContract").bytecode("factory")
				).when(
						contractCall("factoryContract", ContractResources.FACTORY_CONTRACT_SUCCESS)
								.gas(780_000)
								.via("deploymentSuccessTxn")
				).then(
						withOpContext((spec, opLog) -> {
							final var successTxn = getTxnRecord("deploymentSuccessTxn");
							final var parentContract = getContractInfo("factoryContract").saveToRegistry(
									"contractInfo");
							allRunFor(spec, successTxn, parentContract);

							final var parentID = spec.registry().getContractInfo("contractInfo")
									.getContractID();
							List<ContractID> createdContractIDs =
									successTxn.getResponseRecord().getContractCallResult().getCreatedContractIDsList();

							Assertions.assertEquals(createdContractIDs.size(), 1);
							Assertions.assertEquals(parentID.getContractNum(),
									createdContractIDs.get(0).getContractNum() - 1);
						})
				);
	}

	HapiApiSpec stackedFactoryWorks() {
		return defaultHapiSpec("StackedFactoryWorks")
				.given(
						fileCreate("factory").path(ContractResources.FACTORY_CONTRACT),
						contractCreate("factoryContract").bytecode("factory").gas(200_000)
				).when(
						contractCall("factoryContract", ContractResources.FACTORY_CONTRACT_STACKED_DEPLOYMENT_SUCCESS)
								.gas(1_000_000)
								.via("stackedDeploymentSuccessTxn")
				).then(
						withOpContext((spec, opLog) -> {
							final var successTxn = getTxnRecord("stackedDeploymentSuccessTxn");
							final var parentContract = getContractInfo("factoryContract").saveToRegistry(
									"contractInfo");
							allRunFor(spec, successTxn, parentContract);

							final var parentID = spec.registry().getContractInfo("contractInfo")
									.getContractID();
							List<ContractID> createdContractIDs =
									successTxn.getResponseRecord().getContractCallResult().getCreatedContractIDsList();

							Assertions.assertEquals(createdContractIDs.size(), 2);
							Assertions.assertEquals(parentID.getContractNum(),
									createdContractIDs.get(0).getContractNum() - 1);
							Assertions.assertEquals(parentID.getContractNum(),
									createdContractIDs.get(1).getContractNum() - 2);
						})
				);
	}

	HapiApiSpec resetOnFactoryFailureWorks() {
		return defaultHapiSpec("ResetOnFactoryFailureWorks")
				.given(
						fileCreate("factory").path(ContractResources.FACTORY_CONTRACT),
						contractCreate("factoryContract").bytecode("factory").gas(200_000)
				).when(
						contractCall("factoryContract", ContractResources.FACTORY_CONTRACT_FAILURE)
								.hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
								.gas(780_000)
								.via("deploymentFailureTxn"),
						contractCall("factoryContract", ContractResources.FACTORY_CONTRACT_SUCCESS)
								.gas(780_000)
								.via("deploymentSuccessTxn")
				).then(
						withOpContext((spec, opLog) -> {
							final var revertTxn = getTxnRecord("deploymentFailureTxn");
							final var deploymentSuccessTxn = getTxnRecord("deploymentSuccessTxn");
							final var parentContract = getContractInfo("factoryContract").saveToRegistry(
									"contractInfo");
							allRunFor(spec, revertTxn, parentContract, deploymentSuccessTxn);

							final var parentID = spec.registry().getContractInfo("contractInfo")
									.getContractID();
							List<ContractID> createdContracts =
									deploymentSuccessTxn.getResponseRecord().getContractCallResult().getCreatedContractIDsList();

							Assertions.assertTrue(revertTxn.getResponseRecord().getContractCallResult()
									.getCreatedContractIDsList().isEmpty());
							Assertions.assertEquals(createdContracts.size(), 1);
							Assertions.assertEquals(parentID.getContractNum(),
									createdContracts.get(0).getContractNum() - 1);
						})
				);
	}

	HapiApiSpec resetOnFactoryFailureAfterDeploymentWorks() {
		return defaultHapiSpec("ResetOnFactoryFailureAfterDeploymentWorks")
				.given(
						fileCreate("factory").path(ContractResources.FACTORY_CONTRACT),
						contractCreate("factoryContract").bytecode("factory").gas(200_000)
				).when(
						contractCall("factoryContract", ContractResources.FACTORY_CONTRACT_FAILURE_AFTER_DEPLOY)
								.hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
								.gas(780_000)
								.via("failureAfterDeploymentTxn"),
						contractCall("factoryContract", ContractResources.FACTORY_CONTRACT_SUCCESS)
								.gas(780_000)
								.via("deploymentSuccessTxn")
				).then(
						withOpContext((spec, opLog) -> {
							final var revertTxn = getTxnRecord("failureAfterDeploymentTxn");
							final var deploymentSuccessTxn = getTxnRecord("deploymentSuccessTxn");
							final var parentContract = getContractInfo("factoryContract").saveToRegistry(
									"contractInfo");
							allRunFor(spec, revertTxn, parentContract, deploymentSuccessTxn);

							final var parentID = spec.registry().getContractInfo("contractInfo")
									.getContractID();
							List<ContractID> createdContracts =
									deploymentSuccessTxn.getResponseRecord().getContractCallResult().getCreatedContractIDsList();

							Assertions.assertTrue(revertTxn.getResponseRecord().getContractCallResult()
									.getCreatedContractIDsList().isEmpty());
							Assertions.assertEquals(createdContracts.size(), 1);
							Assertions.assertEquals(parentID.getContractNum(),
									createdContracts.get(0).getContractNum() - 1);
						})
				);
	}

	HapiApiSpec resetOnStackedFactoryFailureWorks() {
		return defaultHapiSpec("ResetOnStackedFactoryFailureWorks")
				.given(
						fileCreate("factory").path(ContractResources.FACTORY_CONTRACT),
						contractCreate("factoryContract").bytecode("factory").gas(200_000)
				).when(
						contractCall("factoryContract", ContractResources.FACTORY_CONTRACT_STACKED_DEPLOYMENT_FAILURE)
								.hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
								.gas(780_000)
								.via("stackedDeploymentFailureTxn"),
						contractCall("factoryContract", ContractResources.FACTORY_CONTRACT_SUCCESS)
								.gas(780_000)
								.via("deploymentSuccessTxn")
				).then(
						withOpContext((spec, opLog) -> {
							final var revertTxn = getTxnRecord("stackedDeploymentFailureTxn");
							final var deploymentSuccessTxn = getTxnRecord("deploymentSuccessTxn");
							final var parentContract = getContractInfo("factoryContract").saveToRegistry(
									"contractInfo");
							allRunFor(spec, revertTxn, parentContract, deploymentSuccessTxn);

							final var parentID = spec.registry().getContractInfo("contractInfo")
									.getContractID();
							List<ContractID> createdContracts =
									deploymentSuccessTxn.getResponseRecord().getContractCallResult().getCreatedContractIDsList();

							Assertions.assertTrue(revertTxn.getResponseRecord().getContractCallResult()
									.getCreatedContractIDsList().isEmpty());
							Assertions.assertEquals(createdContracts.size(), 1);
							Assertions.assertEquals(parentID.getContractNum(),
									createdContracts.get(0).getContractNum() - 1);
						})
				);
	}

	private HapiApiSpec contractCreateWithNewOpInConstructor() {
		return defaultHapiSpec("ContractCreateWithNewOpInConstructorAbandoningParent")
				.given(
						fileCreate("AbandoningParentBytecode").path(ContractResources.ABANDONING_PARENT_BYTECODE_PATH)
				).when().then(
						contractCreate("AbandoningParent").bytecode("AbandoningParentBytecode")
								.gas(4_000_000).via("AbandoningParentTxn"),
						getContractInfo("AbandoningParent").saveToRegistry("AbandoningParentParentInfo").logged(),
						getTxnRecord("AbandoningParentTxn")
								.saveCreatedContractListToRegistry("AbandoningParent")
								.logged(),
						UtilVerbs.contractListWithPropertiesInheritedFrom("AbandoningParentCreateResult", 6, "AbandoningParentParentInfo")
				);
	}

	HapiApiSpec childContractStorageWorks() {
		final var CREATED_TRIVIAL_CONTRACT_RETURNS = 7;

		return defaultHapiSpec("childContractStorageWorks")
				.given(
						fileCreate("createTrivialBytecode")
								.path(ContractResources.DELEGATING_CONTRACT_BYTECODE_PATH)
				).when(
						contractCreate("firstContract")
								.bytecode("createTrivialBytecode")
								.gas(200_000)
								.via("firstContractTxn")
				).then(
						assertionsHold((spec, ctxLog) -> {
							var subop1 = contractCall("firstContract", ContractResources.CREATE_CHILD_ABI)
									.gas(785_000)
									.via("createContractTxn");

							// First contract calls created contract and get an integer return value
							var subop2 = contractCallLocal("firstContract", ContractResources.GET_CHILD_RESULT_ABI)
									.saveResultTo("contractCallContractResultBytes");
							CustomSpecAssert.allRunFor(spec, subop1, subop2);

							byte[] resultBytes = spec.registry().getBytes("contractCallContractResultBytes");
							CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(
									ContractResources.GET_CHILD_RESULT_ABI);

							int contractCallReturnVal = 0;
							if (resultBytes != null && resultBytes.length > 0) {
								Object[] retResults = function.decodeResult(resultBytes);
								if (retResults != null && retResults.length > 0) {
									BigInteger retBi = (BigInteger) retResults[0];
									contractCallReturnVal = retBi.intValue();
								}
							}

							ctxLog.info("This contract call contract return value {}", contractCallReturnVal);
							Assertions.assertEquals(CREATED_TRIVIAL_CONTRACT_RETURNS, contractCallReturnVal,
									"This contract call contract return value should be 7");


							// Get created contract's info with call to first contract
							var subop3 = contractCallLocal("firstContract", ContractResources.GET_CHILD_ADDRESS_ABI)
									.saveResultTo("getCreatedContractInfoResultBytes");
							CustomSpecAssert.allRunFor(spec, subop3);

							resultBytes = spec.registry().getBytes("getCreatedContractInfoResultBytes");

							function = CallTransaction.Function.fromJsonInterface(
									ContractResources.GET_CHILD_ADDRESS_ABI);

							Object[] retResults = function.decodeResult(resultBytes);
							String contractIDString = null;
							if (retResults != null && retResults.length > 0) {
								byte[] retVal = (byte[]) retResults[0];

								long realm = Longs.fromByteArray(Arrays.copyOfRange(retVal, 4, 12));
								long accountNum = Longs.fromByteArray(Arrays.copyOfRange(retVal, 12, 20));
								contractIDString = String.format("%d.%d.%d", realm, 0, accountNum);
							}
							ctxLog.info("The created contract ID {}", contractIDString);
							Assertions.assertNotEquals(
									ContractID.newBuilder().getDefaultInstanceForType(),
									TxnUtils.asContractId(contractIDString, spec),
									"Created contract doesn't have valid Contract ID");


							var subop4 = getContractInfo(contractIDString)
									.saveToRegistry("createdContractInfoSaved");

							CustomSpecAssert.allRunFor(spec, subop4);

							ContractGetInfoResponse.ContractInfo createdContratInfo = spec.registry().getContractInfo(
									"createdContractInfoSaved");

							Assertions.assertTrue(createdContratInfo.hasContractID());
							Assertions.assertTrue(createdContratInfo.hasAccountID());
							Assertions.assertTrue(createdContratInfo.hasExpirationTime());
						})
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

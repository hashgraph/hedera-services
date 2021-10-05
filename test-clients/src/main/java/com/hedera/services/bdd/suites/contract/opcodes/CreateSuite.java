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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.contractListWithPropertiesInheritedFrom;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;

public class CreateSuite extends HapiApiSuite {

	private static final Logger log = LogManager.getLogger(CreateSuite.class);

	public static void main(String... args) {
		new CreateSuite().runSuiteSync();
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
				factoryAndSelfDestructInConstructorContract()
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
						contractCreate("factoryContract").bytecode("factory")
				).when(
						contractCall("factoryContract", ContractResources.FACTORY_CONTRACT_STACKED_DEPLOYMENT_SUCCESS)
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
						contractCreate("factoryContract").bytecode("factory")
				).when(
						contractCall("factoryContract", ContractResources.FACTORY_CONTRACT_FAILURE)
								.hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
								.via("deploymentFailureTxn"),
						contractCall("factoryContract", ContractResources.FACTORY_CONTRACT_SUCCESS)
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
						contractCreate("factoryContract").bytecode("factory")
				).when(
						contractCall("factoryContract", ContractResources.FACTORY_CONTRACT_FAILURE_AFTER_DEPLOY)
								.hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
								.via("failureAfterDeploymentTxn"),
						contractCall("factoryContract", ContractResources.FACTORY_CONTRACT_SUCCESS)
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
						contractCreate("factoryContract").bytecode("factory")
				).when(
						contractCall("factoryContract", ContractResources.FACTORY_CONTRACT_STACKED_DEPLOYMENT_FAILURE)
								.hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
								.via("stackedDeploymentFailureTxn"),
						contractCall("factoryContract", ContractResources.FACTORY_CONTRACT_SUCCESS)
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

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

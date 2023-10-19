/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
 */

package com.hedera.services.bdd.suites.contract.opcodes;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.contractListWithPropertiesInheritedFrom;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.eventSignatureOf;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.math.BigInteger;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

@HapiTestSuite
public class CreateOperationSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(CreateOperationSuite.class);
    private static final String CONTRACT = "FactoryContract";
    private static final String CALL_RECORD_TRANSACTION_NAME = "callRecord";
    private static final String DEPLOYMENT_SUCCESS_FUNCTION = "deploymentSuccess";
    private static final String DEPLOYMENT_SUCCESS_TXN = "deploymentSuccessTxn";
    private static final String CONTRACT_INFO = "contractInfo";
    private static final String PARENT_INFO = "parentInfo";

    public static void main(final String... args) {
        new CreateOperationSuite().runSuiteAsync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                simpleFactoryWorks(),
                stackedFactoryWorks(),
                resetOnFactoryFailureWorks(),
                resetOnFactoryFailureAfterDeploymentWorks(),
                resetOnStackedFactoryFailureWorks(),
                inheritanceOfNestedCreatedContracts(),
                factoryAndSelfDestructInConstructorContract(),
                factoryQuickSelfDestructContract(),
                contractCreateWithNewOpInConstructorAbandoningParent(),
                childContractStorageWorks());
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @HapiTest
    private HapiSpec factoryAndSelfDestructInConstructorContract() {
        final var contract = "FactorySelfDestructConstructor";

        final var sender = "sender";
        return defaultHapiSpec("FactoryAndSelfDestructInConstructorContract")
                .given(
                        uploadInitCode(contract),
                        cryptoCreate(sender).balance(ONE_HUNDRED_HBARS),
                        contractCreate(contract).balance(10).payingWith(sender))
                .when(contractCall(contract).hasKnownStatus(CONTRACT_DELETED).payingWith(sender))
                .then(getContractBytecode(contract).hasCostAnswerPrecheck(CONTRACT_DELETED));
    }

    @HapiTest
    private HapiSpec factoryQuickSelfDestructContract() {
        final var contract = "FactoryQuickSelfDestruct";
        final var sender = "sender";
        return defaultHapiSpec("FactoryQuickSelfDestructContract")
                .given(
                        uploadInitCode(contract),
                        contractCreate(contract),
                        cryptoCreate(sender).balance(ONE_HUNDRED_HBARS))
                .when(contractCall(contract, "createAndDeleteChild")
                        .gas(4_000_000)
                        .via(CALL_RECORD_TRANSACTION_NAME)
                        .payingWith(sender))
                .then(getTxnRecord(CALL_RECORD_TRANSACTION_NAME)
                        .hasPriority(recordWith()
                                .contractCallResult(resultWith()
                                        .logs(inOrder(
                                                logWith()
                                                        .withTopicsInOrder(
                                                                List.of(eventSignatureOf("ChildCreated(address)"))),
                                                logWith()
                                                        .withTopicsInOrder(
                                                                List.of(eventSignatureOf("ChildDeleted()"))))))));
    }

    @HapiTest
    private HapiSpec inheritanceOfNestedCreatedContracts() {
        final var contract = "NestedChildren";
        return defaultHapiSpec("InheritanceOfNestedCreatedContracts")
                .given(
                        uploadInitCode(contract),
                        contractCreate(contract).logged().via("createRecord"),
                        getContractInfo(contract).logged().saveToRegistry(PARENT_INFO))
                .when(contractCall(contract, "callCreate").gas(780_000).via(CALL_RECORD_TRANSACTION_NAME))
                .then(
                        getTxnRecord("createRecord").saveCreatedContractListToRegistry("ctorChild"),
                        getTxnRecord(CALL_RECORD_TRANSACTION_NAME).saveCreatedContractListToRegistry("callChild"),
                        contractListWithPropertiesInheritedFrom("callChildCallResult", 2, PARENT_INFO),
                        contractListWithPropertiesInheritedFrom("ctorChildCreateResult", 3, PARENT_INFO));
    }

    @HapiTest
    HapiSpec simpleFactoryWorks() {
        return defaultHapiSpec("simpleFactoryWorks")
                .given(uploadInitCode(CONTRACT), contractCreate(CONTRACT))
                .when(contractCall(CONTRACT, DEPLOYMENT_SUCCESS_FUNCTION)
                        .gas(780_000)
                        .via(DEPLOYMENT_SUCCESS_TXN))
                .then(withOpContext((spec, opLog) -> {
                    final var successTxn = getTxnRecord(DEPLOYMENT_SUCCESS_TXN);
                    final var parentContract = getContractInfo(CONTRACT).saveToRegistry(CONTRACT_INFO);
                    allRunFor(spec, successTxn, parentContract);

                    final var createdContractIDs = successTxn
                            .getResponseRecord()
                            .getContractCallResult()
                            .getCreatedContractIDsList();

                    Assertions.assertEquals(createdContractIDs.size(), 1);
                }));
    }

    @HapiTest
    HapiSpec stackedFactoryWorks() {
        return defaultHapiSpec("StackedFactoryWorks")
                .given(uploadInitCode(CONTRACT), contractCreate(CONTRACT))
                .when(contractCall(CONTRACT, "stackedDeploymentSuccess")
                        .gas(1_000_000)
                        .via("stackedDeploymentSuccessTxn"))
                .then(withOpContext((spec, opLog) -> {
                    final var successTxn = getTxnRecord("stackedDeploymentSuccessTxn");
                    final var parentContract = getContractInfo(CONTRACT).saveToRegistry(CONTRACT_INFO);
                    allRunFor(spec, successTxn, parentContract);

                    final var createdContractIDs = successTxn
                            .getResponseRecord()
                            .getContractCallResult()
                            .getCreatedContractIDsList();

                    Assertions.assertEquals(createdContractIDs.size(), 2);
                }));
    }

    @HapiTest
    HapiSpec resetOnFactoryFailureWorks() {
        return defaultHapiSpec("ResetOnFactoryFailureWorks")
                .given(uploadInitCode(CONTRACT), contractCreate(CONTRACT))
                .when(
                        contractCall(CONTRACT, "stackedDeploymentFailure")
                                .hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
                                .gas(780_000)
                                .via("deploymentFailureTxn"),
                        contractCall(CONTRACT, DEPLOYMENT_SUCCESS_FUNCTION)
                                .gas(780_000)
                                .via(DEPLOYMENT_SUCCESS_TXN))
                .then(withOpContext((spec, opLog) -> {
                    final var revertTxn = getTxnRecord("deploymentFailureTxn");
                    final var deploymentSuccessTxn = getTxnRecord(DEPLOYMENT_SUCCESS_TXN);
                    final var parentContract = getContractInfo(CONTRACT).saveToRegistry(CONTRACT_INFO);
                    allRunFor(spec, revertTxn, parentContract, deploymentSuccessTxn);

                    final var createdContracts = deploymentSuccessTxn
                            .getResponseRecord()
                            .getContractCallResult()
                            .getCreatedContractIDsList();

                    Assertions.assertTrue(revertTxn
                            .getResponseRecord()
                            .getContractCallResult()
                            .getCreatedContractIDsList()
                            .isEmpty());
                    Assertions.assertEquals(createdContracts.size(), 1);
                }));
    }

    @HapiTest
    HapiSpec resetOnFactoryFailureAfterDeploymentWorks() {
        return defaultHapiSpec("ResetOnFactoryFailureAfterDeploymentWorks")
                .given(uploadInitCode(CONTRACT), contractCreate(CONTRACT))
                .when(
                        contractCall(CONTRACT, "failureAfterDeploy")
                                .hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
                                .gas(780_000)
                                .via("failureAfterDeploymentTxn"),
                        contractCall(CONTRACT, DEPLOYMENT_SUCCESS_FUNCTION)
                                .gas(780_000)
                                .via(DEPLOYMENT_SUCCESS_TXN))
                .then(withOpContext((spec, opLog) -> {
                    final var revertTxn = getTxnRecord("failureAfterDeploymentTxn");
                    final var deploymentSuccessTxn = getTxnRecord(DEPLOYMENT_SUCCESS_TXN);
                    final var parentContract = getContractInfo(CONTRACT).saveToRegistry(CONTRACT_INFO);
                    allRunFor(spec, revertTxn, parentContract, deploymentSuccessTxn);

                    final var createdContracts = deploymentSuccessTxn
                            .getResponseRecord()
                            .getContractCallResult()
                            .getCreatedContractIDsList();

                    Assertions.assertTrue(revertTxn
                            .getResponseRecord()
                            .getContractCallResult()
                            .getCreatedContractIDsList()
                            .isEmpty());
                    Assertions.assertEquals(createdContracts.size(), 1);
                }));
    }

    @HapiTest
    HapiSpec resetOnStackedFactoryFailureWorks() {
        return defaultHapiSpec("ResetOnStackedFactoryFailureWorks")
                .given(uploadInitCode(CONTRACT), contractCreate(CONTRACT))
                .when(
                        contractCall(CONTRACT, "stackedDeploymentFailure")
                                .hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
                                .gas(780_000)
                                .via("stackedDeploymentFailureTxn"),
                        contractCall(CONTRACT, DEPLOYMENT_SUCCESS_FUNCTION)
                                .gas(780_000)
                                .via(DEPLOYMENT_SUCCESS_TXN))
                .then(withOpContext((spec, opLog) -> {
                    final var revertTxn = getTxnRecord("stackedDeploymentFailureTxn");
                    final var deploymentSuccessTxn = getTxnRecord(DEPLOYMENT_SUCCESS_TXN);
                    final var parentContract = getContractInfo(CONTRACT).saveToRegistry(CONTRACT_INFO);
                    allRunFor(spec, revertTxn, parentContract, deploymentSuccessTxn);

                    final var createdContracts = deploymentSuccessTxn
                            .getResponseRecord()
                            .getContractCallResult()
                            .getCreatedContractIDsList();

                    Assertions.assertTrue(revertTxn
                            .getResponseRecord()
                            .getContractCallResult()
                            .getCreatedContractIDsList()
                            .isEmpty());
                    Assertions.assertEquals(createdContracts.size(), 1);
                }));
    }

    @HapiTest
    private HapiSpec contractCreateWithNewOpInConstructorAbandoningParent() {
        final var contract = "AbandoningParent";
        return defaultHapiSpec("contractCreateWithNewOpInConstructorAbandoningParent")
                .given(uploadInitCode(contract), contractCreate(contract).via("AbandoningParentTxn"))
                .when()
                .then(
                        getContractInfo(contract)
                                .saveToRegistry("AbandoningParentParentInfo")
                                .logged(),
                        getTxnRecord("AbandoningParentTxn")
                                .saveCreatedContractListToRegistry(contract)
                                .logged(),
                        UtilVerbs.contractListWithPropertiesInheritedFrom(
                                "AbandoningParentCreateResult", 6, "AbandoningParentParentInfo"));
    }

    @HapiTest
    HapiSpec childContractStorageWorks() {
        final var contract = "CreateTrivial";
        final var CREATED_TRIVIAL_CONTRACT_RETURNS = 7;

        return defaultHapiSpec("childContractStorageWorks")
                .given(uploadInitCode(contract))
                .when(contractCreate(contract).via("firstContractTxn"))
                .then(assertionsHold((spec, ctxLog) -> {
                    final var subop1 =
                            contractCall(contract, "create").gas(785_000).via("createContractTxn");

                    // First contract calls created contract and get an integer
                    // return value
                    final var subop2 =
                            contractCallLocal(contract, "getIndirect").saveResultTo("contractCallContractResultBytes");
                    CustomSpecAssert.allRunFor(spec, subop1, subop2);

                    var resultBytes = spec.registry().getBytes("contractCallContractResultBytes");
                    com.esaulpaugh.headlong.abi.Function function =
                            com.esaulpaugh.headlong.abi.Function.fromJson(getABIFor(FUNCTION, "getIndirect", contract));

                    var contractCallReturnVal = 0;
                    if (resultBytes != null && resultBytes.length > 0) {
                        final var retResults = function.decodeReturn(resultBytes);
                        if (retResults != null && retResults.size() > 0) {
                            final var retBi = (BigInteger) retResults.get(0);
                            contractCallReturnVal = retBi.intValue();
                        }
                    }

                    ctxLog.info("This contract call contract return value {}", contractCallReturnVal);
                    Assertions.assertEquals(
                            CREATED_TRIVIAL_CONTRACT_RETURNS,
                            contractCallReturnVal,
                            "This contract call contract return value should be 7");

                    // Get created contract's info with call to first contract
                    final var subop3 =
                            contractCallLocal(contract, "getAddress").saveResultTo("getCreatedContractInfoResultBytes");
                    CustomSpecAssert.allRunFor(spec, subop3);

                    resultBytes = spec.registry().getBytes("getCreatedContractInfoResultBytes");

                    function =
                            com.esaulpaugh.headlong.abi.Function.fromJson(getABIFor(FUNCTION, "getAddress", contract));

                    final var retResults = function.decodeReturn(resultBytes);
                    String contractIDString = null;
                    if (retResults != null && retResults.size() > 0) {
                        contractIDString =
                                ((Address) retResults.get(0)).toString().substring(2);
                    }
                    ctxLog.info("The created contract ID {}", contractIDString);
                    Assertions.assertNotEquals(
                            ContractID.newBuilder().getDefaultInstanceForType(),
                            TxnUtils.asContractId(contractIDString, spec),
                            "Created contract doesn't have valid Contract ID");

                    final var subop4 = getContractInfo(contractIDString).saveToRegistry("createdContractInfoSaved");

                    CustomSpecAssert.allRunFor(spec, subop4);

                    final ContractGetInfoResponse.ContractInfo createdContractInfo =
                            spec.registry().getContractInfo("createdContractInfoSaved");

                    Assertions.assertTrue(createdContractInfo.hasContractID());
                    Assertions.assertTrue(createdContractInfo.hasAccountID());
                    Assertions.assertTrue(createdContractInfo.hasExpirationTime());
                }));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}

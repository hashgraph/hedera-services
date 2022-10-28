/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
import com.google.common.primitives.Longs;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Assertions;

public class CreateOperationSuite extends HapiApiSuite {

    private static final Logger log = LogManager.getLogger(CreateOperationSuite.class);
    private static final String CONTRACT = "FactoryContract";

    public static void main(String... args) {
        new CreateOperationSuite().runSuiteSync();
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                new HapiApiSpec[] {
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
                });
    }

    private HapiApiSpec factoryAndSelfDestructInConstructorContract() {
        final var contract = "FactorySelfDestructConstructor";

        return defaultHapiSpec("FactoryAndSelfDestructInConstructorContract")
                .given(uploadInitCode(contract), contractCreate(contract).balance(10))
                .when(contractCall(contract).hasKnownStatus(CONTRACT_DELETED))
                .then(getContractBytecode(contract).hasCostAnswerPrecheck(CONTRACT_DELETED));
    }

    private HapiApiSpec factoryQuickSelfDestructContract() {
        final var contract = "FactoryQuickSelfDestruct";

        return defaultHapiSpec("FactoryQuickSelfDestructContract")
                .given(uploadInitCode(contract), contractCreate(contract))
                .when(
                        contractCall(contract, "createAndDeleteChild")
                                .gas(4_000_000)
                                .via("callRecord"))
                .then(
                        getTxnRecord("callRecord")
                                .hasPriority(
                                        recordWith()
                                                .contractCallResult(
                                                        resultWith()
                                                                .logs(
                                                                        inOrder(
                                                                                logWith()
                                                                                        .withTopicsInOrder(
                                                                                                List
                                                                                                        .of(
                                                                                                                eventSignatureOf(
                                                                                                                        "ChildCreated(address)"))),
                                                                                logWith()
                                                                                        .withTopicsInOrder(
                                                                                                List
                                                                                                        .of(
                                                                                                                eventSignatureOf(
                                                                                                                        "ChildDeleted()"))))))));
    }

    private HapiApiSpec inheritanceOfNestedCreatedContracts() {
        final var contract = "NestedChildren";
        return defaultHapiSpec("InheritanceOfNestedCreatedContracts")
                .given(
                        uploadInitCode(contract),
                        contractCreate(contract).logged().via("createRecord"),
                        getContractInfo(contract).logged().saveToRegistry("parentInfo"))
                .when(contractCall(contract, "callCreate").gas(780_000).via("callRecord"))
                .then(
                        getTxnRecord("createRecord").saveCreatedContractListToRegistry("ctorChild"),
                        getTxnRecord("callRecord").saveCreatedContractListToRegistry("callChild"),
                        contractListWithPropertiesInheritedFrom(
                                "callChildCallResult", 2, "parentInfo"),
                        contractListWithPropertiesInheritedFrom(
                                "ctorChildCreateResult", 3, "parentInfo"));
    }

    HapiApiSpec simpleFactoryWorks() {
        return defaultHapiSpec("ContractFactoryWorksHappyPath")
                .given(uploadInitCode(CONTRACT), contractCreate(CONTRACT))
                .when(
                        contractCall(CONTRACT, "deploymentSuccess")
                                .gas(780_000)
                                .via("deploymentSuccessTxn"))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var successTxn = getTxnRecord("deploymentSuccessTxn");
                                    final var parentContract =
                                            getContractInfo(CONTRACT)
                                                    .saveToRegistry("contractInfo");
                                    allRunFor(spec, successTxn, parentContract);

                                    final var parentID =
                                            spec.registry()
                                                    .getContractInfo("contractInfo")
                                                    .getContractID();
                                    final var createdContractIDs =
                                            successTxn
                                                    .getResponseRecord()
                                                    .getContractCallResult()
                                                    .getCreatedContractIDsList();

                                    Assertions.assertEquals(createdContractIDs.size(), 1);
                                    Assertions.assertEquals(
                                            parentID.getContractNum(),
                                            createdContractIDs.get(0).getContractNum() - 1);
                                }));
    }

    HapiApiSpec stackedFactoryWorks() {
        return defaultHapiSpec("StackedFactoryWorks")
                .given(uploadInitCode(CONTRACT), contractCreate(CONTRACT))
                .when(
                        contractCall(CONTRACT, "stackedDeploymentSuccess")
                                .gas(1_000_000)
                                .via("stackedDeploymentSuccessTxn"))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var successTxn =
                                            getTxnRecord("stackedDeploymentSuccessTxn");
                                    final var parentContract =
                                            getContractInfo(CONTRACT)
                                                    .saveToRegistry("contractInfo");
                                    allRunFor(spec, successTxn, parentContract);

                                    final var parentID =
                                            spec.registry()
                                                    .getContractInfo("contractInfo")
                                                    .getContractID();
                                    final var createdContractIDs =
                                            successTxn
                                                    .getResponseRecord()
                                                    .getContractCallResult()
                                                    .getCreatedContractIDsList();

                                    Assertions.assertEquals(createdContractIDs.size(), 2);
                                    Assertions.assertEquals(
                                            parentID.getContractNum(),
                                            createdContractIDs.get(0).getContractNum() - 1);
                                    Assertions.assertEquals(
                                            parentID.getContractNum(),
                                            createdContractIDs.get(1).getContractNum() - 2);
                                }));
    }

    HapiApiSpec resetOnFactoryFailureWorks() {
        return defaultHapiSpec("ResetOnFactoryFailureWorks")
                .given(uploadInitCode(CONTRACT), contractCreate(CONTRACT))
                .when(
                        contractCall(CONTRACT, "stackedDeploymentFailure")
                                .hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
                                .gas(780_000)
                                .via("deploymentFailureTxn"),
                        contractCall(CONTRACT, "deploymentSuccess")
                                .gas(780_000)
                                .via("deploymentSuccessTxn"))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var revertTxn = getTxnRecord("deploymentFailureTxn");
                                    final var deploymentSuccessTxn =
                                            getTxnRecord("deploymentSuccessTxn");
                                    final var parentContract =
                                            getContractInfo(CONTRACT)
                                                    .saveToRegistry("contractInfo");
                                    allRunFor(
                                            spec, revertTxn, parentContract, deploymentSuccessTxn);

                                    final var parentID =
                                            spec.registry()
                                                    .getContractInfo("contractInfo")
                                                    .getContractID();
                                    final var createdContracts =
                                            deploymentSuccessTxn
                                                    .getResponseRecord()
                                                    .getContractCallResult()
                                                    .getCreatedContractIDsList();

                                    Assertions.assertTrue(
                                            revertTxn
                                                    .getResponseRecord()
                                                    .getContractCallResult()
                                                    .getCreatedContractIDsList()
                                                    .isEmpty());
                                    Assertions.assertEquals(createdContracts.size(), 1);
                                    Assertions.assertEquals(
                                            parentID.getContractNum(),
                                            createdContracts.get(0).getContractNum() - 1);
                                }));
    }

    HapiApiSpec resetOnFactoryFailureAfterDeploymentWorks() {
        return defaultHapiSpec("ResetOnFactoryFailureAfterDeploymentWorks")
                .given(uploadInitCode(CONTRACT), contractCreate(CONTRACT))
                .when(
                        contractCall(CONTRACT, "failureAfterDeploy")
                                .hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
                                .gas(780_000)
                                .via("failureAfterDeploymentTxn"),
                        contractCall(CONTRACT, "deploymentSuccess")
                                .gas(780_000)
                                .via("deploymentSuccessTxn"))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var revertTxn = getTxnRecord("failureAfterDeploymentTxn");
                                    final var deploymentSuccessTxn =
                                            getTxnRecord("deploymentSuccessTxn");
                                    final var parentContract =
                                            getContractInfo(CONTRACT)
                                                    .saveToRegistry("contractInfo");
                                    allRunFor(
                                            spec, revertTxn, parentContract, deploymentSuccessTxn);

                                    final var parentID =
                                            spec.registry()
                                                    .getContractInfo("contractInfo")
                                                    .getContractID();
                                    final var createdContracts =
                                            deploymentSuccessTxn
                                                    .getResponseRecord()
                                                    .getContractCallResult()
                                                    .getCreatedContractIDsList();

                                    Assertions.assertTrue(
                                            revertTxn
                                                    .getResponseRecord()
                                                    .getContractCallResult()
                                                    .getCreatedContractIDsList()
                                                    .isEmpty());
                                    Assertions.assertEquals(createdContracts.size(), 1);
                                    Assertions.assertEquals(
                                            parentID.getContractNum(),
                                            createdContracts.get(0).getContractNum() - 1);
                                }));
    }

    HapiApiSpec resetOnStackedFactoryFailureWorks() {
        return defaultHapiSpec("ResetOnStackedFactoryFailureWorks")
                .given(uploadInitCode(CONTRACT), contractCreate(CONTRACT))
                .when(
                        contractCall(CONTRACT, "stackedDeploymentFailure")
                                .hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
                                .gas(780_000)
                                .via("stackedDeploymentFailureTxn"),
                        contractCall(CONTRACT, "deploymentSuccess")
                                .gas(780_000)
                                .via("deploymentSuccessTxn"))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var revertTxn =
                                            getTxnRecord("stackedDeploymentFailureTxn");
                                    final var deploymentSuccessTxn =
                                            getTxnRecord("deploymentSuccessTxn");
                                    final var parentContract =
                                            getContractInfo(CONTRACT)
                                                    .saveToRegistry("contractInfo");
                                    allRunFor(
                                            spec, revertTxn, parentContract, deploymentSuccessTxn);

                                    final var parentID =
                                            spec.registry()
                                                    .getContractInfo("contractInfo")
                                                    .getContractID();
                                    final var createdContracts =
                                            deploymentSuccessTxn
                                                    .getResponseRecord()
                                                    .getContractCallResult()
                                                    .getCreatedContractIDsList();

                                    Assertions.assertTrue(
                                            revertTxn
                                                    .getResponseRecord()
                                                    .getContractCallResult()
                                                    .getCreatedContractIDsList()
                                                    .isEmpty());
                                    Assertions.assertEquals(createdContracts.size(), 1);
                                    Assertions.assertEquals(
                                            parentID.getContractNum(),
                                            createdContracts.get(0).getContractNum() - 1);
                                }));
    }

    private HapiApiSpec contractCreateWithNewOpInConstructor() {
        final var contract = "AbandoningParent";
        return defaultHapiSpec("ContractCreateWithNewOpInConstructorAbandoningParent")
                .given(
                        uploadInitCode(contract),
                        contractCreate(contract).via("AbandoningParentTxn"))
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

    HapiApiSpec childContractStorageWorks() {
        final var contract = "CreateTrivial";
        final var CREATED_TRIVIAL_CONTRACT_RETURNS = 7;

        return defaultHapiSpec("childContractStorageWorks")
                .given(uploadInitCode(contract))
                .when(contractCreate(contract).via("firstContractTxn"))
                .then(
                        assertionsHold(
                                (spec, ctxLog) -> {
                                    final var subop1 =
                                            contractCall(contract, "create")
                                                    .gas(785_000)
                                                    .via("createContractTxn");

                                    // First contract calls created contract and get an integer
                                    // return value
                                    final var subop2 =
                                            contractCallLocal(contract, "getIndirect")
                                                    .saveResultTo(
                                                            "contractCallContractResultBytes");
                                    CustomSpecAssert.allRunFor(spec, subop1, subop2);

                                    var resultBytes =
                                            spec.registry()
                                                    .getBytes("contractCallContractResultBytes");
                                    com.esaulpaugh.headlong.abi.Function function =
                                            com.esaulpaugh.headlong.abi.Function.fromJson(
                                                    getABIFor(FUNCTION, "getIndirect", contract));

                                    var contractCallReturnVal = 0;
                                    if (resultBytes != null && resultBytes.length > 0) {
                                        final var retResults = function.decodeReturn(resultBytes);
                                        if (retResults != null && retResults.size() > 0) {
                                            final var retBi = (BigInteger) retResults.get(0);
                                            contractCallReturnVal = retBi.intValue();
                                        }
                                    }

                                    ctxLog.info(
                                            "This contract call contract return value {}",
                                            contractCallReturnVal);
                                    Assertions.assertEquals(
                                            CREATED_TRIVIAL_CONTRACT_RETURNS,
                                            contractCallReturnVal,
                                            "This contract call contract return value should be 7");

                                    // Get created contract's info with call to first contract
                                    final var subop3 =
                                            contractCallLocal(contract, "getAddress")
                                                    .saveResultTo(
                                                            "getCreatedContractInfoResultBytes");
                                    CustomSpecAssert.allRunFor(spec, subop3);

                                    resultBytes =
                                            spec.registry()
                                                    .getBytes("getCreatedContractInfoResultBytes");

                                    function =
                                            com.esaulpaugh.headlong.abi.Function.fromJson(
                                                    getABIFor(FUNCTION, "getAddress", contract));

                                    final var retResults = function.decodeReturn(resultBytes);
                                    String contractIDString = null;
                                    if (retResults != null && retResults.size() > 0) {
                                        final var retValHex =
                                                ((Address) retResults.get(0)).toString();
                                        final var retVal = Bytes.fromHexString(retValHex).toArray();

                                        final var realm =
                                                Longs.fromByteArray(
                                                        Arrays.copyOfRange(retVal, 4, 12));
                                        final var accountNum =
                                                Longs.fromByteArray(
                                                        Arrays.copyOfRange(retVal, 12, 20));
                                        contractIDString =
                                                String.format("%d.%d.%d", realm, 0, accountNum);
                                    }
                                    ctxLog.info("The created contract ID {}", contractIDString);
                                    Assertions.assertNotEquals(
                                            ContractID.newBuilder().getDefaultInstanceForType(),
                                            TxnUtils.asContractId(contractIDString, spec),
                                            "Created contract doesn't have valid Contract ID");

                                    final var subop4 =
                                            getContractInfo(contractIDString)
                                                    .saveToRegistry("createdContractInfoSaved");

                                    CustomSpecAssert.allRunFor(spec, subop4);

                                    ContractGetInfoResponse.ContractInfo createdContractInfo =
                                            spec.registry()
                                                    .getContractInfo("createdContractInfoSaved");

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

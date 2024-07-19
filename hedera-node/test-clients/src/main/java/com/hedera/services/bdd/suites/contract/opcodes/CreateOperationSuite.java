/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.contractListWithPropertiesInheritedFrom;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.ACCEPTED_MONO_GAS_CALCULATION_DIFFERENCE;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.FULLY_NONDETERMINISTIC;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.HIGHLY_NON_DETERMINISTIC_FEES;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_CONTRACT_CALL_RESULTS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_FUNCTION_PARAMETERS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_LOG_DATA;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_TRANSACTION_FEES;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.aaWith;
import static com.hedera.services.bdd.suites.contract.Utils.captureOneChildCreate2MetaFor;
import static com.hedera.services.bdd.suites.contract.Utils.eventSignatureOf;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.contract.opcodes.Create2OperationSuite.CREATE_2_TXN;
import static com.hedera.services.bdd.suites.contract.opcodes.Create2OperationSuite.GET_BYTECODE;
import static com.hedera.services.bdd.suites.contract.opcodes.Create2OperationSuite.assertCreate2Address;
import static com.hedera.services.bdd.suites.contract.opcodes.Create2OperationSuite.setExpectedCreate2Address;
import static com.hedera.services.bdd.suites.contract.opcodes.Create2OperationSuite.setIdentifiers;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.A_TOKEN;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.LAZY_MEMO;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.PARTY;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.TOKEN_A_CREATE;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.MULTI_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hederahashgraph.api.proto.java.TokenSupplyType.FINITE;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;

import com.esaulpaugh.headlong.abi.Address;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.swirlds.common.utility.CommonUtils;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

// CREATE2
@Tag(SMART_CONTRACT)
public class CreateOperationSuite {
    private static final String CONTRACT = "FactoryContract";
    private static final String CALL_RECORD_TRANSACTION_NAME = "callRecord";
    private static final String DEPLOYMENT_SUCCESS_FUNCTION = "deploymentSuccess";
    private static final String DEPLOYMENT_SUCCESS_TXN = "deploymentSuccessTxn";
    private static final String CONTRACT_INFO = "contractInfo";
    private static final String PARENT_INFO = "parentInfo";

    @HapiTest
    final Stream<DynamicTest> factoryQuickSelfDestructContract() {
        final var contract = "FactoryQuickSelfDestruct";
        final var sender = "sender";
        return defaultHapiSpec(
                        "FactoryQuickSelfDestructContract",
                        NONDETERMINISTIC_TRANSACTION_FEES,
                        NONDETERMINISTIC_LOG_DATA)
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
    final Stream<DynamicTest> inheritanceOfNestedCreatedContracts() {
        final var contract = "NestedChildren";
        return defaultHapiSpec("InheritanceOfNestedCreatedContracts", FULLY_NONDETERMINISTIC)
                .given(
                        uploadInitCode(contract),
                        // refuse eth conversion because ethereum transaction is missing admin key and memo is same as
                        // parent
                        contractCreate(contract).logged().via("createRecord").refusingEthConversion(),
                        getContractInfo(contract).logged().saveToRegistry(PARENT_INFO))
                .when(contractCall(contract, "callCreate").gas(780_000).via(CALL_RECORD_TRANSACTION_NAME))
                .then(
                        getTxnRecord("createRecord").saveCreatedContractListToRegistry("ctorChild"),
                        getTxnRecord(CALL_RECORD_TRANSACTION_NAME).saveCreatedContractListToRegistry("callChild"),
                        contractListWithPropertiesInheritedFrom("callChildCallResult", 2, PARENT_INFO),
                        contractListWithPropertiesInheritedFrom("ctorChildCreateResult", 3, PARENT_INFO));
    }

    @HapiTest
    final Stream<DynamicTest> simpleFactoryWorks() {
        return defaultHapiSpec("simpleFactoryWorks", NONDETERMINISTIC_TRANSACTION_FEES)
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
    final Stream<DynamicTest> stackedFactoryWorks() {
        return defaultHapiSpec("StackedFactoryWorks", FULLY_NONDETERMINISTIC)
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
    final Stream<DynamicTest> resetOnFactoryFailureWorks() {
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
    final Stream<DynamicTest> resetOnFactoryFailureAfterDeploymentWorks() {
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
    final Stream<DynamicTest> resetOnStackedFactoryFailureWorks() {
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
    final Stream<DynamicTest> contractCreateWithNewOpInConstructorAbandoningParent() {
        final var contract = "AbandoningParent";
        return defaultHapiSpec(
                        "contractCreateWithNewOpInConstructorAbandoningParent",
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        HIGHLY_NON_DETERMINISTIC_FEES,
                        ACCEPTED_MONO_GAS_CALCULATION_DIFFERENCE)
                // refuse eth conversion because ethereum transaction is missing admin key (the new contract has own key
                // - isSelfAdmin(parent))
                .given(
                        uploadInitCode(contract),
                        contractCreate(contract).via("AbandoningParentTxn").refusingEthConversion())
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
    final Stream<DynamicTest> childContractStorageWorks() {
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

    private static final String CREATION = "creation";
    private static final String ADMIN_KEY = "adminKey";
    private static final String ENTITY_MEMO = "JUST DO IT";
    public static final String DEPLOY = "deploy";

    @HapiTest
    final Stream<DynamicTest> canMergeCreate2ChildWithHollowAccountFungibleTransfersUnlimitedAssociations() {
        final var tcValue = 1_234L;
        final var contract = "Create2Factory";

        final var creation = CREATION;
        final var salt = BigInteger.valueOf(42);
        final var adminKey = ADMIN_KEY;
        final AtomicReference<String> factoryEvmAddress = new AtomicReference<>();
        final AtomicReference<String> expectedCreate2Address = new AtomicReference<>();
        final AtomicReference<String> hollowCreationAddress = new AtomicReference<>();
        final AtomicReference<String> mergedAliasAddr = new AtomicReference<>();
        final AtomicReference<String> mergedMirrorAddr = new AtomicReference<>();
        final AtomicReference<byte[]> testContractInitcode = new AtomicReference<>();

        final var initialTokenSupply = 1000;

        final int fungibleTransfersSize = 5;
        final AtomicReference<TokenID>[] ftIds = new AtomicReference[fungibleTransfersSize];
        for (int i = 0; i < ftIds.length; i++) {
            ftIds[i] = new AtomicReference<>();
        }

        final AtomicReference<AccountID> partyId = new AtomicReference<>();
        final AtomicReference<ByteString> partyAlias = new AtomicReference<>();

        final int givenOpsSize = 6;
        HapiSpecOperation[] givenOps = new HapiSpecOperation[givenOpsSize + (fungibleTransfersSize * 2)];
        givenOps[0] = newKeyNamed(adminKey);
        givenOps[1] = newKeyNamed(MULTI_KEY);
        givenOps[2] = uploadInitCode(contract);
        givenOps[3] = contractCreate(contract)
                .payingWith(GENESIS)
                .adminKey(adminKey)
                .entityMemo(ENTITY_MEMO)
                .via(CREATE_2_TXN)
                .exposingNumTo(num -> factoryEvmAddress.set(asHexedSolidityAddress(0, 0, num)));
        givenOps[4] = cryptoCreate(PARTY).maxAutomaticTokenAssociations(2);
        givenOps[5] = setIdentifiers(Optional.empty(), Optional.empty(), Optional.of(partyId), Optional.of(partyAlias));

        int j = 0;
        for (int i = givenOpsSize; i < fungibleTransfersSize + givenOpsSize; i++) {
            givenOps[i] = tokenCreate(A_TOKEN + j)
                    .tokenType(FUNGIBLE_COMMON)
                    .supplyType(FINITE)
                    .initialSupply(initialTokenSupply)
                    .maxSupply(10L * initialTokenSupply)
                    .treasury(PARTY)
                    .via(TOKEN_A_CREATE + j);
            j++;
        }

        int j1 = 0;
        for (int i = fungibleTransfersSize + givenOpsSize; i < (fungibleTransfersSize * 2) + givenOpsSize; i++) {
            givenOps[i] = setIdentifierToken(Optional.of(ftIds[j1]), A_TOKEN + j1);
            j1++;
        }

        return defaultHapiSpec(
                        "CanMergeCreate2ChildWithHollowAccountFungibleTransfersUnlimitedAssociations",
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS,
                        NONDETERMINISTIC_TRANSACTION_FEES,
                        NONDETERMINISTIC_LOG_DATA)
                .given(givenOps)
                .when(
                        // GET BYTECODE OF THE CREATE2 CONTRACT
                        sourcing(() -> contractCallLocal(
                                        contract, GET_BYTECODE, asHeadlongAddress(factoryEvmAddress.get()), salt)
                                .exposingTypedResultsTo(results -> {
                                    final var tcInitcode = (byte[]) results[0];
                                    testContractInitcode.set(tcInitcode);
                                })
                                .payingWith(GENESIS)
                                .nodePayment(ONE_HBAR)),
                        // GET THE ADDRESS WHERE THE CONTRACT WILL BE DEPLOYED
                        sourcing(() -> setExpectedCreate2Address(
                                contract, salt, expectedCreate2Address, testContractInitcode)),

                        // Now create a hollow account at the desired address
                        lazyCreateAccountWithFungibleTransfers(creation, expectedCreate2Address, ftIds, partyAlias),
                        getTxnRecord(creation)
                                .andAllChildRecords()
                                .logged()
                                .exposingCreationsTo(l -> hollowCreationAddress.set(l.get(0))),
                        sourcing(() -> getAccountInfo(hollowCreationAddress.get())
                                .hasAlreadyUsedAutomaticAssociations(fungibleTransfersSize)
                                .logged()))
                .then(
                        // deploy create2
                        sourcing(() -> contractCall(contract, DEPLOY, testContractInitcode.get(), salt)
                                .payingWith(GENESIS)
                                .gas(4_000_000L)
                                .sending(tcValue)
                                .via("TEST2")),
                        getTxnRecord("TEST2").andAllChildRecords().logged(),
                        captureOneChildCreate2MetaFor(
                                "Merged deployed contract with hollow account",
                                "TEST2",
                                mergedMirrorAddr,
                                mergedAliasAddr),

                        // check failure when trying to deploy again
                        sourcing(() -> contractCall(contract, DEPLOY, testContractInitcode.get(), salt)
                                .payingWith(GENESIS)
                                .gas(4_000_000L)
                                /* Cannot repeat CREATE2
                                with same args without destroying the existing contract */
                                .hasKnownStatusFrom(INVALID_SOLIDITY_ADDRESS, CONTRACT_REVERT_EXECUTED)),

                        // check created contract
                        sourcing(() -> getContractInfo(mergedAliasAddr.get())
                                .has(contractWith()
                                        .hasStandinContractKey()
                                        .maxAutoAssociations(fungibleTransfersSize)
                                        .hasAlreadyUsedAutomaticAssociations(fungibleTransfersSize)
                                        .memo(LAZY_MEMO)
                                        .balance(tcValue))
                                .logged()),
                        sourcing(
                                () -> getContractBytecode(mergedAliasAddr.get()).isNonEmpty()),
                        sourcing(() ->
                                assertCreate2Address(contract, salt, expectedCreate2Address, testContractInitcode)));
    }

    private HapiCryptoTransfer lazyCreateAccountWithFungibleTransfers(
            String creation,
            AtomicReference<String> expectedCreate2Address,
            AtomicReference<TokenID> ftIds[],
            AtomicReference<ByteString> partyAlias) {
        return cryptoTransfer((spec, b) -> {
                    for (AtomicReference<TokenID> ftId : ftIds) {
                        b.addTokenTransfers(TokenTransferList.newBuilder()
                                .setToken(ftId.get())
                                .addTransfers(aaWith(partyAlias.get(), -500))
                                .addTransfers(aaWith(
                                        ByteString.copyFrom(CommonUtils.unhex(expectedCreate2Address.get())), +500)));
                    }
                })
                .signedBy(DEFAULT_PAYER, PARTY)
                .fee(ONE_HBAR)
                .via(creation);
    }

    private CustomSpecAssert setIdentifierToken(final Optional<AtomicReference<TokenID>> ftId, final String token) {
        return withOpContext((spec, opLog) -> {
            final var registry = spec.registry();
            ftId.ifPresent(id -> id.set(registry.getTokenID(token)));
        });
    }
}

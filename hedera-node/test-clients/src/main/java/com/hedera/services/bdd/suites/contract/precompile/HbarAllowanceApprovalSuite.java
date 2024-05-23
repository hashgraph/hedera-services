/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.contract.precompile;

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiPropertySource.idAsHeadlongAddress;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.services.bdd.junit.HapiTest;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
public class HbarAllowanceApprovalSuite {
    private static final Logger log = LogManager.getLogger(HbarAllowanceApprovalSuite.class);
    private static final String MULTI_KEY = "multikey";
    private static final String SPENDER = "spender";
    private static final String HRC632_CONTRACT = "HRC632Contract";
    private static final String IHRC632 = "IHRC632";
    private static final String ACCOUNT = "account";
    private static final String SIGNER = "signer";
    private static final String HBAR_ALLOWANCE_TXN = "hbarAllowanceTxn";
    private static final String HBAR_APPROVE_TXN = "hbarApproveTxn";
    private static final String HBAR_ALLOWANCE = "hbarAllowance";
    private static final String HBAR_APPROVE = "hbarApprove";
    private static final String HBAR_ALLOWANCE_CALL = "hbarAllowanceCall";
    private static final String HBAR_APPROVE_CALL = "hbarApproveCall";
    private static final String CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_ENABLED =
            "contracts.systemContract.accountService.enabled";

    @HapiTest
    final Stream<DynamicTest> hrc632AllowanceFromEOA() {
        final AtomicReference<Address> accountNum = new AtomicReference<>();
        final AtomicReference<Address> spenderNum = new AtomicReference<>();

        return propertyPreservingHapiSpec("hrc632AllowanceFromEOA")
                .preserving(CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_ENABLED)
                .given(
                        overriding(CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_ENABLED, "true"),
                        cryptoCreate(ACCOUNT)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .exposingCreatedIdTo(id -> accountNum.set(idAsHeadlongAddress(id))),
                        cryptoCreate(SPENDER).exposingCreatedIdTo(id -> spenderNum.set(idAsHeadlongAddress(id))),
                        cryptoApproveAllowance().payingWith(ACCOUNT).addCryptoAllowance(ACCOUNT, SPENDER, 1_000_000))
                .when(withOpContext((spec, opLog) -> {
                    var accountAddress = "0.0." + accountNum.get().value();
                    var spenderAddress = spenderNum.get();
                    allRunFor(
                            spec,
                            // call hbarAllowance from EOA
                            contractCallWithFunctionAbi(
                                            accountAddress,
                                            getABIFor(
                                                    com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                    HBAR_ALLOWANCE,
                                                    IHRC632),
                                            spenderAddress)
                                    .payingWith(ACCOUNT)
                                    .gas(1_000_000)
                                    .via(HBAR_ALLOWANCE_TXN));
                }))
                .then(getTxnRecord(HBAR_ALLOWANCE_TXN)
                        .logged()
                        .hasPriority(recordWith()
                                .status(SUCCESS)
                                .contractCallResult(resultWith()
                                        .resultThruAbi(
                                                getABIFor(
                                                        com.hedera.services.bdd.suites.contract.Utils.FunctionType
                                                                .FUNCTION,
                                                        HBAR_ALLOWANCE,
                                                        IHRC632),
                                                isLiteralResult(
                                                        new Object[] {Long.valueOf(22), BigInteger.valueOf(1_000_000L)
                                                        })))));
    }

    @HapiTest
    final Stream<DynamicTest> hrc632ApproveFromEOA() {
        final AtomicReference<Address> accountNum = new AtomicReference<>();
        final AtomicReference<Address> spenderNum = new AtomicReference<>();

        return propertyPreservingHapiSpec("hrc632ApproveFromEOA")
                .preserving(CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_ENABLED)
                .given(
                        overriding(CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_ENABLED, "true"),
                        cryptoCreate(ACCOUNT)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .exposingCreatedIdTo(id -> accountNum.set(idAsHeadlongAddress(id))),
                        cryptoCreate(SPENDER).exposingCreatedIdTo(id -> spenderNum.set(idAsHeadlongAddress(id))))
                .when(withOpContext((spec, opLog) -> {
                    var accountAddress = "0.0." + accountNum.get().value();
                    var spenderAddress = spenderNum.get();
                    allRunFor(
                            spec,
                            // call hbarApprove from EOA
                            contractCallWithFunctionAbi(
                                            accountAddress,
                                            getABIFor(
                                                    com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                    HBAR_APPROVE,
                                                    IHRC632),
                                            spenderAddress,
                                            BigInteger.valueOf(1_000_000L))
                                    .payingWith(ACCOUNT)
                                    .gas(1_000_000)
                                    .via(HBAR_APPROVE_TXN));
                }))
                .then(getTxnRecord(HBAR_APPROVE_TXN)
                        .logged()
                        .hasPriority(recordWith()
                                .status(SUCCESS)
                                .contractCallResult(resultWith()
                                        .resultThruAbi(
                                                getABIFor(
                                                        com.hedera.services.bdd.suites.contract.Utils.FunctionType
                                                                .FUNCTION,
                                                        HBAR_APPROVE,
                                                        IHRC632),
                                                isLiteralResult(new Object[] {Long.valueOf(22)})))));
    }

    @HapiTest
    final Stream<DynamicTest> hrc632AllowanceFromContract() {
        final AtomicReference<Address> accountNum = new AtomicReference<>();
        final AtomicReference<Address> spenderNum = new AtomicReference<>();

        return propertyPreservingHapiSpec("hrc632AllowanceFromContract")
                .preserving(CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_ENABLED)
                .given(
                        overriding(CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_ENABLED, "true"),
                        cryptoCreate(ACCOUNT)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .exposingCreatedIdTo(id -> accountNum.set(idAsHeadlongAddress(id))),
                        cryptoCreate(SPENDER).exposingCreatedIdTo(id -> spenderNum.set(idAsHeadlongAddress(id))),
                        uploadInitCode(HRC632_CONTRACT),
                        contractCreate(HRC632_CONTRACT),
                        cryptoApproveAllowance()
                                .addCryptoAllowance(ACCOUNT, SPENDER, 1_000_000)
                                .payingWith(ACCOUNT))
                .when(withOpContext((spec, opLog) -> {
                    var spenderAddress = spenderNum.get();
                    var ownerAddress = accountNum.get();
                    allRunFor(
                            spec,
                            // call hbarAllowance from Contract
                            contractCall(HRC632_CONTRACT, HBAR_ALLOWANCE_CALL, ownerAddress, spenderAddress)
                                    .payingWith(ACCOUNT)
                                    .gas(1_000_000)
                                    .via(HBAR_ALLOWANCE_TXN));
                }))
                .then(getTxnRecord(HBAR_ALLOWANCE_TXN)
                        .logged()
                        .hasPriority(recordWith()
                                .status(SUCCESS)
                                .contractCallResult(resultWith()
                                        .resultThruAbi(
                                                getABIFor(
                                                        com.hedera.services.bdd.suites.contract.Utils.FunctionType
                                                                .FUNCTION,
                                                        HBAR_ALLOWANCE_CALL,
                                                        HRC632_CONTRACT),
                                                isLiteralResult(
                                                        new Object[] {Long.valueOf(22), BigInteger.valueOf(1_000_000L)
                                                        })))));
    }

    @HapiTest
    final Stream<DynamicTest> hrc632ApproveFromContract() {
        final AtomicReference<Address> accountNum = new AtomicReference<>();
        final AtomicReference<Address> spenderNum = new AtomicReference<>();
        final AtomicReference<Address> contractNum = new AtomicReference<>();

        return propertyPreservingHapiSpec("hrc632FromContract")
                .preserving(CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_ENABLED)
                .given(
                        overriding(CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_ENABLED, "true"),
                        cryptoCreate(ACCOUNT)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .exposingCreatedIdTo(id -> accountNum.set(idAsHeadlongAddress(id))),
                        cryptoCreate(SPENDER).exposingCreatedIdTo(id -> spenderNum.set(idAsHeadlongAddress(id))),
                        uploadInitCode(HRC632_CONTRACT),
                        contractCreate(HRC632_CONTRACT),
                        getContractInfo(HRC632_CONTRACT)
                                .exposingEvmAddress(cb -> contractNum.set(asHeadlongAddress(cb))),
                        cryptoTransfer(tinyBarsFromTo(ACCOUNT, HRC632_CONTRACT, 1_000_000L)))
                .when(withOpContext((spec, opLog) -> {
                    // var accountAddress = "0.0." + accountNum.get().value();
                    var contractAddress = contractNum.get();
                    var spenderAddress = spenderNum.get();
                    allRunFor(
                            spec,
                            // call hbarApprove from contract
                            contractCall(
                                            HRC632_CONTRACT,
                                            HBAR_APPROVE_CALL,
                                            contractAddress,
                                            spenderAddress,
                                            BigInteger.valueOf(1_000_000L))
                                    .payingWith(ACCOUNT)
                                    .gas(1_000_000)
                                    .via(HBAR_APPROVE_TXN),
                            // call hbarAllowance from contract
                            contractCall(HRC632_CONTRACT, HBAR_ALLOWANCE_CALL, contractAddress, spenderAddress)
                                    .payingWith(ACCOUNT)
                                    .gas(1_000_000)
                                    .via(HBAR_ALLOWANCE_TXN));
                }))
                .then(
                        getTxnRecord(HBAR_APPROVE_TXN)
                                .logged()
                                .hasPriority(recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .resultThruAbi(
                                                        getABIFor(
                                                                com.hedera.services.bdd.suites.contract.Utils
                                                                        .FunctionType.FUNCTION,
                                                                HBAR_APPROVE_CALL,
                                                                HRC632_CONTRACT),
                                                        isLiteralResult(new Object[] {Long.valueOf(22)})))),
                        getTxnRecord(HBAR_ALLOWANCE_TXN)
                                .logged()
                                .hasPriority(recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .resultThruAbi(
                                                        getABIFor(
                                                                com.hedera.services.bdd.suites.contract.Utils
                                                                        .FunctionType.FUNCTION,
                                                                HBAR_ALLOWANCE_CALL,
                                                                HRC632_CONTRACT),
                                                        isLiteralResult(new Object[] {
                                                            Long.valueOf(22), BigInteger.valueOf(1_000_000L)
                                                        })))));
    }

    @HapiTest
    final Stream<DynamicTest> hrc632ApproveFromEOAFailsWhenWrongKey() {
        final AtomicReference<Address> accountNum = new AtomicReference<>();
        final AtomicReference<Address> spenderNum = new AtomicReference<>();

        return propertyPreservingHapiSpec("hrc632ApproveFromEOAFailsWhenWrongKey")
                .preserving(CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_ENABLED)
                .given(
                        overriding(CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_ENABLED, "true"),
                        cryptoCreate(SIGNER).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(ACCOUNT)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .exposingCreatedIdTo(id -> accountNum.set(idAsHeadlongAddress(id))),
                        cryptoCreate(SPENDER).exposingCreatedIdTo(id -> spenderNum.set(idAsHeadlongAddress(id))))
                .when(withOpContext((spec, opLog) -> {
                    var accountAddress = "0.0." + accountNum.get().value();
                    var spenderAddress = spenderNum.get();
                    allRunFor(
                            spec,
                            // call hbarApprove from EOA with wrong payer.  should fail
                            contractCallWithFunctionAbi(
                                            accountAddress,
                                            getABIFor(
                                                    com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                    HBAR_APPROVE,
                                                    IHRC632),
                                            spenderAddress,
                                            BigInteger.valueOf(1_000_000L))
                                    .payingWith(SIGNER)
                                    .gas(1_000_000)
                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                    .via(HBAR_APPROVE_TXN));
                }))
                .then(getTxnRecord(HBAR_APPROVE_TXN)
                        .logged()
                        .hasPriority(recordWith().status(CONTRACT_REVERT_EXECUTED)));
    }

    @HapiTest
    final Stream<DynamicTest> hrc632ApproveFromContractFailsWhenNotOwner() {
        final AtomicReference<Address> accountNum = new AtomicReference<>();
        final AtomicReference<Address> spenderNum = new AtomicReference<>();
        final AtomicReference<Address> contractNum = new AtomicReference<>();

        return propertyPreservingHapiSpec("hrc632FromContract")
                .preserving(CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_ENABLED)
                .given(
                        overriding(CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_ENABLED, "true"),
                        cryptoCreate(ACCOUNT)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .exposingCreatedIdTo(id -> accountNum.set(idAsHeadlongAddress(id))),
                        cryptoCreate(SPENDER).exposingCreatedIdTo(id -> spenderNum.set(idAsHeadlongAddress(id))),
                        uploadInitCode(HRC632_CONTRACT),
                        contractCreate(HRC632_CONTRACT),
                        getContractInfo(HRC632_CONTRACT)
                                .exposingEvmAddress(cb -> contractNum.set(asHeadlongAddress(cb))),
                        cryptoTransfer(tinyBarsFromTo(ACCOUNT, HRC632_CONTRACT, 1_000_000L)))
                .when(withOpContext((spec, opLog) -> {
                    var accountAddress = accountNum.get();
                    var spenderAddress = spenderNum.get();
                    allRunFor(
                            spec,
                            // try calling hbarApprove from contract with account as owner.  should fail
                            contractCall(
                                            HRC632_CONTRACT,
                                            HBAR_APPROVE_CALL,
                                            accountAddress,
                                            spenderAddress,
                                            BigInteger.valueOf(1_000_000L))
                                    .payingWith(ACCOUNT)
                                    .gas(1_000_000)
                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                    .via(HBAR_APPROVE_TXN),
                            // call hbarAllowance from contract.  should succeed but the allowance should be 0
                            contractCall(HRC632_CONTRACT, HBAR_ALLOWANCE_CALL, accountAddress, spenderAddress)
                                    .payingWith(ACCOUNT)
                                    .gas(1_000_000)
                                    .via(HBAR_ALLOWANCE_TXN));
                }))
                .then(
                        getTxnRecord(HBAR_APPROVE_TXN)
                                .logged()
                                .hasPriority(recordWith().status(CONTRACT_REVERT_EXECUTED)),
                        getTxnRecord(HBAR_ALLOWANCE_TXN)
                                .logged()
                                .hasPriority(recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .resultThruAbi(
                                                        getABIFor(
                                                                com.hedera.services.bdd.suites.contract.Utils
                                                                        .FunctionType.FUNCTION,
                                                                HBAR_ALLOWANCE_CALL,
                                                                HRC632_CONTRACT),
                                                        isLiteralResult(
                                                                new Object[] {Long.valueOf(22), BigInteger.valueOf(0)
                                                                })))));
    }
}

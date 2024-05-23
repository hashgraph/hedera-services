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
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_FUNCTION_PARAMETERS;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.contract.Utils.mirrorAddrWith;
import static com.hedera.services.bdd.suites.contract.evm.Evm46ValidationSuite.systemAccounts;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts;
import java.math.BigInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
public class ExtCodeSizeOperationSuite {
    @SuppressWarnings("java:S5960")
    @HapiTest
    final Stream<DynamicTest> verifiesExistence() {
        final var contract = "ExtCodeOperationsChecker";
        final var invalidAddress = "0x0000000000000000000000000000000000123456";
        final var sizeOf = "sizeOf";

        final var account = "account";
        return defaultHapiSpec("VerifiesExistence", NONDETERMINISTIC_FUNCTION_PARAMETERS)
                .given(uploadInitCode(contract), contractCreate(contract), cryptoCreate(account))
                .when()
                .then(
                        contractCall(contract, sizeOf, asHeadlongAddress(invalidAddress))
                                .hasKnownStatus(SUCCESS),
                        contractCallLocal(contract, sizeOf, asHeadlongAddress(invalidAddress))
                                .hasAnswerOnlyPrecheck(OK),
                        withOpContext((spec, opLog) -> {
                            final var accountID = spec.registry().getAccountID(account);
                            final var contractID = spec.registry().getContractId(contract);
                            final var accountSolidityAddress = asHexedSolidityAddress(accountID);
                            final var contractAddress = asHexedSolidityAddress(contractID);

                            final var call = contractCall(contract, sizeOf, asHeadlongAddress(accountSolidityAddress))
                                    .via("callRecord");

                            final var callRecord = getTxnRecord("callRecord")
                                    .hasPriority(recordWith()
                                            .contractCallResult(resultWith()
                                                    .resultThruAbi(
                                                            getABIFor(FUNCTION, sizeOf, contract),
                                                            isLiteralResult(new Object[] {BigInteger.valueOf(0)}))));

                            final var accountCodeSizeCallLocal = contractCallLocal(
                                            contract, sizeOf, asHeadlongAddress(accountSolidityAddress))
                                    .has(ContractFnResultAsserts.resultWith()
                                            .resultThruAbi(
                                                    getABIFor(FUNCTION, sizeOf, contract),
                                                    ContractFnResultAsserts.isLiteralResult(
                                                            new Object[] {BigInteger.valueOf(0)})));

                            final var getBytecode =
                                    getContractBytecode(contract).saveResultTo("contractBytecode");

                            final var contractCodeSize = contractCallLocal(
                                            contract, sizeOf, asHeadlongAddress(contractAddress))
                                    .saveResultTo("contractCodeSize");

                            allRunFor(spec, call, callRecord, accountCodeSizeCallLocal, getBytecode, contractCodeSize);

                            final var contractCodeSizeResult = spec.registry().getBytes("contractCodeSize");
                            final var contractBytecode = spec.registry().getBytes("contractBytecode");

                            Assertions.assertEquals(
                                    BigInteger.valueOf(contractBytecode.length),
                                    new BigInteger(contractCodeSizeResult));
                        }));
    }

    @HapiTest
    final Stream<DynamicTest> testExtCodeSizeWithSystemAccounts() {
        final var contract = "ExtCodeOperationsChecker";
        final var sizeOf = "sizeOf";
        final var account = "account";
        final var opsArray = new HapiSpecOperation[systemAccounts.size() * 2];

        for (int i = 0; i < systemAccounts.size(); i++) {
            // add contract call for all accounts in the list
            opsArray[i] = contractCall(contract, sizeOf, mirrorAddrWith(systemAccounts.get(i)))
                    .hasKnownStatus(SUCCESS);

            // add contract call local for all accounts in the list
            opsArray[systemAccounts.size() + i] = contractCallLocal(
                            contract, sizeOf, mirrorAddrWith(systemAccounts.get(i)))
                    .has(ContractFnResultAsserts.resultWith()
                            .resultThruAbi(
                                    getABIFor(FUNCTION, sizeOf, contract),
                                    ContractFnResultAsserts.isLiteralResult(new Object[] {BigInteger.valueOf(0L)})));
        }

        return defaultHapiSpec("testExtCodeSizeWithSystemAccounts", NONDETERMINISTIC_FUNCTION_PARAMETERS)
                .given(uploadInitCode(contract), contractCreate(contract), cryptoCreate(account))
                .when()
                .then(opsArray);
    }
}

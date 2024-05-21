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
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.FULLY_NONDETERMINISTIC;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_FUNCTION_PARAMETERS;
import static com.hedera.services.bdd.suites.contract.Utils.mirrorAddrWith;
import static com.hedera.services.bdd.suites.contract.evm.Evm46ValidationSuite.systemAccounts;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
public class ExtCodeCopyOperationSuite {
    @SuppressWarnings("java:S5960")
    @HapiTest
    final Stream<DynamicTest> verifiesExistence() {
        final var contract = "ExtCodeOperationsChecker";
        final var invalidAddress = "0x0000000000000000000000000000000000123456";
        final var emptyBytecode = ByteString.EMPTY;
        final var codeCopyOf = "codeCopyOf";
        final var account = "account";

        return defaultHapiSpec("VerifiesExistence", FULLY_NONDETERMINISTIC)
                .given(cryptoCreate(account), uploadInitCode(contract), contractCreate(contract))
                .when()
                .then(
                        contractCall(contract, codeCopyOf, asHeadlongAddress(invalidAddress))
                                .hasKnownStatus(SUCCESS),
                        contractCallLocal(contract, codeCopyOf, asHeadlongAddress(invalidAddress))
                                .hasAnswerOnlyPrecheck(OK),
                        withOpContext((spec, opLog) -> {
                            final var accountID = spec.registry().getAccountID(account);
                            final var contractID = spec.registry().getContractId(contract);
                            final var accountSolidityAddress = asHexedSolidityAddress(accountID);
                            final var contractAddress = asHexedSolidityAddress(contractID);

                            final var call = contractCall(
                                            contract, codeCopyOf, asHeadlongAddress(accountSolidityAddress))
                                    .via("callRecord");
                            final var callRecord = getTxnRecord("callRecord");

                            final var accountCodeCallLocal = contractCallLocal(
                                            contract, codeCopyOf, asHeadlongAddress(accountSolidityAddress))
                                    .saveResultTo("accountCode");

                            final var contractCodeCallLocal = contractCallLocal(
                                            contract, codeCopyOf, asHeadlongAddress(contractAddress))
                                    .saveResultTo("contractCode");

                            final var getBytecodeCall =
                                    getContractBytecode(contract).saveResultTo("contractGetBytecode");

                            allRunFor(
                                    spec,
                                    call,
                                    callRecord,
                                    accountCodeCallLocal,
                                    contractCodeCallLocal,
                                    getBytecodeCall);

                            final var recordResult =
                                    callRecord.getResponseRecord().getContractCallResult();
                            final var accountCode = spec.registry().getBytes("accountCode");
                            final var contractCode = spec.registry().getBytes("contractCode");
                            final var getBytecode = spec.registry().getBytes("contractGetBytecode");

                            Assertions.assertEquals(emptyBytecode, recordResult.getContractCallResult());
                            Assertions.assertArrayEquals(emptyBytecode.toByteArray(), accountCode);
                            Assertions.assertArrayEquals(getBytecode, contractCode);
                        }));
    }

    @HapiTest
    final Stream<DynamicTest> testExtCodeCopyWithSystemAccounts() {
        final var contract = "ExtCodeOperationsChecker";
        final var emptyBytecode = ByteString.EMPTY;
        final var codeCopyOf = "codeCopyOf";
        final var account = "account";
        final HapiSpecOperation[] opsArray = new HapiSpecOperation[systemAccounts.size() * 2];

        for (int i = 0; i < systemAccounts.size(); i++) {
            // add contract call for all accounts in the list
            opsArray[i] = contractCall(contract, codeCopyOf, mirrorAddrWith(systemAccounts.get(i)))
                    .hasKnownStatus(SUCCESS);

            // add contract call local for all accounts in the list
            int finalI = i;
            opsArray[systemAccounts.size() + i] = withOpContext((spec, opLog) -> {
                final var accountSolidityAddress = mirrorAddrWith(systemAccounts.get(finalI));

                final var accountCodeCallLocal = contractCallLocal(contract, codeCopyOf, accountSolidityAddress)
                        .saveResultTo("accountCode");
                allRunFor(spec, accountCodeCallLocal);

                final var accountCode = spec.registry().getBytes("accountCode");

                Assertions.assertArrayEquals(emptyBytecode.toByteArray(), accountCode);
            });
        }

        return defaultHapiSpec("testExtCodeCopyWithSystemAccounts", NONDETERMINISTIC_FUNCTION_PARAMETERS)
                .given(uploadInitCode(contract), contractCreate(contract), cryptoCreate(account))
                .when()
                .then(opsArray);
    }
}

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

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts;
import com.hedera.services.bdd.suites.HapiApiSuite;
import java.math.BigInteger;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BalanceOperationSuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(BalanceOperationSuite.class);

    public static void main(String[] args) {
        new BalanceOperationSuite().runSuiteAsync();
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(new HapiApiSpec[] {verifiesExistenceOfAccountsAndContracts()});
    }

    HapiApiSpec verifiesExistenceOfAccountsAndContracts() {
        final var contract = "BalanceChecker";
        final var BALANCE = 10L;
        final var ACCOUNT = "test";
        final var INVALID_ADDRESS = "0x0000000000000000000000000000000000123456";

        return defaultHapiSpec("VerifiesExistenceOfAccountsAndContracts")
                .given(
                        cryptoCreate("test").balance(BALANCE),
                        uploadInitCode(contract),
                        contractCreate(contract))
                .when()
                .then(
                        contractCall(contract, "balanceOf", INVALID_ADDRESS)
                                .hasKnownStatus(INVALID_SOLIDITY_ADDRESS),
                        contractCallLocal(contract, "balanceOf", INVALID_ADDRESS)
                                .hasAnswerOnlyPrecheck(INVALID_SOLIDITY_ADDRESS),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var id = spec.registry().getAccountID(ACCOUNT);
                                    final var contractID = spec.registry().getContractId(contract);
                                    final var solidityAddress = asHexedSolidityAddress(id);
                                    final var contractAddress = asHexedSolidityAddress(contractID);

                                    final var call =
                                            contractCall(contract, "balanceOf", solidityAddress)
                                                    .via("callRecord");

                                    final var callRecord =
                                            getTxnRecord("callRecord")
                                                    .hasPriority(
                                                            recordWith()
                                                                    .contractCallResult(
                                                                            resultWith()
                                                                                    .resultThruAbi(
                                                                                            getABIFor(
                                                                                                    FUNCTION,
                                                                                                    "balanceOf",
                                                                                                    contract),
                                                                                            isLiteralResult(
                                                                                                    new Object
                                                                                                            [] {
                                                                                                        BigInteger
                                                                                                                .valueOf(
                                                                                                                        BALANCE)
                                                                                                    }))));

                                    final var callLocal =
                                            contractCallLocal(
                                                            contract, "balanceOf", solidityAddress)
                                                    .has(
                                                            ContractFnResultAsserts.resultWith()
                                                                    .resultThruAbi(
                                                                            getABIFor(
                                                                                    FUNCTION,
                                                                                    "balanceOf",
                                                                                    contract),
                                                                            ContractFnResultAsserts
                                                                                    .isLiteralResult(
                                                                                            new Object
                                                                                                    [] {
                                                                                                BigInteger
                                                                                                        .valueOf(
                                                                                                                BALANCE)
                                                                                            })));

                                    final var contractCallLocal =
                                            contractCallLocal(
                                                            contract, "balanceOf", contractAddress)
                                                    .has(
                                                            ContractFnResultAsserts.resultWith()
                                                                    .resultThruAbi(
                                                                            getABIFor(
                                                                                    FUNCTION,
                                                                                    "balanceOf",
                                                                                    contract),
                                                                            ContractFnResultAsserts
                                                                                    .isLiteralResult(
                                                                                            new Object
                                                                                                    [] {
                                                                                                BigInteger
                                                                                                        .valueOf(
                                                                                                                0)
                                                                                            })));

                                    allRunFor(spec, call, callLocal, callRecord, contractCallLocal);
                                }));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}

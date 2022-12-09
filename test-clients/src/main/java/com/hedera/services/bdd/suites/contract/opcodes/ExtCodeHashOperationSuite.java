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

import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.crypto.Hash;
import org.junit.jupiter.api.Assertions;

public class ExtCodeHashOperationSuite extends HapiSuite {
    private static final Logger LOG = LogManager.getLogger(ExtCodeHashOperationSuite.class);

    public static void main(String[] args) {
        new ExtCodeHashOperationSuite().runSuiteAsync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(verifiesExistence());
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @SuppressWarnings("java:S5960")
    HapiSpec verifiesExistence() {
        final var contract = "ExtCodeOperationsChecker";
        final var invalidAddress = "0x0000000000000000000000000000000000123456";
        final var expectedAccountHash = ByteString.copyFrom(Hash.keccak256(Bytes.EMPTY).toArray());
        final var hashOf = "hashOf";

        return defaultHapiSpec("VerifiesExistence")
                .given(uploadInitCode(contract), contractCreate(contract))
                .when()
                .then(
                        contractCall(contract, hashOf, asHeadlongAddress(invalidAddress))
                                .hasKnownStatus(INVALID_SOLIDITY_ADDRESS),
                        contractCallLocal(contract, hashOf, asHeadlongAddress(invalidAddress))
                                .hasAnswerOnlyPrecheck(INVALID_SOLIDITY_ADDRESS),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var accountID =
                                            spec.registry().getAccountID(DEFAULT_PAYER);
                                    final var contractID = spec.registry().getContractId(contract);
                                    final var accountSolidityAddress =
                                            asHexedSolidityAddress(accountID);
                                    final var contractAddress = asHexedSolidityAddress(contractID);

                                    final var call =
                                            contractCall(
                                                            contract,
                                                            hashOf,
                                                            asHeadlongAddress(
                                                                    accountSolidityAddress))
                                                    .via("callRecord");
                                    final var callRecord = getTxnRecord("callRecord");

                                    final var accountCodeHashCallLocal =
                                            contractCallLocal(
                                                            contract,
                                                            hashOf,
                                                            asHeadlongAddress(
                                                                    accountSolidityAddress))
                                                    .saveResultTo("accountCodeHash");

                                    final var contractCodeHash =
                                            contractCallLocal(
                                                            contract,
                                                            hashOf,
                                                            asHeadlongAddress(contractAddress))
                                                    .saveResultTo("contractCodeHash");

                                    final var getBytecode =
                                            getContractBytecode(contract)
                                                    .saveResultTo("contractBytecode");

                                    allRunFor(
                                            spec,
                                            call,
                                            callRecord,
                                            accountCodeHashCallLocal,
                                            contractCodeHash,
                                            getBytecode);

                                    final var recordResult =
                                            callRecord.getResponseRecord().getContractCallResult();
                                    final var accountCodeHash =
                                            spec.registry().getBytes("accountCodeHash");

                                    final var contractCodeResult =
                                            spec.registry().getBytes("contractCodeHash");
                                    final var contractBytecode =
                                            spec.registry().getBytes("contractBytecode");
                                    final var expectedContractCodeHash =
                                            ByteString.copyFrom(
                                                            Hash.keccak256(
                                                                            Bytes.of(
                                                                                    contractBytecode))
                                                                    .toArray())
                                                    .toByteArray();

                                    Assertions.assertEquals(
                                            expectedAccountHash,
                                            recordResult.getContractCallResult());
                                    Assertions.assertArrayEquals(
                                            expectedAccountHash.toByteArray(), accountCodeHash);
                                    Assertions.assertArrayEquals(
                                            expectedContractCodeHash, contractCodeResult);
                                }));
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }
}

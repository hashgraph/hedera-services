/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isRandomResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PrngPrecompileSuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(PrngPrecompileSuite.class);
    private static final long GAS_TO_OFFER = 400_000L;
    private static final String THE_GRACEFULLY_FAILING_CONTRACT = "GracefullyFailingPrng";

    public static void main(String... args) {
        new PrngPrecompileSuite().runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return allOf(positiveSpecs(), negativeSpecs());
    }

    List<HapiApiSpec> negativeSpecs() {
        return List.of(
                functionCallWithLessThanFourBytesFailsGracefully(),
                nonSupportedAbiCallGracefullyFails(),
                invalidLargeInputFails(),
                emptyInputCallFails());
    }

    List<HapiApiSpec> positiveSpecs() {
        return List.of(prngPrecompileHappyPathWorks());
    }

    private HapiApiSpec emptyInputCallFails() {
        final var prng = "GracefullyFailingPrng";
        return defaultHapiSpec("emptyInputCallFails")
                .given(cryptoCreate("bob"), uploadInitCode(prng), contractCreate(prng))
                .when(
                        sourcing(
                                () ->
                                        contractCall(prng, "performEmptyInputCall")
                                                .gas(GAS_TO_OFFER)
                                                .payingWith("bob")
                                                .via("emptyInputCall")
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                .logged()))
                .then(
                        getTxnRecord("emptyInputCall")
                                .andAllChildRecords()
                                .logged()
                                .saveTxnRecordToRegistry("emptyInputCall"),
                        withOpContext(
                                (spec, ignore) -> {
                                    final var gasUsed =
                                            spec.registry()
                                                    .getTransactionRecord("emptyInputCall")
                                                    .getContractCallResult()
                                                    .getGasUsed();
                                    assertEquals(394213, gasUsed);
                                }));
    }

    private HapiApiSpec invalidLargeInputFails() {
        final var prng = "GracefullyFailingPrng";
        return defaultHapiSpec("invalidLargeInputFails")
                .given(cryptoCreate("bob"), uploadInitCode(prng), contractCreate(prng))
                .when(
                        sourcing(
                                () ->
                                        contractCall(prng, "performLargeInputCall")
                                                .gas(GAS_TO_OFFER)
                                                .payingWith("bob")
                                                .via("largeInputCall")
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                .logged()))
                .then(
                        getTxnRecord("largeInputCall")
                                .andAllChildRecords()
                                .logged()
                                .saveTxnRecordToRegistry("largeInputCall"),
                        withOpContext(
                                (spec, ignore) -> {
                                    final var gasUsed =
                                            spec.registry()
                                                    .getTransactionRecord("largeInputCall")
                                                    .getContractCallResult()
                                                    .getGasUsed();
                                    assertEquals(394244, gasUsed);
                                }));
    }

    private HapiApiSpec nonSupportedAbiCallGracefullyFails() {
        final var prng = "GracefullyFailingPrng";
        return defaultHapiSpec("nonSupportedAbiCallGracefullyFails")
                .given(cryptoCreate("bob"), uploadInitCode(prng), contractCreate(prng))
                .when(
                        sourcing(
                                () ->
                                        contractCall(prng, "performNonExistingServiceFunctionCall")
                                                .gas(GAS_TO_OFFER)
                                                .payingWith("bob")
                                                .via("failedCall")
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                .logged()))
                .then(
                        getTxnRecord("failedCall")
                                .andAllChildRecords()
                                .logged()
                                .saveTxnRecordToRegistry("failedCall"),
                        withOpContext(
                                (spec, ignore) -> {
                                    final var gasUsed =
                                            spec.registry()
                                                    .getTransactionRecord("failedCall")
                                                    .getContractCallResult()
                                                    .getGasUsed();
                                    assertEquals(394209, gasUsed);
                                }));
    }

    private HapiApiSpec functionCallWithLessThanFourBytesFailsGracefully() {
        return defaultHapiSpec("functionCallWithLessThanFourBytesFailsGracefully")
                .given(
                        cryptoCreate("bob"),
                        uploadInitCode(THE_GRACEFULLY_FAILING_CONTRACT),
                        contractCreate(THE_GRACEFULLY_FAILING_CONTRACT))
                .when(
                        sourcing(
                                () ->
                                        contractCall(
                                                        THE_GRACEFULLY_FAILING_CONTRACT,
                                                        "performLessThanFourBytesFunctionCall")
                                                .gas(GAS_TO_OFFER)
                                                .payingWith("bob")
                                                .via("lessThan4Bytes")
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                .logged()),
                        getTxnRecord("lessThan4Bytes")
                                .andAllChildRecords()
                                .logged()
                                .saveTxnRecordToRegistry("lessThan4Bytes"))
                .then(
                        withOpContext(
                                (spec, ignore) -> {
                                    final var gasUsed =
                                            spec.registry()
                                                    .getTransactionRecord("lessThan4Bytes")
                                                    .getContractCallResult()
                                                    .getGasUsed();
                                    assertEquals(394214, gasUsed);
                                }));
    }

    private HapiApiSpec prngPrecompileHappyPathWorks() {
        final var prng = "PrngSystemContract";
        return defaultHapiSpec("prngPrecompileHappyPathWorks")
                .given(cryptoCreate("bob"), uploadInitCode(prng), contractCreate(prng))
                .when(
                        sourcing(
                                () ->
                                        contractCall(prng, "getPseudorandomSeed")
                                                .gas(GAS_TO_OFFER)
                                                .payingWith("bob")
                                                .via("randomBits")
                                                .logged()))
                .then(
                        getTxnRecord("randomBits")
                                .andAllChildRecords()
                                .hasChildRecordCount(1)
                                .hasChildRecords(
                                        recordWith()
                                                .pseudoRandomBytes()
                                                .contractCallResult(
                                                        resultWith()
                                                                .resultViaFunctionName(
                                                                        "getPseudorandomSeed",
                                                                        prng,
                                                                        isRandomResult(
                                                                                new Object[] {
                                                                                    new byte[32]
                                                                                }))))
                                .logged());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}

/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isRandomResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.swirlds.common.utility.CommonUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class PrngPrecompileSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(PrngPrecompileSuite.class);
    private static final long GAS_TO_OFFER = 400_000L;
    private static final String THE_GRACEFULLY_FAILING_PRNG_CONTRACT = "GracefullyFailingPrng";
    private static final String THE_PRNG_CONTRACT = "PrngSystemContract";
    private static final String BOB = "bob";

    private static final String GET_SEED = "getPseudorandomSeed";
    private static final String EXPLICIT_LARGE_PARAMS =
            "d83bf9a10000000000000000000000d83bf9a10000d83bf9a1000d83bf9a10000000d83bf9a108000d83bf9a100000d83bf9a1000000"
                + "0000d83bf9a100000d83bf9a1000000d83bf9a100339000000d83bf9a1000000000123456789012345678901234"
                + "5678901234567890000000000000000000000000123456789012345678901234567890123456789000000000"
                + "000d83bf9a1083bf9a1000d83bf9a10000000d83bf9a10000000000000000026e790000000000000000000000000000"
                + "00000000d83bf9a1000000000d83bf9a1000";

    public static void main(String... args) {
        new PrngPrecompileSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return allOf(positiveSpecs(), negativeSpecs());
    }

    List<HapiSpec> negativeSpecs() {
        return List.of(
                functionCallWithLessThanFourBytesFailsGracefully(),
                nonSupportedAbiCallGracefullyFails(),
                invalidLargeInputFails(),
                emptyInputCallFails());
    }

    List<HapiSpec> positiveSpecs() {
        return List.of(prngPrecompileHappyPathWorks(), multipleCallsHaveIndependentResults());
    }

    private HapiSpec multipleCallsHaveIndependentResults() {
        final var prng = THE_PRNG_CONTRACT;
        final var gasToOffer = 400_000;
        final var numCalls = 5;
        final List<String> prngSeeds = new ArrayList<>();
        return defaultHapiSpec("MultipleCallsHaveIndependentResults")
                .given(uploadInitCode(prng), contractCreate(prng))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    for (int i = 0; i < numCalls; i++) {
                                        final var txn = "call" + i;
                                        final var call =
                                                contractCall(prng, GET_SEED)
                                                        .gas(gasToOffer)
                                                        .via(txn);
                                        final var lookup = getTxnRecord(txn).andAllChildRecords();
                                        allRunFor(spec, call, lookup);
                                        final var response = lookup.getResponseRecord();
                                        final var rawResult =
                                                response.getContractCallResult()
                                                        .getContractCallResult()
                                                        .toByteArray();
                                        // Since this contract returns the result of the Prng system
                                        // contract, its call result
                                        // should be identical to the result of the system contract
                                        // in the child record
                                        for (final var child : lookup.getChildRecords()) {
                                            if (child.hasContractCallResult()) {
                                                assertArrayEquals(
                                                        rawResult,
                                                        child.getContractCallResult()
                                                                .getContractCallResult()
                                                                .toByteArray());
                                            }
                                        }
                                        prngSeeds.add(CommonUtils.hex(rawResult));
                                    }
                                    opLog.info("Got prng seeds  : {}", prngSeeds);
                                    assertEquals(
                                            prngSeeds.size(),
                                            new HashSet<>(prngSeeds).size(),
                                            "An N-3 running hash was repeated, which is"
                                                    + " inconceivable");
                                }))
                .then(
                        // It's possible to call these contracts in a static context with no issues
                        contractCallLocal(prng, GET_SEED).gas(gasToOffer));
    }

    private HapiSpec emptyInputCallFails() {
        final var prng = THE_PRNG_CONTRACT;
        final var emptyInputCall = "emptyInputCall";
        return defaultHapiSpec("emptyInputCallFails")
                .given(cryptoCreate(BOB), uploadInitCode(prng), contractCreate(prng))
                .when(
                        sourcing(
                                () ->
                                        contractCall(prng, GET_SEED)
                                                .withExplicitParams(
                                                        () ->
                                                                CommonUtils.hex(
                                                                        Bytes.fromBase64String("")
                                                                                .toArray()))
                                                .gas(GAS_TO_OFFER)
                                                .payingWith(BOB)
                                                .via(emptyInputCall)
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)))
                .then(
                        getTxnRecord(emptyInputCall)
                                .andAllChildRecords()
                                .saveTxnRecordToRegistry(emptyInputCall),
                        withOpContext(
                                (spec, ignore) -> {
                                    final var gasUsed =
                                            spec.registry()
                                                    .getTransactionRecord(emptyInputCall)
                                                    .getContractCallResult()
                                                    .getGasUsed();
                                    assertEquals(320000, gasUsed);
                                }));
    }

    private HapiSpec invalidLargeInputFails() {
        final var prng = THE_PRNG_CONTRACT;
        final var largeInputCall = "largeInputCall";
        return defaultHapiSpec("invalidLargeInputFails")
                .given(cryptoCreate(BOB), uploadInitCode(prng), contractCreate(prng))
                .when(
                        sourcing(
                                () ->
                                        contractCall(prng, GET_SEED)
                                                .withExplicitParams(
                                                        () ->
                                                                CommonUtils.hex(
                                                                        Bytes.fromBase64String(
                                                                                        EXPLICIT_LARGE_PARAMS)
                                                                                .toArray()))
                                                .gas(GAS_TO_OFFER)
                                                .payingWith(BOB)
                                                .via(largeInputCall)
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)))
                .then(
                        getTxnRecord(largeInputCall)
                                .andAllChildRecords()
                                .saveTxnRecordToRegistry(largeInputCall),
                        withOpContext(
                                (spec, ignore) -> {
                                    final var gasUsed =
                                            spec.registry()
                                                    .getTransactionRecord(largeInputCall)
                                                    .getContractCallResult()
                                                    .getGasUsed();
                                    assertEquals(320000, gasUsed);
                                }));
    }

    private HapiSpec nonSupportedAbiCallGracefullyFails() {
        final var prng = THE_GRACEFULLY_FAILING_PRNG_CONTRACT;
        final var failedCall = "failedCall";
        return defaultHapiSpec("nonSupportedAbiCallGracefullyFails")
                .given(cryptoCreate(BOB), uploadInitCode(prng), contractCreate(prng))
                .when(
                        sourcing(
                                () ->
                                        contractCall(prng, "performNonExistingServiceFunctionCall")
                                                .gas(GAS_TO_OFFER)
                                                .payingWith(BOB)
                                                .via(failedCall)
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)))
                .then(
                        getTxnRecord(failedCall)
                                .andAllChildRecords()
                                .saveTxnRecordToRegistry(failedCall),
                        withOpContext(
                                (spec, ignore) -> {
                                    final var gasUsed =
                                            spec.registry()
                                                    .getTransactionRecord(failedCall)
                                                    .getContractCallResult()
                                                    .getGasUsed();
                                    assertEquals(394209, gasUsed);
                                }));
    }

    private HapiSpec functionCallWithLessThanFourBytesFailsGracefully() {
        final var lessThan4Bytes = "lessThan4Bytes";
        return defaultHapiSpec("functionCallWithLessThanFourBytesFailsGracefully")
                .given(
                        cryptoCreate(BOB),
                        uploadInitCode(THE_PRNG_CONTRACT),
                        contractCreate(THE_PRNG_CONTRACT))
                .when(
                        sourcing(
                                () ->
                                        contractCall(THE_PRNG_CONTRACT, GET_SEED)
                                                .withExplicitParams(
                                                        () ->
                                                                CommonUtils.hex(
                                                                        Bytes.of(
                                                                                        (byte) 0xab,
                                                                                        (byte) 0xab,
                                                                                        (byte) 0xab)
                                                                                .toArray()))
                                                .gas(GAS_TO_OFFER)
                                                .payingWith(BOB)
                                                .via(lessThan4Bytes)
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        getTxnRecord(lessThan4Bytes)
                                .andAllChildRecords()
                                .saveTxnRecordToRegistry(lessThan4Bytes))
                .then(
                        withOpContext(
                                (spec, ignore) -> {
                                    final var gasUsed =
                                            spec.registry()
                                                    .getTransactionRecord(lessThan4Bytes)
                                                    .getContractCallResult()
                                                    .getGasUsed();
                                    assertEquals(320000, gasUsed);
                                }));
    }

    private HapiSpec prngPrecompileHappyPathWorks() {
        final var prng = THE_PRNG_CONTRACT;
        final var randomBits = "randomBits";
        return defaultHapiSpec("prngPrecompileHappyPathWorks")
                .given(cryptoCreate(BOB), uploadInitCode(prng), contractCreate(prng))
                .when(
                        sourcing(
                                () ->
                                        contractCall(prng, GET_SEED)
                                                .gas(GAS_TO_OFFER)
                                                .payingWith(BOB)
                                                .via(randomBits)))
                .then(
                        getTxnRecord(randomBits)
                                .andAllChildRecords()
                                .hasChildRecordCount(1)
                                .hasChildRecords(
                                        recordWith()
                                                .pseudoRandomBytes()
                                                .contractCallResult(
                                                        resultWith()
                                                                .resultViaFunctionName(
                                                                        GET_SEED,
                                                                        prng,
                                                                        isRandomResult(
                                                                                new Object[] {
                                                                                    new byte[32]
                                                                                })))));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}

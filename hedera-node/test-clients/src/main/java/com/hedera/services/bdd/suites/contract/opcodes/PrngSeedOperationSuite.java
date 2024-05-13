/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
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
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_CONTRACT_CALL_RESULTS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_TRANSACTION_FEES;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.suites.HapiSuite;
import com.swirlds.common.utility.CommonUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@HapiTestSuite(fuzzyMatch = true)
@Tag(SMART_CONTRACT)
public class PrngSeedOperationSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(PrngSeedOperationSuite.class);
    private static final long GAS_TO_OFFER = 400_000L;
    private static final String THE_PRNG_CONTRACT = "PrngSeedOperationContract";
    private static final String BOB = "bob";

    private static final String GET_SEED = "getPseudorandomSeed";

    public static final String CONTRACTS_DYNAMIC_EVM_VERSION = "contracts.evm.version.dynamic";
    public static final String CONTRACTS_EVM_VERSION = "contracts.evm.version";

    public static final String EVM_VERSION_0_34 = "v0.34";
    public static final String EVM_VERSION_0_30 = "v0.30";

    public static void main(String... args) {
        new PrngSeedOperationSuite().runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return allOf(positiveSpecs(), negativeSpecs());
    }

    List<Stream<DynamicTest>> negativeSpecs() {
        return List.of();
    }

    List<Stream<DynamicTest>> positiveSpecs() {
        return List.of(
                prngPrecompileHappyPathWorks(), multipleCallsHaveIndependentResults(), prngPrecompileDisabledInV030());
    }

    @HapiTest
    final Stream<DynamicTest> multipleCallsHaveIndependentResults() {
        final var prng = THE_PRNG_CONTRACT;
        final var gasToOffer = 400_000;
        final var numCalls = 5;
        final List<String> prngSeeds = new ArrayList<>();
        return propertyPreservingHapiSpec(
                        "MultipleCallsHaveIndependentResults",
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS,
                        NONDETERMINISTIC_TRANSACTION_FEES)
                .preserving(CONTRACTS_DYNAMIC_EVM_VERSION, CONTRACTS_EVM_VERSION)
                .given(
                        uploadInitCode(prng),
                        contractCreate(prng),
                        overriding(CONTRACTS_DYNAMIC_EVM_VERSION, TRUE_VALUE),
                        overriding(CONTRACTS_EVM_VERSION, EVM_VERSION_0_34))
                .when(withOpContext((spec, opLog) -> {
                    for (int i = 0; i < numCalls; i++) {
                        final var txn = "call" + i;
                        final var call =
                                contractCall(prng, GET_SEED).gas(gasToOffer).via(txn);
                        final var lookup = getTxnRecord(txn).andAllChildRecords();
                        allRunFor(spec, call, lookup);
                        final var response = lookup.getResponseRecord();
                        final var rawResult = response.getContractCallResult()
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
                            "An N-3 running hash was repeated, which is" + " inconceivable");
                }))
                .then(
                        // It's possible to call these contracts in a static context with no issues
                        contractCallLocal(prng, GET_SEED).gas(gasToOffer));
    }

    @HapiTest
    final Stream<DynamicTest> prngPrecompileHappyPathWorks() {
        final var prng = THE_PRNG_CONTRACT;
        final var randomBits = "randomBits";
        return propertyPreservingHapiSpec(
                        "prngPrecompileHappyPathWorks",
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS,
                        NONDETERMINISTIC_TRANSACTION_FEES)
                .preserving(CONTRACTS_DYNAMIC_EVM_VERSION, CONTRACTS_EVM_VERSION)
                .given(
                        overriding(CONTRACTS_DYNAMIC_EVM_VERSION, TRUE_VALUE),
                        overriding(CONTRACTS_EVM_VERSION, EVM_VERSION_0_34),
                        cryptoCreate(BOB),
                        uploadInitCode(prng),
                        contractCreate(prng))
                .when(sourcing(() -> contractCall(prng, GET_SEED)
                        .gas(GAS_TO_OFFER)
                        .payingWith(BOB)
                        .via(randomBits)
                        .logged()))
                .then(getTxnRecord(randomBits)
                        .hasPriority(recordWith()
                                .contractCallResult(resultWith()
                                        .resultViaFunctionName(
                                                GET_SEED, prng, isRandomResult((new Object[] {new byte[32]})))))
                        .logged());
    }

    @HapiTest
    final Stream<DynamicTest> prngPrecompileDisabledInV030() {
        final var prng = THE_PRNG_CONTRACT;
        final var randomBits = "randomBits";
        return propertyPreservingHapiSpec("prngPrecompileDisabledInV_0_30")
                .preserving(CONTRACTS_DYNAMIC_EVM_VERSION, CONTRACTS_EVM_VERSION)
                .given(
                        overriding(CONTRACTS_DYNAMIC_EVM_VERSION, TRUE_VALUE),
                        overriding(CONTRACTS_EVM_VERSION, EVM_VERSION_0_30),
                        cryptoCreate(BOB),
                        uploadInitCode(prng),
                        contractCreate(prng))
                .when(sourcing(() -> contractCall(prng, GET_SEED)
                        .gas(GAS_TO_OFFER)
                        .payingWith(BOB)
                        .via(randomBits)
                        .logged()))
                .then(getTxnRecord(randomBits)
                        .hasPriority(recordWith()
                                .contractCallResult(resultWith()
                                        .resultViaFunctionName(
                                                GET_SEED, prng, isLiteralResult((new Object[] {new byte[32]})))))
                        .logged());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}

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

package com.hedera.services.bdd.suites.contract.opcodes;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
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

public class PrngSeedOperationSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(PrngSeedOperationSuite.class);
    private static final long GAS_TO_OFFER = 400_000L;
    private static final String THE_PRNG_CONTRACT = "PrngSeedOperationContract";
    private static final String BOB = "bob";

    private static final String GET_SEED = "getPseudorandomSeed";

    public static final String CONTRACTS_DYNAMIC_EVM_VERSION = "contracts.evm.version.dynamic";
    public static final String CONTRACTS_EVM_VERSION = "contracts.evm.version";

    public static final String EVM_VERSION_0_31 = "v0.31";

    public static void main(String... args) {
        new PrngSeedOperationSuite().runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return allOf(positiveSpecs(), negativeSpecs());
    }

    List<HapiSpec> negativeSpecs() {
        return List.of();
    }

    List<HapiSpec> positiveSpecs() {
        return List.of(
                prngPrecompileHappyPathWorks(), multipleCallsHaveIndependentResults(), prngPrecompileDisabledInV030());
    }

    private HapiSpec multipleCallsHaveIndependentResults() {
        final var prng = THE_PRNG_CONTRACT;
        final var gasToOffer = 400_000;
        final var numCalls = 5;
        final List<String> prngSeeds = new ArrayList<>();
        return defaultHapiSpec("MultipleCallsHaveIndependentResults")
                .given(
                        uploadInitCode(prng),
                        contractCreate(prng),
                        overriding(CONTRACTS_DYNAMIC_EVM_VERSION, TRUE_VALUE),
                        overriding(CONTRACTS_EVM_VERSION, EVM_VERSION_0_31))
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

    private HapiSpec prngPrecompileHappyPathWorks() {
        final var prng = THE_PRNG_CONTRACT;
        final var randomBits = "randomBits";
        return defaultHapiSpec("prngPrecompileHappyPathWorks")
                .given(
                        overriding(CONTRACTS_DYNAMIC_EVM_VERSION, TRUE_VALUE),
                        overriding(CONTRACTS_EVM_VERSION, EVM_VERSION_0_31),
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

    private HapiSpec prngPrecompileDisabledInV030() {
        final var prng = THE_PRNG_CONTRACT;
        final var randomBits = "randomBits";
        return defaultHapiSpec("prngPrecompileDisabledInV_0_30")
                .given(
                        overriding(CONTRACTS_DYNAMIC_EVM_VERSION, TRUE_VALUE),
                        overriding(CONTRACTS_EVM_VERSION, EVM_VERSION_0_31),
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

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
package com.hedera.services.bdd.suites.util;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.hapiPrng;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingAllOf;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PRNG_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.primitives.Ints;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.swirlds.common.utility.CommonUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PrngSuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(PrngSuite.class);

    public static void main(String... args) {
        new PrngSuite().runSuiteSync();
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return allOf(positiveTests());
    }

    private List<HapiApiSpec> positiveTests() {
        return List.of(
                new HapiApiSpec[] {
                    happyPathWorksForRangeAndBitString(),
                    failsInPreCheckForNegativeRange(),
                    usdFeeAsExpected(),
                    featureFlagWorks(),
                    multipleCallsHaveIndependentResults(),
                });
    }

    private HapiApiSpec multipleCallsHaveIndependentResults() {
        final var range = 100;
        final var prng = "PrngSystemContract";
        final var gasToOffer = 400_000;
        final var numCalls = 5;
        final List<String> prngSeeds = new ArrayList<>();
        final List<Integer> prngNumbers = new ArrayList<>();
        return defaultHapiSpec("MultipleCallsHaveIndependentResults")
                .given(uploadInitCode(prng), contractCreate(prng))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    for (int i = 0; i < numCalls; i++) {
                                        final var txn = "call" + i;
                                        final var requestSeed = i % 2 == 0;
                                        final var call =
                                                requestSeed
                                                        ? contractCall(prng, "getPseudorandomSeed")
                                                                .gas(gasToOffer)
                                                                .via(txn)
                                                        : contractCall(
                                                                        prng,
                                                                        "getPseudorandomNumber",
                                                                        range)
                                                                .gas(gasToOffer)
                                                                .via(txn);
                                        final var lookup = getTxnRecord(txn).andAllChildRecords();
                                        allRunFor(spec, call, lookup);
                                        final var response = lookup.getResponseRecord();
                                        final var rawResult =
                                                response.getContractCallResult()
                                                        .getContractCallResult()
                                                        .toByteArray();
                                        // Since this contract returns the result of the PRNG system
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
                                        if (requestSeed) {
                                            prngSeeds.add(CommonUtils.hex(rawResult));
                                        } else {
                                            prngNumbers.add(
                                                    Ints.fromByteArray(
                                                            Arrays.copyOfRange(rawResult, 28, 32)));
                                        }
                                    }
                                    opLog.info("Got prng seeds  : {}", prngSeeds);
                                    opLog.info("Got prng numbers: {}", prngNumbers);
                                    assertEquals(
                                            prngSeeds.size(),
                                            new HashSet<>(prngSeeds).size(),
                                            "An N-3 running hash was repeated, which is"
                                                    + " inconceivable");
                                }))
                .then(
                        // It's possible to call these contracts in a static context with no issues
                        contractCallLocal(prng, "getPseudorandomSeed").gas(gasToOffer),
                        contractCallLocal(prng, "getPseudorandomNumber", range).gas(gasToOffer));
    }

    private HapiApiSpec featureFlagWorks() {
        return defaultHapiSpec("featureFlagWorks")
                .given(
                        overridingAllOf(Map.of("prng.isEnabled", "false")),
                        cryptoCreate("bob").balance(ONE_HUNDRED_HBARS),
                        hapiPrng().payingWith("bob").via("baseTxn").blankMemo().logged(),
                        getTxnRecord("baseTxn").hasNoPseudoRandomData().logged())
                .when(
                        hapiPrng(10).payingWith("bob").via("plusRangeTxn").blankMemo().logged(),
                        getTxnRecord("plusRangeTxn").hasNoPseudoRandomData().logged())
                .then();
    }

    private HapiApiSpec usdFeeAsExpected() {
        double baseFee = 0.001;
        double plusRangeFee = 0.0010010316;

        final var baseTxn = "prng";
        final var plusRangeTxn = "prngWithRange";

        return defaultHapiSpec("usdFeeAsExpected")
                .given(
                        overridingAllOf(Map.of("prng.isEnabled", "true")),
                        cryptoCreate("bob").balance(ONE_HUNDRED_HBARS),
                        hapiPrng().payingWith("bob").via(baseTxn).blankMemo().logged(),
                        getTxnRecord(baseTxn).hasOnlyPseudoRandomBytes().logged(),
                        validateChargedUsd(baseTxn, baseFee))
                .when(
                        hapiPrng(10).payingWith("bob").via(plusRangeTxn).blankMemo().logged(),
                        getTxnRecord(plusRangeTxn).hasOnlyPseudoRandomNumberInRange(10).logged(),
                        validateChargedUsdWithin(plusRangeTxn, plusRangeFee, 0.5))
                .then();
    }

    private HapiApiSpec failsInPreCheckForNegativeRange() {
        return defaultHapiSpec("failsInPreCheckForNegativeRange")
                .given(
                        overridingAllOf(Map.of("prng.isEnabled", "true")),
                        cryptoCreate("bob").balance(ONE_HUNDRED_HBARS),
                        hapiPrng(-10)
                                .payingWith("bob")
                                .blankMemo()
                                .hasPrecheck(INVALID_PRNG_RANGE)
                                .logged(),
                        hapiPrng(0).payingWith("bob").blankMemo().hasPrecheck(OK).logged())
                .when()
                .then();
    }

    private HapiApiSpec happyPathWorksForRangeAndBitString() {
        return defaultHapiSpec("happyPathWorksForRangeAndBitString")
                .given(
                        overridingAllOf(Map.of("prng.isEnabled", "true")),
                        // running hash is set
                        cryptoCreate("bob").balance(ONE_HUNDRED_HBARS),
                        // n-1 running hash and running has set
                        hapiPrng().payingWith("bob").blankMemo().via("prng").logged(),
                        // n-1, n-2 running hash and running has set
                        getTxnRecord("prng")
                                .hasNoPseudoRandomData() // When running this suite in CI this check
                                // will fail since it
                                // already has n-3 running hash
                                .logged(),
                        // n-1, n-2, n-3 running hash and running has set
                        hapiPrng(10).payingWith("bob").via("prngWithRange1").blankMemo().logged(),
                        getTxnRecord("prngWithRange1")
                                .hasNoPseudoRandomData() // When running this suite in CI this check
                                // will fail since it
                                // already has n-3 running hash
                                .logged(),
                        hapiPrng().payingWith("bob").via("prng2").blankMemo().logged())
                .when(
                        // should have pseudo random data
                        hapiPrng(10).payingWith("bob").via("prngWithRange").blankMemo().logged(),
                        getTxnRecord("prngWithRange").hasOnlyPseudoRandomNumberInRange(10).logged())
                .then(
                        hapiPrng().payingWith("bob").via("prngWithoutRange").blankMemo().logged(),
                        getTxnRecord("prngWithoutRange").hasOnlyPseudoRandomBytes().logged(),
                        hapiPrng(0).payingWith("bob").via("prngWithZeroRange").blankMemo().logged(),
                        getTxnRecord("prngWithZeroRange").hasOnlyPseudoRandomBytes().logged(),
                        hapiPrng()
                                .range(Integer.MAX_VALUE)
                                .payingWith("bob")
                                .via("prngWithMaxRange")
                                .blankMemo()
                                .logged(),
                        getTxnRecord("prngWithMaxRange")
                                .hasOnlyPseudoRandomNumberInRange(Integer.MAX_VALUE)
                                .logged(),
                        hapiPrng()
                                .range(Integer.MIN_VALUE)
                                .blankMemo()
                                .payingWith("bob")
                                .hasPrecheck(INVALID_PRNG_RANGE));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}

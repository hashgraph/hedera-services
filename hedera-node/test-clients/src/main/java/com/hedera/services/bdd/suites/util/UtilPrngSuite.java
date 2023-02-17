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

package com.hedera.services.bdd.suites.util;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.hapiPrng;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingAllOf;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PRNG_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class UtilPrngSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(UtilPrngSuite.class);
    private static final String PRNG_IS_ENABLED = "utilPrng.isEnabled";
    public static final String BOB = "bob";

    public static void main(String... args) {
        new UtilPrngSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return allOf(positiveTests());
    }

    private List<HapiSpec> positiveTests() {
        return List.of(happyPathWorksForRangeAndBitString(), failsInPreCheckForNegativeRange(), usdFeeAsExpected());
    }

    private HapiSpec usdFeeAsExpected() {
        double baseFee = 0.001;
        double plusRangeFee = 0.0010010316;

        final var baseTxn = "prng";
        final var plusRangeTxn = "prngWithRange";

        return defaultHapiSpec("usdFeeAsExpected")
                .given(
                        overridingAllOf(Map.of(PRNG_IS_ENABLED, "true")),
                        cryptoCreate(BOB).balance(ONE_HUNDRED_HBARS),
                        hapiPrng().payingWith(BOB).via(baseTxn).blankMemo().logged(),
                        getTxnRecord(baseTxn).hasOnlyPseudoRandomBytes().logged(),
                        validateChargedUsd(baseTxn, baseFee))
                .when(
                        hapiPrng(10)
                                .payingWith(BOB)
                                .via(plusRangeTxn)
                                .blankMemo()
                                .logged(),
                        getTxnRecord(plusRangeTxn)
                                .hasOnlyPseudoRandomNumberInRange(10)
                                .logged(),
                        validateChargedUsdWithin(plusRangeTxn, plusRangeFee, 0.5))
                .then();
    }

    private HapiSpec failsInPreCheckForNegativeRange() {
        return defaultHapiSpec("failsInPreCheckForNegativeRange")
                .given(
                        overridingAllOf(Map.of(PRNG_IS_ENABLED, "true")),
                        cryptoCreate(BOB).balance(ONE_HUNDRED_HBARS),
                        hapiPrng(-10)
                                .payingWith(BOB)
                                .blankMemo()
                                .hasPrecheck(INVALID_PRNG_RANGE)
                                .logged(),
                        hapiPrng(0).payingWith(BOB).blankMemo().hasPrecheck(OK).logged())
                .when()
                .then();
    }

    private HapiSpec happyPathWorksForRangeAndBitString() {
        final var rangeTxn = "prngWithRange";
        final var rangeTxn1 = "prngWithRange1";
        final var prngWithoutRange = "prngWithoutRange";
        final var prngWithMaxRange = "prngWithMaxRange";
        final var prngWithZeroRange = "prngWithZeroRange";
        return defaultHapiSpec("happyPathWorksForRangeAndBitString")
                .given(
                        overridingAllOf(Map.of(PRNG_IS_ENABLED, "true")),
                        // running hash is set
                        cryptoCreate(BOB).balance(ONE_HUNDRED_HBARS),
                        // n-1 running hash and running has set
                        hapiPrng().payingWith(BOB).blankMemo().via("prng").logged(),
                        // n-1, n-2 running hash and running has set
                        getTxnRecord("prng")
                                .hasNoPseudoRandomData() // When running this suite in CI this check
                                // will fail since it
                                // already has n-3 running hash
                                .logged(),
                        // n-1, n-2, n-3 running hash and running has set
                        hapiPrng(10).payingWith(BOB).via(rangeTxn1).blankMemo().logged(),
                        getTxnRecord(rangeTxn1)
                                .hasNoPseudoRandomData() // When running this suite in CI this check
                                // will fail since it
                                // already has n-3 running hash
                                .logged(),
                        hapiPrng().payingWith(BOB).via("prng2").blankMemo().logged())
                .when(
                        // should have pseudo random data
                        hapiPrng(10).payingWith(BOB).via(rangeTxn).blankMemo().logged(),
                        getTxnRecord(rangeTxn)
                                .hasOnlyPseudoRandomNumberInRange(10)
                                .logged())
                .then(
                        hapiPrng()
                                .payingWith(BOB)
                                .via(prngWithoutRange)
                                .blankMemo()
                                .logged(),
                        getTxnRecord(prngWithoutRange)
                                .hasOnlyPseudoRandomBytes()
                                .logged(),
                        hapiPrng(0)
                                .payingWith(BOB)
                                .via(prngWithZeroRange)
                                .blankMemo()
                                .logged(),
                        getTxnRecord(prngWithZeroRange)
                                .hasOnlyPseudoRandomBytes()
                                .logged(),
                        hapiPrng()
                                .range(Integer.MAX_VALUE)
                                .payingWith(BOB)
                                .via(prngWithMaxRange)
                                .blankMemo()
                                .logged(),
                        getTxnRecord(prngWithMaxRange)
                                .hasOnlyPseudoRandomNumberInRange(Integer.MAX_VALUE)
                                .logged(),
                        hapiPrng()
                                .range(Integer.MIN_VALUE)
                                .blankMemo()
                                .payingWith(BOB)
                                .hasPrecheck(INVALID_PRNG_RANGE));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}

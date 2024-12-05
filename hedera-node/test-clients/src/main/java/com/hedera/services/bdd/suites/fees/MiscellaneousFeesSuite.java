/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.fees;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getVersionInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.hapiPrng;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingAllOf;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_BILLION_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;

import com.hedera.services.bdd.junit.HapiTest;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class MiscellaneousFeesSuite {
    private static final String PRNG_IS_ENABLED = "utilPrng.isEnabled";
    public static final String BOB = "bob";

    @HapiTest
    final Stream<DynamicTest> usdFeeAsExpectedForPrngTransaction() {
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

    @HapiTest
    final Stream<DynamicTest> usdFeeAsExpectedForGetVersionInfo() {
        return hapiTest(
        cryptoCreate(BOB).balance(ONE_HUNDRED_HBARS),
        getVersionInfo().signedBy(BOB).payingWith(BOB).via("versionInfo").logged(),
        sleepFor(1000),
        validateChargedUsd("versionInfo", 0.0001));
    }

    @HapiTest
    final Stream<DynamicTest> usdFeeAsExpectedForTransactionGetReciept() {
        return hapiTest(
                cryptoCreate(BOB).balance(ONE_HUNDRED_HBARS).via("createTxn").logged(),
                getReceipt("createTxn").signedBy(BOB).payingWith(BOB),
                getAccountBalance(BOB).hasTinyBars(ONE_HUNDRED_HBARS));
    }

    @HapiTest
    final Stream<DynamicTest> usdFeeAsExpectedForTransactionGetRecord() {
        return hapiTest(
                cryptoCreate("Alice").balance(ONE_BILLION_HBARS),
                cryptoCreate(BOB).balance(ONE_HUNDRED_HBARS).signedBy("Alice").payingWith("Alice").via("createTxn").logged(),
                getTxnRecord("createTxn").signedBy(BOB).payingWith(BOB).via("transactionGetRecord"),
                validateChargedUsd("transactionGetRecord", 0.0001));
    }
}

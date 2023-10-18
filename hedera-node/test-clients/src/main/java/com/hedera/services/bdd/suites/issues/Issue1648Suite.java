/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.issues;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

@HapiTestSuite
public class Issue1648Suite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(Issue1648Suite.class);

    public static void main(String... args) {
        new Issue1648Suite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(recordStorageFeeIncreasesWithNumTransfers());
    }

    @HapiTest
    public static HapiSpec recordStorageFeeIncreasesWithNumTransfers() {
        return defaultHapiSpec("RecordStorageFeeIncreasesWithNumTransfers")
                .given(
                        cryptoCreate("civilian").balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate("A"),
                        cryptoCreate("B"),
                        cryptoCreate("C"),
                        cryptoCreate("D"),
                        cryptoTransfer(tinyBarsFromTo("A", "B", 1L))
                                .payingWith("civilian")
                                .via("txn1"),
                        cryptoTransfer(tinyBarsFromTo("A", "B", 1L), tinyBarsFromTo("C", "D", 1L))
                                .payingWith("civilian")
                                .via("txn2"))
                .when(UtilVerbs.recordFeeAmount("txn1", "feeForOne"), UtilVerbs.recordFeeAmount("txn2", "feeForTwo"))
                .then(UtilVerbs.assertionsHold((spec, assertLog) -> {
                    long feeForOne = spec.registry().getAmount("feeForOne");
                    long feeForTwo = spec.registry().getAmount("feeForTwo");
                    assertLog.info("[Record storage] fee for one transfer : {}", feeForOne);
                    assertLog.info("[Record storage] fee for two transfers: {}", feeForTwo);
                    Assertions.assertEquals(-1, Long.compare(feeForOne, feeForTwo));
                }));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}

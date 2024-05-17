/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.records;

import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;

import com.hedera.services.bdd.junit.HapiTest;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class ClosingTime {
    @HapiTest
    final Stream<DynamicTest> closeLastStreamFileWithNoBalanceImpact() {
        return customHapiSpec("CloseLastStreamFileWithNoBalanceImpact")
                .withProperties(Map.of(
                        "fees.useFixedOffer", "true",
                        "fees.fixedOffer", "100000000"))
                .given()
                .when()
                .then(sleepFor(2500), cryptoTransfer((spec, b) -> {}).payingWith(GENESIS), sleepFor(500));
    }
}

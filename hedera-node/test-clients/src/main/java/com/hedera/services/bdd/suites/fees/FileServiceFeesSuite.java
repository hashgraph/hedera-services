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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileAppend;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyListNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.THREE_MONTHS_IN_SECONDS;

import com.hedera.services.bdd.junit.HapiTest;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class FileServiceFeesSuite {
    @HapiTest
    final Stream<DynamicTest> baseOpsHaveExpectedPrices() {
        final var civilian = "NonExemptPayer";

        final var expectedAppendFeesPriceUsd = 0.05;

        final var baseAppend = "baseAppend";
        final var targetFile = "targetFile";
        final var contentBuilder = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            contentBuilder.append("A");
        }
        final var magicKey = "magicKey";
        final var magicWacl = "magicWacl";

        return defaultHapiSpec("BaseOpsHaveExpectedPrices")
                .given(
                        newKeyNamed(magicKey),
                        newKeyListNamed(magicWacl, List.of(magicKey)),
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS).key(magicKey),
                        fileCreate(targetFile)
                                .key(magicWacl)
                                .lifetime(THREE_MONTHS_IN_SECONDS)
                                .contents("Nothing much!"))
                .when(fileAppend(targetFile)
                        .signedBy(magicKey)
                        .blankMemo()
                        .content(contentBuilder.toString())
                        .payingWith(civilian)
                        .via(baseAppend))
                .then(validateChargedUsdWithin(baseAppend, expectedAppendFeesPriceUsd, 0.01));
    }
}

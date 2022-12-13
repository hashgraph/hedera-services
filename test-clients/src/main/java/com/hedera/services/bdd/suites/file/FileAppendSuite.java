/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.suites.file;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileAppend;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyListNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FileAppendSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(FileAppendSuite.class);

    public static void main(String... args) {
        new FileAppendSuite().runSuiteAsync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(vanillaAppendSucceeds(), baseOpsHaveExpectedPrices());
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    public HapiSpec baseOpsHaveExpectedPrices() {
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
                .when(
                        fileAppend(targetFile)
                                .signedBy(magicKey)
                                .blankMemo()
                                .content(contentBuilder.toString())
                                .payingWith(civilian)
                                .via(baseAppend))
                .then(validateChargedUsdWithin(baseAppend, expectedAppendFeesPriceUsd, 0.01));
    }

    private HapiSpec vanillaAppendSucceeds() {
        final byte[] first4K = randomUtf8Bytes(BYTES_4K);
        final byte[] next4k = randomUtf8Bytes(BYTES_4K);
        final byte[] all8k = new byte[2 * BYTES_4K];
        System.arraycopy(first4K, 0, all8k, 0, BYTES_4K);
        System.arraycopy(next4k, 0, all8k, BYTES_4K, BYTES_4K);

        return defaultHapiSpec("VanillaAppendSucceeds")
                .given(fileCreate("test").contents(first4K))
                .when(fileAppend("test").content(next4k))
                .then(getFileContents("test").hasContents(ignore -> all8k));
    }

    private final int BYTES_4K = 4 * (1 << 10);

    private byte[] randomUtf8Bytes(int n) {
        byte[] data = new byte[n];
        int i = 0;
        while (i < n) {
            byte[] rnd = UUID.randomUUID().toString().getBytes();
            System.arraycopy(rnd, 0, data, i, Math.min(rnd.length, n - 1 - i));
            i += rnd.length;
        }
        return data;
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}

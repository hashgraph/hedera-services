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
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.BYTES_4K;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUtf8Bytes;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileAppend;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyListNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withTargetLedgerId;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.THREE_MONTHS_IN_SECONDS;
import static com.hedera.services.bdd.suites.HapiSuite.ZERO_BYTE_MEMO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class FileServiceFeesSuite {
    public static final String CIVILIAN = "civilian";


    @HapiTest
    final Stream<DynamicTest> fileCreateBaseUSDFee() {
        String memo = "Really quite something!";
        //30 days considered for base fee
        var requestedExpiry = Instant.now().getEpochSecond() + 2592000L;
        var contents = "0".repeat(1000).getBytes();

        return hapiTest(
                newKeyNamed("key").shape(KeyShape.SIMPLE),
                cryptoCreate(CIVILIAN).key("key").balance(1_000_000_000L),
                newKeyListNamed("WACL", List.of(CIVILIAN)),
                fileCreate("test").memo(memo).key("WACL").contents(contents).payingWith(CIVILIAN).via("fileCreateBasic"),
                validateChargedUsd("fileCreateBasic", 0.05));
    }

    @HapiTest
    final Stream<DynamicTest> fileUpdateBaseUSDFee() {
        String memo = "Really quite something!";
        var contents = "0".repeat(1000).getBytes();
        return hapiTest(
                newKeyNamed("key").shape(KeyShape.SIMPLE),
                cryptoCreate(CIVILIAN).key("key").balance(1_000_000_000L),
                newKeyListNamed("key", List.of(CIVILIAN)),
                fileCreate("test").key("key").contents("ABC"),
                fileUpdate("test").contents(contents).memo(memo).payingWith(CIVILIAN).via("fileUpdateBasic"),
                validateChargedUsd("fileUpdateBasic", 0.05));
    }

    @HapiTest
    final Stream<DynamicTest> fileDeleteBaseUSDFee() {
        String memo = "Really quite something!";
        return hapiTest(
                newKeyNamed("key").shape(KeyShape.SIMPLE),
                cryptoCreate(CIVILIAN).key("key").balance(1_000_000_000L),
                newKeyListNamed("WACL", List.of(CIVILIAN)),
                fileCreate("test").memo(memo).key("WACL").contents("ABC"),
                TxnVerbs.fileDelete("test")
                        .blankMemo()
                        .payingWith(CIVILIAN).via("fileDeleteBasic"),
                validateChargedUsd("fileDeleteBasic", 0.007));
    }

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

    @HapiTest
    final Stream<DynamicTest> fileGetContentBaseUSDFee() {
        String memo = "Really quite something!";
        return hapiTest(
                cryptoCreate(CIVILIAN).balance(5 * ONE_HUNDRED_HBARS),
                fileCreate("ntb").key(CIVILIAN).contents("Nothing much!").memo(memo),
                getFileContents("ntb").payingWith(CIVILIAN).signedBy(CIVILIAN).via("getFileContentsBasic"),
                validateChargedUsd("getFileContentsBasic", 0.0001));
    }

    @HapiTest
    final Stream<DynamicTest> fileGetInfoBaseUSDFee() {
        String memo = "Really quite something!";
        return hapiTest(
                cryptoCreate(CIVILIAN).balance(5 * ONE_HUNDRED_HBARS),
                fileCreate("ntb").key(CIVILIAN).contents("Nothing much!").memo(memo),
                getFileInfo("ntb").payingWith(CIVILIAN).signedBy(CIVILIAN).via("getFileInfoBasic"),
                validateChargedUsd("getFileInfoBasic", 0.0001));
    }
}

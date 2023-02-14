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
package com.hedera.services.bdd.suites.misc;

import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.keyFromPem;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class KeyExport extends HapiSuite {
    private static final Logger log = LogManager.getLogger(KeyExport.class);

    private static final String PEM_FILE_NAME = "dev-testnet-account2.pem";

    public static void main(String... args) throws Exception {
        new KeyExport().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                new HapiSpec[] {
                    updateTreasuryKey(),
                    //						exportCurrentTreasuryKey(),
                    //						exportGenesisKey(),
                    //						validateNewKey(),
                });
    }

    private HapiSpec updateTreasuryKey() {
        return customHapiSpec("UpdateTreasuryKey")
                .withProperties(Map.of("nodes", "34.74.191.8"))
                .given(
                        //				keyFromPem(PEM_FILE_NAME)
                        //						.name("newKey")
                        //						.passphrase("ya3Xlgxaz3D4IWZcnF")
                        //						.simpleWacl()
                        )
                .when(
                        //				getAccountInfo(GENESIS).logged(),
                        //				cryptoUpdate(GENESIS).key("newKey")
                        fileUpdate(API_PERMISSIONS)
                                .erasingProps(Set.of("tokenGetInfo"))
                                .overridingProps(Map.of("getTokenInfo", "0-*")))
                .then(
                        getFileContents(API_PERMISSIONS).logged()
                        //				getAccountInfo(GENESIS).signedBy("newKey").logged()
                        //				freeze().startingIn(60).seconds().andLasting(1).minutes()
                        );
    }

    private HapiSpec validateNewKey() {
        return defaultHapiSpec("validateNewKey")
                .given(
                        keyFromPem(PEM_FILE_NAME)
                                .name("newKey")
                                .passphrase("P1WUX2Xla2wFslpoPTN39avz")
                                .simpleWacl())
                .when()
                .then(fileCreate("testFile").key("newKey"));
    }

    private HapiSpec exportCurrentTreasuryKey() {
        KeyFactory.PEM_PASSPHRASE = "passphrase";

        return defaultHapiSpec("ExportCurrentTreasuryKeyAsPem")
                .given()
                .when()
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    spec.keys().exportSimpleWacl("devGenesisKeypair.pem", GENESIS);
                                }));
    }

    private HapiSpec exportGenesisKey() {
        final var r = new Random();
        final int passphraseLength = 24;
        final char[] choices =
                "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
        final KeyShape listOfSizeOne = listOf(1);

        KeyFactory.PEM_PASSPHRASE =
                IntStream.range(0, passphraseLength)
                        .map(ignore -> r.nextInt(choices.length))
                        .mapToObj(i -> Character.valueOf(choices[i]).toString())
                        .collect(Collectors.joining(""));

        return defaultHapiSpec("ExportGenesisKey")
                .given(newKeyNamed("ab-initio").shape(listOfSizeOne))
                .when()
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    opLog.info("Passphrase is: {}", KeyFactory.PEM_PASSPHRASE);
                                    spec.keys().exportSimpleWacl(PEM_FILE_NAME, "ab-initio");
                                }));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}

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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;

import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@HapiTestSuite
public class Issue2098Spec extends HapiSuite {
    private static final Logger log = LogManager.getLogger(Issue2098Spec.class);
    private static final String CIVILIAN = "civilian";
    private static final String CRYPTO_TRANSFER = "cryptoTransfer";
    private static final String GET_TOPIC_INFO = "getTopicInfo";

    public static void main(String... args) {
        new Issue2098Spec().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {
            queryApiPermissionsChangeImmediately(),
            txnApiPermissionsChangeImmediately(),
            adminsCanQueryNoMatterPermissions(),
            adminsCanTransactNoMatterPermissions(),
        });
    }

    private HapiSpec txnApiPermissionsChangeImmediately() {
        return defaultHapiSpec("TxnApiPermissionsChangeImmediately")
                .given(cryptoCreate(CIVILIAN))
                .when(fileUpdate(API_PERMISSIONS)
                        .payingWith(ADDRESS_BOOK_CONTROL)
                        .erasingProps(Set.of(CRYPTO_TRANSFER)))
                .then(
                        cryptoTransfer(tinyBarsFromTo(CIVILIAN, FUNDING, 1L))
                                .payingWith(CIVILIAN)
                                .hasPrecheck(NOT_SUPPORTED),
                        fileUpdate(API_PERMISSIONS)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of(CRYPTO_TRANSFER, "0-*")),
                        cryptoTransfer(tinyBarsFromTo(CIVILIAN, FUNDING, 1L)).payingWith(CIVILIAN));
    }

    private HapiSpec queryApiPermissionsChangeImmediately() {
        return defaultHapiSpec("QueryApiPermissionsChangeImmediately")
                .given(cryptoCreate(CIVILIAN), createTopic("misc"))
                .when(fileUpdate(API_PERMISSIONS)
                        .payingWith(ADDRESS_BOOK_CONTROL)
                        .erasingProps(Set.of(GET_TOPIC_INFO)))
                .then(
                        getTopicInfo("misc").payingWith(CIVILIAN).hasAnswerOnlyPrecheck(NOT_SUPPORTED),
                        fileUpdate(API_PERMISSIONS)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of(GET_TOPIC_INFO, "0-*")),
                        getTopicInfo("misc").payingWith(CIVILIAN));
    }

    private HapiSpec adminsCanQueryNoMatterPermissions() {
        return defaultHapiSpec("AdminsCanQueryNoMatterPermissions")
                .given(cryptoCreate(CIVILIAN), createTopic("misc"))
                .when(fileUpdate(API_PERMISSIONS)
                        .payingWith(ADDRESS_BOOK_CONTROL)
                        .erasingProps(Set.of(GET_TOPIC_INFO)))
                .then(
                        getTopicInfo("misc").payingWith(CIVILIAN).hasAnswerOnlyPrecheck(NOT_SUPPORTED),
                        getTopicInfo("misc"),
                        fileUpdate(API_PERMISSIONS)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of(GET_TOPIC_INFO, "0-*")));
    }

    private HapiSpec adminsCanTransactNoMatterPermissions() {
        return defaultHapiSpec("AdminsCanTransactNoMatterPermissions")
                .given(cryptoCreate(CIVILIAN))
                .when(fileUpdate(API_PERMISSIONS)
                        .payingWith(ADDRESS_BOOK_CONTROL)
                        .erasingProps(Set.of(CRYPTO_TRANSFER)))
                .then(
                        cryptoTransfer(tinyBarsFromTo(CIVILIAN, FUNDING, 1L))
                                .payingWith(CIVILIAN)
                                .hasPrecheck(NOT_SUPPORTED),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)),
                        fileUpdate(API_PERMISSIONS)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of(CRYPTO_TRANSFER, "0-*")));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}

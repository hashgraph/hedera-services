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

package com.hedera.services.bdd.suites.issues;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

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
    public List<DynamicTest> getSpecsInSuite() {
        return List.of(
            queryApiPermissionsChangeImmediately(),
            txnApiPermissionsChangeImmediately(),
            adminsCanQueryNoMatterPermissions(),
            adminsCanTransactNoMatterPermissions());
    }

    @HapiTest
    final DynamicTest txnApiPermissionsChangeImmediately() {
        return defaultHapiSpec("TxnApiPermissionsChangeImmediately")
                .given(cryptoCreate(CIVILIAN))
                .when(fileUpdate(API_PERMISSIONS)
                        .payingWith(ADDRESS_BOOK_CONTROL)
                        .overridingProps(Map.of(CRYPTO_TRANSFER, "0-1")))
                .then(
                        cryptoTransfer(tinyBarsFromTo(CIVILIAN, FUNDING, 1L))
                                .payingWith(CIVILIAN)
                                .hasPrecheckFrom(NOT_SUPPORTED, OK)
                                .hasKnownStatus(UNAUTHORIZED),
                        fileUpdate(API_PERMISSIONS)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of(CRYPTO_TRANSFER, "0-*")),
                        cryptoTransfer(tinyBarsFromTo(CIVILIAN, FUNDING, 1L)).payingWith(CIVILIAN));
    }

    @HapiTest
    final DynamicTest queryApiPermissionsChangeImmediately() {
        return defaultHapiSpec("QueryApiPermissionsChangeImmediately")
                .given(cryptoCreate(CIVILIAN), createTopic("misc"))
                .when(fileUpdate(API_PERMISSIONS)
                        .payingWith(ADDRESS_BOOK_CONTROL)
                        .overridingProps(Map.of(GET_TOPIC_INFO, "0-1")))
                .then(
                        getTopicInfo("misc").payingWith(CIVILIAN).hasAnswerOnlyPrecheck(NOT_SUPPORTED),
                        fileUpdate(API_PERMISSIONS)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of(GET_TOPIC_INFO, "0-*")),
                        getTopicInfo("misc").payingWith(CIVILIAN));
    }

    @HapiTest
    final DynamicTest adminsCanQueryNoMatterPermissions() {
        return defaultHapiSpec("AdminsCanQueryNoMatterPermissions")
                .given(cryptoCreate(CIVILIAN), createTopic("misc"))
                .when(fileUpdate(API_PERMISSIONS)
                        .payingWith(ADDRESS_BOOK_CONTROL)
                        .overridingProps(Map.of(GET_TOPIC_INFO, "0-1")))
                .then(
                        getTopicInfo("misc").payingWith(CIVILIAN).hasAnswerOnlyPrecheck(NOT_SUPPORTED),
                        getTopicInfo("misc"),
                        fileUpdate(API_PERMISSIONS)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of(GET_TOPIC_INFO, "0-*")));
    }

    @HapiTest
    final DynamicTest adminsCanTransactNoMatterPermissions() {
        return defaultHapiSpec("AdminsCanTransactNoMatterPermissions")
                .given(cryptoCreate(CIVILIAN))
                .when(fileUpdate(API_PERMISSIONS)
                        .payingWith(ADDRESS_BOOK_CONTROL)
                        .overridingProps(Map.of(CRYPTO_TRANSFER, "0-1")))
                .then(
                        cryptoTransfer(tinyBarsFromTo(CIVILIAN, FUNDING, 1L))
                                .payingWith(CIVILIAN)
                                .hasPrecheckFrom(NOT_SUPPORTED, OK)
                                .hasKnownStatus(UNAUTHORIZED),
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

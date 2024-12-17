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

package com.hedera.services.bdd.suites.file.negative;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.ADDRESS_BOOK;
import static com.hedera.services.bdd.suites.HapiSuite.API_PERMISSIONS;
import static com.hedera.services.bdd.suites.HapiSuite.APP_PROPERTIES;
import static com.hedera.services.bdd.suites.HapiSuite.EXCHANGE_RATES;
import static com.hedera.services.bdd.suites.HapiSuite.EXCHANGE_RATE_CONTROL;
import static com.hedera.services.bdd.suites.HapiSuite.FEE_SCHEDULE;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.NODE_DETAILS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FILE_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class UpdateFailuresSpec {

    private static final long A_LOT = 1_234_567_890L;
    private static final String CIVILIAN = "civilian";

    @HapiTest
    final Stream<DynamicTest> confusedUpdateCantExtendExpiry() {
        // this test verify that the exchange rate file parsed correctly on update, it doesn't check expiry
        var initialExpiry = new AtomicLong();
        var extension = 1_000L;
        return defaultHapiSpec("ConfusedUpdateCantExtendExpiry")
                .given(withOpContext((spec, opLog) -> {
                    var infoOp = QueryVerbs.getFileInfo(EXCHANGE_RATES);
                    CustomSpecAssert.allRunFor(spec, infoOp);
                    var info = infoOp.getResponse().getFileGetInfo().getFileInfo();
                    initialExpiry.set(info.getExpirationTime().getSeconds());
                }))
                .when(fileUpdate(EXCHANGE_RATES)
                        .payingWith(EXCHANGE_RATE_CONTROL)
                        .contents("NONSENSE".getBytes())
                        .extendingExpiryBy(extension)
                        .hasKnownStatus(ResponseCodeEnum.INVALID_EXCHANGE_RATE_FILE))
                .then(QueryVerbs.getFileInfo(EXCHANGE_RATES).hasExpiry(initialExpiry::get));
    }

    @HapiTest
    final Stream<DynamicTest> precheckRejectsUnauthorized() {
        // this test is to verify that the system files cannot be updated without privileged account
        return defaultHapiSpec("precheckRejectsUnauthorized")
                .given(cryptoCreate(CIVILIAN))
                .when()
                .then(
                        fileUpdate(ADDRESS_BOOK).payingWith(CIVILIAN).hasPrecheck(AUTHORIZATION_FAILED),
                        fileUpdate(NODE_DETAILS).payingWith(CIVILIAN).hasPrecheck(AUTHORIZATION_FAILED),
                        fileUpdate(API_PERMISSIONS).payingWith(CIVILIAN).hasPrecheck(AUTHORIZATION_FAILED),
                        fileUpdate(APP_PROPERTIES).payingWith(CIVILIAN).hasPrecheck(AUTHORIZATION_FAILED),
                        fileUpdate(FEE_SCHEDULE).payingWith(CIVILIAN).hasPrecheck(AUTHORIZATION_FAILED),
                        fileUpdate(EXCHANGE_RATES).payingWith(CIVILIAN).hasPrecheck(AUTHORIZATION_FAILED));
    }

    @HapiTest
    final Stream<DynamicTest> precheckAllowsMissing() {
        return defaultHapiSpec("PrecheckAllowsMissing")
                .given()
                .when()
                .then(fileUpdate("1.2.3")
                        .payingWith(GENESIS)
                        .signedBy(GENESIS)
                        .fee(1_234_567L)
                        .hasPrecheck(OK)
                        .hasKnownStatus(INVALID_FILE_ID));
    }

    @HapiTest
    final Stream<DynamicTest> precheckAllowsDeleted() {
        return defaultHapiSpec("PrecheckAllowsDeleted")
                .given(fileCreate("tbd"))
                .when(fileDelete("tbd"))
                .then(fileUpdate("tbd").hasPrecheck(OK).hasKnownStatus(FILE_DELETED));
    }

    @HapiTest
    final Stream<DynamicTest> precheckRejectsPrematureExpiry() {
        long now = Instant.now().getEpochSecond();
        return defaultHapiSpec("PrecheckRejectsPrematureExpiry")
                .given(fileCreate("file"))
                .when()
                .then(fileUpdate("file")
                        .fee(A_LOT)
                        .extendingExpiryBy(-now)
                        .hasPrecheck(AUTORENEW_DURATION_NOT_IN_RANGE));
    }

    @HapiTest
    final Stream<DynamicTest> precheckAllowsBadEncoding() {
        return defaultHapiSpec("PrecheckAllowsBadEncoding")
                .given(fileCreate("file"))
                .when()
                .then(fileUpdate("file")
                        .fee(A_LOT)
                        .signedBy(GENESIS)
                        .useBadWacl()
                        .hasPrecheck(OK)
                        .hasKnownStatus(INVALID_SIGNATURE));
    }

    @SuppressWarnings("java:S5960")
    @HapiTest
    final Stream<DynamicTest> handleIgnoresEarlierExpiry() {
        var initialExpiry = new AtomicLong();

        return defaultHapiSpec("HandleIgnoresEarlierExpiry")
                .given(
                        fileCreate("file"),
                        withOpContext((spec, opLog) -> initialExpiry.set(
                                spec.registry().getTimestamp("file").getSeconds())))
                .when(fileUpdate("file").extendingExpiryBy(-1_000))
                .then(UtilVerbs.assertionsHold((spec, opLog) -> {
                    var infoOp = QueryVerbs.getFileInfo("file");
                    CustomSpecAssert.allRunFor(spec, infoOp);
                    var currExpiry = infoOp.getResponse()
                            .getFileGetInfo()
                            .getFileInfo()
                            .getExpirationTime()
                            .getSeconds();
                    assertEquals(initialExpiry.get(), currExpiry, "Expiry changed unexpectedly!");
                }));
    }
}

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
package com.hedera.services.bdd.suites.file.negative;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FILE_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class UpdateFailuresSpec extends HapiApiSuite {

    private static final long A_LOT = 1_234_567_890L;
    private static final Logger LOG = LogManager.getLogger(UpdateFailuresSpec.class);
    private static final String CIVILIAN = "civilian";

    public static void main(String... args) {
        new UpdateFailuresSpec().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                precheckAllowsMissing(),
                precheckAllowsDeleted(),
                precheckRejectsPrematureExpiry(),
                precheckAllowsBadEncoding(),
                precheckRejectsUnauthorized(),
                confusedUpdateCantExtendExpiry());
    }

    private HapiApiSpec confusedUpdateCantExtendExpiry() {
        var initialExpiry = new AtomicLong();
        var extension = 1_000L;
        return defaultHapiSpec("ConfusedUpdateCantExtendExpiry")
                .given(
                        withOpContext(
                                (spec, opLog) -> {
                                    var infoOp = QueryVerbs.getFileInfo(EXCHANGE_RATES);
                                    CustomSpecAssert.allRunFor(spec, infoOp);
                                    var info = infoOp.getResponse().getFileGetInfo().getFileInfo();
                                    initialExpiry.set(info.getExpirationTime().getSeconds());
                                }))
                .when(
                        fileUpdate(EXCHANGE_RATES)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .contents("NONSENSE".getBytes())
                                .extendingExpiryBy(extension)
                                .hasKnownStatus(ResponseCodeEnum.INVALID_EXCHANGE_RATE_FILE))
                .then(QueryVerbs.getFileInfo(EXCHANGE_RATES).hasExpiry(initialExpiry::get));
    }

    private HapiApiSpec precheckRejectsUnauthorized() {
        return defaultHapiSpec("PrecheckRejectsUnauthAddressBookUpdate")
                .given(cryptoCreate(CIVILIAN))
                .when()
                .then(
                        fileUpdate(ADDRESS_BOOK)
                                .payingWith(CIVILIAN)
                                .hasPrecheck(AUTHORIZATION_FAILED),
                        fileUpdate(NODE_DETAILS)
                                .payingWith(CIVILIAN)
                                .hasPrecheck(AUTHORIZATION_FAILED),
                        fileUpdate(API_PERMISSIONS)
                                .payingWith(CIVILIAN)
                                .hasPrecheck(AUTHORIZATION_FAILED),
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(CIVILIAN)
                                .hasPrecheck(AUTHORIZATION_FAILED),
                        fileUpdate(FEE_SCHEDULE)
                                .payingWith(CIVILIAN)
                                .hasPrecheck(AUTHORIZATION_FAILED),
                        fileUpdate(EXCHANGE_RATES)
                                .payingWith(CIVILIAN)
                                .hasPrecheck(AUTHORIZATION_FAILED));
    }

    private HapiApiSpec precheckAllowsMissing() {
        return defaultHapiSpec("PrecheckAllowsMissing")
                .given()
                .when()
                .then(
                        fileUpdate("1.2.3")
                                .payingWith(GENESIS)
                                .signedBy(GENESIS)
                                .fee(1_234_567L)
                                .hasPrecheck(OK)
                                .hasKnownStatus(INVALID_FILE_ID));
    }

    private HapiApiSpec precheckAllowsDeleted() {
        return defaultHapiSpec("PrecheckAllowsDeleted")
                .given(fileCreate("tbd"))
                .when(fileDelete("tbd"))
                .then(fileUpdate("tbd").hasPrecheck(OK).hasKnownStatus(FILE_DELETED));
    }

    private HapiApiSpec precheckRejectsPrematureExpiry() {
        long now = Instant.now().getEpochSecond();
        return defaultHapiSpec("PrecheckRejectsPrematureExpiry")
                .given(fileCreate("file"))
                .when()
                .then(
                        fileUpdate("file")
                                .fee(A_LOT)
                                .extendingExpiryBy(-now)
                                .hasKnownStatus(EXPIRATION_REDUCTION_NOT_ALLOWED));
    }

    private HapiApiSpec precheckAllowsBadEncoding() {
        return defaultHapiSpec("PrecheckAllowsBadEncoding")
                .given(fileCreate("file"))
                .when()
                .then(
                        fileUpdate("file")
                                .fee(A_LOT)
                                .signedBy(GENESIS)
                                .useBadWacl()
                                .hasPrecheck(OK)
                                .hasKnownStatus(INVALID_SIGNATURE));
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }
}

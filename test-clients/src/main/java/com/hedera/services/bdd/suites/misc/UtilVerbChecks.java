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
package com.hedera.services.bdd.suites.misc;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.ensureDissociated;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.makeFree;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withLiveNode;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class UtilVerbChecks extends HapiSuite {
    private static final Logger log = LogManager.getLogger(UtilVerbChecks.class);

    public static void main(String... args) throws Exception {
        new UtilVerbChecks().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                new HapiSpec[] {
                    //						testLivenessTimeout(),
                    testMakingFree(),
                    //						testDissociation(),
                });
    }

    private HapiSpec testMakingFree() {
        return defaultHapiSpec("TestMakingFree")
                .given(
                        cryptoCreate("civilian"),
                        getAccountInfo("0.0.2")
                                .payingWith("civilian")
                                .nodePayment(0L)
                                .hasAnswerOnlyPrecheck(INSUFFICIENT_TX_FEE))
                .when(makeFree(CryptoGetInfo))
                .then(
                        getAccountInfo("0.0.2")
                                .payingWith("civilian")
                                .nodePayment(0L)
                                .hasAnswerOnlyPrecheck(OK));
    }

    private HapiSpec testDissociation() {
        return defaultHapiSpec("TestDissociation")
                .given(
                        cryptoCreate("t"),
                        tokenCreate("a").treasury("t"),
                        tokenCreate("b").treasury("t"),
                        cryptoCreate("somebody"),
                        tokenAssociate("somebody", "a", "b"),
                        cryptoTransfer(moving(1, "a").between("t", "somebody")),
                        cryptoTransfer(moving(2, "b").between("t", "somebody")))
                .when(ensureDissociated("somebody", List.of("a", "b")))
                .then(
                        getAccountInfo("somebody")
                                .hasNoTokenRelationship("a")
                                .hasNoTokenRelationship("b"));
    }

    private HapiSpec testLivenessTimeout() {
        return defaultHapiSpec("TestLivenessTimeout")
                .given()
                .when()
                .then(
                        withLiveNode("0.0.3")
                                .within(300, TimeUnit.SECONDS)
                                .loggingAvailabilityEvery(30)
                                .sleepingBetweenRetriesFor(10));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}

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

package com.hedera.services.bdd.suites.addressbook;

import static com.hedera.services.bdd.junit.TestTags.EMBEDDED;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.PropertySource.asAccount;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeUpdate;
import static com.hedera.services.bdd.spec.utilops.EmbeddedVerbs.viewNode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.bdd.junit.HapiTest;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

public class NodeUpdateSuite {
    @HapiTest
    @Tag(EMBEDDED)
    final Stream<DynamicTest> updateNodeWorks() {
        final String description1 = "One, two! One, two! And through and through";
        final String description2 = "His vorpal blade went snicker-snack!";

        return hapiTest(
                nodeCreate("ntb").description(description1),
                nodeUpdate("ntb").description(description2),
                viewNode(
                        "ntb",
                        node -> assertEquals(description2, node.description(), "Node description should be updated")));
    }

    @HapiTest
    @Tag(EMBEDDED)
    final Stream<DynamicTest> validateFees() {
        final String description = "His vorpal blade went snicker-snack!";
        return defaultHapiSpec("validateFees")
                .given(
                        newKeyNamed("testKey"),
                        newKeyNamed("randomAccount"),
                        cryptoCreate("payer").balance(10_000_000_000L),
                        nodeCreate("node100").description(description).fee(ONE_HBAR),
                        // Submit to a different node so ingest check is skipped
                        nodeUpdate("node100")
                                .setNode("0.0.5")
                                .payingWith("payer")
                                .accountId(asAccount("0.0.1000"))
                                .fee(ONE_HBAR)
                                .hasKnownStatus(UNAUTHORIZED)
                                .via("failedUpdate"))
                .when()
                .then(
                        getTxnRecord("failedUpdate").logged(),
                        // The fee is charged here because the payer is not privileged
                        validateChargedUsdWithin("failedUpdate", 0.001, 3.0),
                        nodeUpdate("node100")
                                .accountId(asAccount("0.0.1000"))
                                .fee(ONE_HBAR)
                                .via("updateNode"),
                        getTxnRecord("updateNode").logged(),
                        // The fee is not charged here because the payer is privileged
                        validateChargedUsdWithin("updateNode", 0.0, 3.0),

                        // Submit with several signatures and the price should increase
                        nodeUpdate("node100")
                                .setNode("0.0.5")
                                .payingWith("payer")
                                .signedBy("payer", "payer", "randomAccount", "testKey")
                                .accountId(asAccount("0.0.1000"))
                                .fee(ONE_HBAR)
                                .hasKnownStatus(UNAUTHORIZED)
                                .via("failedUpdateMultipleSigs"),
                        validateChargedUsdWithin("failedUpdateMultipleSigs", 0.0011276316, 3.0));
    }

    @HapiTest
    @Tag(EMBEDDED)
    final Stream<DynamicTest> failsAtIngestForUnAuthorizedTxns() {
        final String description = "His vorpal blade went snicker-snack!";
        return defaultHapiSpec("failsAtIngestForUnAuthorizedTxns")
                .given(
                        cryptoCreate("payer").balance(10_000_000_000L),
                        nodeCreate("ntb").description(description).fee(ONE_HBAR).via("nodeCreation"),
                        nodeUpdate("ntb")
                                .payingWith("payer")
                                .accountId(asAccount("0.0.1000"))
                                .hasPrecheck(BUSY)
                                .fee(ONE_HBAR)
                                .via("updateNode"))
                .when()
                .then();
    }
}

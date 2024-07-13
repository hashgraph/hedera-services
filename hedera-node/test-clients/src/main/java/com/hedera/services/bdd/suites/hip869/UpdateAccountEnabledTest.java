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

package com.hedera.services.bdd.suites.hip869;

import static com.hedera.services.bdd.junit.EmbeddedReason.MUST_SKIP_INGEST;
import static com.hedera.services.bdd.junit.EmbeddedReason.NEEDS_STATE_ACCESS;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeUpdate;
import static com.hedera.services.bdd.spec.utilops.EmbeddedVerbs.viewNode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.services.bdd.junit.EmbeddedHapiTest;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;

/**
 * Tests expected behavior when the {@code nodes.updateAccountIdAllowed} feature flag is on for
 * <a href="https://hips.hedera.com/hip/hip-869">HIP-869, "Dynamic Address Book - Stage 1 - HAPI Endpoints"</a>.
 */
@HapiTestLifecycle
public class UpdateAccountEnabledTest {
    @BeforeAll
    static void beforeAll(@NonNull final TestLifecycle testLifecycle) {
        testLifecycle.overrideInClass(Map.of("nodes.updateAccountIdAllowed", "true"));
    }

    @HapiTest
    final Stream<DynamicTest> updateEmptyAccountIdFail() {
        return hapiTest(
                newKeyNamed("adminKey"),
                nodeCreate("testNode").adminKeyName("adminKey"),
                nodeUpdate("testNode").accountId("").hasPrecheck(INVALID_NODE_ACCOUNT_ID));
    }

    @HapiTest
    final Stream<DynamicTest> updateAliasAccountIdFail() {
        return hapiTest(
                newKeyNamed("adminKey"),
                nodeCreate("testNode").adminKeyName("adminKey"),
                nodeUpdate("testNode").aliasAccountId("alias").hasPrecheck(INVALID_NODE_ACCOUNT_ID));
    }

    @EmbeddedHapiTest(MUST_SKIP_INGEST)
    final Stream<DynamicTest> validateFees() {
        final String description = "His vorpal blade went snicker-snack!";
        return hapiTest(
                newKeyNamed("testKey"),
                newKeyNamed("randomAccount"),
                cryptoCreate("payer").balance(10_000_000_000L),
                nodeCreate("node100")
                        .adminKeyName("testKey")
                        .description(description)
                        .fee(ONE_HBAR),
                // Submit to a different node so ingest check is skipped
                nodeUpdate("node100")
                        .setNode("0.0.5")
                        .payingWith("payer")
                        .accountId("0.0.1000")
                        .fee(ONE_HBAR)
                        .hasKnownStatus(INVALID_SIGNATURE)
                        .via("failedUpdate"),
                getTxnRecord("failedUpdate").logged(),
                // The fee is charged here because the payer is not privileged
                validateChargedUsdWithin("failedUpdate", 0.001, 3.0),
                nodeUpdate("node100")
                        .adminKey("testKey")
                        .accountId("0.0.1000")
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
                        .accountId("0.0.1000")
                        .fee(ONE_HBAR)
                        .via("failedUpdateMultipleSigs"),
                validateChargedUsdWithin("failedUpdateMultipleSigs", 0.0011276316, 3.0));
    }

    @EmbeddedHapiTest(NEEDS_STATE_ACCESS)
    final Stream<DynamicTest> updateAccountIdWork() {
        return hapiTest(
                newKeyNamed("adminKey"),
                nodeCreate("testNode").adminKeyName("adminKey"),
                nodeUpdate("testNode").adminKey("adminKey").accountId("0.0.1000"),
                viewNode(
                        "testNode",
                        node -> assertEquals(
                                AccountID.newBuilder().accountNum(1000).build(),
                                node.accountId(),
                                "Node accountId should be updated")));
    }
}

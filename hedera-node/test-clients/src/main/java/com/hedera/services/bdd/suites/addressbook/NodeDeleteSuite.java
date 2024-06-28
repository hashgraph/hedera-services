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
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeDelete;
import static com.hedera.services.bdd.spec.utilops.EmbeddedVerbs.viewNode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.bdd.junit.HapiTest;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

public class NodeDeleteSuite {
    @HapiTest
    @Tag(EMBEDDED)
    final Stream<DynamicTest> deleteNodeWorks() {
        final String nodeName = "mytestnode";

        return hapiTest(
                nodeCreate(nodeName),
                viewNode(nodeName, node -> assertFalse(node.deleted(), "Node should not be deleted")),
                nodeDelete(nodeName),
                viewNode(nodeName, node -> assertTrue(node.deleted(), "Node should be deleted")));
    }

    @HapiTest
    @Tag(EMBEDDED)
    final Stream<DynamicTest> handleNodeNotExist() {
        final String nodeName = "33";

        return hapiTest(nodeDelete(nodeName).hasKnownStatus(ResponseCodeEnum.INVALID_NODE_ID));
    }

    @HapiTest
    @Tag(EMBEDDED)
    final Stream<DynamicTest> handleNodeAlreadyDeleted() {
        final String nodeName = "mytestnode";

        return hapiTest(
                nodeCreate(nodeName),
                nodeDelete(nodeName),
                nodeDelete(nodeName).signedBy(GENESIS).hasKnownStatus(ResponseCodeEnum.NODE_DELETED));
    }

    @HapiTest
    @Tag(EMBEDDED)
    final Stream<DynamicTest> handleCanBeExecutedJustWithPrivilagedAccount() {
        long PAYER_BALANCE = 1_999_999_999L;
        final String nodeName = "mytestnode";

        return hapiTest(
                newKeyNamed("adminKey"),
                newKeyNamed("wrongKey"),
                cryptoCreate("payer").balance(PAYER_BALANCE).key("wrongKey"),
                nodeCreate(nodeName),
                nodeDelete(nodeName)
                        .payingWith("payer")
                        .signedBy("payer", "wrongKey")
                        .hasPrecheck(BUSY),
                nodeDelete(nodeName).hasKnownStatus(SUCCESS));
    }
}

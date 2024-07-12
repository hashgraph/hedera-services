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

import static com.hedera.node.app.hapi.utils.CommonPbjConverters.toPbj;
import static com.hedera.services.bdd.junit.ContextRequirement.PROPERTY_OVERRIDES;
import static com.hedera.services.bdd.junit.TestTags.EMBEDDED;
import static com.hedera.services.bdd.spec.HapiPropertySource.asServiceEndpoint;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.ALL_ZEROS_INVALID_KEY;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeUpdate;
import static com.hedera.services.bdd.spec.utilops.EmbeddedVerbs.viewNode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.NONSENSE_KEY;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.TRUE_VALUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.GOSSIP_ENDPOINTS_EXCEEDED_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.GOSSIP_ENDPOINT_CANNOT_HAVE_FQDN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_GOSSIP_CA_CERTIFICATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_GOSSIP_ENDPOINT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_IPV4_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_DESCRIPTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_REQUIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SERVICE_ENDPOINTS_EXCEEDED_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UPDATE_NODE_ACCOUNT_NOT_ALLOWED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@DisplayName("updateNode")
@Tag(EMBEDDED)
public class NodeUpdateSuite {
    @HapiTest
    @DisplayName("cannot update a negative nodeid")
    final Stream<DynamicTest> cannotUpdateNegativeNodeId() {
        return hapiTest(nodeUpdate("-1").hasPrecheck(INVALID_NODE_ID));
    }

    @HapiTest
    @DisplayName("cannot update a missing nodeid")
    final Stream<DynamicTest> updateMissingNodeFail() {
        return hapiTest(nodeUpdate("100").hasPrecheck(INVALID_NODE_ID));
    }

    @HapiTest
    @DisplayName("cannot update a deleted node")
    final Stream<DynamicTest> updateDeletedNodeFail() {
        return hapiTest(
                newKeyNamed("adminKey"),
                nodeCreate("testNode").adminKeyName("adminKey"),
                nodeDelete("testNode"),
                nodeUpdate("testNode").hasPrecheck(INVALID_NODE_ID));
    }

    @HapiTest
    final Stream<DynamicTest> validateAdminKey() {
        return hapiTest(
                newKeyNamed("adminKey"),
                nodeCreate("testNode").adminKeyName("adminKey"),
                nodeUpdate("testNode").adminKey(NONSENSE_KEY).hasPrecheck(KEY_REQUIRED),
                nodeUpdate("testNode")
                        .adminKey(ALL_ZEROS_INVALID_KEY)
                        .signedBy(GENESIS)
                        .hasPrecheck(INVALID_ADMIN_KEY));
    }

    @HapiTest
    final Stream<DynamicTest> updateEmptyGossipCaCertificateFail() {
        return hapiTest(
                newKeyNamed("adminKey"),
                nodeCreate("testNode").adminKeyName("adminKey"),
                nodeUpdate("testNode").gossipCaCertificate("").hasPrecheck(INVALID_GOSSIP_CA_CERTIFICATE));
    }

    @HapiTest
    final Stream<DynamicTest> updateAccountIdNotAllowed() {
        return hapiTest(
                newKeyNamed("adminKey"),
                nodeCreate("testNode").adminKeyName("adminKey"),
                nodeUpdate("testNode").accountId("0.0.100").hasPrecheck(UPDATE_NODE_ACCOUNT_NOT_ALLOWED));
    }

    @LeakyHapiTest(PROPERTY_OVERRIDES)
    final Stream<DynamicTest> updateEmptyAccountIdFail() {
        final var changedProperty = "nodes.updateAccountIdAllowed";
        return propertyPreservingHapiSpec("UpdateEmptyAccountIdFail")
                .preserving(changedProperty)
                .given(
                        overriding(changedProperty, TRUE_VALUE),
                        newKeyNamed("adminKey"),
                        nodeCreate("testNode").adminKeyName("adminKey"))
                .when()
                .then(nodeUpdate("testNode").accountId("").hasPrecheck(INVALID_NODE_ACCOUNT_ID));
    }

    @LeakyHapiTest(PROPERTY_OVERRIDES)
    final Stream<DynamicTest> updateAliasAccountIdFail() {
        final var changedProperty = "nodes.updateAccountIdAllowed";
        return propertyPreservingHapiSpec("UpdateEmptyAccountIdFail")
                .preserving(changedProperty)
                .given(
                        overriding(changedProperty, TRUE_VALUE),
                        newKeyNamed("adminKey"),
                        nodeCreate("testNode").adminKeyName("adminKey"))
                .when()
                .then(nodeUpdate("testNode").aliasAccountId("alias").hasPrecheck(INVALID_NODE_ACCOUNT_ID));
    }

    @LeakyHapiTest(PROPERTY_OVERRIDES)
    final Stream<DynamicTest> updateTooLargeDescriptionFail() {
        final var changedProperty = "nodes.nodeMaxDescriptionUtf8Bytes";
        return propertyPreservingHapiSpec("UpdateTooLargeDescriptionFail")
                .preserving(changedProperty)
                .given(
                        overriding(changedProperty, "3"),
                        newKeyNamed("adminKey"),
                        nodeCreate("testNode").adminKeyName("adminKey"))
                .when()
                .then(nodeUpdate("testNode")
                        .adminKey("adminKey")
                        .description("toolarge")
                        .hasKnownStatus(INVALID_NODE_DESCRIPTION));
    }

    @HapiTest
    final Stream<DynamicTest> validateGossipEndpoint() {
        return hapiTest(
                newKeyNamed("adminKey"),
                nodeCreate("testNode").adminKeyName("adminKey"),
                nodeUpdate("testNode")
                        .adminKey("adminKey")
                        .gossipEndpoint(List.of(asServiceEndpoint("127.0.0.1:80")))
                        .hasKnownStatus(INVALID_GOSSIP_ENDPOINT),
                nodeUpdate("testNode")
                        .adminKey("adminKey")
                        .gossipEndpoint(List.of(
                                asServiceEndpoint("127.0.0.2:60"),
                                ServiceEndpoint.newBuilder()
                                        .setIpAddressV4(ByteString.copyFromUtf8("300.0.0.1"))
                                        .setPort(10)
                                        .build()))
                        .hasKnownStatus(INVALID_IPV4_ADDRESS),
                nodeUpdate("testNode")
                        .adminKey("adminKey")
                        .gossipEndpoint(List.of(
                                asServiceEndpoint("127.0.0.3:60"),
                                ServiceEndpoint.newBuilder()
                                        .setDomainName("test.dom")
                                        .setPort(10)
                                        .build()))
                        .hasKnownStatus(GOSSIP_ENDPOINT_CANNOT_HAVE_FQDN));
    }

    @LeakyHapiTest(PROPERTY_OVERRIDES)
    final Stream<DynamicTest> validateGossipEndpointSize() {
        final var changedProperty = "nodes.maxGossipEndpoint";
        return propertyPreservingHapiSpec("ValidateGossipEndpointSize")
                .preserving(changedProperty)
                .given(
                        overriding(changedProperty, "2"),
                        newKeyNamed("adminKey"),
                        nodeCreate("testNode").adminKeyName("adminKey"))
                .when()
                .then(nodeUpdate("testNode")
                        .adminKey("adminKey")
                        .gossipEndpoint(List.of(
                                asServiceEndpoint("127.0.0.1:60"),
                                asServiceEndpoint("127.0.0.2:60"),
                                asServiceEndpoint("127.0.0.3:60")))
                        .hasKnownStatus(GOSSIP_ENDPOINTS_EXCEEDED_LIMIT));
    }

    @HapiTest
    final Stream<DynamicTest> validateServiceEndpoint() {
        return hapiTest(
                newKeyNamed("adminKey"),
                nodeCreate("testNode").adminKeyName("adminKey"),
                nodeUpdate("testNode")
                        .adminKey("adminKey")
                        .serviceEndpoint(List.of(
                                asServiceEndpoint("127.0.0.2:60"),
                                ServiceEndpoint.newBuilder()
                                        .setIpAddressV4(ByteString.copyFromUtf8("300.0.0.1"))
                                        .setPort(10)
                                        .build()))
                        .hasKnownStatus(INVALID_IPV4_ADDRESS));
    }

    @LeakyHapiTest(PROPERTY_OVERRIDES)
    final Stream<DynamicTest> validateServiceEndpointSize() {
        final var changedProperty = "nodes.maxServiceEndpoint";
        return propertyPreservingHapiSpec("ValidateServiceEndpointSize")
                .preserving(changedProperty)
                .given(
                        overriding(changedProperty, "2"),
                        newKeyNamed("adminKey"),
                        nodeCreate("testNode").adminKeyName("adminKey"))
                .when()
                .then(nodeUpdate("testNode")
                        .adminKey("adminKey")
                        .serviceEndpoint(List.of(
                                asServiceEndpoint("127.0.0.1:60"),
                                asServiceEndpoint("127.0.0.2:60"),
                                asServiceEndpoint("127.0.0.3:60")))
                        .hasKnownStatus(SERVICE_ENDPOINTS_EXCEEDED_LIMIT));
    }

    @HapiTest
    final Stream<DynamicTest> updateMultipleFieldsWork() {
        return hapiTest(
                newKeyNamed("adminKey"),
                newKeyNamed("adminKey2"),
                nodeCreate("testNode").description("description to be changed").adminKeyName("adminKey"),
                nodeUpdate("testNode")
                        .adminKey("adminKey")
                        .description("updated description")
                        .gossipEndpoint(List.of(
                                asServiceEndpoint("127.0.0.1:60"),
                                asServiceEndpoint("127.0.0.2:60"),
                                asServiceEndpoint("127.0.0.3:60")))
                        .serviceEndpoint(List.of(asServiceEndpoint("127.0.1.1:60"), asServiceEndpoint("127.0.1.2:60")))
                        .gossipCaCertificate("caCert")
                        .grpcCertificateHash("grpcCert"),
                viewNode("testNode", node -> {
                    assertEquals("updated description", node.description(), "Node description should be updated");
                    assertIterableEquals(
                            List.of(
                                    toPbj(asServiceEndpoint("127.0.0.1:60")),
                                    toPbj(asServiceEndpoint("127.0.0.2:60")),
                                    toPbj(asServiceEndpoint("127.0.0.3:60"))),
                            node.gossipEndpoint(),
                            "Node gossipEndpoint should be updated");
                    assertIterableEquals(
                            List.of(toPbj(asServiceEndpoint("127.0.1.1:60")), toPbj(asServiceEndpoint("127.0.1.2:60"))),
                            node.serviceEndpoint(),
                            "Node serviceEndpoint should be updated");
                    assertEquals(
                            Bytes.wrap("caCert"),
                            node.gossipCaCertificate(),
                            "Node gossipCaCertificate should be updated");
                    assertEquals(
                            Bytes.wrap("grpcCert"),
                            node.grpcCertificateHash(),
                            "Node grpcCertificateHash should be updated");
                }));
    }

    @LeakyHapiTest(PROPERTY_OVERRIDES)
    final Stream<DynamicTest> updateAccountIdWork() {
        final var changedProperty = "nodes.updateAccountIdAllowed";
        return propertyPreservingHapiSpec("UpdateAccountIdWork")
                .preserving(changedProperty)
                .given(
                        overriding(changedProperty, TRUE_VALUE),
                        newKeyNamed("adminKey"),
                        nodeCreate("testNode").adminKeyName("adminKey"),
                        nodeUpdate("testNode").adminKey("adminKey").accountId("0.0.1000"))
                .when()
                .then(viewNode(
                        "testNode",
                        node -> assertEquals(
                                AccountID.newBuilder().accountNum(1000).build(),
                                node.accountId(),
                                "Node accountId should be updated")));
    }

    @LeakyHapiTest(PROPERTY_OVERRIDES)
    final Stream<DynamicTest> validateFees() {
        final String description = "His vorpal blade went snicker-snack!";
        final var changedProperty = "nodes.updateAccountIdAllowed";
        return propertyPreservingHapiSpec("validateFees")
                .preserving(changedProperty)
                .given(
                        overriding(changedProperty, TRUE_VALUE),
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
                                .via("failedUpdate"))
                .when()
                .then(
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

    @HapiTest
    final Stream<DynamicTest> failsAtIngestForUnAuthorizedTxns() {
        final String description = "His vorpal blade went snicker-snack!";
        return defaultHapiSpec("failsAtIngestForUnAuthorizedTxns")
                .given(
                        newKeyNamed("adminKey"),
                        cryptoCreate("payer").balance(10_000_000_000L),
                        nodeCreate("ntb")
                                .adminKeyName("adminKey")
                                .description(description)
                                .fee(ONE_HBAR)
                                .via("nodeCreation"),
                        nodeUpdate("ntb")
                                .payingWith("payer")
                                .accountId("0.0.1000")
                                .hasPrecheck(BUSY)
                                .fee(ONE_HBAR)
                                .via("updateNode"))
                .when()
                .then();
    }
}

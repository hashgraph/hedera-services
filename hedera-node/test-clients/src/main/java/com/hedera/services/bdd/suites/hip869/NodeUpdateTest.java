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

package com.hedera.services.bdd.suites.hip869;

import static com.hedera.node.app.hapi.utils.CommonPbjConverters.toPbj;
import static com.hedera.services.bdd.junit.EmbeddedReason.NEEDS_STATE_ACCESS;
import static com.hedera.services.bdd.spec.HapiPropertySource.asAccount;
import static com.hedera.services.bdd.spec.HapiPropertySource.asDnsServiceEndpoint;
import static com.hedera.services.bdd.spec.HapiPropertySource.asServiceEndpoint;
import static com.hedera.services.bdd.spec.HapiPropertySource.invalidServiceEndpoint;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.WRONG_LENGTH_EDDSA_KEY;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeUpdate;
import static com.hedera.services.bdd.spec.utilops.EmbeddedVerbs.viewNode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.NONSENSE_KEY;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.hip869.NodeCreateTest.generateX509Certificates;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.GOSSIP_ENDPOINTS_EXCEEDED_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.GOSSIP_ENDPOINT_CANNOT_HAVE_FQDN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_GOSSIP_CA_CERTIFICATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_GOSSIP_ENDPOINT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_IPV4_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_DESCRIPTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_REQUIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SERVICE_ENDPOINTS_EXCEEDED_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UPDATE_NODE_ACCOUNT_NOT_ALLOWED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.junit.EmbeddedHapiTest;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;

@DisplayName("updateNode")
@HapiTestLifecycle
public class NodeUpdateTest {
    private static List<X509Certificate> gossipCertificates;

    @BeforeAll
    static void beforeAll(@NonNull final TestLifecycle testLifecycle) {
        gossipCertificates = generateX509Certificates(2);
    }

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
    final Stream<DynamicTest> updateDeletedNodeFail() throws CertificateEncodingException {
        return hapiTest(
                newKeyNamed("adminKey"),
                nodeCreate("testNode")
                        .adminKey("adminKey")
                        .gossipCaCertificate(gossipCertificates.getFirst().getEncoded()),
                nodeDelete("testNode"),
                nodeUpdate("testNode").hasPrecheck(INVALID_NODE_ID));
    }

    @HapiTest
    final Stream<DynamicTest> validateAdminKey() throws CertificateEncodingException {
        return hapiTest(
                newKeyNamed("adminKey"),
                nodeCreate("testNode")
                        .adminKey("adminKey")
                        .gossipCaCertificate(gossipCertificates.getFirst().getEncoded()),
                nodeUpdate("testNode").adminKey(NONSENSE_KEY).hasPrecheck(KEY_REQUIRED),
                nodeUpdate("testNode")
                        .adminKey(WRONG_LENGTH_EDDSA_KEY)
                        .signedBy(GENESIS)
                        .hasPrecheck(INVALID_ADMIN_KEY));
    }

    @HapiTest
    final Stream<DynamicTest> updateEmptyGossipCaCertificateFail() throws CertificateEncodingException {
        return hapiTest(
                newKeyNamed("adminKey"),
                nodeCreate("testNode")
                        .adminKey("adminKey")
                        .gossipCaCertificate(gossipCertificates.getFirst().getEncoded()),
                nodeUpdate("testNode").gossipCaCertificate(new byte[0]).hasPrecheck(INVALID_GOSSIP_CA_CERTIFICATE));
    }

    @HapiTest
    final Stream<DynamicTest> updateAccountIdNotAllowed() throws CertificateEncodingException {
        return hapiTest(
                newKeyNamed("adminKey"),
                nodeCreate("testNode")
                        .adminKey("adminKey")
                        .gossipCaCertificate(gossipCertificates.getFirst().getEncoded()),
                nodeUpdate("testNode").accountId("0.0.100").hasPrecheck(UPDATE_NODE_ACCOUNT_NOT_ALLOWED));
    }

    @HapiTest
    final Stream<DynamicTest> validateGossipEndpoint() throws CertificateEncodingException {
        return hapiTest(
                newKeyNamed("adminKey"),
                nodeCreate("testNode")
                        .adminKey("adminKey")
                        .gossipCaCertificate(gossipCertificates.getFirst().getEncoded()),
                nodeUpdate("testNode")
                        .adminKey("adminKey")
                        .gossipEndpoint(List.of(asServiceEndpoint("127.0.0.1:80")))
                        .hasKnownStatus(INVALID_GOSSIP_ENDPOINT),
                nodeUpdate("testNode")
                        .adminKey("adminKey")
                        .gossipEndpoint(List.of(asServiceEndpoint("127.0.0.2:60"), invalidServiceEndpoint()))
                        .hasKnownStatus(INVALID_IPV4_ADDRESS),
                nodeUpdate("testNode")
                        .adminKey("adminKey")
                        .gossipEndpoint(List.of(asServiceEndpoint("127.0.0.3:60"), asDnsServiceEndpoint("test.dom:10")))
                        .hasKnownStatus(GOSSIP_ENDPOINT_CANNOT_HAVE_FQDN));
    }

    @HapiTest
    final Stream<DynamicTest> validateServiceEndpoint() throws CertificateEncodingException {
        return hapiTest(
                newKeyNamed("adminKey"),
                nodeCreate("testNode")
                        .adminKey("adminKey")
                        .gossipCaCertificate(gossipCertificates.getFirst().getEncoded()),
                nodeUpdate("testNode")
                        .adminKey("adminKey")
                        .serviceEndpoint(List.of(asServiceEndpoint("127.0.0.2:60"), invalidServiceEndpoint()))
                        .hasKnownStatus(INVALID_IPV4_ADDRESS));
    }

    @EmbeddedHapiTest(NEEDS_STATE_ACCESS)
    final Stream<DynamicTest> updateMultipleFieldsWork() throws CertificateEncodingException {
        final var updateOp = nodeUpdate("testNode")
                .adminKey("adminKey2")
                .signedBy(DEFAULT_PAYER, "adminKey", "adminKey2")
                .description("updated description")
                .gossipEndpoint(List.of(
                        asServiceEndpoint("127.0.0.1:60"),
                        asServiceEndpoint("127.0.0.2:60"),
                        asServiceEndpoint("127.0.0.3:60")))
                .serviceEndpoint(List.of(asServiceEndpoint("127.0.1.1:60"), asServiceEndpoint("127.0.1.2:60")))
                .gossipCaCertificate(gossipCertificates.getLast().getEncoded())
                .grpcCertificateHash("grpcCert".getBytes());
        return hapiTest(
                newKeyNamed("adminKey"),
                newKeyNamed("adminKey2"),
                nodeCreate("testNode")
                        .description("description to be changed")
                        .adminKey("adminKey")
                        .gossipCaCertificate(gossipCertificates.getFirst().getEncoded()),
                updateOp,
                viewNode("testNode", node -> {
                    assertEquals("updated description", node.description(), "Node description should be updated");
                    assertIterableEquals(
                            List.of(
                                    asServiceEndpoint("127.0.0.1:60"),
                                    asServiceEndpoint("127.0.0.2:60"),
                                    asServiceEndpoint("127.0.0.3:60")),
                            node.gossipEndpoint(),
                            "Node gossipEndpoint should be updated");
                    assertIterableEquals(
                            List.of(asServiceEndpoint("127.0.1.1:60"), asServiceEndpoint("127.0.1.2:60")),
                            node.serviceEndpoint(),
                            "Node serviceEndpoint should be updated");
                    try {
                        assertEquals(
                                Bytes.wrap(gossipCertificates.getLast().getEncoded()),
                                node.gossipCaCertificate(),
                                "Node gossipCaCertificate should be updated");
                    } catch (CertificateEncodingException e) {
                        throw new RuntimeException(e);
                    }
                    assertEquals(
                            Bytes.wrap("grpcCert"),
                            node.grpcCertificateHash(),
                            "Node grpcCertificateHash should be updated");
                    assertEquals(toPbj(updateOp.getAdminKey()), node.adminKey(), "Node adminKey should be updated");
                }));
    }

    @EmbeddedHapiTest(NEEDS_STATE_ACCESS)
    @LeakyHapiTest(overrides = {"nodes.updateAccountIdAllowed"})
    final Stream<DynamicTest> updateAccountIdWork() throws CertificateEncodingException {
        final var updateOp = nodeUpdate("testNode")
                .adminKey("adminKey2")
                .signedBy(DEFAULT_PAYER, "adminKey", "adminKey2")
                .description("updated description")
                .accountId("0.0.100")
                .gossipEndpoint(List.of(
                        asServiceEndpoint("127.0.0.1:60"),
                        asServiceEndpoint("127.0.0.2:60"),
                        asServiceEndpoint("127.0.0.3:60")))
                .serviceEndpoint(List.of(asServiceEndpoint("127.0.1.1:60"), asServiceEndpoint("127.0.1.2:60")))
                .gossipCaCertificate(gossipCertificates.getLast().getEncoded())
                .grpcCertificateHash("grpcCert".getBytes());
        return hapiTest(
                overriding("nodes.updateAccountIdAllowed", "true"),
                newKeyNamed("adminKey"),
                newKeyNamed("adminKey2"),
                nodeCreate("testNode")
                        .description("description to be changed")
                        .adminKey("adminKey")
                        .gossipCaCertificate(gossipCertificates.getFirst().getEncoded()),
                updateOp,
                viewNode("testNode", node -> {
                    assertEquals("updated description", node.description(), "Node description should be updated");
                    assertIterableEquals(
                            List.of(
                                    asServiceEndpoint("127.0.0.1:60"),
                                    asServiceEndpoint("127.0.0.2:60"),
                                    asServiceEndpoint("127.0.0.3:60")),
                            node.gossipEndpoint(),
                            "Node gossipEndpoint should be updated");
                    assertIterableEquals(
                            List.of(asServiceEndpoint("127.0.1.1:60"), asServiceEndpoint("127.0.1.2:60")),
                            node.serviceEndpoint(),
                            "Node serviceEndpoint should be updated");
                    try {
                        assertEquals(
                                Bytes.wrap(gossipCertificates.getLast().getEncoded()),
                                node.gossipCaCertificate(),
                                "Node gossipCaCertificate should be updated");
                    } catch (CertificateEncodingException e) {
                        throw new RuntimeException(e);
                    }
                    assertEquals(
                            Bytes.wrap("grpcCert"),
                            node.grpcCertificateHash(),
                            "Node grpcCertificateHash should be updated");
                    assertEquals(toPbj(updateOp.getAdminKey()), node.adminKey(), "Node adminKey should be updated");
                    assertEquals(toPbj(asAccount("0.0.100")), node.accountId());
                }),
                overriding("nodes.updateAccountIdAllowed", "false"));
    }

    @HapiTest
    final Stream<DynamicTest> failsAtIngestForUnAuthorizedTxns() throws CertificateEncodingException {
        final String description = "His vorpal blade went snicker-snack!";
        return defaultHapiSpec("failsAtIngestForUnAuthorizedTxns")
                .given(
                        newKeyNamed("adminKey"),
                        cryptoCreate("payer").balance(10_000_000_000L),
                        nodeCreate("ntb")
                                .adminKey("adminKey")
                                .description(description)
                                .fee(ONE_HBAR)
                                .gossipCaCertificate(
                                        gossipCertificates.getFirst().getEncoded())
                                .via("nodeCreation"),
                        nodeUpdate("ntb")
                                .payingWith("payer")
                                .accountId("0.0.1000")
                                .hasPrecheck(UPDATE_NODE_ACCOUNT_NOT_ALLOWED)
                                .fee(ONE_HBAR)
                                .via("updateNode"))
                .when()
                .then();
    }

    @LeakyHapiTest(overrides = {"nodes.maxServiceEndpoint"})
    final Stream<DynamicTest> validateServiceEndpointSize() throws CertificateEncodingException {
        return hapiTest(
                overriding("nodes.maxServiceEndpoint", "2"),
                newKeyNamed("adminKey"),
                nodeCreate("testNode")
                        .adminKey("adminKey")
                        .gossipCaCertificate(gossipCertificates.getFirst().getEncoded()),
                nodeUpdate("testNode")
                        .adminKey("adminKey")
                        .serviceEndpoint(List.of(
                                asServiceEndpoint("127.0.0.1:60"),
                                asServiceEndpoint("127.0.0.2:60"),
                                asServiceEndpoint("127.0.0.3:60")))
                        .hasKnownStatus(SERVICE_ENDPOINTS_EXCEEDED_LIMIT));
    }

    @LeakyHapiTest(overrides = {"nodes.maxGossipEndpoint"})
    final Stream<DynamicTest> validateGossipEndpointSize() throws CertificateEncodingException {
        return hapiTest(
                overriding("nodes.maxGossipEndpoint", "2"),
                newKeyNamed("adminKey"),
                nodeCreate("testNode")
                        .adminKey("adminKey")
                        .gossipCaCertificate(gossipCertificates.getFirst().getEncoded()),
                nodeUpdate("testNode")
                        .adminKey("adminKey")
                        .gossipEndpoint(List.of(
                                asServiceEndpoint("127.0.0.1:60"),
                                asServiceEndpoint("127.0.0.2:60"),
                                asServiceEndpoint("127.0.0.3:60")))
                        .hasKnownStatus(GOSSIP_ENDPOINTS_EXCEEDED_LIMIT));
    }

    @LeakyHapiTest(overrides = {"nodes.nodeMaxDescriptionUtf8Bytes"})
    final Stream<DynamicTest> updateTooLargeDescriptionFail() throws CertificateEncodingException {
        return hapiTest(
                overriding("nodes.nodeMaxDescriptionUtf8Bytes", "3"),
                newKeyNamed("adminKey"),
                nodeCreate("testNode")
                        .adminKey("adminKey")
                        .gossipCaCertificate(gossipCertificates.getFirst().getEncoded()),
                nodeUpdate("testNode")
                        .adminKey("adminKey")
                        .description("toolarge")
                        .hasKnownStatus(INVALID_NODE_DESCRIPTION));
    }

    @LeakyHapiTest(overrides = {"nodes.enableDAB"})
    @DisplayName("DAB enable test")
    final Stream<DynamicTest> checkDABEnable() throws CertificateEncodingException {
        return hapiTest(
                newKeyNamed("adminKey"),
                nodeCreate("testNode")
                        .adminKey("adminKey")
                        .gossipCaCertificate(gossipCertificates.getFirst().getEncoded()),
                overriding("nodes.enableDAB", "false"),
                nodeUpdate("testNode")
                        .adminKey("adminKey")
                        .serviceEndpoint(List.of(asServiceEndpoint("127.0.0.2:60"), invalidServiceEndpoint()))
                        .hasPrecheck(NOT_SUPPORTED));
    }
}

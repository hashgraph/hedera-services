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
import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.endpointFor;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.PropertySource.asAccount;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeCreate;
import static com.hedera.services.bdd.spec.utilops.EmbeddedVerbs.viewNode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.NONSENSE_KEY;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.STANDIN_CONTRACT_ID_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.GOSSIP_ENDPOINTS_EXCEEDED_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_GOSSIP_CA_CERTIFICATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_GOSSIP_ENDPOINT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SERVICE_ENDPOINT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_REQUIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SERVICE_ENDPOINTS_EXCEEDED_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

public class NodeCreateSuite {

    public static final String ED_25519_KEY = "ed25519Alias";
    public static List<ServiceEndpoint> GOSSIP_ENDPOINTS = Arrays.asList(
            ServiceEndpoint.newBuilder().setDomainName("test.com").setPort(123).build(),
            ServiceEndpoint.newBuilder().setDomainName("test2.com").setPort(123).build());
    public static List<ServiceEndpoint> SERVICES_ENDPOINTS = Arrays.asList(ServiceEndpoint.newBuilder()
            .setDomainName("service.com")
            .setPort(234)
            .build());
    public static List<ServiceEndpoint> GOSSIP_ENDPOINTS_IPS =
            Arrays.asList(endpointFor("192.168.1.200", 123), endpointFor("192.168.1.201", 123));
    public static List<ServiceEndpoint> SERVICES_ENDPOINTS_IPS = Arrays.asList(endpointFor("192.168.1.205", 234));

    /**
     * This test is to check if the node creation fails during ingest when the admin key is invalid.
     * @see <a href="https://github.com/hashgraph/hedera-improvement-proposal/blob/main/HIP/hip-869.md#specification">HIP-869</a>
     */
    @HapiTest
    final Stream<DynamicTest> adminKeyIsInvalidOnIngest() {
        return hapiTest(nodeCreate("nodeCreate")
                .adminKeyName(NONSENSE_KEY)
                .signedBy(GENESIS)
                .hasPrecheck(KEY_REQUIRED)); // on ingest level before all the events on the handlers happens);
        // expected status to reach consensus and this is the status */);
    }

    /**
     * This test is to check if the node creation fails during pureCheck when the admin key is invalid.
     * @see <a href="https://github.com/hashgraph/hedera-improvement-proposal/blob/main/HIP/hip-869.md#specification">HIP-869</a>
     */
    @HapiTest
    @Tag(EMBEDDED)
    final Stream<DynamicTest> adminKeyIsInvalidEmbedded() { // skipping ingest but purecheck still throw the same
        return hapiTest(nodeCreate("nodeCreate")
                .setNode("0.0.4") // exclude 0.0.3
                .adminKeyName(NONSENSE_KEY)
                .signedBy(GENESIS)
                .hasKnownStatus(KEY_REQUIRED));
    }

    /**
     * This test is to check if the node creation fails when the admin key is empty.
     * @see <a href="https://github.com/hashgraph/hedera-improvement-proposal/blob/main/HIP/hip-869.md#specification">HIP-869</a>
     */
    @HapiTest
    final Stream<DynamicTest> adminKeyIsEmpty() {
        return hapiTest(nodeCreate("nodeCreate")
                .adminKey(STANDIN_CONTRACT_ID_KEY)
                .signedBy(GENESIS)
                .hasPrecheck(KEY_REQUIRED));
    }

    /**
     * This test is to check if the node creation fails when the admin key is not signed by the payer.
     * @see <a href="https://github.com/hashgraph/hedera-improvement-proposal/blob/main/HIP/hip-869.md#specification">HIP-869</a>
     */
    @HapiTest
    @Tag(EMBEDDED)
    final Stream<DynamicTest> adminKeyIsInvalidSigPayer() {
        return hapiTest(
                newKeyNamed("adminKey"),
                cryptoCreate("payer").balance(ONE_HUNDRED_HBARS),
                nodeCreate("nodeCreate")
                        .setNode("0.0.4")
                        .adminKeyName("adminKey")
                        .signedBy("payer")
                        .hasPrecheck(OK)
                        .hasKnownStatus(INVALID_PAYER_SIGNATURE));
    }

    /**
     * This test is to check if the node creation fails when the service endpoint is empty.
     * @see <a href="https://github.com/hashgraph/hedera-improvement-proposal/blob/main/HIP/hip-869.md#specification">HIP-869</a>
     */
    @HapiTest
    final Stream<DynamicTest> failOnInvalidServiceEndpoint() {

        return hapiTest(nodeCreate("nodeCreate").serviceEndpoint(List.of()).hasPrecheck(INVALID_SERVICE_ENDPOINT));
    }

    /**
     * This test is to check if the node creation fails when the gossip endpoint is empty.
     * @see <a href="https://github.com/hashgraph/hedera-improvement-proposal/blob/main/HIP/hip-869.md#specification">HIP-869</a>
     */
    @HapiTest
    final Stream<DynamicTest> failOnInvalidGossipEndpoint() {
        return hapiTest(nodeCreate("nodeCreate").gossipEndpoint(List.of()).hasPrecheck(INVALID_GOSSIP_ENDPOINT));
    }

    /**
     * This test is to check if the node creation fails when the gossip CA certificate is invalid.
     * @see <a href="https://github.com/hashgraph/hedera-improvement-proposal/blob/main/HIP/hip-869.md#specification">HIP-869</a>
     */
    @HapiTest
    final Stream<DynamicTest> failOnEmptyGossipCaCertificate() {
        return hapiTest(
                nodeCreate("nodeCreate").gossipCaCertificate(new byte[0]).hasPrecheck(INVALID_GOSSIP_CA_CERTIFICATE));
    }

    /**
     * Check that node creation fails when more than 10 domain names are provided for gossip endpoints.
     * @see <a href="https://github.com/hashgraph/hedera-improvement-proposal/blob/main/HIP/hip-869.md#specification">HIP-869</a>
     */
    @HapiTest
    final Stream<DynamicTest> failOnTooManyGossipEndpoints() {
        final List<ServiceEndpoint> gossipEndpoints = Arrays.asList(
                ServiceEndpoint.newBuilder()
                        .setDomainName("test.com")
                        .setPort(123)
                        .build(),
                ServiceEndpoint.newBuilder()
                        .setDomainName("test2.com")
                        .setPort(123)
                        .build(),
                ServiceEndpoint.newBuilder()
                        .setDomainName("test3.com")
                        .setPort(123)
                        .build(),
                ServiceEndpoint.newBuilder()
                        .setDomainName("test4.com")
                        .setPort(123)
                        .build(),
                ServiceEndpoint.newBuilder()
                        .setDomainName("test5.com")
                        .setPort(123)
                        .build(),
                ServiceEndpoint.newBuilder()
                        .setDomainName("test6.com")
                        .setPort(123)
                        .build(),
                ServiceEndpoint.newBuilder()
                        .setDomainName("test7.com")
                        .setPort(123)
                        .build(),
                ServiceEndpoint.newBuilder()
                        .setDomainName("test8.com")
                        .setPort(123)
                        .build(),
                ServiceEndpoint.newBuilder()
                        .setDomainName("test9.com")
                        .setPort(123)
                        .build(),
                ServiceEndpoint.newBuilder()
                        .setDomainName("test10.com")
                        .setPort(123)
                        .build(),
                ServiceEndpoint.newBuilder()
                        .setDomainName("test11.com")
                        .setPort(123)
                        .build());
        return hapiTest(nodeCreate("nodeCreate")
                .gossipEndpoint(gossipEndpoints)
                .hasKnownStatus(GOSSIP_ENDPOINTS_EXCEEDED_LIMIT));
    }

    /**
     * Check that node creation fails when more than 8 domain names are provided for service endpoints.
     * @see <a href="https://github.com/hashgraph/hedera-improvement-proposal/blob/main/HIP/hip-869.md#specification">HIP-869</a>
     */
    @HapiTest
    final Stream<DynamicTest> failOnTooManyServiceEndpoints() {
        final List<ServiceEndpoint> serviceEndpoints = Arrays.asList(
                ServiceEndpoint.newBuilder()
                        .setDomainName("test.com")
                        .setPort(123)
                        .build(),
                ServiceEndpoint.newBuilder()
                        .setDomainName("test2.com")
                        .setPort(123)
                        .build(),
                ServiceEndpoint.newBuilder()
                        .setDomainName("test3.com")
                        .setPort(123)
                        .build(),
                ServiceEndpoint.newBuilder()
                        .setDomainName("test4.com")
                        .setPort(123)
                        .build(),
                ServiceEndpoint.newBuilder()
                        .setDomainName("test5.com")
                        .setPort(123)
                        .build(),
                ServiceEndpoint.newBuilder()
                        .setDomainName("test6.com")
                        .setPort(123)
                        .build(),
                ServiceEndpoint.newBuilder()
                        .setDomainName("test7.com")
                        .setPort(123)
                        .build(),
                ServiceEndpoint.newBuilder()
                        .setDomainName("test8.com")
                        .setPort(123)
                        .build(),
                ServiceEndpoint.newBuilder()
                        .setDomainName("test9.com")
                        .setPort(123)
                        .build());
        return hapiTest(nodeCreate("nodeCreate")
                .serviceEndpoint(serviceEndpoints)
                .hasKnownStatus(SERVICE_ENDPOINTS_EXCEEDED_LIMIT));
    }

    /**
     * Check that node creation succeeds with gossip and service endpoints using domain names and all optional fields are recorded.
     * @see <a href="https://github.com/hashgraph/hedera-improvement-proposal/blob/main/HIP/hip-869.md#specification">HIP-869</a>
     */
    @HapiTest
    final Stream<DynamicTest> allFieldsSetHappyCaseForDomains() {
        return hapiTest(
                newKeyNamed(ED_25519_KEY).shape(KeyShape.ED25519),
                overriding("nodes.gossipFqdnRestricted", "false"),
                nodeCreate("nodeCreate")
                        .description("hello")
                        .gossipCaCertificate("gossip".getBytes())
                        .grpcCertificateHash("hash".getBytes())
                        .accountId(asAccount("0.0.100"))
                        .gossipEndpoint(GOSSIP_ENDPOINTS)
                        .serviceEndpoint(SERVICES_ENDPOINTS)
                        .adminKeyName(ED_25519_KEY)
                        .hasPrecheck(OK)
                        .hasKnownStatus(SUCCESS),
                viewNode("nodeCreate", node -> {
                    assertEquals("hello", node.description(), "Description invalid");
                    assertEquals(
                            ByteString.copyFrom("gossip".getBytes()),
                            ByteString.copyFrom(node.gossipCaCertificate().toByteArray()),
                            "Gossip CA invalid");
                    assertEquals(
                            ByteString.copyFrom("hash".getBytes()),
                            ByteString.copyFrom(node.grpcCertificateHash().toByteArray()),
                            "GRPC hash invalid");
                    assertEquals(100, node.accountId().accountNum(), "Account ID invalid");
                    assertEqualServiceEndpoints(GOSSIP_ENDPOINTS, node.gossipEndpoint());
                    assertEqualServiceEndpoints(SERVICES_ENDPOINTS, node.serviceEndpoint());
                    assertNotNull(node.adminKey(), "Admin key invalid");
                }));
    }

    /**
     * Check that node creation succeeds with gossip and service endpoints using IPs and all optional fields are recorded.
     * @see <a href="https://github.com/hashgraph/hedera-improvement-proposal/blob/main/HIP/hip-869.md#specification">HIP-869</a>
     */
    @HapiTest
    @Tag(EMBEDDED)
    final Stream<DynamicTest> allFieldsSetHappyCaseForIps() {

        return hapiTest(
                newKeyNamed(ED_25519_KEY).shape(KeyShape.ED25519),
                overriding("nodes.gossipFqdnRestricted", "false"),
                nodeCreate("nodeCreate")
                        .description("hello")
                        .gossipCaCertificate("gossip".getBytes())
                        .grpcCertificateHash("hash".getBytes())
                        .accountId(asAccount("0.0.100"))
                        .gossipEndpoint(GOSSIP_ENDPOINTS_IPS)
                        .serviceEndpoint(SERVICES_ENDPOINTS_IPS)
                        .adminKeyName(ED_25519_KEY)
                        .hasPrecheck(OK)
                        .hasKnownStatus(SUCCESS),
                viewNode("nodeCreate", node -> {
                    assertEquals("hello", node.description(), "Description invalid");
                    assertEquals(
                            ByteString.copyFrom("gossip".getBytes()),
                            ByteString.copyFrom(node.gossipCaCertificate().toByteArray()),
                            "Gossip CA invalid");
                    assertEquals(
                            ByteString.copyFrom("hash".getBytes()),
                            ByteString.copyFrom(node.grpcCertificateHash().toByteArray()),
                            "GRPC hash invalid");
                    assertEquals(100, node.accountId().accountNum(), "Account ID invalid");
                    assertEqualServiceEndpoints(GOSSIP_ENDPOINTS_IPS, node.gossipEndpoint());
                    assertEqualServiceEndpoints(SERVICES_ENDPOINTS_IPS, node.serviceEndpoint());
                    assertNotNull(node.adminKey(), "Admin key invalid");
                }));
    }

    /**
     * Check that node creation succeeds with minimum required fields set.
     * @see <a href="https://github.com/hashgraph/hedera-improvement-proposal/blob/main/HIP/hip-869.md#specification">HIP-869</a>
     */
    @HapiTest
    final Stream<DynamicTest> minimumFieldsSetHappyCase() {
        return hapiTest(
                nodeCreate("ntb"),
                viewNode("ntb", node -> assertEquals("", node.description(), "Node was created successfully")));
    }

    /**
     * Check that appropriate fees are charged during node creation.
     */
    @HapiTest
    @Tag(EMBEDDED)
    final Stream<DynamicTest> validateFees() {
        return defaultHapiSpec("validateFees")
                .given(
                        newKeyNamed("testKey"),
                        newKeyNamed("randomAccount"),
                        cryptoCreate("payer").balance(10_000_000_000L),
                        // Submit to a different node so ingest check is skipped
                        nodeCreate("ntb")
                                .payingWith("payer")
                                .signedBy("payer")
                                .setNode("0.0.4")
                                .hasKnownStatus(UNAUTHORIZED)
                                .via("nodeCreationFailed"))
                .when()
                .then(
                        getTxnRecord("nodeCreationFailed").logged(),
                        // Validate that the failed transaction charges the correct fees.
                        validateChargedUsdWithin("nodeCreationFailed", 0.001, 3),
                        nodeCreate("ntb").fee(ONE_HBAR).via("nodeCreation"),
                        getTxnRecord("nodeCreation").logged(),
                        // But, note that the fee will not be charged for privileged payer
                        // The fee is charged here because the payer is not privileged
                        validateChargedUsdWithin("nodeCreation", 0.0, 0.0),

                        // Submit with several signatures and the price should increase
                        nodeCreate("ntb")
                                .payingWith("payer")
                                .signedBy("payer", "randomAccount", "testKey")
                                .setNode("0.0.4")
                                .hasKnownStatus(UNAUTHORIZED)
                                .via("multipleSigsCreation"),
                        validateChargedUsdWithin("multipleSigsCreation", 0.0011276316, 3.0));
    }

    /**
     * Check that node creation fails during ingest when the transaction is unauthorized.
     * @see <a href="https://github.com/hashgraph/hedera-improvement-proposal/blob/main/HIP/hip-869.md#specification">HIP-869</a>
     */
    @HapiTest
    @Tag(EMBEDDED)
    final Stream<DynamicTest> validateFeesInsufficientAmount() {
        final String description = "His vorpal blade went snicker-snack!";
        return defaultHapiSpec("validateFees")
                .given(
                        newKeyNamed("testKey"),
                        newKeyNamed("randomAccount"),
                        cryptoCreate("payer").balance(10_000_000_000L),
                        // Submit to a different node so ingest check is skipped
                        nodeCreate("ntb")
                                .payingWith("payer")
                                .signedBy("payer")
                                .description(description)
                                .setNode("0.0.4")
                                .fee(1)
                                .hasKnownStatus(INSUFFICIENT_TX_FEE)
                                .via("nodeCreationFailed"))
                .when()
                .then(
                        getTxnRecord("nodeCreationFailed").logged(),
                        nodeCreate("ntb").description(description).via("nodeCreation"),
                        getTxnRecord("nodeCreation").logged(),
                        // But, note that the fee will not be charged for privileged payer
                        // The fee is charged here because the payer is not privileged
                        validateChargedUsdWithin("nodeCreation", 0.0, 0.0),

                        // Submit with several signatures and the price should increase
                        nodeCreate("ntb")
                                .payingWith("payer")
                                .signedBy("payer", "randomAccount", "testKey")
                                .description(description)
                                .setNode("0.0.4")
                                .fee(1)
                                .hasKnownStatus(INSUFFICIENT_TX_FEE)
                                .via("multipleSigsCreation"));
    }

    @HapiTest
    @Tag(EMBEDDED)
    final Stream<DynamicTest> failsAtIngestForUnAuthorizedTxns() {
        final String description = "His vorpal blade went snicker-snack!";
        return defaultHapiSpec("failsAtIngestForUnAuthorizedTxns")
                .given(
                        cryptoCreate("payer").balance(10_000_000_000L),
                        nodeCreate("ntb")
                                .payingWith("payer")
                                .signedBy("payer")
                                .description(description)
                                .fee(ONE_HBAR)
                                .hasPrecheck(BUSY)
                                .via("nodeCreation"))
                .when()
                .then();
    }

    private static void assertEqualServiceEndpoints(
            List<com.hederahashgraph.api.proto.java.ServiceEndpoint> expected,
            List<com.hedera.hapi.node.base.ServiceEndpoint> actual) {
        assertEquals(expected.size(), actual.size(), "Service endpoints size invalid");
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(
                    ByteString.copyFrom(expected.get(i).getIpAddressV4().toByteArray()),
                    ByteString.copyFrom(actual.get(i).ipAddressV4().toByteArray()),
                    "Service endpoint IP address invalid");
            assertEquals(
                    expected.get(i).getDomainName(),
                    actual.get(i).domainName(),
                    "Service endpoint domain name invalid");
            assertEquals(expected.get(i).getPort(), actual.get(i).port(), "Service endpoint port invalid");
        }
    }
}

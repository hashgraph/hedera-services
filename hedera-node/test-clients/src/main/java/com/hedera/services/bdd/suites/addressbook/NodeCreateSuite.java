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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_REQUIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
    public static List<ServiceEndpoint> GOSSIP_ENDPOINTS = Arrays.asList(ServiceEndpoint.newBuilder().setDomainName("test.com").setPort(123).build(),ServiceEndpoint.newBuilder().setDomainName("test2.com").setPort(123).build());
    public static List<ServiceEndpoint> SERVICES_ENDPOINTS = Arrays.asList(ServiceEndpoint.newBuilder().setDomainName("service.com").setPort(234).build());
    public static List<ServiceEndpoint> GOSSIP_ENDPOINTS_IPS = Arrays.asList(endpointFor("192.168.1.200",123),endpointFor("192.168.1.201", 123));
    public static List<ServiceEndpoint> SERVICES_ENDPOINTS_IPS = Arrays.asList(endpointFor("192.168.1.205",234));



    @HapiTest
    final Stream<DynamicTest> adminKeyIsInvalidOnIngest() {
        return hapiTest(nodeCreate("nodeCreate")
                        .adminKeyName(NONSENSE_KEY)
                        .signedBy(GENESIS)
                         .hasPrecheck(KEY_REQUIRED));//on ingest level before all the events on the handlers happens); //expected status to reach consensus and this si the status */);
    }


    @HapiTest
    @Tag(EMBEDDED)
    final Stream<DynamicTest> adminKeyIsInvalidEmbedded() { //skipping ingest but purecheck still throw the same
        return hapiTest(nodeCreate("nodeCreate").setNode("0.0.4") //exclude 0.0.3
                .adminKeyName(NONSENSE_KEY)
                .signedBy(GENESIS)
                .hasKnownStatus(KEY_REQUIRED));
    }

    @HapiTest
    final Stream<DynamicTest> adminKeyIsEmpty() {
        return hapiTest(nodeCreate("nodeCreate")
                .adminKey(STANDIN_CONTRACT_ID_KEY)
                .signedBy(GENESIS)
                .hasPrecheck(KEY_REQUIRED));
    }

    @HapiTest
    @Tag(EMBEDDED)
    final Stream<DynamicTest> adminKeyIsInValidSigPayer() {
        return hapiTest(newKeyNamed("adminKey"),
                cryptoCreate("payer").balance(ONE_HUNDRED_HBARS),
                nodeCreate("nodeCreate").setNode("0.0.4")
                 .adminKeyName("adminKey")
                .signedBy("payer")
                .hasPrecheck(OK)
                .hasKnownStatus(INVALID_PAYER_SIGNATURE));
    }

    @HapiTest
    final Stream<DynamicTest> adminKeyIsIsValid() {
           return hapiTest(newKeyNamed(ED_25519_KEY).shape(KeyShape.ED25519),
                   cryptoCreate("payer").balance(ONE_HUNDRED_HBARS),
                   nodeCreate("nodeCreate")
                           .adminKeyName(ED_25519_KEY)
                           .hasPrecheck(OK)
                           .hasKnownStatus(SUCCESS));
    }

    @HapiTest
    final Stream<DynamicTest> allFieldsSetHappyCaseForDomains() {
        return hapiTest(newKeyNamed(ED_25519_KEY).shape(KeyShape.ED25519),
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
                        .hasKnownStatus(SUCCESS));
    }

    @HapiTest
    final Stream<DynamicTest> allFieldsSetHappyCaseForIps() {
        return hapiTest(newKeyNamed(ED_25519_KEY).shape(KeyShape.ED25519),
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
                        .hasKnownStatus(SUCCESS));
    }

    @HapiTest
    final Stream<DynamicTest> createNodeWorks() {
        final String description = "His vorpal blade went snicker-snack!";

        return hapiTest(
                nodeCreate("ntb").description(description),
                viewNode(
                        "ntb", node -> assertEquals(description, node.description(), "Node was created successfully")));
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
                        // Submit to a different node so ingest check is skipped
                        nodeCreate("ntb")
                                .payingWith("payer")
                                .signedBy("payer")
                                .description(description)
                                .setNode("0.0.4")
                                .fee(ONE_HBAR)
                                .hasKnownStatus(UNAUTHORIZED)
                                .via("nodeCreationFailed"))
                .when()
                .then(
                        getTxnRecord("nodeCreationFailed").logged(),
                        // Validate that the failed transaction charges the correct fees.
                        validateChargedUsdWithin("nodeCreationFailed", 0.001, 3),
                        nodeCreate("ntb").description(description).fee(ONE_HBAR).via("nodeCreation"),
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
                                .fee(ONE_HBAR)
                                .hasKnownStatus(UNAUTHORIZED)
                                .via("multipleSigsCreation"),
                        validateChargedUsdWithin("multipleSigsCreation", 0.0011276316, 3.0));
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
}

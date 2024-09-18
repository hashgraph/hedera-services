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

package com.hedera.services.bdd.suites.regression.system;

import static com.hedera.services.bdd.junit.TestTags.UPGRADE;
import static com.hedera.services.bdd.junit.hedera.NodeSelector.allNodes;
import static com.hedera.services.bdd.junit.hedera.NodeSelector.byNodeId;
import static com.hedera.services.bdd.junit.hedera.NodeSelector.exceptNodeId;
import static com.hedera.services.bdd.junit.hedera.subprocess.UpgradeConfigTxt.DAB_GENERATED;
import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.CLASSIC_NODE_NAMES;
import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.classicMetadataFor;
import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.nodeAddressFrom;
import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.nodeIdsFrom;
import static com.hedera.services.bdd.spec.HapiPropertySource.asServiceEndpoint;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.dsl.operations.transactions.TouchBalancesOperation.touchBalanceOf;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.ensureStakingActivated;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.given;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateUpgradeAddressBooks;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitUntilStartOfNextStakingPeriod;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_BILLION_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hedera.services.bdd.suites.hip869.NodeCreateTest.generateX509Certificates;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.dsl.annotations.Account;
import com.hedera.services.bdd.spec.dsl.entities.SpecAccount;
import com.hedera.services.bdd.spec.utilops.FakeNmt;
import com.hederahashgraph.api.proto.java.AccountID;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;

/**
 * These tests following tests in HIP-869 Test Plan Freeze section
 * Since this test upgrades the software version, it must run after any other test that does a restart assuming
 * the config version is still zero.
 */
@Tag(UPGRADE)
@DisplayName("DAB freeze test")
@HapiTestLifecycle
public class DabFreezeTest implements LifecycleTest {
    @Account(tinybarBalance = ONE_BILLION_HBARS, stakedNodeId = 0)
    static SpecAccount NODE0_STAKER;

    @Account(tinybarBalance = ONE_BILLION_HBARS, stakedNodeId = 1)
    static SpecAccount NODE1_STAKER;

    @Account(tinybarBalance = ONE_BILLION_HBARS, stakedNodeId = 2)
    static SpecAccount NODE2_STAKER;

    @Account(tinybarBalance = ONE_MILLION_HBARS, stakedNodeId = 3)
    static SpecAccount NODE3_STAKER;

    private static List<X509Certificate> gossipCertificates;

    @BeforeAll
    static void beforeAll(@NonNull final TestLifecycle testLifecycle) {
        testLifecycle.doAdhoc(
                ensureStakingActivated(),
                touchBalanceOf(NODE0_STAKER, NODE1_STAKER, NODE2_STAKER, NODE3_STAKER),
                waitUntilStartOfNextStakingPeriod(1).withBackgroundTraffic(),
                given(() -> gossipCertificates = generateX509Certificates(3)));
    }

    @Nested
    @DisplayName("add one node, node5")
    class AddOneNode {
        private static final AccountID NEW_ACCOUNT_ID =
                AccountID.newBuilder().setAccountNum(7L).build();

        @BeforeAll
        static void beforeAll(@NonNull final TestLifecycle testLifecycle) throws CertificateEncodingException {
            testLifecycle.doAdhoc(nodeCreate("node5")
                    .accountId(NEW_ACCOUNT_ID)
                    .description(CLASSIC_NODE_NAMES[4])
                    .withAvailableSubProcessPorts()
                    .gossipCaCertificate(gossipCertificates.getFirst().getEncoded()));
        }

        @HapiTest
        @DisplayName("validate address book with node5")
        final Stream<DynamicTest> addedNodeTest() {
            return hapiTest(
                    prepareFakeUpgrade(),
                    validateUpgradeAddressBooks(
                            addressBook -> assertThat(nodeIdsFrom(addressBook)).contains(4L)),
                    upgradeToNextConfigVersion(FakeNmt.addNode(Set.of(4L), DAB_GENERATED, true)));
        }
    }

    @Nested
    @DisplayName("add max nodes, node5, node6")
    class AddMaxNodes {
        private static final AccountID NEW_ACCOUNT_ID =
                AccountID.newBuilder().setAccountNum(7L).build();
        private static final AccountID NEW_ACCOUNT_ID_2 =
                AccountID.newBuilder().setAccountNum(8L).build();

        @BeforeAll
        @LeakyHapiTest(overrides = {"nodes.maxNumber"})
        static void beforeAll(@NonNull final TestLifecycle testLifecycle) throws CertificateEncodingException {
            testLifecycle.doAdhoc(
                    overriding("nodes.maxNumber", "6"),
                    nodeCreate("node5")
                            .accountId(NEW_ACCOUNT_ID)
                            .description(CLASSIC_NODE_NAMES[4])
                            .withAvailableSubProcessPorts()
                            .gossipCaCertificate(gossipCertificates.getFirst().getEncoded()),
                    nodeCreate("node6")
                            .accountId(NEW_ACCOUNT_ID)
                            .description(CLASSIC_NODE_NAMES[5])
                            .withAvailableSubProcessPorts()
                            .gossipCaCertificate(gossipCertificates.get(1).getEncoded()));
        }

        @HapiTest
        @DisplayName("validate address book with node5 node6")
        final Stream<DynamicTest> addedNodeTest() {
            return hapiTest(
                    prepareFakeUpgrade(),
                    validateUpgradeAddressBooks(
                            addressBook -> assertThat(nodeIdsFrom(addressBook)).contains(4L, 5L)),
                    upgradeToNextConfigVersion(FakeNmt.addNode(Set.of(4L, 5L), DAB_GENERATED, true)));
        }
    }

    @Nested
    @DisplayName("update one node, node2")
    class UpdateOneNode {
        @BeforeAll
        static void beforeAll(@NonNull final TestLifecycle testLifecycle) throws CertificateEncodingException {
            testLifecycle.doAdhoc(nodeUpdate("1")
                    .signedBy(DEFAULT_PAYER)
                    .gossipEndpoint(List.of(
                            asServiceEndpoint("127.0.0.1:40000"),
                            asServiceEndpoint("127.0.0.1:40001"),
                            asServiceEndpoint("127.0.0.3:60")))
                    .serviceEndpoint(List.of(asServiceEndpoint("127.0.1.1:60"), asServiceEndpoint("127.0.1.1:60")))
                    .gossipCaCertificate(gossipCertificates.getLast().getEncoded())
                    .grpcCertificateHash("grpcCert".getBytes()));
        }

        @HapiTest
        @DisplayName("validate address book with node2")
        final Stream<DynamicTest> updatedNodeTest() {
            return hapiTest(
                    prepareFakeUpgrade(),
                    validateUpgradeAddressBooks(addressBook -> {
                        final var address = nodeAddressFrom(addressBook, 1);
                        assertEquals(address.getSelfName(), "node2");
                        assertEquals(address.getHostnameInternal(), "127.0.0.1");
                        assertEquals(address.getPortInternal(), 40000);
                        assertEquals(address.getHostnameExternal(), "127.0.0.1");
                        assertEquals(address.getPortExternal(), 40001);
                    }),
                    upgradeToNextConfigVersion(FakeNmt.updateNode(
                            byNodeId(1),
                            DAB_GENERATED,
                            classicMetadataFor(1, "networkName", "node2", null, 0, 0, 0, 0),
                            true,
                            byNodeId(1))));
        }
    }

    @Nested
    @DisplayName("update all nodes, node1, node2, node3, node4")
    class UpdateAllNodes {
        @BeforeAll
        static void beforeAll(@NonNull final TestLifecycle testLifecycle) throws CertificateEncodingException {
            testLifecycle.doAdhoc(
                    nodeUpdate("0")
                            .signedBy(DEFAULT_PAYER)
                            .gossipEndpoint(List.of(
                                    asServiceEndpoint("127.0.0.1:40000"), asServiceEndpoint("127.0.0.1:40001"))),
                    nodeUpdate("1")
                            .signedBy(DEFAULT_PAYER)
                            .gossipEndpoint(List.of(
                                    asServiceEndpoint("127.0.0.1:50000"), asServiceEndpoint("127.0.0.1:50001"))),
                    nodeUpdate("2")
                            .signedBy(DEFAULT_PAYER)
                            .gossipEndpoint(List.of(
                                    asServiceEndpoint("127.0.0.1:60000"), asServiceEndpoint("127.0.0.1:60001"))),
                    nodeUpdate("3")
                            .signedBy(DEFAULT_PAYER)
                            .gossipEndpoint(List.of(
                                    asServiceEndpoint("127.0.0.1:70000"), asServiceEndpoint("127.0.0.1:70001"))));
        }

        @HapiTest
        @DisplayName("validate address book with all nodes")
        final Stream<DynamicTest> updatedNodeTest() {
            return hapiTest(
                    prepareFakeUpgrade(),
                    validateUpgradeAddressBooks(addressBook -> {
                        var address = nodeAddressFrom(addressBook, 0);
                        assertEquals(address.getPortInternal(), 40000);
                        assertEquals(address.getPortExternal(), 40001);
                        address = nodeAddressFrom(addressBook, 1);
                        assertEquals(address.getPortInternal(), 50000);
                        assertEquals(address.getPortExternal(), 50001);
                        address = nodeAddressFrom(addressBook, 2);
                        assertEquals(address.getPortInternal(), 60000);
                        assertEquals(address.getPortExternal(), 60001);
                        address = nodeAddressFrom(addressBook, 3);
                        assertEquals(address.getPortInternal(), 70000);
                        assertEquals(address.getPortExternal(), 70001);
                    }),
                    upgradeToNextConfigVersion(
                            FakeNmt.updateNode(
                                    byNodeId(0),
                                    DAB_GENERATED,
                                    classicMetadataFor(0, "networkName", "node1", null, 0, 0, 0, 0),
                                    true,
                                    byNodeId(0)),
                            FakeNmt.updateNode(
                                    byNodeId(1),
                                    DAB_GENERATED,
                                    classicMetadataFor(1, "networkName", "node2", null, 0, 0, 0, 0),
                                    true,
                                    byNodeId(1)),
                            FakeNmt.updateNode(
                                    byNodeId(2),
                                    DAB_GENERATED,
                                    classicMetadataFor(2, "networkName", "node3", null, 0, 0, 0, 0),
                                    true,
                                    byNodeId(2)),
                            FakeNmt.updateNode(
                                    byNodeId(3),
                                    DAB_GENERATED,
                                    classicMetadataFor(3, "networkName", "node4", null, 0, 0, 0, 0),
                                    true,
                                    byNodeId(3))));
        }
    }

    @Nested
    @DisplayName("delete one node, node2")
    class DeleteOneNode {
        @BeforeAll
        static void beforeAll(@NonNull final TestLifecycle testLifecycle) {
            testLifecycle.doAdhoc(nodeDelete("1"));
        }

        @HapiTest
        @DisplayName("validate address book with node2")
        final Stream<DynamicTest> removedNodeTest() {
            return hapiTest(
                    prepareFakeUpgrade(),
                    validateUpgradeAddressBooks(
                            addressBook -> assertThat(nodeIdsFrom(addressBook)).containsExactlyInAnyOrder(0L, 2L, 3L)),
                    upgradeToNextConfigVersion(FakeNmt.removeNode(byNodeId(1), DAB_GENERATED, exceptNodeId(1), null)),
                    waitUntilStartOfNextStakingPeriod(1).withBackgroundTraffic(),
                    touchBalanceOf(NODE0_STAKER, NODE2_STAKER, NODE3_STAKER).andAssertStakingRewardCount(3),
                    touchBalanceOf(NODE1_STAKER).andAssertStakingRewardCount(0));
        }
    }

    @Nested
    @DisplayName("add one node, node5, update it too")
    class AddAndUpdateOneNode {
        private static final AccountID NEW_ACCOUNT_ID =
                AccountID.newBuilder().setAccountNum(8L).build();

        @BeforeAll
        static void beforeAll(@NonNull final TestLifecycle testLifecycle) throws CertificateEncodingException {
            testLifecycle.doAdhoc(
                    newKeyNamed("adminKey"),
                    nodeCreate("node5")
                            .adminKey("adminKey")
                            .accountId(NEW_ACCOUNT_ID)
                            .description(CLASSIC_NODE_NAMES[4])
                            .withAvailableSubProcessPorts()
                            .gossipCaCertificate(gossipCertificates.getFirst().getEncoded()),
                    nodeUpdate("4")
                            .signedBy(DEFAULT_PAYER, "adminKey")
                            .gossipEndpoint(List.of(
                                    asServiceEndpoint("127.0.0.1:40000"), asServiceEndpoint("127.0.0.1:40001"))));
        }

        @HapiTest
        @DisplayName("validate address book with node5")
        final Stream<DynamicTest> addUpdateNodeTest() {
            return hapiTest(
                    prepareFakeUpgrade(),
                    validateUpgradeAddressBooks(addressBook -> {
                        assertThat(nodeIdsFrom(addressBook)).contains(4L);
                        var address = nodeAddressFrom(addressBook, 4);
                        assertEquals(address.getPortInternal(), 40000);
                        assertEquals(address.getPortExternal(), 40001);
                    }),
                    upgradeToNextConfigVersion(
                            FakeNmt.addNode(Set.of(4L), DAB_GENERATED, false),
                            FakeNmt.updateNode(
                                    byNodeId(4),
                                    DAB_GENERATED,
                                    classicMetadataFor(4, "networkName", "node5", null, 0, 0, 0, 0),
                                    true,
                                    exceptNodeId(4))));
        }
    }

    @Nested
    @DisplayName("delete last node, node4, create new node, node5")
    class DeleteLastNodeCreateNode {
        private static final AccountID NEW_ACCOUNT_ID =
                AccountID.newBuilder().setAccountNum(7L).build();

        @BeforeAll
        static void beforeAll(@NonNull final TestLifecycle testLifecycle) throws CertificateEncodingException {
            testLifecycle.doAdhoc(
                    nodeDelete("3"),
                    nodeCreate("node5")
                            .accountId(NEW_ACCOUNT_ID)
                            .description(CLASSIC_NODE_NAMES[4])
                            .withAvailableSubProcessPorts()
                            .gossipCaCertificate(gossipCertificates.getFirst().getEncoded()));
        }

        @HapiTest
        @DisplayName("validate address book with nodes")
        final Stream<DynamicTest> removeCreateNodeTest() {
            return hapiTest(
                    prepareFakeUpgrade(),
                    validateUpgradeAddressBooks(addressBook ->
                            assertThat(nodeIdsFrom(addressBook)).containsExactlyInAnyOrder(0L, 1L, 2L, 4L)),
                    upgradeToNextConfigVersion(
                            FakeNmt.removeNode(byNodeId(3), DAB_GENERATED, allNodes(), null),
                            FakeNmt.addNode(Set.of(4L), DAB_GENERATED, true)));
        }
    }

    @Nested
    @DisplayName("create node5 node6, update node2, node6, delete node3, node5")
    class AddUpdateDeleteNode {
        private static final AccountID NEW_ACCOUNT_ID =
                AccountID.newBuilder().setAccountNum(7L).build();
        private static final AccountID NEW_ACCOUNT_ID_2 =
                AccountID.newBuilder().setAccountNum(8L).build();

        @BeforeAll
        static void beforeAll(@NonNull final TestLifecycle testLifecycle) throws CertificateEncodingException {
            testLifecycle.doAdhoc(
                    newKeyNamed("adminKey"),
                    nodeCreate("node5")
                            .accountId(NEW_ACCOUNT_ID)
                            .description(CLASSIC_NODE_NAMES[4])
                            .withAvailableSubProcessPorts()
                            .gossipCaCertificate(gossipCertificates.getFirst().getEncoded()),
                    nodeCreate("node6")
                            .adminKey("adminKey")
                            .accountId(NEW_ACCOUNT_ID_2)
                            .description(CLASSIC_NODE_NAMES[5])
                            .withAvailableSubProcessPorts()
                            .gossipCaCertificate(gossipCertificates.get(1).getEncoded()),
                    nodeUpdate("1")
                            .signedBy(DEFAULT_PAYER)
                            .gossipEndpoint(List.of(
                                    asServiceEndpoint("127.0.0.1:40000"), asServiceEndpoint("127.0.0.1:40001"))),
                    nodeUpdate("5")
                            .signedBy(DEFAULT_PAYER, "adminKey")
                            .gossipEndpoint(List.of(
                                    asServiceEndpoint("127.0.0.1:50000"), asServiceEndpoint("127.0.0.1:50001"))),
                    nodeDelete("2"));
            //                                nodeDelete("4"));
        }

        @HapiTest
        @DisplayName("validate address book with the changes")
        final Stream<DynamicTest> addUpdateDeleteNodeTest() {
            return hapiTest(
                    prepareFakeUpgrade(),
                    validateUpgradeAddressBooks(addressBook -> {
                        //                        assertThat(nodeIdsFrom(addressBook)).contains(0L, 1L, 3L, 5L);
                        assertThat(nodeIdsFrom(addressBook)).contains(0L, 1L, 3L, 4L, 5L);
                        //                        assertThat(nodeIdsFrom(addressBook)).contains(0L, 1L, 2L, 3L, 5L);
                        var address = nodeAddressFrom(addressBook, 1);
                        assertEquals(address.getPortInternal(), 40000);
                        assertEquals(address.getPortExternal(), 40001);
                        address = nodeAddressFrom(addressBook, 5);
                        assertEquals(address.getPortInternal(), 50000);
                        assertEquals(address.getPortExternal(), 50001);
                    }),
                    upgradeToNextConfigVersion(
                            FakeNmt.addNode(Set.of(4L, 5L), DAB_GENERATED, true),
                            FakeNmt.updateNode(
                                    byNodeId(1),
                                    DAB_GENERATED,
                                    classicMetadataFor(1, "networkName", "node2", null, 0, 0, 0, 0),
                                    false,
                                    byNodeId(1)),
                            FakeNmt.updateNode(
                                    byNodeId(5),
                                    DAB_GENERATED,
                                    classicMetadataFor(5, "networkName", "node6", null, 0, 0, 0, 0),
                                    true,
                                    exceptNodeId(1, 4, 5)),
                            FakeNmt.removeNode(byNodeId(2), DAB_GENERATED, exceptNodeId(4, 5), byNodeId(1))));
            //                                        FakeNmt.removeNode(byNodeId(4), DAB_GENERATED, exceptNodeId(4, 5),
            // byNodeId(1))));
        }
    }
}

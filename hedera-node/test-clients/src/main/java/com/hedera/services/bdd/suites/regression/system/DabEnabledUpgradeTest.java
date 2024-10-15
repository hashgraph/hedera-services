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

import static com.hedera.services.bdd.junit.SharedNetworkLauncherSessionListener.CLASSIC_HAPI_TEST_NETWORK_SIZE;
import static com.hedera.services.bdd.junit.TestTags.UPGRADE;
import static com.hedera.services.bdd.junit.hedera.NodeSelector.byNodeId;
import static com.hedera.services.bdd.junit.hedera.NodeSelector.exceptNodeIds;
import static com.hedera.services.bdd.junit.hedera.subprocess.UpgradeConfigTxt.DAB_GENERATED;
import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.CLASSIC_NODE_NAMES;
import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.nodeIdsFrom;
import static com.hedera.services.bdd.spec.HapiPropertySource.asServiceEndpoint;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.dsl.operations.transactions.TouchBalancesOperation.touchBalanceOf;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getVersionInfo;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.sysFileUpdateTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.ensureStakingActivated;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.given;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.recordStreamMustIncludePassFrom;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.selectedItems;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateUpgradeAddressBooks;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitUntilStartOfNextStakingPeriod;
import static com.hedera.services.bdd.spec.utilops.streams.assertions.VisibleItemsValidator.EXISTENCE_ONLY_VALIDATOR;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.FUNDING;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_BILLION_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hedera.services.bdd.suites.hip869.NodeCreateTest.generateX509Certificates;
import static com.hedera.services.bdd.suites.regression.system.LifecycleTest.configVersionOf;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_NODES_CREATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.OrderedInIsolation;
import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.hedera.NodeSelector;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.dsl.annotations.Account;
import com.hedera.services.bdd.spec.dsl.entities.SpecAccount;
import com.hedera.services.bdd.spec.utilops.FakeNmt;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.swirlds.common.AddressBook;
import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Asserts expected behavior of the network when upgrading with DAB enabled.
 * <p>
 * The test framework simulates DAB by copying the <i>config.txt</i> from the node's upgrade artifacts into their
 * working directories, instead of regenerating a <i>config.txt</i> to match its {@link HederaNode} instances. It
 * <p>
 * There are three upgrades in this test. The first leaves the address book unchanged, the second removes `node1`,
 * and the last one adds a new `node5`.
 * <p>
 * Halfway through the sequence, we also verify that reconnect is still possible  with only `node0` and `node2`
 * left online while `node3` reconnects; which we accomplish by giving most of the stake to those nodes.
 * <p>
 * We also verify that an account staking to a deleted node cannot earn rewards.
 * <p>
 * See <a href="https://github.com/hashgraph/hedera-improvement-proposal/blob/main/HIP/hip-869.md#user-stories">here</a>
 * for the associated HIP-869 user stories.
 * <p>
 * Since this test upgrades the software version, it must run after any other test that does a restart assuming
 * the config version is still zero.
 */
@Tag(UPGRADE)
@Order(Integer.MAX_VALUE - 2)
@DisplayName("Upgrading with DAB enabled")
@HapiTestLifecycle
@OrderedInIsolation
public class DabEnabledUpgradeTest implements LifecycleTest {
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
                given(() -> gossipCertificates = generateX509Certificates(4)));
    }

    @Nested
    @Order(0)
    @DisplayName("with unchanged nodes")
    class WithUnchangedNodes {
        @HapiTest
        @DisplayName("exports the original address book")
        final Stream<DynamicTest> sameNodesTest() {
            final AtomicReference<SemanticVersion> startVersion = new AtomicReference<>();
            return hapiTest(
                    recordStreamMustIncludePassFrom(selectedItems(
                            EXISTENCE_ONLY_VALIDATOR, 2, sysFileUpdateTo("files.nodeDetails", "files.addressBook"))),
                    getVersionInfo().exposingServicesVersionTo(startVersion::set),
                    prepareFakeUpgrade(),
                    validateUpgradeAddressBooks(DabEnabledUpgradeTest::hasClassicAddressMetadata),
                    upgradeToNextConfigVersion(),
                    assertExpectedConfigVersion(startVersion::get),
                    // Ensure we have a post-upgrade transaction to trigger system file exports
                    cryptoCreate("somebodyNew"));
        }
    }

    @Nested
    @Order(1)
    @DisplayName("after removing node id1")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class AfterRemovingNodeId1 {
        @BeforeAll
        static void beforeAll(@NonNull final TestLifecycle testLifecycle) {
            testLifecycle.doAdhoc(nodeDelete("1"));
        }

        @HapiTest
        @Order(0)
        @DisplayName("exports an address book without node1 and pays its stake no rewards")
        final Stream<DynamicTest> removedNodeTest() {
            return hapiTest(
                    recordStreamMustIncludePassFrom(selectedItems(
                            EXISTENCE_ONLY_VALIDATOR, 2, sysFileUpdateTo("files.nodeDetails", "files.addressBook"))),
                    prepareFakeUpgrade(),
                    validateUpgradeAddressBooks(
                            addressBook -> assertThat(nodeIdsFrom(addressBook)).containsExactlyInAnyOrder(0L, 2L, 3L)),
                    upgradeToNextConfigVersion(FakeNmt.removeNode(byNodeId(1), DAB_GENERATED)),
                    waitUntilStartOfNextStakingPeriod(1).withBackgroundTraffic(),
                    touchBalanceOf(NODE0_STAKER, NODE2_STAKER, NODE3_STAKER).andAssertStakingRewardCount(3),
                    touchBalanceOf(NODE1_STAKER).andAssertStakingRewardCount(0));
        }

        @HapiTest
        @Order(1)
        @DisplayName("can still reconnect node id3 since node id0 and node id2 alone have majority weight")
        final Stream<DynamicTest> nodeId3ReconnectTest() {
            final AtomicReference<SemanticVersion> startVersion = new AtomicReference<>();
            return hapiTest(
                    getVersionInfo().exposingServicesVersionTo(startVersion::set),
                    sourcing(() -> reconnectNode(byNodeId(3), configVersionOf(startVersion.get()))));
        }
    }

    @Nested
    @Order(2)
    @DisplayName("after removing last node id3")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class AfterRemovingNodeId3 {
        @BeforeAll
        static void beforeAll(@NonNull final TestLifecycle testLifecycle) {
            testLifecycle.doAdhoc(nodeDelete("3"));
        }

        @HapiTest
        @Order(0)
        @DisplayName("exports an address book without node id3 and pays its stake no rewards")
        final Stream<DynamicTest> removedNodeTest() {
            return hapiTest(
                    recordStreamMustIncludePassFrom(selectedItems(
                            EXISTENCE_ONLY_VALIDATOR, 2, sysFileUpdateTo("files.nodeDetails", "files.addressBook"))),
                    prepareFakeUpgrade(),
                    validateUpgradeAddressBooks(
                            addressBook -> assertThat(nodeIdsFrom(addressBook)).containsExactlyInAnyOrder(0L, 2L)),
                    upgradeToNextConfigVersion(FakeNmt.removeNode(byNodeId(3), DAB_GENERATED)),
                    waitUntilStartOfNextStakingPeriod(1).withBackgroundTraffic(),
                    touchBalanceOf(NODE0_STAKER, NODE2_STAKER).andAssertStakingRewardCount(2),
                    touchBalanceOf(NODE3_STAKER).andAssertStakingRewardCount(0));
        }
    }

    @Nested
    @Order(3)
    @DisplayName("after adding new node id4")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class AfterAddingNodeId4 {
        @BeforeAll
        static void beforeAll(@NonNull final TestLifecycle testLifecycle) throws CertificateEncodingException {
            testLifecycle.doAdhoc(nodeCreate("node4")
                    .adminKey(DEFAULT_PAYER)
                    .accountId(classicFeeCollectorIdFor(4))
                    .description(CLASSIC_NODE_NAMES[4])
                    .withAvailableSubProcessPorts()
                    .gossipCaCertificate(gossipCertificates.getFirst().getEncoded()));
        }

        @HapiTest
        @Order(0)
        @DisplayName("exports an address book with node id4")
        final Stream<DynamicTest> exportedAddressBookIncludesNodeId4() {
            return hapiTest(
                    recordStreamMustIncludePassFrom(selectedItems(
                            EXISTENCE_ONLY_VALIDATOR, 2, sysFileUpdateTo("files.nodeDetails", "files.addressBook"))),
                    prepareFakeUpgrade(),
                    // node4 was not active before this the upgrade, so it could not have written a config.txt
                    validateUpgradeAddressBooks(exceptNodeIds(4L), addressBook -> assertThat(nodeIdsFrom(addressBook))
                            .contains(4L)),
                    upgradeToNextConfigVersion(FakeNmt.addNode(4L, DAB_GENERATED)),
                    // Ensure we have a post-upgrade transaction to trigger system file exports
                    cryptoCreate("somebodyNew"));
        }
    }

    @Nested
    @Order(4)
    @DisplayName("with multipart DAB edits before and after prepare upgrade")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class WithMultipartDabEditsBeforeAndAfterPrepareUpgrade {
        @BeforeAll
        static void beforeAll(@NonNull final TestLifecycle testLifecycle) throws CertificateEncodingException {
            testLifecycle.overrideInClass(Map.of(
                    // Note that deleted nodes still count against the max number of nodes
                    "nodes.maxNumber", "7",
                    "nodes.updateAccountIdAllowed", "true"));
            // Do a combination of node creates, deletes, and updates with a disallowed create in the middle;
            // all the successful edits here are before issuing PREPARE_UPGRADE and should be reflected in the
            // address book after the upgrade
            testLifecycle.doAdhoc(
                    nodeCreate("node5")
                            .adminKey(DEFAULT_PAYER)
                            .accountId(classicFeeCollectorIdFor(5))
                            .description(CLASSIC_NODE_NAMES[5])
                            .withAvailableSubProcessPorts()
                            .gossipCaCertificate(gossipCertificates.get(1).getEncoded()),
                    nodeCreate("toBeDeletedNode6")
                            .adminKey(DEFAULT_PAYER)
                            .accountId(classicFeeCollectorIdFor(6))
                            .description(CLASSIC_NODE_NAMES[6])
                            .withAvailableSubProcessPorts()
                            .gossipCaCertificate(gossipCertificates.get(2).getEncoded()),
                    nodeCreate("disallowedNode7")
                            .adminKey(DEFAULT_PAYER)
                            .accountId(classicFeeCollectorIdFor(7))
                            .description(CLASSIC_NODE_NAMES[7])
                            .withAvailableSubProcessPorts()
                            .gossipCaCertificate(gossipCertificates.get(3).getEncoded())
                            .hasKnownStatus(MAX_NODES_CREATED),
                    // Delete a pending node
                    nodeDelete("6"),
                    // Delete an already active node
                    nodeDelete("4"),
                    // Update a pending node
                    nodeUpdate("node5")
                            // These endpoints will be replaced by the FakeNmt process just before
                            // restart but can still be validated in the DAB-generated config.txt
                            .gossipEndpoint(
                                    List.of(asServiceEndpoint("127.0.0.1:33000"), asServiceEndpoint("127.0.0.1:33001")))
                            .accountId(classicFeeCollectorIdLiteralFor(905)),
                    // Update an existing node
                    nodeUpdate("2").accountId(classicFeeCollectorIdLiteralFor(902)));
        }

        @HapiTest
        @Order(0)
        @DisplayName("exported address book reflects only edits before prepare upgrade")
        final Stream<DynamicTest> exportedAddressBookReflectsOnlyEditsBeforePrepareUpgrade() {
            return hapiTest(
                    recordStreamMustIncludePassFrom(selectedItems(
                            EXISTENCE_ONLY_VALIDATOR, 2, sysFileUpdateTo("files.nodeDetails", "files.addressBook"))),
                    prepareFakeUpgrade(),
                    // Now make some changes that should not be incorporated in this upgrade
                    nodeDelete("5"),
                    nodeDelete("2"),
                    validateUpgradeAddressBooks(NodeSelector.allNodes(), DabEnabledUpgradeTest::validateMultipartEdits),
                    upgradeToNextConfigVersion(
                            FakeNmt.removeNode(NodeSelector.byNodeId(4L), DAB_GENERATED),
                            FakeNmt.addNode(5L, DAB_GENERATED)),
                    // Validate that nodeId2 and nodeId5 have their new fee collector account IDs,
                    // since those were updated before the prepare upgrade
                    cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L))
                            .setNode(classicFeeCollectorIdLiteralFor(902)),
                    cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L))
                            .setNode(classicFeeCollectorIdLiteralFor(905)),
                    // Validate that nodeId0 still has the classic fee collector account ID, since
                    // it was updated after the prepare upgrade
                    cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L))
                            .setNode(classicFeeCollectorIdLiteralFor(0)));
        }
    }

    private static void validateMultipartEdits(@NonNull final AddressBook addressBook) {
        assertThat(nodeIdsFrom(addressBook)).containsExactlyInAnyOrder(0L, 2L, 5L);
        final var node0 = addressBook.getAddress(new NodeId(0L));
        assertEquals(classicFeeCollectorIdLiteralFor(0), node0.getMemo());
        final var node2 = addressBook.getAddress(new NodeId(2L));
        assertEquals(classicFeeCollectorIdLiteralFor(902), node2.getMemo());
        final var node5 = addressBook.getAddress(new NodeId(5L));
        assertEquals(classicFeeCollectorIdLiteralFor(905), node5.getMemo());
        assertEquals("127.0.0.1", node5.getHostnameInternal());
        assertEquals(33000, node5.getPortInternal());
        assertEquals("127.0.0.1", node5.getHostnameExternal());
        assertEquals(33001, node5.getPortExternal());
    }

    private static void hasClassicAddressMetadata(@NonNull AddressBook addressBook) {
        assertEquals(CLASSIC_HAPI_TEST_NETWORK_SIZE, addressBook.getSize(), "Wrong size");
        addressBook.forEach(address -> {
            final var i = (int) address.getNodeId().id();
            assertEquals("" + i, address.getNickname(), "Wrong nickname");
            assertEquals(CLASSIC_NODE_NAMES[i], address.getSelfName(), "Wrong self-name");
        });
    }

    private static AccountID classicFeeCollectorIdFor(final long nodeId) {
        return AccountID.newBuilder().setAccountNum(nodeId + 3L).build();
    }

    private static String classicFeeCollectorIdLiteralFor(final long nodeId) {
        return "0.0." + (nodeId + 3L);
    }
}

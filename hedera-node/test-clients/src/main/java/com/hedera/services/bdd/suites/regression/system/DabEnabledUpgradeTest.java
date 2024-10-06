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
import static com.hedera.services.bdd.junit.hedera.NodeSelector.exceptNodeId;
import static com.hedera.services.bdd.junit.hedera.subprocess.UpgradeConfigTxt.DAB_GENERATED;
import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.CLASSIC_NODE_NAMES;
import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.nodeIdsFrom;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.dsl.operations.transactions.TouchBalancesOperation.touchBalanceOf;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getVersionInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeDelete;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.ensureStakingActivated;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.given;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateUpgradeAddressBooks;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitUntilStartOfNextStakingPeriod;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_BILLION_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hedera.services.bdd.suites.hip869.NodeCreateTest.generateX509Certificates;
import static com.hedera.services.bdd.suites.regression.system.LifecycleTest.configVersionOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.OrderedInIsolation;
import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.dsl.annotations.Account;
import com.hedera.services.bdd.spec.dsl.entities.SpecAccount;
import com.hedera.services.bdd.spec.utilops.FakeNmt;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.List;
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
                given(() -> gossipCertificates = generateX509Certificates(1)));
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
                    getVersionInfo().exposingServicesVersionTo(startVersion::set),
                    prepareFakeUpgrade(),
                    validateUpgradeAddressBooks(DabEnabledUpgradeTest::hasClassicAddressMetadata),
                    upgradeToNextConfigVersion(),
                    assertExpectedConfigVersion(startVersion::get));
        }
    }

    @Nested
    @Order(1)
    @DisplayName("after removing node1")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class AfterRemovingNode1 {
        @BeforeAll
        static void beforeAll(@NonNull final TestLifecycle testLifecycle) {
            testLifecycle.doAdhoc(nodeDelete("1"));
        }

        @HapiTest
        @Order(0)
        @DisplayName("exports an address book without node1 and pays its stake no rewards")
        final Stream<DynamicTest> removedNodeTest() {
            return hapiTest(
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
        @DisplayName("can still reconnect node3 since node0 and node2 alone have majority weight")
        final Stream<DynamicTest> node3ReconnectTest() {
            final AtomicReference<SemanticVersion> startVersion = new AtomicReference<>();
            return hapiTest(
                    getVersionInfo().exposingServicesVersionTo(startVersion::set),
                    sourcing(() -> reconnectNode(byNodeId(3), configVersionOf(startVersion.get()))));
        }
    }

    @Nested
    @Order(2)
    @DisplayName("after removing last node 3")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class AfterRemovingNode3 {
        @BeforeAll
        static void beforeAll(@NonNull final TestLifecycle testLifecycle) {
            testLifecycle.doAdhoc(nodeDelete("3"));
        }

        @HapiTest
        @Order(0)
        @DisplayName("exports an address book without node 3 and pays its stake no rewards")
        final Stream<DynamicTest> removedNodeTest() {
            return hapiTest(
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
    @DisplayName("after adding node4")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class AfterAddingNode4 {
        private static final AccountID NEW_ACCOUNT_ID =
                AccountID.newBuilder().setAccountNum(7L).build();

        @BeforeAll
        static void beforeAll(@NonNull final TestLifecycle testLifecycle) throws CertificateEncodingException {
            testLifecycle.doAdhoc(nodeCreate("node4")
                    .accountId(NEW_ACCOUNT_ID)
                    .description(CLASSIC_NODE_NAMES[4])
                    .withAvailableSubProcessPorts()
                    .gossipCaCertificate(gossipCertificates.getFirst().getEncoded()));
        }

        @HapiTest
        @Order(0)
        @DisplayName("exports an address book with node4")
        final Stream<DynamicTest> addedNodeTest() {
            return hapiTest(
                    prepareFakeUpgrade(),
                    // node4 was not active before this the upgrade, so it could not have written a config.txt
                    validateUpgradeAddressBooks(exceptNodeId(4L), addressBook -> assertThat(nodeIdsFrom(addressBook))
                            .contains(4L)),
                    upgradeToNextConfigVersion(FakeNmt.addNode(4L, DAB_GENERATED)));
        }
    }

    private static void hasClassicAddressMetadata(@NonNull AddressBook addressBook) {
        assertEquals(CLASSIC_HAPI_TEST_NETWORK_SIZE, addressBook.getSize(), "Wrong size");
        addressBook.forEach(address -> {
            final var i = (int) address.getNodeId().id();
            assertEquals("" + i, address.getNickname(), "Wrong nickname");
            assertEquals(CLASSIC_NODE_NAMES[i], address.getSelfName(), "Wrong self-name");
        });
    }
}

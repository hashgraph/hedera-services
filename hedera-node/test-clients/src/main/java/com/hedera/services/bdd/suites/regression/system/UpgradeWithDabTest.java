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
import static com.hedera.services.bdd.junit.hedera.subprocess.UpgradeConfigTxt.DAB_GENERATED;
import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.nodeIdsFrom;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.dsl.operations.transactions.TouchBalancesOperation.touchBalanceOf;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getVersionInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeDelete;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.ensureStakingActivated;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.removeNodeAndRefreshConfigTxt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateUpgradeAddressBooks;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitUntilStartOfNextStakingPeriod;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_BILLION_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hedera.services.bdd.suites.regression.system.LifecycleTest.configVersionOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.OrderedInIsolation;
import com.hedera.services.bdd.junit.support.SpecManager;
import com.hedera.services.bdd.spec.dsl.annotations.AccountSpec;
import com.hedera.services.bdd.spec.dsl.entities.SpecAccount;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
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

// NOTE - not currently running in CI
@Tag(UPGRADE)
@DisplayName("Upgrading with DAB enabled")
@HapiTestLifecycle
@OrderedInIsolation
public class UpgradeWithDabTest implements LifecycleTest {
    @AccountSpec(balance = ONE_BILLION_HBARS, stakedNodeId = 0)
    static SpecAccount NODE0_STAKER;

    @AccountSpec(balance = ONE_BILLION_HBARS, stakedNodeId = 1)
    static SpecAccount NODE1_STAKER;

    @AccountSpec(balance = ONE_BILLION_HBARS, stakedNodeId = 2)
    static SpecAccount NODE2_STAKER;

    @AccountSpec(balance = ONE_MILLION_HBARS, stakedNodeId = 3)
    static SpecAccount NODE3_STAKER;

    @BeforeAll
    static void beforeAll(@NonNull final SpecManager manager) throws Throwable {
        manager.setup(
                ensureStakingActivated(),
                touchBalanceOf(NODE0_STAKER, NODE1_STAKER, NODE2_STAKER, NODE3_STAKER),
                waitUntilStartOfNextStakingPeriod(1));
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
                    validateUpgradeAddressBooks(
                            addressBook -> assertEquals(CLASSIC_HAPI_TEST_NETWORK_SIZE, addressBook.getSize())),
                    upgradeToConfigVersion(1),
                    assertVersion(startVersion::get, 1));
        }
    }

    @Nested
    @Order(1)
    @DisplayName("after removing node1")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class AfterRemovingNode1 {
        @BeforeAll
        static void beforeAll(@NonNull final SpecManager manager) throws Throwable {
            manager.setup(nodeDelete("1"));
        }

        @HapiTest
        @Order(0)
        @DisplayName("exports an address book without node1 and pays its staker no rewards")
        final Stream<DynamicTest> removedNodeTest() {
            return hapiTest(
                    prepareFakeUpgrade(),
                    validateUpgradeAddressBooks(
                            addressBook -> assertThat(nodeIdsFrom(addressBook)).containsExactlyInAnyOrder(0L, 2L, 3L)),
                    upgradeToConfigVersion(2, removeNodeAndRefreshConfigTxt(byNodeId(1), DAB_GENERATED)),
                    waitUntilStartOfNextStakingPeriod(1),
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
}

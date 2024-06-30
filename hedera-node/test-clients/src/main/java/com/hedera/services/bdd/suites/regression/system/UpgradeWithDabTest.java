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
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getVersionInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeDelete;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.removeNodeAndRefreshConfigTxt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateUpgradeAddressBooks;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.OrderedInIsolation;
import com.hedera.services.bdd.junit.support.SpecManager;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.address.Address;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;

// NOTE - not currently running in CI
@Tag(UPGRADE)
@DisplayName("Upgrading with DAB")
@HapiTestLifecycle
@OrderedInIsolation
public class UpgradeWithDabTest implements LifecycleTest {
    private static final AtomicReference<SemanticVersion> START_VERSION = new AtomicReference<>();

    @BeforeAll
    static void beforeAll(@NonNull final SpecManager manager) throws Throwable {
        manager.setup(getVersionInfo().exposingServicesVersionTo(START_VERSION::set));
    }

    @Nested
    @DisplayName("with unchanged nodes")
    class WithUnchangedNodes {
        @HapiTest
        @DisplayName("exports the original address book")
        final Stream<DynamicTest> sameNodesTest() {
            return hapiTest(
                    prepareFakeUpgrade(),
                    validateUpgradeAddressBooks(
                            addressBook -> assertEquals(CLASSIC_HAPI_TEST_NETWORK_SIZE, addressBook.getSize())),
                    upgradeToConfigVersion(1),
                    assertVersion(START_VERSION::get, 1));
        }
    }

    @Nested
    @DisplayName("after removing node1")
    class AfterRemovingNode1 {
        @BeforeAll
        static void beforeAll(@NonNull final SpecManager manager) throws Throwable {
            manager.setup(nodeDelete("1"));
        }

        @HapiTest
        @DisplayName("exports an address book without node1")
        final Stream<DynamicTest> removedNodeTest() {
            return hapiTest(
                    prepareFakeUpgrade(),
                    validateUpgradeAddressBooks(
                            addressBook -> assertThat(StreamSupport.stream(addressBook.spliterator(), false)
                                            .map(Address::getNodeId)
                                            .map(NodeId::id))
                                    .containsExactlyInAnyOrder(0L, 2L, 3L)),
                    upgradeToConfigVersion(2, removeNodeAndRefreshConfigTxt(byNodeId(1), DAB_GENERATED)),
                    assertVersion(START_VERSION::get, 2));
        }
    }
}

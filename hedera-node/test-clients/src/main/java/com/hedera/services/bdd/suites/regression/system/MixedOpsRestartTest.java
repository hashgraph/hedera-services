/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.junit.TestTags.RESTART;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForActiveNetwork;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.regression.system.MixedOperations.burstOfTps;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.utilops.FakeNmt;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

/**
 * This test is to verify restart functionality. It submits a burst of mixed operations, then
 * freezes all nodes, shuts them down, restarts them, and submits the same burst of mixed operations
 * again.
 */
@Tag(RESTART)
public class MixedOpsRestartTest implements LifecycleTest {
    private static final int MIXED_OPS_BURST_TPS = 50;

    @HapiTest
    final Stream<DynamicTest> restartMixedOps() {
        return hapiTest(
                burstOfTps(MIXED_OPS_BURST_TPS, MIXED_OPS_BURST_DURATION),
                // Freeze the network
                freezeOnly().startingIn(10).seconds().payingWith(GENESIS),
                confirmFreezeAndShutdown(),
                // (Re)start all nodes
                FakeNmt.restartNetwork(),
                // Wait for all nodes to be ACTIVE
                waitForActiveNetwork(RESTART_TIMEOUT),
                // Once nodes come back ACTIVE, submit some operations again
                burstOfTps(MIXED_OPS_BURST_TPS, MIXED_OPS_BURST_DURATION));
    }
}

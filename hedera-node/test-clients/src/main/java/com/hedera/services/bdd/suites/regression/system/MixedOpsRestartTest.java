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
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.restartNetwork;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.shutdownNetworkWithin;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForActiveNetwork;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForFrozenNetwork;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;

import com.hedera.services.bdd.junit.HapiTest;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

/**
 * This test is to verify restart functionality. It submits a burst of mixed operations, then
 * freezes all nodes, shuts them down, restarts them, and submits the same burst of mixed operations
 * again.
 */
@Tag(RESTART)
public class MixedOpsRestartTest {
    private static final int MIXED_OPS_BURST_TPS = 50;
    private static final long PORT_UNBINDING_TIMEOUT_MS = 180_000L;
    private static final Duration MIXED_OPS_BURST_DURATION = Duration.ofSeconds(10);
    private static final Duration FREEZE_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration RESTART_TIMEOUT = Duration.ofSeconds(180);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(60);

    @HapiTest
    final Stream<DynamicTest> restartMixedOps() {
        return defaultHapiSpec("RestartMixedOps")
                .given(
                        // Run some mixed transactions
                        MixedOperations.burstOfTps(MIXED_OPS_BURST_TPS, MIXED_OPS_BURST_DURATION))
                .when(
                        // Freeze the network
                        freezeOnly().startingIn(10).seconds().payingWith(GENESIS),
                        // Wait for all nodes to be in FREEZE status
                        waitForFrozenNetwork(FREEZE_TIMEOUT),
                        // Shut down all nodes, since the platform doesn't automatically go back to ACTIVE status
                        shutdownNetworkWithin(SHUTDOWN_TIMEOUT),
                        // This sleep is needed, since the ports of shutdown nodes may still be in time_wait status,
                        // which will cause an error that address is already in use when restarting nodes.
                        // Sleep long enough (120s or 180 secs for TIME_WAIT status to be finished based on
                        // kernel settings), so restarting nodes succeeds.
                        sleepFor(PORT_UNBINDING_TIMEOUT_MS),
                        // (Re)start all nodes
                        restartNetwork(),
                        // Wait for all nodes to be ACTIVE
                        waitForActiveNetwork(RESTART_TIMEOUT))
                .then(
                        // Once nodes come back ACTIVE, submit some operations again
                        MixedOperations.burstOfTps(MIXED_OPS_BURST_TPS, MIXED_OPS_BURST_DURATION));
    }
}

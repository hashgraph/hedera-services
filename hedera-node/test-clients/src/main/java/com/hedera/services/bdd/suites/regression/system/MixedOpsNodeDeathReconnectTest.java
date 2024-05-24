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

import static com.hedera.services.bdd.junit.TestTags.ND_RECONNECT;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.restartNode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.shutdownWithin;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForActive;

import com.hedera.services.bdd.junit.HapiTest;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

/**
 * This test is to verify reconnect functionality. It submits a burst of mixed operations, then
 * shuts one node,and starts it back after some time. Node will reconnect, and once reconnect is completed
 * submits the same burst of mixed operations again.
 */
@Tag(ND_RECONNECT)
public class MixedOpsNodeDeathReconnectTest {
    private static final int MIXED_OPS_BURST_TPS = 50;
    private static final long PORT_UNBINDING_TIMEOUT_MS = 180_000L;
    private static final Duration MIXED_OPS_BURST_DURATION = Duration.ofSeconds(10);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration RESTART_TO_ACTIVE_TIMEOUT = Duration.ofSeconds(180);

    @HapiTest
    final Stream<DynamicTest> reconnectMixedOps() {
        return defaultHapiSpec("RestartMixedOps")
                .given(
                        // Validate we can initially submit transactions to node2
                        cryptoCreate("nobody").setNode("0.0.5"),
                        // Run some mixed transactions
                        MixedOperations.burstOfTps(MIXED_OPS_BURST_TPS, MIXED_OPS_BURST_DURATION),
                        // Stop node 2
                        shutdownWithin("Carol", SHUTDOWN_TIMEOUT),
                        logIt("Node 2 is supposedly down"),
                        sleepFor(PORT_UNBINDING_TIMEOUT_MS))
                .when(
                        // Submit operations when node 2 is down
                        MixedOperations.burstOfTps(MIXED_OPS_BURST_TPS, MIXED_OPS_BURST_DURATION),
                        // Restart node2
                        restartNode("Carol"),
                        logIt("Node 2 is supposedly restarted"),
                        // Wait for node2 ACTIVE (BUSY and RECONNECT_COMPLETE are too transient to reliably poll for)
                        waitForActive("Carol", RESTART_TO_ACTIVE_TIMEOUT))
                .then(
                        // Run some more transactions
                        MixedOperations.burstOfTps(MIXED_OPS_BURST_TPS, MIXED_OPS_BURST_DURATION),
                        // And validate we can still submit transactions to node2
                        cryptoCreate("somebody").setNode("0.0.5"));
    }
}

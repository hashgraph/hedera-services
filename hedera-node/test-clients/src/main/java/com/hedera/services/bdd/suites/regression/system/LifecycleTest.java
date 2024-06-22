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

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.shutdownNetworkWithin;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForFrozenNetwork;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import java.time.Duration;

public interface LifecycleTest {
    //    long PORT_UNBINDING_TIMEOUT_MS = 180_000L;
    long PORT_UNBINDING_TIMEOUT_MS = 1L;
    Duration FREEZE_TIMEOUT = Duration.ofSeconds(90);
    Duration RESTART_TIMEOUT = Duration.ofSeconds(180);
    Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(60);

    default HapiSpecOperation confirmFreezeAndShutdown() {
        return blockingOrder(
                waitForFrozenNetwork(FREEZE_TIMEOUT),
                // Shut down all nodes, since the platform doesn't automatically go back to ACTIVE status
                shutdownNetworkWithin(SHUTDOWN_TIMEOUT),
                // This sleep is needed, since the ports of shutdown nodes may still be in time_wait status,
                // which will cause an error that address is already in use when restarting nodes.
                // Sleep long enough (120s or 180 secs for TIME_WAIT status to be finished based on
                // kernel settings), so restarting nodes succeeds.
                sleepFor(PORT_UNBINDING_TIMEOUT_MS));
    }
}

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

package com.hedera.services.bdd.suites;

import com.hedera.services.bdd.spec.utilops.records.SnapshotModeOp;

/**
 * Enumerates the different types of network that can be targeted by a test suite. There are some
 * operations (currently just {@link SnapshotModeOp}) that
 * only make sense when running against a certain type of network.
 */
public enum TargetNetworkType {
    /**
     * A network launched by the {@link com.hedera.services.bdd.junit.SharedNetworkLauncherSessionListener}.
     */
    SHARED_HAPI_TEST_NETWORK,
    /**
     * A mono-service network started via Gradle task (can be removed once mono-service is no longer in use).
     */
    STANDALONE_MONO_NETWORK,
    /**
     * A Docker network launched in CI via TestContainers.
     */
    CI_DOCKER_NETWORK,
    /**
     * A long-lived remote network
     */
    REMOTE_NETWORK,
    /**
     * An embedded "network" with a single Hedera instance whose workflows invoked directly, without gRPC.
     */
    EMBEDDED_NETWORK,
}

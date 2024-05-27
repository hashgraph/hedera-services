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

package com.hedera.services.bdd.junit.hedera;

import com.hedera.services.bdd.suites.TargetNetworkType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.List;

/**
 * A network of Hedera nodes.
 */
public interface HederaNetwork {
    /**
     * Returns the network type; for now this is always
     * {@link TargetNetworkType#SHARED_HAPI_TEST_NETWORK}.
     *
     * @return the network type
     */
    TargetNetworkType type();

    /**
     * Returns the nodes of the network.
     *
     * @return the nodes of the network
     */
    List<HederaNode> nodes();

    /**
     * Returns the nodes of the network that match the given selector.
     *
     * @param selector the selector
     * @return the nodes that match the selector
     */
    List<HederaNode> nodesFor(@NonNull NodeSelector selector);

    /**
     * Returns the node of the network that matches the given selector.
     *
     * @param selector the selector
     * @return the nodes that match the selector
     */
    HederaNode getRequiredNode(@NonNull NodeSelector selector);

    /**
     * Returns the name of the network.
     *
     * @return the name of the network
     */
    String name();

    /**
     * Starts all nodes in the network.
     */
    void start();

    /**
     * Forcibly stops all nodes in the network.
     */
    void terminate();

    /**
     * Waits for all nodes in the network to be ready within the given timeout.
     */
    void awaitReady(@NonNull Duration timeout);
}

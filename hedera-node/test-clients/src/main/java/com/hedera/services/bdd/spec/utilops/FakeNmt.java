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

package com.hedera.services.bdd.spec.utilops;

import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.junit.hedera.NodeSelector;
import com.hedera.services.bdd.junit.hedera.subprocess.SubProcessNode;
import com.hedera.services.bdd.junit.hedera.subprocess.UpgradeConfigTxt;
import com.hedera.services.bdd.spec.utilops.lifecycle.ops.ShutdownWithinOp;
import com.hedera.services.bdd.spec.utilops.lifecycle.ops.TryToStartNodesOp;
import com.hedera.services.bdd.spec.utilops.upgrade.AddNodeOp;
import com.hedera.services.bdd.spec.utilops.upgrade.RemoveNodeOp;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;

/**
 * Contains operations that in a real environment could only be accomplished by the
 * Network Management Tool responding to marker files written by the nodes.
 */
public class FakeNmt {
    /**
     * Private constructor to prevent instantiation.
     *
     * @throws UnsupportedOperationException if invoked by reflection or other means.
     */
    private FakeNmt() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns an operation that restarts the network with the given config version.
     *
     * @param configVersion the config version to use
     * @return the operation that restarts the network
     */
    public static TryToStartNodesOp restartNetwork(final int configVersion) {
        return new TryToStartNodesOp(NodeSelector.allNodes(), configVersion, SubProcessNode.ReassignPorts.YES);
    }

    /**
     * Returns an operation that removes a subprocess node from the network and refreshes the
     * address books on all remaining nodes using the given <i>config.txt</i> source.
     *
     * @param selector the selector for the node to remove
     * @param upgradeConfigTxt the source of the new <i>config.txt</i> file
     * @return the operation that removes the node
     */
    public static RemoveNodeOp removeNode(
            @NonNull final NodeSelector selector, @NonNull final UpgradeConfigTxt upgradeConfigTxt) {
        return new RemoveNodeOp(selector, upgradeConfigTxt);
    }

    /**
     * Returns an operation that removes a subprocess node from the network and refreshes the
     * address books on all remaining nodes using the given <i>config.txt</i> source.
     *
     * @param nodeId id of the node to add
     * @param upgradeConfigTxt the source of the new <i>config.txt</i> file
     * @return the operation that removes the node
     */
    public static AddNodeOp addNode(final long nodeId, @NonNull final UpgradeConfigTxt upgradeConfigTxt) {
        return new AddNodeOp(nodeId, upgradeConfigTxt);
    }

    /**
     * Returns an operation that restarts the network.
     *
     * @return the operation that restarts the network
     */
    public static TryToStartNodesOp restartNetwork() {
        return new TryToStartNodesOp(NodeSelector.allNodes(), 0, SubProcessNode.ReassignPorts.YES);
    }

    /**
     * Returns an operation that restarts the node with the given selector.
     *
     * @param selector the selector for the node to restart
     * @return the operation that restarts the node
     */
    public static TryToStartNodesOp restartNode(@NonNull final NodeSelector selector) {
        return new TryToStartNodesOp(selector);
    }

    /**
     * Returns an operation that restarts the node with the given name.
     *
     * @param name the name of the node to restart
     * @return the operation that restarts the node
     */
    public static TryToStartNodesOp restartNode(@NonNull final String name) {
        return restartNode(NodeSelector.byName(name));
    }

    /**
     * Returns an operation that restarts the selected nodes with the given config version.
     *
     * @param selector the selector for the nodes to restart
     * @param configVersion the config version to use
     * @return the operation that restarts the nodes
     */
    public static TryToStartNodesOp restartWithConfigVersion(
            @NonNull final NodeSelector selector, final int configVersion) {
        return new TryToStartNodesOp(selector, configVersion);
    }

    /**
     * Returns an operation that shuts down the named node within the given timeout.
     * @param name the name of the node to shut down
     * @param timeout the timeout for the shutdown
     * @return the operation that shuts down the node
     */
    public static ShutdownWithinOp shutdownWithin(@NonNull final String name, @NonNull final Duration timeout) {
        return shutdownWithin(NodeSelector.byName(name), timeout);
    }

    /**
     * Returns an operation that shuts down the selected nodes within the given timeout.
     *
     * @param selector the selector for the nodes to shut down
     * @param timeout the timeout for the shutdown
     * @return the operation that shuts down the nodes
     */
    public static ShutdownWithinOp shutdownWithin(
            @NonNull final NodeSelector selector, @NonNull final Duration timeout) {
        requireNonNull(selector);
        requireNonNull(timeout);
        return new ShutdownWithinOp(selector, timeout);
    }

    /**
     * Returns an operation that shuts down the network within the given timeout.
     *
     * @param timeout the timeout for the shutdown
     * @return the operation that shuts down the network
     */
    public static ShutdownWithinOp shutdownNetworkWithin(@NonNull final Duration timeout) {
        return new ShutdownWithinOp(NodeSelector.allNodes(), timeout);
    }
}

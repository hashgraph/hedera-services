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

package com.swirlds.virtualmap.internal.reconnect;

import static com.swirlds.virtualmap.internal.Path.ROOT_PATH;

import com.swirlds.common.merkle.synchronization.task.ReconnectNodeCount;
import com.swirlds.virtualmap.internal.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Virtual node traversal policy, which starts sending requests from the root node and proceeds
 * virtual tree rank by rank, top to bottom. Every node is checked to have a clean parent on
 * its path to the root. If a clean parent is found, the node is skipped (not sent to the teacher).
 *
 * <p>Clean nodes are tracked in a concurrent set. This set is populated in {@link
 * #nodeReceived(long, boolean)} on the receiving thread. Since teacher responses are always
 * lagging behind learner requests, some clean nodes may be requested redundantly. That is,
 * a request for a clean node is sent before a response for any of its clean parents is
 * received from the teacher.
 */
public class TopToBottomTraversalOrder implements NodeTraversalOrder {

    private final ReconnectNodeCount nodeCount;

    private volatile long reconnectFirstLeafPath;
    private volatile long reconnectLastLeafPath;

    // Last sent path. Initialized to 0, since the root path is always sent first
    private long lastPath = 0;

    // Clean node paths, as received from the teacher. Only internal paths are recorded here,
    // there is no need to track clean leaves, since they don't have children. This set is
    // populated on the receiving thread and queried on the sending thread
    private final Set<Long> cleanNodes = ConcurrentHashMap.newKeySet();

    /**
     * Constructor.
     *
     * @param nodeCount object to report node stats
     */
    public TopToBottomTraversalOrder(final ReconnectNodeCount nodeCount) {
        this.nodeCount = nodeCount;
    }

    @Override
    public void start(final long firstLeafPath, final long lastLeafPath) {
        this.reconnectFirstLeafPath = firstLeafPath;
        this.reconnectLastLeafPath = lastLeafPath;
    }

    @Override
    public void nodeReceived(final long path, final boolean isClean) {
        final boolean isLeaf = path >= reconnectFirstLeafPath;
        if (isClean && !isLeaf) {
            cleanNodes.add(path);
        }
        if (isLeaf) {
            nodeCount.incrementLeafCount();
            if (isClean) {
                nodeCount.incrementRedundantLeafCount();
            }
        } else {
            nodeCount.incrementInternalCount();
            if (isClean) {
                nodeCount.incrementRedundantInternalCount();
            }
        }
    }

    @Override
    public long getNextPathToSend() {
        assert lastPath != Path.INVALID_PATH;
        long path = lastPath + 1;
        long result = skipCleanPaths(path);
        // Find the highest clean path and skip all paths in its sub-tree. Repeat
        while ((result != Path.INVALID_PATH) && (result != path)) {
            path = result;
            result = skipCleanPaths(path);
        }
        return lastPath = result;
    }

    /**
     * For the given path, find the highest clean parent path on the way to the root. If such
     * a clean parent is found, all paths in the parent's sub-tree at the same rank as the
     * initial path are skipped, and the next path outside of the sub-tree is returned (it may
     * also be clean, this is handled in the loop in {@link #getNextPathToSend()}. Is the
     * sub-tree spans up to the last leaf path, this method returns Path.INVALID_PATH, which
     * indicates there are no more requests to the teacher to send. If no clean parents are
     * found, the initial path is returned.
     */
    private long skipCleanPaths(final long path) {
        assert path > 0;
        if (path > reconnectLastLeafPath) {
            return Path.INVALID_PATH;
        }
        // Find the highest clean parent and its rank
        long parent = Path.getParentPath(path);
        long cleanParent = Path.INVALID_PATH;
        int parentRanksAbove = 1;
        int cleanParentRanksAbove = 1;
        while (parent != ROOT_PATH) {
            if (cleanNodes.contains(parent)) {
                cleanParent = parent;
                cleanParentRanksAbove = parentRanksAbove;
            }
            parentRanksAbove++;
            parent = Path.getParentPath(parent);
        }
        final long result;
        if (cleanParent == Path.INVALID_PATH) {
            // no clean parent found
            result = path;
        } else {
            // If found, get the right-most path in parent's sub-tree at the initial rank
            // and return the next path
            result = Path.getRightGrandChildPath(cleanParent, cleanParentRanksAbove) + 1;
        }
        assert result >= path;
        return (result <= reconnectLastLeafPath) ? result : Path.INVALID_PATH;
    }
}

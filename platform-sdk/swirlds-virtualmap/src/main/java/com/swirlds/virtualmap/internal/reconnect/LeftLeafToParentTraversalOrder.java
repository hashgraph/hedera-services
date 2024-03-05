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

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.synchronization.task.ReconnectNodeCount;
import com.swirlds.virtualmap.internal.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LeftLeafToParentTraversalOrder implements NodeTraversalOrder {

    private final VirtualLearnerTreeView view;

    private ReconnectNodeCount nodeCount;

    private final long originalFirstLeafPath;
    private final long originalLastLeafPath;

    private long reconnectFirstLeafPath;
    private long reconnectLastLeafPath;

    private final Set<Long> cleanNodes = ConcurrentHashMap.newKeySet();

    private long lastPath = Path.INVALID_PATH;
    private long lastLeaf = Path.INVALID_PATH;

    public LeftLeafToParentTraversalOrder(
            final VirtualLearnerTreeView view, final long firstLeafPath, final long lastLeafPath) {
        this.view = view;
        this.originalFirstLeafPath = firstLeafPath;
        this.originalLastLeafPath = lastLeafPath;
    }

    @Override
    public void start(final long firstLeafPath, final long lastLeafPath, final ReconnectNodeCount nodeCount) {
        this.reconnectFirstLeafPath = firstLeafPath;
        this.reconnectLastLeafPath = lastLeafPath;
        this.nodeCount = nodeCount;
    }

    @Override
    public boolean nodeReceived(final long path, final Hash hash) {
        long parent = Path.getParentPath(path);
        boolean isClean = false;
        while ((parent > 0) && !isClean) {
            isClean = cleanNodes.contains(parent);
            parent = Path.getParentPath(parent);
        }
        final boolean isLeaf = path >= reconnectFirstLeafPath;
        if (!isClean) {
            if (path <= originalLastLeafPath) {
                final Hash originalHash = view.getNodeHash(path);
                assert originalHash != null;
                isClean = hash.equals(originalHash);
                if (isClean && !isLeaf) {
                    cleanNodes.add(path);
                }
            }
        }
        if (isLeaf) {
            nodeCount.incrementLeafCount();
            if (isClean) {
                nodeCount.incrementRedundantLeafCount();
                //                System.err.println("Redundant leaf: " + path);
            }
        } else {
            nodeCount.incrementInternalCount();
            if (isClean) {
                nodeCount.incrementRedundantInternalCount();
                //                System.err.println("Redundant internal: " + path);
            }
        }
        return isClean;
    }

    @Override
    public long getNextPathToSend() throws InterruptedException {
        long result;
        if (lastPath == Path.INVALID_PATH) {
            result = reconnectFirstLeafPath;
        } else if (lastPath == 0) {
            final int lastLeafRank = Path.getRank(reconnectLastLeafPath);
            result = Path.getPathForRankAndIndex(lastLeafRank, 0) + 1;
            // Corner case: first leaf == last leaf == 1
            if (result > reconnectLastLeafPath) {
                result = Path.INVALID_PATH;
            }
        } else {
            final long lastPathParent = Path.getParentPath(lastPath);
            if (Path.isLeft(lastPath)) {
                result = lastPathParent;
            } else {
                long path = lastLeaf + 1;
                result = skipCleanPaths(path);
                while ((result != Path.INVALID_PATH) && (result != path)) {
                    path = result;
                    result = skipCleanPaths(path);
                }
                //                if (result != lastLeaf + 1) {
                //                    System.err.println("Skipped: " + (result - lastLeaf - 1));
                //                }
            }
        }
        view.applySendBackpressure();
        if (result >= reconnectFirstLeafPath) {
            lastLeaf = result;
        }
        return lastPath = result;
    }

    private long skipCleanPaths(final long path) {
        assert path > 0;
        if (path > reconnectLastLeafPath) {
            return Path.INVALID_PATH;
        }
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
            result = Path.getRightGrandChildPath(cleanParent, cleanParentRanksAbove) + 1;
        }
        assert result >= path;
        return (result <= reconnectLastLeafPath) ? result : Path.INVALID_PATH;
    }

    private boolean isClean(long path) {
        while (path != ROOT_PATH) {
            if (cleanNodes.contains(path)) {
                return true;
            }
            path = Path.getParentPath(path);
        }
        return false;
    }
}

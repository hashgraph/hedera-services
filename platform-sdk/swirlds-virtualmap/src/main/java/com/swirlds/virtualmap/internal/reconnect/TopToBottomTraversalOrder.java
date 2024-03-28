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

public class TopToBottomTraversalOrder implements NodeTraversalOrder {

    private final VirtualLearnerTreeView view;

    private ReconnectNodeCount nodeCount;

    private long reconnectFirstLeafPath;
    private long reconnectLastLeafPath;

    // Path 0 is always sent first, before the order is checked
    private long lastPath = 0;

    private final Set<Long> cleanNodes = ConcurrentHashMap.newKeySet();

    public TopToBottomTraversalOrder(final VirtualLearnerTreeView view) {
        this.view = view;
    }

    @Override
    public void start(final long firstLeafPath, final long lastLeafPath, final ReconnectNodeCount nodeCount) {
        this.reconnectFirstLeafPath = firstLeafPath;
        this.reconnectLastLeafPath = lastLeafPath;
        this.nodeCount = nodeCount;
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
    public long getNextPathToSend() throws InterruptedException {
        if (lastPath == 0) {
            final int firstLeafRank = Path.getRank(reconnectFirstLeafPath);
            if (firstLeafRank > 4) {
                // Skip a 3/4 parent ranks above
//                lastPath = Path.getRightGrandChildPath(0, firstLeafRank * 3 / 4);
            }
        }
        long path = lastPath + 1;
        long result = skipCleanPaths(path);
        while ((result != Path.INVALID_PATH) && (result != path)) {
            path = result;
            result = skipCleanPaths(path);
        }
        if (result != lastPath + 1) {
//            System.err.println("Skipped: " + (result - lastPath - 1));
        }
        view.applySendBackpressure();
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
}

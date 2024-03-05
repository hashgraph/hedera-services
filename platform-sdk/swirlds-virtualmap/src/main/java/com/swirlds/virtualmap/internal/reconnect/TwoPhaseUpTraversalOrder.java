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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TwoPhaseUpTraversalOrder implements NodeTraversalOrder {

    private static final int UP_STREAM_POW = 10; // 2 ^ UP_STREAM_POW streams

    private final VirtualLearnerTreeView view;

    private ReconnectNodeCount nodeCount;

    private final long originalFirstLeafPath;
    private final long originalLastLeafPath;

    private long reconnectFirstLeafPath;
    private long reconnectLastLeafPath;

    private final Set<Long> cleanNodes = ConcurrentHashMap.newKeySet();

    private final Deque<Long> phase1Paths = new ArrayDeque<>();

    private long lastPhase2Path = Path.INVALID_PATH;

    public TwoPhaseUpTraversalOrder(
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

        final int firstLeafRank = Path.getRank(firstLeafPath);
        if (firstLeafRank <= UP_STREAM_POW) {
            return; // no phase1, just iterate over all leaves
        }

        final long firstPathInParentRank = Path.getLeftGrandChildPath(0, firstLeafRank - 1);
        final long betweenParents = 1L << (firstLeafRank - UP_STREAM_POW - 1);
        for (int i = 0; i < (1 << UP_STREAM_POW); i++) {
            long path = firstPathInParentRank + i * betweenParents;
            for (int j = 0; j < firstLeafRank - 5; j++) {
                phase1Paths.add(path);
                path = Path.getParentPath(path);
            }
        }
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
            }
        } else {
            nodeCount.incrementInternalCount();
            if (isClean) {
                nodeCount.incrementRedundantInternalCount();
            }
        }
        return isClean;
    }

    @Override
    public long getNextPathToSend() throws InterruptedException {
        final long result;
        if (!phase1Paths.isEmpty()) {
            result = phase1Paths.removeFirst();
            if (phase1Paths.isEmpty()) {
                System.err.println("cleanNodes = " + cleanNodes.size());
            }
        } else {
            result = getPhase2Path();
        }
        view.applySendBackpressure();
        return result;
    }

    private long getPhase2Path() {
        long path = lastPhase2Path == Path.INVALID_PATH ? reconnectFirstLeafPath : lastPhase2Path + 1;
        if (path == Path.INVALID_PATH) {
            return Path.INVALID_PATH;
        }
        long result = skipCleanPaths(path);
        while ((result != Path.INVALID_PATH) && (result != path)) {
            path = result;
            result = skipCleanPaths(path);
        }
        if (result > reconnectLastLeafPath) {
            result = Path.INVALID_PATH;
        }
        return lastPhase2Path = result;
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

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
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

public class TwoPhaseParentsTraversalOrder implements NodeTraversalOrder {

    private static final int DEFAULT_BATCH_COUNT_POW = 10; // 2 ^ BATCH_COUNT_POW batches

    private final VirtualLearnerTreeView view;

    private ReconnectNodeCount nodeCount;

    private final long originalFirstLeafPath;
    private final long originalLastLeafPath;

    private long reconnectFirstLeafPath;
    private long reconnectLastLeafPath;

    // This set is populated during phase 1 (if any) on the receiving thread. After phase 1 is
    // complete, the set is used on the sending thread. No concurrent reads or writes, not a
    // concurrent set
    private final Set<Long> cleanNodes = new HashSet<>();

    private boolean usePhase1 = true;
    private final Deque<Long> phase1Paths = new ConcurrentLinkedDeque<>();
    private final AtomicInteger pendingPhase1Nodes = new AtomicInteger();

    private int leafParentRank;
    private long firstPathInLeafParentRank;
    private int batchesCount;
    private int batchHeight;
    private int batchWidth;

    private long lastLeafPath = Path.INVALID_PATH;

    public TwoPhaseParentsTraversalOrder(
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

        leafParentRank = Path.getRank(firstLeafPath) - 1;
        if (leafParentRank < DEFAULT_BATCH_COUNT_POW - 1) {
            usePhase1 = false;
            return; // no phase 1, just iterate over all leaves
        }

        final int batchesCountPow = Math.min(DEFAULT_BATCH_COUNT_POW, leafParentRank - 1);
        batchesCount = 1 << batchesCountPow;
        batchHeight = leafParentRank - batchesCountPow;
        batchWidth = 1 << batchHeight;

        firstPathInLeafParentRank = Path.getLeftGrandChildPath(0, leafParentRank);
        for (int i = 0; i < batchesCount; i++) {
            long path = firstPathInLeafParentRank + (long) i * batchWidth;
            phase1Node(path);
        }
    }

    private void phase1Node(final long path) {
        phase1Paths.add(path);
        pendingPhase1Nodes.incrementAndGet();
    }

    @Override
    public boolean nodeReceived(final long path, final Hash hash) {
        final boolean isLeaf = path >= reconnectFirstLeafPath;
        boolean isClean;
        if (isLeaf) {
            isClean = hasCleanParent(path);
            if (!isClean && (path <= originalLastLeafPath)) {
                final Hash originalHash = view.getNodeHash(path);
                assert originalHash != null;
                isClean = hash.equals(originalHash);
            }
            nodeCount.incrementLeafCount();
            if (isClean) {
                nodeCount.incrementRedundantLeafCount();
            }
        } else {
            final int rank = Path.getRank(path);
            if (path <= originalLastLeafPath) {
                final Hash originalHash = view.getNodeHash(path);
                assert originalHash != null;
                isClean = hash.equals(originalHash);
            } else {
                isClean = false;
            }
            if (path != 0) {
                assert usePhase1;
                long nextPathInLeafParentRank = -1;
                if (isClean) {
                    cleanNodes.add(path);
                    cleanNodes.remove(Path.getLeftChildPath(path));
                    cleanNodes.remove(Path.getRightChildPath(path));
                    if (Path.isLeft(path) && (path != 1)) {
                        // check if the parent is clean
                        phase1Node(Path.getParentPath(path));
                    } else {
                        nextPathInLeafParentRank = Path.getRightGrandChildPath(path, leafParentRank - rank) + 1;
                    }
                } else {
                    if (rank == leafParentRank) {
                        nextPathInLeafParentRank = path + 1;
                    } else {
                        final long rightPath = Path.getRightChildPath(path);
                        nextPathInLeafParentRank = Path.getLeftGrandChildPath(rightPath, leafParentRank - rank - 1);
                    }
                }
                if (nextPathInLeafParentRank != -1) {
                    final long grandLeftPathInBatch = Path.getLeftGrandChildPath(path, leafParentRank - rank);
                    final long batchIndex = (grandLeftPathInBatch - firstPathInLeafParentRank) / batchWidth;
                    if (nextPathInLeafParentRank - firstPathInLeafParentRank - batchIndex * batchWidth
                            < batchWidth) { // same batch as path
                        phase1Node(nextPathInLeafParentRank);
                    }
                }
                int c = pendingPhase1Nodes.decrementAndGet();
            }
            nodeCount.incrementInternalCount();
            if (isClean) {
                nodeCount.incrementRedundantInternalCount();
            }
        }
        return isClean;
    }

    @Override
    public long getNextPathToSend() throws InterruptedException {
        Long result;
        if (pendingPhase1Nodes.get() > 0) {
            result = phase1Paths.pollFirst();
            if (result == null) {
                return -2;
            }
        } else {
            result = getNextLeafPath();
        }
        view.applySendBackpressure();
        return result;
    }

    private long getNextLeafPath() {
        if (lastLeafPath == Path.INVALID_PATH) {
            System.err.println("Clean nodes: " + cleanNodes.size());
        }
        long path = lastLeafPath == Path.INVALID_PATH ? reconnectFirstLeafPath : lastLeafPath + 1;
        if (path == Path.INVALID_PATH) {
            return Path.INVALID_PATH;
        }
        long result = skipCleanLeafPaths(path);
        while ((result != Path.INVALID_PATH) && (result != path)) {
            path = result;
            result = skipCleanLeafPaths(path);
        }
        if (result > reconnectLastLeafPath) {
            result = Path.INVALID_PATH;
        }
        assert (result == Path.INVALID_PATH) || (result >= reconnectFirstLeafPath);
        return lastLeafPath = result;
    }

    private boolean hasCleanParent(final long path) {
        long parent = Path.getParentPath(path);
        boolean clean = false;
        while ((parent > 0) && !clean) {
            clean = cleanNodes.contains(parent);
            parent = Path.getParentPath(parent);
        }
        return clean;
    }

    private long skipCleanLeafPaths(final long path) {
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

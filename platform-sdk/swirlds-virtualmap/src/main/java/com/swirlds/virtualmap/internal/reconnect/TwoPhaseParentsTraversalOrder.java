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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TwoPhaseParentsTraversalOrder implements NodeTraversalOrder {

    private final VirtualLearnerTreeView view;

    private ReconnectNodeCount nodeCount;

    private long reconnectFirstLeafPath;
    private long reconnectLastLeafPath;

    // This set is populated during phase 1 (if any) on the receiving thread. After phase 1 is
    // complete, the set is used on the sending thread. No concurrent reads or writes, not a
    // concurrent set
    private final Set<Long> cleanNodes = ConcurrentHashMap.newKeySet();

    private boolean usePhase1 = true;

    // Number of parent node chunks processed in parallel in phase 1
    private int chunkCount;

    private int chunksTopRank;

    private Map<Integer, Long> chunkStartPaths;
    private Map<Integer, Long> chunkWidths;
    private Map<Integer, Integer> chunkStartRanks;

    private int lastSentPathChunk;
    private Map<Integer, Long> chunkNextPaths;

    // Used during phase 2
    private long lastLeafPath = Path.INVALID_PATH;

    public TwoPhaseParentsTraversalOrder(final VirtualLearnerTreeView view) {
        this.view = view;
    }

    @Override
    public void start(final long firstLeafPath, final long lastLeafPath, final ReconnectNodeCount nodeCount) {
        this.reconnectFirstLeafPath = firstLeafPath;
        this.reconnectLastLeafPath = lastLeafPath;
        this.nodeCount = nodeCount;

        final int leafParentRank = Path.getRank(firstLeafPath) - 1;
        if (leafParentRank < 10) {
            usePhase1 = false;
            return; // no phase 1, just iterate over all leaves
        }

        chunksTopRank = leafParentRank * 3 / 4;
        chunkCount = 1 << chunksTopRank;
        final int minChunkHeight = leafParentRank - chunksTopRank;

        final long firstPathInLeafParentRank = Path.getLeftGrandChildPath(0, leafParentRank);

        chunkStartPaths = new ConcurrentHashMap<>(chunkCount);
        chunkWidths = new ConcurrentHashMap<>(chunkCount);
        chunkStartRanks = new ConcurrentHashMap<>(chunkCount);
        lastSentPathChunk = -1;
        chunkNextPaths = new ConcurrentHashMap<>(chunkCount);
        for (int i = 0; i < chunkCount; i++) {
            final long p = firstPathInLeafParentRank + ((long) i << minChunkHeight);
            if (Path.getLeftChildPath(p) + (2L << minChunkHeight) <= reconnectFirstLeafPath) {
                chunkStartPaths.put(i, Path.getLeftChildPath(p));
                chunkWidths.put(i, 2L << minChunkHeight);
                chunkStartRanks.put(i, leafParentRank + 1);
            } else {
                chunkStartPaths.put(i, p);
                chunkWidths.put(i, 1L << minChunkHeight);
                chunkStartRanks.put(i, leafParentRank);
            }
            chunkNextPaths.put(i, chunkStartPaths.get(i));
        }
    }

    @Override
    public void nodeReceived(final long path, final boolean isClean) {
        final boolean isLeaf = path >= reconnectFirstLeafPath;
        if (isLeaf) {
            nodeCount.incrementLeafCount();
            if (isClean) {
                nodeCount.incrementRedundantLeafCount();
            }
        } else {
            final int rank = Path.getRank(path);
            if (path != 0) {
                assert usePhase1;
                final int chunk = getPathChunk(path);
                final int chunkStartRank = chunkStartRanks.get(chunk);
                final long chunkNextPath = chunkNextPaths.get(chunk);
                if (isClean) {
                    cleanNodes.add(path);
                    cleanNodes.remove(Path.getLeftChildPath(path));
                    cleanNodes.remove(Path.getRightChildPath(path));
                    if ((chunkNextPath > 0) && Path.isInSubTree(path, chunkNextPath)) {
                        final long lastCleanPath = Path.getRightGrandChildPath(path, chunkStartRank - rank);
                        chunkNextPaths.put(chunk, getNextPathInChunk(chunk, lastCleanPath));
                    }
                } else {
                    if ((chunkNextPath != -1) && Path.isInSubTree(chunkNextPath, path)) {
                        final long originAtChunkStartRank = Path.getLeftGrandChildPath(path, chunkStartRank - rank);
                        chunkNextPaths.put(chunk, skipCleanPaths(originAtChunkStartRank + 1, reconnectFirstLeafPath));
                    }
                }
            }
            nodeCount.incrementInternalCount();
            if (isClean) {
                nodeCount.incrementRedundantInternalCount();
            }
        }
    }

    private Set<Long> sent = new HashSet<>();

    @Override
    public long getNextPathToSend() throws InterruptedException {
        long result = -1;
        if (usePhase1 && (lastLeafPath == -1)) {
            for (int i = 0; i < chunkCount; i++) {
                final int chunk = (lastSentPathChunk + 1 + i) % chunkCount;
                result = chunkNextPaths.get(chunk);
                if (result == -1) {
                    continue;
                }
                if (hasCleanParent(result) || ((Path.isRight(result) && cleanNodes.contains(Path.getSiblingPath(result))))) {
                    final int rank = Path.getRank(result);
                    final int chunkStartRank = chunkStartRanks.get(chunk);
                    final long originAtChunkStartRank = Path.getLeftGrandChildPath(result, chunkStartRank - rank);
                    result = cleanOrNext(chunk, originAtChunkStartRank + 1);
                }
                if (result == -1) {
                    chunkNextPaths.put(chunk, result);
                    continue;
                }
                chunkNextPaths.put(chunk, getNextPathInChunk(chunk, result));
                lastSentPathChunk = chunk;
                break;
            }
        }
        if (result == -1) {
            result = getNextLeafPath();
        } else {
            view.applySendBackpressure();
        }
        if (sent.contains(result)) {
            System.err.println("Already sent: " + result);
        }
        sent.add(result);
        return result;
    }

    private long cleanOrNext(final int chunk, final long path) {
        if (getPathChunk(path) != chunk) {
            return -1;
        }
        return hasCleanParent(path) ? getNextPathInChunk(chunk, path) : path;
    }

    private long getNextPathInChunk(final int chunk, final long lastPath) {
        final int lastPathRank = Path.getRank(lastPath);
        final int chunkStartRank = chunkStartRanks.get(chunk);
        final int chunkHeight = chunkStartRank - chunksTopRank;
        if (Path.isLeft(lastPath) && (chunkStartRank - lastPathRank < chunkHeight) && !hasCleanParent(lastPath)) {
            return Path.getParentPath(lastPath);
        }
        // next path at chunk start rank
        long path = Path.getLeftGrandChildPath(lastPath, chunkStartRank - lastPathRank) + 1;
        final long chunkStartPath = chunkStartPaths.get(chunk);
        final long chunkWidth = chunkWidths.get(chunk);
        final long lastPathInChunk = chunkStartPath + chunkWidth - 1;
        return skipCleanPaths(path, lastPathInChunk);
    }

    private long getNextLeafPath() {
        if (lastLeafPath == Path.INVALID_PATH) {
//            System.err.println("Clean nodes: " + cleanNodes.size());
//            System.err.println("First leaf sent: " + System.currentTimeMillis());
        }
        long path = lastLeafPath == Path.INVALID_PATH ? reconnectFirstLeafPath : lastLeafPath + 1;
        if ((path > reconnectLastLeafPath) || (reconnectFirstLeafPath < 0)) {
            return Path.INVALID_PATH;
        }
        long result = skipCleanPaths(path, reconnectLastLeafPath);
        if ((lastLeafPath != -1) && (result != -1) && (result != lastLeafPath + 1)) {
//            System.err.println("Skipped: " + (result - lastLeafPath - 1) + " = " + (lastLeafPath + 1) + " -> " + result);
        }
        assert (result == Path.INVALID_PATH) || (result >= reconnectFirstLeafPath);
        return lastLeafPath = result;
    }

    private int getPathChunk(long path) {
        int rank = Path.getRank(path);
        assert rank >= chunksTopRank;
        final long pathAtTopRank = Path.getGrandParentPath(path, rank - chunksTopRank);
        return (int) (pathAtTopRank - Path.getLeftGrandChildPath(0, chunksTopRank));
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

    private long skipCleanPaths(long path, final long limit) {
        long result = skipCleanPaths(path);
        while ((result < limit) && (result != path)) {
            path = result;
            result = skipCleanPaths(path);
        }
        return (result <= limit) ? result : Path.INVALID_PATH;
    }

    private long skipCleanPaths(final long path) {
        assert path > 0;
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
        return result;
    }
}

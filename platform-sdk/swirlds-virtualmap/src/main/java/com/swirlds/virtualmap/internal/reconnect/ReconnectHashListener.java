/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.crypto.Hash;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.internal.Path;
import com.swirlds.virtualmap.internal.hash.VirtualHashListener;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A {@link VirtualHashListener} implementation used by the learner during reconnect. During reconnect, the dirty
 * leaves will be sent from the teacher to the learner in a breadth-first order. The hashing algorithm in the
 * {@link com.swirlds.virtualmap.internal.hash.VirtualHasher} is setup to hash enormous trees in breadth-first order.
 * As the hasher hashes, it notifies this listener which then stores up the changes into different sorted lists.
 * Then, when the "batch" is completed, it flushes the data in the proper order to the data source. This process
 * completely bypasses the {@link com.swirlds.virtualmap.internal.cache.VirtualNodeCache} and the
 * {@link com.swirlds.virtualmap.internal.pipeline.VirtualPipeline}, which is essential for performance and memory
 * reasons, since during reconnect we may need to process the entire data set, which is too large to fit in memory.
 * <p>
 * Three things are required for this listener to work: the {@code firstLeafPath}, the {@code lastLeafPath}, and
 * the {@link VirtualDataSource}.
 * <p>
 * A tree is broken up into "ranks" where "rank 0" is the on that contains root, "rank 1" is the one that contains
 * the left and right children of root, "rank 2" has the children of the nodes in "rank 1", and so forth. The higher
 * the rank, the deeper in the tree the rank lives.
 * <p>
 * A "batch" is a portion of the tree that is independently hashed. The batch will always be processed from the
 * deepest rank (the leaves) to the lowest rank (nearest the top). When we flush, we flush in the opposite order
 * from the closest to the top of the tree to the deepest rank. Each rank is processed in ascending path order.
 * So we store each rank as a separate array and then stream them out in the proper order to disk.
 *
 * @param <K>
 * 		The key
 * @param <V>
 * 		The value
 */
public class ReconnectHashListener<K extends VirtualKey<? super K>, V extends VirtualValue>
        implements VirtualHashListener<K, V> {
    private static final int INITIAL_BATCH_ARRAY_SIZE = 10_000;
    // Maybe it would be better to have the whole state instead of just first/last leaf path so we can use stats
    // while flushing and reconnecting...
    private final VirtualDataSource<K, V> dataSource;
    private final ReconnectNodeRemover<K, V> nodeRemover;
    private final long firstLeafPath;
    private final long lastLeafPath;
    private final List<List<VirtualLeafRecord<K, V>>> batchLeaves = new ArrayList<>();
    private final List<List<VirtualInternalRecord>> batchNodes = new ArrayList<>();
    private List<VirtualLeafRecord<K, V>> rankLeaves;
    private List<VirtualInternalRecord> rankNodes;

    /**
     * Create a new {@link ReconnectHashListener}.
     *
     * @param firstLeafPath
     * 		The first leaf path. Must be a valid path.
     * @param lastLeafPath
     * 		The last leaf path. Must be a valid path.
     * @param dataSource
     * 		The data source. Cannot be null.
     */
    public ReconnectHashListener(
            final long firstLeafPath,
            final long lastLeafPath,
            final VirtualDataSource<K, V> dataSource,
            final ReconnectNodeRemover<K, V> nodeRemover) {

        if (firstLeafPath != Path.INVALID_PATH && !(firstLeafPath > 0 && firstLeafPath <= lastLeafPath)) {
            throw new IllegalArgumentException("The first leaf path is invalid. firstLeafPath=" + firstLeafPath
                    + ", lastLeafPath=" + lastLeafPath);
        }

        if (lastLeafPath != Path.INVALID_PATH && !(lastLeafPath > 0)) {
            throw new IllegalArgumentException(
                    "The last leaf path is invalid. firstLeafPath=" + firstLeafPath + ", lastLeafPath=" + lastLeafPath);
        }

        this.firstLeafPath = firstLeafPath;
        this.lastLeafPath = lastLeafPath;
        this.dataSource = Objects.requireNonNull(dataSource);
        this.nodeRemover = nodeRemover;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onBatchStarted() {
        batchLeaves.clear();
        batchNodes.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRankStarted() {
        rankLeaves = new ArrayList<>(INITIAL_BATCH_ARRAY_SIZE);
        rankNodes = new ArrayList<>(INITIAL_BATCH_ARRAY_SIZE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onNodeHashed(final long path, final Hash hash) {
        rankNodes.add(new VirtualInternalRecord(path, hash));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLeafHashed(final VirtualLeafRecord<K, V> leaf) {
        rankLeaves.add(leaf);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRankCompleted() {
        batchLeaves.add(rankLeaves);
        batchNodes.add(rankNodes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onBatchCompleted() {
        long maxPath = -1;

        Stream<VirtualInternalRecord> sortedDirtyInternals = Stream.of();
        for (int i = batchNodes.size() - 1; i >= 0; i--) {
            final List<VirtualInternalRecord> batch = batchNodes.get(i);
            if (!batch.isEmpty()) {
                sortedDirtyInternals = Stream.concat(sortedDirtyInternals, batch.stream());
                maxPath = Math.max(maxPath, batch.get(batch.size() - 1).getPath());
            }
        }

        Stream<VirtualLeafRecord<K, V>> sortedDirtyLeaves = Stream.of();
        for (int i = batchLeaves.size() - 1; i >= 0; i--) {
            final List<VirtualLeafRecord<K, V>> batch = batchLeaves.get(i);
            if (!batch.isEmpty()) {
                sortedDirtyLeaves = Stream.concat(sortedDirtyLeaves, batch.stream());
                maxPath = Math.max(maxPath, batch.get(batch.size() - 1).getPath());
            }
        }

        final Stream<VirtualLeafRecord<K, V>> leavesToRemove = nodeRemover.getRecordsToDelete(maxPath);

        // flush it down
        try {
            dataSource.saveRecords(
                    firstLeafPath, lastLeafPath, sortedDirtyInternals, sortedDirtyLeaves, leavesToRemove);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

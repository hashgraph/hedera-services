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

package com.swirlds.common.merkle.synchronization.stats;

import com.swirlds.metrics.api.Counter;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;

/**
 * An implementation of ReconnectMapStats that emits all the stats as Counter metrics.
 */
public class ReconnectMapMetrics implements ReconnectMapStats {

    private static final String RECONNECT_MAP_CATEGORY = "reconnect_vmap";

    private final ReconnectMapStats aggregateStats;

    private final Counter transfersFromTeacher;
    private final Counter transfersFromLearner;

    private final Counter internalHashes;
    private final Counter internalCleanHashes;
    private final Counter internalData;
    private final Counter internalCleanData;

    private final Counter leafHashes;
    private final Counter leafCleanHashes;
    private final Counter leafData;
    private final Counter leafCleanData;

    /**
     * Create an instance of ReconnectMapMetrics.
     * @param metrics a non-null Metrics object
     * @param originalLabel an optional label, e.g. a VirtualMap name or similar, may be null.
     *     If specified, the label is added to the names of the metrics.
     * @param aggregateStats an optional aggregateStats object to which all ReconnectMapStats calls will delegate
     *     in addition to emitting metrics for this object, may be null.
     */
    public ReconnectMapMetrics(
            @NonNull final Metrics metrics,
            @Nullable final String originalLabel,
            @Nullable final ReconnectMapStats aggregateStats) {
        Objects.requireNonNull(metrics, "metrics must not be null");
        this.aggregateStats = aggregateStats;
        // Normalize the label
        final String label = originalLabel == null ? null : originalLabel.replace('.', '_');

        this.transfersFromTeacher = metrics.getOrCreate(
                new Counter.Config(RECONNECT_MAP_CATEGORY, formatName("transfersFromTeacher", label))
                        .withDescription("number of transfers from teacher to learner"));
        this.transfersFromLearner = metrics.getOrCreate(
                new Counter.Config(RECONNECT_MAP_CATEGORY, formatName("transfersFromLearner", label))
                        .withDescription("number of transfers from learner to teacher"));

        this.internalHashes =
                metrics.getOrCreate(new Counter.Config(RECONNECT_MAP_CATEGORY, formatName("internalHashes", label))
                        .withDescription("number of internal node hashes transferred"));
        this.internalCleanHashes =
                metrics.getOrCreate(new Counter.Config(RECONNECT_MAP_CATEGORY, formatName("internalCleanHashes", label))
                        .withDescription("number of clean internal node hashes transferred"));
        this.internalData =
                metrics.getOrCreate(new Counter.Config(RECONNECT_MAP_CATEGORY, formatName("internalData", label))
                        .withDescription("number of internal node data transferred"));
        this.internalCleanData =
                metrics.getOrCreate(new Counter.Config(RECONNECT_MAP_CATEGORY, formatName("internalCleanData", label))
                        .withDescription("number of clean internal node data transferred"));

        this.leafHashes =
                metrics.getOrCreate(new Counter.Config(RECONNECT_MAP_CATEGORY, formatName("leafHashes", label))
                        .withDescription("number of leaf node hashes transferred"));
        this.leafCleanHashes =
                metrics.getOrCreate(new Counter.Config(RECONNECT_MAP_CATEGORY, formatName("leafCleanHashes", label))
                        .withDescription("number of clean leaf node hashes transferred"));
        this.leafData = metrics.getOrCreate(new Counter.Config(RECONNECT_MAP_CATEGORY, formatName("leafData", label))
                .withDescription("number of leaf node data transferred"));
        this.leafCleanData =
                metrics.getOrCreate(new Counter.Config(RECONNECT_MAP_CATEGORY, formatName("leafCleanData", label))
                        .withDescription("number of clean leaf node data transferred"));
    }

    private static String formatName(final String name, final String label) {
        return (label == null || label.isBlank() ? name : (name + "_" + label + "_")) + "Total";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incrementTransfersFromTeacher() {
        transfersFromTeacher.increment();
        if (aggregateStats != null) {
            aggregateStats.incrementTransfersFromTeacher();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incrementTransfersFromLearner() {
        transfersFromLearner.increment();
        if (aggregateStats != null) {
            aggregateStats.incrementTransfersFromLearner();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incrementInternalHashes(final int hashNum, final int cleanHashNum) {
        if (hashNum > 0) internalHashes.add(hashNum);
        if (cleanHashNum > 0) internalCleanHashes.add(cleanHashNum);
        if (aggregateStats != null) {
            aggregateStats.incrementInternalHashes(hashNum, cleanHashNum);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incrementInternalData(final int dataNum, final int cleanDataNum) {
        if (dataNum > 0) internalData.add(dataNum);
        if (cleanDataNum > 0) internalCleanData.add(cleanDataNum);
        if (aggregateStats != null) {
            aggregateStats.incrementInternalData(dataNum, cleanDataNum);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incrementLeafHashes(final int hashNum, final int cleanHashNum) {
        if (hashNum > 0) leafHashes.add(hashNum);
        if (cleanHashNum > 0) leafCleanHashes.add(cleanHashNum);
        if (aggregateStats != null) {
            aggregateStats.incrementLeafHashes(hashNum, cleanHashNum);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incrementLeafData(final int dataNum, final int cleanDataNum) {
        if (dataNum > 0) leafData.add(dataNum);
        if (cleanDataNum > 0) leafCleanData.add(cleanDataNum);
        if (aggregateStats != null) {
            aggregateStats.incrementLeafData(dataNum, cleanDataNum);
        }
    }
}

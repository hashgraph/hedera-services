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

package com.swirlds.state.merkle;

import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This class encapsulates metrics for the Merkle root snapshot.
 */
public class MerkleRootSnapshotMetrics {
    private static final RunningAverageMetric.Config WRITE_MERKLE_ROOT_TO_DISK_TIME_CONFIG =
            new RunningAverageMetric.Config("platform", "writeMerkleRootToDisk")
                    .withDescription("average time it takes to write a Merkle tree to disk (in milliseconds)")
                    .withUnit("ms");

    private final RunningAverageMetric writeMerkleRootToDiskTime;
    /**
     * Constructor.
     *
     * @param metrics the metrics object
     */
    public MerkleRootSnapshotMetrics(@NonNull final Metrics metrics) {
        writeMerkleRootToDiskTime = metrics.getOrCreate(WRITE_MERKLE_ROOT_TO_DISK_TIME_CONFIG);
    }

    /**
     * No-arg constructor constructs an object that does not track metrics.
     */
    public MerkleRootSnapshotMetrics() {
        writeMerkleRootToDiskTime = null;
    }

    /**
     * Update the metric tracking the average time required to write a Merkle tree to disk.
     * @param timeTakenMs the time taken to write the state to disk
     */
    public void updateWriteStateToDiskTimeMetric(final long timeTakenMs) {
        if (writeMerkleRootToDiskTime != null) {
            writeMerkleRootToDiskTime.update(timeTakenMs);
        }
    }
}

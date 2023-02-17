/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.stats;

import com.hedera.node.app.service.mono.utils.LongSampleAccumulator;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Holds the accumulators for a single sidecar type's logging/metrics.
 *
 * <p>Keeps an accumulator for the serialized size (in bytes) of a sidecar type (over a consensus
 * transaction), and an accumulator for the duration (in milliseconds) it took to
 * compute/format/serialize the sidecar.
 *
 * <p>Has a separate accumulator for compute duration "splits" so multiple separate compute periods
 * can be added together and become _one_ sample.
 */
public class SidecarAccumulators {

    private final @NonNull LongSampleAccumulator serializedSize;
    private final @NonNull LongSampleAccumulator computeDuration;
    private final @NonNull LongSampleAccumulator durationSplit;

    // duration is computed in ns, but displayed in ms
    private static final long NANOS_TO_MILLIS = 1_000_000L;

    /** Construct a metrics collector for a sidecar, given a name for it. */
    public SidecarAccumulators(@NonNull final String name) {
        Objects.requireNonNull(name, "name:String");
        serializedSize = new LongSampleAccumulator(name + "SizeBytes");
        computeDuration = new LongSampleAccumulator(name + "DurationMs", NANOS_TO_MILLIS);
        durationSplit = new LongSampleAccumulator(name + "DurationSplitMs", NANOS_TO_MILLIS);
    }

    /**
     * Finalize a duration measurement by adding all the splits together as one sample to the
     * compute duration.
     */
    public void finalizeDurationSplits() {
        durationSplit.coalesceSamples();
        computeDuration.addSamples(durationSplit);
        durationSplit.reset();
    }

    /** Reset the sidecar's metrics to zero samples. */
    public void reset() {
        serializedSize.reset();
        computeDuration.reset();
        durationSplit.reset();
    }

    @Override
    public @NonNull String toString() {
        return "SidecarAccumulators[serializedSize=%s, computeDuration=%s, durationSplit=%s]"
                .formatted(serializedSize, computeDuration, durationSplit);
    }

    public @NonNull LongSampleAccumulator getSerializedSize() {
        return serializedSize;
    }

    public @NonNull LongSampleAccumulator getComputeDuration() {
        return computeDuration;
    }

    public @NonNull LongSampleAccumulator getDurationSplit() {
        return durationSplit;
    }
}

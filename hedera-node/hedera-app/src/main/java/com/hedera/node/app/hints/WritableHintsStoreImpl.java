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

package com.hedera.node.app.hints;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.hapi.node.state.hints.PreprocessedKeys;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.service.ReadableRosterStore;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;

/**
 * Default implementation of {@link WritableHintsStore}.
 */
public class WritableHintsStoreImpl extends ReadableHintsStoreImpl implements WritableHintsStore {
    public WritableHintsStoreImpl(@NonNull WritableStates states) {
        super(states);
    }

    @Override
    public HintsConstruction completeAggregation(final long constructionId, @NonNull final PreprocessedKeys keys) {
        requireNonNull(keys);
        throw new AssertionError("Not implemented");
    }

    @Override
    public HintsConstruction setAggregationTime(final long constructionId, @NonNull final Instant now) {
        requireNonNull(now);
        throw new AssertionError("Not implemented");
    }

    @Override
    public HintsConstruction rescheduleAggregationCheckpoint(final long constructionId, @NonNull final Instant then) {
        requireNonNull(then);
        throw new AssertionError("Not implemented");
    }

    @Override
    public void purgeConstructionsNotFor(@NonNull final Bytes targetRosterHash) {
        requireNonNull(targetRosterHash);
        throw new AssertionError("Not implemented");
    }

    @Override
    public HintsConstruction newConstructionFor(
            @Nullable final Bytes sourceRosterHash,
            @NonNull final Bytes targetRosterHash,
            @NonNull final ReadableRosterStore rosterStore) {
        requireNonNull(targetRosterHash);
        requireNonNull(rosterStore);
        throw new AssertionError("Not implemented");
    }
}

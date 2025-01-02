/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.history.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.history.MetadataProofConstruction;
import com.hedera.node.app.history.WritableHistoryStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * Default implementation of {@link WritableHistoryStore}.
 */
public class WritableHistoryStoreImpl implements WritableHistoryStore {
    @Override
    public MetadataProofConstruction rescheduleAssemblyCheckpoint(
            final long constructionId, @NonNull final Instant then) {
        requireNonNull(then);
        throw new AssertionError("Not implemented");
    }

    @Override
    public MetadataProofConstruction setAssemblyTime(final long constructionId, @NonNull final Instant now) {
        requireNonNull(now);
        throw new AssertionError("Not implemented");
    }
}

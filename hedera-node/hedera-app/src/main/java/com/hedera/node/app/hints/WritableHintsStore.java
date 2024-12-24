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

import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.service.ReadableRosterStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Provides write access to the {@link HintsConstruction} instances in state.
 */
public interface WritableHintsStore extends ReadableHintsStore {
    /**
     * Ensures the only construction in state is for the given target roster hash.
     */
    void purgeConstructionsNotFor(@NonNull Bytes targetRosterHash);

    /**
     * Creates a new {@link HintsConstruction} for the given source and target roster hashes.
     * <p>
     * Note that only two constructions can exist at a time, so this may have the side effect
     * of purging a previous construction for a candidate roster.
     * @param sourceRosterHash the source roster hash
     * @param targetRosterHash the target roster hash
     * @param rosterStore the roster store
     */
    HintsConstruction newConstructionFor(
            @Nullable Bytes sourceRosterHash,
            @NonNull Bytes targetRosterHash,
            @NonNull ReadableRosterStore rosterStore);
}

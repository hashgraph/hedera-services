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
import com.hedera.hapi.node.state.hints.PreprocessedKeys;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;

/**
 * Encapsulates information about the hints in state that are not directly scoped to
 * a particular maximum universe size.
 */
public interface ReadableHintsStore {
    /**
     * If there is a known construction with the given source and target roster hashes,
     * returns the corresponding {@link HintsConstruction}; otherwise, returns null.
     * @param sourceRosterHash the source roster hash
     * @param targetRosterHash the target roster hash
     * @return the corresponding {@link HintsConstruction}, or null
     */
    @Nullable
    HintsConstruction constructionFrom(@Nullable Bytes sourceRosterHash, @NonNull Bytes targetRosterHash);

    /**
     * The preprocessed keys computed and votes received so far for a given construction id.
     * @param keysByHash the preprocessed keys by hash
     * @param votesById the votes by id
     */
    record Votes(@NonNull Map<Bytes, PreprocessedKeys> keysByHash, @NonNull Map<Long, Bytes> votesById) {}

    /**
     * Returns the preprocessed keys and votes for the given construction id, if they exist.
     * @param constructionId the construction id
     * @return the preprocessed keys and votes, or null
     */
    @Nullable
    Votes votesFor(long constructionId);
}

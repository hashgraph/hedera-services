/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Encapsulates information about the hints in state that are not directly scoped to a particular maximum party size.
 */
public interface ReadableHintsStore {
    /**
     * Returns the verification key for the given roster hash, if it exists.
     * @param rosterHash the roster hash
     * @return the verification key, or null if it does not exist
     */
    @Nullable
    Bytes getVerificationKeyFor(@NonNull Bytes rosterHash);
}

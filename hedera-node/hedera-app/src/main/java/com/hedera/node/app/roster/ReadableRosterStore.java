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

package com.hedera.node.app.roster;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.Nullable;

@Deprecated
// This is just added to unblock services PRs until this PR https://github.com/hashgraph/hedera-services/pull/15442
// is merged
public interface ReadableRosterStore {
    /**
     * Get the candidate roster.
     *
     * @return The candidate roster.
     */
    @Nullable
    Roster getCandidateRoster();

    /**
     * Get the active roster.
     *
     * @return The active roster.
     */
    Roster getActiveRoster();

    /**
     * Get the roster based on roster hash
     *
     * @param protoBytes
     * @return The roster.
     */
    Roster get(Bytes protoBytes);
}

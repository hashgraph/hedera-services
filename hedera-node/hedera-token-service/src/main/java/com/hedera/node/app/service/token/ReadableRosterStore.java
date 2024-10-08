/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token;

import com.hedera.hapi.node.state.roster.Roster;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with Rosters.
 */
public interface ReadableRosterStore {

    /**
     * todo
     * @param rosterHash
     * @return
     */
    @Nullable
    Roster getRoster(final byte[] rosterHash);

    /**
     * Fetches the current/active/adopted {@link Roster} object from state.
     *todo
     */
    @NonNull
    Roster getActiveRoster(final long roundNum);

    /**
     * Fetches the candidate {@link Roster} object from state.
     * todo
     */
    @NonNull
    Roster getCandidateRoster();
}

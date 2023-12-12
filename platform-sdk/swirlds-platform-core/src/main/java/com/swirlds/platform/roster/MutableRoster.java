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

package com.swirlds.platform.roster;

import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A mutable roster that can have entries added, removed or be cleared completely.  The MutableRoster should be
 * {@link Roster#seal() sealed} before use as a Roster.
 */
public interface MutableRoster extends Roster {

    /**
     * Adds a roster entry to the roster.
     *
     * @param entry the roster entry to add
     * @return this mutable roster
     */
    @NonNull
    MutableRoster addEntry(MutableRosterEntry entry);

    /**
     * Removes a roster entry from the roster with the matching NodeId.  This method does nothing if a matching entry is
     * not present.
     *
     * @param nodeId the roster entry to remove
     * @return this mutable roster
     */
    @NonNull
    MutableRoster removeEntry(NodeId nodeId);

    /**
     * Removes all roster entries from the roster.
     *
     * @return this mutable roster
     */
    @NonNull
    MutableRoster clearEntries();
}

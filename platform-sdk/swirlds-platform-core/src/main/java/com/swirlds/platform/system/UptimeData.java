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

package com.swirlds.platform.system;

import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Set;

/**
 * Uptime data about nodes in the address book.
 */
public interface UptimeData {

    /**
     * The round reported if no events have been observed.
     */
    long NO_ROUND = -1;

    /**
     * Get the consensus time when the most recent consensus event from the given node was observed, or null if no
     * consensus event from the given node has ever been observed.
     *
     * @param id the node ID
     * @return the consensus time when the most recent consensus event from the given node was observed, or null if no
     * consensus event from the given node has ever been received
     */
    @Nullable
    Instant getLastEventTime(@NonNull final NodeId id);

    /**
     * Get the round when the most recent consensus event from the given node was observed, or {@link #NO_ROUND} if no
     * consensus event from the given node has ever been observed.
     *
     * @param id the node ID
     * @return the round when the most recent consensus event from the given node was observed, or {@link #NO_ROUND} if
     * no consensus event from the given node has ever been observed
     */
    long getLastEventRound(@NonNull final NodeId id);

    /**
     * Get the consensus time when the most recent judge from the given node was observed, or null if no judge from the
     * given node has ever been observed.
     *
     * @param id the node ID
     * @return the consensus time when the most recent judge from the given node was observed, or null if no judge
     */
    @Nullable
    Instant getLastJudgeTime(@NonNull final NodeId id);

    /**
     * Get the round when the most recent judge from the given node was observed, or {@link #NO_ROUND} if no judge from
     * the given node has ever been observed.
     *
     * @param id the node ID
     * @return the round when the most recent judge from the given node was observed, or {@link #NO_ROUND} if no judge
     * from the given node has ever been observed
     */
    long getLastJudgeRound(@NonNull final NodeId id);

    /**
     * Get the set of node IDs that are currently being tracked.
     *
     * @return the set of node IDs that are currently being tracked
     */
    @NonNull
    Set<NodeId> getTrackedNodes();
}

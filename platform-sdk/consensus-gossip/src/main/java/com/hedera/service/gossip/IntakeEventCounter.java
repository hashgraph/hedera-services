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

package com.hedera.service.gossip;

import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Keeps track of how many events have been received from each peer, but haven't yet made it through the intake
 * pipeline.
 */
public interface IntakeEventCounter {
    /**
     * Checks whether there are any events from a given sender that have entered the intake pipeline, but aren't yet
     * through it.
     *
     * @param peer the peer to check for unprocessed events
     * @return true if there are unprocessed events, false otherwise
     */
    boolean hasUnprocessedEvents(@NonNull final NodeId peer);

    /**
     * Indicates that an event from a given peer has entered the intake pipeline
     *
     * @param peer the peer that sent the event
     */
    void eventEnteredIntakePipeline(@NonNull final NodeId peer);

    /**
     * Indicates that an event from a given peer has exited the intake pipeline
     *
     * @param peer the peer that sent the event
     */
    void eventExitedIntakePipeline(@Nullable final NodeId peer);

    /**
     * Reset event counts
     */
    void reset();
}

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

package com.swirlds.platform.event.stale;

import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.ConsensusRound;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Detects when a self event becomes stale. This utility does not pay attention to events created by other nodes. This
 * utility may not observe a self event go stale if the node needs to reconnect or restart.
 */
public interface StaleEventDetector {

    /**
     * Add a newly created self event to the detector. If this event goes stale without this node restarting or
     * reconnecting, then the detector will observe the event go stale and take appropriate action.
     *
     * @param event the self event to add
     * @return a list of self events that have gone stale
     */
    @NonNull
    List<GossipEvent> addSelfEvent(@NonNull GossipEvent event);

    /**
     * Add a round that has just reached consensus.
     *
     * @param consensusRound a round that has just reached consensus
     * @return a list of self events that have gone stale
     */
    @NonNull
    List<GossipEvent> addConsensusRound(@NonNull ConsensusRound consensusRound);

    /**
     * Set the initial event window for the detector. Must be called after restart and reconnect.
     *
     * @param initialEventWindow the initial event window
     */
    void setInitialEventWindow(@NonNull EventWindow initialEventWindow);

    /**
     * Clear the internal state of the detector.
     */
    void clear();
}

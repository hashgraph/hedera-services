/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.consensus.RoundNumberProvider;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/** An interface for classes that calculate consensus of events */
public interface Consensus extends GraphGenerations, RoundNumberProvider {
    /**
     * Adds an event to the consensus object. This should be the only public method that modifies the state of the
     * object.
     *
     * @param event the event to be added
     * @return A list of consensus rounds, each with a list of consensus events (that can be empty). The rounds are
     * stored in consensus order (round at index 0 occurs before the round at index 1 in consensus time). Returns null
     * if no consensus was reached
     */
    @Nullable
    List<ConsensusRound> addEvent(@NonNull EventImpl event);

    /**
     * Load consensus from a snapshot. This will continue consensus from the round of the snapshot
     * once all the required events are provided. This method is called at restart and reconnect boundaries.
     */
    void loadSnapshot(@NonNull ConsensusSnapshot snapshot);
}

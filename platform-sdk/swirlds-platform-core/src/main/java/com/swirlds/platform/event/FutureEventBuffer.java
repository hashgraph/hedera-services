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

package com.swirlds.platform.event;

import com.swirlds.common.wiring.component.InputWireLabel;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.wiring.ClearTrigger;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * Buffers events from the future (i.e. events with a birth round that is greater than the round that consensus is
 * currently working on). It is important to note that the future event buffer is only used to store events from the
 * near future that can be fully validated. Events from beyond the event horizon (i.e. far future events that cannot be
 * immediately validated) are never stored by any part of the system.
 * <p>
 * Output from the future event buffer is guaranteed to preserve topological ordering, as long as the input to the
 * buffer is topologically ordered.
 */
public interface FutureEventBuffer {
    /**
     * Add an event to the future event buffer.
     *
     * @param event the event to add
     * @return a list containing the event if it is not a time traveler, or null if the event is from the future and
     * needs to be buffered.
     */
    @InputWireLabel("preconsensus event")
    @Nullable
    List<GossipEvent> addEvent(@NonNull GossipEvent event);

    /**
     * Update the current event window. As the event window advances, time catches up to time travelers, and events that
     * were previously from the future are now from the present.
     *
     * @param eventWindow the new event window
     * @return a list of events that were previously from the future but are now from the present
     */
    @InputWireLabel("non-ancient event window")
    @Nullable
    List<GossipEvent> updateEventWindow(@NonNull NonAncientEventWindow eventWindow);

    /**
     * Clear all data from the future event buffer.
     *
     * @param clearTrigger placeholder clearTrigger object
     */
    @InputWireLabel("clear")
    void clear(@NonNull ClearTrigger clearTrigger);
}

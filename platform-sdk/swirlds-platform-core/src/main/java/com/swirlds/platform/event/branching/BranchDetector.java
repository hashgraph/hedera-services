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

package com.swirlds.platform.event.branching;

import com.swirlds.common.event.PlatformEvent;
import com.swirlds.common.wiring.component.InputWireLabel;
import com.swirlds.platform.consensus.EventWindow;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Detects branching in the hashgraph.
 */
public interface BranchDetector {

    /**
     * Add an event to the branch detector. Events should be passed to this method in topological order.
     *
     * @param event the event to add
     * @return the event if it is a branching event or null if the event is not a branching event
     */
    @Nullable
    PlatformEvent checkForBranches(@NonNull PlatformEvent event);

    /**
     * Update the event window in the branch detector. This should be called whenever the event window is updated and
     * before the first event is added.
     *
     * @param eventWindow the new event window
     */
    @InputWireLabel("event window")
    void updateEventWindow(@NonNull EventWindow eventWindow);

    /**
     * Clear the branch detector, returning it to its initial state.
     */
    void clear();
}

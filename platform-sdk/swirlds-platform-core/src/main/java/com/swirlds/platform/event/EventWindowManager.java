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

import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.internal.ConsensusRound;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This component has a simple task: inform other components when the event window is shifted. This component is
 * stateless, and is defined in this form for the sake of interfacing with the wiring framework. This is not done with a
 * simple wire transformer due to the need to do out of band injection of nn event window into the system (i.e. at
 * restart and reconnect boundaries).
 */
public class EventWindowManager {

    public EventWindowManager() {}

    /**
     * Get the non-ancient event window for the given round.
     *
     * @param round the round
     * @return the non-ancient event window
     */
    @NonNull
    public NonAncientEventWindow roundReachedConsensus(@NonNull final ConsensusRound round) {
        return round.getNonAncientEventWindow();
    }

    /**
     * Manually override the non-ancient event window. A pass through method to make it possible to inject a non-ancient
     * event window into the wiring.
     *
     * @param nonAncientEventWindow the non-ancient event window
     * @return the non-ancient event window
     */
    @NonNull
    public NonAncientEventWindow manuallyOverrideNonAncientEventWindow(
            @NonNull final NonAncientEventWindow nonAncientEventWindow) {
        return nonAncientEventWindow;
    }
}

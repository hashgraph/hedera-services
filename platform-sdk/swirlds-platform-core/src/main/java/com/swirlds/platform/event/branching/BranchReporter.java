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

/**
 * This class is responsible for logging and producing metrics when a branch is observed.
 */
public interface BranchReporter {

    /**
     * Report a branching event.
     *
     * @param event the branching event
     */
    void reportBranch(@NonNull PlatformEvent event);

    /**
     * Update the event window.  This should be called whenever the event window is updated and before the first
     * branching event is reported.
     *
     * @param eventWindow the new event window
     */
    @InputWireLabel("event window")
    void updateEventWindow(@NonNull EventWindow eventWindow);

    /**
     * Clear the branch reporter, returning it to its initial state.
     */
    void clear();
}

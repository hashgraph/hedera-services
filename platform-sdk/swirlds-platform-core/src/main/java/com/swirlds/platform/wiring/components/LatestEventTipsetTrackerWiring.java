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

package com.swirlds.platform.wiring.components;

import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.gossip.shadowgraph.LatestEventTipsetTracker;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for the {@link com.swirlds.platform.gossip.shadowgraph.LatestEventTipsetTracker}.
 *
 * @param eventInput                 the input wire for events
 * @param nonAncientEventWindowInput the input wire for the non-ancient event window
 */
public record LatestEventTipsetTrackerWiring(
        @NonNull InputWire<EventImpl> eventInput,
        @NonNull InputWire<NonAncientEventWindow> nonAncientEventWindowInput) {

    /**
     * Create a new instance of this wiring.
     *
     * @param taskScheduler the task scheduler for this wiring
     * @return the new wiring instance
     */
    public static LatestEventTipsetTrackerWiring create(@NonNull final TaskScheduler<Void> taskScheduler) {
        return new LatestEventTipsetTrackerWiring(
                taskScheduler.buildInputWire("linked events"),
                taskScheduler.buildInputWire("non-ancient event window"));
    }

    /**
     * Bind a latest event tipset tracker to this wiring.
     *
     * @param latestEventTipsetTracker the latest event tipset tracker to bind
     */
    public void bind(@NonNull final LatestEventTipsetTracker latestEventTipsetTracker) {
        ((BindableInputWire<EventImpl, Void>) eventInput).bind(latestEventTipsetTracker::addEvent);
        ((BindableInputWire<NonAncientEventWindow, Void>) nonAncientEventWindowInput)
                .bind(latestEventTipsetTracker::setNonAncientEventWindow);
    }
}

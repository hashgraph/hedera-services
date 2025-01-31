/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.stream;

import com.swirlds.common.stream.RunningEventHashOverride;
import com.swirlds.component.framework.component.InputWireLabel;
import com.swirlds.platform.system.events.CesEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Generates event stream files when enableEventStreaming is true, and calculates runningHash for consensus Events.
 */
public interface ConsensusEventStream {

    /**
     * Adds a list of events to the event stream.
     *
     * @param events the list of events to add
     */
    @InputWireLabel("consensus events")
    void addEvents(@NonNull final List<CesEvent> events);

    /**
     * Updates the running hash with the given event hash. Called when a state is loaded.
     *
     * @param runningEventHashOverride the hash to update the running hash with
     */
    @InputWireLabel("hash override")
    void legacyHashOverride(@NonNull final RunningEventHashOverride runningEventHashOverride);
}

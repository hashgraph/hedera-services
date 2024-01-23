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

import com.swirlds.common.stream.EventStreamManager;
import com.swirlds.common.stream.RunningEventHashUpdate;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

// todo do we need to flush
public record EventStreamManagerWiring(
        @NonNull InputWire<List<EventImpl>> eventsInput,
        @NonNull InputWire<RunningEventHashUpdate> runningHashUpdateInput) {

    public static EventStreamManagerWiring create(@NonNull final TaskScheduler<Void> taskScheduler) {
        return new EventStreamManagerWiring(
                taskScheduler.buildInputWire("events"), taskScheduler.buildInputWire("running hash update"));
    }

    /**
     * Bind the {@link EventStreamManager} to this wiring
     *
     * @param eventStreamManager the event stream manager to bind
     */
    public void bind(@NonNull final EventStreamManager<EventImpl> eventStreamManager) {
        ((BindableInputWire<List<EventImpl>, Void>) eventsInput).bind(eventStreamManager::addEvents);
        ((BindableInputWire<RunningEventHashUpdate, Void>) runningHashUpdateInput)
                .bind(eventStreamManager::updateRunningHash);
    }
}

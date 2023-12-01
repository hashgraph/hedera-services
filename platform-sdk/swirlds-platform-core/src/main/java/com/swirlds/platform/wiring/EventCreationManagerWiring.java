/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.wiring;

import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.Bindable;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.creation.EventCreationManager;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * Wiring for the {@link EventCreationManager}.
 */
public class EventCreationManagerWiring {

    private final BindableInputWire<GossipEvent, GossipEvent> eventInput;
    private final BindableInputWire<Long, GossipEvent> minimumGenerationNonAncientInput;
    private final BindableInputWire<Boolean, GossipEvent> pauseInput;
    private final Bindable<Instant, GossipEvent> heartbeatBindable;
    private final OutputWire<GossipEvent> newEventOutput;

    /**
     * Create a new instance of this wiring.
     *
     * @param taskScheduler the task scheduler for the event creation manager
     * @return the new wiring instance
     */
    @NonNull
    public static EventCreationManagerWiring create(@NonNull final TaskScheduler<GossipEvent> taskScheduler) {
        return new EventCreationManagerWiring(taskScheduler);
    }

    /**
     * Constructor.
     *
     * @param taskScheduler the task scheduler for the event creation manager
     */
    private EventCreationManagerWiring(@NonNull final TaskScheduler<GossipEvent> taskScheduler) {
        eventInput = taskScheduler.buildInputWire("possible parents");
        minimumGenerationNonAncientInput = taskScheduler.buildInputWire("minimum generation non ancient");
        pauseInput = taskScheduler.buildInputWire("pause");
        newEventOutput = taskScheduler.getOutputWire();

        // TODO frequency needs to be configurable
        heartbeatBindable = taskScheduler.buildHeartbeatInputWire("heartbeat", 100);
    }

    /**
     * Bind an event creation manager to this wiring.
     *
     * @param eventCreationManager the event creation manager to bind
     */
    public void bind(@NonNull final EventCreationManager eventCreationManager) {
        eventInput.bind((@NonNull final GossipEvent event) -> {
            // FUTURE WORK: once the feature flag has been removed,
            // convert the internals of event creation to use GossipEvent
            // instead of EventImpl.
            final EventImpl eventImpl = new EventImpl(event.getHashedData(), event.getUnhashedData());
            eventCreationManager.registerEvent(eventImpl);
        });

        minimumGenerationNonAncientInput.bind(eventCreationManager::setMinimumGenerationNonAncient);

        pauseInput.bind(eventCreationManager::setPauseStatus);

        heartbeatBindable.bind(now -> {
            return eventCreationManager.maybeCreateEvent();
        });
    }

    /**
     * Get the input wire for possible parents.
     *
     * @return the input wire for possible parents
     */
    @NonNull
    public InputWire<GossipEvent> eventInput() {
        return eventInput;
    }

    /**
     * Get the input wire for the minimum generation non-ancient.
     *
     * @return the input wire for the minimum generation non-ancient
     */
    @NonNull
    public InputWire<Long> minimumGenerationNonAncientInput() {
        return minimumGenerationNonAncientInput;
    }

    /**
     * Get the input wire for pause operations.
     *
     * @return the input wire for pause operations
     */
    @NonNull
    public InputWire<Boolean> pauseInput() {
        return pauseInput;
    }

    /**
     * Get the output wire where newly created events are sent.
     *
     * @return the output wire for new events
     */
    @NonNull
    public OutputWire<GossipEvent> newEventOutput() {
        return newEventOutput;
    }
}

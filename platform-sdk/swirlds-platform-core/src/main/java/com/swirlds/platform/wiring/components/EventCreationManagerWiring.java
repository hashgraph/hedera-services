/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.Bindable;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.creation.EventCreationConfig;
import com.swirlds.platform.event.creation.EventCreationManager;
import com.swirlds.platform.wiring.ClearTrigger;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * Wiring for the {@link EventCreationManager}.
 */
public class EventCreationManagerWiring {

    private final TaskScheduler<GossipEvent> taskScheduler;

    private final BindableInputWire<GossipEvent, GossipEvent> eventInput;
    private final BindableInputWire<NonAncientEventWindow, GossipEvent> nonAncientEventWindowInput;
    private final BindableInputWire<ClearTrigger, GossipEvent> clearInput;
    private final Bindable<Instant, GossipEvent> heartbeatBindable;
    private final OutputWire<GossipEvent> newEventOutput;

    /**
     * Create a new instance of this wiring.
     *
     * @param platformContext the platform context
     * @param taskScheduler   the task scheduler for the event creation manager
     * @return the new wiring instance
     */
    @NonNull
    public static EventCreationManagerWiring create(
            @NonNull final PlatformContext platformContext, @NonNull final TaskScheduler<GossipEvent> taskScheduler) {
        return new EventCreationManagerWiring(platformContext, taskScheduler);
    }

    /**
     * Constructor.
     *
     * @param platformContext the platform context
     * @param taskScheduler   the task scheduler for the event creation manager
     */
    private EventCreationManagerWiring(
            @NonNull final PlatformContext platformContext, @NonNull final TaskScheduler<GossipEvent> taskScheduler) {

        this.taskScheduler = taskScheduler;

        eventInput = taskScheduler.buildInputWire("possible parents");
        nonAncientEventWindowInput = taskScheduler.buildInputWire("non-ancient event window");
        clearInput = taskScheduler.buildInputWire("clear");
        newEventOutput = taskScheduler.getOutputWire();

        final double frequency = platformContext
                .getConfiguration()
                .getConfigData(EventCreationConfig.class)
                .creationAttemptRate();
        heartbeatBindable = taskScheduler.buildHeartbeatInputWire("heartbeat", frequency);
    }

    /**
     * Bind an event creation manager to this wiring.
     *
     * @param eventCreationManager the event creation manager to bind
     */
    public void bind(@NonNull final EventCreationManager eventCreationManager) {
        eventInput.bindConsumer(eventCreationManager::registerEvent);
        nonAncientEventWindowInput.bindConsumer(eventCreationManager::setNonAncientEventWindow);
        clearInput.bindConsumer(eventCreationManager::clear);
        heartbeatBindable.bind(now -> eventCreationManager.maybeCreateEvent());
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
     * Get the input wire for the non-ancient event window.
     *
     * @return the input wire for the non-ancient event window
     */
    @NonNull
    public InputWire<NonAncientEventWindow> nonAncientEventWindowInput() {
        return nonAncientEventWindowInput;
    }

    /**
     * Get the input wire to clear the internal state of the event creation manager.
     *
     * @return the input wire to clear the internal state of the event creation manager
     */
    @NonNull
    public InputWire<ClearTrigger> clearInput() {
        return clearInput;
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

    /**
     * Flush the task scheduler.
     */
    public void flush() {
        taskScheduler.flush();
    }

    /**
     * Start squelching the task scheduler.
     */
    public void startSquelching() {
        taskScheduler.startSquelching();
    }

    /**
     * Stop squelching the task scheduler.
     */
    public void stopSquelching() {
        taskScheduler.stopSquelching();
    }
}

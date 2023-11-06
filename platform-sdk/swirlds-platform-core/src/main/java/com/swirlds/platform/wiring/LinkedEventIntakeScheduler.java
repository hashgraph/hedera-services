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

import com.swirlds.common.wiring.InputWire;
import com.swirlds.common.wiring.TaskScheduler;
import com.swirlds.common.wiring.WiringModel;
import com.swirlds.platform.components.LinkedEventIntake;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for the {@link LinkedEventIntakeScheduler}.
 */
public class LinkedEventIntakeScheduler {
    private final InputWire<EventImpl, Void> eventInput;

    /**
     * Constructor.
     *
     * @param model the wiring model
     */
    public LinkedEventIntakeScheduler(@NonNull final WiringModel model) {
        final TaskScheduler<Void> taskScheduler = model.schedulerBuilder("linkedEventIntake")
                .withConcurrency(false)
                .withUnhandledTaskCapacity(500)
                .withFlushingEnabled(true)
                .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                .build()
                .cast();

        eventInput = taskScheduler.buildInputWire("linked events");
    }

    /**
     * Gets the event input wire
     *
     * @return the event input wire
     */
    @NonNull
    public InputWire<EventImpl, Void> getEventInput() {
        return eventInput;
    }

    /**
     * Bind a linked event intake object to this scheduler.
     *
     * @param linkedEventIntake the linked event intake to bind
     */
    public void bind(@NonNull final LinkedEventIntake linkedEventIntake) {
        eventInput.bind(linkedEventIntake::addEvent);
    }
}

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
import com.swirlds.common.wiring.OutputWire;
import com.swirlds.common.wiring.TaskScheduler;
import com.swirlds.common.wiring.WiringModel;
import com.swirlds.platform.components.LinkedEventIntake;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Wiring for the {@link LinkedEventIntakeScheduler}.
 */
public class LinkedEventIntakeScheduler {
    private final InputWire<EventImpl, List<ConsensusRound>> eventInput;

    private final OutputWire<ConsensusRound> eventOutput;

    /**
     * Constructor.
     *
     * @param model the wiring model
     */
    public LinkedEventIntakeScheduler(@NonNull final WiringModel model) {
        final TaskScheduler<List<ConsensusRound>> taskScheduler = model.schedulerBuilder("linkedEventIntake")
                .withConcurrency(false)
                .withUnhandledTaskCapacity(500)
                .withFlushingEnabled(true)
                .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                .build()
                .cast();

        eventInput = taskScheduler.buildInputWire("linked events");
        eventOutput = taskScheduler.getOutputWire().buildSplitter();
    }

    /**
     * Gets the event input wire
     *
     * @return the event input wire
     */
    @NonNull
    public InputWire<EventImpl, List<ConsensusRound>> getEventInput() {
        return eventInput;
    }

    /**
     * Get the output of the linked event intake object, which is a stream of consensus rounds.
     *
     * @return the event output wire
     */
    @NonNull
    public OutputWire<ConsensusRound> getEventOutput() {
        return eventOutput;
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

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

package com.swirlds.common.wiring.model.internal.standard;

import com.swirlds.base.time.Time;
import com.swirlds.common.wiring.model.TraceableWiringModel;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.common.wiring.wires.output.StandardOutputWire;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.TimerTask;

/**
 * A task that produces a heartbeat at a specified rate.
 */
public class HeartbeatTask extends TimerTask {

    private final Time time;
    private final Duration period;
    private final StandardOutputWire<Instant> outputWire;

    /**
     * Constructor.
     *
     * @param model  the wiring model that this heartbeat is for
     * @param name   the name of the output wire
     * @param time   provides wall clock time
     * @param period the period of the heartbeat
     */
    public HeartbeatTask(
            @NonNull final TraceableWiringModel model,
            @NonNull final String name,
            @NonNull final Time time,
            @NonNull final Duration period) {
        this.time = Objects.requireNonNull(time);
        this.period = Objects.requireNonNull(period);
        Objects.requireNonNull(name);

        this.outputWire = new StandardOutputWire<>(model, name);
    }

    /**
     * Get the period of the heartbeat.
     *
     * @return the period of the heartbeat
     */
    @NonNull
    public Duration getPeriod() {
        return period;
    }

    /**
     * Get the output wire. Produces an Instant with the current time at the specified rate.
     *
     * @return the output wire
     */
    @NonNull
    public OutputWire<Instant> getOutputWire() {
        return outputWire;
    }

    /**
     * Produce a single heartbeat.
     */
    @Override
    public void run() {
        outputWire.forward(time.now());
    }
}

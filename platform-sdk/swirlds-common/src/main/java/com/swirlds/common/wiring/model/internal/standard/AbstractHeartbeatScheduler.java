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

import static com.swirlds.common.wiring.model.diagram.HyperlinkBuilder.platformCommonHyperlink;

import com.swirlds.base.time.Time;
import com.swirlds.common.wiring.model.TraceableWiringModel;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.common.wiring.wires.output.OutputWire;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A scheduler that produces heartbeats at a specified rate.
 */
public abstract class AbstractHeartbeatScheduler {

    private final TraceableWiringModel model;
    protected final Time time;
    protected final String name;
    protected final List<HeartbeatTask> tasks = new ArrayList<>();
    protected boolean started;

    /**
     * Constructor.
     *
     * @param model the wiring model containing this heartbeat scheduler
     * @param time  provides wall clock time
     * @param name  the name of the heartbeat scheduler
     */
    public AbstractHeartbeatScheduler(
            @NonNull final TraceableWiringModel model, @NonNull final Time time, @NonNull final String name) {
        this.model = Objects.requireNonNull(model);
        this.time = Objects.requireNonNull(time);
        this.name = Objects.requireNonNull(name);
        model.registerVertex(
                name, TaskSchedulerType.SEQUENTIAL, platformCommonHyperlink(AbstractHeartbeatScheduler.class), false);
    }

    /**
     * Build a wire that produces an instant (reflecting current time) at the specified rate. Note that the exact rate
     * of heartbeats may vary. This is a best effort algorithm, and actual rates may vary depending on a variety of
     * factors.
     *
     * @param period the period of the heartbeat. For example, setting a period of 100ms will cause the heartbeat to be
     *               sent at 10 hertz. Note that time is measured at millisecond precision, and so periods less than 1ms
     *               are not supported.
     * @return the output wire
     * @throws IllegalStateException if start has already been called
     */
    @NonNull
    public OutputWire<Instant> buildHeartbeatWire(@NonNull final Duration period) {
        if (started) {
            throw new IllegalStateException("Cannot create heartbeat wires after the heartbeat has started");
        }

        if (period.isNegative() || period.isZero()) {
            throw new IllegalArgumentException("Period must be positive");
        }

        if (period.toMillis() == 0) {
            throw new IllegalArgumentException(
                    "Time is measured at millisecond precision, and so periods less than 1ms are not supported. "
                            + "Requested period: " + period);
        }

        final HeartbeatTask task = new HeartbeatTask(model, name, time, period);
        tasks.add(task);

        return task.getOutputWire();
    }

    /**
     * Build a wire that produces an instant (reflecting current time) at the specified rate. Note that the exact rate
     * of heartbeats may vary. This is a best effort algorithm, and actual rates may vary depending on a variety of
     * factors.
     *
     * @param frequency the frequency of the heartbeat in hertz. Note that time is measured at millisecond precision,
     *                  and so frequencies greater than 1000hz are not supported.
     * @return the output wire
     */
    public OutputWire<Instant> buildHeartbeatWire(final double frequency) {
        if (frequency <= 0) {
            throw new IllegalArgumentException("Frequency must be positive");
        }
        final Duration period = Duration.ofMillis((long) (1000.0 / frequency));
        return buildHeartbeatWire(period);
    }

    /**
     * Start the heartbeats.
     */
    public abstract void start();

    /**
     * Stop the heartbeats.
     */
    public abstract void stop();
}

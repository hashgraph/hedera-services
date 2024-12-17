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

package com.swirlds.common.wiring.model.internal.deterministic;

import static com.swirlds.common.utility.CompareTo.isGreaterThanOrEqualTo;

import com.swirlds.base.time.Time;
import com.swirlds.common.wiring.model.TraceableWiringModel;
import com.swirlds.common.wiring.model.internal.standard.AbstractHeartbeatScheduler;
import com.swirlds.common.wiring.model.internal.standard.HeartbeatTask;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * A deterministic implementation of a heartbeat scheduler.
 */
public class DeterministicHeartbeatScheduler extends AbstractHeartbeatScheduler {

    /**
     * This maps from the period of the heartbeat to the list of heartbeats that want to be notified with that period.
     */
    private final Map<Duration, List<HeartbeatTask>> heartbeatsByPeriod = new HashMap<>();

    /**
     * The timestamps of the previous heartbeats sent for each group of heartbeats that subscribe to the same period.
     */
    private final Map<Duration, Instant> previousHeartbeats = new HashMap<>();

    /**
     * Constructor.
     *
     * @param model the wiring model containing this heartbeat scheduler
     * @param time  provides wall clock time
     * @param name  the name of the heartbeat scheduler
     */
    public DeterministicHeartbeatScheduler(
            @NonNull final TraceableWiringModel model, @NonNull final Time time, @NonNull final String name) {
        super(model, time, name);
    }

    /**
     * Send out heartbeats based on the amount of time that has passed.
     */
    public void tick() {
        if (!started) {
            throw new IllegalStateException("Cannot tick the heartbeat before it has started");
        }

        final Instant currentTime = time.now();

        for (final Entry<Duration, List<HeartbeatTask>> entry : heartbeatsByPeriod.entrySet()) {
            final Duration period = entry.getKey();
            final List<HeartbeatTask> tasksForPeriod = entry.getValue();
            final Instant previousHeartbeat = previousHeartbeats.get(period);
            final Duration timeSinceLastHeartbeat = Duration.between(previousHeartbeat, currentTime);
            if (isGreaterThanOrEqualTo(timeSinceLastHeartbeat, period)) {
                for (final HeartbeatTask task : tasksForPeriod) {
                    task.run();
                }
                previousHeartbeats.put(period, currentTime);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        if (started) {
            throw new IllegalStateException("Cannot start the heartbeat more than once");
        }

        final Instant currentTime = time.now();
        for (final HeartbeatTask task : tasks) {
            final List<HeartbeatTask> tasksForPeriod =
                    heartbeatsByPeriod.computeIfAbsent(task.getPeriod(), k -> new ArrayList<>());
            tasksForPeriod.add(task);
            previousHeartbeats.put(task.getPeriod(), currentTime);
        }

        started = true;
    }

    @Override
    public void stop() {
        if (!started) {
            throw new IllegalStateException("Cannot stop the heartbeat before it has started");
        }
    }
}

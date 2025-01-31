/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.swirlds.component.framework.model.internal.standard;

import com.swirlds.base.time.Time;
import com.swirlds.component.framework.model.StandardWiringModel;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Timer;

/**
 * A scheduler that produces heartbeats at a specified rate.
 */
public class HeartbeatScheduler extends AbstractHeartbeatScheduler {

    private final Timer timer = new Timer();

    /**
     * Constructor.
     *
     * @param model the wiring model containing this heartbeat scheduler
     * @param time  provides wall clock time
     * @param name  the name of the heartbeat scheduler
     */
    public HeartbeatScheduler(
            @NonNull final StandardWiringModel model, @NonNull final Time time, @NonNull final String name) {
        super(model, time, name);
    }

    /**
     * Start the heartbeats.
     */
    @Override
    public void start() {
        if (started) {
            throw new IllegalStateException("Cannot start the heartbeat more than once");
        }
        started = true;

        for (final HeartbeatTask task : tasks) {
            timer.scheduleAtFixedRate(task, 0, task.getPeriod().toMillis());
        }
    }

    /**
     * Stop the heartbeats.
     */
    @Override
    public void stop() {
        if (!started) {
            throw new IllegalStateException("Cannot stop the heartbeat before it has started");
        }
        timer.cancel();
    }
}

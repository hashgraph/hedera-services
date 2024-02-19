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

package com.swirlds.common.wiring.model.internal;

import com.swirlds.common.wiring.schedulers.TaskScheduler;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.time.Instant;

/**
 * A health monitor that does nothing. Useful for when health monitoring is not enabled.
 */
public class NoOpWiringHealthMonitor implements WiringHealthMonitor {

    /**
     * {@inheritDoc}
     */
    @Override
    public void checkHealth(@NonNull final Instant now) {
        // no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerScheduler(@NonNull final TaskScheduler<?> scheduler, final long stressedThreshold) {
        // no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isStressed() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public Duration stressedDuration() {
        return null;
    }
}

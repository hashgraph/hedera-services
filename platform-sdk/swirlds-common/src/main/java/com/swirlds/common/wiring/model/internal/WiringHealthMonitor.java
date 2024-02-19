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

public interface WiringHealthMonitor {

    /**
     * Check the health of the monitored schedulers.
     *
     * @param now the current time
     */
    void checkHealth(@NonNull final Instant now);

    /**
     * Request that the health of a scheduler be monitored.
     *
     * @param scheduler         the scheduler to monitor
     * @param stressedThreshold the capacity threshold at which the scheduler is considered to be stressed
     */
    void registerScheduler(@NonNull final TaskScheduler<?> scheduler, final long stressedThreshold);

    /**
     * Check if the system is stressed. A system is considered to be stressed if any of the monitored schedulers are
     * stressed.
     * <p>
     * This method is fully thread safe.
     *
     * @return true if the system is stressed
     */
    boolean isStressed();

    /**
     * Get the duration that the system has been stressed. Returns null if the system is not stressed.
     * <p>
     * This method is fully thread safe.
     *
     * @return the duration that the system has been stressed, or null if the system is not stressed
     */
    @Nullable
    Duration stressedDuration();
}

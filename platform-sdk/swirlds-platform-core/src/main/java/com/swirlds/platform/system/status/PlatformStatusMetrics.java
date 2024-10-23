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

package com.swirlds.platform.system.status;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.stats.StatConstructor;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Encapsulates metrics for the platform status.
 */
public class PlatformStatusMetrics {

    private final AtomicReference<PlatformStatus> currentStatus = new AtomicReference<>(PlatformStatus.STARTING_UP);

    /**
     * Constructor
     *
     * @param platformContext the platform context
     */
    public PlatformStatusMetrics(@NonNull final PlatformContext platformContext) {
        platformContext
                .getMetrics()
                .getOrCreate(StatConstructor.createEnumStat(
                        "PlatformStatus", Metrics.PLATFORM_CATEGORY, PlatformStatus.values(), currentStatus::get));
    }

    /**
     * Set the current status.
     *
     * @param status the new status
     */
    public void setCurrentStatus(@NonNull final PlatformStatus status) {
        currentStatus.set(status);
    }
}

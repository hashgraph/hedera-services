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
 * The default implementation of {@link PlatformStatusNexus}.
 * <p>
 * Future work: usages of this interface should be replaced by consuming the status updates from
 * {@link StatusStateMachine}
 */
public class DefaultPlatformStatusNexus implements PlatformStatusNexus {
    private final AtomicReference<PlatformStatus> currentStatus;

    /**
     * Constructor
     *
     * @param platformContext the platform context
     */
    public DefaultPlatformStatusNexus(@NonNull final PlatformContext platformContext) {
        this.currentStatus = new AtomicReference<>(PlatformStatus.STARTING_UP);

        platformContext
                .getMetrics()
                .getOrCreate(StatConstructor.createEnumStat(
                        "PlatformStatus", Metrics.PLATFORM_CATEGORY, PlatformStatus.values(), this::getCurrentStatus));
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PlatformStatus getCurrentStatus() {
        return currentStatus.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCurrentStatus(@NonNull final PlatformStatus status) {
        currentStatus.set(status);
    }
}

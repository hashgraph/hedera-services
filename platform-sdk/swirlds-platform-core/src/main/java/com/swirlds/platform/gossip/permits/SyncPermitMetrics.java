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

package com.swirlds.platform.gossip.permits;

import static com.swirlds.metrics.api.Metrics.PLATFORM_CATEGORY;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.metrics.api.IntegerGauge;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Encapsulates metrics for sync permits.
 */
public class SyncPermitMetrics {

    private static final IntegerGauge.Config PERMITS_AVAILABLE_CONFIG = new IntegerGauge.Config(
                    PLATFORM_CATEGORY, "syncPermitsAvailable")
            .withDescription("number of sync permits available");
    private final IntegerGauge permitsAvailable;

    private static final IntegerGauge.Config REVOKED_PERMITS_CONFIG = new IntegerGauge.Config(
                    PLATFORM_CATEGORY, "syncPermitsRevoked")
            .withDescription("number of sync permits revoked");
    private final IntegerGauge revokedPermits;

    private static final IntegerGauge.Config UTILIZED_PERMITS_CONFIG = new IntegerGauge.Config(
                    PLATFORM_CATEGORY, "syncPermitsUtilized")
            .withDescription("number of sync permits utilized");
    private final IntegerGauge utilizedPermits;

    /**
     * Constructor.
     *
     * @param platformContext the platform context
     */
    public SyncPermitMetrics(@NonNull final PlatformContext platformContext) {
        permitsAvailable = platformContext.getMetrics().getOrCreate(PERMITS_AVAILABLE_CONFIG);
        revokedPermits = platformContext.getMetrics().getOrCreate(REVOKED_PERMITS_CONFIG);
        utilizedPermits = platformContext.getMetrics().getOrCreate(UTILIZED_PERMITS_CONFIG);
    }

    /**
     * Reports information about the number of permits available, revoked, and used.
     *
     * @param permitsAvailable the number of permits available
     * @param permitsRevoked   the number of permits revoked
     * @param permitsUsed      the number of permits used
     */
    public void reportPermits(final int permitsAvailable, final int permitsRevoked, final int permitsUsed) {
        this.permitsAvailable.set(permitsAvailable);
        this.revokedPermits.set(permitsRevoked);
        this.utilizedPermits.set(permitsUsed);
    }
}

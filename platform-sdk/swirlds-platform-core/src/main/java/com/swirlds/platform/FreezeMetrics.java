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

package com.swirlds.platform;

import static com.swirlds.common.metrics.Metrics.INTERNAL_CATEGORY;

import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Used for creating freeze metrics.
 */
public final class FreezeMetrics {

    private FreezeMetrics() {}

    /**
     * Register freeze metrics.
     *
     * @param metrics                   the metrics engine
     * @param freezeManager             the freeze manager
     * @param startUpEventFrozenManager the start up event frozen manager
     */
    public static void registerFreezeMetrics(
            @NonNull final Metrics metrics,
            @NonNull final FreezeManager freezeManager,
            @NonNull final StartUpEventFrozenManager startUpEventFrozenManager) {

        Objects.requireNonNull(freezeManager);
        Objects.requireNonNull(startUpEventFrozenManager);

        metrics.getOrCreate(new FunctionGauge.Config<>(
                        INTERNAL_CATEGORY,
                        "isEvFrozen",
                        Boolean.class,
                        () -> freezeManager.isEventCreationFrozen()
                                || startUpEventFrozenManager.isEventCreationPausedAfterStartUp())
                .withDescription("is event creation frozen")
                .withUnit("is event creation frozen"));
    }
}

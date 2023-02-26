/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.system.EventCreationRule;
import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.platform.components.TransThrottleSyncRule;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * This class is used for pausing event creation for a while, when the node starts from a saved state and {@link
 * Settings#freezeSecondsAfterStartup} is positive
 */
public class StartUpEventFrozenManager implements TransThrottleSyncRule, EventCreationRule {
    /** the time when this platforms startup event frozen ends */
    private volatile Instant startUpEventFrozenEndTime = null;
    /** a boolean that indicates whether the statistics have been reset after the startup freeze */
    private volatile boolean freezeResetStatistics = false;
    /** Metrics system */
    private final Metrics metrics;
    /** Instant supplier used for unit testing */
    private final Supplier<Instant> now;

    StartUpEventFrozenManager(final Metrics metrics, final Supplier<Instant> now) {
        this.metrics = metrics;
        this.now = now;
    }

    boolean isEventCreationPausedAfterStartUp() {
        // We first check if the startup event frozen is active, if it is null, it is not active.
        // This is to prevent nodes from creation
        // multiple events with the same sequence number. When a node does a restart or a reconnect, it must first
        // check if any other node has events that it created but does not have in memory at that moment.
        if (startUpEventFrozenEndTime != null) {
            if (!now.get().isAfter(startUpEventFrozenEndTime)) {
                // startup freeze has NOT passed
                return true;
            }
            if (!freezeResetStatistics) {
                // after the startup freeze, we need to reset the statistics
                freezeResetStatistics = true;
                metrics.resetAll();
            }
        }
        return false;
    }

    Instant getStartUpEventFrozenEndTime() {
        return startUpEventFrozenEndTime;
    }

    void setStartUpEventFrozenEndTime(Instant startUpEventFrozenEndTime) {
        this.startUpEventFrozenEndTime = startUpEventFrozenEndTime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldSync() {
        // the node should sync during startup freeze
        return isEventCreationPausedAfterStartUp();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EventCreationRuleResponse shouldCreateEvent() {
        // the node should not create event during startup freeze
        if (isEventCreationPausedAfterStartUp()) {
            return EventCreationRuleResponse.DONT_CREATE;
        } else {
            return EventCreationRuleResponse.PASS;
        }
    }
}

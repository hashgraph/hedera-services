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

package com.swirlds.platform.event.tipset;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.common.system.platformstatus.PlatformStatus;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.platform.StartUpEventFrozenManager;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.eventhandling.EventTransactionPool;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * This class is responsible for blocking event creation during periods of time when we should not be creating events.
 */
public class TipsetEventCreationBlocker {

    private final int eventIntakeThrottle;
    private final EventTransactionPool transactionPool;
    private final QueueThread<EventIntakeTask> eventIntakeQueue;
    private final Supplier<PlatformStatus> platformStatusSupplier;
    private final StartUpEventFrozenManager startUpEventFrozenManager;

    /**
     * Constructor.
     *
     * @param platformContext           the platform's context
     * @param transactionPool           provides transactions to be added to new events
     * @param eventIntakeQueue          the queue to which new events should be added
     * @param platformStatusSupplier    provides the current platform status
     * @param startUpEventFrozenManager prevents event creation when the platform has just started up
     */
    public TipsetEventCreationBlocker(
            @NonNull final PlatformContext platformContext,
            @NonNull final EventTransactionPool transactionPool,
            @NonNull final QueueThread<EventIntakeTask> eventIntakeQueue,
            @NonNull final Supplier<PlatformStatus> platformStatusSupplier,
            @NonNull final StartUpEventFrozenManager startUpEventFrozenManager) {

        eventIntakeThrottle = platformContext
                .getConfiguration()
                .getConfigData(EventCreationConfig.class)
                .eventIntakeThrottle();

        this.transactionPool = Objects.requireNonNull(transactionPool);
        this.eventIntakeQueue = Objects.requireNonNull(eventIntakeQueue);
        this.platformStatusSupplier = Objects.requireNonNull(platformStatusSupplier);
        this.startUpEventFrozenManager = Objects.requireNonNull(startUpEventFrozenManager);
    }

    /**
     * Check if event creation is currently permitted.
     *
     * @return true if event creation is permitted, false otherwise
     */
    public boolean isEventCreationPermitted() {
        final PlatformStatus currentStatus = platformStatusSupplier.get();

        if (startUpEventFrozenManager.shouldCreateEvent() == EventCreationRuleResponse.DONT_CREATE) {
            // Eventually this behavior will be enforced using platform statuses
            return false;
        }

        if (currentStatus == PlatformStatus.FREEZING) {
            return transactionPool.numSignatureTransEvent() > 0;
        }

        if (currentStatus != PlatformStatus.ACTIVE && currentStatus != PlatformStatus.CHECKING) {
            return false;
        }

        return eventIntakeQueue.size() < eventIntakeThrottle;
    }
}

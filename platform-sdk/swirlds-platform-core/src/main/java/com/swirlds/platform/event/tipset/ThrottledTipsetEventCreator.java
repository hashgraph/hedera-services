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

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.common.system.platformstatus.PlatformStatus;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.utility.throttle.RateLimiter;
import com.swirlds.platform.StartUpEventFrozenManager;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.eventhandling.EventTransactionPool;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Responsible for creating new events using the tipset algorithm. Similar behavior as a {@link TipsetEventCreatorImpl},
 * but will sometimes choose not to create events for reasons other than the tipset algorithm.
 */
public class ThrottledTipsetEventCreator implements TipsetEventCreator {

    /**
     * Prevent new events from being created if the event intake queue ever meets or exceeds this size.
     */
    private final int eventIntakeThrottle;

    private final RateLimiter rateLimiter;
    private final EventTransactionPool transactionPool;
    private final QueueThread<EventIntakeTask> eventIntakeQueue;
    private final Supplier<PlatformStatus> platformStatusSupplier;
    private final StartUpEventFrozenManager startUpEventFrozenManager;

    private final TipsetEventCreator baseCreator;

    /**
     * Constructor.
     *
     * @param platformContext           the platform's context
     * @param time                      provides the wall clock time
     * @param transactionPool           provides transactions to be added to new events
     * @param eventIntakeQueue          the queue to which new events should be added
     * @param platformStatusSupplier    provides the current platform status
     * @param startUpEventFrozenManager prevents event creation when the platform has just started up
     * @param baseCreator               the base tipset event creator wrapped by this object
     */
    public ThrottledTipsetEventCreator(
            @NonNull final PlatformContext platformContext,
            @NonNull final Time time,
            @NonNull final EventTransactionPool transactionPool,
            @NonNull final QueueThread<EventIntakeTask> eventIntakeQueue,
            @NonNull final Supplier<PlatformStatus> platformStatusSupplier,
            @NonNull final StartUpEventFrozenManager startUpEventFrozenManager,
            @NonNull final TipsetEventCreator baseCreator) {

        eventIntakeThrottle = platformContext
                .getConfiguration()
                .getConfigData(EventCreationConfig.class)
                .eventIntakeThrottle();

        final EventCreationConfig eventCreationConfig =
                platformContext.getConfiguration().getConfigData(EventCreationConfig.class);

        final double maxCreationRate = eventCreationConfig.maxCreationRate();
        if (maxCreationRate > 0) {
            rateLimiter = new RateLimiter(time, maxCreationRate);
        } else {
            // No brakes!
            rateLimiter = null;
        }

        this.transactionPool = Objects.requireNonNull(transactionPool);
        this.eventIntakeQueue = Objects.requireNonNull(eventIntakeQueue);
        this.platformStatusSupplier = Objects.requireNonNull(platformStatusSupplier);
        this.startUpEventFrozenManager = Objects.requireNonNull(startUpEventFrozenManager);

        this.baseCreator = Objects.requireNonNull(baseCreator);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerEvent(@NonNull final EventImpl event) {
        baseCreator.registerEvent(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMinimumGenerationNonAncient(final long minimumGenerationNonAncient) {
        baseCreator.setMinimumGenerationNonAncient(minimumGenerationNonAncient);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method may choose not to create an event because of external factors such as the platform status or the
     * timing of the last self event, or it may choose not to create an event because of the tipset algorithm.
     */
    @Nullable
    @Override
    public GossipEvent maybeCreateEvent() {
        if (!isEventCreationPermitted()) {
            return null;
        }

        if (rateLimiter != null && !rateLimiter.request()) {
            // We have created a self event too recently
            return null;
        }

        final GossipEvent event = baseCreator.maybeCreateEvent();
        if (event != null) {
            if (rateLimiter != null) {
                rateLimiter.trigger();
            }
        }
        return event;
    }

    /**
     * Check if event creation is currently permitted by external factors. Does not consider the tipset algorithm or
     * timing. If this method returns true, it does not necessarily mean that the tipset algorithm will decide that now
     * is a good time to create an event, and it does not mean that enough time has elapsed since the last event to
     * create a new one.
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

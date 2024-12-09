/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package org.hiero.event.creator.impl.rules;

import static org.hiero.event.creator.EventCreationStatus.OVERLOADED;

import com.swirlds.common.context.PlatformContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.hiero.event.creator.EventCreationRule;
import org.hiero.event.creator.EventCreationStatus;
import org.hiero.event.creator.impl.EventCreationConfig;

/**
 * Prevents event creations when the system is stressed and unable to keep up with its work load.
 */
public class BackpressureRule implements EventCreationRule {

    /**
     * Prevent new events from being created if the event intake queue ever meets or exceeds this size.
     */
    private final int eventIntakeThrottle;

    private final LongSupplier eventIntakeQueueSize;

    /**
     * Constructor.
     *
     * @param platformContext      the platform's context
     * @param eventIntakeQueueSize provides the size of the event intake queue
     */
    public BackpressureRule(
            @NonNull final PlatformContext platformContext, @NonNull final LongSupplier eventIntakeQueueSize) {

        final EventCreationConfig eventCreationConfig =
                platformContext.getConfiguration().getConfigData(EventCreationConfig.class);

        eventIntakeThrottle = eventCreationConfig.eventIntakeThrottle();

        this.eventIntakeQueueSize = Objects.requireNonNull(eventIntakeQueueSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEventCreationPermitted() {
        return eventIntakeQueueSize.getAsLong() < eventIntakeThrottle;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void eventWasCreated() {
        // no-op
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public EventCreationStatus getEventCreationStatus() {
        return OVERLOADED;
    }
}

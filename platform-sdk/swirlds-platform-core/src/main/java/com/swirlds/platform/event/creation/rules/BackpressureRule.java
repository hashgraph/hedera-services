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

package com.swirlds.platform.event.creation.rules;

import static com.swirlds.platform.event.creation.EventCreationStatus.OVERLOADED;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.platform.event.creation.EventCreationConfig;
import com.swirlds.platform.event.creation.EventCreationStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.IntSupplier;

/**
 * Prevents event creations when the system is stressed and unable to keep up with its work load.
 */
public class BackpressureRule implements EventCreationRule {

    /**
     * Prevent new events from being created if the event intake queue ever meets or exceeds this size.
     */
    private final int eventIntakeThrottle;

    private final IntSupplier eventIntakeQueueSize;

    /**
     * Constructor.
     *
     * @param platformContext      the platform's context
     * @param eventIntakeQueueSize provides the size of the event intake queue
     */
    public BackpressureRule(
            @NonNull final PlatformContext platformContext, @NonNull final IntSupplier eventIntakeQueueSize) {

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
        return eventIntakeQueueSize.getAsInt() < eventIntakeThrottle;
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

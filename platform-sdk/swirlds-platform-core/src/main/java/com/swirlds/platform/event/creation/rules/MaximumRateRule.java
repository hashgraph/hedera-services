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

import static com.swirlds.platform.event.creation.EventCreationStatus.RATE_LIMITED;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.utility.throttle.RateLimiter;
import com.swirlds.platform.event.creation.EventCreationConfig;
import com.swirlds.platform.event.creation.EventCreationStatus;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Throttles event creation rate over time.
 */
public class MaximumRateRule implements EventCreationRule {

    private final RateLimiter rateLimiter;

    /**
     * Constructor.
     *
     * @param platformContext the platform context for this node
     * @param time            provides wall clock time
     */
    public MaximumRateRule(@NonNull final PlatformContext platformContext, @NonNull final Time time) {

        final EventCreationConfig eventCreationConfig =
                platformContext.getConfiguration().getConfigData(EventCreationConfig.class);

        final double maxCreationRate = eventCreationConfig.maxCreationRate();
        if (maxCreationRate > 0) {
            rateLimiter = new RateLimiter(time, maxCreationRate);
        } else {
            // No brakes!
            rateLimiter = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEventCreationPermitted() {
        if (rateLimiter != null) {
            return rateLimiter.request();
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void eventWasCreated() {
        if (rateLimiter != null) {
            rateLimiter.trigger();
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public EventCreationStatus getEventCreationStatus() {
        return RATE_LIMITED;
    }
}

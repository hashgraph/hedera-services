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

import static org.hiero.event.creator.EventCreationStatus.RATE_LIMITED;

import com.swirlds.common.config.EventCreationConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.utility.throttle.RateLimiter;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hiero.event.creator.EventCreationRule;
import org.hiero.event.creator.EventCreationStatus;

/**
 * Throttles event creation rate over time.
 */
public class MaximumRateRule implements EventCreationRule {

    private final RateLimiter rateLimiter;

    /**
     * Constructor.
     *
     * @param platformContext the platform context for this node
     */
    public MaximumRateRule(@NonNull final PlatformContext platformContext) {

        final EventCreationConfig eventCreationConfig =
                platformContext.getConfiguration().getConfigData(EventCreationConfig.class);

        final double maxCreationRate = eventCreationConfig.maxCreationRate();
        if (maxCreationRate > 0) {
            rateLimiter = new RateLimiter(platformContext.getTime(), maxCreationRate);
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

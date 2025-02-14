// SPDX-License-Identifier: Apache-2.0
package org.hiero.event.creator.impl.rules;

import static org.hiero.event.creator.EventCreationStatus.RATE_LIMITED;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.utility.throttle.RateLimiter;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hiero.event.creator.EventCreationRule;
import org.hiero.event.creator.EventCreationStatus;
import org.hiero.event.creator.impl.EventCreationConfig;

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

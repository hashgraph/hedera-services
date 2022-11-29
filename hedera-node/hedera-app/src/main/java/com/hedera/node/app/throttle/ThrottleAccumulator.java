package com.hedera.node.app.throttle;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * Keeps track of the amount of usage of different throttle categories (by {@code id}),
 * and returns whether the throttle has been exceeded after applying the given incremental
 * amount.
 */
public class ThrottleAccumulator {

    private static final Logger LOG = LoggerFactory.getLogger(ThrottleAccumulator.class);

    /**
     * Increments the throttle associated with {@code id} and returns whether the throttle
     * has been exceeded. If there is no throttle associated with {@code functionality}, then an
     * {@link IllegalArgumentException} will be thrown. This is to prevent bugs where
     * some code accidentally specified a throttle but a corresponding throttle was never
     * configured, leading to an open-throttle situation (i.e. an un-throttled attack vector).
     *
     * @param functionality The ID of the throttle to increment and check. This must exist.
     * @return true if the throttle has been exceeded, false otherwise.
     * @throws NullPointerException if (@code functionality} is {@code null}
     * @throws IllegalArgumentException if no throttle exists for {@code functionality}
     */
    public boolean shouldThrottle(@Nonnull final HederaFunctionality functionality) {
        // TODO: Implement (#4206)
        LOG.warn("ThrottleAccumulator.shouldThrottle() not implemented");
        return false;
    }
}

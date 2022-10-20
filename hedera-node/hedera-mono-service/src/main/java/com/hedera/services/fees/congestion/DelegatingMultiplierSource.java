package com.hedera.services.fees.congestion;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.throttling.annotations.HandleThrottle;
import com.hedera.services.utils.accessors.TxnAccessor;

import javax.inject.Inject;
import java.time.Instant;

class DelegatingMultiplierSource implements FeeMultiplierSource {
    private final ThrottleMultiplierSource delegate;

    protected DelegatingMultiplierSource(final ThrottleMultiplierSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public long currentMultiplier(final TxnAccessor accessor) {
        return delegate.currentMultiplier(accessor);
    }

    @Override
    public void resetExpectations() {
        delegate.resetExpectations();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    /**
     * Called by {@code handleTransaction} when the multiplier should be recomputed.
     *
     * <p>It is <b>crucial</b> that every node in the network reach the same conclusion for the
     * multiplier. Thus the {@link FunctionalityThrottling} implementation must provide a list of
     * throttle state snapshots that reflect the consensus of the entire network.
     *
     * <p>That is, the throttle states must be a child of the {@link
     * com.hedera.services.ServicesState}.
     */
    @Override
    public void updateMultiplier(final TxnAccessor accessor, final Instant consensusNow) {
        delegate.updateMultiplier(accessor, consensusNow);
    }

    @Override
    public void resetCongestionLevelStarts(final Instant[] savedStartTimes) {
        delegate.resetCongestionLevelStarts(savedStartTimes);
    }

    @Override
    public Instant[] congestionLevelStarts() {
        /* If the Platform is serializing a fast-copy of the MerkleNetworkContext,
        and that copy references this object's congestionLevelStarts, we will get
        a (transient) ISS if the congestion level changes mid-serialization on one
        node but not others. */
        return delegate.congestionLevelStarts();
    }

    @VisibleForTesting
    public ThrottleMultiplierSource getDelegate() {
        return delegate;
    }
}

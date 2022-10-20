/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.fees;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.congestion.ThrottleMultiplierSource;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.throttling.annotations.HandleThrottle;
import com.hedera.services.utils.accessors.TxnAccessor;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class TxnRateFeeMultiplierSource implements FeeMultiplierSource {
    private static final Logger log = LogManager.getLogger(TxnRateFeeMultiplierSource.class);
    private final ThrottleMultiplierSource delegate;

    @Inject
    public TxnRateFeeMultiplierSource(
            GlobalDynamicProperties properties,
            @HandleThrottle FunctionalityThrottling throttling) {
        delegate =
                new ThrottleMultiplierSource(
                        "logical TPS",
                        "TPS",
                        "CryptoTransfer throughput",
                        log,
                        properties::feesMinCongestionPeriod,
                        properties::congestionMultipliers,
                        () -> throttling.activeThrottlesFor(CryptoTransfer));
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
    ThrottleMultiplierSource getDelegate() {
        return delegate;
    }
}

package com.hedera.services.stats;

import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.swirlds.common.system.Platform;

import javax.inject.Singleton;

@Singleton
public class ExpiryStats {
    private final MerkleNetworkContext networkCtx;

    public ExpiryStats(MerkleNetworkContext networkCtx) {
        this.networkCtx = networkCtx;
    }

    public void registerWith(final Platform platform) {
        throw new AssertionError("Not implemented");
    }

    public void updateAll() {
        throw new AssertionError("Not implemented");
    }
}

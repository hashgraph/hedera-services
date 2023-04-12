package com.hedera.node.app.integration.facilities;

import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;

@Singleton
public class ReplayAdvancingConsensusNow {
    private Instant consensusNow = Instant.EPOCH;

    @Inject
    public ReplayAdvancingConsensusNow() {
    }

    public Instant get() {
        return consensusNow;
    }

    public void set(@NonNull final Instant consensusNow) {
        this.consensusNow = consensusNow;
    }
}

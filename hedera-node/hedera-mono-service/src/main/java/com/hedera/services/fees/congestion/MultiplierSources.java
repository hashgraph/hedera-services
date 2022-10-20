package com.hedera.services.fees.congestion;

import com.hedera.services.utils.accessors.TxnAccessor;

import java.time.Instant;

public class MultiplierSources {
    private final FeeMultiplierSource gasFeeMultiplier;
    private final FeeMultiplierSource genericFeeMultiplier;

    public MultiplierSources(
            final FeeMultiplierSource genericFeeMultiplier,
            final FeeMultiplierSource gasFeeMultiplier) {
        this.genericFeeMultiplier = genericFeeMultiplier;
        this.gasFeeMultiplier = gasFeeMultiplier;
    }

    public void updateMultiplier(final TxnAccessor accessor, final Instant consensusNow) {
        gasFeeMultiplier.updateMultiplier(accessor, consensusNow);
        genericFeeMultiplier.updateMultiplier(accessor, consensusNow);
    }


    public void resetCongestionLevelStarts(
            final Instant[] gasSavedStartTimes,
            final Instant[] genericSavedStartTimes) {
        gasFeeMultiplier.resetCongestionLevelStarts(gasSavedStartTimes);
        genericFeeMultiplier.resetCongestionLevelStarts(genericSavedStartTimes);
    }

    public Instant[] gasCongestionStarts() {
        return gasFeeMultiplier.congestionLevelStarts();
    }

    public Instant[] genericCongestionStarts() {
        return genericFeeMultiplier.congestionLevelStarts();
    }

    public void resetExpectations() {
        gasFeeMultiplier.resetExpectations();
        genericFeeMultiplier.resetExpectations();
    }
}

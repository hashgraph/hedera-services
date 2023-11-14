package com.swirlds.platform.state.signed;

import java.time.Instant;

public record StateSavingResult(
        long round,
        boolean success,
        boolean outOfBand,
        boolean freezeState,
        Instant consensusTimestamp) {
    public boolean inBand(){
        return !outOfBand;
    }
}

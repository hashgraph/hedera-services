package com.swirlds.platform.state.signed;

import java.time.Instant;

public record StateSavingResult(
        long round,
        boolean freezeState,
        Instant consensusTimestamp,
        long minGen) {
}

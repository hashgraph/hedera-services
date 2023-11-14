package com.swirlds.platform.state.signed;

import java.time.Instant;

public record StateSavingResult(
        long round,
        boolean success,
        boolean freezeState,
        Instant consensusTimestamp) {
}

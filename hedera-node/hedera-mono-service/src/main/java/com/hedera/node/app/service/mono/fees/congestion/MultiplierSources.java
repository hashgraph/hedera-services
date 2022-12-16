/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.fees.congestion;

import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
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

    public long maxCurrentMultiplier(final TxnAccessor accessor) {
        return Math.max(
                gasFeeMultiplier.currentMultiplier(accessor),
                genericFeeMultiplier.currentMultiplier(accessor));
    }

    public void updateMultiplier(final TxnAccessor accessor, final Instant consensusNow) {
        gasFeeMultiplier.updateMultiplier(accessor, consensusNow);
        genericFeeMultiplier.updateMultiplier(accessor, consensusNow);
    }

    public void resetGenericCongestionLevelStarts(final Instant[] startTimes) {
        genericFeeMultiplier.resetCongestionLevelStarts(startTimes);
    }

    public void resetGasCongestionLevelStarts(final Instant[] startTimes) {
        gasFeeMultiplier.resetCongestionLevelStarts(startTimes);
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

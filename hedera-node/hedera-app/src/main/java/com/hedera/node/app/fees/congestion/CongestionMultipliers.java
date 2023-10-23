/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.fees.congestion;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

public class CongestionMultipliers {
    private final TransactionRateMultiplier genericFeeMultiplier;
    private final ThrottleMultiplier gasFeeMultiplier;

    public CongestionMultipliers(
            @NonNull final TransactionRateMultiplier genericFeeMultiplier,
            @NonNull final ThrottleMultiplier gasFeeMultiplier) {
        this.genericFeeMultiplier = requireNonNull(genericFeeMultiplier, "genericFeeMultiplier must not be null");
        this.gasFeeMultiplier = requireNonNull(gasFeeMultiplier, "gasFeeMultiplier must not be null");
    }

    public void updateMultiplier(@NonNull final Instant consensusTime) {
        gasFeeMultiplier.updateMultiplier(consensusTime);
        genericFeeMultiplier.updateMultiplier(consensusTime);
    }

    public long maxCurrentMultiplier(@NonNull final TransactionInfo txnInfo, @NonNull final SavepointStackImpl stack) {
        return Math.max(gasFeeMultiplier.currentMultiplier(), genericFeeMultiplier.currentMultiplier(txnInfo, stack));
    }

    @NonNull
    public Instant[] genericCongestionStarts() {
        return genericFeeMultiplier.congestionLevelStarts();
    }

    @NonNull
    public Instant[] gasCongestionStarts() {
        return gasFeeMultiplier.congestionLevelStarts();
    }

    public void resetGenericCongestionLevelStarts(@NonNull final Instant[] startTimes) {
        genericFeeMultiplier.resetCongestionLevelStarts(startTimes);
    }

    public void resetGasCongestionLevelStarts(@NonNull final Instant[] startTimes) {
        gasFeeMultiplier.resetCongestionLevelStarts(startTimes);
    }

    public void resetExpectations() {
        gasFeeMultiplier.resetExpectations();
        genericFeeMultiplier.resetExpectations();
    }
}

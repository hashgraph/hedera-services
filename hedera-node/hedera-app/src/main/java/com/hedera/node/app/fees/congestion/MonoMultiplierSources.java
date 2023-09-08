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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.service.mono.fees.congestion.FeeMultiplierSource;
import com.hedera.node.app.service.mono.fees.congestion.MultiplierSources;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.time.Instant;

public class MonoMultiplierSources {
    private final MultiplierSources delegate;

    public MonoMultiplierSources(
            final FeeMultiplierSource genericFeeMultiplier, final FeeMultiplierSource gasFeeMultiplier) {
        this.delegate = new MultiplierSources(genericFeeMultiplier, gasFeeMultiplier);
    }

    public void updateMultiplier(Instant consensusTime) {
        TxnAccessor accessor = null;
        try {
            // only accessor.congestionExempt() is used in the mono multiplier implementation and that seems like could
            // be only set for triggered transactions
            // so using just a dummy accessor
            accessor = SignedTxnAccessor.from(Bytes.EMPTY.toByteArray());
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
        this.delegate.updateMultiplier(accessor, consensusTime);
    }

    public Instant[] genericCongestionStarts() {
        return delegate.genericCongestionStarts();
    }

    public Instant[] gasCongestionStarts() {
        return delegate.gasCongestionStarts();
    }

    public void resetGenericCongestionLevelStarts(final Instant[] startTimes) {
        delegate.resetGenericCongestionLevelStarts(startTimes);
    }

    public void resetGasCongestionLevelStarts(final Instant[] startTimes) {
        delegate.resetGasCongestionLevelStarts(startTimes);
    }

    public void resetExpectations() {
        delegate.resetExpectations();
    }
}

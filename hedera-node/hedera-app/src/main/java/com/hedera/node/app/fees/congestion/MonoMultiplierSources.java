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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.fees.congestion.FeeMultiplierSource;
import com.hedera.node.app.service.mono.fees.congestion.MultiplierSources;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

public class MonoMultiplierSources {
    private static final TxnAccessor accessor;

    static {
        try {
            // only accessor.congestionExempt() is used in the mono multiplier implementation and that seems like could
            // be only set for triggered transactions
            // so using just a dummy accessor
            accessor = SignedTxnAccessor.from(Transaction.PROTOBUF
                    .toBytes(Transaction.newBuilder()
                            .signedTransactionBytes(SignedTransaction.PROTOBUF.toBytes(SignedTransaction.newBuilder()
                                    .bodyBytes(TransactionBody.PROTOBUF.toBytes(TransactionBody.newBuilder()
                                            .cryptoTransfer(CryptoTransferTransactionBody.newBuilder()
                                                    .build())
                                            .build()))
                                    .build()))
                            .build())
                    .toByteArray());
        } catch (InvalidProtocolBufferException e) {
            //             this should never happen because we use a dummy transaction which should be always valid
            throw new RuntimeException(e);
        }
    }

    private final MultiplierSources delegate;

    public MonoMultiplierSources(
            @NonNull final FeeMultiplierSource genericFeeMultiplier,
            @NonNull final FeeMultiplierSource gasFeeMultiplier) {
        requireNonNull(genericFeeMultiplier, "genericFeeMultiplier must not be null");
        requireNonNull(gasFeeMultiplier, "gasFeeMultiplier must not be null");

        this.delegate = new MultiplierSources(genericFeeMultiplier, gasFeeMultiplier);
    }

    public void updateMultiplier(@NonNull final Instant consensusTime) {
        this.delegate.updateMultiplier(accessor, consensusTime);
    }

    @NonNull
    public Instant[] genericCongestionStarts() {
        return delegate.genericCongestionStarts();
    }

    @NonNull
    public Instant[] gasCongestionStarts() {
        return delegate.gasCongestionStarts();
    }

    public void resetGenericCongestionLevelStarts(@NonNull final Instant[] startTimes) {
        delegate.resetGenericCongestionLevelStarts(startTimes);
    }

    public void resetGasCongestionLevelStarts(@NonNull final Instant[] startTimes) {
        delegate.resetGasCongestionLevelStarts(startTimes);
    }

    public void resetExpectations() {
        delegate.resetExpectations();
    }
}

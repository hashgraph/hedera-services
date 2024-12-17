/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.throttle;

import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_TRANSFER;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fees.congestion.CongestionMultipliers;
import com.hedera.node.app.throttle.annotations.BackendThrottle;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Implementation of {@link NetworkUtilizationManager}  that delegates to injected
 * {@link ThrottleAccumulator} and {@link CongestionMultipliers}.
 */
@Singleton
public class NetworkUtilizationManagerImpl implements NetworkUtilizationManager {
    private final ThrottleAccumulator backendThrottle;
    private final CongestionMultipliers congestionMultipliers;

    @Inject
    public NetworkUtilizationManagerImpl(
            @NonNull @BackendThrottle final ThrottleAccumulator backendThrottle,
            @NonNull final CongestionMultipliers congestionMultipliers) {
        this.backendThrottle = requireNonNull(backendThrottle, "backendThrottle must not be null");
        this.congestionMultipliers = requireNonNull(congestionMultipliers, "congestionMultipliers must not be null");
    }

    @Override
    public boolean trackTxn(
            @NonNull final TransactionInfo txnInfo, @NonNull final Instant consensusTime, @NonNull final State state) {
        final var shouldThrottle = backendThrottle.checkAndEnforceThrottle(txnInfo, consensusTime, state);
        congestionMultipliers.updateMultiplier(consensusTime);
        return shouldThrottle;
    }

    @Override
    public void trackFeePayments(@NonNull final Instant consensusNow, @NonNull final State state) {
        // Used to update network utilization after charging fees for an invalid transaction
        final var chargingFeesCryptoTransfer = new TransactionInfo(
                Transaction.DEFAULT,
                TransactionBody.DEFAULT,
                TransactionID.DEFAULT,
                AccountID.DEFAULT,
                SignatureMap.DEFAULT,
                Bytes.EMPTY,
                CRYPTO_TRANSFER,
                null);
        trackTxn(chargingFeesCryptoTransfer, consensusNow, state);
    }

    @Override
    public boolean wasLastTxnGasThrottled() {
        return backendThrottle.wasLastTxnGasThrottled();
    }

    @Override
    public void leakUnusedGasPreviouslyReserved(@NonNull final TransactionInfo txnInfo, long value) {
        backendThrottle.leakUnusedGasPreviouslyReserved(txnInfo, value);
    }

    @Override
    public boolean shouldThrottle(
            @NonNull final TransactionInfo txnInfo, @NonNull final State state, @NonNull final Instant consensusTime) {
        return backendThrottle.checkAndEnforceThrottle(txnInfo, consensusTime, state);
    }

    @Override
    public boolean shouldThrottleNOfUnscaled(
            final int n, @NonNull final HederaFunctionality function, @NonNull final Instant consensusTime) {
        return backendThrottle.shouldThrottleNOfUnscaled(n, function, consensusTime);
    }
}

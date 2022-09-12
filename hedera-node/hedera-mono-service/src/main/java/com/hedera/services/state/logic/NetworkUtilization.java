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
package com.hedera.services.state.logic;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONSENSUS_GAS_EXHAUSTED;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.fees.FeeMultiplierSource;
import com.hedera.services.fees.charging.TxnChargingPolicyAgent;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.throttling.annotations.HandleThrottle;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Small manager component that does the work of tracking network utilization (and its impact on
 * congestion pricing) by delegating to an injected {@link FunctionalityThrottling} and {@link
 * FeeMultiplierSource}.
 *
 * <p>Also responsible for setting {@code CONSENSUS_GAS_EXHAUSTED} and refunding the payer service
 * fee if a contract transaction was gas-throttled.
 */
@Singleton
public class NetworkUtilization {
    // Used to update network utilization after a user-submitted transaction fails the signature
    // validity
    // screen; the stand-in is a CryptoTransfer because it best reflects the work done charging fees
    static final TxnAccessor STAND_IN_CRYPTO_TRANSFER =
            SignedTxnAccessor.uncheckedFrom(
                    Transaction.newBuilder()
                            .setSignedTransactionBytes(
                                    SignedTransaction.newBuilder()
                                            .setBodyBytes(
                                                    TransactionBody.newBuilder()
                                                            .setCryptoTransfer(
                                                                    CryptoTransferTransactionBody
                                                                            .getDefaultInstance())
                                                            .build()
                                                            .toByteString())
                                            .build()
                                            .toByteString())
                            .build());

    static {
        STAND_IN_CRYPTO_TRANSFER.setNumAutoCreations(0);
    }

    private final TransactionContext txnCtx;
    private final FeeMultiplierSource feeMultiplierSource;
    private final TxnChargingPolicyAgent chargingPolicyAgent;
    private final FunctionalityThrottling handleThrottling;

    @Inject
    public NetworkUtilization(
            final TransactionContext txnCtx,
            final FeeMultiplierSource feeMultiplierSource,
            final TxnChargingPolicyAgent chargingPolicyAgent,
            final @HandleThrottle FunctionalityThrottling handleThrottling) {
        this.txnCtx = txnCtx;
        this.feeMultiplierSource = feeMultiplierSource;
        this.chargingPolicyAgent = chargingPolicyAgent;
        this.handleThrottling = handleThrottling;
    }

    void trackUserTxn(final TxnAccessor accessor, final Instant now) {
        track(accessor, now);
    }

    void trackFeePayments(final Instant now) {
        track(STAND_IN_CRYPTO_TRANSFER, now);
    }

    private void track(final TxnAccessor accessor, final Instant now) {
        handleThrottling.shouldThrottleTxn(accessor);
        feeMultiplierSource.updateMultiplier(accessor, now);
    }

    boolean screenForAvailableCapacity() {
        if (handleThrottling.wasLastTxnGasThrottled()) {
            txnCtx.setStatus(CONSENSUS_GAS_EXHAUSTED);
            chargingPolicyAgent.refundPayerServiceFee();
            return false;
        } else {
            return true;
        }
    }
}

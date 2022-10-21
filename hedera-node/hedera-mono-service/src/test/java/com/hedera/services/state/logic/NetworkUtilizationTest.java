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

import static com.hedera.services.state.logic.NetworkUtilization.STAND_IN_CRYPTO_TRANSFER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONSENSUS_GAS_EXHAUSTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.fees.FeeMultiplierSource;
import com.hedera.services.fees.charging.TxnChargingPolicyAgent;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NetworkUtilizationTest {
    private final Instant consensusNow = Instant.ofEpochSecond(1_234_567L, 890);

    @Mock private TransactionContext txnCtx;
    @Mock private FeeMultiplierSource feeMultiplierSource;
    @Mock private TxnChargingPolicyAgent chargingPolicyAgent;
    @Mock private FunctionalityThrottling handleThrottling;
    @Mock private TxnAccessor accessor;

    private NetworkUtilization subject;

    @BeforeEach
    void setUp() {
        subject =
                new NetworkUtilization(
                        txnCtx, feeMultiplierSource, chargingPolicyAgent, handleThrottling);
    }

    @Test
    void tracksUserTxnAsExpected() {
        subject.trackUserTxn(accessor, consensusNow);

        verify(handleThrottling).shouldThrottleTxn(accessor);
        verify(feeMultiplierSource).updateMultiplier(accessor, consensusNow);
    }

    @Test
    void tracksFeePaymentsAsExpected() {
        subject.trackFeePayments(consensusNow);

        verify(handleThrottling).shouldThrottleTxn(STAND_IN_CRYPTO_TRANSFER);
        verify(feeMultiplierSource).updateMultiplier(STAND_IN_CRYPTO_TRANSFER, consensusNow);
    }

    @Test
    void standInCryptoTransferHasExpectedProperties() {
        assertEquals(HederaFunctionality.CryptoTransfer, STAND_IN_CRYPTO_TRANSFER.getFunction());
        assertTrue(STAND_IN_CRYPTO_TRANSFER.areAutoCreationsCounted());
    }

    @Test
    void happyPathWorks() {
        assertTrue(subject.screenForAvailableCapacity());
    }

    @Test
    void rejectsAsExpectedAfterGasThrottledTxn() {
        given(handleThrottling.wasLastTxnGasThrottled()).willReturn(true);

        assertFalse(subject.screenForAvailableCapacity());

        verify(txnCtx).setStatus(CONSENSUS_GAS_EXHAUSTED);
        verify(chargingPolicyAgent).refundPayerServiceFee();
    }
}

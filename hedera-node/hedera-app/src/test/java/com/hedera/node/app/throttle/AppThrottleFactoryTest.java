/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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
import static com.hedera.node.app.throttle.ThrottleAccumulator.ThrottleType.BACKEND_THROTTLE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshot;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshots;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.hapi.utils.throttles.DeterministicThrottle;
import com.hedera.node.app.hapi.utils.throttles.GasLimitDeterministicThrottle;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.State;
import java.time.Instant;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppThrottleFactoryTest {
    private static final int SPLIT_FACTOR = 7;
    private static final Instant CONSENSUS_NOW = Instant.ofEpochSecond(123456, 789);
    private static final AccountID PAYER_ID =
            AccountID.newBuilder().accountNum(666L).build();
    private static final TransactionInfo TXN_INFO = new TransactionInfo(
            Transaction.DEFAULT,
            TransactionBody.newBuilder()
                    .cryptoTransfer(CryptoTransferTransactionBody.DEFAULT)
                    .build(),
            TransactionID.DEFAULT,
            PAYER_ID,
            SignatureMap.DEFAULT,
            Bytes.EMPTY,
            CRYPTO_TRANSFER,
            null);
    private static final ThrottleUsageSnapshots FAKE_SNAPSHOTS = new ThrottleUsageSnapshots(
            List.of(
                    new ThrottleUsageSnapshot(1L, new Timestamp(234567, 8)),
                    new ThrottleUsageSnapshot(2L, new Timestamp(345678, 9))),
            ThrottleUsageSnapshot.DEFAULT);

    @Mock
    private State state;

    @Mock
    private Supplier<Configuration> config;

    @Mock
    private ThrottleAccumulator throttleAccumulator;

    @Mock
    private DeterministicThrottle firstThrottle;

    @Mock
    private DeterministicThrottle lastThrottle;

    @Mock
    private GasLimitDeterministicThrottle gasThrottle;

    @Mock
    private AppThrottleFactory.ThrottleAccumulatorFactory throttleAccumulatorFactory;

    private AppThrottleFactory subject;

    @BeforeEach
    void setUp() {
        subject = new AppThrottleFactory(
                config, () -> state, () -> ThrottleDefinitions.DEFAULT, throttleAccumulatorFactory);
    }

    @Test
    void initializesAccumulatorFromCurrentConfigAndGivenDefinitions() {
        given(throttleAccumulatorFactory.newThrottleAccumulator(
                        eq(config), argThat((IntSupplier i) -> i.getAsInt() == SPLIT_FACTOR), eq(BACKEND_THROTTLE)))
                .willReturn(throttleAccumulator);
        given(throttleAccumulator.allActiveThrottles()).willReturn(List.of(firstThrottle, lastThrottle));
        given(throttleAccumulator.gasLimitThrottle()).willReturn(gasThrottle);

        final var throttle = subject.newThrottle(SPLIT_FACTOR, FAKE_SNAPSHOTS);

        verify(throttleAccumulator).applyGasConfig();
        verify(throttleAccumulator).rebuildFor(ThrottleDefinitions.DEFAULT);
        verify(firstThrottle).resetUsageTo(FAKE_SNAPSHOTS.tpsThrottles().getFirst());
        verify(lastThrottle).resetUsageTo(FAKE_SNAPSHOTS.tpsThrottles().getLast());
        verify(gasThrottle).resetUsageTo(FAKE_SNAPSHOTS.gasThrottleOrThrow());

        given(throttleAccumulator.checkAndEnforceThrottle(TXN_INFO, CONSENSUS_NOW, state))
                .willReturn(true);
        assertThat(throttle.allow(PAYER_ID, TXN_INFO.txBody(), TXN_INFO.functionality(), CONSENSUS_NOW))
                .isFalse();

        given(firstThrottle.usageSnapshot())
                .willReturn(FAKE_SNAPSHOTS.tpsThrottles().getFirst());
        given(lastThrottle.usageSnapshot())
                .willReturn(FAKE_SNAPSHOTS.tpsThrottles().getLast());
        given(gasThrottle.usageSnapshot()).willReturn(FAKE_SNAPSHOTS.gasThrottleOrThrow());
        assertEquals(FAKE_SNAPSHOTS, throttle.usageSnapshots());
    }
}

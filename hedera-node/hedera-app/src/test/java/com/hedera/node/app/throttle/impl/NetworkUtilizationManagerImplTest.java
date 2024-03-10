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

package com.hedera.node.app.throttle.impl;

import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_TRANSFER;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fees.congestion.CongestionMultipliers;
import com.hedera.node.app.hapi.utils.throttles.DeterministicThrottle;
import com.hedera.node.app.hapi.utils.throttles.GasLimitDeterministicThrottle;
import com.hedera.node.app.spi.fixtures.util.LogCaptor;
import com.hedera.node.app.spi.fixtures.util.LogCaptureExtension;
import com.hedera.node.app.spi.fixtures.util.LoggingSubject;
import com.hedera.node.app.spi.fixtures.util.LoggingTarget;
import com.hedera.node.app.spi.state.ReadableSingletonState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableSingletonState;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.throttle.NetworkUtilizationManagerImpl;
import com.hedera.node.app.throttle.ThrottleAccumulator;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class NetworkUtilizationManagerImplTest {
    private final Instant consensusNow = Instant.ofEpochSecond(1_234_567L, 123);

    private final DeterministicThrottle throttle = DeterministicThrottle.withTpsAndBurstPeriodMsNamed(500, 10, "test");
    private final GasLimitDeterministicThrottle gasThrottle = new GasLimitDeterministicThrottle(100);

    @LoggingSubject
    private NetworkUtilizationManagerImpl subject;

    @LoggingTarget
    private LogCaptor logCaptor;

    @Mock
    private ThrottleAccumulator throttleAccumulator;

    @Mock
    private CongestionMultipliers congestionMultipliers;

    @Mock
    private TransactionInfo transactionInfo;

    @Mock
    private HederaState state;

    @Mock
    private ReadableStates readableStates;

    @Mock
    private ReadableSingletonState readableThrottleUsageSnapshotsState;

    @Mock
    private ReadableSingletonState readableCongestionLevelsStartsState;

    @Mock
    private WritableStates writableStates;

    @Mock
    private WritableSingletonState writableThrottleUsageSnapshotsState;

    @Mock
    private WritableSingletonState writableCongestionLevelsStartsState;

    @BeforeEach
    void setUp() {
        subject = new NetworkUtilizationManagerImpl(throttleAccumulator, congestionMultipliers);
    }

    @Test
    void verifyTrackTxn() {
        // when
        subject.trackTxn(transactionInfo, consensusNow, state);

        // then
        verify(throttleAccumulator).shouldThrottle(transactionInfo, consensusNow, state);
        verify(congestionMultipliers).updateMultiplier(consensusNow);
    }

    @Test
    void verifyTrackFeePayments() {
        // given
        final var expectedTxnToBeChargedFor = new TransactionInfo(
                Transaction.DEFAULT,
                TransactionBody.DEFAULT,
                TransactionID.DEFAULT,
                AccountID.DEFAULT,
                SignatureMap.DEFAULT,
                Bytes.EMPTY,
                CRYPTO_TRANSFER);

        // when
        subject.trackFeePayments(consensusNow, state);

        // then
        verify(throttleAccumulator).shouldThrottle(expectedTxnToBeChargedFor, consensusNow, state);
        verify(congestionMultipliers).updateMultiplier(consensusNow);
    }
}

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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.congestion.CongestionLevelStarts;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshot;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshots;
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
import com.hedera.node.app.throttle.CongestionThrottleService;
import com.hedera.node.app.throttle.ThrottleAccumulator;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
    void setup() {
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
        final var payer = AccountID.newBuilder().accountNum(1234L).build();
        final var expectedTxnToBeChargedFor = new TransactionInfo(
                Transaction.DEFAULT,
                TransactionBody.DEFAULT,
                TransactionID.DEFAULT,
                payer,
                SignatureMap.DEFAULT,
                Bytes.EMPTY,
                CRYPTO_TRANSFER);

        // when
        subject.trackFeePayments(payer, consensusNow, state);

        // then
        verify(throttleAccumulator).shouldThrottle(expectedTxnToBeChargedFor, consensusNow, state);
        verify(congestionMultipliers).updateMultiplier(consensusNow);
    }

    @Test
    @DisplayName("Reset from state should not be performed if current throttles count differs from that in state")
    void shouldNotResetIfDifferentFromState() {
        // given
        given(throttleAccumulator.allActiveThrottles()).willReturn(List.of(throttle));
        given(state.createReadableStates(CongestionThrottleService.NAME)).willReturn(readableStates);
        given(readableStates.getSingleton(CongestionThrottleService.THROTTLE_USAGE_SNAPSHOTS_STATE_KEY))
                .willReturn(readableThrottleUsageSnapshotsState);
        given(readableThrottleUsageSnapshotsState.get())
                .willReturn(ThrottleUsageSnapshots.newBuilder().build());

        // when
        subject.resetFrom(state);

        // then
        assertThat(
                logCaptor.warnLogs(),
                contains(
                        "There are 1 active throttles, but 0 usage snapshots from saved state. Not performing a reset!"));
    }

    @Test
    void shouldSuccessfullyResetThrottlesAndCongestionLevelsFromState() {
        // given
        given(throttleAccumulator.allActiveThrottles()).willReturn(List.of(throttle));
        given(throttleAccumulator.gasLimitThrottle()).willReturn(gasThrottle);

        given(state.createReadableStates(CongestionThrottleService.NAME)).willReturn(readableStates);
        given(readableStates.getSingleton(CongestionThrottleService.THROTTLE_USAGE_SNAPSHOTS_STATE_KEY))
                .willReturn(readableThrottleUsageSnapshotsState);
        given(readableThrottleUsageSnapshotsState.get())
                .willReturn(ThrottleUsageSnapshots.newBuilder()
                        .tpsThrottles(new ThrottleUsageSnapshot(
                                50L,
                                Timestamp.newBuilder().seconds(12L).nanos(34).build()))
                        .gasThrottle(new ThrottleUsageSnapshot(
                                100L,
                                Timestamp.newBuilder().seconds(56L).nanos(78).build()))
                        .build());

        given(readableStates.getSingleton(CongestionThrottleService.CONGESTION_LEVEL_STARTS_STATE_KEY))
                .willReturn(readableCongestionLevelsStartsState);
        given(readableCongestionLevelsStartsState.get())
                .willReturn(CongestionLevelStarts.newBuilder()
                        .genericLevelStarts(
                                Timestamp.newBuilder().seconds(12L).nanos(34).build())
                        .gasLevelStarts(
                                Timestamp.newBuilder().seconds(56L).nanos(78).build())
                        .build());

        // when
        subject.resetFrom(state);

        // then
        assertEquals(
                throttle.usageSnapshot(), new DeterministicThrottle.UsageSnapshot(50L, Instant.ofEpochSecond(12L, 34)));
        assertEquals(
                gasThrottle.usageSnapshot(),
                new DeterministicThrottle.UsageSnapshot(100L, Instant.ofEpochSecond(56L, 78)));

        verify(congestionMultipliers)
                .resetEntityUtilizationMultiplierStarts(new Instant[] {Instant.ofEpochSecond(12L, 34)});
        verify(congestionMultipliers).resetThrottleMultiplierStarts(new Instant[] {Instant.ofEpochSecond(56L, 78)});

        assertThat(
                logCaptor.debugLogs(),
                contains(
                        "Reset DeterministicThrottle.UsageSnapshot{used=100, last decision @ 1970-01-01T00:00:56.000000078Z} with saved gas throttle usage snapshot"));
    }

    @Test
    void shouldSuccessfullySaveThrottlesAndCongestionLevelsToState() {
        // given
        given(throttleAccumulator.allActiveThrottles()).willReturn(List.of(throttle));
        given(throttleAccumulator.gasLimitThrottle()).willReturn(gasThrottle);
        final var expectedThrottleUsageSnapshots = ThrottleUsageSnapshots.newBuilder()
                .tpsThrottles(ThrottleUsageSnapshot.newBuilder().build())
                .gasThrottle(ThrottleUsageSnapshot.newBuilder().build())
                .build();

        given(congestionMultipliers.entityUtilizationCongestionStarts())
                .willReturn(new Instant[] {Instant.ofEpochSecond(12L, 34)});
        given(congestionMultipliers.throttleMultiplierCongestionStarts())
                .willReturn(new Instant[] {Instant.ofEpochSecond(56L, 78)});
        final var expectedCongestionLevelStarts = CongestionLevelStarts.newBuilder()
                .genericLevelStarts(
                        Timestamp.newBuilder().seconds(12L).nanos(34).build())
                .gasLevelStarts(Timestamp.newBuilder().seconds(56L).nanos(78).build())
                .build();

        given(state.createWritableStates(CongestionThrottleService.NAME)).willReturn(writableStates);
        given(writableStates.getSingleton(CongestionThrottleService.THROTTLE_USAGE_SNAPSHOTS_STATE_KEY))
                .willReturn(writableThrottleUsageSnapshotsState);
        given(writableStates.getSingleton(CongestionThrottleService.CONGESTION_LEVEL_STARTS_STATE_KEY))
                .willReturn(writableCongestionLevelsStartsState);

        // when
        subject.saveTo(state);

        // then
        verify(writableThrottleUsageSnapshotsState).put(expectedThrottleUsageSnapshots);
        verify(writableCongestionLevelsStartsState).put(expectedCongestionLevelStarts);
    }
}

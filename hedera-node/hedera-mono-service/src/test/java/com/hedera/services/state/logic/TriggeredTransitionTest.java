/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.context.BasicTransactionContext.EMPTY_KEY;
import static com.hedera.services.utils.EntityNum.fromScheduleId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.charging.FeeChargingPolicy;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.fee.FeeObject;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TriggeredTransitionTest {
    private final JKey activePayerKey =
            new JEd25519Key("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes());
    private final Instant consensusNow = Instant.ofEpochSecond(1_234_567L, 890);
    private final FeeObject fee = new FeeObject(1, 2, 3);
    private static final ScheduleID scheduleId = IdUtils.asSchedule("0.0.333333");

    @Mock private SignedTxnAccessor accessor;
    @Mock private StateView currentView;
    @Mock private FeeCalculator fees;
    @Mock private FeeChargingPolicy chargingPolicy;
    @Mock private NetworkCtxManager networkCtxManager;
    @Mock private RequestedTransition requestedTransition;
    @Mock private TransactionContext txnCtx;
    @Mock private NetworkUtilization networkUtilization;
    @Mock private ScheduleStore scheduleStore;
    @Mock private SigImpactHistorian sigImpactHistorian;

    private TriggeredTransition subject;

    @BeforeEach
    void setUp() {
        subject =
                new TriggeredTransition(
                        currentView,
                        fees,
                        chargingPolicy,
                        txnCtx,
                        sigImpactHistorian,
                        networkCtxManager,
                        requestedTransition,
                        scheduleStore,
                        networkUtilization);
    }

    @Test
    void happyPathFlows() {
        given(txnCtx.accessor()).willReturn(accessor);
        given(txnCtx.consensusTime()).willReturn(consensusNow);
        given(txnCtx.activePayerKey()).willReturn(activePayerKey);
        given(fees.computeFee(accessor, activePayerKey, currentView, consensusNow)).willReturn(fee);
        given(chargingPolicy.applyForTriggered(fee)).willReturn(OK);
        given(networkUtilization.screenForAvailableCapacity()).willReturn(true);

        // when:
        subject.run();

        // then:
        verify(networkCtxManager).advanceConsensusClockTo(consensusNow);
        verify(networkUtilization).trackUserTxn(accessor, consensusNow);
        verify(requestedTransition).finishFor(accessor);
        verify(txnCtx, never()).payerSigIsKnownActive();
    }

    @Test
    void doesntRunRequestedIfCapacityMissing() {
        given(txnCtx.accessor()).willReturn(accessor);
        given(txnCtx.consensusTime()).willReturn(consensusNow);
        given(txnCtx.activePayerKey()).willReturn(activePayerKey);
        given(fees.computeFee(accessor, activePayerKey, currentView, consensusNow)).willReturn(fee);
        given(chargingPolicy.applyForTriggered(fee)).willReturn(OK);

        // when:
        subject.run();

        // then:
        verify(networkCtxManager).advanceConsensusClockTo(consensusNow);
        verify(networkUtilization).trackUserTxn(accessor, consensusNow);
        verify(requestedTransition, never()).finishFor(any());
        verify(txnCtx, never()).payerSigIsKnownActive();
    }

    @Test
    void abortsOnChargingFailure() {
        given(txnCtx.accessor()).willReturn(accessor);
        given(txnCtx.consensusTime()).willReturn(consensusNow);
        given(txnCtx.activePayerKey()).willReturn(activePayerKey);
        given(fees.computeFee(accessor, activePayerKey, currentView, consensusNow)).willReturn(fee);
        given(chargingPolicy.applyForTriggered(fee)).willReturn(INSUFFICIENT_TX_FEE);

        // when:
        subject.run();

        // then:
        verify(networkCtxManager).advanceConsensusClockTo(consensusNow);
        verify(txnCtx).setStatus(INSUFFICIENT_TX_FEE);
        verify(requestedTransition, never()).finishFor(any());
        verify(txnCtx, never()).payerSigIsKnownActive();
    }

    @Test
    void marksScheduleTxnExecuted() {
        given(txnCtx.accessor()).willReturn(accessor);
        given(txnCtx.consensusTime()).willReturn(consensusNow);
        given(txnCtx.activePayerKey()).willReturn(activePayerKey);
        given(accessor.isTriggeredTxn()).willReturn(true);
        given(fees.computeFee(accessor, EMPTY_KEY, currentView, consensusNow)).willReturn(fee);
        given(chargingPolicy.applyForTriggered(fee)).willReturn(OK);
        given(networkUtilization.screenForAvailableCapacity()).willReturn(true);
        given(accessor.getScheduleRef()).willReturn(scheduleId);
        given(scheduleStore.markAsExecuted(scheduleId, consensusNow)).willReturn(OK);

        // when:
        subject.run();

        // then:
        verify(networkCtxManager).advanceConsensusClockTo(consensusNow);
        verify(txnCtx).payerSigIsKnownActive();
        verify(networkUtilization).trackUserTxn(accessor, consensusNow);
        verify(requestedTransition).finishFor(accessor);
        verify(scheduleStore).markAsExecuted(scheduleId, consensusNow);
        verify(sigImpactHistorian).markEntityChanged(fromScheduleId(scheduleId).longValue());
    }

    @Test
    void handlesScheduleMarkExecutedFailure() {
        given(txnCtx.accessor()).willReturn(accessor);
        given(txnCtx.consensusTime()).willReturn(consensusNow);
        given(accessor.isTriggeredTxn()).willReturn(true);
        given(accessor.getScheduleRef()).willReturn(scheduleId);
        given(scheduleStore.markAsExecuted(scheduleId, consensusNow))
                .willReturn(INVALID_SCHEDULE_ID);

        // when:
        subject.run();

        // then:
        verify(networkCtxManager).advanceConsensusClockTo(consensusNow);
        verify(scheduleStore).markAsExecuted(scheduleId, consensusNow);
        verify(txnCtx).setStatus(INVALID_SCHEDULE_ID);
        verify(txnCtx, never()).payerSigIsKnownActive();
        verify(networkUtilization, never()).trackUserTxn(any(), any());
        verify(requestedTransition, never()).finishFor(any());
        verify(sigImpactHistorian, never()).markEntityChanged(anyLong());
    }

    @Test
    void handlesNullScheduleRef() {
        given(txnCtx.accessor()).willReturn(accessor);
        given(txnCtx.consensusTime()).willReturn(consensusNow);
        given(txnCtx.activePayerKey()).willReturn(activePayerKey);
        given(accessor.isTriggeredTxn()).willReturn(true);
        given(fees.computeFee(accessor, activePayerKey, currentView, consensusNow)).willReturn(fee);
        given(chargingPolicy.applyForTriggered(fee)).willReturn(OK);
        given(networkUtilization.screenForAvailableCapacity()).willReturn(true);
        given(accessor.getScheduleRef()).willReturn(null);

        // when:
        subject.run();

        // then:
        verify(networkCtxManager).advanceConsensusClockTo(consensusNow);
        verify(networkUtilization).trackUserTxn(accessor, consensusNow);
        verify(requestedTransition).finishFor(accessor);
        verify(scheduleStore, never()).markAsExecuted(any(), any());
        verify(txnCtx, never()).payerSigIsKnownActive();
        verify(sigImpactHistorian, never()).markEntityChanged(anyLong());
    }
}

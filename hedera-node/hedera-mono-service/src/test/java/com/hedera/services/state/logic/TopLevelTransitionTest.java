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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.fees.charging.TxnChargingPolicyAgent;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TopLevelTransitionTest {
    private final Instant consensusNow = Instant.ofEpochSecond(1_234_567L, 890);

    @Mock private TransactionContext txnCtx;
    @Mock private NetworkCtxManager networkCtxManager;
    @Mock private TxnChargingPolicyAgent chargingPolicyAgent;
    @Mock private PlatformTxnAccessor accessor;
    @Mock private RequestedTransition requestedTransition;
    @Mock private SigsAndPayerKeyScreen sigsAndPayerKeyScreen;
    @Mock private NonPayerKeysScreen nonPayerKeysScreen;
    @Mock private NetworkUtilization networkUtilization;

    private TopLevelTransition subject;

    @BeforeEach
    void setUp() {
        subject =
                new TopLevelTransition(
                        sigsAndPayerKeyScreen,
                        networkCtxManager,
                        requestedTransition,
                        txnCtx,
                        nonPayerKeysScreen,
                        networkUtilization,
                        chargingPolicyAgent);
    }

    @Test
    void switchesToStandinUtilizationAndAbortsWhenKeyActivationScreenFails() {
        given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);
        given(txnCtx.consensusTime()).willReturn(consensusNow);
        given(sigsAndPayerKeyScreen.applyTo(accessor)).willReturn(INVALID_SIGNATURE);

        // when:
        subject.run();

        // then:
        verify(networkUtilization).trackFeePayments(consensusNow);
        verify(requestedTransition, never()).finishFor(accessor);
    }

    @Test
    void happyPathScopedProcessFlows() {
        // setup:
        InOrder inOrder =
                Mockito.inOrder(
                        networkCtxManager,
                        sigsAndPayerKeyScreen,
                        chargingPolicyAgent,
                        networkUtilization,
                        nonPayerKeysScreen,
                        requestedTransition);

        given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);
        given(txnCtx.consensusTime()).willReturn(consensusNow);
        given(sigsAndPayerKeyScreen.applyTo(accessor)).willReturn(OK);
        given(chargingPolicyAgent.applyPolicyFor(accessor)).willReturn(true);
        given(nonPayerKeysScreen.reqKeysAreActiveGiven(OK)).willReturn(true);
        given(networkUtilization.screenForAvailableCapacity()).willReturn(true);
        // when:
        subject.run();

        // then:
        inOrder.verify(networkCtxManager).advanceConsensusClockTo(consensusNow);
        inOrder.verify(sigsAndPayerKeyScreen).applyTo(accessor);
        inOrder.verify(networkUtilization).trackUserTxn(accessor, consensusNow);
        inOrder.verify(chargingPolicyAgent).applyPolicyFor(accessor);
        inOrder.verify(nonPayerKeysScreen).reqKeysAreActiveGiven(OK);
        inOrder.verify(networkUtilization).screenForAvailableCapacity();
        inOrder.verify(requestedTransition).finishFor(accessor);
    }

    @Test
    void gasThrottledProcessFlows() {
        // setup:
        InOrder inOrder =
                Mockito.inOrder(
                        networkCtxManager,
                        sigsAndPayerKeyScreen,
                        chargingPolicyAgent,
                        nonPayerKeysScreen,
                        txnCtx,
                        networkUtilization);

        given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);
        given(txnCtx.consensusTime()).willReturn(consensusNow);
        given(sigsAndPayerKeyScreen.applyTo(accessor)).willReturn(OK);
        given(chargingPolicyAgent.applyPolicyFor(accessor)).willReturn(true);
        given(nonPayerKeysScreen.reqKeysAreActiveGiven(OK)).willReturn(true);
        // when:
        subject.run();

        // then:
        inOrder.verify(networkCtxManager).advanceConsensusClockTo(consensusNow);
        inOrder.verify(sigsAndPayerKeyScreen).applyTo(accessor);
        inOrder.verify(networkUtilization).trackUserTxn(accessor, consensusNow);
        inOrder.verify(chargingPolicyAgent).applyPolicyFor(accessor);
        inOrder.verify(nonPayerKeysScreen).reqKeysAreActiveGiven(OK);
        inOrder.verify(networkUtilization).screenForAvailableCapacity();
        verifyNoInteractions(requestedTransition);
    }

    @Test
    void abortsWhenChargingPolicyAgentFails() {
        given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);
        given(txnCtx.consensusTime()).willReturn(consensusNow);
        given(sigsAndPayerKeyScreen.applyTo(accessor)).willReturn(OK);

        // when:
        subject.run();

        // then:
        verify(requestedTransition, never()).finishFor(accessor);
    }

    @Test
    void abortsWhenKeyActivationScreenFails() {
        given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);
        given(txnCtx.consensusTime()).willReturn(consensusNow);
        given(sigsAndPayerKeyScreen.applyTo(accessor)).willReturn(OK);
        given(chargingPolicyAgent.applyPolicyFor(accessor)).willReturn(true);

        // when:
        subject.run();

        // then:
        verify(requestedTransition, never()).finishFor(accessor);
    }
}

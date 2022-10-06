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
package com.hedera.services.fees.charging;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.fee.FeeObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeeChargingPolicyTest {
    private final FeeObject fees = new FeeObject(1L, 2L, 3L);
    private final FeeObject feesForDuplicateTxn = new FeeObject(1L, 2L, 0L);

    @Mock private NarratedCharging narratedCharging;

    private FeeChargingPolicy subject;

    @BeforeEach
    void setUp() {
        subject = new FeeChargingPolicy(narratedCharging);
    }

    @Test
    void chargesNodeUpToNetworkFeeForLackOfDueDiligence() {
        // when:
        subject.applyForIgnoredDueDiligence(fees);

        // then:
        verify(narratedCharging).setFees(fees);
        verify(narratedCharging).chargeSubmittingNodeUpToNetworkFee();
    }

    @Test
    void delegatesRefund() {
        subject.refundPayerServiceFee();

        verify(narratedCharging).refundPayerServiceFee();
    }

    @Test
    void chargesNonServicePenaltyForUnableToCoverTotal() {
        given(narratedCharging.isPayerWillingToCoverNetworkFee()).willReturn(true);
        given(narratedCharging.canPayerAffordNetworkFee()).willReturn(true);
        given(narratedCharging.isPayerWillingToCoverAllFees()).willReturn(true);
        given(narratedCharging.canPayerAffordAllFees()).willReturn(false);

        // when:
        ResponseCodeEnum outcome = subject.apply(fees);

        // then:
        verify(narratedCharging).setFees(fees);
        verify(narratedCharging).chargePayerNetworkAndUpToNodeFee();
        // and:
        assertEquals(INSUFFICIENT_PAYER_BALANCE, outcome);
    }

    @Test
    void chargesNonServicePenaltyForUnwillingToCoverTotal() {
        given(narratedCharging.isPayerWillingToCoverNetworkFee()).willReturn(true);
        given(narratedCharging.canPayerAffordNetworkFee()).willReturn(true);
        given(narratedCharging.isPayerWillingToCoverAllFees()).willReturn(false);

        // when:
        ResponseCodeEnum outcome = subject.apply(fees);

        // then:
        verify(narratedCharging).setFees(fees);
        verify(narratedCharging).chargePayerNetworkAndUpToNodeFee();
        // and:
        assertEquals(INSUFFICIENT_TX_FEE, outcome);
    }

    @Test
    void chargesDiscountedFeesAsExpectedForDuplicate() {
        // setup:
        ArgumentCaptor<FeeObject> captor = ArgumentCaptor.forClass(FeeObject.class);

        givenPayerWillingAndAbleForAllFees();

        // when:
        ResponseCodeEnum outcome = subject.applyForDuplicate(fees);

        // then:
        verify(narratedCharging).setFees(captor.capture());
        // and:
        assertEquals(feesForDuplicateTxn.getNodeFee(), captor.getValue().getNodeFee());
        assertEquals(feesForDuplicateTxn.getNetworkFee(), captor.getValue().getNetworkFee());
        assertEquals(feesForDuplicateTxn.getServiceFee(), captor.getValue().getServiceFee());
        // and:
        verify(narratedCharging).chargePayerAllFees();
        // and:
        assertEquals(OK, outcome);
    }

    @Test
    void chargesFullFeesAsExpected() {
        givenPayerWillingAndAbleForAllFees();

        // when:
        ResponseCodeEnum outcome = subject.apply(fees);

        // then:
        verify(narratedCharging).setFees(fees);
        verify(narratedCharging).chargePayerAllFees();
        // and:
        assertEquals(OK, outcome);
    }

    @Test
    void requiresWillingToPayServiceWhenTriggeredTxn() {
        given(narratedCharging.isPayerWillingToCoverServiceFee()).willReturn(false);

        // when:
        ResponseCodeEnum outcome = subject.applyForTriggered(fees);

        // then:
        verify(narratedCharging).setFees(fees);
        verify(narratedCharging, never()).chargePayerServiceFee();
        // and:
        assertEquals(INSUFFICIENT_TX_FEE, outcome);
    }

    @Test
    void requiresAbleToPayServiceWhenTriggeredTxn() {
        given(narratedCharging.isPayerWillingToCoverServiceFee()).willReturn(true);
        given(narratedCharging.canPayerAffordServiceFee()).willReturn(false);

        // when:
        ResponseCodeEnum outcome = subject.applyForTriggered(fees);

        // then:
        verify(narratedCharging).setFees(fees);
        verify(narratedCharging, never()).chargePayerServiceFee();
        // and:
        assertEquals(INSUFFICIENT_PAYER_BALANCE, outcome);
    }

    @Test
    void chargesServiceFeeForTriggeredTxn() {
        given(narratedCharging.isPayerWillingToCoverServiceFee()).willReturn(true);
        given(narratedCharging.canPayerAffordServiceFee()).willReturn(true);

        // when:
        ResponseCodeEnum outcome = subject.applyForTriggered(fees);

        // then:
        verify(narratedCharging).setFees(fees);
        verify(narratedCharging).chargePayerServiceFee();
        // and:
        assertEquals(OK, outcome);
    }

    @Test
    void chargesNodePenaltyForPayerUnableToPayNetwork() {
        given(narratedCharging.isPayerWillingToCoverNetworkFee()).willReturn(true);
        given(narratedCharging.canPayerAffordNetworkFee()).willReturn(false);

        // when:
        ResponseCodeEnum outcome = subject.apply(fees);

        // then:
        verify(narratedCharging).setFees(fees);
        verify(narratedCharging).chargeSubmittingNodeUpToNetworkFee();
        // and:
        assertEquals(INSUFFICIENT_PAYER_BALANCE, outcome);
    }

    @Test
    void chargesNodePenaltyForPayerUnwillingToPayNetwork() {
        given(narratedCharging.isPayerWillingToCoverNetworkFee()).willReturn(false);

        // when:
        ResponseCodeEnum outcome = subject.apply(fees);

        // then:
        verify(narratedCharging).setFees(fees);
        verify(narratedCharging).chargeSubmittingNodeUpToNetworkFee();
        // and:
        assertEquals(INSUFFICIENT_TX_FEE, outcome);
    }

    private void givenPayerWillingAndAbleForAllFees() {
        given(narratedCharging.isPayerWillingToCoverNetworkFee()).willReturn(true);
        given(narratedCharging.canPayerAffordNetworkFee()).willReturn(true);
        given(narratedCharging.isPayerWillingToCoverAllFees()).willReturn(true);
        given(narratedCharging.canPayerAffordAllFees()).willReturn(true);
    }
}

package com.hedera.services.fees.charging;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.fee.FeeObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
class StagedTxnFeeChargingPolicyTest {
	private final FeeObject fees = new FeeObject(1L, 2L, 3L);
	private final FeeObject feesForDuplicateTxn = new FeeObject(1L, 2L, 0L);

	@Mock
	private NarratedCharging narratedCharging;

	private TxnFeeChargingPolicy subject;

	@BeforeEach
	void setUp() {
		subject = new TxnFeeChargingPolicy(narratedCharging);
	}

	@Test
	void chargesNodeUpToNetworkFeeForLackOfDueDiligence() {
		// when:
		subject.applyForIgnoredDueDiligence(null, fees);

		// then:
		verify(narratedCharging).setFees(fees);
		verify(narratedCharging).chargeSubmittingNodeUpToNetworkFee();
	}

	@Test
	void chargesNonServicePenaltyForUnableToCoverTotal() {
		given(narratedCharging.isPayerWillingToCoverNetworkFee()).willReturn(true);
		given(narratedCharging.canPayerAffordNetworkFee()).willReturn(true);
		given(narratedCharging.isPayerWillingToCoverAllFees()).willReturn(true);
		given(narratedCharging.canPayerAffordAllFees()).willReturn(false);

		// when:
		ResponseCodeEnum outcome = subject.apply(null, fees);

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
		ResponseCodeEnum outcome = subject.apply(null, fees);

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
		ResponseCodeEnum outcome = subject.applyForDuplicate(null, fees);

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
		ResponseCodeEnum outcome = subject.apply(null, fees);

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
		ResponseCodeEnum outcome = subject.applyForTriggered(null, fees);

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
		ResponseCodeEnum outcome = subject.applyForTriggered(null, fees);

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
		ResponseCodeEnum outcome = subject.applyForTriggered(null, fees);

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
		ResponseCodeEnum outcome = subject.apply(null, fees);

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
		ResponseCodeEnum outcome = subject.apply(null, fees);

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
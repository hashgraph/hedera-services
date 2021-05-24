package com.hedera.services.fees.charging;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.fee.FeeObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.fees.TxnFeeType.NETWORK;
import static com.hedera.services.fees.TxnFeeType.NODE;
import static com.hedera.services.fees.TxnFeeType.SERVICE;
import static com.hedera.services.fees.charging.ItemizableFeeCharging.NETWORK_FEE;
import static com.hedera.services.fees.charging.ItemizableFeeCharging.NETWORK_NODE_SERVICE_FEES;
import static com.hedera.services.fees.charging.ItemizableFeeCharging.NODE_FEE;
import static com.hedera.services.fees.charging.ItemizableFeeCharging.SERVICE_FEE;
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
	private StagedCharging stagedCharging;

	private TxnFeeChargingPolicy subject;

	@BeforeEach
	void setUp() {
		subject = new TxnFeeChargingPolicy(stagedCharging);
	}

	@Test
	void chargesNodeUpToNetworkFeeForLackOfDueDiligence() {
		// when:
		subject.applyForIgnoredDueDiligence(null, fees);

		// then:
		verify(stagedCharging).setFees(fees);
		verify(stagedCharging).chargeSubmittingNodeUpToNetworkFee();
	}

	@Test
	void chargesNonServicePenaltyForUnableToCoverTotal() {
		given(stagedCharging.isPayerWillingToCoverNetworkFee()).willReturn(true);
		given(stagedCharging.canPayerAffordNetworkFee()).willReturn(true);
		given(stagedCharging.isPayerWillingToCoverAllFees()).willReturn(true);
		given(stagedCharging.canPayerAffordAllFees()).willReturn(false);

		// when:
		ResponseCodeEnum outcome = subject.apply(null, fees);

		// then:
		verify(stagedCharging).setFees(fees);
		verify(stagedCharging).chargePayerNetworkAndUpToNodeFee();
		// and:
		assertEquals(INSUFFICIENT_PAYER_BALANCE, outcome);
	}

	@Test
	void chargesNonServicePenaltyForUnwillingToCoverTotal() {
		given(stagedCharging.isPayerWillingToCoverNetworkFee()).willReturn(true);
		given(stagedCharging.canPayerAffordNetworkFee()).willReturn(true);
		given(stagedCharging.isPayerWillingToCoverAllFees()).willReturn(false);

		// when:
		ResponseCodeEnum outcome = subject.apply(null, fees);

		// then:
		verify(stagedCharging).setFees(fees);
		verify(stagedCharging).chargePayerNetworkAndUpToNodeFee();
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
		verify(stagedCharging).setFees(captor.capture());
		// and:
		assertEquals(feesForDuplicateTxn.getNodeFee(), captor.getValue().getNodeFee());
		assertEquals(feesForDuplicateTxn.getNetworkFee(), captor.getValue().getNetworkFee());
		assertEquals(feesForDuplicateTxn.getServiceFee(), captor.getValue().getServiceFee());
		// and:
		verify(stagedCharging).chargePayerAllFees();
		// and:
		assertEquals(OK, outcome);
	}

	@Test
	void chargesFullFeesAsExpected() {
		givenPayerWillingAndAbleForAllFees();

		// when:
		ResponseCodeEnum outcome = subject.apply(null, fees);

		// then:
		verify(stagedCharging).setFees(fees);
		verify(stagedCharging).chargePayerAllFees();
		// and:
		assertEquals(OK, outcome);
	}

	@Test
	void requiresWillingToPayServiceWhenTriggeredTxn() {
		given(stagedCharging.isPayerWillingToCoverServiceFee()).willReturn(false);

		// when:
		ResponseCodeEnum outcome = subject.applyForTriggered(null, fees);

		// then:
		verify(stagedCharging).setFees(fees);
		verify(stagedCharging, never()).chargePayerServiceFee();
		// and:
		assertEquals(INSUFFICIENT_TX_FEE, outcome);
	}

	@Test
	void requiresAbleToPayServiceWhenTriggeredTxn() {
		given(stagedCharging.isPayerWillingToCoverServiceFee()).willReturn(true);
		given(stagedCharging.canPayerAffordServiceFee()).willReturn(false);

		// when:
		ResponseCodeEnum outcome = subject.applyForTriggered(null, fees);

		// then:
		verify(stagedCharging).setFees(fees);
		verify(stagedCharging, never()).chargePayerServiceFee();
		// and:
		assertEquals(INSUFFICIENT_PAYER_BALANCE, outcome);
	}

	@Test
	void chargesServiceFeeForTriggeredTxn() {
		given(stagedCharging.isPayerWillingToCoverServiceFee()).willReturn(true);
		given(stagedCharging.canPayerAffordServiceFee()).willReturn(true);

		// when:
		ResponseCodeEnum outcome = subject.applyForTriggered(null, fees);

		// then:
		verify(stagedCharging).setFees(fees);
		verify(stagedCharging).chargePayerServiceFee();
		// and:
		assertEquals(OK, outcome);
	}

	@Test
	void chargesNodePenaltyForPayerUnableToPayNetwork() {
		given(stagedCharging.isPayerWillingToCoverNetworkFee()).willReturn(true);
		given(stagedCharging.canPayerAffordNetworkFee()).willReturn(false);

		// when:
		ResponseCodeEnum outcome = subject.apply(null, fees);

		// then:
		verify(stagedCharging).setFees(fees);
		verify(stagedCharging).chargeSubmittingNodeUpToNetworkFee();
		// and:
		assertEquals(INSUFFICIENT_PAYER_BALANCE, outcome);
	}

	@Test
	void chargesNodePenaltyForPayerUnwillingToPayNetwork() {
		given(stagedCharging.isPayerWillingToCoverNetworkFee()).willReturn(false);

		// when:
		ResponseCodeEnum outcome = subject.apply(null, fees);

		// then:
		verify(stagedCharging).setFees(fees);
		verify(stagedCharging).chargeSubmittingNodeUpToNetworkFee();
		// and:
		assertEquals(INSUFFICIENT_TX_FEE, outcome);
	}

	private void givenPayerWillingAndAbleForAllFees() {
		given(stagedCharging.isPayerWillingToCoverNetworkFee()).willReturn(true);
		given(stagedCharging.canPayerAffordNetworkFee()).willReturn(true);
		given(stagedCharging.isPayerWillingToCoverAllFees()).willReturn(true);
		given(stagedCharging.canPayerAffordAllFees()).willReturn(true);
	}
}
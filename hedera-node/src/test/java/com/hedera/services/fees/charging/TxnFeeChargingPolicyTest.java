package com.hedera.services.fees.charging;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.FeeExemptions;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hedera.services.utils.TxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.FeeObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hedera.services.fees.TxnFeeType.NETWORK;
import static com.hedera.services.fees.TxnFeeType.NODE;
import static com.hedera.services.fees.TxnFeeType.SERVICE;
import static com.hedera.services.fees.charging.ItemizableFeeCharging.NETWORK_FEE;
import static com.hedera.services.fees.charging.ItemizableFeeCharging.NETWORK_NODE_SERVICE_FEES;
import static com.hedera.services.fees.charging.ItemizableFeeCharging.NODE_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.longThat;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

class TxnFeeChargingPolicyTest {
	private TxnFeeChargingPolicy subject = new TxnFeeChargingPolicy();
	private final long node = 1, network = 2, service = 3;
	FeeObject fee;

	ItemizableFeeCharging charging;

	@BeforeEach
	private void setup() {
		fee = new FeeObject(node, network, service);
		charging = mock(ItemizableFeeCharging.class);
	}

	@Test
	public void chargesNodePenaltyForSuspectChronology() {
		// when:
		ResponseCodeEnum outcome = subject.applyForIgnoredDueDiligence(charging, fee);

		// then:
		verify(charging).setFor(NETWORK, network);
		verify(charging).chargeSubmittingNodeUpTo(NETWORK_FEE);
		// and:
		assertEquals(OK, outcome);
	}

	@Test
	public void chargesNonServicePenaltyForUnableToCoverTotal() {
		given(charging.isPayerWillingToCover(NETWORK_FEE)).willReturn(true);
		given(charging.canPayerAfford(NETWORK_FEE)).willReturn(true);
		given(charging.isPayerWillingToCover(NETWORK_NODE_SERVICE_FEES)).willReturn(true);
		given(charging.canPayerAfford(NETWORK_NODE_SERVICE_FEES)).willReturn(false);

		// when:
		ResponseCodeEnum outcome = subject.apply(charging, fee);

		// then:
		verify(charging).setFor(NODE, node);
		verify(charging).setFor(NETWORK, network);
		verify(charging).setFor(SERVICE, service);
		// and:
		verify(charging).isPayerWillingToCover(NETWORK_NODE_SERVICE_FEES);
		verify(charging).canPayerAfford(NETWORK_NODE_SERVICE_FEES);
		verify(charging).chargePayer(NETWORK_FEE);
		verify(charging).chargePayerUpTo(NODE_FEE);
		// and:
		assertEquals(INSUFFICIENT_PAYER_BALANCE, outcome);
	}

	@Test
	public void liveFireDiscountWorksForTriggered() {
		// setup:
		TransactionBody txn = mock(TransactionBody.class);
		AccountID submittingNode = IdUtils.asAccount("0.0.3");
		AccountID payer = IdUtils.asAccount("0.0.1001");
		AccountID funding = IdUtils.asAccount("0.0.98");
		HederaLedger ledger = mock(HederaLedger.class);
		GlobalDynamicProperties properties = mock(GlobalDynamicProperties.class);
		SignedTxnAccessor accessor = mock(SignedTxnAccessor.class);
		charging = new ItemizableFeeCharging(ledger, new NoExemptions(), properties);

		given(ledger.getBalance(any())).willReturn(Long.MAX_VALUE);
		given(properties.fundingAccount()).willReturn(funding);
		given(txn.getTransactionFee()).willReturn(10L);
		given(accessor.getTxn()).willReturn(txn);

		given(accessor.getPayer()).willReturn(payer);

		// when:
		charging.resetFor(accessor, submittingNode);
		ResponseCodeEnum outcome = subject.applyForTriggered(charging, fee);

		// then:
		verify(ledger).doTransfer(payer, funding, network);
		verify(ledger).doTransfer(payer, funding, service);
		verify(ledger, never()).doTransfer(
				argThat(payer::equals),
				argThat(submittingNode::equals),
				longThat(l -> l == node));
		// and:
		assertEquals(OK, outcome);
	}

	@Test
	public void liveFireDiscountWorksForDuplicate() {
		// setup:
		TransactionBody txn = mock(TransactionBody.class);
		AccountID submittingNode = IdUtils.asAccount("0.0.3");
		AccountID payer = IdUtils.asAccount("0.0.1001");
		AccountID funding = IdUtils.asAccount("0.0.98");
		HederaLedger ledger = mock(HederaLedger.class);
		GlobalDynamicProperties properties = mock(GlobalDynamicProperties.class);
		SignedTxnAccessor accessor = mock(SignedTxnAccessor.class);
		charging = new ItemizableFeeCharging(ledger, new NoExemptions(), properties);

		given(ledger.getBalance(any())).willReturn(Long.MAX_VALUE);
		given(properties.fundingAccount()).willReturn(funding);
		given(txn.getNodeAccountID()).willReturn(submittingNode);
		given(txn.getTransactionFee()).willReturn(10L);
		given(accessor.getTxn()).willReturn(txn);

		given(accessor.getPayer()).willReturn(payer);

		// when:
		charging.resetFor(accessor, submittingNode);
		ResponseCodeEnum outcome = subject.applyForDuplicate(charging, fee);

		// then:
		verify(ledger).doTransfer(payer, funding, network);
		verify(ledger).doTransfer(payer, submittingNode, node);
		verify(ledger, never()).doTransfer(
				argThat(payer::equals),
				argThat(funding::equals),
				longThat(l -> l == service));
		// and:
		assertEquals(OK, outcome);
	}

	private static class NoExemptions implements FeeExemptions {
		@Override
		public boolean hasExemptPayer(TxnAccessor accessor) {
			return false;
		}
	}

	@Test
	public void chargesDiscountedFeesAsExpectedForDuplicate() {
		given(charging.isPayerWillingToCover(NETWORK_FEE)).willReturn(true);
		given(charging.canPayerAfford(NETWORK_FEE)).willReturn(true);
		given(charging.isPayerWillingToCover(NETWORK_NODE_SERVICE_FEES)).willReturn(true);
		given(charging.canPayerAfford(NETWORK_NODE_SERVICE_FEES)).willReturn(true);

		// when:
		ResponseCodeEnum outcome = subject.applyForDuplicate(charging, fee);

		// then:
		verify(charging).setFor(NODE, node);
		verify(charging).setFor(NETWORK, network);
		verify(charging).setFor(SERVICE, service);
		verify(charging).setFor(SERVICE, 0);
		// and:
		verify(charging).isPayerWillingToCover(NETWORK_NODE_SERVICE_FEES);
		verify(charging).canPayerAfford(NETWORK_NODE_SERVICE_FEES);
		verify(charging).chargePayer(NETWORK_NODE_SERVICE_FEES);
		// and:
		assertEquals(OK, outcome);
	}

	@Test
	public void chargesFullFeesAsExpected() {
		given(charging.isPayerWillingToCover(NETWORK_FEE)).willReturn(true);
		given(charging.canPayerAfford(NETWORK_FEE)).willReturn(true);
		given(charging.isPayerWillingToCover(NETWORK_NODE_SERVICE_FEES)).willReturn(true);
		given(charging.canPayerAfford(NETWORK_NODE_SERVICE_FEES)).willReturn(true);

		// when:
		ResponseCodeEnum outcome = subject.apply(charging, fee);

		// then:
		verify(charging).setFor(NODE, node);
		verify(charging).setFor(NETWORK, network);
		verify(charging).setFor(SERVICE, service);
		// and:
		verify(charging).isPayerWillingToCover(NETWORK_NODE_SERVICE_FEES);
		verify(charging).canPayerAfford(NETWORK_NODE_SERVICE_FEES);
		verify(charging).chargePayer(NETWORK_NODE_SERVICE_FEES);
		// and:
		assertEquals(OK, outcome);
	}

	@Test
	public void chargesNonServicePenaltyForUnwillingToCoverTotal() {
		given(charging.isPayerWillingToCover(NETWORK_FEE)).willReturn(true);
		given(charging.canPayerAfford(NETWORK_FEE)).willReturn(true);
		given(charging.isPayerWillingToCover(NETWORK_NODE_SERVICE_FEES)).willReturn(false);

		// when:
		ResponseCodeEnum outcome = subject.apply(charging, fee);

		// then:
		verify(charging).setFor(NODE, node);
		verify(charging).setFor(NETWORK, network);
		verify(charging).setFor(SERVICE, service);
		// and:
		verify(charging).isPayerWillingToCover(NETWORK_NODE_SERVICE_FEES);
		verify(charging).chargePayer(NETWORK_FEE);
		verify(charging).chargePayerUpTo(NODE_FEE);
		// and:
		assertEquals(INSUFFICIENT_TX_FEE, outcome);
	}

	@Test
	public void doesntChargeNodePenaltyForPayerUnableToPayNetworkWhenTriggeredTxn() {
		given(charging.isPayerWillingToCover(NETWORK_FEE)).willReturn(true);
		given(charging.canPayerAfford(NETWORK_FEE)).willReturn(false);

		// when:
		ResponseCodeEnum outcome = subject.applyForTriggered(charging, fee);

		// then:
		verify(charging).setFor(NODE, node);
		verify(charging).setFor(NETWORK, network);
		verify(charging).setFor(SERVICE, service);
		// and:
		verify(charging).isPayerWillingToCover(NETWORK_FEE);
		verify(charging).canPayerAfford(NETWORK_FEE);
		verify(charging, never()).chargeSubmittingNodeUpTo(NETWORK_FEE);
		// and:
		assertEquals(INSUFFICIENT_PAYER_BALANCE, outcome);
	}

	@Test
	public void chargesNodePenaltyForPayerUnableToPayNetwork() {
		given(charging.isPayerWillingToCover(NETWORK_FEE)).willReturn(true);
		given(charging.canPayerAfford(NETWORK_FEE)).willReturn(false);

		// when:
		ResponseCodeEnum outcome = subject.apply(charging, fee);

		// then:
		verify(charging).setFor(NODE, node);
		verify(charging).setFor(NETWORK, network);
		verify(charging).setFor(SERVICE, service);
		// and:
		verify(charging).isPayerWillingToCover(NETWORK_FEE);
		verify(charging).canPayerAfford(NETWORK_FEE);
		verify(charging).chargeSubmittingNodeUpTo(NETWORK_FEE);
		// and:
		assertEquals(INSUFFICIENT_PAYER_BALANCE, outcome);
	}

	@Test
	public void doesntChargeNodePenaltyForPayerUnwillingToPayNetworkWhenTriggeredTxn() {
		given(charging.isPayerWillingToCover(NETWORK_FEE)).willReturn(false);

		// when:
		ResponseCodeEnum outcome = subject.applyForTriggered(charging, fee);

		// then:
		verify(charging).setFor(NODE, node);
		verify(charging).setFor(NETWORK, network);
		verify(charging).setFor(SERVICE, service);
		// and:
		verify(charging).isPayerWillingToCover(NETWORK_FEE);
		verify(charging, never()).chargeSubmittingNodeUpTo(NETWORK_FEE);
		// and:
		assertEquals(INSUFFICIENT_TX_FEE, outcome);
	}

	@Test
	public void chargesNodePenaltyForPayerUnwillingToPayNetwork() {
		given(charging.isPayerWillingToCover(NETWORK_FEE)).willReturn(false);

		// when:
		ResponseCodeEnum outcome = subject.apply(charging, fee);

		// then:
		verify(charging).setFor(NODE, node);
		verify(charging).setFor(NETWORK, network);
		verify(charging).setFor(SERVICE, service);
		// and:
		verify(charging).isPayerWillingToCover(NETWORK_FEE);
		verify(charging).chargeSubmittingNodeUpTo(NETWORK_FEE);
		// and:
		assertEquals(INSUFFICIENT_TX_FEE, outcome);
	}
}

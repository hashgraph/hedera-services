package com.hedera.services.fees.charging;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.fees.FeeExemptions;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.FeeObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hedera.services.fees.charging.ItemizableFeeCharging.NETWORK_NODE_SERVICE_FEES;
import static com.hedera.services.fees.charging.ItemizableFeeCharging.NODE_FEE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static com.hedera.services.fees.charging.ItemizableFeeCharging.NETWORK_FEE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;
import static com.hedera.services.fees.TxnFeeType.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

@RunWith(JUnitPlatform.class)
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
		verify(charging).chargeNodeUpTo(NETWORK_FEE);
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
	public void liveFireDiscountWorks() {
		// setup:
		TransactionBody txn = mock(TransactionBody.class);
		AccountID submittingNode = IdUtils.asAccount("0.0.3");
		AccountID payer = IdUtils.asAccount("0.0.1001");
		AccountID funding = IdUtils.asAccount("0.0.98");
		HederaLedger ledger = mock(HederaLedger.class);
		PropertySource properties = mock(PropertySource.class);
		SignedTxnAccessor accessor = mock(SignedTxnAccessor.class);
		charging = new ItemizableFeeCharging(new NoExemptions(), properties);

		given(ledger.getBalance(any())).willReturn(Long.MAX_VALUE);
		given(properties.getAccountProperty("ledger.funding.account")).willReturn(funding);
		given(txn.getNodeAccountID()).willReturn(submittingNode);
		given(txn.getTransactionFee()).willReturn(10L);
		given(accessor.getTxn()).willReturn(txn);

		given(accessor.getPayer()).willReturn(payer);
		charging.setLedger(ledger);

		// when:
		charging.resetFor(accessor);
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
		public boolean isExemptFromFees(TransactionBody txn) {
			return false;
		}

		@Override
		public boolean isExemptFromRecordFees(AccountID id) {
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
		verify(charging).chargeNodeUpTo(NETWORK_FEE);
		// and:
		assertEquals(INSUFFICIENT_PAYER_BALANCE, outcome);
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
		verify(charging).chargeNodeUpTo(NETWORK_FEE);
		// and:
		assertEquals(INSUFFICIENT_TX_FEE, outcome);
	}
}

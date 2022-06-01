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

import com.hedera.services.config.AccountNumbers;
import com.hedera.services.context.NodeInfo;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.FeeExemptions;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.fee.FeeObject;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NarratedLedgerChargingTest {
	private final int stakingRewardPercent = 10;
	private final int nodeRewardPercent = 20;
	private final long stakingRewardAccount = 800L;
	private final long nodeRewardAccount = 801L;
	private final long submittingNodeId = 0L;
	private final long nodeFee = 2L, networkFee = 4L, serviceFee = 6L;
	private final FeeObject fees = new FeeObject(nodeFee, networkFee, serviceFee);
	private final AccountID grpcNodeId = IdUtils.asAccount("0.0.3");
	private final AccountID grpcPayerId = IdUtils.asAccount("0.0.1234");
	private final AccountID grpcFundingId = IdUtils.asAccount("0.0.98");
	private final AccountID grpcStakeFundingId = IdUtils.asAccount("0.0." + stakingRewardAccount);
	private final AccountID grpcNodeFundingId = IdUtils.asAccount("0.0." + nodeRewardAccount);
	private final EntityNum nodeId = EntityNum.fromLong(3L);
	private final EntityNum payerId = EntityNum.fromLong(1_234L);

	@Mock
	private NodeInfo nodeInfo;
	@Mock
	private TxnAccessor accessor;
	@Mock
	private HederaLedger ledger;
	@Mock
	private FeeExemptions feeExemptions;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private MerkleMap<EntityNum, MerkleAccount> accounts;
	@Mock
	private AccountNumbers accountNumbers;

	private NarratedLedgerCharging subject;

	@BeforeEach
	void setUp() {
		given(accountNumbers.stakingRewardAccount()).willReturn(stakingRewardAccount);
		given(accountNumbers.nodeRewardAccount()).willReturn(nodeRewardAccount);
		subject = new NarratedLedgerCharging(nodeInfo, feeExemptions, dynamicProperties, () -> accounts, accountNumbers);
		subject.setLedger(ledger);
	}

	@Test
	void chargesNoFeesToExemptPayer() {
		given(feeExemptions.hasExemptPayer(accessor)).willReturn(true);
		given(accessor.getPayer()).willReturn(grpcPayerId);
		subject.resetForTxn(accessor, submittingNodeId);

		// when:
		subject.chargePayerAllFees();
		subject.chargePayerServiceFee();
		subject.chargePayerNetworkAndUpToNodeFee();

		// then:
		verifyNoInteractions(ledger);
	}

	@Test
	void chargesAllFeesToPayerAsExpected() {
		givenSetupToChargePayer(nodeFee + networkFee + serviceFee, nodeFee + networkFee + serviceFee);
		given(dynamicProperties.getStakingRewardPercent()).willReturn(stakingRewardPercent);
		given(dynamicProperties.getNodeRewardPercent()).willReturn(nodeRewardPercent);

		// expect:
		assertTrue(subject.canPayerAffordAllFees());
		assertTrue(subject.isPayerWillingToCoverAllFees());

		// when:
		subject.chargePayerAllFees();

		// then:
		final var fundingTotalFee = networkFee + serviceFee;
		final var expectedNodeRewardFee = fundingTotalFee * nodeRewardPercent / 100;
		final var expectedStakingRewardFee = fundingTotalFee * stakingRewardPercent / 100;
		final var fundingAccountFee = fundingTotalFee - (expectedNodeRewardFee + expectedStakingRewardFee);

		assertEquals(fundingTotalFee, fundingAccountFee + expectedNodeRewardFee + expectedStakingRewardFee);

		verify(ledger).adjustBalance(grpcPayerId, -(nodeFee + networkFee + serviceFee));
		verify(ledger).adjustBalance(grpcNodeId, +nodeFee);
		verify(ledger).adjustBalance(grpcFundingId, +fundingAccountFee);
		verify(ledger).adjustBalance(grpcStakeFundingId, +expectedStakingRewardFee);
		verify(ledger).adjustBalance(grpcNodeFundingId, +expectedNodeRewardFee);
		assertEquals(nodeFee + networkFee + serviceFee, subject.totalFeesChargedToPayer());
	}

	@Test
	void chargesServiceFeeToPayerAsExpected() {
		givenSetupToChargePayer(serviceFee, serviceFee);
		given(dynamicProperties.getStakingRewardPercent()).willReturn(stakingRewardPercent);
		given(dynamicProperties.getNodeRewardPercent()).willReturn(nodeRewardPercent);

		// expect:
		assertTrue(subject.canPayerAffordServiceFee());
		assertTrue(subject.isPayerWillingToCoverServiceFee());

		// when:
		subject.chargePayerServiceFee();

		// then:
		final var totalFee = serviceFee;
		final var expectedNodeRewardFee = totalFee * nodeRewardPercent / 100;
		final var expectedStakingRewardFee = totalFee * stakingRewardPercent / 100;
		final var fundingAccountFee = totalFee - (expectedNodeRewardFee + expectedStakingRewardFee);

		assertEquals(totalFee, fundingAccountFee + expectedNodeRewardFee + expectedStakingRewardFee);

		verify(ledger).adjustBalance(grpcPayerId, -serviceFee);
		verify(ledger).adjustBalance(grpcFundingId, +fundingAccountFee);
		verify(ledger).adjustBalance(grpcStakeFundingId, +expectedStakingRewardFee);
		verify(ledger).adjustBalance(grpcNodeFundingId, +expectedNodeRewardFee);
		assertEquals(serviceFee, subject.totalFeesChargedToPayer());
	}

	@Test
	void refundsServiceFeeToPayerAsExpected() {
		final var inOrder = Mockito.inOrder(ledger);
		given(dynamicProperties.getStakingRewardPercent()).willReturn(stakingRewardPercent);
		given(dynamicProperties.getNodeRewardPercent()).willReturn(nodeRewardPercent);

		final var allFees = nodeFee + networkFee + serviceFee;
		givenSetupToChargePayer(allFees, allFees);

		assertTrue(subject.canPayerAffordAllFees());
		subject.chargePayerAllFees();
		subject.refundPayerServiceFee();

		final var totalFundingFee = networkFee + serviceFee;
		final var expectedNodeRewardFee = totalFundingFee * nodeRewardPercent / 100;
		final var expectedStakingRewardFee = totalFundingFee * stakingRewardPercent / 100;
		final var fundingAccountFee = totalFundingFee - (expectedNodeRewardFee + expectedStakingRewardFee);

		assertEquals(totalFundingFee, fundingAccountFee + expectedNodeRewardFee + expectedStakingRewardFee);

		inOrder.verify(ledger).adjustBalance(grpcNodeId, +nodeFee);
		inOrder.verify(ledger).adjustBalance(grpcFundingId, +fundingAccountFee);
		inOrder.verify(ledger).adjustBalance(grpcStakeFundingId, +expectedStakingRewardFee);
		inOrder.verify(ledger).adjustBalance(grpcNodeFundingId, +expectedNodeRewardFee);
		inOrder.verify(ledger).adjustBalance(grpcPayerId, -(allFees));

		final var refundFee = serviceFee;
		final var refundNodeRewardFee = refundFee * nodeRewardPercent / 100;
		final var refundStakingRewardFee = refundFee * stakingRewardPercent / 100;
		final var refundFundingAccountFee = refundFee - (refundNodeRewardFee + refundStakingRewardFee);
		assertEquals(refundFee, refundFundingAccountFee + refundNodeRewardFee + refundStakingRewardFee);

		inOrder.verify(ledger).adjustBalance(grpcFundingId, -refundFundingAccountFee);
		inOrder.verify(ledger).adjustBalance(grpcStakeFundingId, -refundStakingRewardFee);
		inOrder.verify(ledger).adjustBalance(grpcNodeFundingId, -refundNodeRewardFee);
		inOrder.verify(ledger).adjustBalance(grpcPayerId, +serviceFee);

		assertEquals(serviceFee, subject.totalFeesChargedToPayer());
	}

	@Test
	void refundingToExemptPayerIsNoop() {
		given(accessor.getPayer()).willReturn(grpcPayerId);
		given(feeExemptions.hasExemptPayer(accessor)).willReturn(true);

		subject.resetForTxn(accessor, submittingNodeId);

		subject.refundPayerServiceFee();

		verifyNoInteractions(ledger);
	}

	@Test
	void failsFastIfTryingToRefundToUnchargedPayer() {
		assertThrows(IllegalStateException.class, subject::refundPayerServiceFee);
	}

	@Test
	void chargesNetworkAndUpToNodeFeeToPayerAsExpected() {
		givenSetupToChargePayer(networkFee + nodeFee / 2, nodeFee + networkFee + serviceFee);

		// when:
		subject.chargePayerNetworkAndUpToNodeFee();

		// then:
		final var expectedNodeRewardFee = (networkFee) * nodeRewardPercent / 100;
		final var expectedStakingRewardFee = (networkFee) * stakingRewardPercent / 100;
		final var fundingAccountFee = +networkFee - (expectedNodeRewardFee + expectedStakingRewardFee);

		verify(ledger).adjustBalance(grpcPayerId, -(networkFee + nodeFee / 2));
		verify(ledger).adjustBalance(grpcFundingId, +fundingAccountFee);
		verify(ledger).adjustBalance(grpcStakeFundingId, +expectedStakingRewardFee);
		verify(ledger).adjustBalance(grpcNodeFundingId, +expectedNodeRewardFee);
		verify(ledger).adjustBalance(grpcNodeId, nodeFee / 2);
		assertEquals(networkFee + nodeFee / 2, subject.totalFeesChargedToPayer());
	}

	@Test
	void chargesNodeUpToNetworkFeeAsExpected() {
		givenSetupToChargeNode(networkFee - 1);

		// when:
		subject.chargeSubmittingNodeUpToNetworkFee();

		// then:
		final var expectedNodeRewardFee = (networkFee) * nodeRewardPercent / 100;
		final var expectedStakingRewardFee = (networkFee) * stakingRewardPercent / 100;
		final var fundingAccountFee = +networkFee - 1 - (expectedNodeRewardFee + expectedStakingRewardFee);

		verify(ledger).adjustBalance(grpcNodeId, -networkFee + 1);
		verify(ledger).adjustBalance(grpcFundingId, +fundingAccountFee);
		verify(ledger).adjustBalance(grpcStakeFundingId, +expectedStakingRewardFee);
		verify(ledger).adjustBalance(grpcNodeFundingId, +expectedNodeRewardFee);

		assertEquals(0, subject.totalFeesChargedToPayer());
	}

	@Test
	void throwsIseIfPayerNotActuallyExtant() {
		// expect:
		assertThrows(IllegalStateException.class, subject::canPayerAffordAllFees);
		assertThrows(IllegalStateException.class, subject::canPayerAffordNetworkFee);

		given(accessor.getPayer()).willReturn(grpcPayerId);
		// and given:
		subject.resetForTxn(accessor, submittingNodeId);
		subject.setFees(fees);

		// still expect:
		assertThrows(IllegalStateException.class, subject::canPayerAffordAllFees);
		assertThrows(IllegalStateException.class, subject::canPayerAffordNetworkFee);
	}

	@Test
	void detectsLackOfWillingness() {
		given(accessor.getPayer()).willReturn(grpcPayerId);

		subject.resetForTxn(accessor, submittingNodeId);
		subject.setFees(fees);

		// expect:
		assertFalse(subject.isPayerWillingToCoverAllFees());
		assertFalse(subject.isPayerWillingToCoverNetworkFee());
		assertFalse(subject.isPayerWillingToCoverServiceFee());
	}

	@Test
	void exemptPayerNeedsNoAbility() {
		given(accessor.getPayer()).willReturn(grpcPayerId);
		given(feeExemptions.hasExemptPayer(accessor)).willReturn(true);

		subject.resetForTxn(accessor, submittingNodeId);
		subject.setFees(fees);

		// expect:
		assertTrue(subject.canPayerAffordAllFees());
		assertTrue(subject.canPayerAffordServiceFee());
		assertTrue(subject.canPayerAffordNetworkFee());
	}

	@Test
	void exemptPayerNeedsNoWillingness() {
		given(accessor.getPayer()).willReturn(grpcPayerId);
		given(feeExemptions.hasExemptPayer(accessor)).willReturn(true);

		subject.resetForTxn(accessor, submittingNodeId);
		subject.setFees(fees);

		// expect:
		assertTrue(subject.isPayerWillingToCoverAllFees());
		assertTrue(subject.isPayerWillingToCoverNetworkFee());
		assertTrue(subject.isPayerWillingToCoverServiceFee());
	}

	private void givenSetupToChargePayer(final long payerBalance, final long totalOfferedFee) {
		final var payerAccount = MerkleAccountFactory.newAccount().balance(payerBalance).get();
		given(accounts.get(payerId)).willReturn(payerAccount);

		given(dynamicProperties.fundingAccount()).willReturn(grpcFundingId);
		given(nodeInfo.accountOf(submittingNodeId)).willReturn(grpcNodeId);
		given(nodeInfo.accountKeyOf(submittingNodeId)).willReturn(nodeId);

		given(accessor.getPayer()).willReturn(grpcPayerId);
		given(accessor.getOfferedFee()).willReturn(totalOfferedFee);
		subject.resetForTxn(accessor, submittingNodeId);
		subject.setFees(fees);
	}

	private void givenSetupToChargeNode(final long nodeBalance) {
		final var nodeAccount = MerkleAccountFactory.newAccount().balance(nodeBalance).get();
		given(accounts.get(nodeId)).willReturn(nodeAccount);

		given(dynamicProperties.fundingAccount()).willReturn(grpcFundingId);
		given(nodeInfo.accountOf(submittingNodeId)).willReturn(nodeId.toGrpcAccountId());
		given(nodeInfo.accountKeyOf(submittingNodeId)).willReturn(nodeId);

		given(accessor.getPayer()).willReturn(grpcPayerId);
		subject.resetForTxn(accessor, submittingNodeId);
		subject.setFees(fees);
	}
}

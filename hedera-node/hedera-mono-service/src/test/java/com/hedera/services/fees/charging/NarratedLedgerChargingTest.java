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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.services.context.NodeInfo;
import com.hedera.services.fees.FeeExemptions;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
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

@ExtendWith(MockitoExtension.class)
class NarratedLedgerChargingTest {
    private final long submittingNodeId = 0L;
    private final long nodeFee = 200L, networkFee = 400L, serviceFee = 600L;
    private final FeeObject fees = new FeeObject(nodeFee, networkFee, serviceFee);
    private final AccountID grpcNodeId = IdUtils.asAccount("0.0.3");
    private final AccountID grpcPayerId = IdUtils.asAccount("0.0.1234");
    private final EntityNum nodeId = EntityNum.fromLong(3L);
    private final EntityNum payerId = EntityNum.fromLong(1_234L);

    @Mock private NodeInfo nodeInfo;
    @Mock private TxnAccessor accessor;
    @Mock private HederaLedger ledger;
    @Mock private FeeExemptions feeExemptions;
    @Mock private MerkleMap<EntityNum, MerkleAccount> accounts;
    @Mock private FeeDistribution feeDistribution;
    @Mock private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;

    private NarratedLedgerCharging subject;

    @BeforeEach
    void setUp() {
        subject =
                new NarratedLedgerCharging(
                        nodeInfo, feeDistribution, feeExemptions, () -> accounts);
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
    void chargesExpectedFeesWithStakingRecipientsWhenEnabled() {
        givenSetupToChargePayer(
                nodeFee + networkFee + serviceFee, nodeFee + networkFee + serviceFee);

        // expect:
        assertTrue(subject.canPayerAffordAllFees());
        assertTrue(subject.isPayerWillingToCoverAllFees());

        // when:
        subject.chargePayerAllFees();

        verify(ledger).adjustBalance(grpcPayerId, -(nodeFee + networkFee + serviceFee));
        verify(ledger).adjustBalance(grpcNodeId, +nodeFee);
        verify(feeDistribution).distributeChargedFee(networkFee + serviceFee, accountsLedger);
        assertEquals(nodeFee + networkFee + serviceFee, subject.totalFeesChargedToPayer());
    }

    @Test
    void chargesExpectedFeesWithNoStakingRecipientsWhenNotEnabled() {
        givenSetupToChargePayer(
                nodeFee + networkFee + serviceFee, nodeFee + networkFee + serviceFee);

        // expect:
        assertTrue(subject.canPayerAffordAllFees());
        assertTrue(subject.isPayerWillingToCoverAllFees());

        // when:
        subject.chargePayerAllFees();

        // then:
        verify(ledger).adjustBalance(grpcPayerId, -(nodeFee + networkFee + serviceFee));
        verify(ledger).adjustBalance(grpcNodeId, +nodeFee);
        verify(feeDistribution).distributeChargedFee(networkFee + serviceFee, accountsLedger);
        assertEquals(nodeFee + networkFee + serviceFee, subject.totalFeesChargedToPayer());
    }

    @Test
    void chargesServiceFeeToPayerAsExpected() {
        givenSetupToChargePayer(serviceFee, serviceFee);

        // expect:
        assertTrue(subject.canPayerAffordServiceFee());
        assertTrue(subject.isPayerWillingToCoverServiceFee());

        // when:
        subject.chargePayerServiceFee();

        verify(ledger).adjustBalance(grpcPayerId, -serviceFee);
        verify(feeDistribution).distributeChargedFee(serviceFee, accountsLedger);
        assertEquals(serviceFee, subject.totalFeesChargedToPayer());
    }

    @Test
    void refundsServiceFeeToPayerAsExpected() {
        final var inOrder = Mockito.inOrder(ledger, feeDistribution);

        final var allFees = nodeFee + networkFee + serviceFee;
        givenSetupToChargePayer(allFees, allFees);

        assertTrue(subject.canPayerAffordAllFees());
        subject.chargePayerAllFees();
        subject.refundPayerServiceFee();

        inOrder.verify(ledger).adjustBalance(grpcNodeId, +nodeFee);
        verify(feeDistribution).distributeChargedFee(networkFee + serviceFee, accountsLedger);
        inOrder.verify(ledger).adjustBalance(grpcPayerId, -(allFees));

        inOrder.verify(feeDistribution).distributeChargedFee(-networkFee - nodeFee, accountsLedger);
        inOrder.verify(ledger).adjustBalance(grpcPayerId, +serviceFee);

        assertEquals(networkFee + nodeFee, subject.totalFeesChargedToPayer());
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

        verify(ledger).adjustBalance(grpcPayerId, -(networkFee + nodeFee / 2));
        verify(ledger).adjustBalance(grpcNodeId, nodeFee / 2);
        verify(feeDistribution).distributeChargedFee(networkFee, accountsLedger);
        assertEquals(networkFee + nodeFee / 2, subject.totalFeesChargedToPayer());
    }

    @Test
    void chargesNodeUpToNetworkFeeAsExpected() {
        givenSetupToChargeNode(networkFee - 1);

        // when:
        subject.chargeSubmittingNodeUpToNetworkFee();

        verify(ledger).adjustBalance(grpcNodeId, -networkFee + 1);
        verify(feeDistribution).distributeChargedFee(networkFee - 1, accountsLedger);

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
        given(ledger.getAccountsLedger()).willReturn(accountsLedger);

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
        given(ledger.getAccountsLedger()).willReturn(accountsLedger);

        given(nodeInfo.accountOf(submittingNodeId)).willReturn(nodeId.toGrpcAccountId());
        given(nodeInfo.accountKeyOf(submittingNodeId)).willReturn(nodeId);

        given(accessor.getPayer()).willReturn(grpcPayerId);
        subject.resetForTxn(accessor, submittingNodeId);
        subject.setFees(fees);
    }
}

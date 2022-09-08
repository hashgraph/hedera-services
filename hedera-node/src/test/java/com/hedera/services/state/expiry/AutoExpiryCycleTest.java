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
package com.hedera.services.state.expiry;

import static com.hedera.services.state.expiry.EntityProcessResult.DONE;
import static com.hedera.services.state.expiry.EntityProcessResult.NOTHING_TO_DO;
import static com.hedera.services.state.expiry.EntityProcessResult.STILL_MORE_TO_DO;
import static com.hedera.services.state.expiry.classification.ClassificationResult.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.calculation.RenewAssessment;
import com.hedera.services.fees.charging.FeeDistribution;
import com.hedera.services.fees.charging.NonHapiFeeCharging;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.expiry.classification.ClassificationWork;
import com.hedera.services.state.expiry.removal.*;
import com.hedera.services.state.expiry.renewal.RenewalHelper;
import com.hedera.services.state.expiry.renewal.RenewalWork;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.throttling.ExpiryThrottle;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AutoExpiryCycleTest {
    private final Instant now = Instant.ofEpochSecond(1_234_567L);
    private final long requestedRenewalPeriod = 3601L;
    private final long nonZeroBalance = 2L;
    private final long fee = 1L;
    private final long actualRenewalPeriod = 3600L;
    private final long fundingAccountNum = 98L;
    private final long nonExpiredAccountNum = 1002L;
    private final long fundedExpiredContractNum = 1004L;
    private final MerkleAccount mockAccount =
            MerkleAccountFactory.newAccount()
                    .autoRenewPeriod(requestedRenewalPeriod)
                    .balance(nonZeroBalance)
                    .expirationTime(now.getEpochSecond() - 1)
                    .get();
    private final MerkleAccount mockContract =
            MerkleAccountFactory.newContract()
                    .autoRenewPeriod(requestedRenewalPeriod)
                    .balance(nonZeroBalance)
                    .expirationTime(now.getEpochSecond() - 1)
                    .get();
    private final MerkleAccount fundingAccount =
            MerkleAccountFactory.newAccount()
                    .number(EntityNum.fromLong(fundingAccountNum))
                    .balance(nonZeroBalance)
                    .expirationTime(now.getEpochSecond() + 100L)
                    .get();

    private final CryptoGcOutcome finishedReturns =
            new CryptoGcOutcome(
                    FungibleTreasuryReturns.FINISHED_NOOP_FUNGIBLE_RETURNS,
                    NonFungibleTreasuryReturns.FINISHED_NOOP_NON_FUNGIBLE_RETURNS,
                    true);

    private final CryptoGcOutcome partiallyFinishedReturns =
            new CryptoGcOutcome(
                    FungibleTreasuryReturns.UNFINISHED_NOOP_FUNGIBLE_RETURNS,
                    new NonFungibleTreasuryReturns(
                            List.of(EntityId.fromIdentityCode(1234)), List.of(), false),
                    false);

    @Mock private FeeCalculator fees;
    @Mock private ClassificationWork classifier;
    @Mock private AccountGC accountGC;
    @Mock private ContractGC contractGC;
    @Mock private ExpiryRecordsHelper recordsHelper;
    @Mock private MerkleMap<EntityNum, MerkleAccount> accounts;
    @Mock private ExpiryThrottle expiryThrottle;
    @Mock private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
    @Mock private FeeDistribution feeDistribution;
    @Mock private SideEffectsTracker sideEffectsTracker;
    private MockGlobalDynamicProps dynamicProperties = new MockGlobalDynamicProps();
    private RenewalWork renewalWork;
    private RemovalWork removalWork;
    private NonHapiFeeCharging nonHapiFeeCharging;
    private AutoExpiryCycle subject;

    @BeforeEach
    void setUp() {
        setUpPreRequisites();
        subject = new AutoExpiryCycle(classifier, renewalWork, removalWork);
    }

    private void setUpPreRequisites() {
        nonHapiFeeCharging = new NonHapiFeeCharging(feeDistribution);
        renewalWork =
                new RenewalHelper(
                        expiryThrottle,
                        classifier,
                        dynamicProperties,
                        fees,
                        recordsHelper,
                        nonHapiFeeCharging,
                        accountsLedger,
                        sideEffectsTracker);
        removalWork =
                new RemovalHelper(
                        classifier, dynamicProperties, contractGC, accountGC, recordsHelper);
    }

    @Test
    void onlyWarnsIfEndingButNotStarted() {
        Assertions.assertDoesNotThrow(subject::endCycle);
    }

    @Test
    void onlyWarnsIfStartingButNotEnded() {
        // when:
        subject.beginCycle(now);

        // expect:
        assertDoesNotThrow(() -> subject.beginCycle(now));
    }

    @Test
    void endsAsExpectedIfStarted() {
        // given:
        subject.beginCycle(now);

        // when:
        subject.endCycle();

        // then:
        assertNull(subject.getCycleTime());
    }

    @Test
    void doesNothingOnNonExpiredAccount() {
        given(classifier.classify(EntityNum.fromLong(nonExpiredAccountNum), now)).willReturn(OTHER);

        subject.beginCycle(now);
        var result = subject.process(nonExpiredAccountNum);

        assertEquals(NOTHING_TO_DO, result);
        verifyNoMoreInteractions(classifier);
    }

    @Test
    void stillMoreToDoIfCannotClassify() {
        given(classifier.classify(EntityNum.fromLong(nonExpiredAccountNum), now))
                .willReturn(COME_BACK_LATER);

        subject.beginCycle(now);
        var result = subject.process(nonExpiredAccountNum);

        assertEquals(STILL_MORE_TO_DO, result);
        verifyNoMoreInteractions(classifier);
    }

    @Test
    void onlyWarnsIfNotInCycle() {
        given(classifier.classify(EntityNum.fromLong(nonExpiredAccountNum), now))
                .willReturn(COME_BACK_LATER);

        subject.beginCycle(now);
        assertDoesNotThrow(() -> subject.process(nonExpiredAccountNum));
    }

    @Test
    void doesNothingDuringGracePeriod() {
        given(classifier.classify(EntityNum.fromLong(nonExpiredAccountNum), now))
                .willReturn(DETACHED_ACCOUNT);

        subject.beginCycle(now);
        var result = subject.process(nonExpiredAccountNum);

        // then:
        assertEquals(NOTHING_TO_DO, result);
        verifyNoMoreInteractions(classifier);
        verifyNoMoreInteractions(sideEffectsTracker);
    }

    @Test
    void doesNothingForTreasuryWithTokenStillLive() {
        given(classifier.classify(EntityNum.fromLong(nonExpiredAccountNum), now))
                .willReturn(DETACHED_TREASURY_GRACE_PERIOD_OVER_BEFORE_TOKEN);

        subject.beginCycle(now);
        final var result = subject.process(nonExpiredAccountNum);

        assertEquals(NOTHING_TO_DO, result);
        verifyNoMoreInteractions(classifier);
    }

    @Test
    void ignoresExpiredBrokeAccountIfNotTargetType() {
        dynamicProperties.disableContractAutoRenew();
        long brokeExpiredAccountNum = 1003L;
        final var expiredNum = EntityNum.fromLong(brokeExpiredAccountNum);

        given(classifier.classify(expiredNum, now)).willReturn(DETACHED_CONTRACT_GRACE_PERIOD_OVER);

        subject.beginCycle(now);
        final var result = subject.process(brokeExpiredAccountNum);

        assertEquals(NOTHING_TO_DO, result);
    }

    @Test
    void ignoresExpiredBrokeContractIfNotTargetType() {
        long brokeExpiredAccountNum = 1003L;
        final var expiredNum = EntityNum.fromLong(brokeExpiredAccountNum);
        given(classifier.classify(expiredNum, now)).willReturn(EXPIRED_CONTRACT_READY_TO_RENEW);

        dynamicProperties.disableAutoRenew();
        dynamicProperties.disableContractAutoRenew();

        subject.beginCycle(now);
        final var result = subject.process(brokeExpiredAccountNum);

        assertEquals(NOTHING_TO_DO, result);
    }

    @Test
    void removesExpiredBrokeAccount() {
        final var treasuryReturns =
                new CryptoGcOutcome(
                        FungibleTreasuryReturns.FINISHED_NOOP_FUNGIBLE_RETURNS,
                        NonFungibleTreasuryReturns.FINISHED_NOOP_NON_FUNGIBLE_RETURNS,
                        true);

        long brokeExpiredAccountNum = 1003L;
        final var expiredNum = EntityNum.fromLong(brokeExpiredAccountNum);
        given(classifier.classify(expiredNum, now)).willReturn(DETACHED_ACCOUNT_GRACE_PERIOD_OVER);
        given(classifier.getLastClassified()).willReturn(mockAccount);
        given(accountGC.expireBestEffort(expiredNum, mockAccount)).willReturn(treasuryReturns);
        dynamicProperties.enableAutoRenew();

        subject.beginCycle(now);
        final var result = subject.process(brokeExpiredAccountNum);

        assertEquals(DONE, result);

        verify(accountGC).expireBestEffort(expiredNum, mockAccount);
        verify(recordsHelper).streamCryptoRemovalStep(false, expiredNum, null, treasuryReturns);
    }

    @Test
    void removesExpiredBrokeContractImmediatelyIfStoragePurged() {
        dynamicProperties.enableContractAutoRenew();

        long brokeExpiredContractNum = 1003L;
        final var expiredNum = EntityNum.fromLong(brokeExpiredContractNum);
        given(classifier.classify(expiredNum, now)).willReturn(DETACHED_CONTRACT_GRACE_PERIOD_OVER);
        given(classifier.getLastClassified()).willReturn(mockContract);
        given(contractGC.expireBestEffort(expiredNum, mockContract)).willReturn(true);
        given(accountGC.expireBestEffort(expiredNum, mockContract)).willReturn(finishedReturns);

        subject.beginCycle(now);
        final var result = subject.process(brokeExpiredContractNum);

        assertEquals(DONE, result);
        verify(accountGC).expireBestEffort(expiredNum, mockContract);
        verify(recordsHelper).streamCryptoRemovalStep(true, expiredNum, null, finishedReturns);
    }

    @Test
    void doesntExpireBrokeContractUntilStoragePurged() {
        long brokeExpiredContractNum = 1003L;
        final var expiredNum = EntityNum.fromLong(brokeExpiredContractNum);
        given(classifier.classify(expiredNum, now)).willReturn(DETACHED_CONTRACT_GRACE_PERIOD_OVER);
        given(classifier.getLastClassified()).willReturn(mockContract);
        dynamicProperties.enableContractAutoRenew();

        subject.beginCycle(now);
        final var result = subject.process(brokeExpiredContractNum);

        assertEquals(STILL_MORE_TO_DO, result);
        verifyNoMoreInteractions(accountGC, recordsHelper);
    }

    @Test
    void alertsIfNotAllExpirationWorkCanBeDone() {
        long brokeExpiredAccountNum = 1003L;
        final var expiredNum = EntityNum.fromLong(brokeExpiredAccountNum);
        given(classifier.classify(expiredNum, now)).willReturn(DETACHED_ACCOUNT_GRACE_PERIOD_OVER);
        given(classifier.getLastClassified()).willReturn(mockAccount);
        given(accountGC.expireBestEffort(expiredNum, mockAccount))
                .willReturn(partiallyFinishedReturns);
        dynamicProperties.enableAutoRenew();

        subject.beginCycle(now);
        final var result = subject.process(brokeExpiredAccountNum);

        assertEquals(STILL_MORE_TO_DO, result);
        verify(accountGC).expireBestEffort(expiredNum, mockAccount);
        verify(recordsHelper)
                .streamCryptoRemovalStep(false, expiredNum, null, partiallyFinishedReturns);
    }

    @Test
    void renewsAccountAtExpectedFee() {
        // setup:
        long fundedExpiredAccountNum = 1004L;
        var key = EntityNum.fromLong(fundedExpiredAccountNum);
        mockAccount.setKey(key);

        given(classifier.classify(EntityNum.fromLong(fundedExpiredAccountNum), now))
                .willReturn(EXPIRED_ACCOUNT_READY_TO_RENEW);
        given(classifier.getLastClassified()).willReturn(mockAccount);
        given(classifier.getLastClassifiedNum()).willReturn(key);

        given(classifier.getPayerForLastClassified()).willReturn(mockAccount);
        given(classifier.getPayerNumForLastClassified()).willReturn(key);
        given(expiryThrottle.allow(any())).willReturn(true);

        given(
                        accountsLedger.get(
                                EntityNum.fromLong(fundedExpiredAccountNum).toGrpcAccountId(),
                                AccountProperty.BALANCE))
                .willReturn(nonZeroBalance);

        given(fees.assessCryptoAutoRenewal(mockAccount, requestedRenewalPeriod, now, mockAccount))
                .willReturn(new RenewAssessment(fee, actualRenewalPeriod));

        subject.beginCycle(now);
        final var result = subject.process(fundedExpiredAccountNum);

        assertEquals(DONE, result);

        verify(feeDistribution).distributeChargedFee(anyLong(), eq(accountsLedger));
        verify(recordsHelper)
                .streamCryptoRenewal(
                        key, fee, now.getEpochSecond() + actualRenewalPeriod, false, key);
    }

    @Test
    void renewsContractAtExpectedFee() {
        // setup: 1241766
        var key = EntityNum.fromLong(fundedExpiredContractNum);
        mockContract.setKey(key);

        given(expiryThrottle.allow(any())).willReturn(true);
        given(classifier.classify(EntityNum.fromLong(fundedExpiredContractNum), now))
                .willReturn(EXPIRED_CONTRACT_READY_TO_RENEW);
        given(classifier.getLastClassified()).willReturn(mockContract);
        given(classifier.getLastClassifiedNum())
                .willReturn(EntityNum.fromLong(fundedExpiredContractNum));
        given(fees.assessCryptoAutoRenewal(mockContract, requestedRenewalPeriod, now, mockContract))
                .willReturn(new RenewAssessment(fee, actualRenewalPeriod));

        given(
                        accountsLedger.get(
                                EntityNum.fromLong(fundedExpiredContractNum).toGrpcAccountId(),
                                AccountProperty.BALANCE))
                .willReturn(nonZeroBalance);

        given(classifier.getPayerNumForLastClassified())
                .willReturn(EntityNum.fromLong(fundedExpiredContractNum));
        given(classifier.getPayerForLastClassified()).willReturn(mockContract);

        subject.beginCycle(now);
        final var result = subject.process(fundedExpiredContractNum);

        assertEquals(DONE, result);

        verify(feeDistribution).distributeChargedFee(anyLong(), eq(accountsLedger));
        verify(recordsHelper)
                .streamCryptoRenewal(
                        key, fee, now.getEpochSecond() + actualRenewalPeriod, true, key);
    }

    @Test
    void skipsAccountRenewalIfNotEnabled() {
        // setup:
        long fundedExpiredAccountNum = 1004L;

        given(classifier.classify(EntityNum.fromLong(fundedExpiredAccountNum), now))
                .willReturn(EXPIRED_ACCOUNT_READY_TO_RENEW);
        dynamicProperties.disableAutoRenew();
        dynamicProperties.enableContractAutoRenew();

        subject.beginCycle(now);
        final var result = subject.process(fundedExpiredAccountNum);

        assertEquals(NOTHING_TO_DO, result);
    }
}

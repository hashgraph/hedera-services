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
package com.hedera.services.state.expiry.renewal;

import static com.hedera.services.state.expiry.EntityProcessResult.DONE;
import static com.hedera.services.state.expiry.EntityProcessResult.NOTHING_TO_DO;
import static com.hedera.services.state.expiry.EntityProcessResult.STILL_MORE_TO_DO;
import static com.hedera.services.state.expiry.classification.ClassificationResult.DETACHED_ACCOUNT;
import static com.hedera.services.state.expiry.classification.ClassificationResult.DETACHED_ACCOUNT_GRACE_PERIOD_OVER;
import static com.hedera.services.state.expiry.classification.ClassificationResult.DETACHED_CONTRACT_GRACE_PERIOD_OVER;
import static com.hedera.services.state.expiry.classification.ClassificationResult.DETACHED_TREASURY_GRACE_PERIOD_OVER_BEFORE_TOKEN;
import static com.hedera.services.state.expiry.classification.ClassificationResult.EXPIRED_ACCOUNT_READY_TO_RENEW;
import static com.hedera.services.state.expiry.classification.ClassificationResult.EXPIRED_CONTRACT_READY_TO_RENEW;
import static com.hedera.services.state.expiry.classification.ClassificationResult.OTHER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.calculation.RenewAssessment;
import com.hedera.services.state.expiry.classification.ClassificationWork;
import com.hedera.services.state.expiry.classification.EntityLookup;
import com.hedera.services.state.expiry.removal.AccountGC;
import com.hedera.services.state.expiry.removal.ContractGC;
import com.hedera.services.state.expiry.removal.RemovalHelper;
import com.hedera.services.state.expiry.removal.RemovalWork;
import com.hedera.services.state.expiry.removal.TreasuryReturns;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.swirlds.merkle.map.MerkleMap;
import java.time.Instant;
import java.util.Collections;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RenewalProcessTest {
    private final long now = 1_234_567L;
    private final long requestedRenewalPeriod = 3601L;
    private final long nonZeroBalance = 2L;
    private final long fee = 1L;
    private final long actualRenewalPeriod = 3600L;
    private final Instant instantNow = Instant.ofEpochSecond(now);
    private final long fundingAccountNum = 98L;
    private final long nonExpiredAccountNum = 1002L;
    private final long fundedExpiredContractNum = 1004L;
    private final MerkleAccount mockAccount =
            MerkleAccountFactory.newAccount()
                    .autoRenewPeriod(requestedRenewalPeriod)
                    .balance(nonZeroBalance)
                    .expirationTime(now - 1)
                    .get();
    private final MerkleAccount mockContract =
            MerkleAccountFactory.newContract()
                    .autoRenewPeriod(requestedRenewalPeriod)
                    .balance(nonZeroBalance)
                    .expirationTime(now - 1)
                    .get();
    private final MerkleAccount fundingAccount =
            MerkleAccountFactory.newAccount()
                    .number(EntityNum.fromLong(fundingAccountNum))
                    .balance(nonZeroBalance)
                    .expirationTime(now + 100L)
                    .get();

    @Mock private FeeCalculator fees;
    @Mock private ClassificationWork classifier;
    @Mock private AccountGC accountGC;
    @Mock private ContractGC contractGC;
    @Mock private RenewalRecordsHelper recordsHelper;
    @Mock private MerkleMap<EntityNum, MerkleAccount> accounts;
    private EntityLookup lookup;
    private MockGlobalDynamicProps dynamicProperties = new MockGlobalDynamicProps();
    private RenewalWork renewalWork;
    private RemovalWork removalWork;
    private RenewalProcess subject;

    @BeforeEach
    void setUp() {
        setUpPreRequisites();
        subject = new RenewalProcess(classifier, recordsHelper, renewalWork, removalWork);
    }

    private void setUpPreRequisites() {
        lookup = new EntityLookup(() -> accounts);
        renewalWork = new RenewalHelper(lookup, classifier, dynamicProperties, fees, recordsHelper);
        removalWork =
                new RemovalHelper(
                        classifier, dynamicProperties, contractGC, accountGC, recordsHelper);
    }

    @Test
    void throwsIfNotInCycle() {
        // expect:
        assertThrows(IllegalStateException.class, () -> subject.process(2));
    }

    @Test
    void startsHelperRenewalCycles() {
        // when:
        subject.beginRenewalCycle(instantNow);

        // then:
        verify(recordsHelper).beginRenewalCycle();
    }

    @Test
    void throwsIfEndingButNotStarted() {
        // expect:
        Assertions.assertThrows(IllegalStateException.class, subject::endRenewalCycle);
    }

    @Test
    void throwsIfStartingButNotEnded() {
        // when:
        subject.beginRenewalCycle(instantNow);

        // expect:
        Assertions.assertThrows(
                IllegalStateException.class, () -> subject.beginRenewalCycle(instantNow));
    }

    @Test
    void endsAsExpectedIfStarted() {
        // given:
        subject.beginRenewalCycle(instantNow);

        // when:
        subject.endRenewalCycle();

        // then:
        verify(recordsHelper).endRenewalCycle();
        assertNull(subject.getCycleTime());
    }

    @Test
    void doesNothingOnNonExpiredAccount() {
        given(classifier.classify(EntityNum.fromLong(nonExpiredAccountNum), now)).willReturn(OTHER);

        subject.beginRenewalCycle(instantNow);
        var result = subject.process(nonExpiredAccountNum);

        assertEquals(NOTHING_TO_DO, result);
        verifyNoMoreInteractions(classifier);
    }

    @Test
    void doesNothingDuringGracePeriod() {
        given(classifier.classify(EntityNum.fromLong(nonExpiredAccountNum), now))
                .willReturn(DETACHED_ACCOUNT);

        subject.beginRenewalCycle(instantNow);
        var result = subject.process(nonExpiredAccountNum);

        // then:
        assertEquals(NOTHING_TO_DO, result);
        verifyNoMoreInteractions(classifier);
    }

    @Test
    void doesNothingForTreasuryWithTokenStillLive() {
        given(classifier.classify(EntityNum.fromLong(nonExpiredAccountNum), now))
                .willReturn(DETACHED_TREASURY_GRACE_PERIOD_OVER_BEFORE_TOKEN);

        subject.beginRenewalCycle(instantNow);
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

        subject.beginRenewalCycle(instantNow);
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

        subject.beginRenewalCycle(instantNow);
        final var result = subject.process(brokeExpiredAccountNum);

        assertEquals(NOTHING_TO_DO, result);
    }

    @Test
    void removesExpiredBrokeAccount() {
        final var treasuryReturns =
                new TreasuryReturns(Collections.emptyList(), Collections.emptyList(), true);

        long brokeExpiredAccountNum = 1003L;
        final var expiredNum = EntityNum.fromLong(brokeExpiredAccountNum);
        given(classifier.classify(expiredNum, now)).willReturn(DETACHED_ACCOUNT_GRACE_PERIOD_OVER);
        given(classifier.getLastClassified()).willReturn(mockAccount);
        given(accountGC.expireBestEffort(expiredNum, mockAccount)).willReturn(treasuryReturns);
        dynamicProperties.enableAutoRenew();

        subject.beginRenewalCycle(instantNow);
        final var result = subject.process(brokeExpiredAccountNum);

        assertEquals(DONE, result);

        verify(accountGC).expireBestEffort(expiredNum, mockAccount);
        verify(recordsHelper)
                .streamCryptoRemoval(expiredNum, Collections.emptyList(), Collections.emptyList());
    }

    @Test
    void removesExpiredBrokeContractImmediatelyIfStoragePurged() {
        dynamicProperties.enableContractAutoRenew();

        final var treasuryReturns =
                new TreasuryReturns(Collections.emptyList(), Collections.emptyList(), true);

        long brokeExpiredContractNum = 1003L;
        final var expiredNum = EntityNum.fromLong(brokeExpiredContractNum);
        given(classifier.classify(expiredNum, now)).willReturn(DETACHED_CONTRACT_GRACE_PERIOD_OVER);
        given(classifier.getLastClassified()).willReturn(mockContract);
        given(contractGC.expireBestEffort(expiredNum, mockContract)).willReturn(true);
        given(accountGC.expireBestEffort(expiredNum, mockContract)).willReturn(treasuryReturns);

        subject.beginRenewalCycle(instantNow);
        final var result = subject.process(brokeExpiredContractNum);

        assertEquals(DONE, result);
        verify(accountGC).expireBestEffort(expiredNum, mockContract);
        verify(recordsHelper)
                .streamCryptoRemoval(expiredNum, Collections.emptyList(), Collections.emptyList());
    }

    @Test
    void doesntExpireBrokeContractUntilStoragePurged() {
        long brokeExpiredContractNum = 1003L;
        final var expiredNum = EntityNum.fromLong(brokeExpiredContractNum);
        given(classifier.classify(expiredNum, now)).willReturn(DETACHED_CONTRACT_GRACE_PERIOD_OVER);
        given(classifier.getLastClassified()).willReturn(mockContract);
        dynamicProperties.enableContractAutoRenew();

        subject.beginRenewalCycle(instantNow);
        final var result = subject.process(brokeExpiredContractNum);

        assertEquals(STILL_MORE_TO_DO, result);
        verify(recordsHelper).beginRenewalCycle();
        verifyNoMoreInteractions(accountGC, recordsHelper);
    }

    @Test
    void alertsIfNotAllExpirationWorkCanBeDone() {
        final var treasuryReturns =
                new TreasuryReturns(Collections.emptyList(), Collections.emptyList(), false);

        long brokeExpiredAccountNum = 1003L;
        final var expiredNum = EntityNum.fromLong(brokeExpiredAccountNum);
        given(classifier.classify(expiredNum, now)).willReturn(DETACHED_ACCOUNT_GRACE_PERIOD_OVER);
        given(classifier.getLastClassified()).willReturn(mockAccount);
        given(accountGC.expireBestEffort(expiredNum, mockAccount)).willReturn(treasuryReturns);
        dynamicProperties.enableAutoRenew();

        subject.beginRenewalCycle(instantNow);
        final var result = subject.process(brokeExpiredAccountNum);

        assertEquals(STILL_MORE_TO_DO, result);
        verify(accountGC).expireBestEffort(expiredNum, mockAccount);
        verify(recordsHelper)
                .streamCryptoRemoval(expiredNum, Collections.emptyList(), Collections.emptyList());
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

        given(classifier.getPayerAccountForAutoRenew()).willReturn(mockAccount);
        given(classifier.getPayerNumForAutoRenew()).willReturn(key);

        given(lookup.getMutableAccount(key)).willReturn(mockAccount);
        given(lookup.getMutableAccount(EntityNum.fromLong(fundingAccountNum)))
                .willReturn(fundingAccount);
        given(accounts.getForModify(EntityNum.fromLong(fundingAccountNum)))
                .willReturn(fundingAccount);
        given(accounts.getForModify(EntityNum.fromLong(fundedExpiredAccountNum)))
                .willReturn(mockAccount);

        given(
                        fees.assessCryptoAutoRenewal(
                                mockAccount, requestedRenewalPeriod, instantNow, mockAccount))
                .willReturn(new RenewAssessment(fee, actualRenewalPeriod));
        dynamicProperties.enableContractAutoRenew();

        subject.beginRenewalCycle(instantNow);
        final var result = subject.process(fundedExpiredAccountNum);

        assertEquals(DONE, result);

        verify(recordsHelper)
                .streamCryptoRenewal(key, fee, now - 1 + actualRenewalPeriod, false, key);
    }

    @Test
    void renewsContractAtExpectedFee() {
        // setup: 1241766
        var key = EntityNum.fromLong(fundedExpiredContractNum);
        mockContract.setKey(key);

        given(classifier.classify(EntityNum.fromLong(fundedExpiredContractNum), now))
                .willReturn(EXPIRED_CONTRACT_READY_TO_RENEW);
        given(classifier.getLastClassified()).willReturn(mockContract);
        given(classifier.getLastClassifiedNum())
                .willReturn(EntityNum.fromLong(fundedExpiredContractNum));
        given(
                        fees.assessCryptoAutoRenewal(
                                mockContract, requestedRenewalPeriod, instantNow, mockContract))
                .willReturn(new RenewAssessment(fee, actualRenewalPeriod));
        dynamicProperties.shouldAutoRenewContracts();

        given(accounts.getForModify(EntityNum.fromLong(fundedExpiredContractNum)))
                .willReturn(mockContract);
        given(accounts.getForModify(EntityNum.fromLong(fundingAccountNum)))
                .willReturn(fundingAccount);

        //        given(classifier.resolvePayerForAutoRenew()).willReturn(mockContract);
        given(classifier.getPayerNumForAutoRenew())
                .willReturn(EntityNum.fromLong(fundedExpiredContractNum));
        given(classifier.getPayerAccountForAutoRenew()).willReturn(mockContract);

        subject.beginRenewalCycle(instantNow);
        final var result = subject.process(fundedExpiredContractNum);

        assertEquals(DONE, result);

        verify(recordsHelper)
                .streamCryptoRenewal(key, fee, now - 1 + actualRenewalPeriod, true, key);
    }

    @Test
    void skipsAccountRenewalIfNotEnabled() {
        // setup:
        long fundedExpiredAccountNum = 1004L;

        given(classifier.classify(EntityNum.fromLong(fundedExpiredAccountNum), now))
                .willReturn(EXPIRED_ACCOUNT_READY_TO_RENEW);
        dynamicProperties.disableAutoRenew();
        dynamicProperties.enableContractAutoRenew();

        subject.beginRenewalCycle(instantNow);
        final var result = subject.process(fundedExpiredAccountNum);

        assertEquals(NOTHING_TO_DO, result);
    }
}

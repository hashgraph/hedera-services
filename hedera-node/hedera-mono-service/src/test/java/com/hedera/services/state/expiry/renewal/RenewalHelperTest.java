/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.state.expiry.classification.ClassificationWork.CLASSIFICATION_WORK;
import static com.hedera.services.state.expiry.renewal.RenewalHelper.SELF_RENEWAL_WORK;
import static com.hedera.services.state.expiry.renewal.RenewalHelper.SUPPORTED_RENEWAL_WORK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.google.protobuf.ByteString;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.calculation.RenewAssessment;
import com.hedera.services.fees.charging.FeeDistribution;
import com.hedera.services.fees.charging.NonHapiFeeCharging;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.expiry.ExpiryRecordsHelper;
import com.hedera.services.state.expiry.classification.ClassificationWork;
import com.hedera.services.state.expiry.classification.EntityLookup;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.tasks.SystemTaskResult;
import com.hedera.services.stats.ExpiryStats;
import com.hedera.services.throttling.ExpiryThrottle;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RenewalHelperTest {
    private final MockGlobalDynamicProps properties = new MockGlobalDynamicProps();
    @Mock private MerkleMap<EntityNum, MerkleAccount> accounts;
    @Mock private FeeCalculator fees;
    @Mock private ExpiryRecordsHelper recordsHelper;
    @Mock private ExpiryThrottle expiryThrottle;
    @Mock private ExpiryStats expiryStats;
    @Mock private FeeDistribution feeDistribution;
    @Mock private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
    @Mock private SideEffectsTracker sideEffectsTracker;

    private NonHapiFeeCharging nonHapiFeeCharging;
    private EntityLookup lookup;
    private ClassificationWork classificationWork;
    private RenewalHelper subject;

    @BeforeEach
    void setUp() {
        lookup = new EntityLookup(() -> accounts);
        classificationWork = new ClassificationWork(properties, lookup, expiryThrottle);
        nonHapiFeeCharging = new NonHapiFeeCharging(feeDistribution);
        subject =
                new RenewalHelper(
                        expiryStats,
                        expiryThrottle,
                        classificationWork,
                        properties,
                        fees,
                        recordsHelper,
                        nonHapiFeeCharging,
                        accountsLedger,
                        sideEffectsTracker);
    }

    @Test
    void renewsLastClassifiedAccountAsRequested() {
        // setup:
        var key = EntityNum.fromLong(fundedExpiredAccountNum);

        givenPresent(fundedExpiredAccountNum, expiredAccountNonZeroBalance);
        givenPresent(98, fundingAccount);
        given(expiryThrottle.allow(anyList())).willReturn(true);
        given(
                        accountsLedger.get(
                                EntityNum.fromLong(fundedExpiredAccountNum).toGrpcAccountId(),
                                BALANCE))
                .willReturn(1234567L);
        // when:
        classificationWork.classify(EntityNum.fromLong(fundedExpiredAccountNum), now);
        given(
                        fees.assessCryptoAutoRenewal(
                                expiredAccountNonZeroBalance,
                                0L,
                                now,
                                expiredAccountNonZeroBalance))
                .willReturn(new RenewAssessment(nonZeroBalance, 3600L));

        // and:
        final var targetNum = EntityNum.fromLong(fundedExpiredAccountNum);
        expiredAccountNonZeroBalance.setKey(targetNum);
        subject.tryToRenewAccount(targetNum, now);

        // then:
        verify(accountsLedger, times(1)).get(key.toGrpcAccountId(), BALANCE);
        verify(feeDistribution).distributeChargedFee(anyLong(), eq(accountsLedger));
        verify(sideEffectsTracker).reset();
        verify(expiryStats, never()).countRenewedContract();
        final var expectedNewExpiry = now.getEpochSecond() + 3600L;
        verify(recordsHelper)
                .streamCryptoRenewal(targetNum, nonZeroBalance, expectedNewExpiry, false);
        assertEquals(key, classificationWork.getPayerNumForLastClassified());
    }

    @Test
    void renewsLastClassifiedContractAsRequested() {
        // setup:
        var key = EntityNum.fromLong(fundedExpiredAccountNum);

        givenPresent(fundedExpiredAccountNum, expiredContractNonZeroBalance);
        givenPresent(98, fundingAccount);
        given(expiryThrottle.allow(any())).willReturn(true);
        given(
                        accountsLedger.get(
                                EntityNum.fromLong(fundedExpiredAccountNum).toGrpcAccountId(),
                                BALANCE))
                .willReturn(1234567L);
        // when:
        classificationWork.classify(EntityNum.fromLong(fundedExpiredAccountNum), now);
        given(
                        fees.assessCryptoAutoRenewal(
                                expiredContractNonZeroBalance,
                                0L,
                                now,
                                expiredContractNonZeroBalance))
                .willReturn(new RenewAssessment(nonZeroBalance, 3600L));

        // and:
        final var targetNum = EntityNum.fromLong(fundedExpiredAccountNum);
        expiredContractNonZeroBalance.setKey(targetNum);
        subject.tryToRenewContract(targetNum, now);

        // then:
        verify(accountsLedger, times(1)).get(key.toGrpcAccountId(), BALANCE);
        verify(feeDistribution).distributeChargedFee(anyLong(), eq(accountsLedger));
        verify(sideEffectsTracker).reset();
        verify(expiryStats).countRenewedContract();
        final var expectedNewExpiry = now.getEpochSecond() + 3600L;
        verify(recordsHelper)
                .streamCryptoRenewal(targetNum, nonZeroBalance, expectedNewExpiry, true);
        assertEquals(key, classificationWork.getPayerNumForLastClassified());
    }

    @Test
    void doesNotRenewIfNoSelfCapacityAvailable() {
        givenPresent(fundedExpiredAccountNum, expiredAccountNonZeroBalance);
        given(expiryThrottle.allow(CLASSIFICATION_WORK)).willReturn(true);
        given(expiryThrottle.allow(SELF_RENEWAL_WORK)).willReturn(false);

        classificationWork.classify(EntityNum.fromLong(fundedExpiredAccountNum), now);

        final var result =
                subject.tryToRenewAccount(EntityNum.fromLong(fundedExpiredAccountNum), now);
        assertEquals(SystemTaskResult.NO_CAPACITY_LEFT, result);
        verifyNoInteractions(sideEffectsTracker);
    }

    @Test
    void doesNotRenewIfNoSupportedCapacityAvailable() {
        given(expiryThrottle.allow(SUPPORTED_RENEWAL_WORK)).willReturn(false);
        classificationWork = mock(ClassificationWork.class);
        given(classificationWork.getLastClassified()).willReturn(new MerkleAccount());
        given(classificationWork.getPayerForLastClassified()).willReturn(new MerkleAccount());

        subject =
                new RenewalHelper(
                        expiryStats,
                        expiryThrottle,
                        classificationWork,
                        properties,
                        fees,
                        recordsHelper,
                        nonHapiFeeCharging,
                        accountsLedger,
                        sideEffectsTracker);

        final var result =
                subject.tryToRenewAccount(EntityNum.fromLong(fundedExpiredAccountNum), now);
        assertEquals(SystemTaskResult.NO_CAPACITY_LEFT, result);
    }

    @Test
    void doesNothingWhenDisabled() {
        properties.disableAutoRenew();
        var result = subject.tryToRenewAccount(EntityNum.fromLong(fundedExpiredAccountNum), now);
        assertEquals(SystemTaskResult.NOTHING_TO_DO, result);

        properties.disableContractAutoRenew();
        result = subject.tryToRenewContract(EntityNum.fromLong(fundedExpiredAccountNum), now);
        assertEquals(SystemTaskResult.NOTHING_TO_DO, result);
        verifyNoInteractions(sideEffectsTracker);
    }

    @Test
    void rejectsAsIseIfFeeIsUnaffordable() {
        givenPresent(brokeExpiredNum, expiredAccountZeroBalance);
        given(expiryThrottle.allow(CLASSIFICATION_WORK)).willReturn(true);

        // when:
        classificationWork.classify(EntityNum.fromLong(brokeExpiredNum), now);
        // expect:
        assertThrows(IllegalStateException.class, () -> subject.renewWith(nonZeroBalance, 3600L));
    }

    private void givenPresent(final long num, final MerkleAccount account) {
        var key = EntityNum.fromLong(num);
        if (num != 98) {
            given(accounts.get(key)).willReturn(account);
        }
    }

    private final Instant now = Instant.ofEpochSecond(1_234_567L);
    private final long nonZeroBalance = 1L;
    private final MerkleAccount expiredAccountZeroBalance =
            MerkleAccountFactory.newAccount()
                    .balance(0)
                    .expirationTime(now.getEpochSecond() - 1)
                    .alias(ByteString.copyFromUtf8("bbbb"))
                    .get();
    private final MerkleAccount expiredAccountNonZeroBalance =
            MerkleAccountFactory.newAccount()
                    .balance(nonZeroBalance)
                    .expirationTime(now.getEpochSecond() - 1)
                    .alias(ByteString.copyFromUtf8("dddd"))
                    .get();
    private final MerkleAccount expiredContractNonZeroBalance =
            MerkleAccountFactory.newContract()
                    .balance(nonZeroBalance)
                    .expirationTime(now.getEpochSecond() - 1)
                    .alias(ByteString.copyFromUtf8("dddd"))
                    .get();
    private final MerkleAccount fundingAccount =
            MerkleAccountFactory.newAccount()
                    .balance(0)
                    .alias(ByteString.copyFromUtf8("eeee"))
                    .get();
    private final long brokeExpiredNum = 2L;
    private final long fundedExpiredAccountNum = 3L;
}

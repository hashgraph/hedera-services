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
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.calculation.RenewAssessment;
import com.hedera.services.state.expiry.EntityProcessResult;
import com.hedera.services.state.expiry.ExpiryRecordsHelper;
import com.hedera.services.state.expiry.classification.ClassificationWork;
import com.hedera.services.state.expiry.classification.EntityLookup;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.throttling.ExpiryThrottle;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
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

    private EntityLookup lookup;
    private ClassificationWork classificationWork;
    private RenewalHelper subject;

    @BeforeEach
    void setUp() {
        lookup = new EntityLookup(() -> accounts);
        classificationWork = new ClassificationWork(properties, lookup, expiryThrottle);
        subject =
                new RenewalHelper(
                        lookup,
                        expiryThrottle,
                        classificationWork,
                        properties,
                        fees,
                        recordsHelper);
    }

    @Test
    void renewsLastClassifiedAsRequested() {
        // setup:
        var key = EntityNum.fromLong(fundedExpiredAccountNum);
        var fundingKey = EntityNum.fromInt(98);

        givenPresent(fundedExpiredAccountNum, expiredAccountNonZeroBalance, true);
        givenPresent(98, fundingAccount, true);
        given(expiryThrottle.allow(any(), any(Instant.class))).willReturn(true);
        given(expiryThrottle.allow(any())).willReturn(true);

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
        subject.tryToRenewAccount(EntityNum.fromLong(fundedExpiredAccountNum), now);

        // then:
        verify(accounts, times(2)).getForModify(key);
        verify(accounts).getForModify(fundingKey);
        assertEquals(key, classificationWork.getPayerNumForLastClassified());
    }

    @Test
    void doesNotRenewIfNoSelfCapacityAvailable() {
        givenPresent(fundedExpiredAccountNum, expiredAccountNonZeroBalance, false);
        given(expiryThrottle.allow(eq(CLASSIFICATION_WORK), any(Instant.class))).willReturn(true);
        given(expiryThrottle.allow(SELF_RENEWAL_WORK)).willReturn(false);

        classificationWork.classify(EntityNum.fromLong(fundedExpiredAccountNum), now);

        final var result =
                subject.tryToRenewAccount(EntityNum.fromLong(fundedExpiredAccountNum), now);
        assertEquals(EntityProcessResult.STILL_MORE_TO_DO, result);
    }

    @Test
    void doesNotRenewIfNoSupportedCapacityAvailable() {
        given(expiryThrottle.allow(SUPPORTED_RENEWAL_WORK)).willReturn(false);
        classificationWork = mock(ClassificationWork.class);
        given(classificationWork.getLastClassified()).willReturn(new MerkleAccount());
        given(classificationWork.getPayerForLastClassified()).willReturn(new MerkleAccount());

        subject =
                new RenewalHelper(
                        lookup,
                        expiryThrottle,
                        classificationWork,
                        properties,
                        fees,
                        recordsHelper);

        final var result =
                subject.tryToRenewAccount(EntityNum.fromLong(fundedExpiredAccountNum), now);
        assertEquals(EntityProcessResult.STILL_MORE_TO_DO, result);
    }

    @Test
    void doesNothingWhenDisabled() {
        properties.disableAutoRenew();
        var result = subject.tryToRenewAccount(EntityNum.fromLong(fundedExpiredAccountNum), now);
        assertEquals(EntityProcessResult.NOTHING_TO_DO, result);

        properties.disableContractAutoRenew();
        result = subject.tryToRenewContract(EntityNum.fromLong(fundedExpiredAccountNum), now);
        assertEquals(EntityProcessResult.NOTHING_TO_DO, result);
    }

    @Test
    void rejectsAsIseIfFeeIsUnaffordable() {
        givenPresent(brokeExpiredNum, expiredAccountZeroBalance);
        given(expiryThrottle.allow(eq(CLASSIFICATION_WORK), any(Instant.class))).willReturn(true);

        // when:
        classificationWork.classify(EntityNum.fromLong(brokeExpiredNum), now);
        // expect:
        assertThrows(IllegalStateException.class, () -> subject.renewWith(nonZeroBalance, 3600L));
    }

    private void givenPresent(final long num, final MerkleAccount account) {
        givenPresent(num, account, false);
    }

    private void givenPresent(long num, MerkleAccount account, boolean modifiable) {
        var key = EntityNum.fromLong(num);
        if (num != 98) {
            given(accounts.get(key)).willReturn(account);
        }
        if (modifiable) {
            given(accounts.getForModify(key)).willReturn(account);
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
    private final MerkleAccount fundingAccount =
            MerkleAccountFactory.newAccount()
                    .balance(0)
                    .alias(ByteString.copyFromUtf8("eeee"))
                    .get();
    private final long brokeExpiredNum = 2L;
    private final long fundedExpiredAccountNum = 3L;
}

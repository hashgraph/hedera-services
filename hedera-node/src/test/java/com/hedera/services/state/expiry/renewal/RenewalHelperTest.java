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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.calculation.RenewAssessment;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.state.expiry.EntityProcessResult;
import com.hedera.services.state.expiry.classification.ClassificationWork;
import com.hedera.services.state.expiry.classification.EntityLookup;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
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
    @Mock private MerkleMap<EntityNum, MerkleAccount> accounts;
    @Mock private AliasManager aliasManager;
    private final MockGlobalDynamicProps properties = new MockGlobalDynamicProps();
    @Mock private FeeCalculator fees;
    @Mock private RenewalRecordsHelper recordsHelper;

    private EntityLookup lookup;
    private ClassificationWork classificationWork;
    private RenewalHelper subject;

    @BeforeEach
    void setUp() {
        lookup = new EntityLookup(() -> accounts);
        classificationWork = new ClassificationWork(properties, lookup);
        subject = new RenewalHelper(lookup, classificationWork, properties, fees, recordsHelper);
    }

    @Test
    void renewsLastClassifiedAsRequested() {
        // setup:
        var key = EntityNum.fromLong(fundedExpiredAccountNum);
        var fundingKey = EntityNum.fromInt(98);

        givenPresent(fundedExpiredAccountNum, expiredAccountNonZeroBalance, true);
        givenPresent(98, fundingAccount, true);

        // when:
        classificationWork.classify(EntityNum.fromLong(fundedExpiredAccountNum), now);
        classificationWork.resolvePayerForAutoRenew();
        given(
                        fees.assessCryptoAutoRenewal(
                                expiredAccountNonZeroBalance,
                                0L,
                                Instant.ofEpochSecond(now),
                                expiredAccountNonZeroBalance))
                .willReturn(new RenewAssessment(nonZeroBalance, 3600L));

        // and:
        subject.tryToRenewAccount(
                EntityNum.fromLong(fundedExpiredAccountNum), Instant.ofEpochSecond(now));

        // then:
        verify(accounts, times(2)).getForModify(key);
        verify(accounts).getForModify(fundingKey);
        verify(aliasManager, never()).forgetAlias(any());
        assertEquals(key, classificationWork.getPayerNumForAutoRenew());
    }

    @Test
    void renewsLastClassifiedWithAutoRenewAccountAsPayer() {
        // setup:
        var key = EntityNum.fromLong(nonExpiredAccountNum);
        var autoRenewAccount = EntityNum.fromLong(10L);
        var fundingKey = EntityNum.fromInt(98);

        givenPresent(nonExpiredAccountNum, nonExpiredAccount, true);

        given(accounts.getForModify(autoRenewAccount)).willReturn(nonExpiredAccountWithAutoRenew);
        given(accounts.get(key)).willReturn(nonExpiredAccountWithAutoRenew);
        given(accounts.get(autoRenewAccount)).willReturn(nonExpiredAccount);
        givenPresent(98, fundingAccount, true);

        // when:
        classificationWork.classify(EntityNum.fromLong(nonExpiredAccountNum), now);
        classificationWork.resolvePayerForAutoRenew();

        // and:
        given(
                        fees.assessCryptoAutoRenewal(
                                nonExpiredAccountWithAutoRenew,
                                0L,
                                Instant.ofEpochSecond(now),
                                autoRenewMerkleAccount))
                .willReturn(new RenewAssessment(nonZeroBalance, 3600L));

        // and:
        subject.tryToRenewAccount(
                EntityNum.fromLong(fundedExpiredAccountNum), Instant.ofEpochSecond(now));

        // then:
        verify(accounts, times(1)).getForModify(autoRenewAccount);
        verify(accounts).getForModify(fundingKey);
        verify(aliasManager, never()).forgetAlias(any());
        assertEquals(autoRenewAccount, classificationWork.getPayerNumForAutoRenew());
    }

    @Test
    void doesNothingWhenDisabled() {
        properties.disableAutoRenew();
        var result =
                subject.tryToRenewAccount(
                        EntityNum.fromLong(fundedExpiredAccountNum), Instant.ofEpochSecond(now));
        assertEquals(EntityProcessResult.NOTHING_TO_DO, result);

        properties.disableContractAutoRenew();
        result =
                subject.tryToRenewContract(
                        EntityNum.fromLong(fundedExpiredAccountNum), Instant.ofEpochSecond(now));
        assertEquals(EntityProcessResult.NOTHING_TO_DO, result);
    }

    @Test
    void cannotRenewIfNoLastClassified() {
        // expect:
        assertThrows(IllegalStateException.class, () -> subject.renewWith(nonZeroBalance, 3600L));
    }

    @Test
    void rejectsAsIseIfFeeIsUnaffordable() {
        givenPresent(brokeExpiredNum, expiredAccountZeroBalance);

        // when:
        classificationWork.classify(EntityNum.fromLong(brokeExpiredNum), now);
        classificationWork.resolvePayerForAutoRenew();
        // expect:
        assertThrows(IllegalStateException.class, () -> subject.renewWith(nonZeroBalance, 3600L));
    }

    private void givenPresent(final long num, final MerkleAccount account) {
        givenPresent(num, account, false);
    }

    private void givenPresent(long num, MerkleAccount account, boolean modifiable) {
        var key = EntityNum.fromLong(num);
        if (num != 98) {
            given(accounts.containsKey(key)).willReturn(true);
            given(accounts.get(key)).willReturn(account);
        }
        if (modifiable) {
            given(accounts.getForModify(key)).willReturn(account);
        }
    }

    private final long now = 1_234_567L;
    private final long nonZeroBalance = 1L;

    private final MerkleAccount nonExpiredAccount =
            MerkleAccountFactory.newAccount()
                    .balance(10)
                    .expirationTime(now + 1)
                    .alias(ByteString.copyFromUtf8("aaaa"))
                    .get();
    private final MerkleAccount nonExpiredAccountWithAutoRenew =
            MerkleAccountFactory.newAccount()
                    .isSmartContract(true)
                    .balance(10)
                    .expirationTime(now + 1)
                    .alias(ByteString.copyFromUtf8("aaaa"))
                    .autoRenewAccount(EntityId.fromIdentityCode(10).toGrpcAccountId())
                    .get();
    private final MerkleAccount autoRenewMerkleAccount =
            MerkleAccountFactory.newAccount()
                    .balance(10)
                    .expirationTime(now + 1)
                    .alias(ByteString.copyFromUtf8("aaaa"))
                    .get();
    private final MerkleAccount expiredAccountZeroBalance =
            MerkleAccountFactory.newAccount()
                    .balance(0)
                    .expirationTime(now - 1)
                    .alias(ByteString.copyFromUtf8("bbbb"))
                    .get();
    private final MerkleAccount expiredAccountNonZeroBalance =
            MerkleAccountFactory.newAccount()
                    .balance(nonZeroBalance)
                    .expirationTime(now - 1)
                    .alias(ByteString.copyFromUtf8("dddd"))
                    .get();
    private final MerkleAccount fundingAccount =
            MerkleAccountFactory.newAccount()
                    .balance(0)
                    .alias(ByteString.copyFromUtf8("eeee"))
                    .get();
    private final MerkleAccount contractAccount =
            MerkleAccountFactory.newAccount()
                    .isSmartContract(true)
                    .balance(1)
                    .expirationTime(now - 1)
                    .get();
    private final long nonExpiredAccountNum = 1L;
    private final long brokeExpiredNum = 2L;
    private final long fundedExpiredAccountNum = 3L;
}

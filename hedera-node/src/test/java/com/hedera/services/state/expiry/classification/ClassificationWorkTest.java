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
package com.hedera.services.state.expiry.classification;

import static com.hedera.services.state.expiry.classification.ClassificationResult.*;
import static com.hedera.services.state.expiry.classification.ClassificationWork.CLASSIFICATION_WORK;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
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
class ClassificationWorkTest {
    @Mock private MerkleMap<EntityNum, MerkleAccount> accounts;
    @Mock private ExpiryThrottle expiryThrottle;

    private EntityLookup lookup;
    private ClassificationWork subject;

    @BeforeEach
    void setUp() {
        lookup = new EntityLookup(() -> accounts);
        subject = new ClassificationWork(dynamicProps, lookup, expiryThrottle);
    }

    @Test
    void classifiesNonAccount() {
        given(expiryThrottle.allow(eq(CLASSIFICATION_WORK), any(Instant.class))).willReturn(true);
        // expect:
        assertEquals(OTHER, subject.classify(EntityNum.fromLong(4L), now));
    }

    @Test
    void classifiesNoCapacityToCheck() {
        assertEquals(COME_BACK_LATER, subject.classify(EntityNum.fromLong(4L), now));
    }

    @Test
    void classifiesNonExpiredAccount() {
        given(expiryThrottle.allow(eq(CLASSIFICATION_WORK), any(Instant.class))).willReturn(true);
        givenPresent(nonExpiredAccountNum, nonExpiredAccount);

        // expect:
        assertEquals(OTHER, subject.classify(EntityNum.fromLong(nonExpiredAccountNum), now));
    }

    @Test
    void classifiesNonExpiredContract() {
        given(expiryThrottle.allow(eq(CLASSIFICATION_WORK), any(Instant.class))).willReturn(true);
        given(expiryThrottle.allow(CLASSIFICATION_WORK)).willReturn(true);
        givenPresent(nonExpiredAccountNum, contractAccount);

        // expect:
        assertEquals(
                EXPIRED_CONTRACT_READY_TO_RENEW,
                subject.classify(EntityNum.fromLong(nonExpiredAccountNum), now));
    }

    @Test
    void classifiesDeletedAccountAfterExpiration() {
        given(expiryThrottle.allow(eq(CLASSIFICATION_WORK), any(Instant.class))).willReturn(true);
        givenPresent(brokeExpiredNum, expiredDeletedAccount);

        // expect:
        assertEquals(
                DETACHED_ACCOUNT_GRACE_PERIOD_OVER,
                subject.classify(EntityNum.fromLong(brokeExpiredNum), now));
    }

    @Test
    void classifiesDeletedContractAfterExpiration() {
        given(expiryThrottle.allow(eq(CLASSIFICATION_WORK), any(Instant.class))).willReturn(true);
        givenPresent(brokeExpiredNum, expiredDeletedContract);

        assertEquals(
                DETACHED_CONTRACT_GRACE_PERIOD_OVER,
                subject.classify(EntityNum.fromLong(brokeExpiredNum), now));
    }

    @Test
    void classifiesDetachedAccountAfterGracePeriod() {
        given(expiryThrottle.allow(eq(CLASSIFICATION_WORK), any(Instant.class))).willReturn(true);
        givenPresent(brokeExpiredNum, expiredAccountZeroBalance);

        // expect:
        assertEquals(
                DETACHED_ACCOUNT_GRACE_PERIOD_OVER,
                subject.classify(
                        EntityNum.fromLong(brokeExpiredNum),
                        now.plusSeconds(dynamicProps.autoRenewGracePeriod())));
    }

    @Test
    void classifiesDetachedContractAfterGracePeriod() {
        given(expiryThrottle.allow(eq(CLASSIFICATION_WORK), any(Instant.class))).willReturn(true);
        given(expiryThrottle.allow(CLASSIFICATION_WORK)).willReturn(true);
        givenPresent(brokeExpiredNum, expiredContractZeroBalance);

        // expect:
        assertEquals(
                DETACHED_CONTRACT_GRACE_PERIOD_OVER,
                subject.classify(
                        EntityNum.fromLong(brokeExpiredNum),
                        now.plusSeconds(dynamicProps.autoRenewGracePeriod())));
    }

    @Test
    void classifiesContractWithAutoRenewReadyForRenew() {
        given(expiryThrottle.allow(eq(CLASSIFICATION_WORK), any(Instant.class))).willReturn(true);
        given(expiryThrottle.allow(CLASSIFICATION_WORK)).willReturn(true);
        givenPresent(brokeExpiredNum, expiredContractWithAutoRenew);
        givenPresent(autoRenewNum, solventAutoRenewAccount);

        // expect:
        assertEquals(
                EXPIRED_CONTRACT_READY_TO_RENEW,
                subject.classify(
                        EntityNum.fromLong(brokeExpiredNum),
                        now.plusSeconds(dynamicProps.autoRenewGracePeriod())));
        assertEquals(EntityNum.fromLong(autoRenewNum), subject.getPayerNumForLastClassified());
        assertSame(solventAutoRenewAccount, subject.getPayerForLastClassified());
    }

    @Test
    void classifiesContractWithInvalidAutoRenewReadyForRenew() {
        given(expiryThrottle.allow(eq(CLASSIFICATION_WORK), any(Instant.class))).willReturn(true);
        given(expiryThrottle.allow(CLASSIFICATION_WORK)).willReturn(true);
        givenPresent(brokeExpiredNum, expiredContractWithAutoRenew);
        givenPresent(autoRenewNum, insolventAutoRenewAccount);

        // expect:
        assertEquals(
                EXPIRED_CONTRACT_READY_TO_RENEW,
                subject.classify(
                        EntityNum.fromLong(brokeExpiredNum),
                        now.plusSeconds(dynamicProps.autoRenewGracePeriod())));
        assertEquals(EntityNum.fromLong(brokeExpiredNum), subject.getPayerNumForLastClassified());
        assertSame(expiredContractWithAutoRenew, subject.getPayerForLastClassified());
    }

    @Test
    void abandonsClassifyingContractIfNoCapacity() {
        given(expiryThrottle.allow(eq(CLASSIFICATION_WORK), any(Instant.class))).willReturn(true);
        given(expiryThrottle.allow(CLASSIFICATION_WORK)).willReturn(false);
        givenPresent(brokeExpiredNum, expiredContractZeroBalance);

        // expect:
        assertEquals(
                COME_BACK_LATER,
                subject.classify(
                        EntityNum.fromLong(brokeExpiredNum),
                        now.plusSeconds(dynamicProps.autoRenewGracePeriod())));
    }

    @Test
    void classifiesDetachedAccountAfterGracePeriodAsOtherIfTokenNotYetRemoved() {
        given(expiryThrottle.allow(eq(CLASSIFICATION_WORK), any(Instant.class))).willReturn(true);
        givenPresent(brokeExpiredNum, expiredAccountZeroBalance);
        expiredAccountZeroBalance.setNumTreasuryTitles(1);

        // expect:
        assertEquals(
                DETACHED_TREASURY_GRACE_PERIOD_OVER_BEFORE_TOKEN,
                subject.classify(
                        EntityNum.fromLong(brokeExpiredNum),
                        now.plusSeconds(dynamicProps.autoRenewGracePeriod())));
    }

    @Test
    void classifiesDetachedAccount() {
        given(expiryThrottle.allow(eq(CLASSIFICATION_WORK), any(Instant.class))).willReturn(true);
        givenPresent(brokeExpiredNum, expiredAccountZeroBalance);

        // expect:
        assertEquals(DETACHED_ACCOUNT, subject.classify(EntityNum.fromLong(brokeExpiredNum), now));
    }

    @Test
    void classifiesDetachedContract() {
        given(expiryThrottle.allow(eq(CLASSIFICATION_WORK), any(Instant.class))).willReturn(true);
        given(expiryThrottle.allow(CLASSIFICATION_WORK)).willReturn(true);
        givenPresent(brokeExpiredNum, expiredContractZeroBalance);

        // expect:
        assertEquals(DETACHED_CONTRACT, subject.classify(EntityNum.fromLong(brokeExpiredNum), now));
    }

    @Test
    void classifiesFundedExpiredAccount() {
        given(expiryThrottle.allow(eq(CLASSIFICATION_WORK), any(Instant.class))).willReturn(true);
        givenPresent(fundedExpiredAccountNum, expiredAccountNonZeroBalance);

        // expect:
        assertEquals(
                EXPIRED_ACCOUNT_READY_TO_RENEW,
                subject.classify(EntityNum.fromLong(fundedExpiredAccountNum), now));
        assertEquals(
                EntityNum.fromLong(fundedExpiredAccountNum),
                subject.getPayerNumForLastClassified());
        // and:
        assertEquals(expiredAccountNonZeroBalance, subject.getLastClassified());
    }

    @Test
    void checksForValidPayer() throws NegativeAccountBalanceException {
        solventAutoRenewAccount.setDeleted(true);
        solventAutoRenewAccount.setBalance(200L);
        assertFalse(subject.isValid(solventAutoRenewAccount));

        solventAutoRenewAccount.setDeleted(false);
        solventAutoRenewAccount.setBalance(0L);
        assertFalse(subject.isValid(solventAutoRenewAccount));

        assertFalse(subject.isValid(null));

        solventAutoRenewAccount.setDeleted(false);
        solventAutoRenewAccount.setBalance(200L);
        assertTrue(subject.isValid(solventAutoRenewAccount));
    }

    private void givenPresent(final long num, final MerkleAccount account) {
        givenPresent(num, account, false);
    }

    private void givenPresent(long num, MerkleAccount account, boolean modifiable) {
        var key = EntityNum.fromLong(num);
        given(accounts.get(key)).willReturn(account);
        if (modifiable) {
            given(accounts.getForModify(key)).willReturn(account);
        }
    }

    private final Instant now = Instant.ofEpochSecond(1_234_567L);
    private final long nonZeroBalance = 1L;
    private final MockGlobalDynamicProps dynamicProps = new MockGlobalDynamicProps();

    private final MerkleAccount nonExpiredAccount =
            MerkleAccountFactory.newAccount()
                    .balance(10)
                    .expirationTime(now.getEpochSecond() + 1)
                    .alias(ByteString.copyFromUtf8("aaaa"))
                    .get();
    private final MerkleAccount solventAutoRenewAccount =
            MerkleAccountFactory.newAccount()
                    .balance(10)
                    .expirationTime(now.getEpochSecond() + 1)
                    .alias(ByteString.copyFromUtf8("aaaa"))
                    .get();
    private final MerkleAccount expiredContractWithAutoRenew =
            MerkleAccountFactory.newAccount()
                    .isSmartContract(true)
                    .balance(10)
                    .expirationTime(now.getEpochSecond() + 1)
                    .alias(ByteString.copyFromUtf8("aaaa"))
                    .autoRenewAccount(EntityId.fromIdentityCode(10).toGrpcAccountId())
                    .get();
    private final MerkleAccount insolventAutoRenewAccount =
            MerkleAccountFactory.newAccount()
                    .balance(0)
                    .expirationTime(now.getEpochSecond() + 1)
                    .alias(ByteString.copyFromUtf8("aaaa"))
                    .get();
    private final MerkleAccount expiredAccountZeroBalance =
            MerkleAccountFactory.newAccount()
                    .balance(0)
                    .expirationTime(now.getEpochSecond() - 1)
                    .alias(ByteString.copyFromUtf8("bbbb"))
                    .get();
    private final MerkleAccount expiredContractZeroBalance =
            MerkleAccountFactory.newAccount()
                    .isSmartContract(true)
                    .balance(0)
                    .expirationTime(now.getEpochSecond() - 1)
                    .alias(ByteString.copyFromUtf8("bbbb"))
                    .get();
    private final MerkleAccount expiredDeletedAccount =
            MerkleAccountFactory.newAccount()
                    .balance(0)
                    .deleted(true)
                    .alias(ByteString.copyFromUtf8("cccc"))
                    .expirationTime(now.getEpochSecond() - 1)
                    .get();
    private final MerkleAccount expiredDeletedContract =
            MerkleAccountFactory.newAccount()
                    .isSmartContract(true)
                    .balance(0)
                    .deleted(true)
                    .alias(ByteString.copyFromUtf8("cccc"))
                    .expirationTime(now.getEpochSecond() - 1)
                    .get();
    private final MerkleAccount expiredAccountNonZeroBalance =
            MerkleAccountFactory.newAccount()
                    .balance(nonZeroBalance)
                    .expirationTime(now.getEpochSecond() - 1)
                    .alias(ByteString.copyFromUtf8("dddd"))
                    .get();
    private final MerkleAccount contractAccount =
            MerkleAccountFactory.newAccount()
                    .isSmartContract(true)
                    .balance(1)
                    .expirationTime(now.getEpochSecond() - 1)
                    .get();
    private final long nonExpiredAccountNum = 1L;
    private final long brokeExpiredNum = 2L;
    private final long fundedExpiredAccountNum = 3L;
    private final long autoRenewNum = 10L;
}

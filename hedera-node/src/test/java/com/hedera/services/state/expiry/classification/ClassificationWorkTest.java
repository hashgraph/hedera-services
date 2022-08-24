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

import static com.hedera.services.state.expiry.classification.ClassificationResult.DETACHED_ACCOUNT;
import static com.hedera.services.state.expiry.classification.ClassificationResult.DETACHED_ACCOUNT_GRACE_PERIOD_OVER;
import static com.hedera.services.state.expiry.classification.ClassificationResult.DETACHED_CONTRACT;
import static com.hedera.services.state.expiry.classification.ClassificationResult.DETACHED_CONTRACT_GRACE_PERIOD_OVER;
import static com.hedera.services.state.expiry.classification.ClassificationResult.DETACHED_TREASURY_GRACE_PERIOD_OVER_BEFORE_TOKEN;
import static com.hedera.services.state.expiry.classification.ClassificationResult.EXPIRED_ACCOUNT_READY_TO_RENEW;
import static com.hedera.services.state.expiry.classification.ClassificationResult.EXPIRED_CONTRACT_READY_TO_RENEW;
import static com.hedera.services.state.expiry.classification.ClassificationResult.OTHER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClassificationWorkTest {
    @Mock private MerkleMap<EntityNum, MerkleAccount> accounts;

    private EntityLookup lookup;
    private ClassificationWork subject;

    @BeforeEach
    void setUp() {
        lookup = new EntityLookup(() -> accounts);
        subject = new ClassificationWork(dynamicProps, lookup);
    }

    @Test
    void classifiesNonAccount() {
        // expect:
        assertEquals(OTHER, subject.classify(EntityNum.fromLong(4L), now));
    }

    @Test
    void classifiesNonExpiredAccount() {
        givenPresent(nonExpiredAccountNum, nonExpiredAccount);

        // expect:
        assertEquals(OTHER, subject.classify(EntityNum.fromLong(nonExpiredAccountNum), now));
    }

    @Test
    void classifiesNonExpiredContract() {
        givenPresent(nonExpiredAccountNum, contractAccount);

        // expect:
        assertEquals(
                EXPIRED_CONTRACT_READY_TO_RENEW,
                subject.classify(EntityNum.fromLong(nonExpiredAccountNum), now));
    }

    @Test
    void classifiesDeletedAccountAfterExpiration() {
        givenPresent(brokeExpiredNum, expiredDeletedAccount);

        // expect:
        assertEquals(
                DETACHED_ACCOUNT_GRACE_PERIOD_OVER,
                subject.classify(EntityNum.fromLong(brokeExpiredNum), now));
    }

    @Test
    void classifiesDeletedContractAfterExpiration() {
        givenPresent(brokeExpiredNum, expiredDeletedContract);

        assertEquals(
                DETACHED_CONTRACT_GRACE_PERIOD_OVER,
                subject.classify(EntityNum.fromLong(brokeExpiredNum), now));
    }

    @Test
    void classifiesDetachedAccountAfterGracePeriod() {
        givenPresent(brokeExpiredNum, expiredAccountZeroBalance);

        // expect:
        assertEquals(
                DETACHED_ACCOUNT_GRACE_PERIOD_OVER,
                subject.classify(
                        EntityNum.fromLong(brokeExpiredNum),
                        now + dynamicProps.autoRenewGracePeriod()));
    }

    @Test
    void classifiesDetachedContractAfterGracePeriod() {
        givenPresent(brokeExpiredNum, expiredContractZeroBalance);

        // expect:
        assertEquals(
                DETACHED_CONTRACT_GRACE_PERIOD_OVER,
                subject.classify(
                        EntityNum.fromLong(brokeExpiredNum),
                        now + dynamicProps.autoRenewGracePeriod()));
    }

    @Test
    void classifiesDetachedAccountAfterGracePeriodAsOtherIfTokenNotYetRemoved() {
        givenPresent(brokeExpiredNum, expiredAccountZeroBalance);
        expiredAccountZeroBalance.setNumTreasuryTitles(1);

        // expect:
        assertEquals(
                DETACHED_TREASURY_GRACE_PERIOD_OVER_BEFORE_TOKEN,
                subject.classify(
                        EntityNum.fromLong(brokeExpiredNum),
                        now + dynamicProps.autoRenewGracePeriod()));
    }

    @Test
    void classifiesDetachedAccount() {
        givenPresent(brokeExpiredNum, expiredAccountZeroBalance);

        // expect:
        assertEquals(DETACHED_ACCOUNT, subject.classify(EntityNum.fromLong(brokeExpiredNum), now));
    }

    @Test
    void classifiesDetachedContract() {
        givenPresent(brokeExpiredNum, expiredContractZeroBalance);

        // expect:
        assertEquals(DETACHED_CONTRACT, subject.classify(EntityNum.fromLong(brokeExpiredNum), now));
    }

    @Test
    void classifiesFundedExpiredAccount() {
        givenPresent(fundedExpiredAccountNum, expiredAccountNonZeroBalance);

        // expect:
        assertEquals(
                EXPIRED_ACCOUNT_READY_TO_RENEW,
                subject.classify(EntityNum.fromLong(fundedExpiredAccountNum), now));
        // and:
        assertEquals(expiredAccountNonZeroBalance, subject.getLastClassified());
    }

    @Test
    void fallsBackToContractIfAutoRenewAccountIsInvalid() {
        var key = EntityNum.fromLong(nonExpiredAccountNum);
        var autoRenewAccount = EntityNum.fromLong(10L);
        givenPresent(nonExpiredAccountNum, nonExpiredAccount, false);
        given(accounts.get(autoRenewAccount)).willReturn(autoRenewMerkleAccountZeroBalance);
        given(accounts.get(key)).willReturn(nonExpiredAccountWithAutoRenew);

        subject.classify(EntityNum.fromLong(nonExpiredAccountNum), now);

        var payer = subject.resolvePayerForAutoRenew();
        assertEquals(payer, nonExpiredAccountWithAutoRenew);

        given(accounts.get(autoRenewAccount)).willReturn(autoRenewMerkleAccount);
        payer = subject.resolvePayerForAutoRenew();
        assertEquals(payer, autoRenewMerkleAccount);
    }

    @Test
    void checksForValidPayer() throws NegativeAccountBalanceException {
        autoRenewMerkleAccount.setDeleted(true);
        autoRenewMerkleAccount.setBalance(200L);
        assertFalse(subject.isValid(autoRenewMerkleAccount));

        autoRenewMerkleAccount.setDeleted(false);
        autoRenewMerkleAccount.setBalance(0L);
        assertFalse(subject.isValid(autoRenewMerkleAccount));

        assertFalse(subject.isValid(null));

        autoRenewMerkleAccount.setDeleted(false);
        autoRenewMerkleAccount.setBalance(200L);
        assertTrue(subject.isValid(autoRenewMerkleAccount));
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
    private final MockGlobalDynamicProps dynamicProps = new MockGlobalDynamicProps();

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
    private final MerkleAccount autoRenewMerkleAccountZeroBalance =
            MerkleAccountFactory.newAccount()
                    .balance(0)
                    .expirationTime(now + 1)
                    .alias(ByteString.copyFromUtf8("aaaa"))
                    .get();
    private final MerkleAccount expiredAccountZeroBalance =
            MerkleAccountFactory.newAccount()
                    .balance(0)
                    .expirationTime(now - 1)
                    .alias(ByteString.copyFromUtf8("bbbb"))
                    .get();
    private final MerkleAccount expiredContractZeroBalance =
            MerkleAccountFactory.newAccount()
                    .isSmartContract(true)
                    .balance(0)
                    .expirationTime(now - 1)
                    .alias(ByteString.copyFromUtf8("bbbb"))
                    .get();
    private final MerkleAccount expiredDeletedAccount =
            MerkleAccountFactory.newAccount()
                    .balance(0)
                    .deleted(true)
                    .alias(ByteString.copyFromUtf8("cccc"))
                    .expirationTime(now - 1)
                    .get();
    private final MerkleAccount expiredDeletedContract =
            MerkleAccountFactory.newAccount()
                    .isSmartContract(true)
                    .balance(0)
                    .deleted(true)
                    .alias(ByteString.copyFromUtf8("cccc"))
                    .expirationTime(now - 1)
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

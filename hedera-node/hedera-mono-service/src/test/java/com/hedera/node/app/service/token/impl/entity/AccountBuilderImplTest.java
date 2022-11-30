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
package com.hedera.node.app.service.token.impl.entity;

import static com.hedera.node.app.Utils.asHederaKey;
import static com.hedera.node.app.service.token.entity.Account.HBARS_TO_TINYBARS;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.node.app.service.token.entity.AccountBuilder;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.services.utils.KeyUtils;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountBuilderImplTest {
    private AccountBuilder subject;
    private final HederaKey key = asHederaKey(KeyUtils.A_COMPLEX_KEY).get();

    @BeforeEach
    void setUp() {
        subject = new AccountBuilderImpl(setUpAccount());
    }

    @Test
    void constructorWorks() {
        assertNotNull(subject);
        final var account = subject.build();

        assertEquals(2, account.accountNumber());
        assertEquals(Optional.empty(), account.alias());
        assertEquals(key, account.key().get());
        assertEquals(123456789L, account.expiry());
        assertEquals(20000000000L, account.balanceInTinyBar());
        assertEquals(20000000000L / HBARS_TO_TINYBARS, account.balanceInHbar());
        assertEquals("test", account.memo());
        assertTrue(account.isDeleted());
        assertTrue(account.isSmartContract());
        assertTrue(account.isReceiverSigRequired());
        assertEquals(100, account.numberOfOwnedNfts());
        assertEquals(200, account.maxAutoAssociations());
        assertEquals(10, account.usedAutoAssociations());
        assertEquals(20, account.numAssociations());
        assertEquals(10, account.numPositiveBalances());
        assertEquals(20, account.ethereumNonce());
        assertEquals(1000000L, account.stakedToMe());
        assertEquals(123456L, account.stakePeriodStart());
        assertEquals(2, account.stakedNum());
        assertFalse(account.declineReward());
        assertEquals(1000L, account.stakeAtStartOfLastRewardedPeriod());
        assertEquals(3000L, account.autoRenewAccountNumber());
        assertEquals(360000, account.autoRenewSecs());
    }

    @Test
    void checksBalance() {
        assertThrows(IllegalArgumentException.class, () -> subject.balance(-1L).build());
        assertThrows(
                IllegalArgumentException.class, () -> subject.balance(50_000_000_0000L).build());
    }

    @Test
    void settersWork() {
        final var newKey = asHederaKey(KeyUtils.A_COMPLEX_KEY).get();
        subject.key(newKey);
        subject.expiry(1234567890L);
        subject.balance(40000000000L);
        subject.memo("test2");
        subject.deleted(false);
        subject.receiverSigRequired(false);
        subject.numberOfOwnedNfts(200);
        subject.maxAutoAssociations(400);
        subject.maxAutoAssociations(400);
        subject.usedAutoAssociations(20);
        subject.numAssociations(40);
        subject.numPositiveBalances(20);
        subject.ethereumNonce(40);
        subject.stakedToMe(2000000L);
        subject.stakePeriodStart(1234567L);
        subject.stakedNum(3);
        subject.declineReward(true);
        subject.stakeAtStartOfLastRewardedPeriod(10000L);
        subject.autoRenewAccountNumber(30000L);
        subject.autoRenewSecs(3600000);

        final var account = subject.build();
        assertEquals(2, account.accountNumber());
        assertEquals(Optional.empty(), account.alias());
        assertEquals(newKey, account.key().get());
        assertEquals(1234567890L, account.expiry());
        assertEquals(40000000000L, account.balanceInTinyBar());
        assertEquals(40000000000L / HBARS_TO_TINYBARS, account.balanceInHbar());
        assertEquals("test2", account.memo());
        assertFalse(account.isDeleted());
        assertTrue(account.isSmartContract());
        assertFalse(account.isReceiverSigRequired());
        assertEquals(200, account.numberOfOwnedNfts());
        assertEquals(400, account.maxAutoAssociations());
        assertEquals(20, account.usedAutoAssociations());
        assertEquals(40, account.numAssociations());
        assertEquals(20, account.numPositiveBalances());
        assertEquals(40, account.ethereumNonce());
        assertEquals(2000000L, account.stakedToMe());
        assertEquals(1234567L, account.stakePeriodStart());
        assertEquals(3, account.stakedNum());
        assertTrue(account.declineReward());
        assertEquals(10000L, account.stakeAtStartOfLastRewardedPeriod());
        assertEquals(30000L, account.autoRenewAccountNumber());
        assertEquals(3600000, account.autoRenewSecs());
    }

    private AccountImpl setUpAccount() {
        return new AccountImpl(
                2,
                Optional.empty(),
                Optional.of(key),
                123456789L,
                20000000000L,
                "test",
                true,
                true,
                true,
                100,
                200,
                10,
                20,
                10,
                20,
                1000000L,
                123456L,
                2,
                false,
                1000L,
                3000L,
                360000);
    }
}

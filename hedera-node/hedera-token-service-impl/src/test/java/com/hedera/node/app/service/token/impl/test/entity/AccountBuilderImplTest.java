/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.test.entity;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.node.app.spi.accounts.Account.HBARS_TO_TINYBARS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.service.token.impl.entity.AccountBuilderImpl;
import com.hedera.node.app.service.token.impl.entity.AccountImpl;
import com.hedera.node.app.spi.accounts.AccountBuilder;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountBuilderImplTest {
    private AccountBuilder subject;
    public static Key A_CONTRACT_KEY = Key.newBuilder()
            .contractID(ContractID.newBuilder()
                    .shardNum(1L)
                    .realmNum(1L)
                    .contractNum(3L)
                    .build())
            .build();
    private final HederaKey key = asHederaKey(A_CONTRACT_KEY).get();

    @BeforeEach
    void setUp() {
        subject = new AccountBuilderImpl(setUpAccount());
    }

    @Test
    void constructorWorks() {
        assertNotNull(subject);
        final var account = subject.build();

        assertEquals(2, account.accountNumber());
        assertEquals(Bytes.EMPTY, account.alias());
        assertEquals(key, account.getKey());
        assertEquals(123456789L, account.expiry());
        assertEquals(20_000_000_000L, account.balanceInTinyBar());
        assertEquals(20_000_000_000L / HBARS_TO_TINYBARS, account.balanceInHbar());
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
        assertEquals(1_000_000L, account.stakedToMe());
        assertEquals(123_456L, account.stakePeriodStart());
        assertEquals(2, account.stakedNum());
        assertFalse(account.declineReward());
        assertEquals(1_000L, account.stakeAtStartOfLastRewardedPeriod());
        assertEquals(3_000L, account.autoRenewAccountNumber());
        assertEquals(360_000, account.autoRenewSecs());
    }

    @Test
    void checksBalance() {
        assertThrows(IllegalArgumentException.class, () -> subject.balance(-1L));
        assertThrows(IllegalArgumentException.class, () -> subject.balance(50_000_000_0000L * HBARS_TO_TINYBARS));
    }

    @Test
    void defaultConstructorWorks() {
        subject = new AccountBuilderImpl();
        assertEquals(Bytes.EMPTY, subject.build().alias());
    }

    @Test
    void settersWork() {
        final var newKey = asHederaKey(A_CONTRACT_KEY).get();
        subject.key(newKey);
        subject.expiry(1_234_567_890L);
        subject.balance(40_000_000_000L);
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
        subject.stakedToMe(2_000_000L);
        subject.stakePeriodStart(1_234_567L);
        subject.stakedNum(3);
        subject.declineReward(true);
        subject.stakeAtStartOfLastRewardedPeriod(10_000L);
        subject.autoRenewAccountNumber(30_000L);
        subject.autoRenewSecs(3_600_000);
        subject.accountNumber(20L);
        subject.alias(new byte[10]);
        subject.isSmartContract(false);

        final var account = subject.build();
        assertEquals(20L, account.accountNumber());
        assertEquals(Bytes.wrap(new byte[10]), account.alias());
        assertEquals(newKey, account.getKey());
        assertEquals(1_234_567_890L, account.expiry());
        assertEquals(40_000_000_000L, account.balanceInTinyBar());
        assertEquals(40_000_000_000L / HBARS_TO_TINYBARS, account.balanceInHbar());
        assertEquals("test2", account.memo());
        assertFalse(account.isDeleted());
        assertFalse(account.isSmartContract());
        assertFalse(account.isReceiverSigRequired());
        assertEquals(200, account.numberOfOwnedNfts());
        assertEquals(400, account.maxAutoAssociations());
        assertEquals(20, account.usedAutoAssociations());
        assertEquals(40, account.numAssociations());
        assertEquals(20, account.numPositiveBalances());
        assertEquals(40, account.ethereumNonce());
        assertEquals(2_000_000L, account.stakedToMe());
        assertEquals(1_234_567L, account.stakePeriodStart());
        assertEquals(3, account.stakedNum());
        assertTrue(account.declineReward());
        assertEquals(10_000L, account.stakeAtStartOfLastRewardedPeriod());
        assertEquals(30_000L, account.autoRenewAccountNumber());
        assertEquals(3_600_000, account.autoRenewSecs());
    }

    private AccountImpl setUpAccount() {
        return new AccountImpl(
                2,
                Bytes.EMPTY,
                key,
                12_3456_789L,
                20_000_000_000L,
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
                1_000_000L,
                123_456L,
                2,
                false,
                1_000L,
                3_000L,
                360_000);
    }
}

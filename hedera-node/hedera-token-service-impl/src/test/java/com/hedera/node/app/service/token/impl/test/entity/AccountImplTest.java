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
package com.hedera.node.app.service.token.impl.test.entity;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.node.app.service.token.entity.Account.HBARS_TO_TINYBARS;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.token.impl.entity.AccountBuilderImpl;
import com.hedera.node.app.service.token.impl.entity.AccountImpl;
import com.hedera.node.app.spi.key.HederaKey;
import com.hederahashgraph.api.proto.java.Key;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountImplTest {
    private AccountImpl subject;
    private final HederaKey key =
            asHederaKey(
                            Key.newBuilder()
                                    .setEd25519(
                                            ByteString.copyFromUtf8(
                                                    "01234567890123456789012345678911"))
                                    .build())
                    .get();

    @BeforeEach
    void setUp() {
        subject = setUpAccount();
    }

    @Test
    void equalsWorks() {
        final var o1 = subject;
        final var o2 = o1.copy().build();
        final var o3 = o1.copy().memo("test1").build();
        assertEquals(o1, o1);
        assertEquals(o1, o2);
        assertNotEquals(o1, o3);
        assertNotEquals(null, o1);
        assertNotNull(o1);
        assertNotNull(o2);
    }

    @Test
    void hashCodeWorks() {
        assertEquals(12_1250_3389, subject.hashCode());
    }

    @Test
    void toStringWorks() {
        final var actual = subject.toString();
        final var expected =
                "AccountImpl[accountNumber=2,alias=Optional.empty,key=<JEd25519Key: ed25519"
                    + " hex=3031323334353637383930313233343536373839303132333435363738393131>,expiry=123456789,balance=20000000000,memo=test,isDeleted=true,isSmartContract=true,isReceiverSigRequired=true,numberOfOwnedNfts=100,maxAutoAssociations=200,usedAutoAssociations=10,numAssociations=20,numPositiveBalances=10,ethereumNonce=20,stakedToMe=1000000,stakePeriodStart=123456,stakedNum=2,declineReward=false,stakeAtStartOfLastRewardedPeriod=1000,autoRenewAccountNumber=3000,autoRenewSecs=360000]";
        assertEquals(expected, actual);
    }

    @Test
    void gettersWork() {
        assertEquals(2, subject.accountNumber());
        assertEquals(Optional.empty(), subject.alias());
        assertEquals(key, subject.getKey().get());
        assertEquals(123_456_789L, subject.expiry());
        assertEquals(20_000_000_000L, subject.balance());
        assertEquals("test", subject.memo());
        assertTrue(subject.isDeleted());
        assertTrue(subject.isSmartContract());
        assertTrue(subject.isReceiverSigRequired());
        assertEquals(100, subject.numberOfOwnedNfts());
        assertEquals(200, subject.maxAutoAssociations());
        assertEquals(10, subject.usedAutoAssociations());
        assertEquals(20, subject.numAssociations());
        assertEquals(10, subject.numPositiveBalances());
        assertEquals(20, subject.ethereumNonce());
        assertEquals(1_000_000L, subject.stakedToMe());
        assertEquals(123_456L, subject.stakePeriodStart());
        assertEquals(2, subject.stakedNum());
        assertFalse(subject.declineReward());
        assertEquals(1_000L, subject.stakeAtStartOfLastRewardedPeriod());
        assertEquals(3_000L, subject.autoRenewAccountNumber());
        assertEquals(360_000, subject.autoRenewSecs());
        assertEquals(0, subject.shardNumber());
        assertEquals(0, subject.realmNumber());
        assertFalse(subject.isHollow());
    }

    @Test
    void gettersWorkForDefaultAccount() {
        subject = (AccountImpl) new AccountBuilderImpl().build();
        assertTrue(subject.isHollow());
    }

    @Test
    void convertsTinybarToHbar() {
        assertEquals(subject.balanceInTinyBar() / HBARS_TO_TINYBARS, subject.balanceInHbar());
    }

    private AccountImpl setUpAccount() {
        return new AccountImpl(
                2,
                Optional.empty(),
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

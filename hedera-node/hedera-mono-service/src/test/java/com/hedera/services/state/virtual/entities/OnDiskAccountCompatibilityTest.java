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
package com.hedera.services.state.virtual.entities;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.state.merkle.internals.BitPackUtils;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.test.utils.SeededPropertySource;
import java.util.SortedMap;
import org.junit.jupiter.api.Test;

class OnDiskAccountCompatibilityTest {
    private final OnDiskAccount subject = new OnDiskAccount();

    @Test
    void tokenTreasuryIsKnowable() {
        assertFalse(subject.isTokenTreasury());
        subject.setNumTreasuryTitles(1);
        assertTrue(subject.isTokenTreasury());
    }

    @Test
    void notContractByDefault() {
        assertFalse(subject.isSmartContract());
        subject.setIsContract(true);
        assertTrue(subject.isSmartContract());
    }

    @Test
    void canReportHeadNftInfoFromHederaInterface() {
        final var nftId = 1234L;
        final var serialNo = 5678L;
        subject.setHeadNftId(nftId);
        subject.setHeadNftSerialNum(serialNo);
        final var expected = EntityNumPair.fromLongs(nftId, serialNo);
        assertEquals(expected, subject.getHeadNftKey());
    }

    @Test
    void canReportLatestAssociationFromHederaInterface() {
        final var num = 666L;
        final var tokenId = 1234L;
        subject.setHeadTokenId(tokenId);
        subject.setAccountNumber(num);
        final var expected = EntityNumPair.fromLongs(num, tokenId);
        assertEquals(expected, subject.getLatestAssociation());
    }

    @Test
    void cannotSetNegativeBalance() {
        assertThrows(NegativeAccountBalanceException.class, () -> subject.setBalance(-1L));
        assertThrows(IllegalArgumentException.class, () -> subject.setBalanceUnchecked(-1L));
    }

    @Test
    void canSetPositiveBalance() throws NegativeAccountBalanceException {
        subject.setBalance(+1L);
        assertEquals(1L, subject.getBalance());
        subject.setBalanceUnchecked(+2L);
        assertEquals(2L, subject.getBalance());
    }

    @Test
    void understandsEntityNumCode() {
        final var num = Integer.MAX_VALUE + 1L;
        subject.setAccountNumber(num);
        final var expected = BitPackUtils.codeFromNum(num);
        assertEquals(expected, subject.number());
    }

    @Test
    void proxyAlwaysMissing() {
        assertEquals(EntityId.MISSING_ENTITY_ID, subject.getProxy());
        subject.setProxy(new EntityId(0L, 0L, 666L));
        assertEquals(EntityId.MISSING_ENTITY_ID, subject.getProxy());
    }

    @Test
    void canGetMaxAutoAssociationsViaHederaInterface() {
        subject.setMaxAutoAssociations(123);
        assertEquals(123, subject.getMaxAutomaticAssociations());
    }

    @Test
    void canSetUsedAutoAssociationsViaHederaInterface() {
        subject.setUsedAutomaticAssociations(123);
        assertEquals(123, subject.getUsedAutoAssociations());
    }

    @Test
    void firstStorageKeyIsNullTilSet() {
        final var pretend = new int[] {1, 2, 3, 4, 5, 6, 7, 8};
        final var alsoPretend = new int[] {6, 6, 6, 6, 6, 6, 6, 6};
        assertNull(subject.getFirstContractStorageKey());
        subject.setFirstStorageKey(pretend);
        assertArrayEquals(pretend, subject.getFirstContractStorageKey().getKey());
        assertArrayEquals(pretend, subject.getFirstUint256Key());
        subject.setFirstUint256StorageKey(alsoPretend);
        assertArrayEquals(alsoPretend, subject.getFirstUint256Key());
    }

    @Test
    void approvalsManageableViaHederaInterface() {
        final var firstSubject = SeededPropertySource.forSerdeTest(1, 1).nextOnDiskAccount();
        final var secondSubject = SeededPropertySource.forSerdeTest(1, 3).nextOnDiskAccount();

        assertSame(firstSubject.getHbarAllowances(), firstSubject.getCryptoAllowances());
        assertSame(firstSubject.getHbarAllowances(), firstSubject.getCryptoAllowancesUnsafe());
        assertSame(firstSubject.getFungibleAllowances(), firstSubject.getFungibleTokenAllowances());
        assertSame(
                firstSubject.getFungibleAllowances(),
                firstSubject.getFungibleTokenAllowancesUnsafe());
        assertSame(firstSubject.getNftOperatorApprovals(), firstSubject.getApproveForAllNfts());
        assertSame(
                firstSubject.getNftOperatorApprovals(), firstSubject.getApproveForAllNftsUnsafe());

        firstSubject.setCryptoAllowances(
                (SortedMap<EntityNum, Long>) secondSubject.getHbarAllowances());
        firstSubject.setCryptoAllowancesUnsafe(secondSubject.getHbarAllowances());
        assertSame(secondSubject.getHbarAllowances(), firstSubject.getCryptoAllowances());

        firstSubject.setFungibleTokenAllowances(
                (SortedMap<FcTokenAllowanceId, Long>) secondSubject.getFungibleAllowances());
        firstSubject.setFungibleTokenAllowancesUnsafe(secondSubject.getFungibleAllowances());
        assertSame(secondSubject.getFungibleAllowances(), firstSubject.getFungibleAllowances());

        firstSubject.setApproveForAllNfts(secondSubject.getApproveForAllNfts());
        assertSame(secondSubject.getNftOperatorApprovals(), firstSubject.getNftOperatorApprovals());
    }

    @Test
    void stakingMetaAvailableFromHederaInterface() {
        assertFalse(subject.hasBeenRewardedSinceLastStakeMetaChange());
        subject.setStakeAtStartOfLastRewardedPeriod(123L);
        assertTrue(subject.hasBeenRewardedSinceLastStakeMetaChange());

        assertEquals(123L, subject.totalStakeAtStartOfLastRewardedPeriod());

        subject.setBalanceUnchecked(100L);
        subject.setStakedToMe(200L);
        assertEquals(300L, subject.totalStake());

        assertFalse(subject.mayHavePendingReward());
        subject.setStakedId(-6L);
        assertEquals(-6L, subject.getStakedId());
        assertTrue(subject.mayHavePendingReward());
        assertEquals(5L, subject.getStakedNodeAddressBookId());
        subject.setStakedId(+6L);
        assertThrows(IllegalStateException.class, subject::getStakedNodeAddressBookId);

        subject.setDeclineReward(true);
        assertFalse(subject.mayHavePendingReward());
    }

    @Test
    void canManageAlias() {
        assertFalse(subject.hasAlias());
        subject.setAlias(ByteString.copyFromUtf8("PRETEND"));
        assertTrue(subject.hasAlias());
    }

    @Test
    void canManageAutoRenewAccount() {
        final var someId = new EntityId(0, 0, 1234L);
        assertFalse(subject.hasAutoRenewAccount());
        assertNull(subject.getAutoRenewAccount());
        subject.setAutoRenewAccountNumber(1234L);
        assertTrue(subject.hasAutoRenewAccount());
        assertEquals(someId, subject.getAutoRenewAccount());

        subject.setAutoRenewAccount(null);
        assertEquals(0L, subject.getAutoRenewAccountNumber());
        subject.setAutoRenewAccount(someId);
        assertEquals(1234L, subject.getAutoRenewAccountNumber());
    }
}

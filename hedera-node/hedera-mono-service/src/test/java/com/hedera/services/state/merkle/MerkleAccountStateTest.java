/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.merkle;

import static com.hedera.services.state.virtual.KeyPackingUtils.readableContractStorageKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.swirlds.common.exceptions.MutabilityException;
import java.util.Collections;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MerkleAccountStateTest {
    private static final JKey key = new JEd25519Key("abcdefghijklmnopqrstuvwxyz012345".getBytes());
    private static final long expiry = 1_234_567L;
    private static final long balance = 555_555L;
    private static final long ethereumNonce = 0L;
    private static final long autoRenewSecs = 234_567L;
    private static final long nftsOwned = 150L;
    private static final long otherNftsOwned = 151L;
    private static final String memo = "A memo";
    private static final boolean deleted = true;
    private static final boolean smartContract = true;
    private static final boolean receiverSigRequired = true;
    private static final EntityId proxy = new EntityId(1L, 2L, 3L);
    private static final int number = 123;
    private static final int maxAutoAssociations = 1234;
    private static final int usedAutoAssociations = 1233;
    private static final Key aliasKey =
            Key.newBuilder()
                    .setECDSASecp256K1(ByteString.copyFromUtf8("bbbbbbbbbbbbbbbbbbbbb"))
                    .build();
    private static final ByteString alias = aliasKey.getECDSASecp256K1();
    private static final ByteString otherAlias = ByteString.copyFrom("012345789".getBytes());
    private static final UInt256 firstKey =
            UInt256.fromHexString(
                    "0x0000fe0432ce31138ecf09aa3e8a410004a1e204ef84efe01ee160fea1e22060");
    private static final int[] explicitFirstKey = ContractKey.asPackedInts(firstKey);
    private static final byte numNonZeroBytesInFirst = 30;

    private static final JKey otherKey =
            new JEd25519Key("aBcDeFgHiJkLmNoPqRsTuVwXyZ012345".getBytes());
    private static final long otherExpiry = 7_234_567L;
    private static final long otherBalance = 666_666L;
    private static final long otherAutoRenewSecs = 432_765L;
    private static final String otherMemo = "Another memo";
    private static final boolean otherDeleted = false;
    private static final boolean otherSmartContract = false;
    private static final boolean otherReceiverSigRequired = false;
    private static final EntityId otherProxy = new EntityId(3L, 2L, 1L);
    private static final EntityId autoRenewAccountId = new EntityId(4L, 5L, 6L);
    private static final int otherNumber = 456;
    private static final int kvPairs = 123;
    private static final int otherKvPairs = 456;
    private static final UInt256 otherFirstKey =
            UInt256.fromHexString(
                    "0x0011fe0432ce31138ecf09aa3e8a410004bbe204ef84efe01ee160febbe22060");
    private static final int[] otherExplicitFirstKey = ContractKey.asPackedInts(otherFirstKey);
    private static final byte otherNumNonZeroBytesInFirst = 31;
    private static final int associatedTokensCount = 3;
    private static final int numPositiveBalances = 2;
    private static final int otherNumPositiveBalances = 3;
    private static final int numTreasuryTitles = 23;
    private static final int otherNumTreasuryTitles = 32;
    private static final long stakedToMe = 12_345L;
    private static final long otherStakedToMe = 4_567_890L;
    private static final long stakePeriodStart = 786L;
    private static final long otherStakePeriodStart = 945L;
    private static final long stakedNum = 1111L;
    private static final long otherStakedNum = 5L;
    private static final long balanceAtStartOfLastRewardedPeriod = 347_576_123L;
    private static final long otherBalanceAtStartOfLastRewardedPeriod = 678_324_546L;
    private static final boolean declineReward = false;
    private static final boolean otherDeclinedReward = true;

    private static final EntityNum spenderNum1 = EntityNum.fromLong(1000L);
    private static final EntityNum spenderNum2 = EntityNum.fromLong(3000L);
    private static final EntityNum tokenForAllowance = EntityNum.fromLong(2000L);
    private static final long headTokenNum = tokenForAllowance.longValue();
    private static final long headNftId = 4000L;
    private static final long headNftSerialNum = 1L;
    private static final Long cryptoAllowance = 10L;
    private static final Long tokenAllowanceVal = 1L;

    private static final FcTokenAllowanceId tokenAllowanceKey1 =
            FcTokenAllowanceId.from(tokenForAllowance, spenderNum1);
    private static final FcTokenAllowanceId tokenAllowanceKey2 =
            FcTokenAllowanceId.from(tokenForAllowance, spenderNum2);

    private TreeMap<EntityNum, Long> cryptoAllowances = new TreeMap<>();
    private TreeMap<FcTokenAllowanceId, Long> fungibleTokenAllowances = new TreeMap<>();
    private TreeSet<FcTokenAllowanceId> approveForAllNfts = new TreeSet<>();

    TreeMap<EntityNum, Long> otherCryptoAllowances = new TreeMap<>();
    TreeMap<FcTokenAllowanceId, Long> otherFungibleTokenAllowances = new TreeMap<>();
    TreeSet<FcTokenAllowanceId> otherApproveForAllNfts = new TreeSet<>();

    private MerkleAccountState subject;

    @BeforeEach
    void setup() {
        cryptoAllowances.put(spenderNum1, cryptoAllowance);
        approveForAllNfts.add(tokenAllowanceKey2);
        fungibleTokenAllowances.put(tokenAllowanceKey1, tokenAllowanceVal);

        subject =
                new MerkleAccountState(
                        key,
                        expiry,
                        balance,
                        autoRenewSecs,
                        memo,
                        deleted,
                        smartContract,
                        receiverSigRequired,
                        proxy,
                        number,
                        maxAutoAssociations,
                        usedAutoAssociations,
                        alias,
                        kvPairs,
                        cryptoAllowances,
                        fungibleTokenAllowances,
                        approveForAllNfts,
                        explicitFirstKey,
                        numNonZeroBytesInFirst,
                        nftsOwned,
                        associatedTokensCount,
                        numPositiveBalances,
                        headTokenNum,
                        numTreasuryTitles,
                        ethereumNonce,
                        autoRenewAccountId,
                        headNftId,
                        headNftSerialNum,
                        stakedToMe,
                        stakePeriodStart,
                        stakedNum,
                        declineReward,
                        balanceAtStartOfLastRewardedPeriod);
    }

    @Test
    void onlyHasAutoRenewAccountIfSetToNonMissing() {
        final var otherSubject = new MerkleAccountState();
        assertFalse(otherSubject.hasAutoRenewAccount());
        otherSubject.setAutoRenewAccount(EntityId.MISSING_ENTITY_ID);
        assertFalse(otherSubject.hasAutoRenewAccount());
        otherSubject.setAutoRenewAccount(autoRenewAccountId);
        assertTrue(otherSubject.hasAutoRenewAccount());
    }

    @Test
    void toStringWorks() {
        assertEquals(
                "MerkleAccountState{number=123 <-> 0.0.123, "
                        + "key="
                        + MiscUtils.describe(key)
                        + ", "
                        + "expiry="
                        + expiry
                        + ", "
                        + "balance="
                        + balance
                        + ", "
                        + "autoRenewSecs="
                        + autoRenewSecs
                        + ", "
                        + "memo="
                        + memo
                        + ", "
                        + "deleted="
                        + deleted
                        + ", "
                        + "smartContract="
                        + smartContract
                        + ", "
                        + "numContractKvPairs="
                        + kvPairs
                        + ", "
                        + "receiverSigRequired="
                        + receiverSigRequired
                        + ", "
                        + "proxy="
                        + proxy
                        + ", nftsOwned="
                        + nftsOwned
                        + ", "
                        + "alreadyUsedAutoAssociations="
                        + usedAutoAssociations
                        + ", "
                        + "maxAutoAssociations="
                        + maxAutoAssociations
                        + ", "
                        + "alias="
                        + alias.toStringUtf8()
                        + ", "
                        + "cryptoAllowances="
                        + cryptoAllowances
                        + ", "
                        + "fungibleTokenAllowances="
                        + fungibleTokenAllowances
                        + ", "
                        + "approveForAllNfts="
                        + approveForAllNfts
                        + ", "
                        + "firstContractStorageKey="
                        + readableContractStorageKey(explicitFirstKey)
                        + ", "
                        + "numAssociations="
                        + associatedTokensCount
                        + ", "
                        + "numPositiveBalances="
                        + numPositiveBalances
                        + ", "
                        + "headTokenId="
                        + headTokenNum
                        + ", "
                        + "numTreasuryTitles="
                        + numTreasuryTitles
                        + ", "
                        + "ethereumNonce="
                        + ethereumNonce
                        + ", "
                        + "autoRenewAccount="
                        + autoRenewAccountId
                        + ", "
                        + "headNftId="
                        + headNftId
                        + ", "
                        + "headNftSerialNum="
                        + headNftSerialNum
                        + ", "
                        + "stakedToMe="
                        + stakedToMe
                        + ", "
                        + "stakePeriodStart="
                        + stakePeriodStart
                        + ", "
                        + "stakedNum="
                        + stakedNum
                        + ", "
                        + "declineReward="
                        + declineReward
                        + ", "
                        + "balanceAtStartOfLastRewardedPeriod="
                        + balanceAtStartOfLastRewardedPeriod
                        + "}",
                subject.toString());
    }

    @Test
    void copyIsImmutable() {
        final var key = new JKeyList();
        final var proxy = new EntityId(0, 0, 2);

        subject.copy();

        assertThrows(MutabilityException.class, () -> subject.setHbarBalance(1L));
        assertThrows(MutabilityException.class, () -> subject.setAutoRenewSecs(1_234_567L));
        assertThrows(MutabilityException.class, () -> subject.setDeleted(true));
        assertThrows(MutabilityException.class, () -> subject.setAccountKey(key));
        assertThrows(MutabilityException.class, () -> subject.setMemo("NOPE"));
        assertThrows(MutabilityException.class, () -> subject.setSmartContract(false));
        assertThrows(MutabilityException.class, () -> subject.setReceiverSigRequired(true));
        assertThrows(MutabilityException.class, () -> subject.setExpiry(1_234_567L));
        assertThrows(MutabilityException.class, () -> subject.setNumContractKvPairs(otherKvPairs));
        assertThrows(MutabilityException.class, () -> subject.setProxy(proxy));
        assertThrows(
                MutabilityException.class,
                () -> subject.setMaxAutomaticAssociations(maxAutoAssociations));
        assertThrows(
                MutabilityException.class,
                () -> subject.setUsedAutomaticAssociations(usedAutoAssociations));
        assertThrows(
                MutabilityException.class, () -> subject.setCryptoAllowances(cryptoAllowances));
        assertThrows(
                MutabilityException.class, () -> subject.setApproveForAllNfts(approveForAllNfts));
        assertThrows(MutabilityException.class, () -> subject.setNumAssociations(5));
        assertThrows(MutabilityException.class, () -> subject.setNumPositiveBalances(5));
        assertThrows(MutabilityException.class, () -> subject.setHeadTokenId(5L));
        assertThrows(MutabilityException.class, () -> subject.setNftsOwned(nftsOwned));
        assertThrows(MutabilityException.class, () -> subject.setFirstUint256Key(explicitFirstKey));
        assertThrows(MutabilityException.class, () -> subject.setNumTreasuryTitles(1));
        assertThrows(
                MutabilityException.class,
                () -> subject.setUsedAutomaticAssociations(usedAutoAssociations));
        assertThrows(MutabilityException.class, () -> subject.setStakedToMe(otherStakedToMe));
        assertThrows(
                MutabilityException.class,
                () -> subject.setStakePeriodStart(otherStakePeriodStart));
        assertThrows(MutabilityException.class, () -> subject.setStakedNum(otherStakedNum));
        assertThrows(
                MutabilityException.class, () -> subject.setDeclineReward(otherDeclinedReward));
        assertThrows(
                MutabilityException.class,
                () ->
                        subject.setStakeAtStartOfLastRewardedPeriod(
                                otherBalanceAtStartOfLastRewardedPeriod));
    }

    @Test
    void reportsTreasuryStatus() {
        assertTrue(subject.isTokenTreasury());
        subject.setNumTreasuryTitles(0);
        assertFalse(subject.isTokenTreasury());
    }

    @Test
    void copyWorks() {
        final var copySubject = subject.copy();
        assertNotSame(copySubject, subject);
        assertEquals(subject, copySubject);
    }

    @Test
    void equalsWorksWithRadicalDifferences() {
        final var identical = subject;
        assertEquals(subject, identical);
        assertNotEquals(subject, null);
        assertNotEquals(subject, new Object());
    }

    @Test
    void equalsWorksForFirstKeyBytes() {
        final var otherSubject = subject.copy();
        otherSubject.setFirstUint256Key(otherExplicitFirstKey);

        assertNotEquals(subject, otherSubject);
    }

    @Test
    void equalsWorksForNftsOwned() {
        final var otherSubject = subject.copy();
        otherSubject.setNftsOwned(otherNftsOwned);
        assertNotEquals(subject, otherSubject);
    }

    @Test
    void equalsWorksForNumTreasuryTitles() {
        final var otherSubject = subject.copy();
        otherSubject.setNumTreasuryTitles(otherNumTreasuryTitles);
        assertNotEquals(subject, otherSubject);
    }

    @Test
    void equalsWorksForKey() {
        final var otherSubject = subject.copy();
        otherSubject.setAccountKey(otherKey);
        assertNotEquals(subject, otherSubject);
    }

    @Test
    void equalsWorksForExpiry() {
        final var otherSubject = subject.copy();
        otherSubject.setExpiry(otherExpiry);
        assertNotEquals(subject, otherSubject);
    }

    @Test
    void equalsWorksForBalance() {
        final var otherSubject = subject.copy();
        otherSubject.setHbarBalance(otherBalance);
        assertNotEquals(subject, otherSubject);
    }

    @Test
    void equalsWorksForAutoRenewSecs() {
        final var otherSubject = subject.copy();
        otherSubject.setAutoRenewSecs(otherAutoRenewSecs);
        assertNotEquals(subject, otherSubject);
    }

    @Test
    void equalsWorksForMemo() {
        final var otherSubject = subject.copy();
        otherSubject.setMemo(otherMemo);
        assertNotEquals(subject, otherSubject);
    }

    @Test
    void equalsWorksForDeleted() {
        final var otherSubject = subject.copy();
        otherSubject.setDeleted(otherDeleted);
        assertNotEquals(subject, otherSubject);
    }

    @Test
    void equalsWorksForSmartContract() {
        final var otherSubject = subject.copy();
        otherSubject.setSmartContract(otherSmartContract);
        assertNotEquals(subject, otherSubject);
    }

    @Test
    void equalsWorksForReceiverSigRequired() {
        final var otherSubject = subject.copy();
        otherSubject.setReceiverSigRequired(otherReceiverSigRequired);
        assertNotEquals(subject, otherSubject);
    }

    @Test
    void equalsWorksForNumber() {
        final var otherSubject = subject.copy();
        otherSubject.setNumber(otherNumber);
        assertNotEquals(subject, otherSubject);
    }

    @Test
    void equalsWorksForProxy() {
        final var otherSubject = subject.copy();
        otherSubject.setProxy(otherProxy);
        assertNotEquals(subject, otherSubject);
    }

    @Test
    void equalsWorksForAlias() {
        final var otherSubject = subject.copy();
        otherSubject.setAlias(otherAlias);
        assertNotEquals(subject, otherSubject);
    }

    @Test
    void equalsWorksForKvPairs() {
        final var otherSubject = subject.copy();
        otherSubject.setNumContractKvPairs(otherKvPairs);
        assertNotEquals(subject, otherSubject);
    }

    @Test
    void equalsWorksForAllowances1() {
        final EntityNum spenderNum1 = EntityNum.fromLong(100L);
        final Long cryptoAllowance = 100L;
        otherCryptoAllowances.put(spenderNum1, cryptoAllowance);
        final var otherSubject = subject.copy();
        otherSubject.setCryptoAllowances(otherCryptoAllowances);
        assertNotEquals(subject, otherSubject);
    }

    @Test
    void equalsWorksForAllowances2() {
        final EntityNum spenderNum1 = EntityNum.fromLong(100L);
        final EntityNum tokenForAllowance = EntityNum.fromLong(200L);
        final Long tokenAllowanceVal = 1L;
        final FcTokenAllowanceId tokenAllowanceKey =
                FcTokenAllowanceId.from(tokenForAllowance, spenderNum1);
        otherFungibleTokenAllowances.put(tokenAllowanceKey, tokenAllowanceVal);
        final var otherSubject = subject.copy();
        otherSubject.setFungibleTokenAllowances(otherFungibleTokenAllowances);
        assertNotEquals(subject, otherSubject);
    }

    @Test
    void equalsWorksForAllowances3() {
        final EntityNum spenderNum1 = EntityNum.fromLong(100L);
        final EntityNum tokenForAllowance = EntityNum.fromLong(200L);
        final FcTokenAllowanceId tokenAllowanceKey =
                FcTokenAllowanceId.from(tokenForAllowance, spenderNum1);
        otherApproveForAllNfts.add(tokenAllowanceKey);
        final var otherSubject = subject.copy();
        otherSubject.setApproveForAllNfts(otherApproveForAllNfts);
        assertNotEquals(subject, otherSubject);
    }

    @Test
    void equalsWorksForNumPositiveBalances() {
        final var otherSubject = subject.copy();
        otherSubject.setNumPositiveBalances(otherNumPositiveBalances);
        assertNotEquals(subject, otherSubject);
    }

    @Test
    void equalsWorksForStakedToMe() {
        final var otherSubject = subject.copy();
        otherSubject.setStakedToMe(otherStakedToMe);
        assertNotEquals(subject, otherSubject);
    }

    @Test
    void equalsWorksForStakePeriodStart() {
        final var otherSubject = subject.copy();
        otherSubject.setStakePeriodStart(otherStakePeriodStart);
        assertNotEquals(subject, otherSubject);
    }

    @Test
    void equalsWorksForStakedNum() {
        final var otherSubject = subject.copy();
        otherSubject.setStakedNum(otherStakedNum);
        assertNotEquals(subject, otherSubject);
    }

    @Test
    void equalsWorksForDeclineReward() {
        final var otherSubject = subject.copy();
        otherSubject.setDeclineReward(otherDeclinedReward);
        assertNotEquals(subject, otherSubject);
    }

    @Test
    void equalsWorksForBalanceAtStartOfLastRewardedPeriod() {
        final var otherSubject = subject.copy();
        otherSubject.setStakeAtStartOfLastRewardedPeriod(otherBalanceAtStartOfLastRewardedPeriod);
        assertNotEquals(subject, otherSubject);
    }

    @Test
    void merkleMethodsWork() {
        assertEquals(MerkleAccountState.RELEASE_0270_VERSION, subject.getVersion());
        assertEquals(MerkleAccountState.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
        assertTrue(subject.isLeaf());
    }

    @Test
    void objectContractMet() {
        final var defaultSubject = new MerkleAccountState();
        final var identicalSubject = subject.copy();
        assertNotEquals(subject.hashCode(), defaultSubject.hashCode());
        assertEquals(subject.hashCode(), identicalSubject.hashCode());
    }

    @Test
    void autoAssociationMetadataWorks() {
        final int max = 12;
        final int used = 5;
        final var defaultSubject = new MerkleAccountState();
        defaultSubject.setMaxAutomaticAssociations(max);
        defaultSubject.setUsedAutomaticAssociations(used);

        assertEquals(used, defaultSubject.getUsedAutomaticAssociations());
        assertEquals(max, defaultSubject.getMaxAutomaticAssociations());

        var toIncrement = defaultSubject.getUsedAutomaticAssociations();
        toIncrement++;

        defaultSubject.setUsedAutomaticAssociations(toIncrement);
        assertEquals(toIncrement, defaultSubject.getUsedAutomaticAssociations());

        var changeMax = max + 10;
        defaultSubject.setMaxAutomaticAssociations(changeMax);

        assertEquals(changeMax, defaultSubject.getMaxAutomaticAssociations());
    }

    @Test
    void gettersForAllowancesWork() {
        var subject = new MerkleAccountState();
        assertEquals(Collections.emptyMap(), subject.getCryptoAllowances());
        assertEquals(Collections.emptyMap(), subject.getFungibleTokenAllowances());
        assertEquals(Collections.emptySet(), subject.getApproveForAllNfts());
    }

    @Test
    void settersForAllowancesWork() {
        var subject = new MerkleAccountState();
        subject.setCryptoAllowances(cryptoAllowances);
        subject.setFungibleTokenAllowances(fungibleTokenAllowances);
        subject.setApproveForAllNfts(approveForAllNfts);
        assertEquals(cryptoAllowances, subject.getCryptoAllowances());
        assertEquals(fungibleTokenAllowances, subject.getFungibleTokenAllowances());
        assertEquals(approveForAllNfts, subject.getApproveForAllNfts());
    }

    @Test
    void gettersAndSettersForAutoRenewAccountWorks() {
        var subject = new MerkleAccountState();
        final var account = EntityId.fromIdentityCode(10);
        subject.setAutoRenewAccount(account);
        assertEquals(account, subject.getAutoRenewAccount());
    }
}

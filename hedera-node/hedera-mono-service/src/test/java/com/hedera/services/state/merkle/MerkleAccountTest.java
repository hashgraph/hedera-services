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

import static com.hedera.services.legacy.core.jproto.JKey.equalUpToDecodability;
import static com.hedera.services.state.merkle.internals.BitPackUtils.numFromCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.Key;
import com.swirlds.fcqueue.FCQueue;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MerkleAccountTest {
    private static final JKey key = new JEd25519Key("abcdefghijklmnopqrstuvwxyz012345".getBytes());
    private static final int numTreasuryTitles = 23;
    private static final long expiry = 1_234_567L;
    private static final long balance = 555_555L;
    private static final long nftsOwned = 150L;
    private static final long ethereumNonce = 1L;
    private static final long autoRenewSecs = 234_567L;
    private static final String memo = "A memo";
    private static final boolean deleted = true;
    private static final boolean smartContract = true;
    private static final boolean receiverSigRequired = true;
    private static final EntityId proxy = new EntityId(1L, 2L, 3L);
    private static final EntityId autoRenewAccountId = new EntityId(4L, 5L, 6L);
    private final int number = 123;
    private final int maxAutoAssociations = 1234;
    private final int usedAutoAssociations = 123;
    private static final Key aliasKey =
            Key.newBuilder()
                    .setECDSASecp256K1(ByteString.copyFromUtf8("bbbbbbbbbbbbbbbbbbbbb"))
                    .build();
    private static final int kvPairs = 123;
    private static final ByteString alias = aliasKey.getECDSASecp256K1();
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
    private static final long lastAssociatedTokenNum = 456;
    private static final long lastAssociatedNftNum = 4587;
    private static final long lastAssociatedNftSerial = 2;
    private static final String otherMemo = "Another memo";
    private static final boolean otherDeleted = false;
    private static final boolean otherSmartContract = false;
    private static final boolean otherReceiverSigRequired = false;
    private static final EntityId otherProxy = new EntityId(3L, 2L, 1L);
    private static final FcTokenAllowanceId tokenAllowanceKey =
            FcTokenAllowanceId.from(EntityNum.fromLong(2000L), EntityNum.fromLong(1000L));
    private static final long stakedToMe = 12_345L;
    private static final long stakePeriodStart = 786L;
    private static final long stakedNum = 1111L;
    private static final boolean declinedReward = false;
    private static final long balanceAtStartOfLastRewardedPeriod = 347_576_123L;

    private MerkleAccountState state;
    private FCQueue<ExpirableTxnRecord> payerRecords;
    private TreeMap<EntityNum, Long> cryptoAllowances;
    private TreeMap<FcTokenAllowanceId, Long> fungibleTokenAllowances;
    private TreeSet<FcTokenAllowanceId> approveForAllNfts;

    private MerkleAccountState delegate;

    private MerkleAccount subject;

    @BeforeEach
    void setup() {
        payerRecords = mock(FCQueue.class);
        given(payerRecords.copy()).willReturn(payerRecords);
        given(payerRecords.isImmutable()).willReturn(false);

        cryptoAllowances = mock(TreeMap.class);
        fungibleTokenAllowances = mock(TreeMap.class);
        approveForAllNfts = new TreeSet<>();
        approveForAllNfts.add(tokenAllowanceKey);

        delegate = mock(MerkleAccountState.class);

        state =
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
                        0,
                        0,
                        lastAssociatedTokenNum,
                        numTreasuryTitles,
                        ethereumNonce,
                        autoRenewAccountId,
                        lastAssociatedNftNum,
                        lastAssociatedNftSerial,
                        stakedToMe,
                        stakePeriodStart,
                        stakedNum,
                        declinedReward,
                        balanceAtStartOfLastRewardedPeriod);

        subject = new MerkleAccount(List.of(state, payerRecords));
    }

    @Test
    void totalStakeIsSumOfBalanceAndStakedToMe() {
        assertEquals(balance + stakedToMe, subject.totalStake());
    }

    @Test
    void equalsIncorporatesRecords() {
        final var otherRecords = mock(FCQueue.class);

        final var otherSubject = new MerkleAccount(List.of(state, otherRecords));

        assertNotEquals(otherSubject, subject);
    }

    @Test
    void namecanGetHeadNftKey() {
        final var expected = EntityNumPair.fromLongs(lastAssociatedNftNum, lastAssociatedNftSerial);
        assertEquals(expected, subject.getHeadNftKey());
    }

    @Test
    void returnsExpectedNumPayerRecords() {
        given(payerRecords.size()).willReturn(123);

        assertEquals(123, subject.numRecords());
    }

    @Test
    void returnsExpectedAutoRenewAccount() {
        final var account = EntityId.fromIdentityCode(10);
        subject.setAutoRenewAccount(account);
        assertEquals(account, subject.getAutoRenewAccount());
        assertTrue(subject.hasAutoRenewAccount());
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsExpectedRecordsIterator() {
        final Iterator<ExpirableTxnRecord> mockIter =
                (Iterator<ExpirableTxnRecord>) mock(Iterator.class);
        given(payerRecords.iterator()).willReturn(mockIter);

        assertSame(mockIter, subject.recordIterator());
    }

    @Test
    void immutableAccountThrowsIse() {
        MerkleAccount.stackDump = () -> {};
        final var original = new MerkleAccount();

        original.copy();

        assertThrows(IllegalStateException.class, () -> original.copy());

        MerkleAccount.stackDump = Thread::dumpStack;
    }

    @Test
    void merkleMethodsWork() {
        assertEquals(
                MerkleAccount.ChildIndices.NUM_POST_0240_CHILDREN, subject.getMinimumChildCount());
        assertEquals(MerkleAccount.MERKLE_VERSION, subject.getVersion());
        assertEquals(MerkleAccount.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
        assertFalse(subject.isLeaf());
    }

    @Test
    void toStringWorks() {
        given(payerRecords.size()).willReturn(3);

        assertEquals(
                "MerkleAccount{state=" + state.toString() + ", # records=" + 3 + "}",
                subject.toString());
    }

    @Test
    void gettersDelegate() {
        // expect:
        assertEquals(new EntityNum(number), subject.getKey());
        assertEquals(state.expiry(), subject.getExpiry());
        assertEquals(state.balance(), subject.getBalance());
        assertEquals(state.autoRenewSecs(), subject.getAutoRenewSecs());
        assertEquals(state.isDestroyed(), subject.isDestroyed());
        assertEquals(state.isSmartContract(), subject.isSmartContract());
        assertEquals(state.isReceiverSigRequired(), subject.isReceiverSigRequired());
        assertEquals(state.memo(), subject.getMemo());
        assertEquals(state.proxy(), subject.getProxy());
        assertTrue(equalUpToDecodability(state.key(), subject.getAccountKey()));
        assertEquals(nftsOwned, subject.getNftsOwned());
        assertEquals(state.getMaxAutomaticAssociations(), subject.getMaxAutomaticAssociations());
        assertEquals(state.getUsedAutomaticAssociations(), subject.getUsedAutoAssociations());
        assertEquals(state.getAlias(), subject.getAlias());
        assertEquals(state.getNumContractKvPairs(), subject.getNumContractKvPairs());
        assertEquals(
                state.getCryptoAllowances().entrySet(), subject.getCryptoAllowances().entrySet());
        assertEquals(
                state.getFungibleTokenAllowances().entrySet(),
                subject.getFungibleTokenAllowances().entrySet());
        final var expected = new ContractKey(numFromCode(number), explicitFirstKey);
        final var actual = subject.getFirstContractStorageKey();
        assertEquals(expected, actual);
        assertEquals(state.getApproveForAllNfts(), subject.getApproveForAllNfts());
        assertEquals(state.getNumAssociations(), subject.getNumAssociations());
        assertEquals(state.getNumPositiveBalances(), subject.getNumPositiveBalances());
        assertEquals(state.getHeadTokenId(), subject.getHeadTokenId());
        assertEquals(state.getNumTreasuryTitles(), subject.getNumTreasuryTitles());
        assertEquals(state.isTokenTreasury(), subject.isTokenTreasury());
        assertEquals(state.getStakedToMe(), subject.getStakedToMe());
        assertEquals(state.getStakePeriodStart(), subject.getStakePeriodStart());
        assertEquals(state.isDeclineReward(), subject.isDeclinedReward());
        assertEquals(state.getStakedNum(), subject.getStakedId());
        assertEquals(
                state.getStakeAtStartOfLastRewardedPeriod(),
                subject.totalStakeAtStartOfLastRewardedPeriod());
    }

    @Test
    void uncheckedSetterDelegates() {
        subject = new MerkleAccount(List.of(delegate, new FCQueue<>()));
        assertThrows(IllegalArgumentException.class, () -> subject.setBalanceUnchecked(-1L));

        subject.setBalanceUnchecked(otherBalance);

        verify(delegate).setHbarBalance(otherBalance);
    }

    @Test
    void settersDelegate() throws NegativeAccountBalanceException {
        subject = new MerkleAccount(List.of(delegate, new FCQueue<>()));
        given(delegate.getMaxAutomaticAssociations()).willReturn(maxAutoAssociations);

        subject.setExpiry(otherExpiry);
        subject.setBalance(otherBalance);
        subject.setAutoRenewSecs(otherAutoRenewSecs);
        subject.setDeleted(otherDeleted);
        subject.setSmartContract(otherSmartContract);
        subject.setReceiverSigRequired(otherReceiverSigRequired);
        subject.setMemo(otherMemo);
        subject.setProxy(otherProxy);
        subject.setAccountKey(otherKey);
        subject.setKey(new EntityNum(number));
        subject.setMaxAutomaticAssociations(maxAutoAssociations);
        subject.setUsedAutomaticAssociations(usedAutoAssociations);
        subject.setNftsOwned(2L);
        subject.setAlias(alias);
        subject.setNumContractKvPairs(kvPairs);
        subject.setCryptoAllowances(cryptoAllowances);
        subject.setFungibleTokenAllowances(fungibleTokenAllowances);
        subject.setApproveForAllNfts(approveForAllNfts);
        subject.setHeadTokenId(lastAssociatedTokenNum);
        subject.setNumPositiveBalances(0);
        subject.setNumAssociations(0);
        subject.setNumTreasuryTitles(numTreasuryTitles);
        subject.setStakedToMe(stakedToMe);
        subject.setStakePeriodStart(stakePeriodStart);
        subject.setDeclineReward(declinedReward);
        subject.setStakedId(-stakedNum);
        subject.setStakeAtStartOfLastRewardedPeriod(balanceAtStartOfLastRewardedPeriod);

        verify(delegate).setExpiry(otherExpiry);
        verify(delegate).setAutoRenewSecs(otherAutoRenewSecs);
        verify(delegate).setDeleted(otherDeleted);
        verify(delegate).setSmartContract(otherSmartContract);
        verify(delegate).setReceiverSigRequired(otherReceiverSigRequired);
        verify(delegate).setMemo(otherMemo);
        verify(delegate).setProxy(otherProxy);
        verify(delegate).setAccountKey(otherKey);
        verify(delegate).setHbarBalance(otherBalance);
        verify(delegate).setNumber(number);
        verify(delegate).setMaxAutomaticAssociations(maxAutoAssociations);
        verify(delegate).setUsedAutomaticAssociations(usedAutoAssociations);
        verify(delegate).setNumContractKvPairs(kvPairs);
        verify(delegate).setNftsOwned(2L);
        verify(delegate).setAlias(alias);
        verify(delegate).setCryptoAllowances(cryptoAllowances);
        verify(delegate).setFungibleTokenAllowances(fungibleTokenAllowances);
        verify(delegate).setApproveForAllNfts(approveForAllNfts);
        verify(delegate).setNumPositiveBalances(0);
        verify(delegate).setNumAssociations(0);
        verify(delegate).setHeadTokenId(lastAssociatedTokenNum);
        verify(delegate).setNumTreasuryTitles(numTreasuryTitles);
        verify(delegate).setStakedNum(-stakedNum);
        verify(delegate).setStakedToMe(stakedToMe);
        verify(delegate).setStakePeriodStart(stakePeriodStart);
        verify(delegate).setDeclineReward(declinedReward);
        verify(delegate).setStakeAtStartOfLastRewardedPeriod(balanceAtStartOfLastRewardedPeriod);

        subject.setStakedId(stakedNum);
        verify(delegate).setStakedNum(stakedNum);
    }

    @Test
    void isDeletedWorks() {
        subject.setDeleted(true);
        assertTrue(subject.isDeleted());
    }

    @Test
    void copyStillWorksWithPre0250() {
        final var one = new MerkleAccount();
        final var two = new MerkleAccount(List.of(state, payerRecords));
        final var three = two.copy();

        verify(payerRecords).copy();
        assertNotEquals(null, one);
        assertNotEquals(new Object(), one);
        assertNotEquals(two, one);
        assertEquals(two, three);

        assertNotEquals(one.hashCode(), two.hashCode());
        assertEquals(two.hashCode(), three.hashCode());
    }

    @Test
    void copyWorksWith0250() {
        final var two = new MerkleAccount(List.of(state, payerRecords));
        final var three = two.copy();

        verify(payerRecords).copy();
        assertEquals(two, three);
        assertEquals(two.hashCode(), three.hashCode());
    }

    @Test
    void copyConstructorFastCopiesMutableFcqs() {
        given(payerRecords.isImmutable()).willReturn(false);

        final var copy = subject.copy();

        verify(payerRecords).copy();
        assertEquals(payerRecords, copy.records());
    }

    @Test
    void throwsOnNegativeBalance() {
        assertThrows(NegativeAccountBalanceException.class, () -> subject.setBalance(-1L));
    }

    @Test
    void throwsOnInvalidAlreadyUsedAtoAssociations() {
        assertThrows(
                IllegalArgumentException.class, () -> subject.setUsedAutomaticAssociations(-1));
        assertThrows(
                IllegalArgumentException.class,
                () -> subject.setUsedAutomaticAssociations(maxAutoAssociations + 1));
    }

    @Test
    void isMutableAfterCopy() {
        subject.copy();

        assertTrue(subject.isImmutable());
    }

    @Test
    void equalsWorksWithExtremes() {
        final var sameButDifferent = subject;
        assertEquals(subject, sameButDifferent);
        assertNotEquals(null, subject);
        assertNotEquals(subject, new Object());
    }

    @Test
    void originalIsMutable() {
        assertFalse(subject.isImmutable());
    }

    @Test
    void delegatesRelease() {
        subject.release();

        verify(payerRecords).release();
    }

    @Test
    void canForgetMerkleAccountTokensPlaceholder() {
        final var normalSubject =
                new MerkleAccount(List.of(new MerkleAccountState(), new FCQueue<>()));
        normalSubject.forgetThirdChildIfPlaceholder();
        assertEquals(2, normalSubject.getNumberOfChildren());

        final var forgettableSubject =
                new MerkleAccount(
                        List.of(
                                new MerkleAccountState(),
                                new FCQueue<>(),
                                new MerkleAccountTokensPlaceholder()));
        forgettableSubject.forgetThirdChildIfPlaceholder();
        assertEquals(2, forgettableSubject.getNumberOfChildren());

        final var strangelyMemorableSubject =
                new MerkleAccount(
                        List.of(
                                new MerkleAccountState(),
                                new FCQueue<>(),
                                new MerkleAccountState()));
        strangelyMemorableSubject.forgetThirdChildIfPlaceholder();
        assertEquals(3, strangelyMemorableSubject.getNumberOfChildren());
    }

    @Test
    void checksIfMayHavePendingReward() {
        assertFalse(subject.mayHavePendingReward());

        subject.setStakedId(-1L);
        assertTrue(subject.mayHavePendingReward());

        subject.setDeclineReward(true);
        assertFalse(subject.mayHavePendingReward());
    }

    @Test
    void checksIfRewardedSinceLastStakeMetaChange() {
        state.setStakeAtStartOfLastRewardedPeriod(-1L);
        assertFalse(subject.hasBeenRewardedSinceLastStakeMetaChange());
        state.setStakeAtStartOfLastRewardedPeriod(123L);
        assertTrue(subject.hasBeenRewardedSinceLastStakeMetaChange());
    }

    @Test
    void getsNodeStakedIdCorrectly() {
        var subject = new MerkleAccount();

        subject.setStakedId(-1L);
        assertEquals(0L, subject.getStakedNodeAddressBookId());

        subject.setStakedId(1L);
        assertThrows(IllegalStateException.class, () -> subject.getStakedNodeAddressBookId());

        subject.setStakedId(0L);
        assertThrows(IllegalStateException.class, () -> subject.getStakedNodeAddressBookId());
    }
}

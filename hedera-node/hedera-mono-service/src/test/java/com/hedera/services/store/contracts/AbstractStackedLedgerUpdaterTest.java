/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts;

import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.ledger.accounts.ContractCustomizer;
import com.hedera.services.ledger.backing.HashMapBackingAccounts;
import com.hedera.services.ledger.backing.HashMapBackingNfts;
import com.hedera.services.ledger.backing.HashMapBackingTokenRels;
import com.hedera.services.ledger.backing.HashMapBackingTokens;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.migration.UniqueTokenAdapter;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractStackedLedgerUpdaterTest {
    @Mock private CodeCache codeCache;
    @Mock private EntityAccess entityAccess;
    @Mock private ContractAliases aliases;
    @Mock private HederaWorldState worldState;
    @Mock private ContractCustomizer customizer;
    @Mock private RecordsHistorian recordsHistorian;

    private WorldLedgers ledgers;
    private MockLedgerWorldUpdater wrapped;

    private AbstractStackedLedgerUpdater<HederaWorldState, Account> subject;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        setupLedgers();

        wrapped = new MockLedgerWorldUpdater(worldState, ledgers.wrapped(), customizer);

        subject = (AbstractStackedLedgerUpdater<HederaWorldState, Account>) wrapped.updater();
    }

    @Test
    void parentUpdaterIsSetForStackedUpdater() {
        final var parent = subject.parentUpdater();
        assertTrue(parent.isPresent());
        assertSame(wrapped, parent.get());
    }

    @Test
    void getForMutationReturnsTrackingAccountIfPresentInParent() {
        wrapped.createAccount(aAddress, aNonce, Wei.of(aBalance));

        assertSame(subject.getForMutation(aAddress), wrapped.updatedAccounts.get(aAddress));
    }

    @Test
    void getForMutationReturnsNullForDeletedAccount() {
        wrapped.createAccount(aAddress, aNonce, Wei.of(aBalance));
        wrapped.deleteAccount(aAddress);

        assertNull(subject.getForMutation(aAddress));
    }

    @Test
    void getForMutationPropagatesNullIfParentDoesntHave() {
        assertNull(subject.getForMutation(aAddress));
    }

    @Test
    void getForMutationWrapsParentMutable() {
        final var account =
                new WorldStateAccount(aAddress, Wei.of(aBalance), codeCache, entityAccess);
        given(worldState.get(aAddress)).willReturn(account);

        final var mutableAccount = subject.getForMutation(aAddress);
        assertSame(account, mutableAccount.getWrappedAccount());
    }

    @Test
    void revertsAsExpected() {
        subject.revert();

        assertTrackingLedgersInTxn();
    }

    @Test
    void commitsNewlyCreatedAccountAsExpected() {
        subject.createAccount(aAddress, aNonce, Wei.of(aBalance));

        subject.commit();

        assertTrackingLedgersNotInTxn();
        assertEquals(aNonce, wrapped.getAccount(aAddress).getNonce());
        assertTrue(wrapped.trackingLedgers().accounts().contains(aAccount));
        assertEquals(aBalance, wrapped.trackingLedgers().accounts().get(aAccount, BALANCE));

        final var wrappedMutableAccount = wrapped.getAccount(aAddress);
        wrappedMutableAccount.getMutable().decrementBalance(Wei.of(1));

        wrapped.commit();
        assertEquals(aBalance - 1, ledgers.accounts().get(aAccount, BALANCE));
    }

    @Test
    @SuppressWarnings("unchecked")
    void doesntAdjustBalanceOfProxyTokenAccountWrapper() {
        final var proxyAccountWrapper = mock(UpdateTrackingLedgerAccount.class);
        given(proxyAccountWrapper.wrappedAccountIsTokenProxy()).willReturn(true);
        given(proxyAccountWrapper.getAddress()).willReturn(aAddress);
        subject.updatedAccounts.put(aAddress, proxyAccountWrapper);

        assertDoesNotThrow(subject::commit);

        verify(proxyAccountWrapper, never()).getBalance();
    }

    @Test
    void commitsSourceIdIfKnown() {
        final var mySourceId = 666;
        given(recordsHistorian.nextChildRecordSourceId()).willReturn(mySourceId);
        final var aRecord = ExpirableTxnRecord.newBuilder();

        subject.manageInProgressRecord(recordsHistorian, aRecord, TransactionBody.newBuilder());

        subject.commit();

        assertEquals(List.of(mySourceId), wrapped.getCommittedRecordSourceIds());
        assertSame(recordsHistorian, wrapped.getRecordsHistorian());
    }

    @Test
    void alsoCommitsChildSourceIdsIfKnown() {
        final var firstChildId = 444;
        final var secondChildId = 555;
        final var mySourceId = 666;
        given(recordsHistorian.nextChildRecordSourceId()).willReturn(mySourceId);
        final var aRecord = ExpirableTxnRecord.newBuilder();

        subject.addCommittedRecordSourceId(firstChildId, recordsHistorian);
        subject.manageInProgressRecord(recordsHistorian, aRecord, TransactionBody.newBuilder());
        subject.addCommittedRecordSourceId(secondChildId, recordsHistorian);

        subject.commit();

        assertEquals(
                List.of(mySourceId, firstChildId, secondChildId),
                wrapped.getCommittedRecordSourceIds());
        assertSame(recordsHistorian, wrapped.getRecordsHistorian());
    }

    @Test
    void commitsNewlyModifiedAccountAsExpected() {
        final var mockCode = Bytes.ofUnsignedLong(1_234L);
        final var account =
                new WorldStateAccount(aAddress, Wei.of(aBalance), codeCache, entityAccess);
        given(worldState.get(aAddress)).willReturn(account);
        ledgers.accounts().create(aAccount);
        ledgers.accounts().set(aAccount, BALANCE, aBalance);

        final var mutableAccount = subject.getAccount(aAddress);
        mutableAccount.getMutable().decrementBalance(Wei.of(1));
        mutableAccount.getMutable().setCode(mockCode);
        mutableAccount.getMutable().clearStorage();
        subject.trackingLedgers().aliases().link(nonMirrorAddress, aAddress);
        subject.trackingLedgers().aliases().link(otherNonMirrorAddress, bAddress);

        subject.commit();

        final var wrappedMutableAccount = wrapped.getAccount(aAddress);
        assertEquals(mockCode, wrappedMutableAccount.getCode());
        wrappedMutableAccount.getMutable().decrementBalance(Wei.of(1));
        assertEquals(aBalance, ledgers.accounts().get(aAccount, BALANCE));

        wrapped.commit();
        assertEquals(aBalance - 2, ledgers.accounts().get(aAccount, BALANCE));

        verify(aliases).link(nonMirrorAddress, aAddress);
        verify(aliases, never()).link(otherNonMirrorAddress, bAddress);
    }

    private void assertTrackingLedgersInTxn() {
        assertTrackingLedgersTxnStatus(true);
    }

    private void assertTrackingLedgersNotInTxn() {
        assertTrackingLedgersTxnStatus(false);
    }

    private void assertTrackingLedgersTxnStatus(final boolean inTxn) {
        assertEquals(inTxn, subject.trackingLedgers().tokenRels().isInTransaction());
        assertEquals(inTxn, subject.trackingLedgers().accounts().isInTransaction());
        assertEquals(inTxn, subject.trackingLedgers().nfts().isInTransaction());
    }

    private void setupLedgers() {
        final var tokenRelsLedger =
                new TransactionalLedger<>(
                        TokenRelProperty.class,
                        MerkleTokenRelStatus::new,
                        new HashMapBackingTokenRels(),
                        new ChangeSummaryManager<>());
        final var accountsLedger =
                new TransactionalLedger<>(
                        AccountProperty.class,
                        MerkleAccount::new,
                        new HashMapBackingAccounts(),
                        new ChangeSummaryManager<>());
        final var nftsLedger =
                new TransactionalLedger<>(
                        NftProperty.class,
                        UniqueTokenAdapter::newEmptyMerkleToken,
                        new HashMapBackingNfts(),
                        new ChangeSummaryManager<>());
        final var tokensLedger =
                new TransactionalLedger<>(
                        TokenProperty.class,
                        MerkleToken::new,
                        new HashMapBackingTokens(),
                        new ChangeSummaryManager<>());

        tokenRelsLedger.begin();
        accountsLedger.begin();
        nftsLedger.begin();

        ledgers =
                new WorldLedgers(
                        aliases, tokenRelsLedger, accountsLedger, nftsLedger, tokensLedger);
    }

    private static final AccountID aAccount = IdUtils.asAccount("0.0.12345");
    private static final Address aAddress = EntityIdUtils.asTypedEvmAddress(aAccount);
    private static final Address bAddress = EntityNum.fromLong(54321).toEvmAddress();
    private static final long aBalance = 1_000L;
    private static final long aNonce = 1L;
    private static final long aExpiry = 1_234_567L;
    private static final long aAutoRenew = 7776000L;
    private static final byte[] rawNonMirrorAddress =
            unhex("abcdefabcdefabcdefbabcdefabcdefabcdefbbb");
    private static final Address nonMirrorAddress = Address.wrap(Bytes.wrap(rawNonMirrorAddress));
    private static final byte[] otherRawNonMirrorAddress =
            unhex("abcdecabcdecabcdecbabcdecabcdecabcdecbbb");
    private static final Address otherNonMirrorAddress =
            Address.wrap(Bytes.wrap(otherRawNonMirrorAddress));
}

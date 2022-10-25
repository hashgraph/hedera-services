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

import static com.hedera.services.ledger.properties.AccountProperty.ALIAS;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.ledger.properties.AccountProperty.NUM_NFTS_OWNED;
import static com.hedera.services.ledger.properties.NftProperty.OWNER;
import static com.hedera.services.ledger.properties.TokenRelProperty.TOKEN_BALANCE;
import static com.hedera.services.store.contracts.WorldLedgers.staticLedgersWith;
import static com.hedera.test.utils.IdUtils.asContract;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import com.google.protobuf.ByteString;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.ledger.accounts.ContractCustomizer;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.ledger.backing.HashMapBackingAccounts;
import com.hedera.services.ledger.backing.HashMapBackingNfts;
import com.hedera.services.ledger.backing.HashMapBackingTokenRels;
import com.hedera.services.ledger.backing.HashMapBackingTokens;
import com.hedera.services.ledger.interceptors.AccountsCommitInterceptor;
import com.hedera.services.ledger.interceptors.AutoAssocTokenRelsCommitInterceptor;
import com.hedera.services.ledger.interceptors.LinkAwareUniqueTokensCommitInterceptor;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.state.migration.UniqueTokenAdapter;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.SidecarUtils;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.worldstate.WrappedEvmAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractLedgerWorldUpdaterTest {
    private static final TokenID aToken = IdUtils.asToken("0.0.666");
    private static final TokenID bToken = IdUtils.asToken("0.0.777");
    private static final AccountID aAccount = IdUtils.asAccount("0.0.12345");
    private static final AccountID bAccount = IdUtils.asAccount("0.0.23456");
    private static final Pair<AccountID, TokenID> aaRel = Pair.of(aAccount, aToken);
    private static final Pair<AccountID, TokenID> abRel = Pair.of(aAccount, bToken);
    private static final Pair<AccountID, TokenID> bbRel = Pair.of(bAccount, bToken);
    private static final NftId aNft = new NftId(0, 0, 0, 3456);
    private static final NftId bNft = new NftId(0, 0, 0, 4567);
    private static final Address aAddress = EntityIdUtils.asTypedEvmAddress(aAccount);
    private static final Address bAddress = EntityIdUtils.asTypedEvmAddress(bAccount);
    private static final long aaBalance = 1L;
    private static final long bbBalance = 9L;
    private static final long aHbarBalance = 1_000L;
    private static final long bHbarBalance = 2_000L;
    private static final long aNonce = 1L;

    @Mock private CodeCache codeCache;
    @Mock private HederaWorldState worldState;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private ContractAliases aliases;
    @Mock private SideEffectsTracker sideEffectsTracker;
    @Mock private LinkAwareUniqueTokensCommitInterceptor linkAwareUniqueTokensCommitInterceptor;
    @Mock private AutoAssocTokenRelsCommitInterceptor autoAssocTokenRelsCommitInterceptor;
    @Mock private AccountsCommitInterceptor accountsCommitInterceptor;
    @Mock private ContractCustomizer customizer;
    @Mock private EntityAccess entityAccess;
    @Mock private StaticEntityAccess staticEntityAccess;
    @Mock private WorldLedgers mockLedgers;

    private WorldLedgers ledgers;
    private MockLedgerWorldUpdater subject;

    @BeforeEach
    void setUp() {
        setupLedgers();

        subject = new MockLedgerWorldUpdater(worldState, ledgers, customizer);
    }

    @Test
    void isPossibleToWrapStaticLedgers() {
        final var staticLedgers = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);
        subject = new MockLedgerWorldUpdater(worldState, staticLedgers, customizer);
        assertDoesNotThrow(() -> subject.wrappedTrackingLedgers(sideEffectsTracker));
    }

    @Test
    void usesSameSourceIdAcrossMultipleManagedRecords() {
        final var sourceId = 123;
        final var firstRecord = ExpirableTxnRecord.newBuilder();
        final var secondRecord = ExpirableTxnRecord.newBuilder();
        final var firstSynthBuilder = TransactionBody.newBuilder();
        final var secondSynthBuilder = TransactionBody.newBuilder();

        given(recordsHistorian.nextChildRecordSourceId()).willReturn(sourceId);

        subject.manageInProgressRecord(recordsHistorian, firstRecord, firstSynthBuilder);
        subject.manageInProgressRecord(recordsHistorian, secondRecord, secondSynthBuilder);

        verify(recordsHistorian, times(1)).nextChildRecordSourceId();
        verify(recordsHistorian)
                .trackFollowingChildRecord(
                        sourceId, firstSynthBuilder, firstRecord, Collections.emptyList());
        verify(recordsHistorian)
                .trackFollowingChildRecord(
                        sourceId, secondSynthBuilder, secondRecord, Collections.emptyList());
    }

    @Test
    void sidecarRecordsAreProperlyPassedToRecordHistorian() {
        // given
        final var sourceId = 123;
        given(recordsHistorian.nextChildRecordSourceId()).willReturn(sourceId);
        final var firstRecord = ExpirableTxnRecord.newBuilder();
        final var firstSynthBuilder = TransactionBody.newBuilder();
        final var contractBytecode =
                SidecarUtils.createContractBytecodeSidecarFrom(
                        asContract("0.0.666"), "bytes".getBytes(), "moreBytes".getBytes());
        final var sidecars = List.of(contractBytecode);

        // when
        subject.manageInProgressRecord(recordsHistorian, firstRecord, firstSynthBuilder, sidecars);

        // then
        verify(recordsHistorian, times(1)).nextChildRecordSourceId();
        verify(recordsHistorian)
                .trackFollowingChildRecord(sourceId, firstSynthBuilder, firstRecord, sidecars);
    }

    @Test
    void revertsSourceIdsIfCreated() {
        final var sourceId = 123;
        final var aRecord = ExpirableTxnRecord.newBuilder();

        given(recordsHistorian.nextChildRecordSourceId()).willReturn(sourceId);

        subject.manageInProgressRecord(recordsHistorian, aRecord, TransactionBody.newBuilder());
        subject.revert();

        verify(recordsHistorian).revertChildRecordsFromSource(sourceId);
    }

    @Test
    void revertsCommittedChildIdsSourceIdsIfCreated() {
        final var firstChildSourceId = 123;
        final var mySourceId = 666;
        final var secondChildSourceId = 456;
        final var aRecord = ExpirableTxnRecord.newBuilder();

        given(recordsHistorian.nextChildRecordSourceId()).willReturn(mySourceId);

        subject.addCommittedRecordSourceId(firstChildSourceId, recordsHistorian);
        subject.manageInProgressRecord(recordsHistorian, aRecord, TransactionBody.newBuilder());
        subject.addCommittedRecordSourceId(secondChildSourceId, recordsHistorian);
        subject.revert();

        verify(recordsHistorian).revertChildRecordsFromSource(mySourceId);
        verify(recordsHistorian).revertChildRecordsFromSource(firstChildSourceId);
        verify(recordsHistorian).revertChildRecordsFromSource(secondChildSourceId);
    }

    @Test
    void notOkToCommitFromDifferentHistorians() {
        final var otherHistorian = mock(RecordsHistorian.class);
        final var firstChildSourceId = 123;
        final var secondChildSourceId = 456;

        subject.addCommittedRecordSourceId(firstChildSourceId, recordsHistorian);
        assertThrows(
                IllegalArgumentException.class,
                () -> subject.addCommittedRecordSourceId(secondChildSourceId, otherHistorian));
    }

    @Test
    void getDelegatesToWrappedIfNotDeletedAndNotMutable() {
        final var wrappedAccount =
                new WorldStateAccount(aAddress, Wei.of(aHbarBalance), codeCache, entityAccess);
        given(worldState.get(aAddress)).willReturn(wrappedAccount);
        given(aliases.resolveForEvm(aAddress)).willReturn(aAddress);

        final var actual = subject.get(aAddress);
        assertSame(wrappedAccount, actual);
    }

    @Test
    void getReturnsNullWithMirrorUsageInsteadOfCreate2() {
        subject = new MockLedgerWorldUpdater(worldState, mockLedgers, customizer);
        given(mockLedgers.canonicalAddress(aAddress)).willReturn(bAddress);

        final var result = subject.get(aAddress);
        assertNull(result);
    }

    @Test
    void getPropagatesToParentUpdaterProperly() {
        final var worldStateUpdater =
                new MockLedgerWorldUpdater(worldState, mockLedgers, customizer);
        final var stackedWorldStateUpdater =
                new MockStackedLedgerUpdater(worldStateUpdater, mockLedgers, customizer);
        final var wrappedAccount =
                new WorldStateAccount(aAddress, Wei.of(aHbarBalance), codeCache, entityAccess);
        given(worldState.get(aAddress)).willReturn(wrappedAccount);
        given(mockLedgers.aliases()).willReturn(aliases);
        given(mockLedgers.canonicalAddress(aAddress)).willReturn(aAddress);
        given(aliases.resolveForEvm(aAddress)).willReturn(aAddress);

        final var actual = stackedWorldStateUpdater.get(aAddress);
        assertSame(wrappedAccount, actual);
    }

    @Test
    void parentUpdaterIsEmptyForBaseUpdater() {
        assertTrue(subject.parentUpdater().isEmpty());
    }

    @Test
    void getAndGetAccountReturnNullForDeleted() {
        final var trackingAccounts = ledgers.accounts();
        trackingAccounts.create(aAccount);
        trackingAccounts.set(aAccount, BALANCE, aHbarBalance);
        trackingAccounts.set(aAccount, ALIAS, ByteString.copyFromUtf8("TBD"));
        final var trackingAliases = ledgers.aliases();
        given(trackingAliases.resolveForEvm(aAddress)).willReturn(aAddress);
        given(trackingAliases.isInUse(aAddress)).willReturn(true);

        subject.deleteAccount(aAddress);

        assertNull(subject.get(aAddress));
        assertNull(subject.getAccount(aAddress));
        verify(trackingAliases).unlink(aAddress);
        assertEquals(ByteString.EMPTY, trackingAccounts.get(aAccount, ALIAS));
    }

    @Test
    void recognizesNonNullTokenLedgers() {
        assertNotNull(subject.trackingTokens());
    }

    @Test
    void doesntUnlinkDeletedMirrorAddress() {
        final var trackingAccounts = ledgers.accounts();
        trackingAccounts.create(aAccount);
        trackingAccounts.set(aAccount, BALANCE, aHbarBalance);
        final var trackingAliases = ledgers.aliases();
        given(trackingAliases.resolveForEvm(aAddress)).willReturn(aAddress);

        subject.deleteAccount(aAddress);

        verify(trackingAliases, never()).unlink(aAddress);
    }

    @Test
    void unlinksDeletedMirrorAddressWithAlias() {
        final byte[] alias = unhex("aaaaaaaaaaaaaaaaaaaaaaaa9abcdefabcdefbbb");
        final var aliasAddress = Address.wrap(Bytes.wrap(alias));
        final var trackingAccounts = ledgers.accounts();
        trackingAccounts.create(aAccount);
        trackingAccounts.set(aAccount, BALANCE, aHbarBalance);
        trackingAccounts.set(aAccount, ALIAS, ByteString.copyFrom(alias));
        final var trackingAliases = ledgers.aliases();
        given(trackingAliases.resolveForEvm(aAddress)).willReturn(aAddress);
        given(trackingAliases.isInUse(aAddress)).willReturn(false);
        given(trackingAliases.isInUse(aliasAddress)).willReturn(true);

        subject.deleteAccount(aAddress);

        verify(trackingAliases).unlink(aliasAddress);
        assertEquals(ByteString.EMPTY, trackingAccounts.get(aAccount, ALIAS));
    }

    @Test
    void doesntUnlinksDeletedMirrorAddressWithNoAlias() {
        final var trackingAccounts = ledgers.accounts();
        trackingAccounts.create(aAccount);
        trackingAccounts.set(aAccount, BALANCE, aHbarBalance);
        final var trackingAliases = ledgers.aliases();
        given(trackingAliases.resolveForEvm(aAddress)).willReturn(aAddress);

        subject.deleteAccount(aAddress);

        verify(trackingAliases, never()).unlink(any());
    }

    @Test
    void getReusesMutableIfPresent() {
        final var trackingAccounts = ledgers.accounts();
        trackingAccounts.create(aAccount);
        trackingAccounts.set(aAccount, BALANCE, aHbarBalance);

        given(worldState.get(aAddress))
                .willReturn(
                        new WorldStateAccount(
                                aAddress, Wei.of(aHbarBalance), codeCache, entityAccess));
        given(aliases.resolveForEvm(aAddress)).willReturn(aAddress);

        final var mutableResponse = subject.getAccount(aAddress);
        final var getResponse = subject.get(aAddress);
        assertSame(mutableResponse.getMutable(), getResponse);
    }

    @Test
    void commitsToWrappedTrackingAccountsRejectChangesToMissingAccountBalances() {
        /* Get the wrapped accounts for the updater */
        final var wrappedLedgers = subject.wrappedTrackingLedgers(sideEffectsTracker);
        final var wrappedAccounts = wrappedLedgers.accounts();
        final var aAccountMock = mock(MerkleAccount.class);
        final BackingStore<AccountID, HederaAccount> backingAccounts = new HashMapBackingAccounts();
        backingAccounts.put(aAccount, aAccountMock);
        wrappedAccounts.setCommitInterceptor(accountsCommitInterceptor);

        /* Make an illegal change to them...well-behaved HTS precompiles should not create accounts! */
        wrappedAccounts.create(aAccount);
        wrappedAccounts.set(aAccount, BALANCE, aHbarBalance + 2);
        wrappedAccounts.put(aAccount, aAccountMock);

        /* Verify we cannot commit the illegal change */
        assertThrows(IllegalArgumentException.class, wrappedLedgers::commit);
    }

    @Test
    void commitsToWrappedTrackingAccountsRejectChangesToDeletedAccountBalances() {
        final var trackingAccounts = ledgers.accounts();
        trackingAccounts.create(aAccount);
        given(aliases.resolveForEvm(aAddress)).willReturn(aAddress);

        /* Make some pending changes to one of the well-known accounts */
        subject.deleteAccount(aAddress);

        /* Get the wrapped accounts for the updater */
        final var wrappedLedgers = subject.wrappedTrackingLedgers(sideEffectsTracker);
        final var wrappedAccounts = wrappedLedgers.accounts();

        /* Make an illegal change to them---this should never happen from a well-behaved HTS
         * precompile, since our wrapped tracking ledger should show this account as deleted! */
        wrappedAccounts.set(aAccount, BALANCE, aHbarBalance + 2);

        /* Verify we cannot commit the illegal change */
        assertThrows(IllegalArgumentException.class, wrappedLedgers::commit);
    }

    @Test
    void commitsToWrappedTrackingAccountsAreRespectedAndSyncedWithUpdatedAccounts() {
        final var mockNftsOwned = 1_234_567L;
        setupWellKnownAccounts();

        /* Make some pending changes to one of the well-known accounts */
        subject.getAccount(aAddress).getMutable().setBalance(Wei.of(aHbarBalance + 1));
        subject.getAccount(bAddress).getMutable().setBalance(Wei.of(bHbarBalance - 1));

        /* Get the wrapped accounts for the updater */
        final var wrappedLedgers = subject.wrappedTrackingLedgers(sideEffectsTracker);
        final var wrappedAccounts = wrappedLedgers.accounts();

        /* Make some changes to them (e.g. as part of an HTS precompile) */
        wrappedAccounts.set(aAccount, BALANCE, aHbarBalance + 2);
        wrappedAccounts.set(aAccount, NUM_NFTS_OWNED, mockNftsOwned);
        wrappedAccounts.set(bAccount, BALANCE, bHbarBalance - 2);

        /* Commit the changes */
        wrappedLedgers.commit();

        /* And they should be present in the underlying ledgers */
        assertEquals(aHbarBalance + 2, ledgers.accounts().get(aAccount, BALANCE));
        assertEquals(bHbarBalance - 2, ledgers.accounts().get(bAccount, BALANCE));
        /* And consistently in the updatedAccounts map */
        assertTrue(subject.updatedAccounts.containsKey(aAddress));
        assertEquals(aHbarBalance + 2, subject.updatedAccounts.get(aAddress).getBalance().toLong());
        assertTrue(subject.updatedAccounts.containsKey(bAddress));
        assertEquals(bHbarBalance - 2, subject.updatedAccounts.get(bAddress).getBalance().toLong());
    }

    @Test
    void commitsToWrappedTrackingLedgersAreRespected() {
        final var aEntityId = EntityId.fromGrpcAccountId(aAccount);
        setupWellKnownNfts();

        /* Get the wrapped nfts for the updater */
        final var wrappedLedgers = subject.wrappedTrackingLedgers(sideEffectsTracker);
        final var wrappedNfts = wrappedLedgers.nfts();

        /* Make some changes to them (e.g. as part of an HTS precompile) */
        wrappedNfts.destroy(aNft);
        wrappedNfts.set(bNft, OWNER, aEntityId);

        /* Commit the changes */
        wrappedLedgers.commit();

        /* And they should be present in the underlying ledgers */
        assertFalse(ledgers.nfts().contains(aNft));
        assertEquals(aEntityId, ledgers.nfts().get(bNft, OWNER));
    }

    @Test
    void commitsToWrappedTrackingLedgersWithoutInterceptorsAreRespected() {
        final var aEntityId = EntityId.fromGrpcAccountId(aAccount);
        setupWellKnownNfts();

        /* Get the wrapped nfts for the updater */
        final var trackingLedgers = subject.trackingLedgers();
        final var nfts = trackingLedgers.nfts();

        /* Make some changes to them (e.g. as part of an HTS precompile) */
        nfts.destroy(aNft);
        nfts.set(bNft, OWNER, aEntityId);

        /* Commit the changes */
        trackingLedgers.commit();

        /* And they should be present in the underlying ledgers */
        assertFalse(ledgers.nfts().contains(aNft));
        assertEquals(aEntityId, ledgers.nfts().get(bNft, OWNER));
    }

    @Test
    void commitsToWrappedTrackingLedgersWithSetSideEffectsTrackerAreRespected() {
        final var aEntityId = EntityId.fromGrpcAccountId(aAccount);
        setupWellKnownNfts();
        /* Get the wrapped nfts for the updater */
        final var wrappedLedgers = subject.wrappedTrackingLedgers(sideEffectsTracker);
        final var wrappedNfts = wrappedLedgers.nfts();

        /* Make some changes to them (e.g. as part of an HTS precompile) */
        wrappedNfts.destroy(aNft);
        wrappedNfts.set(bNft, OWNER, aEntityId);

        /* Commit the changes */
        wrappedLedgers.commit();

        /* And they should be present in the underlying ledgers */
        assertFalse(ledgers.nfts().contains(aNft));
        assertEquals(aEntityId, ledgers.nfts().get(bNft, OWNER));
    }

    @Test
    void commitsToWrappedTrackingTokenRelsAreRespected() {
        setupWellKnownTokenRels();

        /* Get the wrapped token rels for the updater */
        final var wrappedLedgers = subject.wrappedTrackingLedgers(sideEffectsTracker);
        final var wrappedTokenRels = wrappedLedgers.tokenRels();

        /* Make some changes to them (e.g. as part of an HTS precompile) */
        wrappedTokenRels.set(aaRel, TOKEN_BALANCE, aaBalance + 1L);
        wrappedTokenRels.destroy(abRel);
        wrappedTokenRels.create(bbRel);
        wrappedTokenRels.set(bbRel, TOKEN_BALANCE, bbBalance);

        /* Commit the changes */
        wrappedLedgers.commit();

        /* And they should be present in the underlying ledgers */
        assertEquals(aaBalance + 1, ledgers.tokenRels().get(aaRel, TOKEN_BALANCE));
        assertFalse(ledgers.tokenRels().contains(abRel));
        assertTrue(ledgers.tokenRels().contains(bbRel));
        assertEquals(bbBalance, ledgers.tokenRels().get(bbRel, TOKEN_BALANCE));
    }

    @Test
    void updatesTrackingLedgerAccountOnCreateIfUsable() {
        given(aliases.resolveForEvm(aAddress)).willReturn(aAddress);
        final var newAccount = subject.createAccount(aAddress, aNonce, Wei.of(aHbarBalance));
        assertInstanceOf(WrappedEvmAccount.class, newAccount);
        final var newWrappedAccount = (WrappedEvmAccount) newAccount;
        assertFalse(newWrappedAccount.isImmutable());
        assertInstanceOf(UpdateTrackingLedgerAccount.class, newWrappedAccount.getMutable());

        final var trackingAccounts = subject.trackingLedgers().accounts();
        assertTrue(trackingAccounts.contains(aAccount));
        verify(customizer).customize(aAccount, ledgers.accounts());
        assertEquals(aHbarBalance, trackingAccounts.get(aAccount, AccountProperty.BALANCE));
    }

    @Test
    void updatesTrackingLedgerAliasesOnCreateIfUsableAndNotMirror() {
        given(aliases.resolveForEvm(aAddress)).willReturn(bAddress);
        given(aliases.isInUse(aAddress)).willReturn(true);

        subject.createAccount(aAddress, aNonce, Wei.of(aHbarBalance));

        final var trackingAccounts = subject.trackingLedgers().accounts();
        assertTrue(trackingAccounts.contains(bAccount));
        assertEquals(
                ByteString.copyFrom(aAddress.toArrayUnsafe()),
                trackingAccounts.get(bAccount, ALIAS));
    }

    @Test
    void updatesTrackingLedgerAccountOnDeleteIfUsable() {
        given(aliases.resolveForEvm(aAddress)).willReturn(aAddress);
        subject.createAccount(aAddress, aNonce, Wei.of(aHbarBalance));

        subject.deleteAccount(aAddress);

        assertNull(subject.get(aAddress));

        final var trackingAccounts = subject.trackingLedgers().accounts();
        assertTrue((boolean) trackingAccounts.get(aAccount, IS_DELETED));
    }

    @Test
    void noopsOnCreateWithUnusableTrackingErrorToPreserveExistingErrorHandling() {
        given(aliases.resolveForEvm(aAddress)).willReturn(aAddress);

        subject =
                new MockLedgerWorldUpdater(
                        worldState, staticLedgersWith(aliases, null), customizer);

        assertDoesNotThrow(() -> subject.createAccount(aAddress, aNonce, Wei.of(aHbarBalance)));
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
        final var tokensLedger =
                new TransactionalLedger<>(
                        TokenProperty.class,
                        MerkleToken::new,
                        new HashMapBackingTokens(),
                        new ChangeSummaryManager<>());
        final var nftsLedger =
                new TransactionalLedger<>(
                        NftProperty.class,
                        UniqueTokenAdapter::newEmptyMerkleToken,
                        new HashMapBackingNfts(),
                        new ChangeSummaryManager<>());

        tokenRelsLedger.begin();
        accountsLedger.begin();
        nftsLedger.begin();
        tokensLedger.begin();

        ledgers =
                new WorldLedgers(
                        aliases, tokenRelsLedger, accountsLedger, nftsLedger, tokensLedger);
    }

    private void setupWellKnownAccounts() {
        final var trackingAccounts = ledgers.accounts();
        trackingAccounts.create(aAccount);
        trackingAccounts.set(aAccount, BALANCE, aHbarBalance);
        trackingAccounts.create(bAccount);
        trackingAccounts.set(bAccount, BALANCE, bHbarBalance);

        given(worldState.get(aAddress))
                .willReturn(
                        new WorldStateAccount(
                                aAddress, Wei.of(aHbarBalance), codeCache, entityAccess));
        given(worldState.get(bAddress))
                .willReturn(
                        new WorldStateAccount(
                                bAddress, Wei.of(bHbarBalance), codeCache, entityAccess));
    }

    private void setupWellKnownNfts() {
        final var trackingNfts = ledgers.nfts();
        trackingNfts.setCommitInterceptor(linkAwareUniqueTokensCommitInterceptor);
        trackingNfts.create(aNft);
        trackingNfts.set(aNft, OWNER, EntityId.fromGrpcAccountId(aAccount));
        trackingNfts.create(bNft);
        trackingNfts.set(bNft, OWNER, EntityId.fromGrpcAccountId(bAccount));
        trackingNfts.commit();
        trackingNfts.begin();
    }

    private void setupWellKnownTokenRels() {
        final var trackingRels = ledgers.tokenRels();
        trackingRels.setCommitInterceptor(autoAssocTokenRelsCommitInterceptor);
        trackingRels.create(aaRel);
        trackingRels.set(aaRel, TOKEN_BALANCE, aaBalance);
        trackingRels.create(abRel);
        trackingRels.commit();
        trackingRels.begin();
    }
}

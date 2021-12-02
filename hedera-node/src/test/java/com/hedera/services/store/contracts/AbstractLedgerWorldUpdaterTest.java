package com.hedera.services.store.contracts;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.backing.HashMapBackingAccounts;
import com.hedera.services.ledger.backing.HashMapBackingNfts;
import com.hedera.services.ledger.backing.HashMapBackingTokenRels;
import com.hedera.services.ledger.backing.HashMapBackingTokens;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.lang3.tuple.Pair;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.worldstate.WrappedEvmAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.ledger.properties.AccountProperty.NUM_NFTS_OWNED;
import static com.hedera.services.ledger.properties.NftProperty.OWNER;
import static com.hedera.services.ledger.properties.TokenRelProperty.TOKEN_BALANCE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

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
	private static final Address aAddress = EntityIdUtils.asTypedSolidityAddress(aAccount);
	private static final Address bAddress = EntityIdUtils.asTypedSolidityAddress(bAccount);
	private static final long aaBalance = 1L;
	private static final long bbBalance = 9L;
	private static final long aHbarBalance = 1_000L;
	private static final long bHbarBalance = 2_000L;
	private static final long aNonce = 1L;

	@Mock
	private HederaWorldState worldState;
	@Mock
	private AccountRecordsHistorian recordsHistorian;

	private WorldLedgers ledgers;
	private MockLedgerWorldUpdater subject;

	@BeforeEach
	void setUp() {
		setupLedgers();

		subject = new MockLedgerWorldUpdater(worldState, ledgers);
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
		verify(recordsHistorian).trackFollowingChildRecord(sourceId, firstSynthBuilder, firstRecord);
		verify(recordsHistorian).trackFollowingChildRecord(sourceId, secondSynthBuilder, secondRecord);
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
	void getDelegatesToWrappedIfNotDeletedAndNotMutable() {
		final var wrappedAccount =
				worldState.new WorldStateAccount(
						aAddress, Wei.of(aHbarBalance), 1_234_567L, 7776000L, EntityId.MISSING_ENTITY_ID);
		given(worldState.get(aAddress)).willReturn(wrappedAccount);

		final var actual = subject.get(aAddress);
		assertSame(wrappedAccount, actual);
	}

	@Test
	void parentUpdaterIsEmptyForBaseUpdater() {
		assertTrue(subject.parentUpdater().isEmpty());
	}

	@Test
	void getAndGetAccountReturnNullForDeleted() {
		final var trackingAccounts = ledgers.accountsLedger;
		trackingAccounts.create(aAccount);
		trackingAccounts.set(aAccount, BALANCE, aHbarBalance);

		subject.deleteAccount(aAddress);

		assertNull(subject.get(aAddress));
		assertNull(subject.getAccount(aAddress));
	}

	@Test
	void getReusesMutableIfPresent() {
		final var trackingAccounts = ledgers.accountsLedger;
		trackingAccounts.create(aAccount);
		trackingAccounts.set(aAccount, BALANCE, aHbarBalance);

		given(worldState.get(aAddress)).willReturn(worldState.new WorldStateAccount(
				aAddress, Wei.of(aHbarBalance), 1_234_567L, 7776000L, EntityId.MISSING_ENTITY_ID));

		final var mutableResponse = subject.getAccount(aAddress);
		final var getResponse = subject.get(aAddress);
		assertSame(mutableResponse.getMutable(), getResponse);
	}

	@Test
	void commitsToWrappedTrackingAccountsRejectChangesToMissingAccountBalances() {
		/* Get the wrapped accounts for the updater */
		final var wrappedLedgers = subject.wrappedTrackingLedgers();
		final var wrappedAccounts = wrappedLedgers.accounts();

		/* Make an illegal change to them...well-behaved HTS precompiles should not create accounts! */
		wrappedAccounts.create(aAccount);
		wrappedAccounts.set(aAccount, BALANCE, aHbarBalance + 2);

		/* Verify we cannot commit the illegal change */
		assertThrows(IllegalArgumentException.class, wrappedLedgers::commit);
	}

	@Test
	void commitsToWrappedTrackingAccountsRejectChangesToDeletedAccountBalances() {
		final var trackingAccounts = ledgers.accountsLedger;
		trackingAccounts.create(aAccount);

		/* Make some pending changes to one of the well-known accounts */
		subject.deleteAccount(aAddress);

		/* Get the wrapped accounts for the updater */
		final var wrappedLedgers = subject.wrappedTrackingLedgers();
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

		/* Get the wrapped accounts for the updater */
		final var wrappedLedgers = subject.wrappedTrackingLedgers();
		final var wrappedAccounts = wrappedLedgers.accounts();

		/* Make some changes to them (e.g. as part of an HTS precompile) */
		wrappedAccounts.set(aAccount, BALANCE, aHbarBalance + 2);
		wrappedAccounts.set(aAccount, NUM_NFTS_OWNED, mockNftsOwned);
		wrappedAccounts.set(bAccount, BALANCE, bHbarBalance - 2);

		/* Commit the changes */
		wrappedLedgers.commit();

		/* And they should be present in the underlying ledgers */
		assertEquals(aHbarBalance + 2, ledgers.accountsLedger.get(aAccount, BALANCE));
		assertEquals(bHbarBalance - 2, ledgers.accountsLedger.get(bAccount, BALANCE));
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
		final var wrappedLedgers = subject.wrappedTrackingLedgers();
		final var wrappedNfts = wrappedLedgers.nfts();

		/* Make some changes to them (e.g. as part of an HTS precompile) */
		wrappedNfts.destroy(aNft);
		wrappedNfts.set(bNft, OWNER, aEntityId);

		/* Commit the changes */
		wrappedLedgers.commit();

		/* And they should be present in the underlying ledgers */
		assertFalse(ledgers.nftsLedger.contains(aNft));
		assertEquals(aEntityId, ledgers.nftsLedger.get(bNft, OWNER));
	}

	@Test
	void commitsToWrappedTrackingTokenRelsAreRespected() {
		setupWellKnownTokenRels();

		/* Get the wrapped token rels for the updater */
		final var wrappedLedgers = subject.wrappedTrackingLedgers();
		final var wrappedTokenRels = wrappedLedgers.tokenRels();

		/* Make some changes to them (e.g. as part of an HTS precompile) */
		wrappedTokenRels.set(aaRel, TOKEN_BALANCE, aaBalance + 1L);
		wrappedTokenRels.destroy(abRel);
		wrappedTokenRels.create(bbRel);
		wrappedTokenRels.set(bbRel, TOKEN_BALANCE, bbBalance);

		/* Commit the changes */
		wrappedLedgers.commit();

		/* And they should be present in the underlying ledgers */
		assertEquals(aaBalance + 1, ledgers.tokenRelsLedger.get(aaRel, TOKEN_BALANCE));
		assertFalse(ledgers.tokenRelsLedger.contains(abRel));
		assertTrue(ledgers.tokenRelsLedger.contains(bbRel));
		assertEquals(bbBalance, ledgers.tokenRelsLedger.get(bbRel, TOKEN_BALANCE));
	}

	@Test
	void updatesTrackingLedgerAccountOnCreateIfUsable() {
		final var newAccount = subject.createAccount(aAddress, aNonce, Wei.of(aHbarBalance));
		assertInstanceOf(WrappedEvmAccount.class, newAccount);
		final var newWrappedAccount = (WrappedEvmAccount) newAccount;
		assertFalse(newWrappedAccount.isImmutable());
		assertInstanceOf(UpdateTrackingLedgerAccount.class, newWrappedAccount.getMutable());

		final var trackingAccounts = subject.trackingLedgers().accounts();
		assertTrue(trackingAccounts.contains(aAccount));
		assertEquals(aHbarBalance, trackingAccounts.get(aAccount, AccountProperty.BALANCE));
	}

	@Test
	void updatesTrackingLedgerAccountOnDeleteIfUsable() {
		subject.createAccount(aAddress, aNonce, Wei.of(aHbarBalance));

		subject.deleteAccount(aAddress);

		assertNull(subject.get(aAddress));

		final var trackingAccounts = subject.trackingLedgers().accounts();
		assertTrue((boolean) trackingAccounts.get(aAccount, IS_DELETED));
	}

	@Test
	void noopsOnCreateWithUnusableTrackingErrorToPreserveExistingErrorHandling() {
		subject = new MockLedgerWorldUpdater(worldState, WorldLedgers.NULL_WORLD_LEDGERS);

		assertDoesNotThrow(() -> subject.createAccount(aAddress, aNonce, Wei.of(aHbarBalance)));
	}

	private void setupLedgers() {
		final var tokenRelsLedger = new TransactionalLedger<>(
				TokenRelProperty.class,
				MerkleTokenRelStatus::new,
				new HashMapBackingTokenRels(),
				new ChangeSummaryManager<>());
		final var accountsLedger = new TransactionalLedger<>(
				AccountProperty.class,
				MerkleAccount::new,
				new HashMapBackingAccounts(),
				new ChangeSummaryManager<>());
		final var tokensLedger = new TransactionalLedger<>(
				TokenProperty.class,
				MerkleToken::new,
				new HashMapBackingTokens(),
				new ChangeSummaryManager<>());
		final var nftsLedger = new TransactionalLedger<>(
				NftProperty.class,
				MerkleUniqueToken::new,
				new HashMapBackingNfts(),
				new ChangeSummaryManager<>());

		tokenRelsLedger.begin();
		accountsLedger.begin();
		nftsLedger.begin();
		tokensLedger.begin();

		ledgers = new WorldLedgers(tokenRelsLedger, accountsLedger, nftsLedger, tokensLedger);
	}

	private void setupWellKnownAccounts() {
		final var trackingAccounts = ledgers.accountsLedger;
		trackingAccounts.create(aAccount);
		trackingAccounts.set(aAccount, BALANCE, aHbarBalance);
		trackingAccounts.create(bAccount);
		trackingAccounts.set(bAccount, BALANCE, bHbarBalance);

		given(worldState.get(aAddress)).willReturn(worldState.new WorldStateAccount(
				aAddress, Wei.of(aHbarBalance), 1_234_567L, 7776000L, EntityId.MISSING_ENTITY_ID));
		given(worldState.get(bAddress)).willReturn(worldState.new WorldStateAccount(
				bAddress, Wei.of(bHbarBalance), 1_234_567L, 7776000L, EntityId.MISSING_ENTITY_ID));
	}

	private void setupWellKnownNfts() {
		final var trackingNfts = ledgers.nftsLedger;
		trackingNfts.create(aNft);
		trackingNfts.set(aNft, OWNER, EntityId.fromGrpcAccountId(aAccount));
		trackingNfts.create(bNft);
		trackingNfts.set(bNft, OWNER, EntityId.fromGrpcAccountId(bAccount));
		trackingNfts.commit();
		trackingNfts.begin();
	}

	private void setupWellKnownTokenRels() {
		final var trackingRels = ledgers.tokenRelsLedger;
		trackingRels.create(aaRel);
		trackingRels.set(aaRel, TOKEN_BALANCE, aaBalance);
		trackingRels.create(abRel);
		trackingRels.commit();
		trackingRels.begin();
	}
}

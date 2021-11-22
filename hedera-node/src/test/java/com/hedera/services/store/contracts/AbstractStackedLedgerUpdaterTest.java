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
import com.hedera.services.ledger.backing.HashMapBackingTokens;
import com.hedera.services.ledger.backing.HashMapBackingAccounts;
import com.hedera.services.ledger.backing.HashMapBackingNfts;
import com.hedera.services.ledger.backing.HashMapBackingTokenRels;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AbstractStackedLedgerUpdaterTest {
	@Mock
	private HederaWorldState worldState;

	private WorldLedgers ledgers;
	private MockLedgerWorldUpdater wrapped;

	private AbstractStackedLedgerUpdater<HederaWorldState, HederaWorldState.WorldStateAccount> subject;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		setupLedgers();

		wrapped = new MockLedgerWorldUpdater(worldState, ledgers.wrapped());

		subject = (AbstractStackedLedgerUpdater<HederaWorldState, HederaWorldState.WorldStateAccount>) wrapped.updater();
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
		final var account = worldState.new WorldStateAccount(
				aAddress, Wei.of(aBalance), aExpiry, aAutoRenew, EntityId.MISSING_ENTITY_ID);
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
	void commitsNewlyModifiedAccountAsExpected() {
		final var mockCode = Bytes.ofUnsignedLong(1_234L);
		final var account = worldState.new WorldStateAccount(
				aAddress, Wei.of(aBalance), aExpiry, aAutoRenew, EntityId.MISSING_ENTITY_ID);
		given(worldState.get(aAddress)).willReturn(account);
		ledgers.accounts().create(aAccount);
		ledgers.accounts().set(aAccount, BALANCE, aBalance);

		final var mutableAccount = subject.getAccount(aAddress);
		mutableAccount.getMutable().decrementBalance(Wei.of(1));
		mutableAccount.getMutable().setCode(mockCode);
		mutableAccount.getMutable().clearStorage();

		subject.commit();

		final var wrappedMutableAccount = wrapped.getAccount(aAddress);
		assertEquals(mockCode, wrappedMutableAccount.getCode());
		wrappedMutableAccount.getMutable().decrementBalance(Wei.of(1));
		assertEquals(aBalance, ledgers.accounts().get(aAccount, BALANCE));

		wrapped.commit();
		assertEquals(aBalance - 2, ledgers.accounts().get(aAccount, BALANCE));
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
		final var nftsLedger = new TransactionalLedger<>(
				NftProperty.class,
				MerkleUniqueToken::new,
				new HashMapBackingNfts(),
				new ChangeSummaryManager<>());
		final var tokensLedger = new TransactionalLedger<>(
				TokenProperty.class,
				MerkleToken::new,
				new HashMapBackingTokens(),
				new ChangeSummaryManager<>());

		tokenRelsLedger.begin();
		accountsLedger.begin();
		nftsLedger.begin();

		ledgers = new WorldLedgers(tokenRelsLedger, accountsLedger, nftsLedger, tokensLedger);
	}

	private static final AccountID aAccount = IdUtils.asAccount("0.0.12345");
	private static final Address aAddress = EntityIdUtils.asTypedSolidityAddress(aAccount);
	private static final long aBalance = 1_000L;
	private static final long aNonce = 1L;
	private static final long aExpiry = 1_234_567L;
	private static final long aAutoRenew = 7776000L;
}
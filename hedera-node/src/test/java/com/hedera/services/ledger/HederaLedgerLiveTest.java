package com.hedera.services.ledger;

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

import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.exceptions.InconsistentAdjustmentsException;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.backing.BackingTokenRels;
import com.hedera.services.ledger.backing.HashMapBackingAccounts;
import com.hedera.services.ledger.backing.HashMapBackingNfts;
import com.hedera.services.ledger.backing.HashMapBackingTokenRels;
import com.hedera.services.ledger.backing.HashMapBackingTokens;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.store.contracts.MutableEntityAccess;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.store.tokens.views.UniqueTokenViewsManager;
import com.hedera.services.txns.crypto.AutoCreationLogic;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.mocks.TestContextValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class HederaLedgerLiveTest extends BaseHederaLedgerTestHelper {
	private static final long thisSecond = 1_234_567L;

	@Mock
	private AutoCreationLogic autoCreationLogic;

	@BeforeEach
	void setup() {
		commonSetup();

		accountsLedger = new TransactionalLedger<>(
				AccountProperty.class,
				MerkleAccount::new,
				new HashMapBackingAccounts(),
				new ChangeSummaryManager<>());
		final MerkleMap<EntityNum, MerkleToken> tokens = new MerkleMap<>();
		final FCOneToManyRelation<EntityNum, Long> uniqueTokenOwnerships = new FCOneToManyRelation<>();
		final FCOneToManyRelation<EntityNum, Long> uniqueTokenAccountOwnerships = new FCOneToManyRelation<>();
		final FCOneToManyRelation<EntityNum, Long> uniqueTokenTreasuryOwnerships = new FCOneToManyRelation<>();
		final var sideEffectsTracker = new SideEffectsTracker();

		nftsLedger = new TransactionalLedger<>(
				NftProperty.class,
				MerkleUniqueToken::new,
				new HashMapBackingNfts(),
				new ChangeSummaryManager<>());
		tokenRelsLedger = new TransactionalLedger<>(
				TokenRelProperty.class,
				MerkleTokenRelStatus::new,
				new HashMapBackingTokenRels(),
				new ChangeSummaryManager<>());
		tokenRelsLedger.setKeyToString(BackingTokenRels::readableTokenRel);
		final var viewManager = new UniqueTokenViewsManager(
				() -> uniqueTokenOwnerships,
				() -> uniqueTokenAccountOwnerships,
				() -> uniqueTokenTreasuryOwnerships,
				false, true);
		tokenStore = new HederaTokenStore(
				ids,
				TestContextValidator.TEST_VALIDATOR,
				sideEffectsTracker,
				viewManager,
				new MockGlobalDynamicProps(),
				tokenRelsLedger,
				nftsLedger,
				new HashMapBackingTokens());
		subject = new HederaLedger(
				tokenStore, ids, creator, validator, sideEffectsTracker, historian, dynamicProps, accountsLedger,
				transferLogic, autoCreationLogic);
		subject.setMutableEntityAccess(mock(MutableEntityAccess.class));
	}

	@Test
	void throwsOnCommittingInconsistentAdjustments() {
		subject.begin();
		subject.adjustBalance(genesis, -1L);

		assertThrows(InconsistentAdjustmentsException.class, () -> subject.commit());
	}

	@Test
	void doesntIncludeZeroAdjustsInNetTransfers() {
		subject.begin();
		final var a = subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
		subject.delete(a, genesis);

		assertEquals(0L, subject.netTransfersInTxn().getAccountAmountsList().size());
	}

	@Test
	void recordsCreationOfAccountDeletedInSameTxn() {
		subject.begin();
		final var a = subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
		subject.delete(a, genesis);
		final var numNetTransfers = subject.netTransfersInTxn().getAccountAmountsCount();
		subject.commit();

		assertEquals(0, numNetTransfers);
		assertTrue(subject.exists(a));
		assertEquals(GENESIS_BALANCE, subject.getBalance(genesis));
	}

	@Test
	void addsRecordsAndEntitiesBeforeCommitting() {
		subject.begin();
		subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
		subject.commit();

		verify(historian).saveExpirableTransactionRecords();
		verify(historian).noteNewExpirationEvents();
	}

	@Test
	void recognizesPendingCreates() {
		subject.begin();
		final var a = subject.create(genesis, 1L, new HederaAccountCustomizer().memo("a"));

		assertTrue(subject.isPendingCreation(a));
		assertFalse(subject.isPendingCreation(genesis));
	}

	private TokenCreateTransactionBody stdWith(final String symbol, final String tokenName, final AccountID account) {
		final var key = TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asKey();
		return TokenCreateTransactionBody.newBuilder()
				.setAdminKey(key)
				.setFreezeKey(TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asKey())
				.setSymbol(symbol)
				.setName(tokenName)
				.setInitialSupply(0)
				.setTreasury(account)
				.setExpiry(Timestamp.newBuilder().setSeconds(2 * thisSecond))
				.setDecimals(0)
				.setFreezeDefault(false)
				.build();
	}
}

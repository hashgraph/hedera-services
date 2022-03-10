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
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.store.contracts.MutableEntityAccess;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.store.tokens.views.UniqueTokenViewsManager;
import com.hedera.services.txns.crypto.AutoCreationLogic;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.mocks.TestContextValidator;
import com.swirlds.fchashmap.FCOneToManyRelation;
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

	@Mock
	private AutoCreationLogic autoCreationLogic;
	@Mock
	private TokenRelsCommitInterceptor tokenRelsCommitInterceptor;
	@Mock
	private AccountsCommitInterceptor accountsCommitInterceptor;
	@Mock
	private UniqueTokensCommitInterceptor uniqueTokensCommitInterceptor;

	final SideEffectsTracker liveSideEffects = new SideEffectsTracker();

	@BeforeEach
	void setup() {
		commonSetup();

		accountsLedger = new TransactionalLedger<>(
				AccountProperty.class,
				MerkleAccount::new,
				new HashMapBackingAccounts(),
				new ChangeSummaryManager<>());
		accountsLedger.setCommitInterceptor(accountsCommitInterceptor);
		final FCOneToManyRelation<EntityNum, Long> uniqueTokenOwnerships = new FCOneToManyRelation<>();
		final FCOneToManyRelation<EntityNum, Long> uniqueTokenAccountOwnerships = new FCOneToManyRelation<>();
		final FCOneToManyRelation<EntityNum, Long> uniqueTokenTreasuryOwnerships = new FCOneToManyRelation<>();

		nftsLedger = new TransactionalLedger<>(
				NftProperty.class,
				MerkleUniqueToken::new,
				new HashMapBackingNfts(),
				new ChangeSummaryManager<>());
		nftsLedger.setCommitInterceptor(uniqueTokensCommitInterceptor);
		tokenRelsLedger = new TransactionalLedger<>(
				TokenRelProperty.class,
				MerkleTokenRelStatus::new,
				new HashMapBackingTokenRels(),
				new ChangeSummaryManager<>());
		tokenRelsLedger.setKeyToString(BackingTokenRels::readableTokenRel);
		tokenRelsLedger.setCommitInterceptor(tokenRelsCommitInterceptor);
		final var viewManager = new UniqueTokenViewsManager(
				() -> uniqueTokenOwnerships,
				() -> uniqueTokenAccountOwnerships,
				() -> uniqueTokenTreasuryOwnerships,
				false, true);
		tokenStore = new HederaTokenStore(
				ids,
				TestContextValidator.TEST_VALIDATOR,
				liveSideEffects,
				viewManager,
				new MockGlobalDynamicProps(),
				tokenRelsLedger,
				nftsLedger,
				new HashMapBackingTokens());
		subject = new HederaLedger(
				tokenStore, ids, creator, validator, liveSideEffects, historian, dynamicProps, accountsLedger,
				transferLogic, autoCreationLogic);
		subject.setMutableEntityAccess(mock(MutableEntityAccess.class));
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
	void showsInconsistentStateIfSpawnFails() {
		subject.begin();
		subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
		subject.commit();

		ids.reclaimLastId();
		liveSideEffects.reset();
		subject.begin();
		final var customizer = new HederaAccountCustomizer().memo("a");
		assertThrows(IllegalArgumentException.class, () -> subject.create(genesis, 1_000L, customizer));
	}

	@Test
	void recognizesPendingCreates() {
		subject.begin();
		final var a = subject.create(genesis, 1L, new HederaAccountCustomizer().memo("a"));

		assertTrue(subject.isPendingCreation(a));
		assertFalse(subject.isPendingCreation(genesis));
	}
}

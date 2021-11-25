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
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.store.models.NftId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import static com.hedera.services.store.contracts.WorldLedgers.NULL_WORLD_LEDGERS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WorldLedgersTest {
	private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger;
	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
	private TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger;
	private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger;

	@Test
	@SuppressWarnings("unchecked")
	void commitsAsExpected() {
		tokenRelsLedger = mock(TransactionalLedger.class);
		accountsLedger = mock(TransactionalLedger.class);
		nftsLedger = mock(TransactionalLedger.class);
		tokensLedger = mock(TransactionalLedger.class);

		final var source = new WorldLedgers(tokenRelsLedger, accountsLedger, nftsLedger, tokensLedger);

		source.commit();

		verify(tokenRelsLedger).commit();
		verify(accountsLedger).commit();
		verify(nftsLedger).commit();
		verify(tokensLedger).commit();
	}

	@Test
	@SuppressWarnings("unchecked")
	void revertsAsExpected() {
		tokenRelsLedger = mock(TransactionalLedger.class);
		accountsLedger = mock(TransactionalLedger.class);
		nftsLedger = mock(TransactionalLedger.class);
		tokensLedger = mock(TransactionalLedger.class);

		final var source = new WorldLedgers(tokenRelsLedger, accountsLedger, nftsLedger, tokensLedger);

		source.revert();

		verify(tokenRelsLedger).rollback();
		verify(accountsLedger).rollback();
		verify(nftsLedger).rollback();
		verify(tokensLedger).rollback();

		verify(tokenRelsLedger).begin();
		verify(accountsLedger).begin();
		verify(nftsLedger).begin();
		verify(tokensLedger).begin();
	}

	@Test
	void wrapsAsExpected() {
		tokenRelsLedger = new TransactionalLedger<>(
				TokenRelProperty.class,
				MerkleTokenRelStatus::new,
				new HashMapBackingTokenRels(),
				new ChangeSummaryManager<>());
		accountsLedger = new TransactionalLedger<>(
				AccountProperty.class,
				MerkleAccount::new,
				new HashMapBackingAccounts(),
				new ChangeSummaryManager<>());
		nftsLedger = new TransactionalLedger<>(
				NftProperty.class,
				MerkleUniqueToken::new,
				new HashMapBackingNfts(),
				new ChangeSummaryManager<>());
		tokensLedger = new TransactionalLedger<>(
				TokenProperty.class,
				MerkleToken::new,
				new HashMapBackingTokens(),
				new ChangeSummaryManager<>());

		final var source = new WorldLedgers(tokenRelsLedger, accountsLedger, nftsLedger, tokensLedger);
		assertTrue(source.areUsable());

		final var wrappedSource = source.wrapped();

		assertSame(tokenRelsLedger, wrappedSource.tokenRels().getEntitiesLedger());
		assertSame(accountsLedger, wrappedSource.accounts().getEntitiesLedger());
		assertSame(nftsLedger, wrappedSource.nfts().getEntitiesLedger());
		assertSame(tokensLedger, wrappedSource.tokens().getEntitiesLedger());
	}

	@Test
	void nullLedgersWorkAsExpected() {
		assertSame(NULL_WORLD_LEDGERS, NULL_WORLD_LEDGERS.wrapped());
		assertFalse(NULL_WORLD_LEDGERS.areUsable());
		assertDoesNotThrow(NULL_WORLD_LEDGERS::commit);
		assertDoesNotThrow(NULL_WORLD_LEDGERS::revert);
	}
}

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

import com.google.protobuf.ByteString;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.ledger.accounts.StackedContractAliases;
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
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.virtual.UniqueTokenValue;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.fchashmap.FCHashMap;
import org.apache.commons.lang3.tuple.Pair;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.ledger.properties.NftProperty.OWNER;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WorldLedgersTest {
	private static final NftId target = new NftId(0, 0, 123, 456);
	private static final TokenID nft = target.tokenId();
	private static final EntityId treasury = new EntityId(0, 0, 666);
	private static final EntityId notTreasury = new EntityId(0, 0, 777);
	private static final Address alias = Address.fromHexString("0xabcdefabcdefabcdefbabcdefabcdefabcdefbbb");
	private static final Address sponsor = Address.fromHexString("0xcba");

	@Mock
	private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger;
	@Mock
	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
	@Mock
	private TransactionalLedger<NftId, NftProperty, UniqueTokenValue> nftsLedger;
	@Mock
	private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger;
	@Mock
	private SigImpactHistorian sigImpactHistorian;
	@Mock
	private ContractAliases aliases;
	@Mock
	private StaticEntityAccess staticEntityAccess;
	@Mock
	private SideEffectsTracker sideEffectsTracker;

	private WorldLedgers subject;

	@BeforeEach
	void setUp() {
		subject = new WorldLedgers(aliases, tokenRelsLedger, accountsLedger, nftsLedger, tokensLedger);
	}

	@Test
	void resolvesOwnerDirectlyIfNotTreasury() {
		given(nftsLedger.get(target, OWNER)).willReturn(notTreasury);

		final var expected = notTreasury.toEvmAddress();
		final var actual = subject.ownerOf(target);
		assertEquals(expected, actual);
	}

	@Test
	void resolvesTreasuryOwner() {
		given(nftsLedger.get(target, OWNER)).willReturn(MISSING_ENTITY_ID);
		given(tokensLedger.get(nft, TokenProperty.TREASURY)).willReturn(treasury);

		final var expected = treasury.toEvmAddress();
		final var actual = subject.ownerOf(target);
		assertEquals(expected, actual);
	}

	@Test
	void commitsAsExpectedNoHistorian() {
		subject.commit();

		verify(tokenRelsLedger).commit();
		verify(accountsLedger).commit();
		verify(nftsLedger).commit();
		verify(tokensLedger).commit();
		verify(aliases).commit(null);
	}

	@Test
	void aliasIsCanonicalCreate2SourceAddress() {
		given(aliases.isInUse(alias)).willReturn(true);

		assertSame(alias, subject.canonicalAddress(alias));
	}

	@Test
	void mirrorNoAliasIsCanonicalSourceWithLedgers() {
		final var id = EntityIdUtils.accountIdFromEvmAddress(sponsor);
		given(accountsLedger.exists(id)).willReturn(true);
		given(accountsLedger.get(id, AccountProperty.ALIAS)).willReturn(ByteString.EMPTY);

		assertSame(sponsor, subject.canonicalAddress(sponsor));
	}

	@Test
	void missingMirrorIsCanonicalSourceWithLedgers() {
		assertSame(sponsor, subject.canonicalAddress(sponsor));
	}

	@Test
	void missingMirrorIsCanonicalSourceWithStaticAccess() {
		subject = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);
		assertSame(sponsor, subject.canonicalAddress(sponsor));
	}

	@Test
	void mirrorNoAliasIsCanonicalSourceWithStaticAccess() {
		subject = WorldLedgers.staticLedgersWith(aliases, staticEntityAccess);
		final var id = EntityIdUtils.accountIdFromEvmAddress(sponsor);
		given(staticEntityAccess.isExtant(id)).willReturn(true);
		given(staticEntityAccess.alias(id)).willReturn(ByteString.EMPTY);

		assertSame(sponsor, subject.canonicalAddress(sponsor));
	}

	@Test
	void mirrorWithAliasUsesAliasAsCanonicalSource() {
		final var id= EntityIdUtils.accountIdFromEvmAddress(sponsor);
		given(accountsLedger.exists(id)).willReturn(true);
		given(accountsLedger.get(id, AccountProperty.ALIAS)).willReturn(ByteString.copyFrom(alias.toArrayUnsafe()));
		assertEquals(alias, subject.canonicalAddress(sponsor));
	}

	@Test
	void commitsAsExpectedWithHistorian() {
		subject.commit(sigImpactHistorian);

		verify(tokenRelsLedger).commit();
		verify(accountsLedger).commit();
		verify(nftsLedger).commit();
		verify(tokensLedger).commit();
		verify(aliases).commit(sigImpactHistorian);
	}

	@Test
	void revertsAsExpected() {
		subject.revert();

		verify(tokenRelsLedger).rollback();
		verify(accountsLedger).rollback();
		verify(nftsLedger).rollback();
		verify(tokensLedger).rollback();
		verify(aliases).revert();

		verify(tokenRelsLedger).begin();
		verify(accountsLedger).begin();
		verify(nftsLedger).begin();
		verify(tokensLedger).begin();
	}

	@Test
	void wrapsAsExpectedWithCommitInterceptors() {
		final var liveTokenRels = new TransactionalLedger<>(
				TokenRelProperty.class,
				MerkleTokenRelStatus::new,
				new HashMapBackingTokenRels(),
				new ChangeSummaryManager<>());
		final var liveAccounts = new TransactionalLedger<>(
				AccountProperty.class,
				MerkleAccount::new,
				new HashMapBackingAccounts(),
				new ChangeSummaryManager<>());
		final var liveNfts = new TransactionalLedger<>(
				NftProperty.class,
				UniqueTokenValue::new,
				new HashMapBackingNfts(),
				new ChangeSummaryManager<>());
		final var liveTokens = new TransactionalLedger<>(
				TokenProperty.class,
				MerkleToken::new,
				new HashMapBackingTokens(),
				new ChangeSummaryManager<>());
		final FCHashMap<ByteString, EntityNum> aliases = new FCHashMap<>();
		final var liveAliases = new AliasManager(() -> aliases);

		final var source = new WorldLedgers(liveAliases, liveTokenRels, liveAccounts, liveNfts, liveTokens);
		assertTrue(source.areMutable());
		final var nullTokenRels = new WorldLedgers(liveAliases, null, liveAccounts, liveNfts, liveTokens);
		final var nullAccounts = new WorldLedgers(liveAliases, liveTokenRels, null, liveNfts, liveTokens);
		final var nullNfts = new WorldLedgers(liveAliases, liveTokenRels, liveAccounts, null, liveTokens);
		final var nullTokens = new WorldLedgers(liveAliases, liveTokenRels, liveAccounts, liveNfts, null);
		assertFalse(nullTokenRels.areMutable());
		assertFalse(nullAccounts.areMutable());
		assertFalse(nullNfts.areMutable());
		assertFalse(nullTokens.areMutable());

		final var wrappedUnusable = nullAccounts.wrapped(sideEffectsTracker);
		assertSame(((StackedContractAliases) wrappedUnusable.aliases()).wrappedAliases(), nullAccounts.aliases());
		assertFalse(wrappedUnusable.areMutable());

		final var wrappedSource = source.wrapped(sideEffectsTracker);

		assertSame(liveTokenRels, wrappedSource.tokenRels().getEntitiesLedger());
		assertSame(liveAccounts, wrappedSource.accounts().getEntitiesLedger());
		assertSame(liveNfts, wrappedSource.nfts().getEntitiesLedger());
		assertSame(liveTokens, wrappedSource.tokens().getEntitiesLedger());
		final var stackedAliases = (StackedContractAliases) wrappedSource.aliases();
		assertSame(liveAliases, stackedAliases.wrappedAliases());
	}

	@Test
	void wrapsAsExpectedWithoutCommitInterceptors() {
		final var liveTokenRels = new TransactionalLedger<>(
				TokenRelProperty.class,
				MerkleTokenRelStatus::new,
				new HashMapBackingTokenRels(),
				new ChangeSummaryManager<>());
		final var liveAccounts = new TransactionalLedger<>(
				AccountProperty.class,
				MerkleAccount::new,
				new HashMapBackingAccounts(),
				new ChangeSummaryManager<>());
		final var liveNfts = new TransactionalLedger<>(
				NftProperty.class,
				UniqueTokenValue::new,
				new HashMapBackingNfts(),
				new ChangeSummaryManager<>());
		final var liveTokens = new TransactionalLedger<>(
				TokenProperty.class,
				MerkleToken::new,
				new HashMapBackingTokens(),
				new ChangeSummaryManager<>());
		final FCHashMap<ByteString, EntityNum> aliases = new FCHashMap<>();
		final var liveAliases = new AliasManager(() -> aliases);

		final var source = new WorldLedgers(liveAliases, liveTokenRels, liveAccounts, liveNfts, liveTokens);
		assertTrue(source.areMutable());
		final var nullTokenRels = new WorldLedgers(liveAliases, null, liveAccounts, liveNfts, liveTokens);
		final var nullAccounts = new WorldLedgers(liveAliases, liveTokenRels, null, liveNfts, liveTokens);
		final var nullNfts = new WorldLedgers(liveAliases, liveTokenRels, liveAccounts, null, liveTokens);
		final var nullTokens = new WorldLedgers(liveAliases, liveTokenRels, liveAccounts, liveNfts, null);
		assertFalse(nullTokenRels.areMutable());
		assertFalse(nullAccounts.areMutable());
		assertFalse(nullNfts.areMutable());
		assertFalse(nullTokens.areMutable());
		assertThrows(IllegalStateException.class, () -> nullAccounts.ownerOf(target));

		final var wrappedUnusable = nullAccounts.wrapped();
		assertSame(((StackedContractAliases) wrappedUnusable.aliases()).wrappedAliases(), nullAccounts.aliases());
		assertFalse(wrappedUnusable.areMutable());

		final var wrappedSource = source.wrapped();

		assertSame(liveTokenRels, wrappedSource.tokenRels().getEntitiesLedger());
		assertSame(liveAccounts, wrappedSource.accounts().getEntitiesLedger());
		assertSame(liveNfts, wrappedSource.nfts().getEntitiesLedger());
		assertSame(liveTokens, wrappedSource.tokens().getEntitiesLedger());
		final var stackedAliases = (StackedContractAliases) wrappedSource.aliases();
		assertSame(liveAliases, stackedAliases.wrappedAliases());
	}
}

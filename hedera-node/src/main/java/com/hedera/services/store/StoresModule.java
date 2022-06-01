package com.hedera.services.store;

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

import com.hedera.services.config.AccountNumbers;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.staking.RewardCalculator;
import com.hedera.services.ledger.accounts.staking.StakePeriodManager;
import com.hedera.services.ledger.accounts.staking.StakeInfoManager;
import com.hedera.services.ledger.backing.BackingNfts;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.ledger.backing.BackingTokenRels;
import com.hedera.services.ledger.interceptors.LinkAwareTokenRelsCommitInterceptor;
import com.hedera.services.ledger.interceptors.LinkAwareUniqueTokensCommitInterceptor;
import com.hedera.services.ledger.interceptors.StakeAwareAccountsCommitsInterceptor;
import com.hedera.services.ledger.accounts.staking.StakeChangeManager;
import com.hedera.services.ledger.interceptors.TokenRelsLinkManager;
import com.hedera.services.ledger.interceptors.UniqueTokensLinkManager;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.schedule.HederaScheduleStore;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.store.tokens.annotations.AreTreasuryWildcardsEnabled;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.merkle.map.MerkleMap;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Singleton;
import java.util.function.Supplier;

@Module
public interface StoresModule {
	@Binds
	@Singleton
	TokenStore bindTokenStore(HederaTokenStore hederaTokenStore);

	@Binds
	@Singleton
	BackingStore<NftId, MerkleUniqueToken> bindBackingNfts(
			TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger);

	@Binds
	@Singleton
	ScheduleStore bindScheduleStore(HederaScheduleStore scheduleStore);

	@Provides
	@Singleton
	static TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> provideNftsLedger(
			final UniqueTokensLinkManager uniqueTokensLinkManager,
			final Supplier<MerkleMap<EntityNumPair, MerkleUniqueToken>> uniqueTokens
	) {
		final var uniqueTokensLedger = new TransactionalLedger<>(
				NftProperty.class,
				MerkleUniqueToken::new,
				new BackingNfts(uniqueTokens),
				new ChangeSummaryManager<>());
		final var uniqueTokensCommitInterceptor = new LinkAwareUniqueTokensCommitInterceptor(uniqueTokensLinkManager);
		uniqueTokensLedger.setCommitInterceptor(uniqueTokensCommitInterceptor);
		return uniqueTokensLedger;
	}

	@Provides
	@Singleton
	static TransactionalLedger<TokenID, TokenProperty, MerkleToken> provideTokensLedger(
			final BackingStore<TokenID, MerkleToken> backingTokens,
			final SideEffectsTracker sideEffectsTracker
	) {
		return new TransactionalLedger<>(
				TokenProperty.class,
				MerkleToken::new,
				backingTokens,
				new ChangeSummaryManager<>());
	}

	@Binds
	@Singleton
	BackingStore<Pair<AccountID, TokenID>, MerkleTokenRelStatus> bindBackingTokenRels(
			TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger);

	@Provides
	@Singleton
	static TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> provideTokenRelsLedger(
			final TransactionContext txnCtx,
			final SideEffectsTracker sideEffectsTracker,
			final TokenRelsLinkManager relsLinkManager,
			final Supplier<MerkleMap<EntityNumPair, MerkleTokenRelStatus>> tokenAssociations
	) {
		final var tokenRelsLedger = new TransactionalLedger<>(
				TokenRelProperty.class,
				MerkleTokenRelStatus::new,
				new BackingTokenRels(tokenAssociations),
				new ChangeSummaryManager<>());
		tokenRelsLedger.setKeyToString(BackingTokenRels::readableTokenRel);
		final var interceptor = new LinkAwareTokenRelsCommitInterceptor(txnCtx, sideEffectsTracker, relsLinkManager);
		tokenRelsLedger.setCommitInterceptor(interceptor);
		return tokenRelsLedger;
	}

	@Provides
	@Singleton
	static TransactionalLedger<AccountID, AccountProperty, MerkleAccount> provideAccountsLedger(
			final BackingStore<AccountID, MerkleAccount> backingAccounts,
			final SideEffectsTracker sideEffectsTracker,
			final Supplier<MerkleNetworkContext> networkCtx,
			final GlobalDynamicProperties dynamicProperties,
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final RewardCalculator rewardCalculator,
			final StakeChangeManager manager,
			final StakePeriodManager stakePeriodManager,
			final StakeInfoManager stakeInfoManager,
			final AccountNumbers accountNumbers
	) {
		final var accountsLedger = new TransactionalLedger<>(
				AccountProperty.class,
				MerkleAccount::new,
				backingAccounts,
				new ChangeSummaryManager<>());
		final var accountsCommitInterceptor = new StakeAwareAccountsCommitsInterceptor(sideEffectsTracker,
				networkCtx, dynamicProperties, rewardCalculator, manager, stakePeriodManager,
				stakeInfoManager, accountNumbers);
		accountsLedger.setCommitInterceptor(accountsCommitInterceptor);
		return accountsLedger;
	}

	@Provides
	@AreTreasuryWildcardsEnabled
	static boolean provideAreTreasuryWildcardsEnabled(final @CompositeProps PropertySource properties) {
		return properties.getBooleanProperty("tokens.nfts.useTreasuryWildcards");
	}
}

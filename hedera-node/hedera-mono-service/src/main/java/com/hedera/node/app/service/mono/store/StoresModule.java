/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.store;

import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_STORE_ON_DISK;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_NFTS_USE_TREASURY_WILD_CARDS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_NFTS_USE_VIRTUAL_MERKLE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_STORE_RELS_ON_DISK;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;

import com.hedera.node.app.service.mono.config.AccountNumbers;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.annotations.CompositeProps;
import com.hedera.node.app.service.mono.context.properties.BootstrapProperties;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.mono.ledger.HederaLedger;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.ledger.accounts.staking.RewardCalculator;
import com.hedera.node.app.service.mono.ledger.accounts.staking.StakeChangeManager;
import com.hedera.node.app.service.mono.ledger.accounts.staking.StakeInfoManager;
import com.hedera.node.app.service.mono.ledger.accounts.staking.StakePeriodManager;
import com.hedera.node.app.service.mono.ledger.backing.BackingNfts;
import com.hedera.node.app.service.mono.ledger.backing.BackingStore;
import com.hedera.node.app.service.mono.ledger.backing.BackingTokenRels;
import com.hedera.node.app.service.mono.ledger.backing.BackingTokens;
import com.hedera.node.app.service.mono.ledger.interceptors.LinkAwareTokenRelsCommitInterceptor;
import com.hedera.node.app.service.mono.ledger.interceptors.LinkAwareUniqueTokensCommitInterceptor;
import com.hedera.node.app.service.mono.ledger.interceptors.StakingAccountsCommitInterceptor;
import com.hedera.node.app.service.mono.ledger.interceptors.TokenRelsLinkManager;
import com.hedera.node.app.service.mono.ledger.interceptors.TokensCommitInterceptor;
import com.hedera.node.app.service.mono.ledger.interceptors.UniqueTokensLinkManager;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.ledger.properties.ChangeSummaryManager;
import com.hedera.node.app.service.mono.ledger.properties.NftProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenRelProperty;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.merkle.MerkleTokenRelStatus;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.migration.HederaTokenRel;
import com.hedera.node.app.service.mono.state.migration.TokenRelStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenAdapter;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenMapAdapter;
import com.hedera.node.app.service.mono.state.validation.UsageLimits;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskAccount;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskTokenRel;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.node.app.service.mono.store.schedule.HederaScheduleStore;
import com.hedera.node.app.service.mono.store.schedule.ScheduleStore;
import com.hedera.node.app.service.mono.store.tokens.HederaTokenStore;
import com.hedera.node.app.service.mono.store.tokens.TokenStore;
import com.hedera.node.app.service.mono.store.tokens.annotations.AreTreasuryWildcardsEnabled;
import com.hedera.node.app.service.mono.throttling.FunctionalityThrottling;
import com.hedera.node.app.service.mono.throttling.annotations.HapiThrottle;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;

@Module
public interface StoresModule {

    @Binds
    @Singleton
    TokenStore bindTokenStore(HederaTokenStore hederaTokenStore);

    @Binds
    @Singleton
    BackingStore<NftId, UniqueTokenAdapter> bindBackingNfts(
            TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nftsLedger);

    @Binds
    @Singleton
    ScheduleStore bindScheduleStore(HederaScheduleStore scheduleStore);

    @Provides
    @Singleton
    static WorldLedgers provideWorldLedgers(
            final HederaLedger ledger,
            final AliasManager aliasManager,
            final TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger) {
        return new WorldLedgers(
                aliasManager,
                ledger.getTokenRelsLedger(),
                ledger.getAccountsLedger(),
                ledger.getNftsLedger(),
                tokensLedger);
    }

    @Provides
    @Singleton
    static TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> provideNftsLedger(
            final BootstrapProperties bootstrapProperties,
            final UsageLimits usageLimits,
            final UniqueTokensLinkManager uniqueTokensLinkManager,
            final Supplier<UniqueTokenMapAdapter> uniqueTokens) {
        final boolean isVirtual = bootstrapProperties.getBooleanProperty(TOKENS_NFTS_USE_VIRTUAL_MERKLE);
        final var uniqueTokensLedger = new TransactionalLedger<>(
                NftProperty.class,
                isVirtual ? UniqueTokenAdapter::newEmptyVirtualToken : UniqueTokenAdapter::newEmptyMerkleToken,
                new BackingNfts(uniqueTokens),
                new ChangeSummaryManager<>());
        final var uniqueTokensCommitInterceptor =
                new LinkAwareUniqueTokensCommitInterceptor(usageLimits, uniqueTokensLinkManager);
        uniqueTokensLedger.setCommitInterceptor(uniqueTokensCommitInterceptor);
        return uniqueTokensLedger;
    }

    @Provides
    @Singleton
    static TransactionalLedger<TokenID, TokenProperty, MerkleToken> provideTokensLedger(
            final UsageLimits usageLimits, final Supplier<MerkleMapLike<EntityNum, MerkleToken>> tokens) {
        final var interceptor = new TokensCommitInterceptor(usageLimits);
        final var tokensLedger = new TransactionalLedger<>(
                TokenProperty.class, MerkleToken::new, new BackingTokens(tokens), new ChangeSummaryManager<>());
        tokensLedger.setCommitInterceptor(interceptor);
        return tokensLedger;
    }

    @Binds
    @Singleton
    BackingStore<TokenID, MerkleToken> bindBackingTokens(
            TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger);

    @Binds
    @Singleton
    BackingStore<Pair<AccountID, TokenID>, HederaTokenRel> bindBackingTokenRels(
            TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, HederaTokenRel> tokenRelsLedger);

    @Provides
    @Singleton
    static TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, HederaTokenRel> provideTokenRelsLedger(
            final UsageLimits usageLimits,
            final TransactionContext txnCtx,
            final SideEffectsTracker sideEffectsTracker,
            final TokenRelsLinkManager relsLinkManager,
            final Supplier<HederaTokenRel> tokenRelSupplier,
            final Supplier<TokenRelStorageAdapter> tokenAssociations) {
        final var tokenRelsLedger = new TransactionalLedger<>(
                TokenRelProperty.class,
                MerkleTokenRelStatus::new,
                new BackingTokenRels(tokenAssociations),
                new ChangeSummaryManager<>());
        tokenRelsLedger.setKeyToString(BackingTokenRels::readableTokenRel);
        final var interceptor = new LinkAwareTokenRelsCommitInterceptor(
                usageLimits, txnCtx, sideEffectsTracker, relsLinkManager, tokenRelSupplier);
        tokenRelsLedger.setCommitInterceptor(interceptor);
        return tokenRelsLedger;
    }

    @Provides
    @Singleton
    static IntConsumer provideCryptoCreateThrottleReclaimer(
            @NonNull @HapiThrottle final FunctionalityThrottling hapiThrottling) {
        return n -> {
            try {
                hapiThrottling.leakCapacityForNOfUnscaled(n, CryptoCreate);
            } catch (Exception ignore) {
                // Ignore if the frontend bucket has already leaked all the capacity
                // used for throttling the transaction on the frontend
            }
        };
    }

    @Provides
    @Singleton
    static Supplier<HederaAccount> provideAccountSupplier(final BootstrapProperties bootstrapProperties) {
        return bootstrapProperties.getBooleanProperty(ACCOUNTS_STORE_ON_DISK) ? OnDiskAccount::new : MerkleAccount::new;
    }

    @Provides
    @Singleton
    static Supplier<HederaTokenRel> provideTokenRelSupplier(final BootstrapProperties bootstrapProperties) {
        return bootstrapProperties.getBooleanProperty(TOKENS_STORE_RELS_ON_DISK)
                ? OnDiskTokenRel::new
                : MerkleTokenRelStatus::new;
    }

    @Provides
    @Singleton
    static TransactionalLedger<AccountID, AccountProperty, HederaAccount> provideAccountsLedger(
            final BackingStore<AccountID, HederaAccount> backingAccounts,
            final SideEffectsTracker sideEffectsTracker,
            final BootstrapProperties bootstrapProperties,
            final Supplier<MerkleNetworkContext> networkCtx,
            final GlobalDynamicProperties dynamicProperties,
            final Supplier<HederaAccount> accountSupplier,
            final RewardCalculator rewardCalculator,
            final StakeChangeManager stakeChangeManager,
            final StakePeriodManager stakePeriodManager,
            final StakeInfoManager stakeInfoManager,
            final AccountNumbers accountNumbers,
            final TransactionContext txnCtx,
            final UsageLimits usageLimits) {
        final var accountsLedger = new TransactionalLedger<>(
                AccountProperty.class, accountSupplier, backingAccounts, new ChangeSummaryManager<>());
        final var accountsCommitInterceptor = new StakingAccountsCommitInterceptor(
                sideEffectsTracker,
                networkCtx,
                dynamicProperties,
                rewardCalculator,
                stakeChangeManager,
                stakePeriodManager,
                stakeInfoManager,
                accountNumbers,
                txnCtx,
                usageLimits);
        accountsLedger.setCommitInterceptor(accountsCommitInterceptor);
        return accountsLedger;
    }

    @Provides
    @AreTreasuryWildcardsEnabled
    static boolean provideAreTreasuryWildcardsEnabled(final @CompositeProps PropertySource properties) {
        return properties.getBooleanProperty(TOKENS_NFTS_USE_TREASURY_WILD_CARDS);
    }
}

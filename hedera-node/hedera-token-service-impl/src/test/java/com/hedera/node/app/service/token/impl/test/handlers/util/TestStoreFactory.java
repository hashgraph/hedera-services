/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.test.handlers.util;

import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.ACCOUNTS_KEY;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.ALIASES_KEY;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.TOKENS_KEY;
import static com.hedera.node.app.service.token.impl.test.handlers.util.StateBuilderUtil.AIRDROPS;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountPendingAirdrop;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableNftStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.ReadableAccountStoreImpl;
import com.hedera.node.app.service.token.impl.ReadableNftStoreImpl;
import com.hedera.node.app.service.token.impl.ReadableTokenRelationStoreImpl;
import com.hedera.node.app.service.token.impl.ReadableTokenStoreImpl;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableAirdropStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.test.fixtures.MapReadableStates;
import com.swirlds.state.test.fixtures.MapWritableKVState;
import com.swirlds.state.test.fixtures.MapWritableStates;
import java.util.HashMap;
import java.util.Map;

/**
 * A factory for creating test stores.
 */
public final class TestStoreFactory {

    private static final Configuration CONFIGURATION = HederaTestConfigBuilder.createConfig();

    private TestStoreFactory() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Creates a new {@link ReadableTokenStore} with the given {@link Token}s.
     * @param tokens the tokens to add to the store
     * @return the new store
     */
    public static ReadableTokenStore newReadableStoreWithTokens(Token... tokens) {
        final var wrappedState = newTokenStateFromTokens(tokens);
        return new ReadableTokenStoreImpl(new MapReadableStates(Map.of(TOKENS_KEY, wrappedState)));
    }

    /**
     * Creates a new {@link WritableTokenStore} with the given {@link Token}s.
     * @param tokens the tokens to add to the store
     * @return the new store
     */
    public static WritableTokenStore newWritableStoreWithTokens(Token... tokens) {
        final var wrappedState = newTokenStateFromTokens(tokens);
        return new WritableTokenStore(
                new MapWritableStates(Map.of(TOKENS_KEY, wrappedState)),
                CONFIGURATION,
                mock(StoreMetricsService.class));
    }

    /**
     * Creates a new {@link ReadableAccountStore} with the given {@link Account}s.
     * @param accounts the accounts to add to the store
     * @return the new store
     */
    public static ReadableAccountStore newReadableStoreWithAccounts(Account... accounts) {
        return new ReadableAccountStoreImpl(new MapReadableStates(writableAccountStates(accounts)),CONFIGURATION);
    }

    private static Map<String, MapWritableKVState<?, ?>> writableAccountStates(final Account... accounts) {
        final var wrappingState = newAccountStateFromAccounts(accounts);
        return Map.of(ACCOUNTS_KEY, wrappingState, ALIASES_KEY, new MapWritableKVState<>(ALIASES_KEY, new HashMap<>()));
    }

    private static MapWritableKVState<AccountID, Account> newAccountStateFromAccounts(Account... accounts) {
        final var backingMap = new HashMap<AccountID, Account>();
        for (final Account account : accounts) {
            backingMap.put(account.accountId(), account);
        }

        return new MapWritableKVState<>(ACCOUNTS_KEY, backingMap);
    }

    /**
     * Creates a new {@link WritableAccountStore} with the given {@link Account}s.
     * @param accounts the accounts to add to the store
     * @return the new store
     */
    public static WritableAccountStore newWritableStoreWithAccounts(Account... accounts) {
        return new WritableAccountStore(
                new MapWritableStates(writableAccountStates(accounts)), CONFIGURATION, mock(StoreMetricsService.class));
    }

    /**
     * Creates a new {@link ReadableTokenRelationStore} with the given {@link TokenRelation}s.
     * @param tokenRels the token relations to add to the store
     * @return the new store
     */
    public static ReadableTokenRelationStore newReadableStoreWithTokenRels(final TokenRelation... tokenRels) {
        final var wrappedState = newTokenRelStateFromTokenRels(tokenRels);
        return new ReadableTokenRelationStoreImpl(
                new MapReadableStates(Map.of(V0490TokenSchema.TOKEN_RELS_KEY, wrappedState)));
    }

    private static MapWritableKVState<EntityIDPair, TokenRelation> newTokenRelStateFromTokenRels(
            TokenRelation[] tokenRels) {
        final var backingMap = new HashMap<EntityIDPair, TokenRelation>();
        for (final TokenRelation tokenRel : tokenRels) {
            backingMap.put(
                    EntityIDPair.newBuilder()
                            .accountId(tokenRel.accountId())
                            .tokenId(tokenRel.tokenId())
                            .build(),
                    tokenRel);
        }

        return new MapWritableKVState<>(ACCOUNTS_KEY, backingMap);
    }

    /**
     * Creates a new {@link WritableTokenRelationStore} with the given {@link TokenRelation}s.
     * @param tokenRels the token relations to add to the store
     * @return the new store
     */
    public static WritableTokenRelationStore newWritableStoreWithTokenRels(final TokenRelation... tokenRels) {
        final var wrappingState = newTokenRelStateFromTokenRels(tokenRels);
        return new WritableTokenRelationStore(
                new MapWritableStates(Map.of(V0490TokenSchema.TOKEN_RELS_KEY, wrappingState)),
                CONFIGURATION,
                mock(StoreMetricsService.class));
    }

    /**
     * Creates a new {@link ReadableNftStore} with the given {@link Nft}s.
     * @param nfts the nfts to add to the store
     * @return the new store
     */
    public static ReadableNftStore newReadableStoreWithNfts(Nft... nfts) {
        final var wrappingState = newNftStateFromNfts(nfts);
        return new ReadableNftStoreImpl(new MapReadableStates(Map.of(V0490TokenSchema.NFTS_KEY, wrappingState)));
    }

    /**
     * Creates a new {@link WritableNftStore} with the given {@link Nft}s.
     * @param nfts the nfts to add to the store
     * @return the new store
     */
    public static WritableNftStore newWritableStoreWithNfts(Nft... nfts) {
        final var wrappingState = newNftStateFromNfts(nfts);
        return new WritableNftStore(
                new MapWritableStates(Map.of(V0490TokenSchema.NFTS_KEY, wrappingState)),
                CONFIGURATION,
                mock(StoreMetricsService.class));
    }

    private static MapWritableKVState<TokenID, Token> newTokenStateFromTokens(Token... tokens) {
        final var backingMap = new HashMap<TokenID, Token>();
        for (final Token token : tokens) {
            backingMap.put(token.tokenId(), token);
        }

        return new MapWritableKVState<>(TOKENS_KEY, backingMap);
    }

    private static MapWritableKVState<NftID, Nft> newNftStateFromNfts(Nft... nfts) {
        final var backingMap = new HashMap<NftID, Nft>();
        for (final Nft nft : nfts) {
            backingMap.put(nft.nftId(), nft);
        }

        return new MapWritableKVState<>(V0490TokenSchema.NFTS_KEY, backingMap);
    }

    public static WritableAirdropStore newWritableStoreWithAirdrops(PendingAirdropId... airdrops) {
        return new WritableAirdropStore(
                new MapWritableStates(Map.of(AIRDROPS, newAirdropStateFromAirdrops(airdrops))),
                CONFIGURATION,
                mock(StoreMetricsService.class));
    }

    private static MapWritableKVState<PendingAirdropId, AccountPendingAirdrop> newAirdropStateFromAirdrops(
            PendingAirdropId... airdrops) {
        final var backingMap = new HashMap<PendingAirdropId, AccountPendingAirdrop>();
        for (final PendingAirdropId airdrop : airdrops) {
            backingMap.put(airdrop, AccountPendingAirdrop.newBuilder().build());
        }

        return new MapWritableKVState<>(AIRDROPS, backingMap);
    }
}

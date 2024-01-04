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

import static com.hedera.node.app.service.token.impl.TokenServiceImpl.ACCOUNTS_KEY;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.ALIASES_KEY;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.TOKENS_KEY;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.token.Account;
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
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.spi.fixtures.state.MapReadableStates;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import java.util.HashMap;
import java.util.Map;

public final class TestStoreFactory {
    private TestStoreFactory() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static ReadableTokenStore newReadableStoreWithTokens(Token... tokens) {
        final var wrappedState = newTokenStateFromTokens(tokens);
        return new ReadableTokenStoreImpl(new MapReadableStates(Map.of(TOKENS_KEY, wrappedState)));
    }

    public static WritableTokenStore newWritableStoreWithTokens(Token... tokens) {
        final var wrappedState = newTokenStateFromTokens(tokens);
        return new WritableTokenStore(new MapWritableStates(Map.of(TOKENS_KEY, wrappedState)));
    }

    public static ReadableAccountStore newReadableStoreWithAccounts(Account... accounts) {
        return new ReadableAccountStoreImpl(new MapReadableStates(writableAccountStates(accounts)));
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

    public static WritableAccountStore newWritableStoreWithAccounts(Account... accounts) {
        return new WritableAccountStore(new MapWritableStates(writableAccountStates(accounts)));
    }

    public static ReadableTokenRelationStore newReadableStoreWithTokenRels(final TokenRelation... tokenRels) {
        final var wrappedState = newTokenRelStateFromTokenRels(tokenRels);
        return new ReadableTokenRelationStoreImpl(
                new MapReadableStates(Map.of(TokenServiceImpl.TOKEN_RELS_KEY, wrappedState)));
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

    public static WritableTokenRelationStore newWritableStoreWithTokenRels(final TokenRelation... tokenRels) {
        final var wrappingState = newTokenRelStateFromTokenRels(tokenRels);
        return new WritableTokenRelationStore(
                new MapWritableStates(Map.of(TokenServiceImpl.TOKEN_RELS_KEY, wrappingState)));
    }

    public static ReadableNftStore newReadableStoreWithNfts(Nft... nfts) {
        final var wrappingState = newNftStateFromNfts(nfts);
        return new ReadableNftStoreImpl(new MapReadableStates(Map.of(TokenServiceImpl.NFTS_KEY, wrappingState)));
    }

    public static WritableNftStore newWritableStoreWithNfts(Nft... nfts) {
        final var wrappingState = newNftStateFromNfts(nfts);
        return new WritableNftStore(new MapWritableStates(Map.of(TokenServiceImpl.NFTS_KEY, wrappingState)));
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

        return new MapWritableKVState<>(TokenServiceImpl.NFTS_KEY, backingMap);
    }
}

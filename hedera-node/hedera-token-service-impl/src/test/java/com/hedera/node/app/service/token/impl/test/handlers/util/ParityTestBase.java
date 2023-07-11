/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.mono.pbj.PbjConverter.toPbj;
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
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.ReadableTokenStoreImpl;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler;
import com.hedera.node.app.service.token.impl.test.util.SigReqAdapterUtils;
import com.hedera.node.app.spi.fixtures.state.MapReadableStates;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;

public class ParityTestBase {
    protected ReadableAccountStore readableAccountStore;
    protected WritableAccountStore writableAccountStore;
    protected ReadableTokenStore readableTokenStore;
    protected WritableTokenRelationStore writableTokenRelStore;
    protected TokenID token = TokenID.newBuilder().tokenNum(1).build();

    @BeforeEach
    public void setUp() {
        readableAccountStore = SigReqAdapterUtils.wellKnownAccountStoreAt();
        writableAccountStore = SigReqAdapterUtils.wellKnownWritableAccountStoreAt();
        readableTokenStore = SigReqAdapterUtils.wellKnownTokenStoreAt();
        writableTokenRelStore = SigReqAdapterUtils.wellKnownTokenRelStoreAt();
    }

    protected TransactionBody txnFrom(final TxnHandlingScenario scenario) {
        try {
            return toPbj(scenario.platformTxn().getTxn());
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private MapWritableKVState<TokenID, Token> newTokenStateFromTokens(Token... tokens) {
        final var backingMap = new HashMap<TokenID, Token>();
        for (final Token token : tokens) {
            backingMap.put(token.tokenId(), token);
        }

        return new MapWritableKVState<>(TOKENS_KEY, backingMap);
    }

    protected ReadableTokenStore newReadableStoreWithTokens(Token... tokens) {
        final var wrappedState = newTokenStateFromTokens(tokens);
        return new ReadableTokenStoreImpl(new MapReadableStates(Map.of(TOKENS_KEY, wrappedState)));
    }

    protected WritableTokenStore newWritableStoreWithTokens(Token... tokens) {
        final var wrappedState = newTokenStateFromTokens(tokens);
        return new WritableTokenStore(new MapWritableStates(Map.of(TOKENS_KEY, wrappedState)));
    }

    protected WritableAccountStore newWritableStoreWithAccounts(Account... accounts) {
        final var backingMap = new HashMap<AccountID, Account>();
        for (final Account account : accounts) {
            backingMap.put(BaseCryptoHandler.asAccount(account.accountNumber()), account);
        }

        final var wrappingState = new MapWritableKVState<>(ACCOUNTS_KEY, backingMap);
        return new WritableAccountStore(new MapWritableStates(Map.of(
                ACCOUNTS_KEY, wrappingState, ALIASES_KEY, new MapWritableKVState<>(ALIASES_KEY, new HashMap<>()))));
    }

    protected WritableTokenRelationStore newWritableStoreWithTokenRels(final TokenRelation... tokenRels) {
        final var backingMap = new HashMap<EntityIDPair, TokenRelation>();
        for (final TokenRelation tokenRel : tokenRels) {
            backingMap.put(
                    EntityIDPair.newBuilder()
                            .accountId(tokenRel.accountId())
                            .tokenId(tokenRel.tokenId())
                            .build(),
                    tokenRel);
        }

        final var wrappingState = new MapWritableKVState<>(ACCOUNTS_KEY, backingMap);
        return new WritableTokenRelationStore(
                new MapWritableStates(Map.of(TokenServiceImpl.TOKEN_RELS_KEY, wrappingState)));
    }

    protected WritableNftStore newWritableStoreWithNfts(Nft... nfts) {
        final var nftStateBuilder = MapWritableKVState.<NftID, Nft>builder(TokenServiceImpl.NFTS_KEY);
        for (final Nft nft : nfts) {
            nftStateBuilder.value(nft.id(), nft);
        }
        return new WritableNftStore(new MapWritableStates(Map.of(TokenServiceImpl.NFTS_KEY, nftStateBuilder.build())));
    }
}

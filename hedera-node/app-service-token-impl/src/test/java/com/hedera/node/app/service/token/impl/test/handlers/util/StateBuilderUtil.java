/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.common.UniqueTokenId;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.mono.state.virtual.EntityNumValue;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;

public class StateBuilderUtil {

    public static final String ACCOUNTS = "ACCOUNTS";
    public static final String ALIASES = "ALIASES";
    public static final String TOKENS = "TOKENS";
    public static final String TOKEN_RELS = "TOKEN_RELS";
    public static final String NFTS = "NFTS";

    @NonNull
    protected MapReadableKVState.Builder<AccountID, Account> emptyReadableAccountStateBuilder() {
        return MapReadableKVState.builder(ACCOUNTS);
    }

    @NonNull
    protected MapWritableKVState.Builder<AccountID, Account> emptyWritableAccountStateBuilder() {
        return MapWritableKVState.builder(ACCOUNTS);
    }

    @NonNull
    protected MapReadableKVState.Builder<EntityNumPair, TokenRelation> emptyReadableTokenRelsStateBuilder() {
        return MapReadableKVState.builder(TOKEN_RELS);
    }

    @NonNull
    protected MapWritableKVState.Builder<EntityNumPair, TokenRelation> emptyWritableTokenRelsStateBuilder() {
        return MapWritableKVState.builder(TOKEN_RELS);
    }

    @NonNull
    protected MapReadableKVState.Builder<UniqueTokenId, Nft> emptyReadableNftStateBuilder() {
        return MapReadableKVState.builder(NFTS);
    }

    @NonNull
    protected MapWritableKVState.Builder<UniqueTokenId, Nft> emptyWritableNftStateBuilder() {
        return MapWritableKVState.builder(NFTS);
    }

    @NonNull
    protected MapReadableKVState.Builder<EntityNum, Token> emptyReadableTokenStateBuilder() {
        return MapReadableKVState.builder(TOKENS);
    }

    @NonNull
    protected MapWritableKVState.Builder<EntityNum, Token> emptyWritableTokenStateBuilder() {
        return MapWritableKVState.builder(TOKENS);
    }

    @NonNull
    protected MapWritableKVState.Builder<String, EntityNumValue> emptyWritableAliasStateBuilder() {
        return MapWritableKVState.builder(ALIASES);
    }

    @NonNull
    protected MapReadableKVState.Builder<String, EntityNumValue> emptyReadableAliasStateBuilder() {
        return MapReadableKVState.builder(ALIASES);
    }

    @NonNull
    protected MapWritableKVState<EntityNum, Token> emptyWritableTokenState() {
        return MapWritableKVState.<EntityNum, Token>builder(TOKENS).build();
    }
}

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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountPendingAirdrop;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.swirlds.state.test.fixtures.MapReadableKVState;
import com.swirlds.state.test.fixtures.MapWritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Utility class for building state for tests.
 *
 */
public class StateBuilderUtil {
    /**
     * The state key for accounts.
     */
    public static final String ACCOUNTS = "ACCOUNTS";
    /**
     * The state key for pending airdrops.
     */
    public static final String AIRDROPS = "PENDING_AIRDROPS";
    /**
     * The state key for aliases.
     */
    public static final String ALIASES = "ALIASES";
    /**
     * The state key for tokens.
     */
    public static final String TOKENS = "TOKENS";
    /**
     * The state key for token relations.
     */
    public static final String TOKEN_RELS = "TOKEN_RELS";
    /**
     * The state key for NFTs.
     */
    public static final String NFTS = "NFTS";
    /**
     * The state key for staking infos.
     */
    public static final String STAKING_INFO = "STAKING_INFOS";
    /**
     * The state key for network rewards.
     */
    public static final String NETWORK_REWARDS = "STAKING_NETWORK_REWARDS";

    @NonNull
    protected MapReadableKVState.Builder<AccountID, Account> emptyReadableAccountStateBuilder() {
        return MapReadableKVState.builder(ACCOUNTS);
    }

    @NonNull
    protected MapWritableKVState.Builder<AccountID, Account> emptyWritableAccountStateBuilder() {
        return MapWritableKVState.builder(ACCOUNTS);
    }

    @NonNull
    protected MapReadableKVState.Builder<PendingAirdropId, AccountPendingAirdrop> emptyReadableAirdropStateBuilder() {
        return MapReadableKVState.builder(AIRDROPS);
    }

    @NonNull
    protected MapWritableKVState.Builder<PendingAirdropId, AccountPendingAirdrop> emptyWritableAirdropStateBuilder() {
        return MapWritableKVState.builder(AIRDROPS);
    }

    @NonNull
    protected MapReadableKVState.Builder<EntityIDPair, TokenRelation> emptyReadableTokenRelsStateBuilder() {
        return MapReadableKVState.builder(TOKEN_RELS);
    }

    @NonNull
    protected MapWritableKVState.Builder<EntityIDPair, TokenRelation> emptyWritableTokenRelsStateBuilder() {
        return MapWritableKVState.builder(TOKEN_RELS);
    }

    @NonNull
    protected MapReadableKVState.Builder<NftID, Nft> emptyReadableNftStateBuilder() {
        return MapReadableKVState.builder(NFTS);
    }

    @NonNull
    protected MapWritableKVState.Builder<NftID, Nft> emptyWritableNftStateBuilder() {
        return MapWritableKVState.builder(NFTS);
    }

    @NonNull
    protected MapReadableKVState.Builder<TokenID, Token> emptyReadableTokenStateBuilder() {
        return MapReadableKVState.builder(TOKENS);
    }

    @NonNull
    protected MapWritableKVState.Builder<TokenID, Token> emptyWritableTokenStateBuilder() {
        return MapWritableKVState.builder(TOKENS);
    }

    @NonNull
    protected MapWritableKVState.Builder<ProtoBytes, AccountID> emptyWritableAliasStateBuilder() {
        return MapWritableKVState.builder(ALIASES);
    }

    @NonNull
    protected MapReadableKVState.Builder<ProtoBytes, AccountID> emptyReadableAliasStateBuilder() {
        return MapReadableKVState.builder(ALIASES);
    }

    @NonNull
    protected MapWritableKVState<TokenID, Token> emptyWritableTokenState() {
        return MapWritableKVState.<TokenID, Token>builder(TOKENS).build();
    }
}

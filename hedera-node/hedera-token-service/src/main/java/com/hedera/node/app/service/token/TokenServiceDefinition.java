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

package com.hedera.node.app.service.token;

import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.hapi.node.transaction.TransactionResponse;
import com.hedera.pbj.runtime.RpcMethodDefinition;
import com.hedera.pbj.runtime.RpcServiceDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Transactions and queries for the Token Service
 */
@SuppressWarnings("java:S6548")
public final class TokenServiceDefinition implements RpcServiceDefinition {
    public static final TokenServiceDefinition INSTANCE = new TokenServiceDefinition();

    private static final Set<RpcMethodDefinition<?, ?>> methods = Set.of(
            new RpcMethodDefinition<>("createToken", Transaction.class, TransactionResponse.class),
            new RpcMethodDefinition<>("updateToken", Transaction.class, TransactionResponse.class),
            new RpcMethodDefinition<>("mintToken", Transaction.class, TransactionResponse.class),
            new RpcMethodDefinition<>("burnToken", Transaction.class, TransactionResponse.class),
            new RpcMethodDefinition<>("deleteToken", Transaction.class, TransactionResponse.class),
            new RpcMethodDefinition<>("wipeTokenAccount", Transaction.class, TransactionResponse.class),
            new RpcMethodDefinition<>("freezeTokenAccount", Transaction.class, TransactionResponse.class),
            new RpcMethodDefinition<>("unfreezeTokenAccount", Transaction.class, TransactionResponse.class),
            new RpcMethodDefinition<>("grantKycToTokenAccount", Transaction.class, TransactionResponse.class),
            new RpcMethodDefinition<>("revokeKycFromTokenAccount", Transaction.class, TransactionResponse.class),
            new RpcMethodDefinition<>("associateTokens", Transaction.class, TransactionResponse.class),
            new RpcMethodDefinition<>("dissociateTokens", Transaction.class, TransactionResponse.class),
            new RpcMethodDefinition<>("updateTokenFeeSchedule", Transaction.class, TransactionResponse.class),
            new RpcMethodDefinition<>("getTokenInfo", Query.class, Response.class),
            new RpcMethodDefinition<>("getAccountNftInfos", Query.class, Response.class),
            new RpcMethodDefinition<>("getTokenNftInfo", Query.class, Response.class),
            new RpcMethodDefinition<>("getTokenNftInfos", Query.class, Response.class),
            new RpcMethodDefinition<>("pauseToken", Transaction.class, TransactionResponse.class),
            new RpcMethodDefinition<>("updateNfts", Transaction.class, TransactionResponse.class),
            new RpcMethodDefinition<>("unpauseToken", Transaction.class, TransactionResponse.class));

    private TokenServiceDefinition() {
        // Forbid instantiation
    }

    @Override
    @NonNull
    public String basePath() {
        return "proto.TokenService";
    }

    @Override
    @NonNull
    public Set<RpcMethodDefinition<?, ?>> methods() {
        return methods;
    }
}

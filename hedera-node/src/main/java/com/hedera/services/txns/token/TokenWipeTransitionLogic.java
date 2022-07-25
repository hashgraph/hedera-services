/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.token;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.OwnershipTracker;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.utils.accessors.TokenWipeAccessor;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Provides the state transition for wiping [part of] a token balance. */
@Singleton
public class TokenWipeTransitionLogic implements TransitionLogic {
    private final TransactionContext txnCtx;
    private final TypedTokenStore tokenStore;
    private final AccountStore accountStore;

    @Inject
    public TokenWipeTransitionLogic(
            final TypedTokenStore tokenStore,
            final AccountStore accountStore,
            final TransactionContext txnCtx) {
        this.txnCtx = txnCtx;
        this.tokenStore = tokenStore;
        this.accountStore = accountStore;
    }

    @Override
    public void doStateTransition() {
        /* --- Translate from gRPC types --- */
        final var accessor = (TokenWipeAccessor) txnCtx.swirldsTxnAccessor().getDelegate();
        final var targetTokenId = accessor.targetToken();
        final var targetAccountId = accessor.accountToWipe();
        final var serialNums = accessor.serialNums();
        final var amount = accessor.amount();

        /* --- Load the model objects --- */
        final var token = tokenStore.loadToken(targetTokenId);
        final var account = accountStore.loadAccount(targetAccountId);
        final var accountRel = tokenStore.loadTokenRelationship(token, account);

        /* --- Instantiate change trackers --- */
        final var ownershipTracker = new OwnershipTracker();

        /* --- Do the business logic --- */
        if (token.getType().equals(TokenType.FUNGIBLE_COMMON)) {
            token.wipe(accountRel, amount);
        } else {
            tokenStore.loadUniqueTokens(token, serialNums);
            token.wipe(ownershipTracker, accountRel, serialNums);
        }
        /* --- Persist the updated models --- */
        tokenStore.commitToken(token);
        tokenStore.commitTokenRelationships(List.of(accountRel));
        tokenStore.commitTrackers(ownershipTracker);
        accountStore.commitAccount(account);
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasTokenWipe;
    }
}

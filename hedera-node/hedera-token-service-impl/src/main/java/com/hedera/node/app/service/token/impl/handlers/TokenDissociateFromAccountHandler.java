/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.*;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.TokenDissociateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.validators.TokenListChecks;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.AutoRenewConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_DISSOCIATE_FROM_ACCOUNT}.
 */
@Singleton
public class TokenDissociateFromAccountHandler implements TransactionHandler {
    @Inject
    public TokenDissociateFromAccountHandler() {
        // Exists for injection
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().tokenDissociateOrThrow();

        final var target = op.accountOrElse(AccountID.DEFAULT);

        context.requireKeyOrThrow(target, INVALID_ACCOUNT_ID);
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * <p>
     * Dissociates a token with an account, preventing various token operations from happening with
     * respect to the given account
     *
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(@NonNull final HandleContext context) {
        requireNonNull(context);
        final var accountStore = context.writableStore(WritableAccountStore.class);
        final var tokenStore = context.readableStore(ReadableTokenStore.class);
        final var tokenRelStore = context.writableStore(WritableTokenRelationStore.class);
        final var txn = context.body();
        final var op = txn.tokenDissociateOrThrow();
        final var tokenIds = op.tokensOrThrow();
        final var autoRenewConfig = context.configuration().getConfigData(AutoRenewConfig.class);
        final var dissociations = validateSemantics(
                op.accountOrThrow(), tokenIds, accountStore, tokenStore, tokenRelStore, autoRenewConfig);

        // @todo: finish implementation
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        final TokenDissociateTransactionBody op = txn.tokenDissociateOrThrow();
        if (!op.hasAccount()) {
            throw new PreCheckException(INVALID_ACCOUNT_ID);
        }

        if (TokenListChecks.repeatsItself(op.tokensOrThrow())) {
            throw new PreCheckException(TOKEN_ID_REPEATED_IN_TOKEN_LIST);
        }
    }

    /**
     * Performs checks that require state and context
     */
    @NonNull
    private List<Dissociation> validateSemantics(
            @NonNull final AccountID accountId,
            @NonNull final List<TokenID> tokenIds,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final ReadableTokenStore tokenStore,
            @NonNull final WritableTokenRelationStore tokenRelStore,
            @NonNull final AutoRenewConfig autoRenewConfig) {
        // Check that the account is usable
        final var acct = ContextualRetriever.getIfUsable(accountId, accountStore, autoRenewConfig);

        // Construct the dissociation for each token ID
        final var dissociations = new ArrayList<Dissociation>();
        for (final var tokenId : tokenIds) {
            final var tokenRel = tokenRelStore.get(accountId, tokenId).orElse(null);
            validateTrue(tokenRel != null, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);

            // Here we check/retrieve a token that may not be "usable," but since we are dissociating token relations,
            // we don't require a usable token (or even an existing token). We only need to update the token relation
            final var possiblyUnusableToken = tokenStore.get(tokenId);
            final TokenRelation dissociatedTokenTreasuryRel;
            if (possiblyUnusableToken != null) {
                validateFalse(possiblyUnusableToken.paused(), TOKEN_IS_PAUSED);
                final var tokenTreasuryAcct = AccountID.newBuilder()
                        .accountNum(possiblyUnusableToken.treasuryAccountNumber())
                        .build();
                dissociatedTokenTreasuryRel =
                        tokenRelStore.get(tokenTreasuryAcct, tokenId).orElse(null);
            } else {
                // If the token isn't found, assume the treasury rel is null
                dissociatedTokenTreasuryRel = null;
            }

            final Dissociation dissociation =
                    new Dissociation(possiblyUnusableToken, tokenRel, acct, dissociatedTokenTreasuryRel);
            dissociations.add(dissociation);
        }

        return dissociations;
    }

    private record Dissociation(
            @Nullable Token token,
            @NonNull TokenRelation tokenRel,
            @NonNull Account acct,
            @Nullable TokenRelation treasuryTokenRel) {}
}

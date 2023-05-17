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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.TokenAssociateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_ASSOCIATE_TO_ACCOUNT}.
 */
@Singleton
public class TokenAssociateToAccountHandler implements TransactionHandler {
    @Inject
    public TokenAssociateToAccountHandler() {
        // Exists for injection
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().tokenAssociateOrThrow();
        pureChecks(op);

        final var target = op.accountOrElse(AccountID.DEFAULT);
        context.requireKeyOrThrow(target, INVALID_ACCOUNT_ID);
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * <p>
     * Associates a token with an account, making various token operations possible with the account
     *
     * @param txn           the {@link TokenAssociateTransactionBody} for the active transaction
     * @param context       the {@link HandleContext} for the active transaction
     * @param accountStore  the {@link WritableAccountStore} for the active transaction
     * @param tokenStore    the {@link ReadableTokenStore} for the active transaction
     * @param tokenRelStore the {@link WritableTokenRelationStore} for the active transaction
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(
            @NonNull final TransactionBody txn,
            @NonNull final HandleContext context,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final ReadableTokenStore tokenStore,
            @NonNull final WritableTokenRelationStore tokenRelStore) {
        requireNonNull(txn);
        requireNonNull(context);
        requireNonNull(accountStore);
        requireNonNull(tokenStore);
        requireNonNull(tokenRelStore);
        final var op = txn.tokenAssociateOrThrow();
        final var tokenIds = op.tokensOrThrow();
        final var validated = validateSemantics(
                tokenIds, op.accountOrThrow(), context.getConfiguration(), accountStore, tokenStore, tokenRelStore);

        // Now that we've validated we can link all the new token IDs to the account,
        // create the corresponding token relations and update the account
        createAndLinkTokenRels(validated.account(), validated.tokens(), accountStore, tokenRelStore);
    }

    private void createAndLinkTokenRels(
            @NonNull final Account account,
            @NonNull final List<Token> tokens,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final WritableTokenRelationStore tokenRelStore) {
        final var newTokenRels = new ArrayList<TokenRelation>();
        for (int i = 0; i < tokens.size(); i++) {
            final var token = tokens.get(i);
            // Link each of the new token IDs together in a doubly-linked list way by setting each
            // token relation's previous and next token IDs.

            // Compute the previous and next token IDs. Unfortunately `TokenRelation` doesn't
            // allow for null values, so a value of '0' will have to indicate a null pointer to
            // the previous or next token (since no token number 0 can exist)
            long prevTokenId = 0;
            long nextTokenId = 0;
            if (i - 1 >= 0) { // if there is a previous token
                prevTokenId = Optional.ofNullable(tokens.get(i - 1))
                        .map(Token::tokenNumber)
                        .orElse(0L);
            }
            if (i + 1 < tokens.size()) { // if there is a next token
                nextTokenId = Optional.ofNullable(tokens.get(i + 1))
                        .map(Token::tokenNumber)
                        .orElse(0L);
            }

            // Create the new token relation
            final var isFrozen = token.hasFreezeKey() && token.accountsFrozenByDefault();
            final var kycGranted = !token.hasKycKey();
            final var newTokenRel = new TokenRelation(
                    token.tokenNumber(),
                    account.accountNumber(),
                    0,
                    isFrozen,
                    kycGranted,
                    false,
                    false,
                    prevTokenId,
                    nextTokenId);
            newTokenRels.add(newTokenRel);
        }

        // Now all the NEW token relations are linked together, but they are not yet linked to
        // the account. First,
        // compute where the account's current head token number should go in the linked list of
        // tokens
        final var currentHeadTokenNum = account.headTokenNumber();
        // NOTE: if currentHeadTokenNum is less than 1, it means the account isn't associated
        // with any tokens yet, so
        // we'll just set the head to the first token, i.e. the first token ID list from the
        // transaction (since the new
        // tokenRels are all linked, and in the order of the token IDs as they appeared in the
        // original list)
        if (currentHeadTokenNum >= 1) {
            // The account is already associated with some tokens, so we need to insert the new
            // tokenRels at the beginning of the list of existing token numbers first. We start by
            // retrieving the token rel object with the currentHeadTokenNum at the head of the
            // account
            final var headTokenRel = tokenRelStore
                    .get(account.accountNumber(), currentHeadTokenNum)
                    .orElse(null);
            validateTrue(headTokenRel != null, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);

            // Recreate the current head token's tokenRel, but with its previous pointer set to
            // the last of the new tokenRels. This links the new token rels to the rest of the
            // token rels connected via the old head token rel
            final var lastOfNewTokenRels = newTokenRels.remove(newTokenRels.size() - 1);
            final var headTokenAsNonHeadTokenRel = headTokenRel
                    .copyBuilder()
                    .previousToken(lastOfNewTokenRels.tokenNumber())
                    .build(); // the old head token rel is no longer the head
            // Also connect the last of the new tokenRels to the old head token rel
            newTokenRels.add(lastOfNewTokenRels
                    .copyBuilder()
                    .nextToken(headTokenAsNonHeadTokenRel.tokenNumber())
                    .build());
            tokenRelStore.put(headTokenAsNonHeadTokenRel);
        }

        // Now replace the account's old head token number with the new head token number. This is
        // how we link the new tokenRels to the account
        final var firstOfNewTokenRels = newTokenRels.get(0);
        final var updatedAcct = account.copyBuilder()
                // replace the head token number with the first token number of the new tokenRels
                .headTokenNumber(firstOfNewTokenRels.tokenNumber())
                // and also update the account's total number of token associations
                .numberAssociations(account.numberAssociations() + newTokenRels.size())
                .build();

        // Save the results
        accountStore.put(updatedAcct);
        newTokenRels.forEach(tokenRelStore::put);
    }

    /**
     * Performs checks independent of state or context
     */
    private void pureChecks(@NonNull final TokenAssociateTransactionBody op) throws PreCheckException {
        if (!op.hasAccount()) {
            throw new PreCheckException(ResponseCodeEnum.INVALID_ACCOUNT_ID);
        }

        if (TokenListChecks.repeatsItself(op.tokensOrThrow())) {
            throw new PreCheckException(TOKEN_ID_REPEATED_IN_TOKEN_LIST);
        }
    }

    /**
     * Performs checks that require state and context
     */
    @NonNull
    private Validated validateSemantics(
            @NonNull final List<TokenID> tokenIds,
            @NonNull final AccountID accountId,
            @NonNull final Configuration config,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final ReadableTokenStore tokenStore,
            @NonNull final WritableTokenRelationStore tokenRelStore) {
        requireNonNull(config);

        // Check that the system hasn't reached its limit of token associations
        var maxTokenRels = config.getValue("maxNumTokenRels", Long.class);
        validateTrue(
                isTotalNumTokenRelsWithinMax(tokenIds.size(), tokenRelStore, maxTokenRels),
                MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);

        // Check that the account exists
        final var account = accountStore.get(accountId).orElse(null);
        validateTrue(account != null, INVALID_ACCOUNT_ID);

        // Check that the given tokens exist and are usable
        final var tokens = new ArrayList<Token>();
        for (final TokenID tokenId : tokenIds) {
            final var token = tokenStore.getToken(tokenId);
            validateTrue(token != null, INVALID_TOKEN_ID);
            validateFalse(token.deleted(), TOKEN_WAS_DELETED);
            validateFalse(token.paused(), TOKEN_IS_PAUSED);
            tokens.add(token);
        }

        // Check that the total number of old and new token IDs wouldn't be bigger than
        // the max number of token associations allowed per account (if the rel limit is enabled)
        validateTrue(
                maxAccountAssociationsAllowTokenRels(config, account, tokenIds), TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);

        // Check that a token rel doesn't already exist for each new token ID
        for (final TokenID tokenId : tokenIds) {
            final var existingTokenRel = tokenRelStore
                    .get(tokenId.tokenNum(), accountId.accountNumOrThrow())
                    .orElse(null);
            validateTrue(existingTokenRel == null, TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT);
        }

        return new Validated(account, tokens);
    }

    private boolean isTotalNumTokenRelsWithinMax(
            final int numNewTokenRels, WritableTokenRelationStore tokenRelStore, long maxNumTokenRels) {
        return tokenRelStore.sizeOfState() + numNewTokenRels <= maxNumTokenRels;
    }

    /**
     * Method that checks if the number of token associations for the given account is within the
     * allowable limit set by the config (if the limit is enabled)
     */
    private boolean maxAccountAssociationsAllowTokenRels(
            @NonNull Configuration config, @NonNull Account account, @NonNull List<TokenID> tokenIds) {
        final var numAssociations = requireNonNull(account).numberAssociations();
        final var tokenAssociationsLimited = config.getValue("areTokenAssociationsLimited", Boolean.class);
        final var maxTokensPerAccount = config.getValue("maxTokensPerAccount", Long.class);
        return !tokenAssociationsLimited || (numAssociations + tokenIds.size() <= maxTokensPerAccount);
    }

    private record Validated(@NonNull Account account, @NonNull List<Token> tokens) {}
}

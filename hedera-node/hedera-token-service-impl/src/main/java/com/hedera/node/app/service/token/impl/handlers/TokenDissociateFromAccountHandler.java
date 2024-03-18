/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hedera.hapi.node.base.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.node.app.hapi.fees.usage.crypto.CryptoOpsUsage.txnEstimateFactory;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.TokenDissociateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.fees.calculation.token.txns.TokenDissociateResourceUsage;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.util.TokenHandlerHelper;
import com.hedera.node.app.service.token.impl.util.TokenRelListCalculator;
import com.hedera.node.app.service.token.impl.validators.TokenListChecks;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
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
    // a sentinel value for headTokenId to indicate that no tokens are associated with the account
    private static final TokenID NO_ASSOCIATED_TOKENS =
            TokenID.newBuilder().tokenNum(-1).build();

    @Inject
    public TokenDissociateFromAccountHandler() {}

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var trxBody = context.body();
        final var op = trxBody.tokenDissociateOrThrow();
        pureChecks(trxBody);

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
     * <p>
     * <b>Note:</b> this method will NOT apply account operation changes by account ID
     *
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(@NonNull final HandleContext context) {
        requireNonNull(context);
        final var accountStore = context.writableStore(WritableAccountStore.class);
        final var tokenStore = context.readableStore(ReadableTokenStore.class);
        final var tokenRelStore = context.writableStore(WritableTokenRelationStore.class);
        final var expiryValidator = context.expiryValidator();
        final var txn = context.body();
        final var op = txn.tokenDissociateOrThrow();
        final var tokenIds = op.tokensOrThrow();
        final var validated = validateSemantics(
                op.accountOrThrow(), tokenIds, accountStore, tokenStore, tokenRelStore, expiryValidator);

        // Update the account and relevant token relations
        // Note: since we can't query an Account copyBuilder for current values, and since the account object is
        // undergoing an update (i.e. we can't accurately query account.numXYZs() at any arbitrary point during the
        // following loop), we have to keep track of the aggregate number of NFTs, auto associations, associations, and
        // positive balances to remove from the account separate from the account object itself. Confusing, but we'll
        // _add_ the number to subtract to each aggregating variable. The total subtraction for each variable will be
        // done outside the dissociation loop
        var numNftsToSubtract = 0;
        var numAutoAssociationsToSubtract = 0;
        var numAssociationsToSubtract = 0;
        var numPositiveBalancesToSubtract = 0;
        final var account = validated.account();
        final var tokenRelsToRemove = new ArrayList<TokenRelation>();
        final var treasuryBalancesToUpdate = new ArrayList<TokenRelation>();
        for (final var dissociation : validated.dissociations()) {
            final var tokenRel = dissociation.tokenRel();
            final var tokenRelBalance = tokenRel.balance();
            final var token = dissociation.token();

            // Handle dissociation from an inactive (deleted or removed) token
            if (token == null || token.deleted()) {
                // Nothing to do here for a fungible token, downstream code already
                // "burns" our held units
                if (token != null && token.tokenType() == NON_FUNGIBLE_UNIQUE) {
                    // Downstream code already takes care of decrementing the number of
                    // positive balances in the case we owned serial numbers of this type
                    numNftsToSubtract += tokenRelBalance;
                }
            } else {
                // Handle active tokens
                validateFalse(
                        token.treasuryAccountId() != null
                                && token.treasuryAccountId().equals(tokenRel.accountId()),
                        ACCOUNT_IS_TREASURY);
                validateFalse(tokenRel.frozen(), ACCOUNT_FROZEN_FOR_TOKEN);

                if (tokenRelBalance > 0) {
                    validateFalse(token.tokenType() == NON_FUNGIBLE_UNIQUE, ACCOUNT_STILL_OWNS_NFTS);

                    final var tokenIsExpired = tokenIsExpired(token, context.consensusNow());
                    validateTrue(tokenIsExpired, TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES);

                    // If the fungible common token is expired, we automatically transfer the
                    // dissociating account's balance back to the token's treasury
                    final var treasuryTokenRel = dissociation.treasuryTokenRel();
                    if (treasuryTokenRel != null) {
                        final var updatedTreasuryBalanceTokenRel = treasuryTokenRel.balance() + tokenRelBalance;
                        treasuryBalancesToUpdate.add(treasuryTokenRel
                                .copyBuilder()
                                .balance(updatedTreasuryBalanceTokenRel)
                                .build());
                    }
                }
            }

            // Regardless of the token status, now the token relation with the given account ID must be updated to zero
            final var updatedBalanceTokenRel =
                    tokenRel.copyBuilder().balance(0L).build();
            tokenRelsToRemove.add(updatedBalanceTokenRel);

            if (tokenRel.automaticAssociation()) {
                numAutoAssociationsToSubtract++;
            }

            if (tokenRel.balance() != 0) {
                numPositiveBalancesToSubtract++;
            }

            numAssociationsToSubtract++;
        }

        // get changes to account and token relations
        final var updatedTokenRels =
                new TokenRelListCalculator(tokenRelStore).removeTokenRels(account, tokenRelsToRemove);
        final var newHeadTokenId = updatedTokenRels.updatedHeadTokenId();

        // Update the account with the aggregate number of NFTs, auto associations, associations, and positive balances
        // to remove, as well as the new head token number
        final var updatedAcct = account.copyBuilder()
                .numberOwnedNfts(account.numberOwnedNfts() - numNftsToSubtract)
                .usedAutoAssociations(account.usedAutoAssociations() - numAutoAssociationsToSubtract)
                .numberAssociations(account.numberAssociations() - numAssociationsToSubtract)
                .numberPositiveBalances(account.numberPositiveBalances() - numPositiveBalancesToSubtract)
                .headTokenId(newHeadTokenId == null ? NO_ASSOCIATED_TOKENS : newHeadTokenId)
                .build();

        // Finally, update the account and the token relations via their respective stores
        accountStore.put(updatedAcct);
        updatedTokenRels.updatedTokenRelsStillInChain().forEach(tokenRelStore::put);
        tokenRelsToRemove.forEach(tokenRelStore::remove);
        treasuryBalancesToUpdate.forEach(tokenRelStore::put);
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        final TokenDissociateTransactionBody op = txn.tokenDissociateOrThrow();

        validateTruePreCheck(op.hasAccount(), INVALID_ACCOUNT_ID);

        validateTruePreCheck(!TokenListChecks.repeatsItself(op.tokensOrThrow()), TOKEN_ID_REPEATED_IN_TOKEN_LIST);
    }

    /**
     * Performs checks that require state and context
     */
    @NonNull
    private ValidatedResult validateSemantics(
            @NonNull final AccountID accountId,
            @NonNull final List<TokenID> tokenIds,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final ReadableTokenStore tokenStore,
            @NonNull final WritableTokenRelationStore tokenRelStore,
            @NonNull final ExpiryValidator expiryValidator) {
        // Check that the account is usable
        final var acct = TokenHandlerHelper.getIfUsable(accountId, accountStore, expiryValidator, INVALID_ACCOUNT_ID);

        // Construct the dissociation for each token ID
        final var dissociations = new ArrayList<Dissociation>();
        for (final var tokenId : tokenIds) {
            final var tokenRel = tokenRelStore.get(accountId, tokenId);
            validateTrue(tokenRel != null, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);

            // Here we check/retrieve a token that may not be "usable," but since we are dissociating token relations,
            // we don't require a usable token (or even an existing token). We only need to update the token relation
            final var possiblyUnusableToken = tokenStore.get(tokenId);
            final TokenRelation dissociatedTokenTreasuryRel;
            if (possiblyUnusableToken != null) {
                validateFalse(possiblyUnusableToken.paused(), TOKEN_IS_PAUSED);
                // If there is no treasury, or the token is deleted, we don't return
                // the dissociated balance to the treasury
                if (!possiblyUnusableToken.deleted() && possiblyUnusableToken.treasuryAccountId() != null) {
                    final var tokenTreasuryAcct = possiblyUnusableToken.treasuryAccountId();
                    dissociatedTokenTreasuryRel = tokenRelStore.get(tokenTreasuryAcct, tokenId);
                } else {
                    dissociatedTokenTreasuryRel = null;
                }
            } else {
                // If the token isn't found, assume the treasury token rel is null
                dissociatedTokenTreasuryRel = null;
            }

            final Dissociation dissociation =
                    new Dissociation(possiblyUnusableToken, tokenRel, acct, dissociatedTokenTreasuryRel);
            dissociations.add(dissociation);
        }

        return new ValidatedResult(acct, dissociations);
    }

    private boolean tokenIsExpired(final Token token, final Instant consensusNow) {
        return token.expirationSecond() <= consensusNow.getEpochSecond();
    }

    private record ValidatedResult(@NonNull Account account, @NonNull List<Dissociation> dissociations) {}

    private record Dissociation(
            @Nullable Token token,
            // This is the relation of the submitted account ID and the token ID
            @NonNull TokenRelation tokenRel,
            @NonNull Account acct,
            // This is the relation of the token's treasury account ID and the token ID
            @Nullable TokenRelation treasuryTokenRel) {}

    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        requireNonNull(feeContext);
        final var body = feeContext.body();
        final var op = body.tokenDissociateOrThrow();
        final var accountId = op.accountOrThrow();
        final var readableAccountStore = feeContext.readableStore(ReadableAccountStore.class);
        final var account = readableAccountStore.getAccountById(accountId);

        return feeContext
                .feeCalculator(SubType.DEFAULT)
                .legacyCalculate(sigValueObj -> new TokenDissociateResourceUsage(txnEstimateFactory)
                        .usageGiven(fromPbj(body), sigValueObj, account));
    }
}

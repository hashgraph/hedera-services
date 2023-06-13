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

package com.hedera.node.app.service.token.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.*;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.util.IdConvenienceUtils.isValidTokenNum;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseTokenHandler {
    private static final Logger log = LoggerFactory.getLogger(BaseTokenHandler.class);
    /**
     * Mints fungible tokens. This method is called in both token create and mint.
     * @param token the new or existing token to mint
     * @param treasuryRel the treasury relation for the token
     * @param amount the amount to mint
     * @param isMintOnTokenCreation true if this is a mint on token creation
     * @param accountStore the account store
     * @param tokenStore the token store
     * @param tokenRelationStore the token relation store
     */
    protected void mintFungible(
            @NonNull final Token token,
            @NonNull final TokenRelation treasuryRel,
            final long amount,
            final boolean isMintOnTokenCreation,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final WritableTokenStore tokenStore,
            @NonNull final WritableTokenRelationStore tokenRelationStore) {
        requireNonNull(token);
        requireNonNull(treasuryRel);

        validateTrue(amount >= 0, INVALID_TOKEN_MINT_AMOUNT);
        // validate token supply key exists for mint or burn.
        // But this flag is not set when mint is called on token creation with initial supply.
        // We don't need to check the supply key ONLY in that case
        if (!isMintOnTokenCreation) {
            validateTrue(token.supplyKey() != null, TOKEN_HAS_NO_SUPPLY_KEY);
        }
        changeSupply(
                token, treasuryRel, +amount, INVALID_TOKEN_MINT_AMOUNT, accountStore, tokenStore, tokenRelationStore);
    }

    /**
     * Since token mint and token burn change the supply on the token and treasury account,
     * this method is used to change the supply.
     *
     * <p>
     * <b>Note:</b> This method assumes the given token has a non-null supply key!
     *
     * @param token the token that is minted or burned
     * @param treasuryRel the treasury relation for the token
     * @param amount the amount to mint or burn
     * @param invalidSupplyCode the invalid supply code to use if the supply is invalid
     * @param accountStore the account store
     * @param tokenStore the token store
     * @param tokenRelationStore the token relation store
     */
    protected void changeSupply(
            @NonNull final Token token,
            @NonNull final TokenRelation treasuryRel,
            final long amount,
            @NonNull final ResponseCodeEnum invalidSupplyCode,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final WritableTokenStore tokenStore,
            @NonNull final WritableTokenRelationStore tokenRelationStore) {
        requireNonNull(token);
        requireNonNull(treasuryRel);
        requireNonNull(invalidSupplyCode);

        validateTrue(
                treasuryRel.accountNumber() == token.treasuryAccountNumber()
                        && token.tokenNumber() == treasuryRel.tokenNumber(),
                FAIL_INVALID);
        final long newTotalSupply = token.totalSupply() + amount;

        // validate that the new total supply is not negative after mint or burn or wipe
        // FUTURE - All these checks that return FAIL_INVALID probably should end up in a
        // finalize method in token service to validate everything before we commit
        validateTrue(newTotalSupply >= 0, invalidSupplyCode);

        if (token.supplyType() == TokenSupplyType.FINITE) {
            validateTrue(token.maxSupply() >= newTotalSupply, TOKEN_MAX_SUPPLY_REACHED);
        }

        final var treasuryAccount = accountStore.get(asAccount(treasuryRel.accountNumber()));
        validateTrue(treasuryAccount != null, INVALID_TREASURY_ACCOUNT_FOR_TOKEN);

        final long newTreasuryBalance = treasuryRel.balance() + amount;
        validateTrue(newTreasuryBalance >= 0, INSUFFICIENT_TOKEN_BALANCE);

        // copy the token, treasury account and treasury relation
        final var copyTreasuryAccount = treasuryAccount.copyBuilder();
        final var copyToken = token.copyBuilder();
        final var copyTreasuryRel = treasuryRel.copyBuilder();

        if (treasuryRel.balance() == 0 && amount > 0) {
            // On an account positive balances are incremented for newly added tokens.
            // If treasury relation did mint any for this token till now, only then increment
            // total positive balances on treasury account.
            copyTreasuryAccount.numberPositiveBalances(treasuryAccount.numberPositiveBalances() + 1);
        } else if (newTreasuryBalance == 0 && amount < 0) {
            // On an account positive balances are decremented for burning tokens completely.
            // If treasury relation did not burn any for this token till now or if this burn makes the balance to 0,
            // only then decrement total positive balances on treasury account.
            copyTreasuryAccount.numberPositiveBalances(treasuryAccount.numberPositiveBalances() - 1);
        }

        // since we have either minted or burned tokens, we need to update the total supply
        copyToken.totalSupply(newTotalSupply);
        copyTreasuryRel.balance(newTreasuryBalance);

        // put the changed token, treasury account and treasury relation
        accountStore.put(copyTreasuryAccount.build());
        tokenStore.put(copyToken.build());
        tokenRelationStore.put(copyTreasuryRel.build());
    }

    /**
     * Creates {@link TokenRelation} object for each token association to account and links tokens to the account.
     * This is used in both token associate logic and also token create logic
     * @param account the account to link the tokens to
     * @param tokens the tokens to link to the account
     * @param accountStore the account store
     * @param tokenRelStore the token relation store
     */
    protected void createAndLinkTokenRels(
            @NonNull final Account account,
            @NonNull final List<Token> tokens,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final WritableTokenRelationStore tokenRelStore) {
        // create list of token relations to be added
        final var newTokenRels = createTokenRelsToAccount(account, tokens);
        // Link the new token relations to the account
        linkTokenRels(account, newTokenRels, tokenRelStore);

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
     * Link all the new token relations created for the account together, and then link them to the account.
     * @param account the account to link the tokens to
     * @param newTokenRels the new token relations to link to the account
     * @param tokenRelStore the token relation store
     */
    private void linkTokenRels(
            @NonNull final Account account,
            @NonNull final List<TokenRelation> newTokenRels,
            @NonNull final WritableTokenRelationStore tokenRelStore) {
        // Now all the NEW token relations are linked together, but they are not yet linked to the account. First,
        // compute where the account's current head token number should go in the linked list of tokens
        final var currentHeadTokenNum = account.headTokenNumber();
        // NOTE: if currentHeadTokenNum is less than 1, it means the account isn't associated with any tokens yet, so
        // we'll just set the head to the first token, i.e. the first token ID list from the transaction (since the new
        // tokenRels are all linked, and in the order of the token IDs as they appeared in the original list)
        if (isValidTokenNum(currentHeadTokenNum)) {
            // The account is already associated with some tokens, so we need to insert the new
            // tokenRels at the beginning of the list of existing token numbers first. We start by
            // retrieving the token rel object with the currentHeadTokenNum at the head of the
            // account
            final var headTokenRel = tokenRelStore.get(
                    AccountID.newBuilder().accountNum(account.accountNumber()).build(),
                    TokenID.newBuilder().tokenNum(currentHeadTokenNum).build());
            if (headTokenRel != null) {
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
            } else {
                // This shouldn't happen, but if it does we'll log the error and continue with creating the token
                // associations
                log.error(
                        "Unable to get head tokenRel for account {}, token {}! Linked-list relations are likely in a bad state",
                        account.accountNumber(),
                        currentHeadTokenNum);
            }
        }
    }

    /**
     * Creates list of {@link TokenRelation}s for each token association to account.
     * @param account the account to link the tokens to
     * @param tokens the tokens to link to the account
     * @return the list of token relations to be added
     */
    private List<TokenRelation> createTokenRelsToAccount(
            @NonNull final Account account, @NonNull final List<Token> tokens) {
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
        return newTokenRels;
    }

    /* ------------------------- Helper functions ------------------------- */
    @NonNull
    public static TokenID asToken(final long num) {
        return TokenID.newBuilder().tokenNum(num).build();
    }
}

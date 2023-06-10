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
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import edu.umd.cs.findbugs.annotations.NonNull;

public class BaseTokenHandler {
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

    @NonNull
    public static TokenID asToken(final long num) {
        return TokenID.newBuilder().tokenNum(num).build();
    }
}

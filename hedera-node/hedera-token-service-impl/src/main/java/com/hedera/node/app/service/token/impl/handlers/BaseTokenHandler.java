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
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;

import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This class contains common functionality needed for Token handlers.
 */
public class BaseTokenHandler {
    /**
     * Mint type enum that represemnts the type of minting.
     */
    enum MintType {
        CREATION,
        OTHER
    }

    protected void mintTreasuryOnCreation(final Token token,
                                final TokenRelation treasuryRel,
                                final long amount,
                              final ResponseCodeEnum errorCode) {
        mint(token, treasuryRel, +amount, errorCode, true);
    }

    private void mint(@NonNull final TokenRelation relation,
                      final long amount,
                      @NonNull final Token token,
                      final boolean ignoreSupplyKey){
        validateTrue(amount >= 0, INVALID_TOKEN_MINT_AMOUNT);
        validateTrue(token.tokenType() == TokenType.FUNGIBLE_COMMON, FAIL_INVALID);
        mintTreasury(token, relation, +amount, INVALID_TOKEN_MINT_AMOUNT);
        changeSupply(token, relation, +amount, INVALID_TOKEN_MINT_AMOUNT, ignoreSupplyKey);
    }


    private void changeSupply(
            final Token token,
            final TokenRelation treasuryRel,
            final long amount,
            final ResponseCodeEnum negSupplyCode,
            final boolean ignoreSupplyKey,
            final WritableAccountStore accountStore,
            final WritableTokenRelationStore tokenRelationStore,
            final WritableTokenStore tokenStore){
        validateTrue(treasuryRel != null, FAIL_INVALID);
        validateTrue(treasuryRel.accountNumber() == token.treasuryAccountNumber() &&
                        treasuryRel.tokenNumber() == token.tokenNumber(), FAIL_INVALID);
        if (!ignoreSupplyKey) {
            validateTrue(token.supplyKey() != null, TOKEN_HAS_NO_SUPPLY_KEY);
        }
        final long newTotalSupply = token.totalSupply() + amount;
        validateTrue(newTotalSupply >= 0, negSupplyCode);
        if (token.supplyType() == TokenSupplyType.FINITE) {
            validateTrue(token.maxSupply() >= newTotalSupply, TOKEN_MAX_SUPPLY_REACHED);
        }
        final var treasuryAccount = accountStore
                .getAccountById(AccountID.newBuilder().accountNum(treasuryRel.accountNumber()).build());
        final long newTreasuryBalance = treasuryRel.balance() + amount;
        validateTrue(newTreasuryBalance >= 0, INSUFFICIENT_TOKEN_BALANCE);
        final var copyTreasury = treasuryAccount.copyBuilder();
        final var copyToken = token.copyBuilder();
        if (treasuryRel.balance() == 0 && amount > 0) {
            // for mint op
            copyTreasury.numberPositiveBalances(treasuryAccount.numberPositiveBalances() + 1);
        } else if (newTreasuryBalance == 0 && amount < 0) {
            // for burn op
            copyTreasury.numberPositiveBalances(treasuryAccount.numberPositiveBalances() - 1);
        }

        copyToken.totalSupply(newTotalSupply);
        final var copyRel = treasuryRel.copyBuilder().balance(newTreasuryBalance);

        accountStore.put(copyTreasury.build());
        tokenStore.put(copyToken.build());
        tokenRelationStore.put(copyRel.build());
    }
}

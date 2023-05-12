/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.service.token.impl.models;

import static com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_INVALID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_MAX_SUPPLY_REACHED;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.node.app.spi.workflows.HandleException;

public class Operations {
    public void mint(final TokenRelation treasuryRel, final long amount, final boolean ignoreSupplyKey) {
        validateTrue(amount >= 0, INVALID_TOKEN_MINT_AMOUNT);
        validateTrue(
                type == TokenType.FUNGIBLE_COMMON,
                FAIL_INVALID,
                "Fungible mint can be invoked only on fungible token type");

        changeSupply(treasuryRel, +amount, INVALID_TOKEN_MINT_AMOUNT, ignoreSupplyKey);
    }

    private void changeSupply(
            final TokenRelation treasuryRel,
            final long amount,
            final ResponseCodeEnum negSupplyCode,
            final boolean ignoreSupplyKey) {
        validateTrue(treasuryRel != null, FAIL_INVALID);
        validateTrue(
                treasuryRel.hasInvolvedIds(id, treasury.getId()),
                FAIL_INVALID,
                "Cannot change " + this + " supply (" + amount + ") with non-treasury rel " + treasuryRel);
        if (!ignoreSupplyKey) {
            validateTrue(supplyKey != null, TOKEN_HAS_NO_SUPPLY_KEY);
        }
        final long newTotalSupply = totalSupply + amount;
        validateTrue(newTotalSupply >= 0, negSupplyCode);
        if (supplyType == TokenSupplyType.FINITE) {
            validateTrue(
                    maxSupply >= newTotalSupply,
                    TOKEN_MAX_SUPPLY_REACHED,
                    "Cannot mint new supply (" + amount + "). Max supply (" + maxSupply + ") reached");
        }
        final var treasuryAccount = treasuryRel.getAccount();
        final long newTreasuryBalance = treasuryRel.getBalance() + amount;
        validateTrue(newTreasuryBalance >= 0, INSUFFICIENT_TOKEN_BALANCE);
        if (treasuryRel.getBalance() == 0 && amount > 0) {
            // for mint op
            treasuryAccount.setNumPositiveBalances(treasuryAccount.getNumPositiveBalances() + 1);
        } else if (newTreasuryBalance == 0 && amount < 0) {
            // for burn op
            treasuryAccount.setNumPositiveBalances(treasuryAccount.getNumPositiveBalances() - 1);
        }
        setTotalSupply(newTotalSupply);
        treasuryRel.setBalance(newTreasuryBalance);
    }
}

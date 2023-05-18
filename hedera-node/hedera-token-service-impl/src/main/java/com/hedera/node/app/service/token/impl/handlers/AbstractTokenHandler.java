package com.hedera.node.app.service.token.impl.handlers;


import static com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_INVALID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.TokenRelation;
import edu.umd.cs.findbugs.annotations.NonNull;

public class AbstractTokenHandler {
    /**
     * Mint tokens to the treasury account. This is called for
     * @param treasuryRel
     * @param amount
     * @param tokenType
     */
    protected void mint(@NonNull final TokenRelation treasuryRel, final long amount, @NonNull final TokenType tokenType){
        validateTrue(amount >= 0, INVALID_TOKEN_MINT_AMOUNT);
        validateTrue(tokenType == TokenType.FUNGIBLE_COMMON, FAIL_INVALID);
        changeSupply(treasuryRel, +amount, INVALID_TOKEN_MINT_AMOUNT, ignoreSupplyKey);
    }

    private void changeSupply(
            final TokenRelation treasuryRel,
            final long amount,
            final ResponseCodeEnum negSupplyCode,
            final boolean ignoreSupplyKey) {
        validateTrue(treasuryRel != null, FAIL_INVALID, "Cannot mint with a null treasuryRel");
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

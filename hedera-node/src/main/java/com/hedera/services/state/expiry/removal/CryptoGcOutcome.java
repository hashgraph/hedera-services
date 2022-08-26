package com.hedera.services.state.expiry.removal;

import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.NftAdjustments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record CryptoGcOutcome(FungibleTreasuryReturns fungibleTreasuryReturns,
                              NonFungibleTreasuryReturns nonFungibleTreasuryReturns, boolean finished) {

    public boolean needsExternalizing() {
        return finished || fungibleTreasuryReturns().numReturns() != 0 || nonFungibleTreasuryReturns.numReturns() != 0;
    }

    public List<EntityId> allReturnedTokens() {
        List<EntityId> ans = Collections.emptyList();
        if (fungibleTreasuryReturns.numReturns() != 0) {
            ans = fungibleTreasuryReturns.tokenTypes();
        }
        if (nonFungibleTreasuryReturns.numReturns() != 0) {
            ans = new ArrayList<>(ans);
            ans.addAll(nonFungibleTreasuryReturns.tokenTypes());
        }
        return ans;
    }

    public List<CurrencyAdjustments> parallelAdjustments() {
        if (nonFungibleTreasuryReturns.numReturns() == 0) {
            return fungibleTreasuryReturns.transfers();
        } else {
            final List<CurrencyAdjustments> ans = new ArrayList<>(fungibleTreasuryReturns.transfers());
            for (int i = 0, n = nonFungibleTreasuryReturns.numReturns(); i < n; i++) {
                ans.add(null);
            }
            return ans;
        }
    }

    public List<NftAdjustments> parallelExchanges() {
        if (fungibleTreasuryReturns.numReturns() == 0) {
            return nonFungibleTreasuryReturns.exchanges();
        } else {
            final List<NftAdjustments> ans = new ArrayList<>(nonFungibleTreasuryReturns.exchanges());
            for (int i = 0, n = fungibleTreasuryReturns.numReturns(); i < n; i++) {
                ans.add(0, null);
            }
            return ans;
        }
    }
}

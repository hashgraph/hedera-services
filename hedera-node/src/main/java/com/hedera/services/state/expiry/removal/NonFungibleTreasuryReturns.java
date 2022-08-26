package com.hedera.services.state.expiry.removal;

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.NftAdjustments;

import java.util.Collections;
import java.util.List;

public record NonFungibleTreasuryReturns(List<EntityId> tokenTypes, List<NftAdjustments> exchanges, boolean finished) {
    public static final NonFungibleTreasuryReturns FINISHED_NOOP_NON_FUNGIBLE_RETURNS =
            new NonFungibleTreasuryReturns(Collections.emptyList(), Collections.emptyList(), true);
    public static final NonFungibleTreasuryReturns UNFINISHED_NOOP_NON_FUNGIBLE_RETURNS =
            new NonFungibleTreasuryReturns(Collections.emptyList(), Collections.emptyList(),false);

    public int numReturns() {
        return tokenTypes.size();
    }
}

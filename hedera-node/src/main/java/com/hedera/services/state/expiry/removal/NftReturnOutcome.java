package com.hedera.services.state.expiry.removal;

import com.hedera.services.utils.EntityNumPair;

public record NftReturnOutcome(NonFungibleTreasuryReturns nftReturns, EntityNumPair newRoot, int remainingNfts) {
}

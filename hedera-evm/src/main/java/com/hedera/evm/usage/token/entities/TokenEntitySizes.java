package com.hedera.evm.usage.token.entities;

import static com.hedera.evm.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;

public enum TokenEntitySizes {
    TOKEN_ENTITY_SIZES;

    public int bytesUsedToRecordTokenTransfers(
            int numTokens, int fungibleNumTransfers, int uniqueNumTransfers) {
        return numTokens * BASIC_ENTITY_ID_SIZE
                + fungibleNumTransfers * USAGE_PROPERTIES.accountAmountBytes()
                + uniqueNumTransfers * USAGE_PROPERTIES.nftTransferBytes();
    }
}

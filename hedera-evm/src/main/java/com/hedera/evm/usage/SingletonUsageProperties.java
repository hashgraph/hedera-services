package com.hedera.evm.usage;

import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;

public enum SingletonUsageProperties implements UsageProperties {
    USAGE_PROPERTIES;

    @Override
    public int accountAmountBytes() {
        return LONG_SIZE + BASIC_ENTITY_ID_SIZE;
    }

    @Override
    public int nftTransferBytes() {
        return LONG_SIZE + 2 * BASIC_ENTITY_ID_SIZE;
    }
}

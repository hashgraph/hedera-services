package com.hedera.evm.usage.token.meta;

import com.hederahashgraph.api.proto.java.SubType;

public class TokenWipeMeta extends TokenBurnWipeMeta{
    public TokenWipeMeta(
            final int bpt,
            final SubType subType,
            final long transferRecordRb,
            final int serialNumsCount) {
        super(bpt, subType, transferRecordRb, serialNumsCount);
    }
}

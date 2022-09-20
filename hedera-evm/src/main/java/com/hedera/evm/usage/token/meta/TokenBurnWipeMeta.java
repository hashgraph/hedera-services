package com.hedera.evm.usage.token.meta;

import com.google.common.base.MoreObjects;
import com.hederahashgraph.api.proto.java.SubType;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class TokenBurnWipeMeta extends TokenOpMetaBase {
    private final int serialNumsCount;

    protected TokenBurnWipeMeta(
            final int bpt,
            final SubType subType,
            final long transferRecordRb,
            final int serialNumsCount) {
        super(bpt, subType, transferRecordRb);
        this.serialNumsCount = serialNumsCount;
    }

    public int getSerialNumsCount() {
        return serialNumsCount;
    }

    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public MoreObjects.ToStringHelper toStringHelper() {
        return super.toStringHelper().add("serialNumsCount", serialNumsCount);
    }
}

/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.hapi.fees.usage.token.meta;

import com.google.common.base.MoreObjects;
import com.hederahashgraph.api.proto.java.SubType;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class TokenCreateMeta {
    private final int baseSize;
    private final long lifeTime;
    private final int customFeeScheduleSize;
    private final int fungibleNumTransfers;
    private final int nftsTransfers;
    private final int numTokens;
    private final long networkRecordRb;
    private final SubType subType;

    private TokenCreateMeta(final Builder builder) {
        this.lifeTime = builder.lifeTime;
        this.subType = builder.subType;
        this.baseSize = builder.baseSize;
        this.customFeeScheduleSize = builder.customFeeScheduleSize;
        this.fungibleNumTransfers = builder.fungibleNumTransfers;
        this.numTokens = builder.numTokens;
        this.networkRecordRb = builder.networkRecordRb;
        this.nftsTransfers = builder.nftsTransfers;
    }

    public int getBaseSize() {
        return baseSize;
    }

    public int getCustomFeeScheduleSize() {
        return customFeeScheduleSize;
    }

    public long getLifeTime() {
        return lifeTime;
    }

    public int getNftsTransfers() {
        return nftsTransfers;
    }

    public int getFungibleNumTransfers() {
        return fungibleNumTransfers;
    }

    public int getNumTokens() {
        return numTokens;
    }

    public long getNetworkRecordRb() {
        return networkRecordRb;
    }

    public SubType getSubType() {
        return subType;
    }

    public static class Builder {
        private int baseSize;
        private long lifeTime;
        private int customFeeScheduleSize;
        private int fungibleNumTransfers;
        private int nftsTransfers;
        private int numTokens;
        private long networkRecordRb;
        private SubType subType;

        public Builder() {
            // empty here on purpose.
        }

        public Builder baseSize(final int baseSize) {
            this.baseSize = baseSize;
            return this;
        }

        public Builder lifeTime(final long lifeTime) {
            this.lifeTime = lifeTime;
            return this;
        }

        public Builder customFeeScheleSize(final int feeScheduleSize) {
            this.customFeeScheduleSize = feeScheduleSize;
            return this;
        }

        public Builder fungibleNumTransfers(final int fungibleNumTransfers) {
            this.fungibleNumTransfers = fungibleNumTransfers;
            return this;
        }

        public Builder nftsTranfers(final int nftsTransfers) {
            this.nftsTransfers = nftsTransfers;
            return this;
        }

        public Builder numTokens(final int numTokens) {
            this.numTokens = numTokens;
            return this;
        }

        public Builder networkRecordRb(final int networkRecordRb) {
            this.networkRecordRb = networkRecordRb;
            return this;
        }

        public Builder subType(final SubType subType) {
            this.subType = subType;
            return this;
        }

        public TokenCreateMeta build() {
            return new TokenCreateMeta(this);
        }
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
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("baseSize", baseSize)
                .add("lifeTime", lifeTime)
                .add("customFeeScheduleSize", customFeeScheduleSize)
                .add("fungibleNumTransfers", fungibleNumTransfers)
                .add("numTokens", numTokens)
                .add("networkRecordRb", networkRecordRb)
                .add("nftsTransfers", nftsTransfers)
                .add("subType", subType)
                .toString();
    }
}

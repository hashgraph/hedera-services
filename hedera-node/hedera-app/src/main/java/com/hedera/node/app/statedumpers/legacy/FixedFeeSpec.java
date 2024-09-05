/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.statedumpers.legacy;

import com.google.common.base.MoreObjects;
import com.hederahashgraph.api.proto.java.FixedFee;
import java.util.Objects;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class FixedFeeSpec {
    private final long unitsToCollect;
    /* If null, fee is collected in ℏ */
    private final EntityId tokenDenomination;

    private boolean usedDenomWildcard = false;

    public FixedFeeSpec(long unitsToCollect, EntityId tokenDenomination) {
        this.unitsToCollect = unitsToCollect;
        this.tokenDenomination = tokenDenomination;
    }

    public boolean usedDenomWildcard() {
        return usedDenomWildcard;
    }

    public static FixedFeeSpec fromGrpc(FixedFee fixedFee) {
        if (fixedFee.hasDenominatingTokenId()) {
            final var denom = EntityId.fromGrpcTokenId(fixedFee.getDenominatingTokenId());
            return new FixedFeeSpec(fixedFee.getAmount(), denom);
        } else {
            return new FixedFeeSpec(fixedFee.getAmount(), null);
        }
    }

    public FixedFee asGrpc() {
        final var builder = FixedFee.newBuilder().setAmount(unitsToCollect);
        if (tokenDenomination != null) {
            builder.setDenominatingTokenId(tokenDenomination.toGrpcTokenId());
        }
        return builder.build();
    }

    public long getUnitsToCollect() {
        return unitsToCollect;
    }

    public EntityId getTokenDenomination() {
        return tokenDenomination;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || !obj.getClass().equals(FixedFeeSpec.class)) {
            return false;
        }

        final var that = (FixedFeeSpec) obj;
        return this.unitsToCollect == that.unitsToCollect
                && Objects.equals(this.tokenDenomination, that.tokenDenomination);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(FixedFeeSpec.class)
                .add("unitsToCollect", unitsToCollect)
                .add("tokenDenomination", tokenDenomination == null ? "ℏ" : tokenDenomination.toAbbrevString())
                .toString();
    }
}

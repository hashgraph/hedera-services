/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Represents the key for {@code nftAllowances} and {@code fungibleTokenAllowances} maps in {@code
 * MerkleAccountState}. It consists of the information about the token for which allowance is
 * granted to and the spender who is granted the allowance.
 *
 * <p>Having allowance on a token will grant the spender to transfer fungible or non-fungible token
 * units from the owner's account.
 */
public class FcTokenAllowanceId implements Comparable<FcTokenAllowanceId> {
    private EntityNum tokenNum;
    private EntityNum spenderNum;

    public FcTokenAllowanceId(final EntityNum tokenNum, final EntityNum spenderNum) {
        this.tokenNum = tokenNum;
        this.spenderNum = spenderNum;
    }

    public static FcTokenAllowanceId from(final TokenID tokenId, final AccountID accountId) {
        return new FcTokenAllowanceId(EntityNum.fromTokenId(tokenId), EntityNum.fromAccountId(accountId));
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || !obj.getClass().equals(FcTokenAllowanceId.class)) {
            return false;
        }

        final var that = (FcTokenAllowanceId) obj;
        return new EqualsBuilder()
                .append(tokenNum, that.tokenNum)
                .append(spenderNum, that.spenderNum)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(tokenNum).append(spenderNum).toHashCode();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .omitNullValues()
                .add("tokenNum", tokenNum.longValue())
                .add("spenderNum", spenderNum.longValue())
                .toString();
    }

    public EntityNum getTokenNum() {
        return tokenNum;
    }

    public EntityNum getSpenderNum() {
        return spenderNum;
    }

    public static FcTokenAllowanceId from(final EntityNum tokenNum, final EntityNum spenderNum) {
        return new FcTokenAllowanceId(tokenNum, spenderNum);
    }

    @Override
    public int compareTo(@NonNull final FcTokenAllowanceId that) {
        return new CompareToBuilder()
                .append(tokenNum, that.tokenNum)
                .append(spenderNum, that.spenderNum)
                .toComparison();
    }
}

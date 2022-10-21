/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.submerkle;

import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.FreezeNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Frozen;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Unfrozen;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.Granted;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.KycNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.Revoked;

import com.google.common.base.MoreObjects;
import com.hedera.services.state.merkle.MerkleToken;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import com.hederahashgraph.api.proto.java.TokenRelationship;
import java.util.Objects;

public class RawTokenRelationship {
    private final long balance;
    private final long shardNum;
    private final long realmNum;
    private final long tokenNum;
    private final boolean frozen;
    private final boolean kycGranted;
    private final boolean automaticAssociation;

    public RawTokenRelationship(
            long balance,
            long shardNum,
            long realmNum,
            long tokenNum,
            boolean frozen,
            boolean kycGranted,
            boolean automaticAssociation) {
        this.balance = balance;
        this.shardNum = shardNum;
        this.realmNum = realmNum;
        this.tokenNum = tokenNum;
        this.frozen = frozen;
        this.kycGranted = kycGranted;
        this.automaticAssociation = automaticAssociation;
    }

    public long getBalance() {
        return balance;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public boolean isKycGranted() {
        return kycGranted;
    }

    public long getTokenNum() {
        return tokenNum;
    }

    public boolean isAutomaticAssociation() {
        return automaticAssociation;
    }

    public TokenID id() {
        return TokenID.newBuilder().setTokenNum(tokenNum).build();
    }

    public TokenRelationship asGrpcFor(MerkleToken token) {
        return TokenRelationship.newBuilder()
                .setBalance(balance)
                .setDecimals(token.decimals())
                .setSymbol(token.symbol())
                .setTokenId(
                        TokenID.newBuilder()
                                .setShardNum(shardNum)
                                .setRealmNum(realmNum)
                                .setTokenNum(tokenNum))
                .setFreezeStatus(freezeStatusFor(token))
                .setKycStatus(kycStatusFor(token))
                .setAutomaticAssociation(automaticAssociation)
                .build();
    }

    private TokenFreezeStatus freezeStatusFor(MerkleToken token) {
        if (!token.hasFreezeKey()) {
            return FreezeNotApplicable;
        }
        return frozen ? Frozen : Unfrozen;
    }

    private TokenKycStatus kycStatusFor(MerkleToken token) {
        if (!token.hasKycKey()) {
            return KycNotApplicable;
        }
        return kycGranted ? Granted : Revoked;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || RawTokenRelationship.class != o.getClass()) {
            return false;
        }

        var that = (RawTokenRelationship) o;

        return this.balance == that.balance
                && this.frozen == that.frozen
                && this.kycGranted == that.kycGranted
                && this.automaticAssociation == that.automaticAssociation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(balance, frozen, kycGranted, automaticAssociation);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("token", String.format("%d.%d.%d", shardNum, realmNum, tokenNum))
                .add("balance", balance)
                .add("frozen", frozen)
                .add("kycGranted", kycGranted)
                .add("automaticAssociation", automaticAssociation)
                .toString();
    }
}

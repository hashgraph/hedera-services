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
package com.hedera.services.utils;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.state.merkle.internals.BitPackUtils.isValidNum;
import static com.hedera.services.state.merkle.internals.BitPackUtils.packedNums;
import static com.hedera.services.state.merkle.internals.BitPackUtils.unsignedHighOrder32From;
import static com.hedera.services.state.merkle.internals.BitPackUtils.unsignedLowOrder32From;
import static com.hedera.services.utils.EntityNum.areValidNums;

import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.TokenRelationship;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.Objects;
import org.apache.commons.lang3.tuple.Pair;

public record EntityNumPair(long value) {
    public static final EntityNumPair MISSING_NUM_PAIR = new EntityNumPair(0);

    public EntityNumPair {
        Objects.requireNonNull(value);
    }

    public static EntityNumPair fromNums(final EntityNum a, final EntityNum b) {
        return fromLongs(a.longValue(), b.longValue());
    }

    public static EntityNumPair fromLongs(long hi, long lo) {
        if (!isValidNum(hi) || !isValidNum(lo)) {
            return MISSING_NUM_PAIR;
        }
        final var value = packedNums(hi, lo);
        return new EntityNumPair(value);
    }

    public static EntityNumPair fromGrpcNftId(NftID grpcId) {
        final var tokenId = grpcId.getTokenID();
        if (!areValidNums(tokenId.getShardNum(), tokenId.getRealmNum())) {
            return MISSING_NUM_PAIR;
        }
        return fromLongs(tokenId.getTokenNum(), grpcId.getSerialNumber());
    }

    public static EntityNumPair fromNftId(NftId id) {
        if (!areValidNums(id.shard(), id.realm())) {
            return MISSING_NUM_PAIR;
        }
        return fromLongs(id.num(), id.serialNo());
    }

    public static EntityNumPair fromAccountTokenRel(Pair<AccountID, TokenID> rel) {
        return fromAccountTokenRel(rel.getLeft(), rel.getRight());
    }

    public static EntityNumPair fromAccountTokenRel(AccountID account, TokenID token) {
        final var accountNum = EntityNum.fromAccountId(account);
        final var tokenNum = EntityNum.fromTokenId(token);
        return fromLongs(accountNum.longValue(), tokenNum.longValue());
    }

    public EntityNum getHiOrderAsNum() {
        return EntityNum.fromLong(getHiOrderAsLong());
    }

    public long getLowOrderAsLong() {
        return unsignedLowOrder32From(value);
    }

    public long getHiOrderAsLong() {
        return unsignedHighOrder32From(value);
    }

    public static EntityNumPair fromModelRel(TokenRelationship tokenRelationship) {
        return EntityNumPair.fromLongs(
                tokenRelationship.getAccount().getId().num(),
                tokenRelationship.getToken().getId().num());
    }

    public Pair<AccountID, TokenID> asAccountTokenRel() {
        return Pair.of(
                STATIC_PROPERTIES.scopedAccountWith(getHiOrderAsLong()),
                STATIC_PROPERTIES.scopedTokenWith(getLowOrderAsLong()));
    }

    public Pair<Long, Long> asTokenNumAndSerialPair() {
        return Pair.of(getHiOrderAsLong(), getLowOrderAsLong());
    }

    public EntityNum getLowOrderAsNum() {
        return EntityNum.fromLong(getLowOrderAsLong());
    }

    public NftNumPair asNftNumPair() {
        return NftNumPair.fromLongs(getHiOrderAsLong(), getLowOrderAsLong());
    }

    @Override
    public int hashCode() {
        return (int) MiscUtils.perm64(value);
    }

    @Override
    public String toString() {
        return "PermHashLong(" + getHiOrderAsLong() + ", " + getLowOrderAsLong() + ")";
    }
}

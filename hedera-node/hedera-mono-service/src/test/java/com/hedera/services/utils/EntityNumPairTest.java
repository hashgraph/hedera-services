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

import static com.hedera.services.utils.EntityNumPair.MISSING_NUM_PAIR;
import static com.hedera.services.utils.EntityNumPair.fromLongs;
import static com.hedera.services.utils.EntityNumPair.fromNftId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.hedera.services.state.merkle.internals.BitPackUtils;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.junit.jupiter.api.Test;

class EntityNumPairTest {
    @Test
    void overridesJavaLangImpl() {
        final var v = 1_234_567;

        final var subject = new EntityNumPair(v);

        assertNotEquals(Long.valueOf(v).hashCode(), subject.hashCode());
    }

    @Test
    void equalsWorks() {
        final var a = new EntityNumPair(1);
        final var b = new EntityNumPair(2);
        final var c = a;

        assertNotEquals(a, b);
        assertNotEquals(null, a);
        assertNotEquals(a, new Object());
        assertEquals(a, c);
    }

    @Test
    void usesExpectedBitPacking() {
        final var expected = new EntityNumPair(BitPackUtils.packedNums(1, 2));

        assertEquals(expected, fromLongs(1, 2));
    }

    @Test
    void factoryFromNftIdWorks() {
        final var expected = fromLongs(1, 2);
        final var nftId = new NftId(0, 0, 1, 2);

        final var actual = fromNftId(nftId);

        assertEquals(expected, actual);
    }

    @Test
    void grpcNftIdFactoryWorks() {
        final var notOkId =
                NftID.newBuilder()
                        .setTokenID(TokenID.newBuilder().setShardNum(1).setTokenNum(1234).build())
                        .setSerialNumber(1)
                        .build();
        assertSame(MISSING_NUM_PAIR, EntityNumPair.fromGrpcNftId(notOkId));
        final var okId =
                NftID.newBuilder()
                        .setTokenID(TokenID.newBuilder().setTokenNum(1234).build())
                        .setSerialNumber(1)
                        .build();
        assertEquals(EntityNumPair.fromLongs(1234L, 1L), EntityNumPair.fromGrpcNftId(okId));
    }

    @Test
    void returnsMissingNumPairIfInvalidLong() {
        assertEquals(MISSING_NUM_PAIR, fromLongs(Long.MAX_VALUE, 2));
        assertEquals(MISSING_NUM_PAIR, fromLongs(Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(MISSING_NUM_PAIR, fromLongs(-1L, 2));
        assertEquals(MISSING_NUM_PAIR, fromLongs(-1L, Long.MAX_VALUE));
    }

    @Test
    void returnsCorrectNumPairIfValidLong() {
        final var expectedVal = BitPackUtils.packedNums(2, 2);
        assertEquals(expectedVal, fromLongs(2, 2).value());
    }

    @Test
    void factoryFromInvalidNftIdWorks() {
        final var invalidShard = new NftId(1, 0, 1, 2);
        final var invalidRealm = new NftId(0, 1, 1, 2);

        assertSame(MISSING_NUM_PAIR, fromNftId(invalidShard));
        assertSame(MISSING_NUM_PAIR, fromNftId(invalidRealm));
    }

    @Test
    void factoryFromModelRelWorks() {
        final var expected = fromLongs(1, 2);
        final var modelRel =
                new TokenRelationship(new Token(new Id(0, 0, 2)), new Account(new Id(0, 0, 1)));

        final var actual = EntityNumPair.fromModelRel(modelRel);

        assertEquals(expected, actual);
    }

    @Test
    void hiPhiAccessorWorks() {
        final long bigNum = (long) Integer.MAX_VALUE + 123;
        final var expected = EntityNum.fromLong(bigNum);
        final var subject = fromLongs(bigNum, 1);

        final var hi = subject.getHiOrderAsNum();

        assertEquals(expected, hi);
    }

    @Test
    void accountTokenReprWorks() {
        final var subject = fromLongs(1, 2);

        final var pairRepr = subject.asAccountTokenRel();

        assertEquals(1, pairRepr.getLeft().getAccountNum());
        assertEquals(2, pairRepr.getRight().getTokenNum());
    }

    @Test
    void toStringWorks() {
        final var subject = fromLongs(1, 2);

        assertEquals("PermHashLong(1, 2)", subject.toString());
    }
}

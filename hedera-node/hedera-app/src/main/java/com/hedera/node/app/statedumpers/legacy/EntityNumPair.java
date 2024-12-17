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

import com.google.common.primitives.Longs;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.Objects;
import org.apache.commons.lang3.tuple.Pair;

public record EntityNumPair(long value) implements Comparable<EntityNumPair> {
    private static final long MASK_INT_AS_UNSIGNED_LONG = (1L << 32) - 1;
    private static final long MASK_HI_ORDER_32 = MASK_INT_AS_UNSIGNED_LONG << 32;
    public static final EntityNumPair MISSING_NUM_PAIR = new EntityNumPair(0);

    public EntityNumPair {
        Objects.requireNonNull(value);
    }

    public static EntityNumPair fromLongs(long hi, long lo) {
        final var value = packedNums(hi, lo);
        return new EntityNumPair(value);
    }

    public long getLowOrderAsLong() {
        return unsignedLowOrder32From(value);
    }

    public long getHiOrderAsLong() {
        return unsignedHighOrder32From(value);
    }

    public Pair<AccountID, TokenID> asAccountTokenRel() {
        return Pair.of(
                AccountID.newBuilder().setAccountNum(getHiOrderAsLong()).build(),
                TokenID.newBuilder().setTokenNum(getLowOrderAsLong()).build());
    }

    @Override
    public int compareTo(final EntityNumPair that) {
        return Longs.compare(this.value, that.value);
    }

    private static long packedNums(long a, long b) {
        return a << 32 | b;
    }

    private static long unsignedHighOrder32From(long l) {
        return (l & MASK_HI_ORDER_32) >>> 32;
    }

    /**
     * Returns the low-order 32 bits of the given {@code long}, interpreted as an unsigned integer.
     *
     * @param l any long
     * @return the low-order 32 bits as an unsigned integer
     */
    public static long unsignedLowOrder32From(long l) {
        return l & MASK_INT_AS_UNSIGNED_LONG;
    }
}

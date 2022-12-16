/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.hapi.utils.sysfiles.domain;

import com.google.common.primitives.Longs;
import com.hedera.node.app.hapi.utils.sysfiles.ParsingUtils;
import com.swirlds.common.utility.CommonUtils;
import java.util.Arrays;

public record KnownBlockValues(byte[] hash, long number) {
    private static final int KECCAK256_HASH_LENGTH = 32;

    public static KnownBlockValues MISSING_BLOCK_VALUES = new KnownBlockValues(new byte[0], 0);

    public static KnownBlockValues from(final String literal) {
        if (literal.isBlank()) {
            return MISSING_BLOCK_VALUES;
        }
        return ParsingUtils.fromTwoPartDelimited(
                literal,
                "@",
                (hash, number) -> {
                    if (hash.length != KECCAK256_HASH_LENGTH) {
                        throw new IllegalArgumentException(
                                "Wrong hash length in '" + literal + "'");
                    }
                    if (number <= 0) {
                        throw new IllegalArgumentException(
                                "Non-positive block number in '" + literal + "'");
                    }
                },
                CommonUtils::unhex,
                Long::parseLong,
                KnownBlockValues::new);
    }

    public boolean isMissing() {
        return hash.length == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || KnownBlockValues.class != o.getClass()) {
            return false;
        }
        final var that = (KnownBlockValues) o;
        return this.number == that.number && Arrays.equals(this.hash, that.hash);
    }

    @Override
    public int hashCode() {
        return 31 * Longs.hashCode(number) + Arrays.hashCode(hash);
    }

    @Override
    public String toString() {
        return "KnownBlockValues{" + "hash=" + CommonUtils.hex(hash) + ", number=" + number + '}';
    }
}

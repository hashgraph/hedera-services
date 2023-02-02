/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TokenMintMetaTest {
    @Test
    void allGettersAndToStringWork() {
        final var expected =
                "TokenMintMeta{bpt=1000, transferRecordDb=12345,"
                        + " subType=TOKEN_NON_FUNGIBLE_UNIQUE, rbs=1234567}";

        final var subject = new TokenMintMeta(1000, TOKEN_NON_FUNGIBLE_UNIQUE, 12_345L, 1_234_567L);
        assertEquals(1000, subject.getBpt());
        assertEquals(12_345L, subject.getTransferRecordDb());
        assertEquals(TOKEN_NON_FUNGIBLE_UNIQUE, subject.getSubType());
        assertEquals(1_234_567L, subject.getRbs());
        assertEquals(expected, subject.toString());
    }

    @Test
    void hashCodeAndEqualsWork() {
        final var meta1 = new TokenMintMeta(1000, TOKEN_NON_FUNGIBLE_UNIQUE, 12_345L, 1_234_567L);
        final var meta2 = new TokenMintMeta(1000, TOKEN_NON_FUNGIBLE_UNIQUE, 12_345L, 1_234_567L);

        assertEquals(meta1, meta2);
        assertEquals(meta1.hashCode(), meta1.hashCode());
    }

    @Test
    void assertToStringHelper() {
        final var bpt = 1000;
        final var transferRecordRb = 12_345L;
        final var rbs = 1_234_567L;
        final var meta = new TokenMintMeta(bpt, TOKEN_NON_FUNGIBLE_UNIQUE, transferRecordRb, rbs);
        final String expected =
                String.format(
                        "TokenMintMeta{bpt=%d, transferRecordDb=%d, subType=%s, rbs=%d}",
                        bpt, transferRecordRb, TOKEN_NON_FUNGIBLE_UNIQUE, rbs);
        assertEquals(expected, meta.toStringHelper().toString());
    }
}

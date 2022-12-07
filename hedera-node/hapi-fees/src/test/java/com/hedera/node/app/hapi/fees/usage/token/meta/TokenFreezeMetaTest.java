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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TokenFreezeMetaTest {
    @Test
    void getterAndToStringWork() {
        final var expected = "TokenFreezeMeta{bpt=132}";

        final var subject = new TokenFreezeMeta(132);
        assertEquals(132, subject.getBpt());
        assertEquals(expected, subject.toString());
    }

    @Test
    void unfreezeBasicsAlso() {
        final var expected = "TokenUnfreezeMeta{bpt=48}";

        final var subject = new TokenUnfreezeMeta(48);
        assertEquals(48, subject.getBpt());
        assertEquals(expected, subject.toString());
    }

    @Test
    void hashCodeAndEqualsWork() {
        final var meta1 = new TokenFreezeMeta(100);
        final var meta2 = new TokenFreezeMeta(100);

        assertEquals(meta1, meta2);
        assertEquals(meta1.hashCode(), meta1.hashCode());
    }
}

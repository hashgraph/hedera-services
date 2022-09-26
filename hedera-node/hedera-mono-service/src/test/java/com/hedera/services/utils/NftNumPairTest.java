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
package com.hedera.services.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class NftNumPairTest {
    @Test
    void toStringWorks() {
        final var subject = NftNumPair.fromLongs(1001L, 1L);
        final var expected = "0.0.1001.1";

        assertEquals(expected, subject.toString());
    }

    @Test
    void equalsWorks() {
        final var subject = NftNumPair.fromLongs(1001L, 1L);
        final var subject2 = NftNumPair.fromLongs(1002L, 1L);
        final var subject3 = NftNumPair.fromLongs(1001L, 2L);
        final var identical = NftNumPair.fromLongs(1001L, 1L);

        assertEquals(subject, identical);
        assertNotEquals(subject, subject2);
        assertNotEquals(subject, subject3);
        assertNotEquals(null, subject);
    }
}

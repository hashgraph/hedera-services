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
package com.hedera.services.context.domain.security;

import static com.hedera.services.context.domain.security.PermissionedAccountsRange.from;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PermissionedAccountsRangeTest {
    @Test
    void recognizesDegenerate() {
        // given:
        var subject = from("55");

        // expect:
        Assertions.assertTrue(subject.contains(55));
        Assertions.assertFalse(subject.contains(56));
    }

    @Test
    void recognizesNonDegenerate() {
        // given:
        var subject = from("55-99");

        // expect:
        Assertions.assertTrue(subject.contains(55));
        Assertions.assertTrue(subject.contains(99));
        Assertions.assertFalse(subject.contains(54));
        Assertions.assertFalse(subject.contains(100));
    }

    @Test
    void nullsOnEmptyDescription() {
        // expect:
        assertNull(from(""));
    }

    @Test
    void nullsOnNonsense() {
        // expect:
        assertNull(from("-1-*"));
    }

    @Test
    void constructsDegenerate() {
        // given:
        var range = from("12345");

        // expect:
        assertNull(range.inclusiveTo);
        assertEquals(12345L, range.from);
    }

    @Test
    void constructsNonDegenerateExplicit() {
        // given:
        var range = from("12345-54321");

        // expect:
        assertEquals(12345L, range.from);
        assertEquals(54321L, range.inclusiveTo);
    }

    @Test
    void recognizesWildcard() {
        // given:
        var range = from("12345-*");

        // expect:
        assertEquals(12345L, range.from);
        assertEquals(Long.MAX_VALUE, range.inclusiveTo);
    }

    @Test
    void nullsOnEmpty() {
        // expect:
        assertNull(from("2-1"));
    }
}

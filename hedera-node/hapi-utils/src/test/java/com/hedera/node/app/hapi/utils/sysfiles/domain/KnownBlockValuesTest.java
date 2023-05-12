/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.utility.CommonUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KnownBlockValuesTest {
    @CsvSource({
        "c9e37a7a454638ca62662bd1a06de49ef40b3444203fe329bbc81363604ea7f8@666,"
                + "c9e37a7a454638ca62662bd1a06de49ef40b3444203fe329bbc81363604ea7f8,666",
        "16ed3719345c6455bf606efa7d36c248e0006d2742cd9b1731b43eefa2d72456@"
                + Long.MAX_VALUE
                + ","
                + "16ed3719345c6455bf606efa7d36c248e0006d2742cd9b1731b43eefa2d72456,"
                + Long.MAX_VALUE,
    })
    @ParameterizedTest
    void parsesValidAsExpected(String literal, String hash, long number) {
        final var subject = KnownBlockValues.from(literal);
        assertArrayEquals(CommonUtils.unhex(hash), subject.hash());
        assertEquals(number, subject.number());
    }

    @CsvSource({
        "c9e37a7a454638ca62662bd1a06de49ef40b3444203fe329bbc81363604ea7f8:666",
        "666",
        "c9e37a7a454638ca62662bd1a06de49ef40b3444203fe329bbc81363604ea7f8",
        "c9e37a7a454638ca62662bd1a06de49ef40b3444203fe329bbc81363604ea7f8@0",
        "c9e37a7a454638ca62662bd1a06de49ef40b3444203fe329bbc81363604ea7@1",
    })
    @ParameterizedTest
    void throwsIaeOnInvalid(String invalidLiteral) {
        Assertions.assertThrows(IllegalArgumentException.class, () -> KnownBlockValues.from(invalidLiteral));
    }

    @Test
    void parsesEmptyStringAsMissingBlockValues() {
        final var subject = KnownBlockValues.from("");
        assertTrue(subject.isMissing());
        assertSame(KnownBlockValues.MISSING_BLOCK_VALUES, subject);
    }

    @Test
    void objectMethodsAsExpected() {
        final var a = KnownBlockValues.from("c9e37a7a454638ca62662bd1a06de49ef40b3444203fe329bbc81363604ea7f8@666");
        final var b = KnownBlockValues.from("c9e37a7a454638ca62662bd1a06de49ef40b3444203fe329bbc81363604ea7f8@667");
        final var c = KnownBlockValues.from("cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc@666");
        final var d = a;
        final var e = KnownBlockValues.from("c9e37a7a454638ca62662bd1a06de49ef40b3444203fe329bbc81363604ea7f8@666");
        final var desired = "KnownBlockValues{hash=c9e37a7a454638ca62662bd1a06de49ef40b3444203fe329bbc81363604ea7f8,"
                + " number=666}";

        assertEquals(a, d);
        assertNotEquals(a, null);
        assertNotEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a, e);
        // and:
        assertEquals(a.hashCode(), e.hashCode());
        // and:
        assertEquals(desired, a.toString());
    }
}

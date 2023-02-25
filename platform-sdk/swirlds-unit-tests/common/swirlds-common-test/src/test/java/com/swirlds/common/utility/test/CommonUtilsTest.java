/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.utility.test;

import static com.swirlds.common.utility.CommonUtils.hex;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CommonUtilsTest {
    private static final byte[] HEX_BYTES = {0x12, 0x34, 0x56, 0x78, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f};
    private static final String HEX_STRING = "123456780a0b0c0d0e0f";

    @Test
    void hexTest() {
        assertTrue(hex(null).contains("null"), "the output of a null input should indicate its null");
        assertEquals("", hex(new byte[0]), "for an empty array we should get an empty string");

        assertEquals(HEX_STRING, hex(HEX_BYTES), "hex value should match");
        final int length = 2;
        assertEquals(HEX_STRING.substring(0, length * 2), hex(HEX_BYTES, length), "hex value should match");

        assertThrows(
                IllegalArgumentException.class,
                () -> hex(HEX_BYTES, HEX_BYTES.length + 1),
                "should throw if illegal length");
    }

    @Test
    void unhexTest() {
        assertNull(unhex(null), "null input should provide null output");
        assertThrows(
                IllegalArgumentException.class,
                () -> unhex("123"),
                "a hex string can never have a odd number of characters");
        assertArrayEquals(HEX_BYTES, unhex(HEX_STRING), "hex value should match");
        assertArrayEquals(HEX_BYTES, unhex(HEX_STRING.toUpperCase()), "hex value should match");

        assertThrows(
                IllegalArgumentException.class,
                () -> unhex("a random string"),
                "hex characters should be in the range: [A-Fa-f0-9]");
    }
}

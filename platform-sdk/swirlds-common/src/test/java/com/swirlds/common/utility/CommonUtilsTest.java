/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.utility;

import static com.swirlds.common.utility.CommonUtils.byteCountToDisplaySize;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    @Test
    void decodeHexValid() {
        // Given
        final String hex = "68656c6c6f";

        // When
        final byte[] result = unhex(hex);

        // Then
        final byte[] expected = "hello".getBytes();
        assertArrayEquals(expected, result);
    }

    @Test
    void decodeHexEmptyString() {
        // Given
        final String hex = "";

        // When
        final byte[] result = unhex(hex);

        // Then
        final byte[] expected = new byte[0];
        assertArrayEquals(expected, result);
    }

    @Test
    void decodeHexInvalidOddNumberOfChars() {
        final String hex = "6865e"; // Odd number of characters
        assertThrows(IllegalArgumentException.class, () -> unhex(hex));
    }

    @Test
    void decodeHexInvalidCharacter() {
        final String hex = "68656c6c6g"; // Contains an invalid character "g"
        assertThrows(IllegalArgumentException.class, () -> unhex(hex));
    }

    @Test
    void hexWithNullBytes() {
        final byte[] bytes = null;
        assertEquals("null", hex(bytes, 0));
    }

    @Test
    void hexWithValidBytes() {
        // Given
        final byte[] bytes = "hello".getBytes();

        // When
        final String result = hex(bytes, bytes.length);

        // Then
        assertEquals("68656c6c6f", result);
    }

    @Test
    void hexWithLengthTooLow() {
        final byte[] bytes = "hello".getBytes();
        assertThrows(
                IllegalArgumentException.class,
                () -> hex(bytes, -1)); // Assuming throwRangeInvalid throws IllegalArgumentException
    }

    @Test
    void hexWithLengthTooHigh() {
        final byte[] bytes = "hello".getBytes();
        assertThrows(
                IllegalArgumentException.class,
                () -> hex(bytes, bytes.length + 1)); // Assuming throwRangeInvalid throws IllegalArgumentException
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0 B",
        "512, 512 B",
        "1023, 1023 B",
        "1024, 1.0 KB",
        "1536, 1.5 KB",
        "1048576, 1.0 MB",
        "1572864, 1.5 MB",
        "1073741824, 1.0 GB",
        "1610612736, 1.5 GB",
        "1099511627776, 1.0 TB",
        "1649267441664, 1.5 TB",
        "1125899906842624, 1.0 PB",
        "1688849860263936, 1.5 PB",
        "1152921504606846976, 1.0 EB",
        "1729382256910270464, 1.5 EB"
    })
    void testByteCountToDisplaySize(final long inputBytes, final String expectedOutput) {
        assertEquals(expectedOutput, byteCountToDisplaySize(inputBytes));
    }

    @Test
    void leftPad() {
        final String start = "123";

        final String padded = CommonUtils.leftPad(start, 5, '_');

        assertEquals("__123", padded);
    }
}

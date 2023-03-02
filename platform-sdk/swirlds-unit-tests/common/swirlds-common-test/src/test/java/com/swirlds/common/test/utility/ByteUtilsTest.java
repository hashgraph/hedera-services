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

package com.swirlds.common.test.utility;

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.utility.ByteUtils.byteArrayToInt;
import static com.swirlds.common.utility.ByteUtils.byteArrayToLong;
import static com.swirlds.common.utility.ByteUtils.byteArrayToShort;
import static com.swirlds.common.utility.ByteUtils.intToByteArray;
import static com.swirlds.common.utility.ByteUtils.longToByteArray;
import static com.swirlds.common.utility.ByteUtils.shortToByteArray;
import static com.swirlds.common.utility.Units.BYTES_PER_INT;
import static com.swirlds.common.utility.Units.BYTES_PER_LONG;
import static com.swirlds.common.utility.Units.BYTES_PER_SHORT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ByteUtils Test")
class ByteUtilsTest {

    @Test
    @DisplayName("Short Array Test")
    void shortArrayTest() {
        final Random random = getRandomPrintSeed();
        final int count = 10_000;
        final int bufferSize = 1_000;

        final byte[] buffer = new byte[bufferSize];

        for (int i = 0; i < count; i++) {
            final short value = (short) random.nextInt();
            final int position = random.nextInt(bufferSize - BYTES_PER_SHORT);

            shortToByteArray(value, buffer, position);
            assertEquals(value, byteArrayToShort(buffer, position), "resulting value should match");

            final byte[] bytes = shortToByteArray(value);
            assertEquals(value, byteArrayToShort(bytes, 0), "resulting value should match");
        }
    }

    @Test
    @DisplayName("Int Array Test")
    void intArrayTest() {
        final Random random = getRandomPrintSeed();
        final int count = 10_000;
        final int bufferSize = 1_000;

        final byte[] buffer = new byte[bufferSize];

        for (int i = 0; i < count; i++) {
            final int value = random.nextInt();
            final int position = random.nextInt(bufferSize - BYTES_PER_INT);

            intToByteArray(value, buffer, position);
            assertEquals(value, byteArrayToInt(buffer, position), "resulting value should match");

            final byte[] bytes = intToByteArray(value);
            assertEquals(value, byteArrayToInt(bytes, 0), "resulting value should match");
        }
    }

    @Test
    @DisplayName("Long Array Test")
    void longArrayTest() {
        final Random random = getRandomPrintSeed();
        final int count = 10_000;
        final int bufferSize = 1_000;

        final byte[] buffer = new byte[bufferSize];

        for (int i = 0; i < count; i++) {
            final long value = random.nextLong();
            final int position = random.nextInt(bufferSize - BYTES_PER_LONG);

            longToByteArray(value, buffer, position);
            assertEquals(value, byteArrayToLong(buffer, position), "resulting value should match");

            final byte[] bytes = longToByteArray(value);
            assertEquals(value, byteArrayToLong(bytes, 0), "resulting value should match");
        }
    }

    @Test
    @DisplayName("Partial Long Test")
    void partialLongTest() {
        final Random random = getRandomPrintSeed();

        final long value = random.nextLong();
        final byte[] bytes = longToByteArray(value);

        for (int position = 0; position <= 8; position++) {
            final long partialValue = byteArrayToLong(bytes, position);
            final long expectedValue = (position == 8) ? 0 : (value << (position * 8));
            assertEquals(expectedValue, partialValue, "resulting value should match");
        }
    }

    @Test
    @DisplayName("Partial Int Test")
    void partialIntTest() {
        final Random random = getRandomPrintSeed();

        final int value = random.nextInt();
        final byte[] bytes = intToByteArray(value);

        for (int position = 0; position <= 4; position++) {
            final int partialValue = byteArrayToInt(bytes, position);
            final int expectedValue = (position == 4) ? 0 : (value << (position * 8));
            assertEquals(expectedValue, partialValue, "resulting value should match");
        }
    }

    @Test
    @DisplayName("Partial Short Test")
    void partialShortTest() {
        final Random random = getRandomPrintSeed();

        final short value = (short) random.nextInt();
        final byte[] bytes = shortToByteArray(value);

        for (int position = 0; position <= 2; position++) {
            final short partialValue = byteArrayToShort(bytes, position);
            final short expectedValue = (short) ((position == 2) ? 0 : (value << (position * 8)));
            assertEquals(expectedValue, partialValue, "resulting value should match");
        }
    }
}

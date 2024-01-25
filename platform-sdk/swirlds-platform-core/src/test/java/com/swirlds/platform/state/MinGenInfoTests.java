/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.platform.state.MinGenInfo.MAX_MINGEN_INFO_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MinGenInfoTests {

    @Test
    void emptySerializationTest() throws IOException {
        final List<MinGenInfo> original = List.of();

        final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        final SerializableDataOutputStream out = new SerializableDataOutputStream(byteOut);

        MinGenInfo.serializeList(original, out);

        final SerializableDataInputStream in =
                new SerializableDataInputStream(new ByteArrayInputStream(byteOut.toByteArray()));
        final List<MinGenInfo> deserialized = MinGenInfo.deserializeList(in);

        assertEquals(original, deserialized);
    }

    @Test
    void serializationTest() throws IOException {
        final Random random = getRandomPrintSeed();
        final int size = random.nextInt(1, MAX_MINGEN_INFO_SIZE);

        final List<MinGenInfo> original = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            original.add(new MinGenInfo(random.nextLong(), random.nextLong()));
        }

        final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        final SerializableDataOutputStream out = new SerializableDataOutputStream(byteOut);

        MinGenInfo.serializeList(original, out);

        final SerializableDataInputStream in =
                new SerializableDataInputStream(new ByteArrayInputStream(byteOut.toByteArray()));
        final List<MinGenInfo> deserialized = MinGenInfo.deserializeList(in);

        assertEquals(original, deserialized);
    }

    @Test
    void serializationOverflowTest() throws IOException {
        final Random random = getRandomPrintSeed();
        final int size = MAX_MINGEN_INFO_SIZE + random.nextInt(1, MAX_MINGEN_INFO_SIZE);

        final List<MinGenInfo> original = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            original.add(new MinGenInfo(random.nextLong(), random.nextLong()));
        }

        final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        final SerializableDataOutputStream out = new SerializableDataOutputStream(byteOut);

        MinGenInfo.serializeList(original, out);

        final SerializableDataInputStream in =
                new SerializableDataInputStream(new ByteArrayInputStream(byteOut.toByteArray()));

        assertThrows(IOException.class, () -> MinGenInfo.deserializeList(in));
    }
}

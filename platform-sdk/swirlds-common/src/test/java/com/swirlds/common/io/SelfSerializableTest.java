/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.io.exceptions.InvalidVersionException;
import com.swirlds.common.test.fixtures.io.InputOutputStream;
import com.swirlds.common.test.fixtures.io.SelfSerializableExample;
import com.swirlds.common.test.fixtures.junit.tags.TestComponentTags;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SelfSerializableTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @Tag(TestComponentTags.IO)
    @DisplayName("Serialize Deserialize")
    void serializeDeserialize(boolean includeClassId) throws IOException {
        SelfSerializableExample serializable = new SelfSerializableExample(123, "a simple string");

        InputOutputStream io = new InputOutputStream();
        io.getOutput().writeSerializable(serializable, includeClassId);
        io.startReading();
        SelfSerializableExample fromStream =
                io.getInput().readSerializable(includeClassId, SelfSerializableExample::new);

        assertEquals(serializable, fromStream);
        io.close();
    }

    @Test
    @Tag(TestComponentTags.IO)
    @DisplayName("Serialize Deserialize")
    void deserializeInvalidVersions() throws IOException {

        SelfSerializableExample serializable = new SelfSerializableExample(123, "a simple string");

        // Deserialize with a version number below the minimum
        serializable.setVersion(0);
        InputOutputStream io = new InputOutputStream();
        io.getOutput().writeSerializable(serializable, true);
        io.startReading();
        assertThrows(InvalidVersionException.class, () -> io.getInput()
                .readSerializable(true, SelfSerializableExample::new));

        // Deserialize with a version number above the current
        serializable.setVersion(1234);
        InputOutputStream io2 = new InputOutputStream();
        io2.getOutput().writeSerializable(serializable, true);
        io2.startReading();
        assertThrows(InvalidVersionException.class, () -> io2.getInput()
                .readSerializable(true, SelfSerializableExample::new));
    }

    @Test
    void pbjSupportTest() throws IOException {
        final SelfSerializableExample serializable = new SelfSerializableExample(666, "Not a PBJ object");
        final byte[] byteArray = {1, 2, 3};
        final Bytes bytes = Bytes.wrap(byteArray);

        try (final InputOutputStream io = new InputOutputStream()) {
            io.getOutput().writeSerializable(serializable, true);
            bytes.writeTo(io.getOutput().getWritableSequentialData());
            io.getOutput().writeSerializable(serializable, false);

            io.startReading();

            final SelfSerializable readSer1 = io.getInput().readSerializable(true, SelfSerializableExample::new);
            final Bytes readBytes = io.getInput().getReadableSequentialData().readBytes(byteArray.length);
            final SelfSerializable readSer2 = io.getInput().readSerializable(false, SelfSerializableExample::new);

            assertEquals(serializable, readSer1, "the serializable object should be the same as the one written");
            assertEquals(bytes, readBytes, "the bytes should be the same as the ones written");
            assertEquals(serializable, readSer2, "the serializable object should be the same as the one written");
        }
    }
}

/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.virtual;

import static com.swirlds.merkledb.serialize.BaseSerializer.VARIABLE_DATA_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class UniqueTokenKeySerializerTest {

    private static final long EXAMPLE_SERIAL = 0xFAFF_FFFF_FFFF_FFFFL;

    private static final int UNIQUE_TOKEN_KEY_SIZE = 17;

    @Test
    void deserializeToUniqueTokenKey_whenValidVersion_shouldMatch() throws IOException {
        final UniqueTokenKey src = new UniqueTokenKey(Long.MAX_VALUE, EXAMPLE_SERIAL);
        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        src.serialize(new SerializableDataOutputStream(byteStream));

        final UniqueTokenKeySerializer serializer = new UniqueTokenKeySerializer();
        final BufferedData inputBuffer = BufferedData.wrap(byteStream.toByteArray());
        final UniqueTokenKey dst = serializer.deserialize(inputBuffer);
        assertThat(dst.getNum()).isEqualTo(Long.MAX_VALUE);
        assertThat(dst.getTokenSerial()).isEqualTo(EXAMPLE_SERIAL);
    }

    @Test
    void serializeToPbj_shouldReturnExpectedBytes() {
        final ByteBuffer byteBuffer = ByteBuffer.allocate(UNIQUE_TOKEN_KEY_SIZE);
        final WritableSequentialData out = BufferedData.wrap(byteBuffer);
        final UniqueTokenKeySerializer serializer = new UniqueTokenKeySerializer();

        final long originalPos = out.position();
        serializer.serialize(new UniqueTokenKey(Long.MAX_VALUE, EXAMPLE_SERIAL), out);
        final long finalPos = out.position();

        assertThat(finalPos - originalPos).isEqualTo(UNIQUE_TOKEN_KEY_SIZE);
        assertThat(byteBuffer.array())
                .isEqualTo(Bytes.concat(
                        new byte[] {(byte) 0x88},
                        Longs.toByteArray(Long.MAX_VALUE),
                        Longs.toByteArray(EXAMPLE_SERIAL)));
    }

    @Test
    void serializeToByteBuffer_shouldReturnExpectedBytes() throws IOException {
        final byte[] arr = new byte[UNIQUE_TOKEN_KEY_SIZE];
        final BufferedData byteBuffer = BufferedData.wrap(arr);
        final UniqueTokenKeySerializer serializer = new UniqueTokenKeySerializer();
        serializer.serialize(new UniqueTokenKey(Long.MAX_VALUE, EXAMPLE_SERIAL), byteBuffer);

        assertThat(byteBuffer.position()).isEqualTo(UNIQUE_TOKEN_KEY_SIZE);
        assertThat(arr)
                .isEqualTo(Bytes.concat(
                        new byte[] {(byte) 0x88},
                        Longs.toByteArray(Long.MAX_VALUE),
                        Longs.toByteArray(EXAMPLE_SERIAL)));
    }

    @Test
    void serializerEquals_whenCorrectDataVersion_shouldReturnTrue() throws IOException {
        final BufferedData buffer = BufferedData.wrap(new byte[UNIQUE_TOKEN_KEY_SIZE]);
        final UniqueTokenKeySerializer serializer = new UniqueTokenKeySerializer();
        serializer.serialize(new UniqueTokenKey(Long.MAX_VALUE, EXAMPLE_SERIAL), buffer);
        buffer.reset();

        assertThat(serializer.equals(buffer, new UniqueTokenKey(Long.MAX_VALUE, EXAMPLE_SERIAL)))
                .isTrue();

        buffer.reset();
        assertThat(serializer.equals(buffer, new UniqueTokenKey(Long.MAX_VALUE, EXAMPLE_SERIAL)))
                .isTrue();

        buffer.reset();
        assertThat(serializer.equals(buffer, new UniqueTokenKey(10, EXAMPLE_SERIAL)))
                .isFalse();
    }

    // Test invariants. The below tests are designed to fail if one accidentally modifies specified
    // constants.
    @Test
    void serializer_shouldBeVariable() {
        final UniqueTokenKeySerializer serializer = new UniqueTokenKeySerializer();
        assertThat(serializer.getSerializedSize()).isEqualTo(VARIABLE_DATA_SIZE);
        assertThat(serializer.isVariableSize()).isTrue();
    }

    @Test
    void serializer_estimatedSize() {
        final UniqueTokenKeySerializer serializer = new UniqueTokenKeySerializer();
        assertThat(serializer.getTypicalSerializedSize()).isEqualTo(UNIQUE_TOKEN_KEY_SIZE);
    }

    @Test
    void serializer_getSerializedSize() {
        final var key = new UniqueTokenKey(Long.MAX_VALUE, EXAMPLE_SERIAL);
        final UniqueTokenKeySerializer serializer = new UniqueTokenKeySerializer();
        assertEquals(UNIQUE_TOKEN_KEY_SIZE, serializer.getSerializedSize(key));
    }

    @Test
    void serializer_version() {
        final UniqueTokenKeySerializer serializer = new UniqueTokenKeySerializer();
        assertThat(serializer.getVersion()).isEqualTo(1);
        assertThat(serializer.getCurrentDataVersion()).isEqualTo(1);
    }

    @Test
    void serializer_classId() {
        final UniqueTokenKeySerializer serializer = new UniqueTokenKeySerializer();
        assertThat(serializer.getClassId()).isEqualTo(0xb3c94b6cf62aa6c5L);
    }

    @Test
    void noopFunctions_forTestCoverage() throws IOException {
        final UniqueTokenKeySerializer serializer = new UniqueTokenKeySerializer();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final SerializableDataOutputStream dataOutputStream = new SerializableDataOutputStream(outputStream);
        serializer.serialize(dataOutputStream);
        assertThat(outputStream.toByteArray()).isEmpty();

        final SerializableDataInputStream dataInputStream =
                new SerializableDataInputStream(new ByteArrayInputStream(outputStream.toByteArray()));
        serializer.deserialize(dataInputStream, 1);
    }
}

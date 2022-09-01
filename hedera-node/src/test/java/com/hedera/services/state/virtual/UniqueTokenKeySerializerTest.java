/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.virtual;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.DataFileCommon;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class UniqueTokenKeySerializerTest {
    private static final long EXAMPLE_SERIAL = 0xFAFF_FFFF_FFFF_FFFFL;

    @Test
    void deserializeToUniqueTokenKey_whenValidVersion_shouldMatch() throws IOException {
        final UniqueTokenKey src = new UniqueTokenKey(Long.MAX_VALUE, EXAMPLE_SERIAL);
        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        src.serialize(new SerializableDataOutputStream(byteStream));

        final UniqueTokenKeySerializer serializer = new UniqueTokenKeySerializer();
        final ByteBuffer inputBuffer = ByteBuffer.wrap(byteStream.toByteArray());
        final UniqueTokenKey dst =
                serializer.deserialize(inputBuffer, UniqueTokenKey.CURRENT_VERSION);
        assertThat(dst.getNum()).isEqualTo(Long.MAX_VALUE);
        assertThat(dst.getTokenSerial()).isEqualTo(EXAMPLE_SERIAL);
    }

    @Test
    void serializeUniqueTokenKey_shouldReturnExpectedBytes() throws IOException {
        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        final UniqueTokenKeySerializer serializer = new UniqueTokenKeySerializer();
        final int len =
                serializer.serialize(
                        new UniqueTokenKey(Long.MAX_VALUE, EXAMPLE_SERIAL),
                        new SerializableDataOutputStream(byteStream));

        assertThat(len).isEqualTo(17);
        assertThat(byteStream.toByteArray())
                .isEqualTo(
                        Bytes.concat(
                                new byte[] {(byte) 0x88},
                                Longs.toByteArray(Long.MAX_VALUE),
                                Longs.toByteArray(EXAMPLE_SERIAL)));
    }

    @Test
    void serializerEquals_whenCorrectDataVersion_shouldReturnTrue() throws IOException {
        final ByteBuffer buffer = ByteBuffer.wrap(new byte[17]);
        new UniqueTokenKey(Long.MAX_VALUE, EXAMPLE_SERIAL).serialize(buffer);
        buffer.rewind();
        final UniqueTokenKeySerializer serializer = new UniqueTokenKeySerializer();

        assertThat(
                        serializer.equals(
                                buffer,
                                UniqueTokenKey.CURRENT_VERSION,
                                new UniqueTokenKey(Long.MAX_VALUE, EXAMPLE_SERIAL)))
                .isTrue();

        buffer.rewind();
        assertThat(
                        serializer.equals(
                                buffer,
                                UniqueTokenKey.CURRENT_VERSION,
                                new UniqueTokenKey(Long.MAX_VALUE, EXAMPLE_SERIAL)))
                .isTrue();

        buffer.rewind();
        assertThat(
                        serializer.equals(
                                buffer,
                                UniqueTokenKey.CURRENT_VERSION,
                                new UniqueTokenKey(10, EXAMPLE_SERIAL)))
                .isFalse();
    }

    @Test
    void deserializeKeySize_shouldReturnExpectedSize() throws IOException {
        final ByteBuffer buffer = ByteBuffer.wrap(new byte[17]);
        new UniqueTokenKey(Long.MAX_VALUE, EXAMPLE_SERIAL).serialize(buffer);
        buffer.rewind();
        final UniqueTokenKeySerializer serializer = new UniqueTokenKeySerializer();
        assertThat(serializer.deserializeKeySize(buffer)).isEqualTo(17);
    }

    // Test invariants. The below tests are designed to fail if one accidentally modifies specified
    // constants.
    @Test
    void serializer_shouldBeVariable() {
        final UniqueTokenKeySerializer serializer = new UniqueTokenKeySerializer();
        assertThat(serializer.getSerializedSize()).isEqualTo(DataFileCommon.VARIABLE_DATA_SIZE);
        assertThat(serializer.isVariableSize()).isTrue();
    }

    @Test
    void serializer_estimatedSize() {
        final UniqueTokenKeySerializer serializer = new UniqueTokenKeySerializer();
        assertThat(serializer.getTypicalSerializedSize()).isEqualTo(17);
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
        assertThat(serializer.getClassId()).isEqualTo(0xb3c94b6cf62aa6c4L);
    }

    @Test
    void noopFunctions_forTestCoverage() throws IOException {
        final UniqueTokenKeySerializer serializer = new UniqueTokenKeySerializer();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final SerializableDataOutputStream dataOutputStream =
                new SerializableDataOutputStream(outputStream);
        serializer.serialize(dataOutputStream);
        assertThat(outputStream.toByteArray()).isEmpty();

        final SerializableDataInputStream dataInputStream =
                new SerializableDataInputStream(
                        new ByteArrayInputStream(outputStream.toByteArray()));
        serializer.deserialize(dataInputStream, 1);
    }
}

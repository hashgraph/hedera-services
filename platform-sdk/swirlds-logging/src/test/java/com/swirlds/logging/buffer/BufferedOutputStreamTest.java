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

package com.swirlds.logging.buffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class BufferedOutputStreamTest {

    @Test
    void testWriteSingleByte() throws IOException {
        // Given
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream, 10)) {
            // When
            bufferedOutputStream.write('A');
        }

        // Then
        assertThat(outputStream.toString()).isEqualTo("A");
    }

    @Test
    void testWriteByteArray() throws IOException {
        // Given
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] bytes = "Hello, Swirlds!".getBytes(StandardCharsets.UTF_8);
        try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream, 10)) {
            // When
            bufferedOutputStream.write(bytes);
        }

        // Then
        assertThat(outputStream.toString()).isEqualTo("Hello, Swirlds!");
    }

    @Test
    void testWriteByteArrayWithOffsetAndLength() throws IOException {
        // Given
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] bytes = "Hello, Swirlds!".getBytes(StandardCharsets.UTF_8);
        try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream, 10)) {
            // When
            bufferedOutputStream.write(bytes, 7, 7);
        }

        // Then
        assertThat(outputStream.toString()).isEqualTo("Swirlds");
    }

    @Test
    void testFlush() throws IOException {
        // Given
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream, 10);

        // When
        bufferedOutputStream.write('A');
        bufferedOutputStream.flush();

        // Then
        assertThat(outputStream.toString()).isEqualTo("A");
    }

    @Test
    void testClose() throws IOException {
        // Given
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream, 10);

        // When
        bufferedOutputStream.close();

        // Then
        assertThat(outputStream.toString()).isEmpty();
    }

    @Test
    void testInvalidBufferCapacity() {
        // Given
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> new BufferedOutputStream(outputStream, 0));
    }
}

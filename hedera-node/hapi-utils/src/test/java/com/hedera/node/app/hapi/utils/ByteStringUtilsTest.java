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

package com.hedera.node.app.hapi.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import com.google.protobuf.ByteString;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class ByteStringUtilsTest {
    @Test
    void unsafeWrappingWorks() {
        final var wrappedData = ByteStringUtils.wrapUnsafely(data);
        assertArrayEquals(data, wrappedData.toByteArray());
    }

    @Test
    void unsafeUnwrappingWorks() {
        final var wrapper = ByteStringUtils.wrapUnsafely(data);
        final var unwrapped = ByteStringUtils.unwrapUnsafelyIfPossible(wrapper);
        assertSame(data, unwrapped);
    }

    @Test
    void unsafeUnwrappingOkIfNotSupported() {
        final var wrapper = mock(ByteString.class);
        assertDoesNotThrow(() -> ByteStringUtils.unwrapUnsafelyIfPossible(wrapper));
    }

    @Test
    void supportsOnlyReasonable() {
        final var wrapper = mock(ByteString.class);
        given(wrapper.size()).willReturn(ByteStringUtils.UnsafeByteOutput.SIZE - 1);
        assertFalse(ByteStringUtils.UnsafeByteOutput.supports(wrapper));
        given(wrapper.size()).willReturn(42);
        assertFalse(ByteStringUtils.UnsafeByteOutput.supports(wrapper));
        assertTrue(ByteStringUtils.UnsafeByteOutput.supports(ByteString.copyFrom(data)));
    }

    @Test
    void unsafeOutputUnsupportedAsExpected() {
        final var buffer = ByteBuffer.allocate(1);
        final var subject = new ByteStringUtils.UnsafeByteOutput();

        assertThrows(UnsupportedOperationException.class, () -> subject.write((byte) 1));
        assertThrows(UnsupportedOperationException.class, () -> subject.write(buffer));
        assertThrows(UnsupportedOperationException.class, () -> subject.writeLazy(buffer));
    }

    @Test
    void unsafeOutputSupportedAsExpected() throws IOException {
        final var subject = new ByteStringUtils.UnsafeByteOutput();

        subject.write(data, 0, data.length);

        assertSame(data, subject.getBytes());
    }

    @Test
    void propagatesCnfeAsIse() {
        assertThrows(IllegalStateException.class, () -> ByteStringUtils.UnsafeByteOutput.CLASS_BY_NAME.apply("Nope"));
    }

    @Test
    void worksAroundIoeWhenUnwrapping() throws IOException {
        final var wrapper = ByteString.copyFrom(data);
        final var byteOutput = mock(ByteStringUtils.UnsafeByteOutput.class);
        willThrow(IOException.class).given(byteOutput).writeLazy(any(), anyInt(), anyInt());
        assertDoesNotThrow(() -> ByteStringUtils.internalUnwrap(wrapper, byteOutput));
    }

    private static final byte[] data = "Between the idea and the reality".getBytes();
}

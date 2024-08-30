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

package com.swirlds.merkledb.utilities;

import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import sun.misc.Unsafe;

public class MemoryUtils {

    /**
     * Access to sun.misc.Unsafe required to close mapped byte buffers explicitly rather than
     * to rely on GC to collect them.
     */
    private static final Unsafe UNSAFE;

    /** Offset of the {@code java.nio.Buffer#address} field. */
    private static final long BYTE_BUFFER_ADDRESS_FIELD_OFFSET;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            throw new InternalError(e);
        }
        try {
            BYTE_BUFFER_ADDRESS_FIELD_OFFSET = UNSAFE.objectFieldOffset(Buffer.class.getDeclaredField("address"));
        } catch (final NoSuchFieldException | SecurityException | IllegalArgumentException e) {
            throw new InternalError(e);
        }
    }

    public static void closeMmapBuffer(final MappedByteBuffer buffer) {
        assert buffer != null;
        UNSAFE.invokeCleaner(buffer);
    }

    public static void closeDirectByteBuffer(final ByteBuffer buffer) {
        assert buffer != null;
        UNSAFE.invokeCleaner(buffer);
    }

    public static long getLongVolatile(final ByteBuffer buffer, final long offset) {
        final long address = bufferAddress(buffer);
        return UNSAFE.getLongVolatile(null, address + offset);
    }

    public static void putLongVolatile(final ByteBuffer buffer, final long offset, final long value) {
        final long address = bufferAddress(buffer);
        UNSAFE.putLongVolatile(null, address + offset, value);
    }

    public static boolean compareAndSwapLong(
            final ByteBuffer buffer, final long offset, final long expected, final long value) {
        final long address = bufferAddress(buffer);
        return UNSAFE.compareAndSwapLong(null, address + offset, expected, value);
    }

    public static void setMemory(final ByteBuffer buffer, final long offset, final long len, final byte value) {
        final long address = bufferAddress(buffer);
        UNSAFE.setMemory(address + offset, len, value);
    }

    /**
     * Get the address at which the underlying buffer storage begins.
     *
     * @param buffer that wraps the underlying storage.
     * @return the memory address at which the buffer storage begins.
     */
    private static long bufferAddress(final Buffer buffer) {
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("buffer.isDirect() must be true");
        }
        return UNSAFE.getLong(buffer, BYTE_BUFFER_ADDRESS_FIELD_OFFSET);
    }
}

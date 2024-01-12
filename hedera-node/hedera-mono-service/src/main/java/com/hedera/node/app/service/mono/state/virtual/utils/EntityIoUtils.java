/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.virtual.utils;

public final class EntityIoUtils {
    private EntityIoUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static <E extends Exception> void writeBytes(
            final byte[] data,
            final ThrowingConsumer<Integer, E> writeIntFn,
            final ThrowingConsumer<byte[], E> writeBytesFn)
            throws E {
        writeIntFn.accept(data.length);
        writeBytesFn.accept(data);
    }

    public static <E extends Exception> byte[] readBytes(
            final ThrowingSupplier<Integer, E> readIntFn, final ThrowingConsumer<byte[], E> readBytesFn) throws E {
        final var len = readIntFn.get();
        final var data = new byte[len];
        readBytesFn.accept(data);
        return data;
    }
}

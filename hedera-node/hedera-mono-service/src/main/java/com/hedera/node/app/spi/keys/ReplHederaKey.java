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
package com.hedera.node.app.spi.keys;

import com.google.common.io.ByteStreams;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualValue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Temporary key needed to not break existing code. This will be removed after legacy {@link
 * com.hedera.services.legacy.core.jproto.JKey} is replaced with {@link HederaKey}. Once this is
 * removed {@link HederaKey} will extend {@link VirtualValue}
 */
public interface ReplHederaKey extends VirtualValue, HederaKey {
    @Override
    default void serialize(final ByteBuffer buf) throws IOException {
        try (final var baos = new ByteArrayOutputStream()) {
            try (final var out = new SerializableDataOutputStream(baos)) {
                this.serialize(out);
            }
            baos.flush();
            buf.put(baos.toByteArray());
        }
    }

    @Override
    default void deserialize(final ByteBuffer buf, final int version) throws IOException {
        try (final var bbIn = new ByteBufferBackedInputStream(buf)) {
            try (final var in = new SerializableDataInputStream(bbIn)) {
                this.deserialize(in, version);
            }
        }
    }
}

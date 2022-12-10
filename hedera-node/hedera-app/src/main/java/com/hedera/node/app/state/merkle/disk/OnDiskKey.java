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
package com.hedera.node.app.state.merkle.disk;

import com.hedera.node.app.spi.state.Parser;
import com.hedera.node.app.spi.state.Writer;
import com.hedera.node.app.state.merkle.data.ByteBufferDataInput;
import com.hedera.node.app.state.merkle.data.ByteBufferDataOutput;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class OnDiskKey<K extends Comparable<K>> implements VirtualKey<OnDiskKey<K>> {
    private static final long CLASS_ID = 0xa8d20bd12992d91bL;

    private K key;
    private final Parser<K> keyParser;
    private final Writer<K> keyWriter;

    public OnDiskKey(@NonNull final Parser<K> keyParser, @NonNull final Writer<K> keyWriter) {
        this.keyWriter = Objects.requireNonNull(keyWriter);
        this.keyParser = Objects.requireNonNull(keyParser);
    }

    public OnDiskKey(
            @NonNull final K key,
            @NonNull final Parser<K> keyParser,
            @NonNull final Writer<K> keyWriter) {
        this.key = Objects.requireNonNull(key);
        this.keyWriter = Objects.requireNonNull(keyWriter);
        this.keyParser = Objects.requireNonNull(keyParser);
    }

    @NonNull
    public K getKey() {
        return key;
    }

    @Override
    public void serialize(@NonNull final ByteBuffer byteBuffer) throws IOException {
        final var output = new ByteBufferDataOutput(byteBuffer);
        keyWriter.write(key, output);
    }

    @Override
    public void serialize(@NonNull final SerializableDataOutputStream serializableDataOutputStream)
            throws IOException {
        keyWriter.write(key, serializableDataOutputStream);
    }

    @Override
    public void deserialize(@NonNull final ByteBuffer byteBuffer, int ignored) throws IOException {
        final var input = new ByteBufferDataInput(byteBuffer);
        key = keyParser.parse(input);
    }

    @Override
    public void deserialize(
            @NonNull final SerializableDataInputStream serializableDataInputStream, int ignored)
            throws IOException {
        key = keyParser.parse(new DataInputStream(serializableDataInputStream));
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public int compareTo(@NonNull final OnDiskKey<K> o) {
        // By contract, throw NPE if o or o.key are null
        return key.compareTo(o.key);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OnDiskKey<?> onDiskKey)) return false;
        return Objects.equals(key, onDiskKey.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }
}

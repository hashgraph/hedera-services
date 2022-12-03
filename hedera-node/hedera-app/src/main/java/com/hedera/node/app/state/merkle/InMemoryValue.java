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
package com.hedera.node.app.state.merkle;

import com.hedera.node.app.spi.state.Parser;
import com.hedera.node.app.spi.state.Writer;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.merkle.map.MerkleMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Objects;

/** */
public final class InMemoryValue<K, V> extends PartialMerkleLeaf
        implements MerkleNode, Keyed<InMemoryKey<K>>, SelfSerializable, MerkleLeaf {

    /** The classId for serialization purposes to be used for the created {@link InMemoryValue} */
    private final long classId;
    /** The key associated with this value. {@link MerkleMap} requires we do this. */
    private InMemoryKey<K> key;
    /** The actual value. For example, it could be an Account or SmartContract. */
    private V val;
    /** The writer to use for serializing a key */
    private final Writer<K> keyWriter;
    /** The writer to use for serializing a value */
    private final Writer<V> valueWriter;
    /** The parser to use for deserializing a key */
    private final Parser<K> keyParser;
    /** The parser to use for deserializing a value */
    private final Parser<V> valueParser;

    public InMemoryValue() {
        // These are ALL BOGUS
        this.classId = -1;
        this.keyWriter = null;
        this.keyParser = null;
        this.valueWriter = null;
        this.valueParser = null;
    }

    /**
     * Used by the deserialization system only
     *
     * @param classId The classId to use for any created {@link InMemoryValue}s.
     * @param keyParser The {@link Parser} to use for parsing keys from state on disk
     * @param valueParser The {@link Parser} to use for parsing values from state on disk
     * @param keyWriter The {@link Writer} to use for writing keys to state on disk, or for hashing
     * @param valueWriter The {@link Writer} to use for writing values to state on disk, or for
     *     hashing
     */
    public InMemoryValue(
            final long classId,
            @NonNull final Parser<K> keyParser,
            @NonNull final Parser<V> valueParser,
            @NonNull final Writer<K> keyWriter,
            @NonNull final Writer<V> valueWriter) {
        this.classId = classId;
        this.keyWriter = Objects.requireNonNull(keyWriter);
        this.valueWriter = Objects.requireNonNull(valueWriter);
        this.keyParser = Objects.requireNonNull(keyParser);
        this.valueParser = Objects.requireNonNull(valueParser);
    }

    /**
     * Create a new instance with the given value.
     *
     * @param classId The classId to use for any created {@link InMemoryValue}s.
     * @param key The associated key.
     * @param value The value.
     * @param keyParser The {@link Parser} to use for parsing keys from state on disk
     * @param valueParser The {@link Parser} to use for parsing values from state on disk
     * @param keyWriter The {@link Writer} to use for writing keys to state on disk, or for hashing
     * @param valueWriter The {@link Writer} to use for writing values to state on disk, or for
     *     hashing
     */
    public InMemoryValue(
            final long classId,
            @NonNull final InMemoryKey<K> key,
            @NonNull final V value,
            @NonNull Parser<K> keyParser,
            @NonNull Parser<V> valueParser,
            @NonNull Writer<K> keyWriter,
            @NonNull Writer<V> valueWriter) {
        this(classId, keyParser, valueParser, keyWriter, valueWriter);
        this.key = Objects.requireNonNull(key);
        this.val = Objects.requireNonNull(value);
    }

    @Override
    public InMemoryValue<K, V> copy() {
        throwIfImmutable();
        throwIfDestroyed();

        final var cp =
                new InMemoryValue<>(
                        classId, key, val, keyParser, valueParser, keyWriter, valueWriter);
        setImmutable(true);
        return cp;
    }

    @Override
    public long getClassId() {
        return classId;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public InMemoryKey<K> getKey() {
        return key;
    }

    @Override
    public void setKey(@NonNull final InMemoryKey<K> inMemoryKey) {
        throwIfImmutable();
        this.key = Objects.requireNonNull(inMemoryKey);
    }

    public V getValue() {
        return val;
    }

    public void setValue(final V value) {
        throwIfImmutable();
        this.val = value;
    }

    @Override
    public void deserialize(SerializableDataInputStream serializableDataInputStream, int version)
            throws IOException {
        final var k = keyParser.deserialize(serializableDataInputStream, version);
        if (k == null) {
            throw new IllegalStateException("Deserialized a null key, which is not allowed!");
        }
        this.key = new InMemoryKey<>(k);
        this.val = valueParser.deserialize(serializableDataInputStream, version);
    }

    @Override
    public void serialize(SerializableDataOutputStream serializableDataOutputStream)
            throws IOException {
        keyWriter.serialize(key.key(), serializableDataOutputStream);
        valueWriter.serialize(val, serializableDataOutputStream);
    }
}

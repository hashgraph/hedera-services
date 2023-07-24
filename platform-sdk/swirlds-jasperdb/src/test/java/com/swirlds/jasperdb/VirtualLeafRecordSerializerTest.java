/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.jasperdb;

import static com.swirlds.common.test.fixtures.RandomUtils.randomString;
import static com.swirlds.jasperdb.ExampleFixedSizeVirtualValue.RANDOM_BYTES;
import static com.swirlds.jasperdb.VirtualLeafRecordSerializer.ClassVersion.ORIGINAL;
import static com.swirlds.jasperdb.VirtualLeafRecordSerializer.ClassVersion.REMOVED_LEAF_HASHES;
import static com.swirlds.jasperdb.files.DataFileCommon.VARIABLE_DATA_SIZE;
import static com.swirlds.jasperdb.utilities.HashTools.DEFAULT_DIGEST;
import static org.apache.commons.lang3.RandomUtils.nextInt;
import static org.apache.commons.lang3.RandomUtils.nextLong;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashBuilder;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.hashmap.KeySerializer;
import com.swirlds.virtualmap.VirtualLongKey;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@SuppressWarnings("unchecked")
class VirtualLeafRecordSerializerTest {
    @BeforeAll
    static void setUp() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds.jasperdb");
    }

    @ParameterizedTest
    @EnumSource(TestType.class)
    @SuppressWarnings("rawtypes")
    void testEqualsAndHashCode(final TestType testType) {
        final VirtualLeafRecordSerializer virtualLeafRecordSerializer1 = createSerializer(testType);
        final VirtualLeafRecordSerializer virtualLeafRecordSerializer2 = createSerializer(testType);

        assertEquals(
                virtualLeafRecordSerializer1,
                virtualLeafRecordSerializer2,
                "Two identical VirtualLeafRecordSerializers did not equal each other");

        assertEquals(
                virtualLeafRecordSerializer1.hashCode(),
                virtualLeafRecordSerializer2.hashCode(),
                "Two identical VirtualLeafRecordSerializers did not have the same hash code");
    }

    @ParameterizedTest
    @EnumSource(TestType.class)
    void testSerializeDeserialize_removedLeafHashVersion(final TestType testType) throws IOException {
        final VirtualLeafRecordSerializer expectedSerializer = createSerializer(testType);
        final ByteBuffer buffer = ByteBuffer.allocate(1024);
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        expectedSerializer.serialize(new SerializableDataOutputStream(out));

        buffer.put(out.toByteArray());
        buffer.flip();

        // creating empty serializer
        final VirtualLeafRecordSerializer actualSerializer = new VirtualLeafRecordSerializer();

        SerializableDataInputStream in = new SerializableDataInputStream(new ByteArrayInputStream(buffer.array()));
        actualSerializer.deserialize(in, REMOVED_LEAF_HASHES);

        assertEquals(expectedSerializer, actualSerializer, "Deserialized serializer did not match original serializer");
    }

    @SuppressWarnings("rawtypes")
    @ParameterizedTest
    @EnumSource(TestType.class)
    void testSerializeDeserialize_originalVersion(final TestType testType) throws IOException {
        final VirtualLeafRecordSerializer expectedSerializer = createSerializer(testType);
        final KeySerializer<? extends VirtualLongKey> keySerializer =
                testType.dataType().getKeySerializer();
        final SelfSerializableSupplier<? extends ExampleByteArrayVirtualValue> valueSerializer =
                testType.dataType().getValueSerializer();
        final boolean expectedVariableDataSize = !testType.fixedSize;

        final ByteBuffer buffer = ByteBuffer.allocate(1024);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(1024);
        SerializableDataOutputStream out = new SerializableDataOutputStream(byteArrayOutputStream);
        long expectedVersion = generateVersion(true);
        out.writeLong(expectedVersion);
        out.writeSerializable(keySerializer, true);
        out.writeSerializable(valueSerializer, true);

        out.writeBoolean(expectedVariableDataSize);
        int originalSerializedSize = expectedVariableDataSize
                ? VARIABLE_DATA_SIZE
                : valueSerializer.get().getData().length + DEFAULT_DIGEST.digestLength();
        int expectedSerializedSize = expectedVariableDataSize
                ? VARIABLE_DATA_SIZE
                : valueSerializer.get().getData().length;
        out.writeInt(originalSerializedSize);
        out.writeInt(expectedSerializer.getHeaderSize());
        out.writeBoolean(false); // byteMaxSize

        buffer.put(byteArrayOutputStream.toByteArray());
        buffer.flip();

        // creating empty serializer
        final VirtualLeafRecordSerializer actualSerializer = new VirtualLeafRecordSerializer();

        SerializableDataInputStream in = new SerializableDataInputStream(new ByteArrayInputStream(buffer.array()));
        actualSerializer.deserialize(in, ORIGINAL);

        assertEquals(
                expectedVersion & 0xFFFFFFFFFFFF0000L,
                actualSerializer.getCurrentDataVersion(),
                "Deserialized version did not match original version with removed hash version");
        assertEquals(
                expectedSerializer.isVariableSize(),
                actualSerializer.isVariableSize(),
                "Deserialized variable size did not match original variable size");
        assertEquals(
                expectedSerializer.getHeaderSize(),
                actualSerializer.getHeaderSize(),
                "Deserialized header size did not match original header size");
        assertEquals(
                expectedSerializedSize,
                actualSerializer.getSerializedSize(),
                "Deserialized size did not match original size");
    }

    @ParameterizedTest
    @EnumSource(TestType.class)
    void testSerializedSize(final TestType testType) {
        final var serializer = createSerializer(testType);
        int expectedSerializedSize = testType.fixedSize
                ? testType.dataType().getKeySerializer().getSerializedSize()
                        + testType.dataType().getValueSerializer().get().getData().length
                        + Long.BYTES
                : -1;
        assertEquals(expectedSerializedSize, serializer.getSerializedSize());
    }

    @ParameterizedTest
    @EnumSource(TestType.class)
    void testHeaderSize(final TestType testType) {
        final var serializer = createSerializer(testType);
        int expectedSerializedSize = testType.fixedSize ? Long.BYTES : Long.BYTES + Integer.BYTES;
        assertEquals(expectedSerializedSize, serializer.getHeaderSize());
    }

    @ParameterizedTest
    @EnumSource(TestType.class)
    void testSerializeDeserializeRecord(final TestType testType) throws IOException {
        final VirtualLeafRecord<VirtualLongKey, ExampleByteArrayVirtualValue> record =
                testType.dataType().createVirtualLeafRecord(nextLong(), nextInt(), nextInt());

        final var serializer = createSerializer(testType);
        final ByteBuffer buffer = ByteBuffer.allocate(2048);
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        serializer.serialize(record, new SerializableDataOutputStream(out));

        buffer.put(out.toByteArray());
        buffer.flip();

        final VirtualLeafRecord<VirtualLongKey, ExampleByteArrayVirtualValue> deserializedRecord =
                serializer.deserialize(buffer, serializer.getCurrentDataVersion());
        assertEquals(record, deserializedRecord, "Deserialized record did not match original record");
    }

    /**
     * This test emulates a record that was serialized with a hash
     */
    @Test
    void testDeserializeWithHash() throws IOException {
        VirtualLeafRecordSerializer<VirtualLongKey, ExampleByteArrayVirtualValue> serializer =
                createSerializer(TestType.fixed_fixed);

        // emulate a serialized record
        final ByteBuffer buffer = ByteBuffer.allocate(1024);
        final long path = nextLong();
        final long key = nextLong();
        // RANDOM_BYTES is the size of ExampleFixedSizeVirtualValue data
        final String value = randomString(new Random(), RANDOM_BYTES);
        buffer.putLong(path);
        Hash hash = generateRandomHash();
        buffer.put(hash.getValue());
        buffer.putLong(key);
        int id = nextInt();
        buffer.putInt(id); // value id
        buffer.put(value.getBytes());

        buffer.flip();

        VirtualLeafRecord<VirtualLongKey, ExampleByteArrayVirtualValue> record =
                serializer.deserialize(buffer, serializer.getCurrentDataVersion() | 0x1);

        assertEquals(path, record.getPath());
        assertEquals(key, record.getKey().getKeyAsLong());
        assertEquals(value, new String(record.getValue().getData()));
    }

    private static Hash generateRandomHash() {
        Hash hash = new HashBuilder(DEFAULT_DIGEST).update(nextInt()).build();
        return hash;
    }

    @ParameterizedTest
    @EnumSource(TestType.class)
    public void testGetSerializedSizeForVersionForFixedSize_noHash(final TestType testType) {
        long version = generateVersion(false);
        VirtualLeafRecordSerializer<VirtualLongKey, ExampleByteArrayVirtualValue> serializer =
                createSerializer(testType);
        if (testType.fixedSize) {
            assertEquals(
                    Long.BYTES
                            + testType.dataType().getKeySerializer().getSerializedSize()
                            + testType.dataType().getValueSerializer().get().getData().length,
                    serializer.getSerializedSize(version));
        } else {
            assertEquals(VARIABLE_DATA_SIZE, serializer.getSerializedSize(version));
        }
    }

    @ParameterizedTest
    @EnumSource(TestType.class)
    public void testGetSerializedSizeForVersionForFixedSize_withHash(final TestType testType) {
        long version = generateVersion(true);
        VirtualLeafRecordSerializer<VirtualLongKey, ExampleByteArrayVirtualValue> serializer =
                createSerializer(testType);
        if (testType.fixedSize) {
            assertEquals(
                    Long.BYTES
                            + testType.dataType().getKeySerializer().getSerializedSize()
                            + testType.dataType().getValueSerializer().get().getData().length
                            + DEFAULT_DIGEST.digestLength(),
                    serializer.getSerializedSize(version));
        } else {
            assertEquals(VARIABLE_DATA_SIZE, serializer.getSerializedSize(version));
        }
    }

    @Test
    void testString() {
        VirtualLeafRecordSerializer<VirtualLongKey, ExampleByteArrayVirtualValue> serializer =
                createSerializer(TestType.fixed_fixed);
        assertEquals(
                "VirtualLeafRecordSerializer[currentVersion=1232855760896,hasVariableDataSize=false,totalSerializedSize=48,headerSize=8,byteMaxSize=false]",
                serializer.toString());
    }

    private static long generateVersion(boolean withHash) {
        long result = ((long) nextInt(1, 100) << 16) | ((long) nextInt(1, 100) << 32);
        return withHash ? result | 0x1 : result;
    }

    private static VirtualLeafRecordSerializer<VirtualLongKey, ExampleByteArrayVirtualValue> createSerializer(
            TestType testType) {
        final KeySerializer<? extends VirtualLongKey> keySerializer =
                testType.dataType().getKeySerializer();
        final SelfSerializableSupplier<? extends ExampleByteArrayVirtualValue> valueSerializer =
                testType.dataType().getValueSerializer();
        return new VirtualLeafRecordSerializer<>(
                (short) keySerializer.getCurrentDataVersion(),
                keySerializer.getSerializedSize(),
                (SelfSerializableSupplier<VirtualLongKey>) keySerializer,
                (short) valueSerializer.getVersion(),
                testType.fixedSize ? valueSerializer.get().getData().length : VARIABLE_DATA_SIZE,
                (SelfSerializableSupplier<ExampleByteArrayVirtualValue>) valueSerializer,
                false);
    }
}

/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.AugmentedDataOutputStream;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.test.TransactionUtils;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Serializable Stream Tests")
public class SerializableStreamTests {
    static final String PACKAGE_PREFIX = "com.swirlds";
    private static final int MAX_LENGTH = 1000;
    private static final int LENGTH_IN_BYTES = 4;
    private static final int CHECKSUM_IN_BYTES = 4;
    private static final Random random = new Random();

    @BeforeAll
    static void setUp() throws ConstructableRegistryException {
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();

        registry.registerConstructables(PACKAGE_PREFIX);

        registry.registerConstructable(
                new ClassConstructorPair(SelfSerializableExample.class, SelfSerializableExample::new));
    }

    static Stream<byte[]> byteArrayArgProvider() {
        return Stream.of(null, new byte[0], new byte[] {1, 2, 3}, "some string".getBytes());
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.IO)
    @DisplayName("Int Array")
    void intArray() throws IOException {

        // Test null array
        int[] data = null;
        InputOutputStream io = new InputOutputStream();
        io.getOutput().writeIntArray(data);
        io.startReading();
        int[] resultingData = io.getInput().readIntArray(Integer.MAX_VALUE);
        assertEquals(resultingData, null);

        // Test regular array
        data = new int[] {1, 2, 3};
        io = new InputOutputStream();
        io.getOutput().writeIntArray(data);
        io.startReading();
        resultingData = io.getInput().readIntArray(Integer.MAX_VALUE);
        assertEquals(data.length, resultingData.length);
        for (int index = 0; index < resultingData.length; index++) {
            assertEquals(data[index], resultingData[index]);
        }

        // Test data that is too long
        io = new InputOutputStream();
        io.getOutput().writeIntArray(data);
        io.startReading();
        final SerializableDataInputStream in = io.getInput();
        assertThrows(IOException.class, () -> in.readIntArray(2));
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.IO)
    @DisplayName("Int List")
    void intList() throws IOException {

        // Test null array
        List<Integer> data = null;
        InputOutputStream io = new InputOutputStream();
        io.getOutput().writeIntList(data);
        io.startReading();
        List<Integer> resultingData = io.getInput().readIntList(Integer.MAX_VALUE);
        assertEquals(resultingData, null);

        // Test regular array
        data = new LinkedList<>();
        data.add(1);
        data.add(2);
        data.add(3);
        io = new InputOutputStream();
        io.getOutput().writeIntList(data);
        io.startReading();
        resultingData = io.getInput().readIntList(Integer.MAX_VALUE);
        assertEquals(data.size(), resultingData.size());
        for (int index = 0; index < resultingData.size(); index++) {
            assertEquals(data.get(index), resultingData.get(index));
        }

        // Test data that is too long
        io = new InputOutputStream();
        io.getOutput().writeIntList(data);
        io.startReading();
        final SerializableDataInputStream in = io.getInput();
        assertThrows(IOException.class, () -> in.readIntArray(2));
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.IO)
    @DisplayName("Long Array")
    void longArray() throws IOException {

        // Test null array
        long[] data = null;
        InputOutputStream io = new InputOutputStream();
        io.getOutput().writeLongArray(data);
        io.startReading();
        long[] resultingData = io.getInput().readLongArray(Integer.MAX_VALUE);
        assertEquals(resultingData, null);

        // Test regular array
        data = new long[] {1, 2, 3};
        io = new InputOutputStream();
        io.getOutput().writeLongArray(data);
        io.startReading();
        resultingData = io.getInput().readLongArray(Integer.MAX_VALUE);
        assertEquals(data.length, resultingData.length);
        for (int index = 0; index < resultingData.length; index++) {
            assertEquals(data[index], resultingData[index]);
        }

        // Test data that is too long
        io = new InputOutputStream();
        io.getOutput().writeLongArray(data);
        io.startReading();
        final SerializableDataInputStream in = io.getInput();
        assertThrows(IOException.class, () -> in.readLongArray(2));
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.IO)
    @DisplayName("Long List")
    void longList() throws IOException {

        // Test null array
        List<Long> data = null;
        InputOutputStream io = new InputOutputStream();
        io.getOutput().writeLongList(data);
        io.startReading();
        List<Long> resultingData = io.getInput().readLongList(Integer.MAX_VALUE);
        assertEquals(resultingData, null);

        // Test regular array
        data = new LinkedList<>();
        data.add(1L);
        data.add(2L);
        data.add(3L);
        io = new InputOutputStream();
        io.getOutput().writeLongList(data);
        io.startReading();
        resultingData = io.getInput().readLongList(Integer.MAX_VALUE);
        assertEquals(data.size(), resultingData.size());
        for (int index = 0; index < resultingData.size(); index++) {
            assertEquals(data.get(index), resultingData.get(index));
        }

        // Test data that is too long
        io = new InputOutputStream();
        io.getOutput().writeLongList(data);
        io.startReading();
        final SerializableDataInputStream in = io.getInput();
        assertThrows(IOException.class, () -> in.readLongList(2));
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.IO)
    @DisplayName("Float Array")
    void floatArray() throws IOException {

        // Test null array
        float[] data = null;
        InputOutputStream io = new InputOutputStream();
        io.getOutput().writeFloatArray(data);
        io.startReading();
        float[] resultingData = io.getInput().readFloatArray(Integer.MAX_VALUE);
        assertEquals(resultingData, null);

        // Test regular array
        data = new float[] {1, 2, 3};
        io = new InputOutputStream();
        io.getOutput().writeFloatArray(data);
        io.startReading();
        resultingData = io.getInput().readFloatArray(Integer.MAX_VALUE);
        assertEquals(data.length, resultingData.length);
        for (int index = 0; index < resultingData.length; index++) {
            assertEquals(data[index], resultingData[index]);
        }

        // Test data that is too long
        io = new InputOutputStream();
        io.getOutput().writeFloatArray(data);
        io.startReading();
        final SerializableDataInputStream in = io.getInput();
        assertThrows(IOException.class, () -> in.readFloatArray(2));
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.IO)
    @DisplayName("Float List")
    void floatList() throws IOException {

        // Test null array
        List<Float> data = null;
        InputOutputStream io = new InputOutputStream();
        io.getOutput().writeFloatList(data);
        io.startReading();
        List<Float> resultingData = io.getInput().readFloatList(Integer.MAX_VALUE);
        assertEquals(resultingData, null);

        // Test regular array
        data = new LinkedList<>();
        data.add(1.0F);
        data.add(2.0F);
        data.add(3.0F);
        io = new InputOutputStream();
        io.getOutput().writeFloatList(data);
        io.startReading();
        resultingData = io.getInput().readFloatList(Integer.MAX_VALUE);
        assertEquals(data.size(), resultingData.size());
        for (int index = 0; index < resultingData.size(); index++) {
            assertEquals(data.get(index), resultingData.get(index));
        }

        // Test data that is too long
        io = new InputOutputStream();
        io.getOutput().writeFloatList(data);
        io.startReading();
        final SerializableDataInputStream in = io.getInput();
        assertThrows(IOException.class, () -> in.readFloatList(2));
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.IO)
    @DisplayName("Double Array")
    void doubleArray() throws IOException {

        // Test null array
        double[] data = null;
        InputOutputStream io = new InputOutputStream();
        io.getOutput().writeDoubleArray(data);
        io.startReading();
        double[] resultingData = io.getInput().readDoubleArray(Integer.MAX_VALUE);
        assertEquals(resultingData, null);

        // Test regular array
        data = new double[] {1, 2, 3};
        io = new InputOutputStream();
        io.getOutput().writeDoubleArray(data);
        io.startReading();
        resultingData = io.getInput().readDoubleArray(Integer.MAX_VALUE);
        assertEquals(data.length, resultingData.length);
        for (int index = 0; index < resultingData.length; index++) {
            assertEquals(data[index], resultingData[index]);
        }

        // Test data that is too long
        io = new InputOutputStream();
        io.getOutput().writeDoubleArray(data);
        io.startReading();
        final SerializableDataInputStream in = io.getInput();
        assertThrows(IOException.class, () -> in.readDoubleArray(2));
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.IO)
    @DisplayName("Double List")
    void doubleList() throws IOException {

        // Test null array
        List<Double> data = null;
        InputOutputStream io = new InputOutputStream();
        io.getOutput().writeDoubleList(data);
        io.startReading();
        List<Double> resultingData = io.getInput().readDoubleList(Integer.MAX_VALUE);
        assertEquals(resultingData, null);

        // Test regular array
        data = new LinkedList<>();
        data.add(1.0);
        data.add(2.0);
        data.add(3.0);
        io = new InputOutputStream();
        io.getOutput().writeDoubleList(data);
        io.startReading();
        resultingData = io.getInput().readDoubleList(Integer.MAX_VALUE);
        assertEquals(data.size(), resultingData.size());
        for (int index = 0; index < resultingData.size(); index++) {
            assertEquals(data.get(index), resultingData.get(index));
        }

        // Test data that is too long
        io = new InputOutputStream();
        io.getOutput().writeDoubleList(data);
        io.startReading();
        final SerializableDataInputStream in = io.getInput();
        assertThrows(IOException.class, () -> in.readDoubleList(2));
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.IO)
    @DisplayName("String Array")
    void stringArray() throws IOException {

        // Test null array
        String[] data = null;
        InputOutputStream io = new InputOutputStream();
        io.getOutput().writeStringArray(data);
        io.startReading();
        String[] resultingData = io.getInput().readStringArray(Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(resultingData, null);

        // Test regular array
        data = new String[] {"this", "is", "a", "test"};
        io = new InputOutputStream();
        io.getOutput().writeStringArray(data);
        io.startReading();
        resultingData = io.getInput().readStringArray(Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(data.length, resultingData.length);
        for (int index = 0; index < resultingData.length; index++) {
            assertEquals(data[index], resultingData[index]);
        }

        // Test data that is too long
        io = new InputOutputStream();
        io.getOutput().writeStringArray(data);
        io.startReading();
        final SerializableDataInputStream in = io.getInput();
        assertThrows(IOException.class, () -> in.readStringArray(2, Integer.MAX_VALUE));

        io = new InputOutputStream();
        io.getOutput().writeStringArray(data);
        io.startReading();
        final SerializableDataInputStream in2 = io.getInput();
        assertThrows(IOException.class, () -> in2.readStringArray(Integer.MAX_VALUE, 2));
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.IO)
    @DisplayName("String List")
    void stringList() throws IOException {

        // Test null array
        List<String> data = null;
        InputOutputStream io = new InputOutputStream();
        io.getOutput().writeStringList(data);
        io.startReading();
        List<String> resultingData = io.getInput().readStringList(Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(resultingData, null);

        // Test regular array
        data = new LinkedList<>();
        data.add("this");
        data.add("is");
        data.add("a");
        data.add("test");
        io = new InputOutputStream();
        io.getOutput().writeStringList(data);
        io.startReading();
        resultingData = io.getInput().readStringList(Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(data.size(), resultingData.size());
        for (int index = 0; index < resultingData.size(); index++) {
            assertEquals(data.get(index), resultingData.get(index));
        }

        // Test data that is too long
        io = new InputOutputStream();
        io.getOutput().writeStringList(data);
        io.startReading();
        final SerializableDataInputStream in = io.getInput();
        assertThrows(IOException.class, () -> in.readStringList(2, Integer.MAX_VALUE));

        io = new InputOutputStream();
        io.getOutput().writeStringList(data);
        io.startReading();
        final SerializableDataInputStream in2 = io.getInput();
        assertThrows(IOException.class, () -> in2.readStringList(Integer.MAX_VALUE, 2));
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.IO)
    @DisplayName("String normalization")
    void stringNormalization() throws IOException {
        final Charset charset = StandardCharsets.UTF_8;
        // the last character can be represented in different ways in UTF-8
        final String originalString = "Lazar Petrović";
        final byte[] nfd =
                Normalizer.normalize(originalString, Normalizer.Form.NFD).getBytes(charset);
        final byte[] nfc =
                Normalizer.normalize(originalString, Normalizer.Form.NFC).getBytes(charset);
        assertFalse(Arrays.equals(nfd, nfc), "this string should have different byte representations");

        final InputOutputStream io = new InputOutputStream();
        io.getOutput().writeNormalisedString(new String(nfd, charset));
        io.getOutput().writeNormalisedString(new String(nfc, charset));
        io.startReading();
        final String desNfd = io.getInput().readNormalisedString(originalString.getBytes().length * 2);
        final String desNfc = io.getInput().readNormalisedString(originalString.getBytes().length * 2);

        assertEquals(desNfd, desNfc, "when serialized and deserialized, the strings should have been normalized");
        assertArrayEquals(
                desNfd.getBytes(charset),
                desNfc.getBytes(charset),
                "if the strings were normalized, they should have identical byte representations");
    }

    @ParameterizedTest
    @MethodSource("byteArrayArgProvider")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.IO)
    @DisplayName("Byte Array")
    void byteArray(byte[] array) throws IOException {
        InputOutputStream io = new InputOutputStream();
        io.getOutput().writeByteArray(array);
        io.startReading();
        byte[] copy = io.getInput().readByteArray(MAX_LENGTH);

        assertArrayEquals(array, copy);
        io.close();
    }

    static Stream<SelfSerializableExample> serializableArgProvider() {
        return Stream.of(null, new SelfSerializableExample(), new SelfSerializableExample(123, "some string"));
    }

    @ParameterizedTest
    @MethodSource("serializableArgProvider")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.IO)
    @DisplayName("Serializable")
    void serializable(SelfSerializableExample serializable) throws IOException {
        InputOutputStream io = new InputOutputStream();
        io.getOutput().writeSerializable(serializable, true);
        io.getOutput().writeSerializable(serializable, true);
        io.getOutput().writeSerializable(serializable, false);
        io.startReading();
        SelfSerializable runtime = io.getInput().readSerializable();
        SelfSerializable withId = io.getInput().readSerializable(true, SelfSerializableExample::new);
        SelfSerializable withoutId = io.getInput().readSerializable(false, SelfSerializableExample::new);

        assertEquals(serializable, runtime);
        assertEquals(serializable, withId);
        assertEquals(serializable, withoutId);
        io.close();
    }

    static Stream<String> stringArgProvider() {
        return Stream.of(
                null, "", "some string", "Лазар Петровић" // multi byte UTF-8
                );
    }

    @ParameterizedTest
    @MethodSource("stringArgProvider")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.IO)
    @DisplayName("String")
    void string(String string) throws IOException {
        InputOutputStream io = new InputOutputStream();
        io.getOutput().writeNormalisedString(string);
        io.startReading();
        String copy = io.getInput().readNormalisedString(MAX_LENGTH);

        assertEquals(string, copy);
        io.close();
    }

    static Stream<List<SelfSerializableExample>> serializableListArgProvider() {
        return Stream.of(
                null,
                Collections.EMPTY_LIST,
                Arrays.asList(null, new SelfSerializableExample(), new SelfSerializableExample(321, "aaddssff")),
                Arrays.asList(
                        new SelfSerializableExample(),
                        new SelfSerializableExample(55446, "another string"),
                        null,
                        null));
    }

    @ParameterizedTest
    @MethodSource("serializableListArgProvider")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.IO)
    @DisplayName("Serializable List")
    void serializableList(List<SelfSerializableExample> list) throws IOException {
        InputOutputStream io = new InputOutputStream();
        io.getOutput().writeSerializableList(list, true, true);
        io.getOutput().writeSerializableList(list, true, true);
        io.getOutput().writeSerializableList(list, false, true);
        io.getOutput().writeSerializableList(list, true, false);
        io.getOutput().writeSerializableList(list, false, false);
        io.startReading();
        List<SelfSerializable> runtime = io.getInput().readSerializableList(MAX_LENGTH);
        List<SelfSerializableExample> allSameWithClassId =
                io.getInput().readSerializableList(MAX_LENGTH, true, SelfSerializableExample::new);
        List<SelfSerializableExample> allSameWithoutClassId =
                io.getInput().readSerializableList(MAX_LENGTH, false, SelfSerializableExample::new);
        List<SelfSerializableExample> notAllSameWithClassId =
                io.getInput().readSerializableList(MAX_LENGTH, true, SelfSerializableExample::new);
        List<SelfSerializableExample> notAllSameWithoutClassId =
                io.getInput().readSerializableList(MAX_LENGTH, false, SelfSerializableExample::new);

        assertEquals(list, runtime);
        assertEquals(list, allSameWithClassId);
        assertEquals(list, allSameWithoutClassId);
        assertEquals(list, notAllSameWithClassId);
        assertEquals(list, notAllSameWithoutClassId);

        io.close();
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.IO)
    @DisplayName("Instant Test")
    void instantTest() throws IOException {
        InputOutputStream io = new InputOutputStream();

        Instant original = Instant.now();
        io.getOutput().writeInstant(original);
        // fake instant with bad nanos
        io.getOutput().writeLong(123);
        io.getOutput().writeLong(-100);
        // fake instant with bad nanos
        io.getOutput().writeLong(123);
        io.getOutput().writeLong(1_000_000_000);

        io.startReading();

        Instant copy = io.getInput().readInstant();
        assertEquals(original, copy);
        // these should throw because nanos is invalid
        assertThrows(IOException.class, () -> io.getInput().readInstant());
        assertThrows(IOException.class, () -> io.getInput().readInstant());
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.IO)
    @DisplayName("getLongArraySerializedLengthTest")
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 10, 1024})
    void getLongArraySerializedLengthTest(int num) {
        assertEquals(
                LENGTH_IN_BYTES + num * Long.BYTES,
                AugmentedDataOutputStream.getArraySerializedLength(new long[num]),
                "length mismatch");

        long[] numbers = null;
        if (num > 0) {
            numbers = new long[num];
            for (int i = 0; i < numbers.length; i++) {
                numbers[i] = random.nextLong();
            }
        }
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.IO)
    @DisplayName("getIntArraySerializedLengthTest")
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 10, 1024})
    void getIntArraySerializedLengthTest(int num) {
        assertEquals(
                LENGTH_IN_BYTES + num * Integer.BYTES,
                AugmentedDataOutputStream.getArraySerializedLength(new int[num]));
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.IO)
    @DisplayName("getNullByteArraySerializedLengthTest")
    void getNullByteArraySerializedLengthTest() {

        // without checksum
        assertEquals(LENGTH_IN_BYTES, AugmentedDataOutputStream.getArraySerializedLength(null, false));

        // with checksum
        assertEquals(
                LENGTH_IN_BYTES + CHECKSUM_IN_BYTES, AugmentedDataOutputStream.getArraySerializedLength(null, true));
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.IO)
    @DisplayName("getByteArraySerializedLengthTest")
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 10, 1024})
    void getByteArraySerializedLengthTest(int num) {
        assertEquals(
                LENGTH_IN_BYTES + num * Byte.BYTES, AugmentedDataOutputStream.getArraySerializedLength(new byte[num]));

        // without checksum
        assertEquals(
                LENGTH_IN_BYTES + num * Byte.BYTES,
                AugmentedDataOutputStream.getArraySerializedLength(new byte[num], false));

        // with checksum
        assertEquals(
                LENGTH_IN_BYTES + CHECKSUM_IN_BYTES + num * Byte.BYTES,
                AugmentedDataOutputStream.getArraySerializedLength(new byte[num], true));
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.IO)
    @DisplayName("serializedLengthEmptyArray")
    void serializedLengthEmptyArray() throws IOException {
        Transaction[] transactions = new Transaction[0];
        final int length = SerializableDataOutputStream.getSerializedLength(transactions, true, false);

        try (final ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            try (final SerializableDataOutputStream dos = new SerializableDataOutputStream(bos)) {
                dos.writeSerializableArray(transactions, true, false);
                checkExpectedSize(dos.size(), length);
            }
        }
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.IO)
    @DisplayName("serializedLengthArrayWithNullElement")
    void serializedLengthArrayWithNullElement() throws IOException {
        Transaction[] transactions = TransactionUtils.randomSwirldTransactions(1234321, 2);
        transactions[1] = null;

        final int length = SerializableDataOutputStream.getSerializedLength(transactions, true, false);

        try (final ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            try (final SerializableDataOutputStream dos = new SerializableDataOutputStream(bos)) {
                dos.writeSerializableArray(transactions, true, false);
                checkExpectedSize(dos.size(), length);
            }
        }
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.IO)
    @DisplayName("serializedLengthNullArray")
    void serializedLengthNullArray() throws IOException {
        final int length = SerializableDataOutputStream.getSerializedLength(null, true, false);

        try (final ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            try (final SerializableDataOutputStream dos = new SerializableDataOutputStream(bos)) {
                dos.writeSerializableArray(null, true, false);
                checkExpectedSize(dos.size(), length);
            }
        }
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.IO)
    @DisplayName("serializedSingleInstance")
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 10, 64, 100})
    void serializedSingleInstance(int tranAmount) throws IOException {
        final Transaction[] randomTransactions = TransactionUtils.randomSwirldTransactions(1234321, tranAmount);

        for (Transaction tran : randomTransactions) {
            try (final ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                try (final SerializableDataOutputStream dos = new SerializableDataOutputStream(bos)) {
                    // write a single instance to stream, the version info is always written
                    final int length = SerializableDataOutputStream.getInstanceSerializedLength(tran, true, true);
                    dos.writeSerializable(tran, true);
                    checkExpectedSize(dos.size(), length);
                }
            }
        }

        for (Transaction tran : randomTransactions) {
            try (final ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                try (final SerializableDataOutputStream dos = new SerializableDataOutputStream(bos)) {
                    // write a single instance to stream, the version info is always written
                    final int length = SerializableDataOutputStream.getInstanceSerializedLength(tran, true, false);
                    dos.writeSerializable(tran, false);
                    checkExpectedSize(dos.size(), length);
                }
            }
        }
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.IO)
    @DisplayName("serializedLengthArraySameClass")
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 10, 64, 100})
    void serializedLengthArraySameClass(int tranAmount) throws IOException {
        Transaction[] transactions = TransactionUtils.randomSwirldTransactions(1234321, tranAmount);

        try (final ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            try (final SerializableDataOutputStream dos = new SerializableDataOutputStream(bos)) {
                final int length = SerializableDataOutputStream.getSerializedLength(transactions, true, true);
                dos.writeSerializableArray(transactions, true, true);
                checkExpectedSize(dos.size(), length);
            }
        }

        try (final ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            try (final SerializableDataOutputStream dos = new SerializableDataOutputStream(bos)) {
                final int length = SerializableDataOutputStream.getSerializedLength(transactions, false, true);
                dos.writeSerializableArray(transactions, false, true);
                checkExpectedSize(dos.size(), length);
            }
        }
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.IO)
    @DisplayName("serializedLengthArrayDiffClass")
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 10, 64, 100})
    void serializedLengthArrayDiffClass(int tranAmount) throws IOException {
        Transaction[] randomTransactions = TransactionUtils.randomMixedTransactions(new Random(), tranAmount);

        try (final ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            try (final SerializableDataOutputStream dos = new SerializableDataOutputStream(bos)) {
                final int length = SerializableDataOutputStream.getSerializedLength(randomTransactions, true, false);
                dos.writeSerializableArray(randomTransactions, true, false);
                checkExpectedSize(dos.size(), length);
            }
        }
    }

    private void checkExpectedSize(int actualWrittenBytes, int calculatedBytes) {
        assertEquals(actualWrittenBytes, calculatedBytes, "length mismatch");
    }
}

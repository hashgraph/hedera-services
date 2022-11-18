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

import static org.junit.jupiter.api.Assertions.*;

import com.swirlds.common.exceptions.MutabilityException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import utils.TestUtils;

class StringLeafTest {
    @Test
    @DisplayName("Null string values are not allowed")
    void nullValueThrowsConstructor() {
        //noinspection ConstantConditions
        assertThrows(NullPointerException.class, () -> new StringLeaf(null));
    }

    @Test
    @DisplayName("Long string values are not allowed")
    void longValueThrowsConstructor() {
        final var randomString = new String(TestUtils.randomBytes(129));
        assertThrows(IllegalArgumentException.class, () -> new StringLeaf(randomString));
    }

    @ParameterizedTest(name = "With string `{0}`")
    @ValueSource(strings = {"", " ", "\n", "\uD83D\uDE07", "Test", "Multiple Words"})
    @DisplayName(
            "Non-null string values are allowed if their length in bytes is less than the maximum")
    void nonNullStringValuesAreGood(String value) {
        final var leaf = new StringLeaf(value);
        assertEquals(value, leaf.getValue());
    }

    @Test
    @DisplayName("Cannot copy a leaf twice")
    void copiedLeafBecomesImmutable() {
        final var leaf1 = new StringLeaf("Leaf Value");
        leaf1.copy();
        assertThrows(MutabilityException.class, leaf1::copy);
    }

    @Test
    @DisplayName("Copies are exactly the same")
    void copies() {
        // Note, leaf1.value may not be exactly the same as `value`, because these random bytes will
        // be normalized by the constructor.
        final var value = new String(TestUtils.randomBytes(32));
        final var leaf1 = new StringLeaf(value);
        final var leaf2 = leaf1.copy();
        assertEquals(leaf1.getValue(), leaf2.getValue());
    }

    @Test
    @DisplayName("Leaf can be serialized and deserialized")
    void serdes() throws IOException {
        // Note, leaf1.value may not be exactly the same as `value`, because these random bytes will
        // be normalized by the constructor.
        final var value = new String(TestUtils.randomBytes(32));
        final var originalLeaf = new StringLeaf(value);
        final var outputStream = new ByteArrayOutputStream();
        originalLeaf.serialize(new SerializableDataOutputStream(outputStream));

        //noinspection deprecation
        final var deserializedLeaf = new StringLeaf();
        var inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        deserializedLeaf.deserialize(
                new SerializableDataInputStream(inputStream), originalLeaf.getVersion());
        assertEquals(originalLeaf.getValue(), deserializedLeaf.getValue());

        inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        final var deserializedLeaf2 =
                StringLeaf.createFromStream(
                        new SerializableDataInputStream(inputStream), originalLeaf.getVersion());
        assertEquals(originalLeaf.getValue(), deserializedLeaf2.getValue());
    }

    @Test
    @DisplayName("We cannot deserialize newer versions")
    void deserializeNewerVersionsThrows() {
        final var value = TestUtils.randomBytes(32);
        final var leaf = new StringLeaf("");

        final var inputStream1 = new SerializableDataInputStream(new ByteArrayInputStream(value));
        var e =
                assertThrows(
                        IllegalArgumentException.class, () -> leaf.deserialize(inputStream1, 200));
        assertTrue(e.getMessage().contains("version"));

        final var inputStream2 = new SerializableDataInputStream(new ByteArrayInputStream(value));
        e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> StringLeaf.createFromStream(inputStream2, 200));
        assertTrue(e.getMessage().contains("version"));
    }

    @Test
    @DisplayName("equals and hashCode are consistent")
    void equalsAndHashcode() {
        final var leaf1 = new StringLeaf("Value 1");
        final var leaf2 = new StringLeaf("Value 2");
        final var sameAsLeaf1 = new StringLeaf("Value 1");

        assertEquals(leaf1, sameAsLeaf1);
        assertNotEquals(leaf1, leaf2);

        assertEquals(leaf1.hashCode(), sameAsLeaf1.hashCode());
        assertNotEquals(leaf1.hashCode(), leaf2.hashCode());

        assertEquals(leaf1, leaf1.copy());
    }
}

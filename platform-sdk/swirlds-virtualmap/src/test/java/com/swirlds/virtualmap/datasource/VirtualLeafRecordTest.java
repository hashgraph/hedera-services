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

package com.swirlds.virtualmap.datasource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.test.io.InputOutputStream;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import com.swirlds.virtualmap.TestKey;
import com.swirlds.virtualmap.TestValue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class VirtualLeafRecordTest {
    private static final long FAKE_KEY_NUM = -1000;
    private static final long DIFFERENT_KEY_NUM = -2000;
    private static final Random RANDOM = new Random(49);
    private static final Cryptography CRYPTO = CryptographyHolder.get();

    @BeforeAll
    public static void globalSetup() throws ConstructableRegistryException {
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();
        registry.registerConstructable(new ClassConstructorPair(VirtualLeafRecord.class, VirtualLeafRecord::new));
        registry.registerConstructable(new ClassConstructorPair(TestKey.class, TestKey::new));
        registry.registerConstructable(new ClassConstructorPair(TestValue.class, TestValue::new));
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("Using the default Constructor works")
    void createLeafRecordUsingDefaultConstructor() {
        final VirtualLeafRecord<TestKey, TestValue> rec = new VirtualLeafRecord<>();
        assertNull(rec.getHash(), "hash should be null");
        assertNull(rec.getKey(), "key should be null");
        assertNull(rec.getValue(), "value should be null");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("Using the full constructor works")
    void createLeafRecordUsingFullConstructor() {
        final Hash hash = CRYPTO.digestSync("Fake Hash".getBytes(StandardCharsets.UTF_8));
        final TestKey key = new TestKey(FAKE_KEY_NUM);
        final TestValue value = new TestValue("Fake value");
        final VirtualLeafRecord<TestKey, TestValue> rec = new VirtualLeafRecord<>(102, hash, key, value);
        assertEquals(hash, rec.getHash(), "hash should match original");
        assertEquals(key, rec.getKey(), "key should match original");
        assertEquals(value, rec.getValue(), "value should match original");
        assertEquals(102, rec.getPath(), "path should match value set");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("toString with a null elements is OK")
    void toStringWithNullElementsDoesNotThrow() {
        final VirtualLeafRecord<TestKey, TestValue> rec = new VirtualLeafRecord<>();
        final String str = rec.toString();
        assertNotNull(str, "value should not be null");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("Setting the value to the same thing does nothing")
    void settingIdentityValueIsNoop() {
        final Hash hash = CRYPTO.digestSync("Fake Hash".getBytes(StandardCharsets.UTF_8));
        final TestKey key = new TestKey(FAKE_KEY_NUM);
        final TestValue value = new TestValue("Fake value");
        final VirtualLeafRecord<TestKey, TestValue> rec = new VirtualLeafRecord<>(102, hash, key, value);
        rec.setValue(value);
        assertEquals(hash, rec.getHash(), "hash should match original");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("Changing the value invalidates the hash")
    void settingNewValueInvalidatesHash() {
        final Hash hash = CRYPTO.digestSync("Fake Hash".getBytes(StandardCharsets.UTF_8));
        final TestKey key = new TestKey(FAKE_KEY_NUM);
        final TestValue value = new TestValue("Fake value");
        final VirtualLeafRecord<TestKey, TestValue> rec = new VirtualLeafRecord<>(102, hash, key, value);
        rec.setValue(new TestValue("New Fake value"));
        assertNull(rec.getHash(), "hash should be null");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("Identity equals")
    void identityEqualsWorks() {
        final Hash hash = CRYPTO.digestSync("Fake Hash".getBytes(StandardCharsets.UTF_8));
        final TestKey key = new TestKey(FAKE_KEY_NUM);
        final TestValue value = new TestValue("Fake value");
        final VirtualLeafRecord<TestKey, TestValue> rec = new VirtualLeafRecord<>(102, hash, key, value);
        assertEquals(rec, rec, "records should be equal");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("Equal instances")
    void equalInstances() {
        final Hash hash = CRYPTO.digestSync("Fake Hash".getBytes(StandardCharsets.UTF_8));
        final TestKey key = new TestKey(FAKE_KEY_NUM);
        final TestValue value = new TestValue("Fake value");
        final VirtualLeafRecord<TestKey, TestValue> rec = new VirtualLeafRecord<>(102, hash, key, value);
        final VirtualLeafRecord<TestKey, TestValue> rec2 = new VirtualLeafRecord<>(102, hash, key, value);
        assertEquals(rec, rec2, "records should be equal");
        assertEquals(rec2, rec, "records should be equal");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("Unequal instances")
    void unequalInstances() {
        final Hash hash = CRYPTO.digestSync("Fake Hash".getBytes(StandardCharsets.UTF_8));
        final TestKey key = new TestKey(FAKE_KEY_NUM);
        final TestValue value = new TestValue("Fake value");
        final VirtualLeafRecord<TestKey, TestValue> first = new VirtualLeafRecord<>(102, hash, key, value);

        // Test with null
        //noinspection ConstantConditions,SimplifiableAssertion
        assertFalse(first.equals(null), "should not be equal with null");

        // Test with a different path
        VirtualLeafRecord<TestKey, TestValue> second = new VirtualLeafRecord<>(988, hash, key, value);
        assertNotEquals(first, second, "records should not be equal");
        assertNotEquals(second, first, "records should not be equal");

        // Test with a different hash
        final Hash differentHash = CRYPTO.digestSync("Different hash".getBytes(StandardCharsets.UTF_8));
        second = new VirtualLeafRecord<>(102, differentHash, key, value);
        assertNotEquals(first, second, "records should not be equal");
        assertNotEquals(second, first, "records should not be equal");

        // Test with a null hash
        second = new VirtualLeafRecord<>(102, null, key, value);
        assertNotEquals(first, second, "records should not be equal");
        assertNotEquals(second, first, "records should not be equal");

        // Test with a different key
        final TestKey differentKey = new TestKey(DIFFERENT_KEY_NUM);
        second = new VirtualLeafRecord<>(102, hash, differentKey, value);
        assertNotEquals(first, second, "records should not be equal");
        assertNotEquals(second, first, "records should not be equal");

        // Test with a null key
        second = new VirtualLeafRecord<>(102, hash, null, value);
        assertNotEquals(first, second, "records should not be equal");
        assertNotEquals(second, first, "records should not be equal");

        // Test with a different value
        final TestValue differentValue = new TestValue("Different value");
        second = new VirtualLeafRecord<>(102, hash, key, differentValue);
        assertNotEquals(first, second, "records should not be equal");
        assertNotEquals(second, first, "records should not be equal");

        // Test with a null value
        second = new VirtualLeafRecord<>(102, hash, key, null);
        assertNotEquals(first, second, "records should not be equal");
        assertNotEquals(second, first, "records should not be equal");

        // Test with some random object
        final String random = "Random!";
        //noinspection AssertBetweenInconvertibleTypes
        assertNotEquals(first, random, "records should not be equal");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("hashCode")
    void testHashCode() {
        final Hash hash = CRYPTO.digestSync("Fake Hash".getBytes(StandardCharsets.UTF_8));
        final TestKey key = new TestKey(FAKE_KEY_NUM);
        final TestValue value = new TestValue("Fake value");
        final VirtualLeafRecord<TestKey, TestValue> rec = new VirtualLeafRecord<>(102, hash, key, value);
        final int hash1 = rec.hashCode();

        // Test the identity
        VirtualLeafRecord<TestKey, TestValue> second = new VirtualLeafRecord<>(102, hash, key, value);
        assertEquals(hash1, second.hashCode(), "hash should match original");

        // Create a variant with a different path and assert the hashCode is different
        second = new VirtualLeafRecord<>(988, hash, key, value);
        assertNotEquals(hash1, second.hashCode(), "hash should not be the same");

        // Create a variant with a different hash and assert the hashCode is different
        final Hash differentHash = CRYPTO.digestSync("Different hash".getBytes(StandardCharsets.UTF_8));
        second = new VirtualLeafRecord<>(102, differentHash, key, value);
        assertNotEquals(hash1, second.hashCode(), "hash should not be the same");

        // Test with a null hash
        second = new VirtualLeafRecord<>(102, null, key, value);
        assertNotEquals(hash1, second.hashCode(), "hash should not be the same");

        // Test with a different key
        final TestKey differentKey = new TestKey(DIFFERENT_KEY_NUM);
        second = new VirtualLeafRecord<>(102, hash, differentKey, value);
        assertNotEquals(hash1, second.hashCode(), "hash should not be the same");

        // Test with a null key
        second = new VirtualLeafRecord<>(102, hash, null, value);
        assertNotEquals(hash1, second.hashCode(), "hash should not be the same");

        // Test with a different value
        final TestValue differentValue = new TestValue("Different value");
        second = new VirtualLeafRecord<>(102, hash, key, differentValue);
        assertNotEquals(hash1, second.hashCode(), "hash should not be the same");

        // Test with a null value
        second = new VirtualLeafRecord<>(102, hash, key, null);
        assertNotEquals(hash1, second.hashCode(), "hash should not be the same");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("Serialization and deserialization")
    void serializesAndDeserializes() throws IOException {
        final long keyId = RANDOM.nextLong();
        final TestKey key = new TestKey(keyId);
        final TestValue value = new TestValue("This is a custom value");
        final long path = 1329;
        final VirtualLeafRecord<TestKey, TestValue> leafRecord = new VirtualLeafRecord<>(path, null, key, value);
        final Hash hash = CRYPTO.digestSync(leafRecord);
        leafRecord.setHash(hash);

        try (final InputOutputStream ioStream = new InputOutputStream()) {
            ioStream.getOutput().writeSerializable(leafRecord, true);
            ioStream.startReading();
            final VirtualLeafRecord<TestKey, TestValue> deserializedLeafRecord =
                    ioStream.getInput().readSerializable();
            final Hash deserializedHash = CRYPTO.digestSync(deserializedLeafRecord);
            deserializedLeafRecord.setHash(deserializedHash);

            assertEquals(leafRecord, deserializedLeafRecord, "Deserialized leaf should match original");
        }
    }
}

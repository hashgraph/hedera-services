/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.crypto;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.common.test.fixtures.io.InputOutputStream;
import com.swirlds.common.test.fixtures.io.SerializationUtils;
import com.swirlds.common.utility.Mnemonics;
import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;

public class HashTests {

    @BeforeAll
    public static void setUp() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds.common.crypto");
    }

    @Test
    public void exceptionTests() {
        final byte[] nonZeroHashValue = new byte[DigestType.SHA_384.digestLength()];
        Arrays.fill(nonZeroHashValue, Byte.MAX_VALUE);

        final Hash hash = new Hash(DigestType.SHA_384);

        assertDoesNotThrow((ThrowingSupplier<Hash>) Hash::new);
        assertDoesNotThrow(() -> new Hash(nonZeroHashValue));
        assertDoesNotThrow(() -> new Hash(DigestType.SHA_384));
        assertDoesNotThrow(() -> new Hash(DigestType.SHA_512));

        assertThrows(NullPointerException.class, () -> new Hash((DigestType) null));
        assertThrows(NullPointerException.class, () -> new Hash((byte[]) null));
        assertThrows(IllegalArgumentException.class, () -> new Hash((Hash) null));

        assertThrows(NullPointerException.class, () -> new Hash(nonZeroHashValue, null));
        assertThrows(IllegalArgumentException.class, () -> new Hash(new byte[0], DigestType.SHA_384));
        assertThrows(IllegalArgumentException.class, () -> new Hash(new byte[47], DigestType.SHA_384));
        assertThrows(IllegalArgumentException.class, () -> new Hash(new byte[71], DigestType.SHA_512));
    }

    @Test
    public void serializeDeserialize() throws IOException {
        final InputOutputStream ioStream = new InputOutputStream();
        final HashBuilder builder = new HashBuilder(DigestType.SHA_384);

        final Hash original = builder.update(0x0f87da12).build();

        ioStream.getOutput().writeSerializable(original, true);
        ioStream.startReading();

        final Hash copy = ioStream.getInput().readSerializable(true, Hash::new);
        assertEquals(original, copy);
    }

    @Test
    public void accessorCorrectness() {
        final HashBuilder builder = new HashBuilder(DigestType.SHA_384);

        final Hash original = builder.update(0x1d88a790).build();
        final Hash copy = original.copy();
        final Hash recalculated = builder.update(0x1d88a790).build();
        final Hash different = builder.update(0x1d112233).build();

        assertNotNull(original.toString());
        assertEquals(96, original.toString().length());
        assertEquals(original.toString(), copy.toString());
        assertEquals(original.toString(), recalculated.toString());
        assertEquals(copy.toString(), recalculated.toString());
        assertNotEquals(original.toString(), different.toString());
        assertNotEquals(copy.toString(), different.toString());

        assertFalse(original.equals(null));
        assertNotEquals(original, new Object());
        assertTrue(original.equals(original));
        assertEquals(0, original.compareTo(original));
        assertNotEquals(0, original.compareTo(new Hash(DigestType.SHA_512)));

        ////////
        assertEquals(original.getBytes(), copy.getBytes());
        assertEquals(original.getBytes(), recalculated.getBytes());
        assertEquals(copy.getBytes(), recalculated.getBytes());

        assertEquals(original, copy);
        assertEquals(original, recalculated);
        assertEquals(copy, recalculated);
        assertNotEquals(original, different);
        assertNotEquals(copy, different);

        assertEquals(0, original.compareTo(copy));
        assertEquals(0, original.compareTo(recalculated));
        assertEquals(0, copy.compareTo(recalculated));
        assertThrows(NullPointerException.class, () -> original.compareTo(null));
        assertNotEquals(0, original.compareTo(different));
        assertNotEquals(0, copy.compareTo(different));

        assertEquals(original.hashCode(), copy.hashCode());
        assertEquals(original.hashCode(), recalculated.hashCode());
        assertEquals(copy.hashCode(), recalculated.hashCode());
        assertNotEquals(original.hashCode(), different.hashCode());
        assertNotEquals(copy.hashCode(), different.hashCode());
    }

    @Test
    public void serializeAndDeserializeTest() throws IOException {
        final HashBuilder builder = new HashBuilder(DigestType.SHA_384);

        final Hash original = builder.update(0x1d88a790).build();
        SerializationUtils.checkSerializeDeserializeEqual(original);
    }

    @Test
    public void serializeAndDeserializeImmutableHashTest() throws IOException {
        final HashBuilder builder = new HashBuilder(DigestType.SHA_384);

        final Hash original = builder.update(0x1d88a790).build();
        SerializationUtils.checkSerializeDeserializeEqual(original);
    }

    @Test
    @DisplayName("Mnemonic Test")
    void mnemonicTest() {
        final Hash hash = RandomUtils.randomHash();
        final String mnemonic = hash.toMnemonic();
        assertEquals(mnemonic, hash.toMnemonic());
        final String[] words = mnemonic.split("-");
        assertEquals(4, words.length);
        for (final String word : words) {
            assertDoesNotThrow(() -> Mnemonics.getWordIndex(word));
        }
    }
}

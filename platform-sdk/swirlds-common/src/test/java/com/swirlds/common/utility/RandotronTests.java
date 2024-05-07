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

package com.swirlds.common.utility;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.test.fixtures.Randotron;
import java.net.InetAddress;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the Randotron class
 */
class RandotronTests {
    private Randotron random1;
    private Randotron random1Duplicate;
    private Randotron random2;

    @BeforeEach
    void setUp() {
        random1 = Randotron.create();
        random1Duplicate = Randotron.create(random1.getSeed());
        random2 = Randotron.create();
    }

    @Test
    @DisplayName("Test randomString with different seeds")
    void randomStringUnique() {
        final String randomString1 = random1.randomString(10);
        final String randomString2 = random2.randomString(10);

        assertNotEquals(randomString1, randomString2);
        assertEquals(10, randomString1.length());
        assertEquals(10, randomString2.length());
    }

    @Test
    @DisplayName("Test randomString with same seed")
    void randomStringSameSeed() {
        final String randomString1 = random1.randomString(10);
        final String randomString2 = random1Duplicate.randomString(10);

        assertEquals(randomString1, randomString2);
        assertEquals(10, randomString1.length());
        assertEquals(10, randomString2.length());
    }

    @Test
    @DisplayName("Test randomString edge cases")
    void randomStringEdgeCases() {
        assertEquals("", random1.randomString(0));
        assertThrows(IllegalArgumentException.class, () -> random1.randomString(-1));
    }

    @Test
    @DisplayName("Test randomIp with different seeds")
    void randomIpUnique() {
        final String ip1 = random1.randomIp();
        final String ip2 = random2.randomIp();

        assertNotEquals(ip1, ip2);
        assertDoesNotThrow(() -> InetAddress.getByName(ip1));
        assertDoesNotThrow(() -> InetAddress.getByName(ip2));
    }

    @Test
    @DisplayName("Test randomIp with same seed")
    void randomIpSameSeed() {
        final String ip1 = random1.randomIp();
        final String ip2 = random1Duplicate.randomIp();

        assertEquals(ip1, ip2);
        assertDoesNotThrow(() -> InetAddress.getByName(ip1));
        assertDoesNotThrow(() -> InetAddress.getByName(ip2));
    }

    @Test
    @DisplayName("Test bounded randomPositiveLong with different seeds")
    void randomPositiveBoundedLongUnique() {
        final long randomLong1 = random1.randomPositiveLong(10000000);
        final long randomLong2 = random2.randomPositiveLong(10000000);

        assertNotEquals(randomLong1, randomLong2);
        assertTrue(randomLong1 < 10000000);
        assertTrue(randomLong2 < 10000000);
    }

    @Test
    @DisplayName("Test bounded randomPositiveLong with same seed")
    void randomPositiveBoundedLongSameSeed() {
        final long randomLong1 = random1.randomPositiveLong(10000000);
        final long randomLong2 = random1Duplicate.randomPositiveLong(10000000);

        assertEquals(randomLong1, randomLong2);
        assertTrue(randomLong1 < 10000000);
    }

    @Test
    @DisplayName("Test bounded randomPositiveLong edge cases")
    void randomPositiveBoundedLongEdgeCases() {
        assertThrows(IllegalArgumentException.class, () -> random1.randomPositiveLong(1));
        assertThrows(IllegalArgumentException.class, () -> random1.randomPositiveLong(0));
        assertThrows(IllegalArgumentException.class, () -> random1.randomPositiveLong(-1));
        assertEquals(1, random1.randomPositiveLong(2));
    }

    @Test
    @DisplayName("Test randomPositiveLong with different seeds")
    void randomPositiveLongUnique() {
        assertNotEquals(random1.randomPositiveLong(), random2.randomPositiveLong());
    }

    @Test
    @DisplayName("Test randomPositiveLong with same seed")
    void randomPositiveLongSameSeed() {
        assertEquals(random1.randomPositiveLong(), random1Duplicate.randomPositiveLong());
    }

    @Test
    @DisplayName("Test randomHash with different seeds")
    void randomHashUnique() {
        final Hash hash1 = random1.randomHash();
        final Hash hash2 = random2.randomHash();
        assertNotEquals(hash1, hash2);

        assertEquals(48, hash1.getBytes().length());
        assertEquals(48, hash2.getBytes().length());
        assertEquals(DigestType.SHA_384, hash1.getDigestType());
        assertEquals(DigestType.SHA_384, hash2.getDigestType());
    }

    @Test
    @DisplayName("Test randomHash with same seed")
    void randomHashSameSeed() {
        final Hash hash1 = random1.randomHash();
        final Hash hash2 = random1Duplicate.randomHash();

        assertEquals(hash1, hash2);

        assertEquals(48, hash1.getBytes().length());
        assertEquals(48, hash2.getBytes().length());
        assertEquals(DigestType.SHA_384, hash1.getDigestType());
        assertEquals(DigestType.SHA_384, hash2.getDigestType());
    }

    @Test
    @DisplayName("Test randomHashBytes with different seeds")
    void randomHashBytesUnique() {
        final Bytes hashBytes1 = random1.randomHashBytes();
        final Bytes hashBytes2 = random2.randomHashBytes();

        assertNotEquals(hashBytes1, hashBytes2);
        assertEquals(48, hashBytes1.length());
        assertEquals(48, hashBytes2.length());
    }

    @Test
    @DisplayName("Test randomHashBytes with same seed")
    void randomHashBytesSameSeed() {
        final Bytes hashBytes1 = random1.randomHashBytes();
        final Bytes hashBytes2 = random1Duplicate.randomHashBytes();

        assertEquals(hashBytes1, hashBytes2);
        assertEquals(48, hashBytes1.length());
        assertEquals(48, hashBytes2.length());
    }

    @Test
    @DisplayName("Test randomSignature with different seeds")
    void randomSignatureUnique() {
        final Signature signature1 = random1.randomSignature();
        final Signature signature2 = random2.randomSignature();

        assertNotEquals(signature1, signature2);
        assertEquals(384, signature1.getSignatureBytes().length);
        assertEquals(384, signature2.getSignatureBytes().length);
    }

    @Test
    @DisplayName("Test randomSignature with same seed")
    void randomSignatureSameSeed() {
        final Signature signature1 = random1.randomSignature();
        final Signature signature2 = random1Duplicate.randomSignature();

        assertEquals(signature1, signature2);
        assertEquals(384, signature1.getSignatureBytes().length);
        assertEquals(384, signature2.getSignatureBytes().length);
    }

    @Test
    @DisplayName("Test randomSignatureBytes with different seeds")
    void randomSignatureBytesUnique() {
        final Bytes signatureBytes1 = random1.randomSignatureBytes();
        final Bytes signatureBytes2 = random2.randomSignatureBytes();

        assertNotEquals(signatureBytes1, signatureBytes2);
        assertEquals(384, signatureBytes1.length());
        assertEquals(384, signatureBytes2.length());
    }

    @Test
    @DisplayName("Test randomSignatureBytes with same seed")
    void randomSignatureBytesSameSeed() {
        final Bytes signatureBytes1 = random1.randomSignatureBytes();
        final Bytes signatureBytes2 = random1Duplicate.randomSignatureBytes();

        assertEquals(signatureBytes1, signatureBytes2);
        assertEquals(384, signatureBytes1.length());
        assertEquals(384, signatureBytes2.length());
    }

    @Test
    @DisplayName("Test randomByteArray with different seeds")
    void randomByteArrayUnique() {
        final byte[] randomBytes1 = random1.randomByteArray(10);
        final byte[] randomBytes2 = random2.randomByteArray(10);

        assertFalse(Arrays.equals(randomBytes1, randomBytes2));

        assertEquals(10, randomBytes1.length);
        assertEquals(10, randomBytes2.length);
    }

    @Test
    @DisplayName("Test randomByteArray with same seed")
    void randomByteArraySameSeed() {
        final byte[] randomBytes1 = random1.randomByteArray(10);
        final byte[] randomBytes2 = random1Duplicate.randomByteArray(10);

        assertArrayEquals(random1.randomByteArray(10), random1Duplicate.randomByteArray(10));
        assertEquals(10, randomBytes1.length);
        assertEquals(10, randomBytes2.length);
    }

    @Test
    @DisplayName("Test randomInstant with different seeds")
    void randomInstantUnique() {
        assertNotEquals(random1.randomInstant(), random2.randomInstant());
    }

    @Test
    @DisplayName("Test randomInstant with same seed")
    void randomInstantSameSeed() {
        assertEquals(random1.randomInstant(), random1Duplicate.randomInstant());
    }

    @Test
    @DisplayName("Test randomBooleanWithProbability with different seeds")
    void randomBooleanWithProbabilityUnique() {
        boolean isDifferent = false;
        for (int i = 0; i < 100; i++) {
            if (random1.randomBooleanWithProbability(0.5) != random2.randomBooleanWithProbability(0.5)) {
                isDifferent = true;
                break;
            }
        }

        assertTrue(isDifferent, "You should buy a lottery ticket if this test fails");
    }

    @Test
    @DisplayName("Test randomBooleanWithProbability with same seed")
    void randomBooleanWithProbabilitySameSeed() {
        for (int i = 0; i < 100; i++) {
            assertEquals(random1.randomBooleanWithProbability(0.5), random1Duplicate.randomBooleanWithProbability(0.5));
        }
    }

    @Test
    @DisplayName("Test randomBooleanWithProbability edge cases")
    void randomBooleanWithProbabilityEdgeCases() {
        assertThrows(IllegalArgumentException.class, () -> random1.randomBooleanWithProbability(1.1));
        assertThrows(IllegalArgumentException.class, () -> random1.randomBooleanWithProbability(-0.1));
        assertDoesNotThrow(() -> random1.randomBooleanWithProbability(0));
        assertDoesNotThrow(() -> random1.randomBooleanWithProbability(1));
    }
}
